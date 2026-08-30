package io.github.jeroenvervaeke.coffeefinder.location

import io.github.jeroenvervaeke.embeddedmongodb.MongoCollection
import io.github.jeroenvervaeke.coffeefinder.data.PlaceRepository
import io.github.jeroenvervaeke.coffeefinder.data.finder.NearbyFinder
import io.github.jeroenvervaeke.coffeefinder.data.model.Coordinates
import io.github.jeroenvervaeke.coffeefinder.data.model.Ireland
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/**
 * What one attempt at locating the device does to the screen and to the query behind it.
 *
 * The finder is the real one over a scripted seam, so "the query is measured from there" is
 * asserted on the origin the query would carry rather than on a callback having been made.
 */
class LocationOriginTest {
    @Test
    fun `a fix is what the query is measured from`() = runTest {
        val finder = finder()
        val origin = LocationOrigin(Locator { CORK }, backgroundScope, BUDGET)

        origin.locate { finder }
        settle()

        assertEquals(CORK, finder.asked.value.origin)
        assertEquals(LocationSource.DEVICE, origin.source.value)
    }

    @Test
    fun `a provider that never calls back stops the screen saying it is still looking`() = runTest {
        val finder = finder()
        val origin = LocationOrigin(SilentLocator(), backgroundScope, BUDGET)

        origin.locate { finder }
        settle()

        assertEquals(LocationSource.TIMED_OUT, origin.source.value)
    }

    @Test
    fun `it gives up when the budget runs out and not a moment before`() = runTest {
        val finder = finder()
        val origin = LocationOrigin(SilentLocator(), backgroundScope, BUDGET)

        origin.locate { finder }
        // advanceTimeBy stops short of whatever is scheduled at exactly the instant it lands on,
        // which here is the give-up itself. So this is the moment before it, and the moment after.
        advanceTimeBy(BUDGET)
        assertEquals(LocationSource.ASKING, origin.source.value, "gave up early")
        runCurrent()

        assertEquals(LocationSource.TIMED_OUT, origin.source.value)
    }

    @Test
    fun `having given up, the query is still measured from Dublin`() = runTest {
        val finder = finder()
        val origin = LocationOrigin(SilentLocator(), backgroundScope, BUDGET)

        origin.locate { finder }
        settle()

        assertEquals(Ireland.DUBLIN, finder.asked.value.origin)
    }

    @Test
    fun `a device that will not say puts the screen on Dublin as soon as it knows`() = runTest {
        val finder = finder()
        val origin = LocationOrigin(Locator { null }, backgroundScope, BUDGET)

        origin.locate { finder }
        settle()

        assertEquals(LocationSource.FALLBACK, origin.source.value)
    }

    @Test
    fun `a second ask is not overtaken by the one before it`() = runTest {
        val finder = finder()
        val origin = LocationOrigin(slowThenPrompt(), backgroundScope, BUDGET)

        origin.locate { finder }
        advanceTimeBy(1.seconds)
        origin.locate { finder }
        settle()

        assertEquals(GALWAY, finder.asked.value.origin)
    }

    @Test
    fun `an abandoned ask does not cost a second query`() = runTest {
        val engine = CountingEngine()
        val finder = finder(engine.places)
        val origin = LocationOrigin(slowThenPrompt(), backgroundScope, BUDGET)

        origin.locate { finder }
        advanceTimeBy(1.seconds)
        origin.locate { finder }
        settle()

        // The opening Dublin query, and one for the fix that won. Not a third for the fix that
        // was abandoned and answered anyway.
        assertEquals(2, engine.queries)
    }

    @Test
    fun `a failed retry does not claim Dublin over a fix already in effect`() = runTest {
        val finder = finder()
        val origin = LocationOrigin(ScriptedLocator(listOf(fix(CORK), fix(null))), backgroundScope, BUDGET)
        origin.locate { finder }
        settle()

        origin.locate { finder }
        settle()

        assertEquals(LocationSource.DEVICE, origin.source.value)
        assertEquals(CORK, finder.asked.value.origin)
    }

    @Test
    fun `a failed retry does not claim Dublin over a point tapped on the map`() = runTest {
        val finder = finder()
        val origin = LocationOrigin(Locator { null }, backgroundScope, BUDGET)
        origin.pick(finder, GALWAY)

        origin.locate { finder }
        settle()

        assertEquals(LocationSource.PICKED, origin.source.value)
    }

    @Test
    fun `a tap on the map calls off a fix that was already on its way`() = runTest {
        val finder = finder()
        val slow = Locator {
            delay(2.seconds)
            CORK
        }
        val origin = LocationOrigin(slow, backgroundScope, BUDGET)

        origin.locate { finder }
        origin.pick(finder, GALWAY)
        settle()

        assertEquals(GALWAY, finder.asked.value.origin)
        assertEquals(LocationSource.PICKED, origin.source.value)
    }

    @Test
    fun `the location button still works after a tap on the map`() = runTest {
        // A tap beats a fix that was already on its way; it does not beat the next press of the
        // button, which is the user overruling their own tap.
        val finder = finder()
        val origin = LocationOrigin(Locator { CORK }, backgroundScope, BUDGET)
        origin.pick(finder, GALWAY)

        origin.locate { finder }
        settle()

        assertEquals(CORK, finder.asked.value.origin)
        assertEquals(LocationSource.DEVICE, origin.source.value)
    }

    @Test
    fun `a database that never opened does not strand the attempt on a finder it will not get`() =
        runTest {
            val origin = LocationOrigin(Locator { CORK }, backgroundScope, BUDGET)

            origin.locate { null }
            settle()

            assertEquals(LocationSource.ASKING, origin.source.value)
        }

    /**
     * A first fix that takes its time, and a second that is immediate -- in that order.
     *
     * The slow one is inside the budget deliberately: it is an answer that would arrive, and land
     * second, if the ask that superseded it had not called it off.
     */
    private fun slowThenPrompt() = ScriptedLocator(
        listOf(Answer(after = 3.seconds, where = CORK), fix(GALWAY)),
    )

    /** An answer that arrives at once, which is every ask whose timing is not the point. */
    private fun fix(where: Coordinates?) = Answer(after = Duration.ZERO, where = where)

    private fun TestScope.finder(places: MongoCollection = CountingEngine().places) =
        NearbyFinder(PlaceRepository(places, StandardTestDispatcher(testScheduler)), backgroundScope)

    /**
     * Past the budget, past the debounce in front of a query, and through the query.
     *
     * `advanceUntilIdle` would do none of that: it stops as soon as only background coroutines
     * are left, and everything here runs in `backgroundScope`.
     */
    private fun TestScope.settle() {
        advanceTimeBy(BUDGET + 1.seconds)
        runCurrent()
    }
}
