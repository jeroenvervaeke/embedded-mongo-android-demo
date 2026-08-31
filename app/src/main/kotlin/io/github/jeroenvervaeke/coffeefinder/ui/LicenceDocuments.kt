package io.github.jeroenvervaeke.coffeefinder.ui

/** A text the application is obliged to ship, and where in `assets` it ships. */
data class LicenceDocument(val title: String, val asset: String)

/**
 * Every licence and provenance text the About screen shows, in the order it shows them.
 *
 * A list rather than a run of calls in the composable, so that a test can open each path. Naming
 * an asset that is not there is otherwise a mistake nothing catches until the screen is opened on
 * a device -- which, for the one screen whose job is licence compliance, is too late.
 */
val LICENCE_DOCUMENTS = listOf(
    LicenceDocument("Attribution and provenance", "places/ireland.attribution.txt"),
    LicenceDocument("Foursquare NOTICE", "places/licenses/NOTICE.txt"),
    LicenceDocument(
        title = "Community Data License Agreement, Permissive 2.0",
        asset = "places/licenses/CDLA-Permissive-2.0.txt",
    ),
    LicenceDocument("Apache License 2.0", "places/licenses/Apache-2.0.txt"),
    LicenceDocument("CC0 1.0 Universal", "places/licenses/CC0-1.0.txt"),
    LicenceDocument("Where the licence texts came from", "places/licenses/SOURCES"),
)
