package io.github.jeroenvervaeke.coffeefinder.data.query

import io.github.jeroenvervaeke.coffeefinder.data.model.Coordinates
import io.github.jeroenvervaeke.coffeefinder.data.model.Metres
import io.github.jeroenvervaeke.coffeefinder.data.model.PlaceCategory
import org.bson.Document

/**
 * Coffee places whose name or brand matches [text], nearest [from] first.
 *
 * `$text` reads the text index built over `name` and `brand`, and like `$geoNear` it only works
 * as the first stage of a pipeline -- `$geoNear` will not even take a `$text` in its `query`
 * option. So the two indexes are not combined: the text one selects, and
 * [distanceFromExpression] measures what it selected.
 *
 * The `$sort` is on distance rather than on relevance, because "the nearest place called X" is
 * the question a person standing in a street is asking, and a chain's branches are all equally
 * good matches for its name.
 */
fun searchCommand(
    text: String,
    from: Coordinates,
    limit: Int,
    category: PlaceCategory? = null,
    maxDistance: Metres? = null,
): Document {
    require(text.isNotBlank()) { "an empty search matches every document rather than none" }
    require(limit > 0) { "asking for $limit places returns nothing" }

    val pipeline = mutableListOf(Document("\$match", Document("\$text", Document("\$search", text))))
    // A second stage rather than another field in the first: `$text` has to stand alone at the
    // head of the pipeline, and this filter is a plain equality over what it already matched.
    category?.let { pipeline += Document("\$match", Document("cat", it.stored)) }
    pipeline += Document("\$set", Document(DISTANCE_FIELD, distanceFromExpression(from)))
    // `$geoNear` takes its own `maxDistance`; here the distance is a field this pipeline
    // computed, so cutting it off is an ordinary `$match` over that field.
    maxDistance?.let {
        pipeline += Document("\$match", Document(DISTANCE_FIELD, Document("\$lte", it.value)))
    }
    pipeline += Document("\$sort", Document(DISTANCE_FIELD, 1))
    pipeline += Document("\$limit", limit)

    return aggregate(pipeline)
}
