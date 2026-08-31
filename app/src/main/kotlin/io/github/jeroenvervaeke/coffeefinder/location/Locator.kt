package io.github.jeroenvervaeke.coffeefinder.location

import io.github.jeroenvervaeke.coffeefinder.data.model.Coordinates
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Something that can be asked where the device is, and may never answer.
 *
 * An interface rather than [DeviceLocation] itself, because everything worth testing about asking
 * (how long to wait, what to say when the wait runs out, what a second ask does to the first)
 * sits above it, and none of that can be exercised against Play services on the JVM.
 */
fun interface Locator {
    /**
     * Where the device is, or `null` when it will not say.
     *
     * `null` covers every way that happens (permission refused, location switched off, no fix,
     * no Play services), because they all lead to the same place: measure from Dublin and say so.
     *
     * Suspends until the provider calls back, which on a real phone is sometimes never. Callers
     * want [fixWithin] rather than this.
     */
    suspend fun fix(): Coordinates?
}

/** What one attempt at locating the device produced. */
sealed interface LocationFix {
    /** What the screen says about a query measured this way. */
    val source: LocationSource

    data class Known(val coordinates: Coordinates) : LocationFix {
        override val source get() = LocationSource.DEVICE
    }

    /** The device answered that it does not know where it is. */
    data object Unavailable : LocationFix {
        override val source get() = LocationSource.FALLBACK
    }

    /** It did not answer at all, and the wait was called off. */
    data object GaveUp : LocationFix {
        override val source get() = LocationSource.TIMED_OUT
    }
}

/**
 * Asks where the device is and stops waiting after [budget].
 *
 * Cancelling is part of giving up, not merely how it is spelled: the timeout cancels this
 * coroutine, [DeviceLocation] turns that into a cancelled `CancellationToken`, and Play services
 * drops a location request nobody will ever read the answer to. Cancellation also makes a late
 * answer harmless (it resumes a continuation that is already dead, which
 * `suspendCancellableCoroutine` discards rather than delivering), so no fix can arrive after this
 * has reported giving up.
 *
 * A coroutine timeout rather than `CurrentLocationRequest.setDurationMillis`, because the
 * component that has to honour that duration is the one that misbehaved. This bound holds even if
 * Play services never speaks again.
 */
suspend fun Locator.fixWithin(budget: Duration): LocationFix =
    withTimeoutOrNull(budget) { fix()?.let(LocationFix::Known) ?: LocationFix.Unavailable }
        ?: LocationFix.GaveUp

/**
 * How long to wait for a fix before measuring from Dublin instead.
 *
 * There is no bound without one. `getCurrentLocation(priority, token)` builds a
 * `CurrentLocationRequest` whose `durationMillis` is left at `Long.MAX_VALUE` (read out of the
 * bytecode of play-services-location 21.3.0 rather than assumed), so nothing in the request ever
 * ends the wait, and on a Galaxy S23 Ultra it twice did not end at all.
 *
 * Ten seconds is several times over what a coarse network fix costs when it works, and it is
 * about as long as anyone will believe a screen that says "Finding you…" is still working. It can
 * afford to be generous: the list is already answering from Dublin while this runs, so the only
 * thing the wait holds up is the label, and it can afford not to be more generous than this,
 * because giving up is one tap away from asking again.
 */
val LOCATION_BUDGET: Duration = 10.seconds
