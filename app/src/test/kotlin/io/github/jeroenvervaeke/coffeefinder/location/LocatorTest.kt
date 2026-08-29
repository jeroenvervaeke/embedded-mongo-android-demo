package io.github.jeroenvervaeke.coffeefinder.location

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest

/**
 * The budget itself, on virtual time.
 *
 * Every wait here costs nothing to run and is exact to the millisecond, which is what lets these
 * assert *when* the answer came as well as what it was — the difference between a test of a
 * timeout and a test that would pass without one.
 */
class LocatorTest {
    @Test
    fun `a fix that arrives inside the budget is the answer`() = runTest {
        val prompt = Locator {
            delay(2.seconds)
            CORK
        }

        val fix = prompt.fixWithin(BUDGET)

        assertEquals(LocationFix.Known(CORK), fix)
    }

    @Test
    fun `a provider that never calls back is given up on when the budget runs out`() = runTest {
        val silent = SilentLocator()

        val fix = silent.fixWithin(BUDGET)

        assertEquals(LocationFix.GaveUp, fix)
        // Exact: it waited the whole budget and no longer, which is the assertion that fails if
        // the timeout is taken away -- there is then no answer to make at all.
        assertEquals(BUDGET.inWholeMilliseconds, currentTime)
    }

    @Test
    fun `giving up cancels the request rather than leaving it running`() = runTest {
        val silent = SilentLocator()

        silent.fixWithin(BUDGET)

        assertTrue(silent.cancelled, "the location request outlived the wait for it")
    }

    @Test
    fun `a device that says it does not know is not waited out`() = runTest {
        val refused = Locator { null }

        val fix = refused.fixWithin(BUDGET)

        assertEquals(LocationFix.Unavailable, fix)
        assertEquals(0L, currentTime)
    }

    @Test
    fun `a silence is not reported to the screen as the same thing as a refusal`() {
        // One is answered by granting a permission and the other by asking again, so the screen
        // has to be able to tell them apart.
        assertNotEquals(LocationFix.Unavailable.source, LocationFix.GaveUp.source)
    }
}
