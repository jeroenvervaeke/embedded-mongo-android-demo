package io.github.jeroenvervaeke.coffeefinder.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.jeroenvervaeke.coffeefinder.CoffeeFinderApplication
import io.github.jeroenvervaeke.coffeefinder.SEED_ASSET
import io.github.jeroenvervaeke.coffeefinder.data.PlaceRepository
import io.github.jeroenvervaeke.coffeefinder.data.finder.MapFinder
import io.github.jeroenvervaeke.coffeefinder.data.finder.NearbyFinder
import io.github.jeroenvervaeke.coffeefinder.data.model.Coordinates
import io.github.jeroenvervaeke.coffeefinder.data.seed.SeedProgress
import io.github.jeroenvervaeke.coffeefinder.data.seed.Seeder
import io.github.jeroenvervaeke.coffeefinder.location.DeviceLocation
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
    private val preparing = MutableStateFlow<Startup>(Startup.Preparing(SeedProgress.Checking))
    private val source = MutableStateFlow(LocationSource.ASKING)

    val startup: StateFlow<Startup> get() = preparing

    /** Why the list is measured from where it is, which the screen says out loud. */
    val locationSource: StateFlow<LocationSource> get() = source

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
        if (preparing.value is Startup.Failed) start()
    }

    private fun start() {
        preparing.value = Startup.Preparing(SeedProgress.Checking)
        viewModelScope.launch { prepare() }
    }

    /**
     * Asks the device where it is and measures from there.
     *
     * Called once the location permission has been decided, whichever way: a refusal is what puts
     * the screen on Dublin, and it should say so as soon as it knows.
     */
    fun locate() = viewModelScope.launch {
        val here = deviceLocation.current()
        if (here == null) {
            source.value = LocationSource.FALLBACK
            return@launch
        }
        // On a cold start the fix usually arrives before the seed has finished going in, so this
        // waits for the finders. It waits for a failure too: awaiting Ready alone would leave a
        // coroutine suspended for the life of the ViewModel on a database that never opened.
        val ready = startup.first { it !is Startup.Preparing } as? Startup.Ready ?: return@launch
        // A tap on the map during that wait is a choice; a fix that arrives afterwards is not,
        // and should not quietly replace it while the screen still says the tap is in effect.
        if (source.value == LocationSource.PICKED) return@launch
        ready.nearby.measureFrom(here)
        source.value = LocationSource.DEVICE
    }

    /** Whether the location permission is already granted, which decides whether to ask for it. */
    fun hasLocationPermission(): Boolean = deviceLocation.isPermitted()

    /** Measures from a point tapped on the map, which is the other way to ask "what is near here". */
    fun measureFrom(coordinates: Coordinates) {
        val ready = startup.value as? Startup.Ready ?: return
        ready.nearby.measureFrom(coordinates)
        source.value = LocationSource.PICKED
    }

    private suspend fun prepare() {
        try {
            val mongo = getApplication<CoffeeFinderApplication>().database.seam()
            Seeder(mongo).seed { getApplication<Application>().assets.open(SEED_ASSET) }
                .collect { progress -> preparing.value = Startup.Preparing(progress) }
            val places = PlaceRepository(mongo)
            preparing.value = Startup.Ready(
                nearby = NearbyFinder(places, viewModelScope),
                map = MapFinder(places, viewModelScope),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            preparing.value = Startup.Failed(failure.message ?: failure.javaClass.simpleName)
        }
    }
}
