package io.github.jeroenvervaeke.coffeefinder.engine

import io.github.jeroenvervaeke.coffeefinder.data.query.COFFEE_DATABASE
import io.github.jeroenvervaeke.embeddedmongodb.FreeDiskFloor
import io.github.jeroenvervaeke.embeddedmongodb.MongoDatabase
import io.github.jeroenvervaeke.embeddedmongodb.StorageOptions
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.job
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.bson.Document

class CoffeeDatabaseTest {
    @Test
    fun `the floor it asks for is sized to this database rather than to a server`() = runTest {
        val opener = RecordingOpener()

        CoffeeDatabase(DIRECTORY, engineScope(), opener).mongo()

        assertEquals(FreeDiskFloor.ofMebibytes(64), opener.options.single().freeDiskFloor)
    }

    @Test
    fun `the floor is a lowering of the engine's, with room for what is about to be written`() {
        val floor = requireNotNull(COFFEE_STORAGE.freeDiskFloor)

        // Below MongoDB's own, which is what makes a phone near its limit able to seed at all.
        assertTrue(floor.mebibytes < FreeDiskFloor.ENGINE_DEFAULT.mebibytes, "${floor.mebibytes} MiB")
        // And far enough above what the finished directory costs that the build has room. The
        // floor stops a build that would start with too little; nothing stops one that runs out.
        assertTrue(floor.mebibytes >= DIRECTORY_MEBIBYTES * 5, "${floor.mebibytes} MiB")
    }

    @Test
    fun `nothing else about the engine's storage is second-guessed`() {
        assertNull(COFFEE_STORAGE.cacheSize)
        assertNull(COFFEE_STORAGE.journalFileSize)
        assertNull(COFFEE_STORAGE.journalPreallocation)
    }

    @Test
    fun `the engine is opened once however many screens ask for it`() = runTest {
        val opener = RecordingOpener()
        val database = CoffeeDatabase(DIRECTORY, engineScope(), opener)

        val first = database.mongo()
        val second = database.mongo()

        assertEquals(1, opener.opens)
        assertSame(first, second)
    }

    @Test
    fun `a screen that leaves while the engine is starting does not throw the engine away`() =
        runTest {
            // The ordinary Android case: the first ask comes from a ViewModel, and the ViewModel
            // dies with the screen. Tied to that scope, the open is discarded and the next screen
            // pays for a second cold start.
            val opener = RecordingOpener(finishImmediately = false)
            val database = CoffeeDatabase(DIRECTORY, engineScope(), opener)
            val screen = launch { database.mongo() }
            opener.started.await()

            screen.cancel()
            // Let the cancelled caller run its failure path before asking again. Without this the
            // catch has not been scheduled yet, and the test passes whatever that path does --
            // which is how a bug that cleared the cached open on success survived it.
            runCurrent()
            opener.finish()
            val next = database.mongo()

            assertEquals(1, opener.opens)
            assertSame(opener.opened.single().mongo, next)
        }

    @Test
    fun `an open that failed is not remembered as the answer for every later screen`() = runTest {
        val opener = RecordingOpener(failWith = IOException("no room"))
        val database = CoffeeDatabase(DIRECTORY, engineScope(), opener)
        assertFailsWith<IOException> { database.mongo() }

        opener.failWith = null
        val mongo = database.mongo()

        assertEquals(2, opener.opens)
        assertSame(opener.opened.single().mongo, mongo)
    }

    @Test
    fun `closing waits for the open it is racing and closes what that produced`() = runTest {
        val opener = RecordingOpener(finishImmediately = false)
        val database = CoffeeDatabase(DIRECTORY, engineScope(), opener)
        val screen = launch { database.mongo() }
        opener.started.await()

        screen.cancel()
        opener.finish()
        database.close()

        assertEquals(listOf(true), opener.opened.map { it.closed })
    }

    @Test
    fun `closing a database that was never opened does nothing`() = runTest {
        val opener = RecordingOpener()

        CoffeeDatabase(DIRECTORY, engineScope(), opener).close()

        assertEquals(0, opener.opens)
    }
}

/**
 * The scope the application opens the engine in, as the application builds it.
 *
 * A supervisor, and that matters rather than being a detail: under an ordinary Job the first
 * failed open would cancel the scope, and every later attempt would fail on a dead scope instead
 * of being allowed to try again. `CoffeeFinderApplication` uses a `SupervisorJob` for that
 * reason, so a test that used anything else would be testing a different application.
 */
private fun TestScope.engineScope(): CoroutineScope =
    CoroutineScope(backgroundScope.coroutineContext + SupervisorJob(backgroundScope.coroutineContext.job))

/** Stands in for the engine, which no unit test can start. */
private class RecordingOpener(
    finishImmediately: Boolean = true,
    var failWith: Exception? = null,
) : DatabaseOpener {
    val started = CompletableDeferred<Unit>()
    private val gate = CompletableDeferred<Unit>()
    private val requested = mutableListOf<StorageOptions>()
    private val produced = mutableListOf<FakeOpenDatabase>()

    val options: List<StorageOptions> get() = requested
    val opened: List<FakeOpenDatabase> get() = produced
    val opens: Int get() = requested.size

    init {
        if (finishImmediately) gate.complete(Unit)
    }

    override suspend fun open(directory: File, options: StorageOptions): OpenDatabase {
        requested += options
        started.complete(Unit)
        gate.await()
        failWith?.let { throw it }
        return FakeOpenDatabase().also { produced += it }
    }

    fun finish() {
        gate.complete(Unit)
    }
}

private class FakeOpenDatabase : OpenDatabase {
    var closed = false
        private set

    override val mongo = MongoDatabase({ _, _ -> Document("ok", 1.0) }, COFFEE_DATABASE)

    override fun close() {
        closed = true
    }
}

private val DIRECTORY = File("build/tmp/coffee-database-test")

/** What an Ireland-scale directory costs on disk, documents, indexes and journal together. */
private const val DIRECTORY_MEBIBYTES = 11
