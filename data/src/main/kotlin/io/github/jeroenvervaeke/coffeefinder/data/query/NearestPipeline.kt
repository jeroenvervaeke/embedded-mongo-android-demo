package io.github.jeroenvervaeke.coffeefinder.data.query

import io.github.jeroenvervaeke.coffeefinder.data.model.Coordinates
import io.github.jeroenvervaeke.coffeefinder.data.model.Metres
import org.bson.Document

/**
 * The coffee places nearest [from], nearest first.
 *
 * `$geoNear` does the work the application would otherwise do badly: it walks the `2dsphere`
 * index outwards from the point, so it reads the [limit] documents it returns rather than all
 * 5,180 of them, and it emits them already ordered. It has to be the first stage (that index
 * walk is the source of the documents, not a filter over them), which is why [criteria] goes
 * into its own `query` option instead of into a `$match` in front of it.
 *
 * The `$limit` is a stage rather than `$geoNear`'s own deprecated `num`, and a `null` [limit]
 * leaves it off entirely: that is the same selection counted rather than listed, and the count
 * has to see everything the radius holds.
 */
fun nearestPipeline(
    from: Coordinates,
    limit: Int?,
    maxDistance: Metres? = null,
    criteria: PlaceCriteria = PlaceCriteria.NONE,
): List<Document> {
    require(limit == null || limit > 0) { "asking for $limit places returns nothing" }
    val geoNear = Document("near", geoJsonPoint(from))
        .append("distanceField", DISTANCE_FIELD)
        .append("spherical", true)
    maxDistance?.let { geoNear.append("maxDistance", it.value) }
    criteria.asQuery()?.let { geoNear.append("query", it) }

    return buildList {
        add(Document("\$geoNear", geoNear))
        limit?.let { add(Document("\$limit", it)) }
    }
}
