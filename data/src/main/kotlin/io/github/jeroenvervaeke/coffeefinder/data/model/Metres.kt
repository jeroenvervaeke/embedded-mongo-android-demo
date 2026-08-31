package io.github.jeroenvervaeke.coffeefinder.data.model

import java.util.Locale
import kotlin.math.roundToInt

/**
 * A distance over the ground, in metres.
 *
 * Metres rather than a bare `Double` because MongoDB answers a `2dsphere` query in metres while
 * an application talks in kilometres and miles: a number that has lost its unit is a number that
 * eventually gets converted twice.
 */
@JvmInline
value class Metres(val value: Double) {
    init {
        require(value.isFinite() && value >= 0) { "a distance cannot be $value metres" }
    }

    /**
     * How this distance reads to a person: metres up to a kilometre, then kilometres to one
     * decimal.
     *
     * In [locale], defaulting to the device's, because the only thing that calls this puts the
     * result on screen, and a reader in France writes 1,2 km rather than 1.2 km. Tests pass
     * [Locale.ROOT] so that what they assert does not depend on the machine running them.
     */
    fun describe(locale: Locale = Locale.getDefault()): String {
        // Rounded before the unit is chosen rather than after: 999.6 m rounds to 1000, and
        // "1000 m" is a distance no one writes.
        val metres = value.roundToInt()
        return when {
            metres < METRES_PER_KILOMETRE -> "$metres m"
            else -> String.format(locale, "%.1f km", value / METRES_PER_KILOMETRE)
        }
    }

    companion object {
        fun ofKilometres(kilometres: Double) = Metres(kilometres * METRES_PER_KILOMETRE)
    }
}

private const val METRES_PER_KILOMETRE = 1000
