package io.github.jeroenvervaeke.coffeefinder.location

/** Why a query is being measured from where it is, which is worth saying on screen. */
enum class LocationSource {
    /** No answer yet — the permission has not been decided, or the fix has not arrived. */
    ASKING,

    /** The device said where it is. */
    DEVICE,

    /** It said it does not know, so the queries measure from Dublin. */
    FALLBACK,

    /**
     * It said nothing at all before [LOCATION_BUDGET] ran out, so the queries measure from Dublin.
     *
     * Told apart from [FALLBACK] because the two ask different things of the reader. A device that
     * answered "I do not know" will keep answering that until something changes — the permission,
     * the location switch — and asking again is pointless. A device that answered nothing may well
     * answer the next time, so the button beside this is worth pressing.
     */
    TIMED_OUT,

    /** The user tapped the map, which overrides wherever the device thinks it is. */
    PICKED;

    /**
     * Whether a query in this state really is measured from Dublin.
     *
     * What makes it safe to relabel the screen after an attempt that produced no fix. An attempt
     * that fails once a fix or a tap has landed leaves that origin in place, so saying "measured
     * from Dublin" over it would be a lie the reader has no way to check.
     *
     * A `when` rather than a set of equalities, so a new state has to decide which it is.
     */
    val measuresFromDublin: Boolean get() = when (this) {
        ASKING, FALLBACK, TIMED_OUT -> true
        DEVICE, PICKED -> false
    }
}
