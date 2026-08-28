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
     * decimal. [Locale.ROOT] because the separator belongs to the caller's formatting, not to a
     * value that also ends up in logs and tests.
     */
    fun describe(): String {
        // Rounded before the unit is chosen rather than after: 999.6 m rounds to 1000, and
        // "1000 m" is a distance no one writes.
        val metres = value.roundToInt()
        return when {
            metres < METRES_PER_KILOMETRE -> "$metres m"
            else -> String.format(Locale.ROOT, "%.1f km", value / METRES_PER_KILOMETRE)
        }
    }

    companion object {
        fun ofKilometres(kilometres: Double) = Metres(kilometres * METRES_PER_KILOMETRE)
    }
}

private const val METRES_PER_KILOMETRE = 1000
