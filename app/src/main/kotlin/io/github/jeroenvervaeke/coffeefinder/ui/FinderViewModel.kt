package io.github.jeroenvervaeke.coffeefinder.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.jeroenvervaeke.coffeefinder.CoffeeFinderApplication
import io.github.jeroenvervaeke.coffeefinder.SEED_ASSET
import io.github.jeroenvervaeke.coffeefinder.data.PlaceRepository
import io.github.jeroenvervaeke.coffeefinder.data.finder.MapFinder
import io.github.jeroenvervaeke.coffeefinder.data.finder.MapState
import io.github.jeroenvervaeke.coffeefinder.data.finder.NearbyFinder
import io.github.jeroenvervaeke.coffeefinder.data.finder.NearbyState
import io.github.jeroenvervaeke.coffeefinder.data.model.Coordinates
import io.github.jeroenvervaeke.coffeefinder.data.seed.SeedProgress
import io.github.jeroenvervaeke.coffeefinder.data.seed.Seeder
import io.github.jeroenvervaeke.coffeefinder.location.DeviceLocation
import io.github.jeroenvervaeke.coffeefinder.location.LocationOrigin
import io.github.jeroenvervaeke.coffeefinder.location.LocationSource
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Opens the database, seeds it, and hands the screens the two finders that query it.
 *
 * Almost nothing happens here: the finders are `:data` classes given [viewModelScope] to run in,
 * so what this class contributes is the Android part — the assets the seed comes from, and the
 * device location the queries are measured from.
 */
class FinderViewModel(application: Application) : AndroidViewModel(application) {
    private val deviceLocation = DeviceLocation(application)
    private val origin = LocationOrigin(deviceLocation, viewModelScope)
    private val preparing = MutableStateFlow<Startup>(Startup.Preparing(SeedProgress.Checking))

    val startup: StateFlow<Startup> get() = preparing

    /** Why the list is measured from where it is, which the screen says out loud. */
    val locationSource: StateFlow<LocationSource> get() = origin.source

    init {
        start()
    }

    /**
     * Tries again after a failed start.
     *
     * The scope the engine opens in is a supervisor precisely so that a failure leaves it usable,
     * and that is only worth anything if something reaches this. A device that was full when the
     * application first started recovers by freeing space and tapping retry, rather than by being
     * killed.
     */
    fun retry() {
        if (preparing.value !is Startup.Failed) return
        start()
        // And ask again where the device is. The attempt made during the failed start found no
        // finder to hand its fix to and stopped, so without this the recovered screen sits on
        // "Finding you..." with nothing looking -- which is the state this application stopped
        // tolerating everywhere else.
        locate()
    }

    private fun start() {
        preparing.value = Startup.Preparing(SeedProgress.Checking)
        viewModelScope.launch { prepare() }
    }

    /** Asks the device where it is. What that means is [LocationOrigin]'s. */
    fun locate() = origin.locate {
        // Waits for a failure as well as for a success: awaiting Ready alone would leave a
        // coroutine suspended for the life of the ViewModel on a database that never opened.
        (startup.first { it !is Startup.Preparing } as? Startup.Ready)?.nearby
    }

    /** Whether the location permission is already granted, which decides whether to ask for it. */
    fun hasLocationPermission(): Boolean = deviceLocation.isPermitted()

    /** Measures from a point tapped on the map, which is the other way to ask "what is near here". */
    fun measureFrom(coordinates: Coordinates) {
        val ready = startup.value as? Startup.Ready ?: return
        origin.pick(ready.nearby, coordinates)
    }

    private suspend fun prepare() {
        try {
            // Constructed before the database is asked for, so that opening the engine is inside
            // what it measures rather than in front of it.
            val startup = StartupTimer()
            val mongo = getApplication<CoffeeFinderApplication>().database.seam()
            Seeder(mongo).seed { getApplication<Application>().assets.open(SEED_ASSET) }
                .collect { progress ->
                    preparing.value = Startup.Preparing(progress)
                    startup.reached(progress)
                }
            val places = PlaceRepository(mongo)
            val ready = Startup.Ready(
                nearby = NearbyFinder(places, viewModelScope),
                map = MapFinder(places, viewModelScope),
            )
            preparing.value = ready
            reportQueriesOf(ready)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            preparing.value = Startup.Failed(failure.message ?: failure.javaClass.simpleName)
        }
    }

    /**
     * Prints what every finished query cost.
     *
     * Collected here rather than from a screen: a query runs whether or not the screen that asked
     * for it is on top, and a collector tied to a composition would miss exactly the ones that ran
     * while it was not — which on the map is most of a gesture.
     */
    private fun reportQueriesOf(ready: Startup.Ready) {
        viewModelScope.launch {
            ready.nearby.state.collect { if (it is NearbyState.Ready) logQuery("nearby", it.places.size, it.took) }
        }
        viewModelScope.launch {
            ready.map.state.collect { if (it is MapState.Ready) logQuery("map", it.places.size, it.took) }
        }
    }
}
