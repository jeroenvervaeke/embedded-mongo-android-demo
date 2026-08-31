package io.github.jeroenvervaeke.coffeefinder.ui.console

/**
 * Where a sheet let go at [offset] should settle: `0f` open, [travel] shut.
 *
 * A flick decides by its direction, however far the sheet had got: the gesture that throws the
 * list up from the very bottom is the one this screen is built around, and asking it to cross the
 * half-way line first would make it feel like it had not been noticed. A slower drag decides by
 * where it ended, which is the only thing it said.
 *
 * [velocity] is pixels per second, positive downwards, as Compose reports it.
 */
fun settleTarget(offset: Float, travel: Float, velocity: Float): Float = when {
    travel <= 0f -> 0f
    velocity < -FLICK -> 0f
    velocity > FLICK -> travel
    offset < travel / 2 -> 0f
    else -> travel
}

/**
 * The speed at which a drag counts as a flick, in pixels per second.
 *
 * About a finger's width in a tenth of a second on a phone screen; below it the gesture reads as
 * placing the sheet rather than throwing it.
 */
private const val FLICK = 450f
