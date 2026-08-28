package io.github.jeroenvervaeke.coffeefinder.data.query

import io.github.jeroenvervaeke.coffeefinder.data.model.Viewport
import org.bson.Document

/**
 * Every coffee place inside [viewport], up to [limit] of them.
 *
 * This is what the map asks as it is panned and zoomed: `$geoWithin` against the same `2dsphere`
 * index `$geoNear` walks, but selecting a region rather than ordering by distance from a point —
 * so nothing is sorted and nothing is measured, which is all the map needs to draw a dot.
 *
 * A `$project` keeps the reply to what a dot and its label need. Over a few thousand documents
 * that is the difference between the addresses crossing the JNI bridge and not.
 */
fun viewportCommand(viewport: Viewport, limit: Int): Document {
    require(limit > 0) { "asking for $limit places returns nothing" }

    return aggregate(
        listOf(
            Document(
                "\$match",
                Document("loc", Document("\$geoWithin", Document("\$geometry", geoJsonPolygon(viewport)))),
            ),
            Document("\$limit", limit),
            Document(
                "\$project",
                Document("name", 1).append("cat", 1).append("brand", 1)
                    .append("confidence", 1).append("loc", 1),
            ),
        ),
    )
}
