package io.github.jeroenvervaeke.coffeefinder.data.finder

import io.github.jeroenvervaeke.coffeefinder.data.DUBLIN
import io.github.jeroenvervaeke.coffeefinder.data.FakeMongo
import io.github.jeroenvervaeke.coffeefinder.data.placesIn
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
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
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

        assertEquals(1, mongo.commands.size)
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
            (mongo.lastCommand.stage("\$geoNear")["near"] as Document)["coordinates"],
        )
    }

    @Test
    fun `a category filter reaches the query`() = runTest {
        val mongo = found()
        val finder = NearbyFinder(placesIn(mongo), backgroundScope)

        finder.filterBy(PlaceCategory.COFFEE_ROASTERY)
        settle()

        assertEquals(Document("cat", "coffee_roastery"), mongo.lastCommand.stage("\$geoNear")["query"])
    }

    @Test
    fun `a distance cap reaches the nearest query as geoNear's own maxDistance`() = runTest {
        val mongo = found()
        val finder = NearbyFinder(placesIn(mongo), backgroundScope)

        finder.limitTo(Metres.ofKilometres(5.0))
        settle()

        assertEquals(5000.0, mongo.lastCommand.stage("\$geoNear")["maxDistance"])
    }

    @Test
    fun `a distance cap reaches a text search too, rather than being silently dropped`() = runTest {
        val mongo = found()
        val finder = NearbyFinder(placesIn(mongo), backgroundScope)

        finder.searchFor("insomnia")
        finder.limitTo(Metres.ofKilometres(5.0))
        settle()

        assertTrue(mongo.lastCommand.toJson().contains("5000.0"))
    }

    @Test
    fun `lifting the cap asks again without one`() = runTest {
        val mongo = found()
        val finder = NearbyFinder(placesIn(mongo), backgroundScope)
        finder.limitTo(Metres.ofKilometres(5.0))
        settle()

        finder.limitTo(null)
        settle()

        assertFalse(mongo.lastCommand.stage("\$geoNear").containsKey("maxDistance"))
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

    private fun found() = FakeMongo(queryResults = { listOf(placeDocument().append("distance", 240.0)) })

    private fun ready(finder: NearbyFinder) = finder.state.value as NearbyState.Ready
}

/** Past the debounce and through the query the finder started after it. */
internal fun TestScope.settle() {
    advanceTimeBy(1_000)
    runCurrent()
}
