package io.github.jeroenvervaeke.coffeefinder.location

import io.github.jeroenvervaeke.coffeefinder.data.finder.NearbyFinder
import io.github.jeroenvervaeke.coffeefinder.data.model.Coordinates
import io.github.jeroenvervaeke.coffeefinder.ui.logLocation
import kotlin.time.Duration
import kotlin.time.TimeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Where the nearby query is measured from, and why.
 *
 * Plain Kotlin with a [CoroutineScope] handed to it rather than logic inside the `ViewModel`, for
 * the same reason as the finders: what is worth testing here (that a provider which never calls
 * back is given up on, that giving up says so instead of claiming the device has no location, that
 * a second ask cannot be overtaken by the first) is testable on the JVM with virtual time, and an
 * `AndroidViewModel` is not.
 *
 * Not thread-safe, and does not need to be: [locate] and [pick] are called from the UI thread and
 * [scope] is the one the screen runs in, so nothing here can interleave with the attempt it
 * launched. A scope on a dispatcher with more than one thread would need locking that is not here.
 */
class LocationOrigin(
    private val locator: Locator,
    private val scope: CoroutineScope,
    private val budget: Duration = LOCATION_BUDGET,
    /** Where the outcome of an attempt is reported. See [logLocation]. */
    private val report: (String, Duration) -> Unit = ::logLocation,
    /** The clock the attempt is measured against, injected for the same reason the finders' is. */
    private val clock: TimeSource = TimeSource.Monotonic,
) {
    private val state = MutableStateFlow(LocationSource.ASKING)
    private var attempt: Job? = null

    /** Why the list is measured from where it is, which the screen says out loud. */
    val source: StateFlow<LocationSource> get() = state

    /**
     * Asks the device where it is and measures from there.
     *
     * Called once the location permission has been decided, whichever way: a refusal is what puts
     * the screen on Dublin, and it should say so as soon as it knows.
     *
     * [nearby] is awaited rather than passed in because on a cold start the fix usually arrives
     * before the seed has finished going in. It answers `null` for a database that never opened,
     * which has to be an answer rather than a longer wait: awaiting a finder that will never exist
     * would strand this coroutine for the life of the process.
     */
    fun locate(nearby: suspend () -> NearbyFinder?) {
        // The newest ask wins. Giving up is answered by pressing the button again, so two attempts
        // in flight is now an ordinary thing rather than a stray double tap -- and left running,
        // the older one would land second, overwrite the newer fix with a staler one, and pay for
        // a second query to do it.
        attempt?.cancel()
        attempt = scope.launch {
            val started = clock.markNow()
            val fix = locator.fixWithin(budget)
            report(fix.describe(), started.elapsedNow())
            if (fix !is LocationFix.Known) {
                // Only a screen whose query really is on Dublin may be told it is on Dublin. The
                // button is pressed again after a fix has landed as well as before one has, and
                // a failure then changes nothing about where the list is measured from.
                if (state.value.measuresFromDublin) state.value = fix.source
                return@launch
            }
            val finder = nearby() ?: return@launch
            finder.measureFrom(fix.coordinates)
            state.value = fix.source
        }
    }

    /**
     * Measures from a point tapped on the map instead, and says so.
     *
     * The finder is moved here rather than by the caller, because [LocationSource.PICKED] is a
     * claim about where the query is measured from and the two must not be able to disagree: the
     * fallback labelling reads that claim and rewrites the screen on the strength of it.
     *
     * Cancelling is how a tap beats a fix that was already on its way. A guard on the state would
     * not do: it cannot tell a tap that landed during the attempt, which should win, from one made
     * before the user pressed the location button, which they have just overruled themselves.
     * Guarding on it left that button dead for the life of the process after any tap.
     */
    fun pick(finder: NearbyFinder, coordinates: Coordinates) {
        attempt?.cancel()
        finder.measureFrom(coordinates)
        state.value = LocationSource.PICKED
    }
}

/** What to call this outcome in a log line. */
private fun LocationFix.describe(): String = when (this) {
    is LocationFix.Known -> "fixed at ${coordinates.longitude}, ${coordinates.latitude}"
    LocationFix.Unavailable -> "the device does not know where it is"
    LocationFix.GaveUp -> "gave up waiting"
}
