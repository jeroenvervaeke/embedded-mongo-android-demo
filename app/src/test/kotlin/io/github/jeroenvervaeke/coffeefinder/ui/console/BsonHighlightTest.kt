package io.github.jeroenvervaeke.coffeefinder.ui.console

import io.github.jeroenvervaeke.coffeefinder.ui.theme.Console
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BsonHighlightTest {
    @Test
    fun `colouring changes how the text looks and not what it says`() {
        val json = """{ "name": "Kaph", "confidence": 0.93 }"""

        assertEquals(json, highlightJson(json).text)
    }

    @Test
    fun `a stage operator is coloured as an operator and not as a key`() {
        val styles = highlightJson("""{ "${'$'}geoNear": { "spherical": true } }""").spanStyles

        assertTrue(styles.any { it.item.color == Console.Spring }, "no operator was coloured")
    }

    @Test
    fun `a key and the string under it are coloured differently`() {
        val styles = highlightJson("""{ "name": "Kaph" }""").spanStyles

        assertTrue(styles.any { it.item.color == Console.Mint }, "the key was not coloured")
        assertTrue(styles.any { it.item.color == Console.Lavender }, "the value was not coloured")
    }

    @Test
    fun `numbers are coloured, including negative and exponent ones`() {
        val styles = highlightJson("""{ "lon": -6.2603, "e": 1.0E-4 }""").spanStyles

        assertEquals(4, styles.count(), "keys and numbers should be four spans")
        assertTrue(styles.count { it.item.color == Console.Blue } == 2)
    }

    @Test
    fun `booleans and nulls are coloured apart from strings`() {
        val styles = highlightJson("""{ "spherical": true, "brand": null }""").spanStyles

        assertTrue(styles.count { it.item.color == Console.Amber } == 2)
    }

    @Test
    fun `an escaped quote inside a string does not end it`() {
        val json = """{ "name": "O\"Brien's" }"""

        assertEquals(json, highlightJson(json).text)
        assertTrue(highlightJson(json).spanStyles.any { it.item.color == Console.Lavender })
    }

    @Test
    fun `text with nothing to colour is returned untouched`() {
        assertTrue(highlightJson("waiting for the engine").spanStyles.isEmpty())
    }
}
