package io.github.jeroenvervaeke.coffeefinder.ui

import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * How long something took, as a reader sees it: milliseconds under a second, then seconds.
 *
 * In [locale], defaulting to the device's, for the same reason
 * [io.github.jeroenvervaeke.coffeefinder.data.model.Metres.describe] is: a reader in France
 * writes 41,3 ms. Tests pass [Locale.ROOT] so what they assert does not depend on the machine.
 *
 * A decimal either side of the boundary rather than whole milliseconds: a `$geoNear` over a
 * 2dsphere index answers in well under a millisecond on a phone, and rounding that to "0 ms"
 * would throw away the only interesting thing about it.
 */
fun Duration.describe(locale: Locale = Locale.getDefault()): String = when {
    this < 1.seconds -> String.format(locale, "%.1f ms", inWholeMicroseconds / MICROS_PER_MILLI)
    else -> String.format(locale, "%.2f s", inWholeMicroseconds / MICROS_PER_SECOND)
}

private const val MICROS_PER_MILLI = 1_000.0

private const val MICROS_PER_SECOND = 1_000_000.0
