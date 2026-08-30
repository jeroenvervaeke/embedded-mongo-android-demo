package io.github.jeroenvervaeke.coffeefinder.ui.console

import kotlin.test.Test
import kotlin.test.assertEquals

class SheetSnapTest {
    @Test
    fun `let go past half way, the sheet opens`() {
        assertEquals(OPEN, settleTarget(offset = 300f, travel = 800f, velocity = 0f))
    }

    @Test
    fun `let go short of half way, the sheet shuts again`() {
        assertEquals(800f, settleTarget(offset = 500f, travel = 800f, velocity = 0f))
    }

    @Test
    fun `a flick upward opens it from wherever it had got to`() {
        // The gesture the screen is built around: the list thrown up from the very bottom.
        assertEquals(OPEN, settleTarget(offset = 780f, travel = 800f, velocity = -1_200f))
    }

    @Test
    fun `a flick downward shuts it from wherever it had got to`() {
        assertEquals(800f, settleTarget(offset = 20f, travel = 800f, velocity = 1_200f))
    }

    @Test
    fun `a slow drag is not a flick, so where it ended is what decides`() {
        assertEquals(OPEN, settleTarget(offset = 100f, travel = 800f, velocity = 200f))
        assertEquals(800f, settleTarget(offset = 700f, travel = 800f, velocity = -200f))
    }

    @Test
    fun `a sheet with nowhere to go stays open rather than dividing by its own travel`() {
        assertEquals(OPEN, settleTarget(offset = 0f, travel = 0f, velocity = 900f))
    }

    @Test
    fun `exactly half way opens, because the drag that got there was upward more often than not`() {
        assertEquals(800f, settleTarget(offset = 400f, travel = 800f, velocity = 0f))
        assertEquals(OPEN, settleTarget(offset = 399f, travel = 800f, velocity = 0f))
    }
}

private const val OPEN = 0f
