package io.github.jeroenvervaeke.coffeefinder.ui

import io.github.jeroenvervaeke.coffeefinder.data.finder.MapFinder
import io.github.jeroenvervaeke.coffeefinder.data.finder.NearbyFinder
import io.github.jeroenvervaeke.coffeefinder.data.seed.SeedProgress

/**
 * Getting the database ready, which on a cold start is the whole application for a few seconds.
 *
 * The two finders only exist once there is something to query, so they are carried by [Ready]
 * rather than being nullable properties every screen would have to check.
 */
sealed interface Startup {
    data class Preparing(val progress: SeedProgress) : Startup

    data class Ready(val nearby: NearbyFinder, val map: MapFinder) : Startup

    data class Failed(val reason: String) : Startup
}
