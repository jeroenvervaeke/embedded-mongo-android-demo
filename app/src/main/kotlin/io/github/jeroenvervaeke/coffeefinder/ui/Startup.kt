package io.github.jeroenvervaeke.coffeefinder.ui

import io.github.jeroenvervaeke.coffeefinder.data.PlaceRepository
import io.github.jeroenvervaeke.coffeefinder.data.explorer.PipelineExplorer
import io.github.jeroenvervaeke.coffeefinder.data.finder.MapFinder
import io.github.jeroenvervaeke.coffeefinder.data.finder.NearbyFinder
import io.github.jeroenvervaeke.coffeefinder.data.seed.SeedProgress

/**
 * Getting the database ready, which on a cold start is the whole application for a few seconds.
 *
 * Nothing that queries exists until there is something to query, so the finders, the explorer and
 * the repository behind them are all carried by [Ready] rather than being nullable properties
 * every screen would have to check.
 */
sealed interface Startup {
    data class Preparing(val progress: SeedProgress) : Startup

    data class Ready(
        val nearby: NearbyFinder,
        val map: MapFinder,
        /** What the explorer screen runs whatever somebody typed against. */
        val explorer: PipelineExplorer,
        /** The about screen counts the collection with this; the finders query through it. */
        val places: PlaceRepository,
        /** What this launch cost, phase by phase, for the about screen to show. */
        val startup: List<StartupPhase>,
    ) : Startup

    data class Failed(val reason: String) : Startup
}
