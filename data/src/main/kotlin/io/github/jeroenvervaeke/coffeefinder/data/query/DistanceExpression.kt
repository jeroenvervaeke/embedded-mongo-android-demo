package io.github.jeroenvervaeke.coffeefinder.data.query

import io.github.jeroenvervaeke.coffeefinder.data.model.Coordinates
import kotlin.math.cos
import org.bson.Document

/**
 * An aggregation expression giving the great-circle distance in metres from [origin] to each
 * document's `loc`.
 *
 * This exists because `$geoNear` and `$text` cannot be combined: both have to be the first stage
 * of a pipeline, and `$geoNear`'s `query` option explicitly rejects `$text`. A text search that
 * still wants distances therefore computes them itself, in the pipeline rather than on the
 * client, so sorting and limiting still happen in the engine.
 *
 * The haversine formula, evaluated by the engine's own trigonometric operators:
 *
 * ```
 * d = 2r · asin( √( sin²(Δφ/2) + cos φ₀ · cos φ₁ · sin²(Δλ/2) ) )
 * ```
 *
 * `cos φ₀` and the origin's radians are constants folded in here rather than left to the engine.
 * Unlike `$geoNear` this reads every document the `$text` stage passed on — which is the point:
 * the index that narrowed the set was the text one.
 */
internal fun distanceFromExpression(origin: Coordinates): Document {
    val originLongitude = radians(origin.longitude)
    val originLatitude = radians(origin.latitude)

    val halfLatitudeDelta = half(subtract(documentLatitude(), originLatitude))
    val halfLongitudeDelta = half(subtract(documentLongitude(), originLongitude))

    val haversine = Document(
        "\$add",
        listOf(
            squaredSine(halfLatitudeDelta),
            Document(
                "\$multiply",
                listOf(cos(originLatitude), Document("\$cos", documentLatitude()), squaredSine(halfLongitudeDelta)),
            ),
        ),
    )

    // $asin is undefined above 1, and floating point takes the square root of a near-antipodal
    // pair fractionally past it. Ireland cannot reach that, but the origin is wherever the device
    // says it is, and a phone on the far side of the world would turn every distance into NaN.
    val chord = Document("\$min", listOf(1, Document("\$sqrt", haversine)))
    return Document("\$multiply", listOf(2 * EARTH_RADIUS_METRES, Document("\$asin", chord)))
}

/** GeoJSON stores longitude first, so the array indexes are not interchangeable. */
private fun documentLongitude() = Document("\$degreesToRadians", coordinateAt(0))

private fun documentLatitude() = Document("\$degreesToRadians", coordinateAt(1))

private fun coordinateAt(index: Int) = Document("\$arrayElemAt", listOf("\$loc.coordinates", index))

private fun squaredSine(radians: Document) = Document("\$pow", listOf(Document("\$sin", radians), 2))

private fun subtract(from: Document, constant: Double) = Document("\$subtract", listOf(from, constant))

private fun half(value: Document) = Document("\$divide", listOf(value, 2))

private fun radians(degrees: Double) = Math.toRadians(degrees)
