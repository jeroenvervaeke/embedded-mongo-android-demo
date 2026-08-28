package io.github.jeroenvervaeke.coffeefinder.engine

import android.content.Context
import io.github.jeroenvervaeke.coffeefinder.data.MongoSeam
import io.github.jeroenvervaeke.embeddedmongodb.EmbeddedMongo
import java.io.File
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The one database this process has, opened once and kept for as long as the application runs.
 *
 * The engine refuses a second runtime in a process, so opening is behind a mutex rather than left
 * to whichever screen asks first. Its directory is named here and excluded from backup in the
 * manifest by the same name — a restored WiredTiger directory is corrupt, not migrated.
 *
 * The engine's storage limits are left at their defaults, and one of them is a known limitation
 * here: MongoDB will not start an index build with less than 500 MB free. That is a server's
 * number, and this database is about 10 MB — so a phone near its limit can open the database and
 * never finish seeding it, failing `createIndexes` with `OutOfDiskSpace` on every launch. The
 * startup screen reports that rather than the application avoiding it. Naming a floor sized for
 * this data is the fix, and wants a `StorageOptions` the library's `master` does not yet carry.
 */
class CoffeeDatabase(private val context: Context) {
    private val opening = Mutex()
    private var opened: EmbeddedMongo? = null

    /**
     * Opens the database if it is not open yet and returns the seam onto it.
     *
     * Suspends: `EmbeddedMongo.open` refuses to run on the main thread, and dispatches itself off
     * whichever thread calls it.
     */
    suspend fun seam(): MongoSeam = EmbeddedMongoSeam(database())

    /**
     * Closes the database, waiting for an open in flight rather than racing it.
     *
     * Under the same lock as opening: without it, a close that overlapped an open would leave
     * `opened` holding a database nobody closed, or close one a caller was about to be handed.
     */
    suspend fun close() = opening.withLock {
        opened?.close()
        opened = null
    }

    private suspend fun database(): EmbeddedMongo = opening.withLock {
        opened ?: EmbeddedMongo.open(context, File(context.filesDir, DIRECTORY)).also { opened = it }
    }
}

/**
 * Relative to `filesDir`, and the same name the backup exclusion rules use. Changing one without
 * the other starts backing the database up again.
 */
private const val DIRECTORY = "coffee"
