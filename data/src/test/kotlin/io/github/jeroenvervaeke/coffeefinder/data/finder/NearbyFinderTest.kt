package io.github.jeroenvervaeke.coffeefinder.data.finder

import io.github.jeroenvervaeke.coffeefinder.data.DUBLIN
import io.github.jeroenvervaeke.coffeefinder.data.FakeMongo
import io.github.jeroenvervaeke.coffeefinder.data.placesIn
import io.github.jeroenvervaeke.coffeefinder.data.model.Confidence
import io.github.jeroenvervaeke.coffeefinder.data.model.Coordinates
import io.github.jeroenvervaeke.coffeefinder.data.model.Metres
import io.github.jeroenvervaeke.coffeefinder.data.model.PlaceCategory
import io.github.jeroenvervaeke.coffeefinder.data.pipeline
import io.github.jeroenvervaeke.coffeefinder.data.placeDocument
import io.github.jeroenvervaeke.coffeefinder.data.stage
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.testTimeSource
import org.bson.Document

class NearbyFinderTest {
    @Test
    fun `an empty search box asks for the places nearest the origin`() = runTest {
        val mongo = found()
        val finder = NearbyFinder(placesIn(mongo), backgroundScope)

        settle()

        assertEquals("\$geoNear", mongo.lastCommand.pipeline().first().keys.single())
        assertEquals(1, ready(finder).places.size)
    }

    @Test
    fun `typing switches to a text search of the same origin`() = runTest {
        val mongo = found()
        val finder = NearbyFinder(placesIn(mongo), backgroundScope)

        finder.searchFor("insomnia")
        settle()

        assertEquals("\$match", mongo.lastCommand.pipeline().first().keys.single())
        assertTrue(mongo.lastCommand.toJson().contains("insomnia"))
    }

    @Test
    fun `a search of only spaces is the same question as an empty one`() = runTest {
        val mongo = found()
        val finder = NearbyFinder(placesIn(mongo), backgroundScope)

        finder.searchFor("   ")
        settle()

        assertEquals("\$geoNear", mongo.lastCommand.pipeline().first().keys.single())
    }

    @Test
    fun `typing a word runs one query rather than one per keystroke`() = runTest {
        val mongo = found()
        val finder = NearbyFinder(placesIn(mongo), backgroundScope)

        "cafe".forEachIndexed { index, _ ->
            finder.searchFor("cafe".take(index + 1))
            advanceTimeBy(40)
        }
        settle()

        // Two commands, not five: one `$count` for the headline and one list for the rows,
        // which is what a settled request costs however many keystrokes went into it.
        assertEquals(2, mongo.commands.size)
        assertTrue(mongo.lastCommand.toJson().contains("cafe"))
    }

    @Test
    fun `the origin the device reported is the one the query measures from`() = runTest {
        val mongo = found()
        val finder = NearbyFinder(placesIn(mongo), backgroundScope)

        finder.measureFrom(Coordinates(longitude = -8.4756, latitude = 51.8985))
        settle()

        assertEquals(
            listOf(-8.4756, 51.8985),
            (mongo.lastCommand.pipeline().stage("\$geoNear")["near"] as Document)["coordinates"],
        )
    }

    @Test
    fun `a category filter reaches the query`() = runTest {
        val mongo = found()
        val finder = NearbyFinder(placesIn(mongo), backgroundScope)

        finder.filterBy(PlaceCategory.COFFEE_ROASTERY)
        settle()

        assertEquals(Document("cat", "coffee_roastery"), mongo.lastCommand.pipeline().stage("\$geoNear")["query"])
    }

    @Test
    fun `the radius reaches the nearest query as geoNear's own maxDistance`() = runTest {
        val mongo = found()
        val finder = NearbyFinder(placesIn(mongo), backgroundScope)

        finder.within(Metres.ofKilometres(5.0))
        settle()

        assertEquals(5000.0, mongo.lastCommand.pipeline().stage("\$geoNear")["maxDistance"])
    }

    @Test
    fun `the radius reaches a text search too, rather than being silently dropped`() = runTest {
        val mongo = found()
        val finder = NearbyFinder(placesIn(mongo), backgroundScope)

        finder.searchFor("insomnia")
        finder.within(Metres.ofKilometres(5.0))
        settle()

        assertTrue(mongo.lastCommand.toJson().contains("5000.0"))
    }

    @Test
    fun `the map opens on a kilometre, because a radius is what the headline counts inside`() =
        runTest {
            val mongo = found()
            NearbyFinder(placesIn(mongo), backgroundScope)

            settle()

            assertEquals(1000.0, mongo.lastCommand.pipeline().stage("\$geoNear")["maxDistance"])
        }

    @Test
    fun `a narrowed radius asks again with the new one`() = runTest {
        val mongo = found()
        val finder = NearbyFinder(placesIn(mongo), backgroundScope)
        settle()

        finder.within(Metres(250.0))
        settle()

        assertEquals(250.0, mongo.lastCommand.pipeline().stage("\$geoNear")["maxDistance"])
    }

