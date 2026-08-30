package io.github.jeroenvervaeke.coffeefinder.data.finder

import io.github.jeroenvervaeke.coffeefinder.data.CORK
import io.github.jeroenvervaeke.coffeefinder.data.FakeMongo
import io.github.jeroenvervaeke.coffeefinder.data.placesIn
import io.github.jeroenvervaeke.coffeefinder.data.geo.Camera
import io.github.jeroenvervaeke.coffeefinder.data.model.Coordinates
import io.github.jeroenvervaeke.coffeefinder.data.model.Ireland
import io.github.jeroenvervaeke.coffeefinder.data.model.Metres
import io.github.jeroenvervaeke.coffeefinder.data.placeDocument
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.testTimeSource

class MapFinderTest {
    @Test
    fun `the map opens on the whole island, so the first draw holds every place`() = runTest {
        val finder = MapFinder(placesIn(found()), backgroundScope)

        settle()

        val viewport = ready(finder).viewport
        assertTrue(Coordinates(Ireland.EXTENT.west, Ireland.EXTENT.south) in viewport)
        assertTrue(Coordinates(Ireland.EXTENT.east, Ireland.EXTENT.north) in viewport)
    }

    @Test
    fun `moving the map asks about where it moved to`() = runTest {
        val mongo = found()
        val finder = MapFinder(placesIn(mongo), backgroundScope)
        settle()
        val before = ready(finder).viewport

        finder.moveBy(eastFraction = 0.0, northFraction = 0.0, zoom = 8.0)
        settle()

        assertEquals(before.centre, ready(finder).viewport.centre)
        assertTrue(
            ready(finder).viewport.heightDegrees < before.heightDegrees / 7,
            "the viewport went from ${before.heightDegrees} to ${ready(finder).viewport.heightDegrees}",
        )
    }

    @Test
    fun `a drag moves the map by that fraction of what is on screen`() = runTest {
        val finder = MapFinder(placesIn(found()), backgroundScope)
        settle()
        val before = ready(finder).viewport

        finder.moveBy(eastFraction = 0.5, northFraction = 0.0, zoom = 1.0)
        settle()

        assertEquals(
            before.centre.longitude + before.widthDegrees * 0.5,
            ready(finder).viewport.centre.longitude,
            absoluteTolerance = 1e-9,
        )
    }

    @Test
    fun `each step of a gesture is applied to where the camera is by then, not where it was drawn`() =
        runTest {
            val finder = MapFinder(placesIn(found()), backgroundScope)
            settle()
            val before = ready(finder).viewport.heightDegrees

            // Two events inside one frame: nothing recomposes between them, so a canvas holding
            // its own copy of the camera would apply both to the first one and lose a zoom.
            finder.moveBy(0.0, 0.0, zoom = 2.0)
            finder.moveBy(0.0, 0.0, zoom = 2.0)
            settle()

            assertEquals(before / 4, ready(finder).viewport.heightDegrees, absoluteTolerance = 1e-9)
        }

    @Test
    fun `asking for the whole island again frames it, however far in the map was`() = runTest {
        val finder = MapFinder(placesIn(found()), backgroundScope)
        settle()
        finder.moveBy(0.0, 0.0, zoom = 500.0)
        settle()

        finder.frameIreland()
        settle()

        val viewport = ready(finder).viewport
        assertTrue(Coordinates(Ireland.EXTENT.west, Ireland.EXTENT.south) in viewport)
        assertTrue(Coordinates(Ireland.EXTENT.east, Ireland.EXTENT.north) in viewport)
    }

    @Test
    fun `a movement too small to redraw does not cost a query`() = runTest {
        val mongo = found()
        val finder = MapFinder(placesIn(mongo), backgroundScope)
        settle()
        val before = mongo.commands.size

        finder.moveBy(eastFraction = 0.0, northFraction = 1e-7, zoom = 1.0)
        settle()

        assertEquals(before, mongo.commands.size)
    }

    @Test
    fun `a gesture is one query rather than one per frame`() = runTest {
        val mongo = found()
        val finder = MapFinder(placesIn(mongo), backgroundScope)
        settle()
        val before = mongo.commands.size

        repeat(20) { finder.moveBy(eastFraction = 0.01, northFraction = 0.0, zoom = 1.02) }
        settle()

        assertEquals(before + 1, mongo.commands.size)
    }

    @Test
    fun `the camera is published immediately, because the canvas draws from it every frame`() =
        runTest {
            val finder = MapFinder(placesIn(found()), backgroundScope)

            finder.moveBy(eastFraction = 0.0, northFraction = 0.0, zoom = 4.0)

            assertEquals(Camera.IRELAND.latitudeSpan / 4, finder.camera.value.latitudeSpan)
        }

    @Test
    fun `a wider canvas is asked about a wider box`() = runTest {
        val finder = MapFinder(placesIn(found()), backgroundScope)
        settle()
        val portrait = ready(finder).viewport.widthDegrees

        finder.resizedTo(2.0)
        settle()

        assertTrue(ready(finder).viewport.widthDegrees > portrait)
    }

