package io.github.jeroenvervaeke.coffeefinder.data.finder

import io.github.jeroenvervaeke.coffeefinder.data.PlaceRepository
import io.github.jeroenvervaeke.coffeefinder.data.geo.Camera
import io.github.jeroenvervaeke.coffeefinder.data.geo.showsSameAs
import io.github.jeroenvervaeke.coffeefinder.data.model.Coordinates
import io.github.jeroenvervaeke.coffeefinder.data.model.Ireland
import io.github.jeroenvervaeke.coffeefinder.data.model.Metres
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlin.time.TimeSource
import kotlin.time.measureTimedValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/**
 * The map screen's state: a camera in, and the coffee places inside what it is looking at out.
 *
 * The camera moves once per frame while a finger is on the screen, so the two filters in front of
 * the query matter more here than on the list. `debounce` waits for the gesture to stop, and
 * [showsSameAs] drops movement too small to change what is drawn: a pixel of drift on a released
 * finger would otherwise cost a full `$geoWithin` over the island.
 *
 * [camera] is published unfiltered, because the canvas redraws every frame from it while the
 * query behind it is still catching up.
 */
class MapFinder(
    private val places: PlaceRepository,
    scope: CoroutineScope,
    private val limit: Int = DEFAULT_LIMIT,
    /**
     * Where [MapState.Ready.took] is read from.
     *
     * Injected so the tests can measure against the virtual clock they already advance by hand.
     * Left to the monotonic clock, they would time a scripted fake on real threads and assert
     * about a duration that is whatever the machine felt like.
     */
    private val clock: TimeSource = TimeSource.Monotonic,
) {
    private val cameraState = MutableStateFlow(Camera.IRELAND)
    private val aspectRatio = MutableStateFlow(Camera.PORTRAIT_ASPECT_RATIO)

    /** Whether a pan or a pinch has happened, which is what makes the camera the user's. */
    private var moved = false

    /** Whether a resize still refits the island, which stops as soon as anything else frames. */
    private var refitsIsland = true

    val camera: StateFlow<Camera> get() = cameraState

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val state: StateFlow<MapState> = combine(cameraState, aspectRatio, ::View)
        .debounce(SETTLE_MILLIS)
        .distinctUntilChanged { old, new ->
            old.camera.showsSameAs(new.camera) && abs(old.aspectRatio - new.aspectRatio) < ASPECT_TOLERANCE
        }
        .mapLatest(::load)
        .stateIn(scope, SharingStarted.Eagerly, MapState.Searching)

    /**
     * Applies one step of a gesture: a drag of [eastFraction] and [northFraction] of the screen,
     * and a pinch of [zoom].
     *
     * A gesture rather than a camera, because the canvas that reports the gesture is a frame
     * behind: it draws from the camera it last recomposed with, and two pointer events inside one
     * frame would both be applied to that same stale camera and the first would be lost. Here the
     * step is applied to whatever the camera is now.
     */
    fun moveBy(eastFraction: Double, northFraction: Double, zoom: Double) {
        moved = true
        refitsIsland = false
        cameraState.update { it.panned(eastFraction, northFraction, aspectRatio.value).zoomedBy(zoom) }
    }

    /**
     * Frames the radius the console screen is counting inside, around where it is measuring from.
     *
     * Asked for, so it takes the camera: a later resize will not throw it away and refit the
     * island.
     */
    fun frameOn(centre: Coordinates, radius: Metres) {
        moved = true
        refitsIsland = false
        cameraState.value = Camera.around(centre, radius)
    }

    /**
     * The same, but only while the camera is still the application's.
     *
     * What the console screen calls when it opens and when a location fix finally lands. A fix
     * that arrived after somebody had panned somewhere would otherwise pull the map out from
     * under them, and so would coming back to the map tab, which composes the screen again.
     */
    fun frameOnUnlessMoved(centre: Coordinates, radius: Metres) {
        if (!moved) frameOn(centre, radius)
    }

    /** Frames the whole island again, and lets a later resize keep it framed. */
    fun frameIreland() {
        moved = false
        refitsIsland = true
        cameraState.value = Camera.covering(Ireland.EXTENT, aspectRatio.value)
    }

    /**
     * The canvas reports its shape, because that is what decides how wide the viewport is.
     *
     * While the island is what is framed, it is reframed to the new shape, which is what makes
     * the view right on a tablet and after a rotation, not only on the portrait phone the default
     * assumes. Once something has framed something else, a resize only changes the viewport.
     */
    fun resizedTo(aspectRatio: Double) {
        this.aspectRatio.value = aspectRatio
        if (refitsIsland) cameraState.value = Camera.covering(Ireland.EXTENT, aspectRatio)
    }

    private suspend fun load(view: View): MapState = try {
        val viewport = view.camera.viewport(view.aspectRatio)
        val (found, took) = clock.measureTimedValue { places.inViewport(viewport, limit) }
        MapState.Ready(viewport, found.results, found.command, took)
    } catch (cancelled: CancellationException) {
        // Superseded by a later camera, cancelled by mapLatest. Reporting it would replace the
        // results of the query that superseded it with an error nobody caused.
        throw cancelled
    } catch (failure: Exception) {
        MapState.Failed(failure.message ?: failure.javaClass.simpleName)
    }

    private data class View(val camera: Camera, val aspectRatio: Double)
}

/** Long enough to sit out a fling, short enough that letting go feels like it queried at once. */
private const val SETTLE_MILLIS = 200L

/** Above the 5,180 places the seed holds, so the opening view of the island draws all of them. */
private const val DEFAULT_LIMIT = 6000

private const val ASPECT_TOLERANCE = 0.001
