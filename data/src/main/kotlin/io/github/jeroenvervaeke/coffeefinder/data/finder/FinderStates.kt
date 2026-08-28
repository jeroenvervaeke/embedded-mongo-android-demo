package io.github.jeroenvervaeke.coffeefinder.data.finder

import io.github.jeroenvervaeke.coffeefinder.data.model.NearbyPlace
import io.github.jeroenvervaeke.coffeefinder.data.model.Place
import io.github.jeroenvervaeke.coffeefinder.data.model.Viewport
import org.bson.Document

/**
 * The list screen: the coffee places to show, and the command that found them.
 *
 * The command travels with the results rather than being rebuilt for display, so what the
 * pipeline screen shows is the document that crossed the bridge — which is the only version of
 * it worth showing.
 */
sealed interface NearbyState {
    /** Before the first reply, and while a changed query is in flight. */
    data object Searching : NearbyState

    data class Ready(val places: List<NearbyPlace>, val command: Document) : NearbyState

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
    ) : MapState

    data class Failed(val reason: String) : MapState
}
