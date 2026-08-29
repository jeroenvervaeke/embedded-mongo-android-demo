package io.github.jeroenvervaeke.coffeefinder.location

import io.github.jeroenvervaeke.coffeefinder.data.finder.NearbyFinder
import kotlin.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Where the nearby query is measured from, and why.
 *
 * Plain Kotlin with a [CoroutineScope] handed to it rather than logic inside the `ViewModel`, for
 * the same reason as the finders: what is worth testing here — that a provider which never calls
 * back is given up on, that giving up says so instead of claiming the device has no location, that
 * a second ask cannot be overtaken by the first — is testable on the JVM with virtual time, and an
 * `AndroidViewModel` is not.
 */
class LocationOrigin(
    private val locator: Locator,
    private val scope: CoroutineScope,
    private val budget: Duration = LOCATION_BUDGET,
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
            val fix = locator.fixWithin(budget)
            if (fix !is LocationFix.Known) {
                // Only a screen whose query really is on Dublin may be told it is on Dublin. The
                // button is pressed again after a fix has landed as well as before one has, and
                // a failure then changes nothing about where the list is measured from.
                if (state.value.measuresFromDublin) state.value = fix.source
                return@launch
            }
            val finder = nearby() ?: return@launch
            // A tap on the map during that wait is a choice; a fix that arrives afterwards is not,
            // and should not quietly replace it while the screen still says the tap is in effect.
            if (state.value == LocationSource.PICKED) return@launch
            finder.measureFrom(fix.coordinates)
            state.value = fix.source
        }
    }

    /** Records that the origin is now a point the user tapped on the map. */
    fun picked() {
        state.value = LocationSource.PICKED
    }
}
