package io.github.jeroenvervaeke.coffeefinder.data.query

/** The one database and the one collection this application has. */
const val COFFEE_DATABASE = "coffee"

const val PLACES_COLLECTION = "places"

/**
 * The field a geo query writes its distance into. `$geoNear` names it in `distanceField`, and the
 * text search computes the same field itself, so one parser reads both.
 */
const val DISTANCE_FIELD = "distance"

/**
 * How far MongoDB thinks it is to the other side of the planet.
 *
 * `$geoNear` measures GeoJSON distances on a sphere of this radius, so the text search's own
 * distance expression uses it too: two numbers labelled "metres" that came from different spheres
 * would sort against each other subtly wrongly.
 */
const val EARTH_RADIUS_METRES = 6378100.0
