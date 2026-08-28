package io.github.jeroenvervaeke.coffeefinder

import android.app.Application
import io.github.jeroenvervaeke.coffeefinder.engine.COFFEE_DIRECTORY
import io.github.jeroenvervaeke.coffeefinder.engine.CoffeeDatabase
import io.github.jeroenvervaeke.coffeefinder.engine.EmbeddedMongoOpener
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Holds the database, because the engine allows one per process and a `ViewModel` is per screen.
 *
 * It is never closed: Android ends the process rather than calling anything on the way out, and
 * an embedded database whose last write was journalled has nothing to flush.
 */
class CoffeeFinderApplication : Application() {
    /**
     * The scope the engine is opened in. It belongs to the application rather than to a screen,
     * because the database outlives every screen and so must the work of starting it.
     *
     * A `SupervisorJob` rather than a plain one, and not as a formality: an open that fails would
     * otherwise cancel this scope on its way out, and every later attempt would fail on a dead
     * scope rather than being allowed to try again. A device that was full when the application
     * first started would never recover without being killed.
     */
    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: CoffeeDatabase by lazy {
        CoffeeDatabase(File(filesDir, COFFEE_DIRECTORY), engineScope, EmbeddedMongoOpener(this))
    }
}
