package io.github.jeroenvervaeke.coffeefinder.data.finder

import io.github.jeroenvervaeke.coffeefinder.data.PlaceRepository
import io.github.jeroenvervaeke.coffeefinder.data.geo.Camera
import io.github.jeroenvervaeke.coffeefinder.data.geo.showsSameAs
import io.github.jeroenvervaeke.coffeefinder.data.model.Ireland
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
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
 * [showsSameAs] drops movement too small to change what is drawn — a pixel of drift on a released
 * finger would otherwise cost a full `$geoWithin` over the island.
 *
 * [camera] is published unfiltered, because the canvas redraws every frame from it while the
 * query behind it is still catching up.
 */
class MapFinder(
    private val places: PlaceRepository,
    scope: CoroutineScope,
    private val limit: Int = DEFAULT_LIMIT,
) {
    private val cameraState = MutableStateFlow(Camera.IRELAND)
    private val aspectRatio = MutableStateFlow(Camera.PORTRAIT_ASPECT_RATIO)

    /** Whether the user has taken over the camera; until then the island is reframed to fit. */
    private var moved = false

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
        cameraState.update { it.panned(eastFraction, northFraction, aspectRatio.value).zoomedBy(zoom) }
    }

    /** Frames the whole island again, and lets a later resize keep it framed. */
    fun frameIreland() {
        moved = false
        cameraState.value = Camera.covering(Ireland.EXTENT, aspectRatio.value)
    }

    /**
     * The canvas reports its shape, because that is what decides how wide the viewport is.
     *
     * Until the map has been panned or zoomed it is also reframed to hold the whole island —
     * which is what makes the opening view right on a tablet and after a rotation, not only on
     * the portrait phone the default assumes.
     */
    fun resizedTo(aspectRatio: Double) {
        this.aspectRatio.value = aspectRatio
        if (!moved) cameraState.value = Camera.covering(Ireland.EXTENT, aspectRatio)
    }

    private suspend fun load(view: View): MapState = try {
        val viewport = view.camera.viewport(view.aspectRatio)
        val found = places.inViewport(viewport, limit)
        MapState.Ready(viewport, found.results, found.command)
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
