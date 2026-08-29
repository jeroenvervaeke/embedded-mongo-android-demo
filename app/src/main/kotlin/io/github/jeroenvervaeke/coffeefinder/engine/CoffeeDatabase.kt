package io.github.jeroenvervaeke.coffeefinder.engine

import io.github.jeroenvervaeke.coffeefinder.data.MongoSeam
import io.github.jeroenvervaeke.embeddedmongodb.FreeDiskFloor
import io.github.jeroenvervaeke.embeddedmongodb.StorageOptions
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The one database this process has, opened once and kept for as long as the application runs.
 *
 * The engine allows one runtime per process, so opening is behind a mutex rather than left to
 * whichever screen asks first. Its directory is named here and excluded from backup in the
 * manifest by the same name — a restored WiredTiger directory is corrupt, not migrated.
 *
 * The open runs in [scope], which belongs to the application, rather than in the coroutine that
 * happened to ask first. That is deliberate: the first ask comes from a `ViewModel`, whose scope
 * dies when the screen does, and leaving a screen while the engine is starting is an ordinary
 * thing to do. Tied to the caller, that cancellation throws away an engine that came up anyway —
 * the library closes it rather than stranding it, but the next screen then pays for a second
 * cold start. Held here, the open finishes and the next screen is handed the result.
 */
class CoffeeDatabase(
    private val directory: File,
    private val scope: CoroutineScope,
    private val opener: DatabaseOpener,
) {
    private val lock = Mutex()
    private var starting: Deferred<OpenDatabase>? = null

    /** Opens the database if it is not open yet and returns the seam onto it. */
    suspend fun seam(): MongoSeam = database().seam

    /**
     * Closes the database, waiting for an open in flight rather than racing it.
     *
     * Never called by this application — Android ends the process instead, and a journalled write
     * has nothing left to flush — but a database that cannot be closed is a database that cannot
     * be reopened, so it is here and it is correct.
     */
    suspend fun close() = withContext(NonCancellable) {
        val opening = lock.withLock { starting.also { starting = null } }
        // An open that failed, or one whose scope went away, produced no database to close. The
        // swallow is safe under NonCancellable: there is no caller cancellation to lose.
        opening?.runCatching { await() }?.getOrNull()?.close()
        Unit
    }

    // getCompletionExceptionOrNull is the only way to ask a Deferred whether it *failed* rather
    // than merely finished, and it is still marked experimental.
    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun database(): OpenDatabase {
        val opening = lock.withLock {
            starting ?: scope.async { opener.open(directory, COFFEE_STORAGE) }.also { starting = it }
        }
        return try {
            opening.await()
        } catch (failure: Throwable) {
            // Only an open that actually failed is forgotten. `isCompleted` is not that question:
            // it is also true of an open that *succeeded* and whose caller merely lost the race to
            // a cancellation, and forgetting one of those strands a running engine nobody holds --
            // the next open is refused as a second runtime, and every screen fails until the
            // process dies. NonCancellable because the caller is usually already cancelled here.
            // Both halves are needed: getCompletionExceptionOrNull throws on a job still in
            // flight, which is exactly the state a cancelled caller leaves behind.
            if (opening.isCompleted && opening.getCompletionExceptionOrNull() != null) {
                withContext(NonCancellable) { lock.withLock { if (starting === opening) starting = null } }
            }
            throw failure
        }
    }
}

/**
 * Relative to `filesDir`, and the same name the backup exclusion rules use. Changing one without
 * the other starts backing the database up again.
 */
const val COFFEE_DIRECTORY = "coffee"

/**
 * The one storage limit this application knows better than the library does.
 *
 * MongoDB will not start an index build with less than 500 MB free, and this application's cold
 * start is a bulk insert followed by two index builds. That default is sized for a server: an
 * Ireland-scale directory here holds about 2.25 MiB of documents and indexes and occupies about
 * 10.25 MiB with its journal, so a phone with 400 MB free could open the database and never
 * finish seeding it — `createIndexes` failing with `OutOfDiskSpace` on every launch.
 *
 * 64 MiB is about six times that, which is the shape the library asks for: lower it to what the
 * work about to be done actually needs, not to what will fit. It is deliberately not lower.
 *
 * The 10.25 MiB is the library's own figure for an Ireland-scale directory, quoted rather than
 * measured here — no engine has run against this application yet. It is the right order and the
 * margin is generous, but the number to trust is one taken from this database once it exists. The floor is a pre-flight check and the only warning there is — nothing stops a build
 * that runs out part-way, and WiredTiger answers a genuinely full disk by aborting the process,
 * with no exception to catch. The margin is the whole of the protection.
 *
 * Naming it also lowers the check the library makes before the engine is opened at all, from
 * 256 MiB to this, which is the intended pairing: an application that says 64 MiB is enough
 * should not be refused at 256.
 *
 * Nothing else is second-guessed. The cache is a ceiling WiredTiger grows into rather than memory
 * it takes, and this database is far too small to reach it.
 */
internal val COFFEE_STORAGE = StorageOptions(freeDiskFloor = FreeDiskFloor.ofMebibytes(64))
