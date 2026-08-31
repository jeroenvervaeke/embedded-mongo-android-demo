package io.github.jeroenvervaeke.coffeefinder.data.model

/** The island this application ships, and where it looks when the device will not say. */
object Ireland {
    /**
     * Where the map starts and where a query is measured from when location is unavailable:
     * refused, switched off, or a device that has not had a fix yet.
     */
    val DUBLIN = Coordinates(longitude = -6.2603, latitude = 53.3498)

    /**
     * The extent the seed was extracted with, so the opening view holds every document in it and
     * the first `$geoWithin` returns the whole island.
     */
    val EXTENT = Viewport(west = -10.8, south = 51.3, east = -5.3, north = 55.5)
}
