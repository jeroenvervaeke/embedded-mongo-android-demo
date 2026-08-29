package io.github.jeroenvervaeke.coffeefinder.data.finder

import io.github.jeroenvervaeke.coffeefinder.data.PlaceRepository
import io.github.jeroenvervaeke.coffeefinder.data.model.Coordinates
import io.github.jeroenvervaeke.coffeefinder.data.model.Ireland
import io.github.jeroenvervaeke.coffeefinder.data.model.Metres
import io.github.jeroenvervaeke.coffeefinder.data.model.PlaceCategory
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
 * The list screen's state: what the user asked for, in, and coffee places out.
 *
 * Plain Kotlin with a [CoroutineScope] handed to it rather than an Android `ViewModel`, so the
 * behaviour that is actually worth testing — which query an empty search box runs, that typing
 * does not run one query per keystroke, that a failed query becomes a message rather than a crash
 * — is tested on the JVM with virtual time.
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
        // Typing is the only input that arrives per keystroke, and a query per keystroke is a
        // query per keystroke the engine has to finish before the useful one starts.
        .debounce(SETTLE_MILLIS)
        .mapLatest(::load)
        .stateIn(scope, SharingStarted.Eagerly, NearbyState.Searching)

    fun searchFor(text: String) = request.update { it.copy(text = text) }

    /** Called when the device reports a position, and when a tap on the map picks one. */
    fun measureFrom(origin: Coordinates) = request.update { it.copy(origin = origin) }

    fun filterBy(category: PlaceCategory?) = request.update { it.copy(category = category) }

    /** Caps how far a result may be, or lifts the cap when [within] is `null`. */
    fun limitTo(within: Metres?) = request.update { it.copy(maxDistance = within) }

    private suspend fun load(request: Request): NearbyState = try {
        val text = request.text.trim()
        val (found, took) = clock.measureTimedValue {
            if (text.isEmpty()) {
                places.nearest(request.origin, limit, request.maxDistance, request.category)
            } else {
                places.search(text, request.origin, limit, request.category, request.maxDistance)
            }
        }
        NearbyState.Ready(found.results, found.command, took)
    } catch (cancelled: CancellationException) {
        // A superseded query, cancelled by mapLatest. Reporting it would replace the results of
        // the query that superseded it with an error nobody caused.
        throw cancelled
    } catch (failure: Exception) {
        NearbyState.Failed(failure.message ?: failure.javaClass.simpleName)
    }

    /** Everything the list screen is asking for, so one change cannot lose another. */
    data class Request(
        val origin: Coordinates,
        val text: String = "",
        val category: PlaceCategory? = null,
        val maxDistance: Metres? = null,
    )
}

/** Long enough to swallow a keystroke, short enough that a deliberate pause feels immediate. */
private const val SETTLE_MILLIS = 250L

/** More than a phone screen holds, so scrolling does not run into the end of the list. */
private const val DEFAULT_LIMIT = 50
