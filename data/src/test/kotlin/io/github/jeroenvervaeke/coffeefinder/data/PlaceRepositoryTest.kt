package io.github.jeroenvervaeke.coffeefinder.data

import io.github.jeroenvervaeke.coffeefinder.data.model.PlaceCategory
import io.github.jeroenvervaeke.coffeefinder.data.model.Viewport
import io.github.jeroenvervaeke.coffeefinder.data.query.nearestPipeline
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class PlaceRepositoryTest {
    @Test
    fun `the nearest query sends the pipeline it reports and parses what comes back`() = runTest {
        val mongo = FakeMongo(queryResults = { listOf(placeDocument().append("distance", 240.0)) })

        val found = placesIn(mongo).nearest(DUBLIN, limit = 5, category = PlaceCategory.CAFE)

        assertEquals(240.0, found.results.single().distance.value)
        assertEquals("The House Of Pretzels", found.results.single().place.name)
        assertEquals(nearestPipeline(DUBLIN, 5, category = PlaceCategory.CAFE), found.command.pipeline())
        assertEquals(found.command, mongo.lastCommand)
    }

    @Test
    fun `a query goes to the places collection as an aggregate the library built`() = runTest {
        val mongo = FakeMongo(queryResults = { listOf(placeDocument().append("distance", 12.0)) })

        placesIn(mongo).search("pretzels", DUBLIN, limit = 5)

        assertEquals(listOf("aggregate", "pipeline", "cursor"), mongo.lastCommand.keys.toList())
        assertEquals("places", mongo.lastCommand["aggregate"])
    }

    @Test
    fun `a search reaches the engine as a text query and comes back as places`() = runTest {
        val mongo = FakeMongo(queryResults = { listOf(placeDocument().append("distance", 12.0)) })

        val found = placesIn(mongo).search("pretzels", DUBLIN, limit = 5)

        assertEquals(
            "\$text",
            (found.command.pipeline().first()["\$match"] as Map<*, *>).keys.single(),
        )
        assertEquals("The House Of Pretzels", found.results.single().place.name)
    }

    @Test
    fun `a viewport query returns places without distances, because it measured none`() = runTest {
        val mongo = FakeMongo(queryResults = { listOf(placeDocument(), placeDocument(id = "second")) })

        val found = placesIn(mongo).inViewport(BOX, limit = 100)

        assertEquals(2, found.results.size)
        assertEquals(found.command, mongo.lastCommand)
    }

    @Test
    fun `replies are decoded away from the thread that asked for them`() = runTest {
        // On Android the asking thread is the main one, and six thousand documents of BSON
        // parsed on it is the difference between a pan and a dropped frame.
        val decoder = namedDispatcher("decoder")
        try {
            val mongo = FakeMongo(queryResults = { listOf(placeDocument().append("distance", 1.0)) })

            PlaceRepository(mongo.places, decoder).nearest(DUBLIN, limit = 5)

            // The coroutine debugger appends its own suffix to the thread name.
            assertTrue(mongo.threads.single().startsWith("decoder"), "ran on ${mongo.threads}")
        } finally {
            decoder.close()
        }
    }

    @Test
    fun `a query that matched nothing is an empty result, not a failure`() = runTest {
        val found = placesIn(FakeMongo()).nearest(DUBLIN, limit = 5)

        assertEquals(emptyList(), found.results)
    }
}

private val BOX = Viewport(west = -10.0, south = 51.0, east = -5.0, north = 55.0)
