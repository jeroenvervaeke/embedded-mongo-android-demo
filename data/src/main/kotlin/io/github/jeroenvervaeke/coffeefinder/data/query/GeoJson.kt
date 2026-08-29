package io.github.jeroenvervaeke.coffeefinder.data.query

import io.github.jeroenvervaeke.coffeefinder.data.model.Coordinates
import io.github.jeroenvervaeke.coffeefinder.data.model.Viewport
import org.bson.Document

/** [coordinates] as the GeoJSON point `$geoNear` and `$geoWithin` take. */
internal fun geoJsonPoint(coordinates: Coordinates): Document =
    Document("type", "Point")
        .append("coordinates", listOf(coordinates.longitude, coordinates.latitude))

/**
 * [viewport] as a GeoJSON polygon.
 *
 * The ring is wound anticlockwise and closed by repeating its first corner, which is what the
 * specification asks for and what MongoDB reads as "the inside of this box" rather than as
 * everything else on Earth.
 */
internal fun geoJsonPolygon(viewport: Viewport): Document = Document("type", "Polygon")
    .append(
        "coordinates",
        listOf(
            listOf(
                listOf(viewport.west, viewport.south),
                listOf(viewport.east, viewport.south),
                listOf(viewport.east, viewport.north),
                listOf(viewport.west, viewport.north),
                listOf(viewport.west, viewport.south),
            ),
        ),
    )

/** An `aggregate` command over the places collection. The cursor options are the engine's own. */
internal fun aggregate(pipeline: List<Document>): Document =
    Document("aggregate", PLACES_COLLECTION)
        .append("pipeline", pipeline)
        .append("cursor", Document())
