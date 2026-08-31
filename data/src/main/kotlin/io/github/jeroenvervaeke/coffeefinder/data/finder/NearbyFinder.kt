package io.github.jeroenvervaeke.coffeefinder.data.finder

import io.github.jeroenvervaeke.coffeefinder.data.PlaceRepository
import io.github.jeroenvervaeke.coffeefinder.data.model.Confidence
import io.github.jeroenvervaeke.coffeefinder.data.model.Coordinates
import io.github.jeroenvervaeke.coffeefinder.data.model.Ireland
import io.github.jeroenvervaeke.coffeefinder.data.model.Metres
import io.github.jeroenvervaeke.coffeefinder.data.model.PlaceCategory
import io.github.jeroenvervaeke.coffeefinder.data.query.PlaceCriteria
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.TimeSource
import kotlin.time.measureTimedValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/**
 * The console screen's state: what the user asked for, in, and coffee places out.
 *
 * Plain Kotlin with a [CoroutineScope] handed to it rather than an Android `ViewModel`, so the
 * behaviour that is actually worth testing (which query an empty search box runs, that dragging
 * a radius does not run one query per pixel, that a failed query becomes a message rather than a
 * crash) is tested on the JVM with virtual time.
 *
 * Two queries answer one screen: the `$count` behind the headline and the capped list under it.
 * They are built from the same request, so the number and the rows can never be answers to
 * different questions.
 */
class NearbyFinder(
    private val places: PlaceRepository,
    scope: CoroutineScope,
    private val limit: Int = DEFAULT_LIMIT,
    /** Where [NearbyState.Ready.took] is read from. See [MapFinder]'s for why it is injected. */
    private val clock: TimeSource = TimeSource.Monotonic,
) {
    private val request = MutableStateFlow(Request(origin = Ireland.DUBLIN))

    /** What the user is asking, which is also what the screen shows in its controls. */
    val asked: StateFlow<Request> get() = request

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val state: StateFlow<NearbyState> = request
        // Typing and dragging a radius both arrive many times a second, and a query apiece is a
        // query apiece the engine has to finish before the useful one starts.
        .debounce(SETTLE_MILLIS)
        .mapLatest(::load)
        .stateIn(scope, SharingStarted.Eagerly, NearbyState.Searching)

    fun searchFor(text: String) = request.update { it.copy(text = text) }

    /** Called when the device reports a position, and when a tap on the map drops the pin. */
    fun measureFrom(origin: Coordinates) = request.update { it.copy(origin = origin) }

    fun filterBy(category: PlaceCategory?) =
        request.update { it.copy(criteria = it.criteria.copy(category = category)) }

    /** How far the headline counts, which is the radius drawn on the map. */
    fun within(radius: Metres) = request.update { it.copy(radius = radius) }

    /** Keeps only what Overture is at least this sure about, or drops the floor when `null`. */
    fun requireConfidence(floor: Confidence?) =
        request.update { it.copy(criteria = it.criteria.copy(minimumConfidence = floor)) }

    /** Keeps only the places that carry a chain. */
    fun requireBrand(branded: Boolean) =
        request.update { it.copy(criteria = it.criteria.copy(brandedOnly = branded)) }

    /**
     * Whether the list is capped at [limit] documents.
     *
     * The `$limit` is a stage a person can switch off on the screen, because seeing the cap in
     * the pipeline and then seeing what it costs to lift it is the point of showing the pipeline.
     */
    fun capResults(capped: Boolean) = request.update { it.copy(capped = capped) }

    private suspend fun load(request: Request): NearbyState = try {
        val text = request.text.trim()
        val cap = request.limit(limit)
        val (found, took) = clock.measureTimedValue {
            val matching = places.count(request.origin, request.radius, request.criteria, text)
            val results = if (text.isEmpty()) {
                places.nearest(request.origin, cap, request.radius, request.criteria)
            } else {
                places.search(text, request.origin, cap, request.criteria, request.radius)
            }
            matching to results
        }
        val (matching, results) = found
        NearbyState.Ready(results.results, results.command, took, matching)
    } catch (cancelled: CancellationException) {
        // A superseded query, cancelled by mapLatest. Reporting it would replace the results of
        // the query that superseded it with an error nobody caused.
        throw cancelled
    } catch (failure: Exception) {
        NearbyState.Failed(failure.message ?: failure.javaClass.simpleName)
    }

    /** Everything the screen is asking for, so one change cannot lose another. */
    data class Request(
        val origin: Coordinates,
        val text: String = "",
        val radius: Metres = DEFAULT_RADIUS,
        val criteria: PlaceCriteria = PlaceCriteria.NONE,
        val capped: Boolean = true,
    ) {
        /** The `$limit` this request wants, which is none at all when the stage is switched off. */
        internal fun limit(cap: Int): Int? = cap.takeIf { capped }
    }

    companion object {
        /** What the map opens on: a walk, and the radius the headline counts inside. */
        val DEFAULT_RADIUS = Metres(1_000.0)
    }
}

/** Long enough to swallow a keystroke or a drag, short enough that a pause feels immediate. */
private const val SETTLE_MILLIS = 250L

/** More than a phone screen holds, so scrolling does not run into the end of the list. */
private const val DEFAULT_LIMIT = 50
