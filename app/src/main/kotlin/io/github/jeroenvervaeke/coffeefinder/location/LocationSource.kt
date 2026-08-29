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
    PICKED,
}
