package io.github.jeroenvervaeke.coffeefinder

import android.app.Application
import io.github.jeroenvervaeke.coffeefinder.engine.CoffeeDatabase

/**
 * Holds the database, because the engine allows one per process and a `ViewModel` is per screen.
 *
 * It is never closed: Android ends the process rather than calling anything on the way out, and
 * an embedded database whose last write was journalled has nothing to flush. `close` exists on
 * [CoffeeDatabase] for a caller that has a reason; this application does not.
 */
class CoffeeFinderApplication : Application() {
    val database: CoffeeDatabase by lazy { CoffeeDatabase(this) }
}
