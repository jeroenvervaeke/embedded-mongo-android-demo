package io.github.jeroenvervaeke.coffeefinder.data.parse

import io.github.jeroenvervaeke.coffeefinder.data.model.Address
import io.github.jeroenvervaeke.coffeefinder.data.model.Confidence
import io.github.jeroenvervaeke.coffeefinder.data.model.Coordinates
import io.github.jeroenvervaeke.coffeefinder.data.model.Metres
import io.github.jeroenvervaeke.coffeefinder.data.model.NearbyPlace
import io.github.jeroenvervaeke.coffeefinder.data.model.Place
import io.github.jeroenvervaeke.coffeefinder.data.model.PlaceCategory
import io.github.jeroenvervaeke.coffeefinder.data.model.PlaceId
import io.github.jeroenvervaeke.coffeefinder.data.query.DISTANCE_FIELD
import org.bson.Document

/**
 * Reads one reply document as a [Place].
 *
 * Every field is checked here, at the one boundary the engine's replies cross, so nothing above
 * this has to ask whether a name is really a string. A document that does not fit raises rather
 * than being dropped: silently skipping it would turn a broken seed into a map that is merely a
 * little emptier than it should be.
 */
fun Document.toPlace(): Place = Place(
    id = PlaceId(requiredString("_id")),
    name = requiredString("name"),
    category = requiredString("cat").let {
        PlaceCategory.of(it) ?: fail("`cat` holds the unknown category `$it`")
    },
    confidence = Confidence(requiredNumber("confidence")),
    coordinates = requiredCoordinates(),
    brand = optionalString("brand"),
    address = (this["addr"] as? Document)?.toAddress(),
)

/** Reads a reply from a query that measured a distance as well as matching a place. */
fun Document.toNearbyPlace(): NearbyPlace =
    NearbyPlace(place = toPlace(), distance = Metres(requiredNumber(DISTANCE_FIELD)))

private fun Document.toAddress() = Address(
    street = optionalString("street"),
    locality = optionalString("locality"),
    postcode = optionalString("postcode"),
    region = optionalString("region"),
)

/**
 * `loc` is a GeoJSON point, so the coordinates are an array of two numbers, longitude first. The
 * type is checked rather than assumed: a `LineString` here would otherwise be read as a point at
 * the start of it.
 */
private fun Document.requiredCoordinates(): Coordinates {
    val location = this["loc"] as? Document ?: fail("`loc` is missing or is not a document")
    val type = location["type"]
    if (type != "Point") fail("`loc` is a $type rather than a Point")
    val coordinates = location["coordinates"] as? List<*>
        ?: fail("`loc.coordinates` is missing or is not an array")
    if (coordinates.size != 2) fail("`loc.coordinates` holds ${coordinates.size} values, not 2")
    return Coordinates(
        longitude = coordinates[0].asDouble("loc.coordinates.0"),
        latitude = coordinates[1].asDouble("loc.coordinates.1"),
    )
}

private fun Document.requiredString(field: String): String =
    this[field] as? String ?: fail("`$field` is missing or is not a string")

private fun Document.optionalString(field: String): String? = this[field] as? String

private fun Document.requiredNumber(field: String): Double = this[field].asDouble(field)

/**
 * BSON keeps the width it was written with, so a value stored as a double and a value the engine
 * computed as an integer both arrive here and both are the number that was meant.
 */
private fun Any?.asDouble(field: String): Double =
    (this as? Number)?.toDouble() ?: throw PlaceFormatException("`$field` is missing or is not a number")

private fun fail(reason: String): Nothing = throw PlaceFormatException(reason)