    @Test
    fun `rotating the device reframes the island, because the opening view should still hold it`() =
        runTest {
            val finder = MapFinder(placesIn(found()), backgroundScope)

            finder.resizedTo(1.8)
            settle()

            val viewport = ready(finder).viewport
            assertTrue(Coordinates(Ireland.EXTENT.west, Ireland.EXTENT.south) in viewport)
            assertTrue(Coordinates(Ireland.EXTENT.east, Ireland.EXTENT.north) in viewport)
        }

    @Test
    fun `rotating after the user moved the map keeps where they moved it to`() = runTest {
        val finder = MapFinder(placesIn(found()), backgroundScope)
        finder.moveBy(eastFraction = 0.0, northFraction = 0.0, zoom = 20.0)
        val chosen = finder.camera.value

        finder.resizedTo(1.8)
        settle()

        assertEquals(chosen, finder.camera.value)
    }

    @Test
    fun `framing the radius puts the whole of it on screen`() = runTest {
        val finder = MapFinder(placesIn(found()), backgroundScope)

        finder.frameOn(CORK, Metres(1_000.0))
        settle()

        val viewport = ready(finder).viewport
        assertTrue(CORK in viewport)
        // A kilometre north of the centre is about 0.009 degrees of latitude, and the ring has to
        // fit with ground around it rather than run off the top of the screen.
        assertTrue(Coordinates(CORK.longitude, CORK.latitude + 0.009) in viewport)
    }

    @Test
    fun `a resize after framing a radius keeps the radius framed rather than refitting the island`() =
        runTest {
            val finder = MapFinder(placesIn(found()), backgroundScope)
            finder.frameOn(CORK, Metres(1_000.0))
            val framed = finder.camera.value

            finder.resizedTo(1.8)
            settle()

            assertEquals(framed, finder.camera.value)
        }

    @Test
    fun `framing unless moved leaves a camera the user has taken over alone`() = runTest {
        val finder = MapFinder(placesIn(found()), backgroundScope)
        finder.moveBy(eastFraction = 0.2, northFraction = 0.0, zoom = 1.0)
        val chosen = finder.camera.value

        finder.frameOnUnlessMoved(CORK, Metres(1_000.0))

        assertEquals(chosen, finder.camera.value)
    }

    @Test
    fun `framing unless moved does frame a camera nobody has touched`() = runTest {
        val finder = MapFinder(placesIn(found()), backgroundScope)

        finder.frameOnUnlessMoved(CORK, Metres(1_000.0))
        settle()

        assertTrue(CORK in ready(finder).viewport)
    }

    @Test
    fun `asking for the island again lets a later rotation keep it framed`() = runTest {
        val finder = MapFinder(placesIn(found()), backgroundScope)
        finder.frameOn(CORK, Metres(1_000.0))

        finder.frameIreland()
        finder.resizedTo(1.8)
        settle()

        val viewport = ready(finder).viewport
        assertTrue(Coordinates(Ireland.EXTENT.west, Ireland.EXTENT.south) in viewport)
        assertTrue(Coordinates(Ireland.EXTENT.east, Ireland.EXTENT.north) in viewport)
    }

    @Test
    fun `a query the engine refused becomes a message rather than a crash`() = runTest {
        val mongo = FakeMongo(queryResults = { throw IOException("the engine is closed") })
        val finder = MapFinder(placesIn(mongo), backgroundScope)

        settle()

        assertEquals(MapState.Failed("the engine is closed"), finder.state.value)
    }

    @Test
    fun `the command the screen shows is the one that produced the dots on it`() = runTest {
        val mongo = found()
        val finder = MapFinder(placesIn(mongo), backgroundScope)

        settle()

        assertEquals(mongo.lastCommand, ready(finder).command)
        assertEquals(2, ready(finder).places.size)
    }

    @Test
    fun `a settled pan reports what the engine cost, and not the settle it waited out`() = runTest {
        val finder = MapFinder(placesIn(slow()), backgroundScope, clock = testTimeSource)

        settle()

        // Exactly the engine's own time. The debounce in front of the query is longer than this,
        // so an implementation that started the clock at the gesture instead of at the query
        // could not produce this number -- which is the whole point of asserting it exactly.
        assertEquals(ENGINE_TIME, ready(finder).took)
    }

    private fun found() = FakeMongo(
        queryResults = { listOf(placeDocument(), placeDocument(id = "second", name = "Bean and Leaf")) },
    )

    private fun slow() = FakeMongo(queryResults = { listOf(placeDocument()) }, answersIn = ENGINE_TIME)

    private fun ready(finder: MapFinder) = finder.state.value as MapState.Ready
}

/** Long enough to tell from zero, and shorter than the debounce it must not be confused with. */
private val ENGINE_TIME = 40.milliseconds