    @Test
    fun `the headline is counted by the engine over the same selection as the list`() = runTest {
        val mongo = found(within = 412)
        val finder = NearbyFinder(placesIn(mongo), backgroundScope)

        finder.within(Metres(750.0))
        settle()

        // The count is the first of the two commands, and it is the list's own pipeline with
        // the `$limit` off and a `$count` on: 412 matched, 1 was listed.
        val counting = mongo.commands.first().pipeline()
        assertEquals(750.0, counting.stage("\$geoNear")["maxDistance"])
        assertFalse(counting.any { it.containsKey("\$limit") })
        assertEquals("n", counting.last()["\$count"])
        assertEquals(412, ready(finder).matching)
        assertEquals(1, ready(finder).places.size)
    }

    @Test
    fun `a search counts what the text matched, not what is near`() = runTest {
        val mongo = found(within = 3)
        val finder = NearbyFinder(placesIn(mongo), backgroundScope)

        finder.searchFor("insomnia")
        settle()

        assertEquals("\$match", mongo.commands.first().pipeline().first().keys.single())
        assertTrue(mongo.commands.first().toJson().contains("insomnia"))
        assertEquals(3, ready(finder).matching)
    }

    @Test
    fun `an empty selection counts as none rather than failing on a missing row`() = runTest {
        val mongo = FakeMongo(queryResults = { emptyList() })
        val finder = NearbyFinder(placesIn(mongo), backgroundScope)

        settle()

        assertEquals(0, ready(finder).matching)
        assertTrue(ready(finder).places.isEmpty())
    }

    @Test
    fun `switching the limit stage off asks for the list uncapped`() = runTest {
        val mongo = found()
        val finder = NearbyFinder(placesIn(mongo), backgroundScope)

        finder.capResults(false)
        settle()

        assertFalse(mongo.lastCommand.pipeline().any { it.containsKey("\$limit") })
    }

    @Test
    fun `a confidence floor and a brand filter reach one query together`() = runTest {
        val mongo = found()
        val finder = NearbyFinder(placesIn(mongo), backgroundScope)

        finder.requireConfidence(Confidence(0.9))
        finder.requireBrand(true)
        settle()

        val query = mongo.lastCommand.pipeline().stage("\$geoNear")["query"] as Document
        assertEquals(Document("\$gte", 0.9), query["confidence"])
        assertEquals(Document("\$exists", true), query["brand"])
    }

    @Test
    fun `dropping the confidence floor asks again without it`() = runTest {
        val mongo = found()
        val finder = NearbyFinder(placesIn(mongo), backgroundScope)
        finder.requireConfidence(Confidence(0.9))
        settle()

        finder.requireConfidence(null)
        settle()

        assertFalse(mongo.lastCommand.pipeline().stage("\$geoNear").containsKey("query"))
    }

    @Test
    fun `what the user asked is published before the query settles, so the controls do not lag`() =
        runTest {
            val finder = NearbyFinder(placesIn(found()), backgroundScope)

            finder.searchFor("insomnia")

            assertEquals("insomnia", finder.asked.value.text)
            assertEquals(DUBLIN, finder.asked.value.origin)
        }

    @Test
    fun `a query the engine refused becomes a message rather than a crash`() = runTest {
        val mongo = FakeMongo(queryResults = { throw IOException("the engine is closed") })
        val finder = NearbyFinder(placesIn(mongo), backgroundScope)

        settle()

        assertEquals(NearbyState.Failed("the engine is closed"), finder.state.value)
    }

    @Test
    fun `the command the screen shows is the one that produced what it is showing`() = runTest {
        val mongo = found()
        val finder = NearbyFinder(placesIn(mongo), backgroundScope)

        settle()

        assertEquals(mongo.lastCommand, ready(finder).command)
    }

    @Test
    fun `the list reports what the engine cost, and not the keystroke debounce before it`() = runTest {
        val finder = NearbyFinder(placesIn(slow()), backgroundScope, clock = testTimeSource)

        settle()

        // Both queries, because both are on the screen. Exact, and smaller than the debounce:
        // see the same assertion in MapFinderTest.
        assertEquals(ENGINE_TIME * 2, ready(finder).took)
    }

    /**
     * An engine that answers the `$count` with [within] and the list with one place.
     *
     * Two answers rather than one, because a settled request is two queries: a fake that replied
     * with places to both would be answering a `$count` with a document that has no count in it.
     */
    private fun found(within: Int = 1) = FakeMongo(queryResults = replies(within))

    private fun slow() = FakeMongo(queryResults = replies(), answersIn = ENGINE_TIME)

    private fun replies(within: Int = 1): (Document) -> List<Document> = { command ->
        if (command.pipeline().any { it.containsKey("\$count") }) {
            listOf(Document("n", within))
        } else {
            listOf(placeDocument().append("distance", 240.0))
        }
    }

    private fun ready(finder: NearbyFinder) = finder.state.value as NearbyState.Ready
}

/** Long enough to tell from zero, and shorter than the debounce it must not be confused with. */
private val ENGINE_TIME = 40.milliseconds

/** Past the debounce and through the query the finder started after it. */
internal fun TestScope.settle() {
    advanceTimeBy(1_000)
    runCurrent()
}
