package io.github.jeroenvervaeke.coffeefinder.ui

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class ElapsedTest {
    @Test
    fun `a query is milliseconds`() {
        assertEquals("41.3 ms", 41_300.microseconds.describe(Locale.ROOT))
    }

    @Test
    fun `a query faster than a millisecond still reads as a number`() {
        assertEquals("0.4 ms", 400.microseconds.describe(Locale.ROOT))
    }

    @Test
    fun `a cold start is seconds`() {
        assertEquals("1.87 s", 1_870.milliseconds.describe(Locale.ROOT))
    }

    @Test
    fun `exactly a second is already seconds, not a thousand milliseconds`() {
        assertEquals("1.00 s", 1.seconds.describe(Locale.ROOT))
    }

    @Test
    fun `the decimal separator is the reader's`() {
        assertEquals("41,3 ms", 41_300.microseconds.describe(Locale.FRANCE))
        assertEquals("1,87 s", 1_870.milliseconds.describe(Locale.FRANCE))
    }
}
