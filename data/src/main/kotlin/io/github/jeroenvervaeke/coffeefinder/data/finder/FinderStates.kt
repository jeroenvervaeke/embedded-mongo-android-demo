package io.github.jeroenvervaeke.coffeefinder.data.finder

import io.github.jeroenvervaeke.coffeefinder.data.model.NearbyPlace
import io.github.jeroenvervaeke.coffeefinder.data.model.Place
import io.github.jeroenvervaeke.coffeefinder.data.model.Viewport
import kotlin.time.Duration
import org.bson.Document

/**
 * The list screen: the coffee places to show, and the command that found them.
 *
 * The command travels with the results rather than being rebuilt for display, so what the
 * pipeline screen shows is the document that crossed the bridge, which is the only version of
 * it worth showing.
 */
sealed interface NearbyState {
    /** Before the first reply, and while a changed query is in flight. */
    data object Searching : NearbyState

    data class Ready(
        val places: List<NearbyPlace>,
        val command: Document,
        /**
         * How long the engine took to answer, measured around the two queries and nothing else.
         *
         * Both of them, because both are on the screen: the `$count` behind the headline and the
         * capped list under it. Not the whole wait: the debounce in front of them is deliberately
         * outside this, because the question it exists to answer is what the engine costs, and
         * adding a constant to every reading would only hide it.
         */
        val took: Duration,
        /**
         * How many places match inside the radius, before the `$limit` [places] was capped with.
         *
         * The headline number, and the reason it is here rather than `places.size`: the list
         * stops at fifty documents and the radius does not.
         */
        val matching: Long,
    ) : NearbyState

    /** The engine refused the query, or answered something that would not parse. */
    data class Failed(val reason: String) : NearbyState
}

/** The map screen. [viewport] is what was asked for, so the drawing and the query cannot disagree. */
sealed interface MapState {
    data object Searching : MapState

    data class Ready(
        val viewport: Viewport,
        val places: List<Place>,
        val command: Document,
        /** What the `$geoWithin` behind this cost. See [NearbyState.Ready.took]. */
        val took: Duration,
    ) : MapState

    data class Failed(val reason: String) : MapState
}
