package io.github.jeroenvervaeke.coffeefinder.data.seed

import io.github.jeroenvervaeke.coffeefinder.data.FakeMongo
import io.github.jeroenvervaeke.coffeefinder.data.namedDispatcher
import io.github.jeroenvervaeke.coffeefinder.data.okReply
import io.github.jeroenvervaeke.coffeefinder.data.placeDocument
import io.github.jeroenvervaeke.coffeefinder.data.query.SEED_MARKER_ID
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.GZIPOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.bson.Document

class SeederTest {
    @Test
    fun `an empty database is filled, then indexed, then reported ready`() = runTest {
        val database = EmptyDatabase()

        val progress = Seeder(database.mongo, batchSize = 2, readOn = StandardTestDispatcher(testScheduler)).seed(gzipped(3)).toList()

        assertEquals(
            listOf(
                SeedProgress.Checking,
                SeedProgress.Inserting(0),
                SeedProgress.Inserting(2),
                SeedProgress.Inserting(3),
                SeedProgress.Indexing,
                SeedProgress.Ready(3),
            ),
            progress,
        )
    }

    @Test
    fun `the indexes are built after the documents rather than in front of them`() = runTest {
        val database = EmptyDatabase()

        Seeder(database.mongo, batchSize = 2, readOn = StandardTestDispatcher(testScheduler)).seed(gzipped(3)).toList()

        val names = database.mongo.commands.map { it.keys.first() }
        assertTrue(
            names.indexOf("createIndexes") > names.lastIndexOf("insert"),
            "commands ran in the order $names",
        )
    }

    @Test
    fun `the marker is written only once every document is in`() = runTest {
        val database = EmptyDatabase()

        Seeder(database.mongo, batchSize = 2, readOn = StandardTestDispatcher(testScheduler)).seed(gzipped(3)).toList()

        val inserts = database.mongo.commands.filter { it.containsKey("insert") }
        assertEquals(listOf("places", "places", "seed"), inserts.map { it.getString("insert") })
    }

    @Test
    fun `a database that already carries the marker is indexed but not seeded again`() = runTest {
        val database = EmptyDatabase(marker = Document("_id", SEED_MARKER_ID).append("documents", 3))
        database.stored = 3

        val progress = Seeder(database.mongo, readOn = StandardTestDispatcher(testScheduler)).seed(gzipped(3)).toList()

        assertEquals(listOf(SeedProgress.Checking, SeedProgress.Indexing, SeedProgress.Ready(3)), progress)
        assertTrue(database.mongo.commands.none { it.containsKey("insert") })
        assertTrue(database.mongo.commands.any { it.containsKey("createIndexes") })
    }

    @Test
    fun `a marker that disagrees with what is stored starts the seed again`() = runTest {
        // The collection emptied under a marker that says it holds three: believing the marker
        // alone would leave the application showing an empty map on every launch, for ever.
        val database = EmptyDatabase(marker = Document("_id", SEED_MARKER_ID).append("documents", 3))
        database.stored = 0

        val progress = Seeder(database.mongo, 2, StandardTestDispatcher(testScheduler))
            .seed(gzipped(3)).toList()

        assertEquals(SeedProgress.Ready(3), progress.last())
        assertTrue(database.mongo.commands.any { it.getString("drop") == "seed" })
        assertEquals(3, database.stored)
    }

    @Test
    fun `a marker that agrees with what is stored is believed`() = runTest {
        val database = EmptyDatabase(marker = Document("_id", SEED_MARKER_ID).append("documents", 3))
        database.stored = 3

        Seeder(database.mongo, 2, StandardTestDispatcher(testScheduler)).seed(gzipped(3)).toList()

        assertTrue(database.mongo.commands.none { it.containsKey("drop") })
    }

    @Test
    fun `a seed that held nothing is reported rather than marked as finished`() = runTest {
        // Marking an empty seed done is irreversible: every later start would agree.
        val database = EmptyDatabase()

        assertFailsWith<IOException> {
            Seeder(database.mongo, 2, StandardTestDispatcher(testScheduler)).seed(gzipped(0)).toList()
        }
        assertTrue(database.mongo.commands.none { it.getString("insert") == "seed" })
    }

    @Test
    fun `a run killed part way through is thrown away rather than left half seeded`() = runTest {
        // Documents but no marker: exactly what a process death between two batches leaves.
        val database = EmptyDatabase()
        database.stored = 1

        Seeder(database.mongo, batchSize = 2, readOn = StandardTestDispatcher(testScheduler)).seed(gzipped(3)).toList()

        assertTrue(database.mongo.commands.any { it.containsKey("drop") })
        assertEquals(3, database.stored)
    }

    @Test
    fun `a first run does not drop a collection that was never created`() = runTest {
        val database = EmptyDatabase()

        Seeder(database.mongo, batchSize = 2, readOn = StandardTestDispatcher(testScheduler)).seed(gzipped(3)).toList()

        assertTrue(database.mongo.commands.none { it.containsKey("drop") })
    }

    @Test
    fun `the seed is opened again when the flow is collected again`() = runTest {
        val database = EmptyDatabase()
        var opened = 0
        val seed = gzipped(2)

        val flow = Seeder(database.mongo, batchSize = 2, readOn = StandardTestDispatcher(testScheduler)).seed { opened++; seed() }
        flow.toList()
        flow.toList()

        assertEquals(2, opened)
    }

    @Test
    fun `a count the engine answered without an n is reported rather than read as empty`() = runTest {
        val mongo = FakeMongo(commandReply = { okReply() })

        assertFailsWith<IOException> { Seeder(mongo, readOn = StandardTestDispatcher(testScheduler)).seed(gzipped(1)).toList() }
    }

    @Test
    fun `the seed is read and inserted away from the thread collecting the progress`() = runTest {
        val reader = namedDispatcher("seed-reader")
        try {
            val database = EmptyDatabase()

            Seeder(database.mongo, batchSize = 2, readOn = reader).seed(gzipped(3)).toList()

            // The coroutine debugger appends its own suffix to the thread name.
            assertTrue(
                database.mongo.threads.all { it.startsWith("seed-reader") },
                "ran on ${database.mongo.threads.distinct()}",
            )
        } finally {
            reader.close()
        }
    }

    @Test
    fun `a batch size of zero is refused when the seeder is built`() {
        assertFailsWith<IllegalArgumentException> { Seeder(FakeMongo(), batchSize = 0) }
    }
}

/**
 * A database that starts empty and counts what is inserted into it, which is what the seeder's
 * decisions are made of: the marker says whether a seed finished, the count says whether one
 * started.
 */
private class EmptyDatabase(marker: Document? = null) {
    var stored = 0

    val mongo = FakeMongo(
        commandReply = { command ->
            when {
                command.containsKey("count") -> okReply("n" to stored)
                command.getString("insert") == "places" ->
                    okReply("n" to (command["documents"] as List<*>).size)
                        .also { stored += (command["documents"] as List<*>).size }
                command.containsKey("drop") -> okReply().also { stored = 0 }
                else -> okReply()
            }
        },
        queryResults = { listOfNotNull(marker) },
    )
}

/** [count] places, encoded and gzipped exactly as the shipped seed is. */
private fun gzipped(count: Int): () -> InputStream {
    val documents = List(count) { placeDocument(id = "place-$it") }
    val compressed = ByteArrayOutputStream()
    GZIPOutputStream(compressed).use { it.write(encoded(documents)) }
    val bytes = compressed.toByteArray()
    return { ByteArrayInputStream(bytes) }
}
