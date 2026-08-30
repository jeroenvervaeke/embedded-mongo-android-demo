package io.github.jeroenvervaeke.coffeefinder.data.query

import io.github.jeroenvervaeke.coffeefinder.data.model.Coordinates
import io.github.jeroenvervaeke.coffeefinder.data.model.Metres
import io.github.jeroenvervaeke.coffeefinder.data.model.PlaceCategory
import org.bson.Document

/**
 * The coffee places nearest [from], nearest first.
 *
 * `$geoNear` does the work the application would otherwise do badly: it walks the `2dsphere`
 * index outwards from the point, so it reads the [limit] documents it returns rather than all
 * 5,180 of them, and it emits them already ordered. It has to be the first stage — that index
 * walk is the source of the documents, not a filter over them — which is why [category] goes
 * into its own `query` option instead of into a `$match` in front of it.
 *
 * The `$limit` is a stage rather than `$geoNear`'s own deprecated `num`.
 */
fun nearestPipeline(
    from: Coordinates,
    limit: Int,
    maxDistance: Metres? = null,
    category: PlaceCategory? = null,
): List<Document> {
    require(limit > 0) { "asking for $limit places returns nothing" }
    val geoNear = Document("near", geoJsonPoint(from))
        .append("distanceField", DISTANCE_FIELD)
        .append("spherical", true)
    maxDistance?.let { geoNear.append("maxDistance", it.value) }
    category?.let { geoNear.append("query", Document("cat", it.stored)) }

    return listOf(
        Document("\$geoNear", geoNear),
        Document("\$limit", limit),
    )
}
