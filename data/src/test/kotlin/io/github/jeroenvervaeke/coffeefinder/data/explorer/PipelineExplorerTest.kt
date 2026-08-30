package io.github.jeroenvervaeke.coffeefinder.data.explorer

import io.github.jeroenvervaeke.coffeefinder.data.FakeMongo
import io.github.jeroenvervaeke.coffeefinder.data.okReply
import io.github.jeroenvervaeke.coffeefinder.data.pipeline
import io.github.jeroenvervaeke.coffeefinder.data.placeDocument
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.testTimeSource
import org.bson.Document

class PipelineExplorerTest {
    @Test
    fun `what was typed is what the engine is asked`() = runTest {
        val mongo = FakeMongo(queryResults = { listOf(placeDocument()) })

        val result = explorer(mongo).run("""[ { "${'$'}match": { "cat": "cafe" } } ]""")

        assertIs<ExplorerResult.Ran>(result)
        assertEquals(listOf(Document("\$match", Document("cat", "cafe"))), mongo.lastCommand.pipeline())
        assertEquals("The House Of Pretzels", result.documents.single()["name"])
    }

    @Test
    fun `the command it reports is the one the library sent`() = runTest {
        val mongo = FakeMongo(queryResults = { listOf(placeDocument()) })

        val result = explorer(mongo).run("""[ { "${'$'}limit": 1 } ]""")

        assertEquals(mongo.lastCommand, assertIs<ExplorerResult.Ran>(result).command)
    }

    @Test
    fun `a cursor holding more than the batch is reported as cut off rather than as all of it`() =
        runTest {
            val mongo = FakeMongo(queryResults = { List(9) { placeDocument(id = "place-$it") } })

            val result = explorer(mongo, batch = 4).run("""[ { "${'$'}match": {} } ]""")

            assertIs<ExplorerResult.Ran>(result)
            assertEquals(4, result.documents.size)
            assertTrue(result.truncated)
        }

    @Test
    fun `a cursor that fits is not reported as cut off`() = runTest {
        val mongo = FakeMongo(queryResults = { listOf(placeDocument()) })

        val result = explorer(mongo, batch = 4).run("""[ { "${'$'}match": {} } ]""")

        assertFalse(assertIs<ExplorerResult.Ran>(result).truncated)
    }

    @Test
    fun `text that is not a pipeline never reaches the engine`() = runTest {
        val mongo = FakeMongo(queryResults = { listOf(placeDocument()) })

        val result = explorer(mongo).run("[ { oops")

        assertIs<ExplorerResult.Rejected>(result)
        assertTrue(mongo.commands.isEmpty(), "a pipeline that would not parse was still sent")
    }

    @Test
    fun `a pipeline the engine refuses comes back in the engine's own words`() = runTest {
        val mongo = FakeMongo(queryResults = { throw IOException("\$geoNear is only valid as the first stage") })

        val result = explorer(mongo).run("""[ { "${'$'}geoNear": {} } ]""")

        assertEquals(
            ExplorerResult.Refused("\$geoNear is only valid as the first stage"),
            result,
        )
    }

    @Test
    fun `explain asks the engine what it would do, around the same pipeline`() = runTest {
        val mongo = FakeMongo(commandReply = { okReply("stages" to listOf("IXSCAN")) })

        val result = explorer(mongo).explain("""[ { "${'$'}match": { "cat": "cafe" } } ]""")

        assertIs<ExplorerResult.Explained>(result)
        val explained = mongo.lastCommand["explain"] as Document
        assertEquals("places", explained["aggregate"])
        assertEquals(listOf(Document("\$match", Document("cat", "cafe"))), explained.pipeline())
        assertEquals("executionStats", mongo.lastCommand["verbosity"])
    }

    @Test
    fun `an engine that will not explain says so rather than the screen inventing a plan`() =
        runTest {
            val mongo = FakeMongo(commandReply = { throw IOException("no such command: explain") })

            val result = explorer(mongo).explain("""[ { "${'$'}limit": 1 } ]""")

            assertEquals(ExplorerResult.Refused("no such command: explain"), result)
        }

    @Test
    fun `what it reports as the cost is what the engine took`() = runTest {
        val mongo = FakeMongo(queryResults = { listOf(placeDocument()) }, answersIn = 40.milliseconds)

        val result = explorer(mongo).run("""[ { "${'$'}limit": 1 } ]""")

        assertEquals(40.milliseconds, assertIs<ExplorerResult.Ran>(result).took)
    }

    @Test
    fun `it runs against the collection the rest of the application queries`() = runTest {
        assertEquals("coffee.places", explorer(FakeMongo()).namespace)
    }

    private fun kotlinx.coroutines.test.TestScope.explorer(mongo: FakeMongo, batch: Int = 25) =
        PipelineExplorer(mongo.places, testTimeSource, batch)
}
