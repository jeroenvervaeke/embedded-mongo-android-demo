package io.github.jeroenvervaeke.coffeefinder.ui.console

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MonogramTest {
    @Test
    fun `two words give two initials`() {
        assertEquals("TP", initialsOf("Two Pups"))
    }

    @Test
    fun `a name of many words stops at two, because a tile holds two`() {
        assertEquals("PO", initialsOf("Proper Order Coffee Co"))
    }

    @Test
    fun `punctuation is skipped rather than initialised`() {
        assertEquals("CP", initialsOf("Clement & Pekoe"))
    }

    @Test
    fun `a name starting with a digit keeps the digit`() {
        assertEquals("3", initialsOf("3fe"))
    }

    @Test
    fun `a name with nothing to initialise gives nothing rather than throwing`() {
        assertEquals("", initialsOf("— · —"))
        assertEquals("", initialsOf(""))
    }

    @Test
    fun `the same name is always the same colour, because the tile is the shop's`() {
        assertEquals(tileBrush("Kaph"), tileBrush("Kaph"))
    }

    @Test
    fun `two names are not usually the same colour`() {
        assertTrue(tileBrush("Kaph") != tileBrush("Vice Coffee Inc"))
    }
}
