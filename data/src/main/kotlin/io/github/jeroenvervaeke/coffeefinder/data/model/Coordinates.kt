package io.github.jeroenvervaeke.coffeefinder.data.model

/**
 * A WGS84 position.
 *
 * Longitude is named first because that is the order GeoJSON and a `2dsphere` index use, and
 * getting it the wrong way round produces a query that succeeds and answers nonsense rather than
 * one that fails.
 */
data class Coordinates(val longitude: Double, val latitude: Double) {
    init {
        require(longitude in -180.0..180.0) { "longitude $longitude is outside -180..180" }
        require(latitude in -90.0..90.0) { "latitude $latitude is outside -90..90" }
    }
}
