package io.github.jeroenvervaeke.coffeefinder.data.model

/**
 * The rectangle of the world a query is asked about, in degrees.
 *
 * A rectangle rather than a centre and a radius because that is what a map shows and what
 * `$geoWithin` takes. It cannot cross the antimeridian: nothing this application draws does, and
 * a wrapped box is a polygon MongoDB reads as going the long way round the planet.
 */
data class Viewport(
    val west: Double,
    val south: Double,
    val east: Double,
    val north: Double,
) {
    init {
        require(west < east) { "a viewport running from $west to $east east is empty or wrapped" }
        require(south < north) { "a viewport running from $south to $north north is empty" }
        require(west >= -180.0 && east <= 180.0) { "longitudes $west..$east leave the world" }
        require(south >= -90.0 && north <= 90.0) { "latitudes $south..$north leave the world" }
    }

    /** Computed rather than stored: a centre that disagrees with its corners is unrepresentable. */
    val centre: Coordinates get() = Coordinates((west + east) / 2, (south + north) / 2)

    val widthDegrees: Double get() = east - west

    val heightDegrees: Double get() = north - south

    operator fun contains(coordinates: Coordinates): Boolean =
        coordinates.longitude in west..east && coordinates.latitude in south..north
}
