package io.github.jeroenvervaeke.coffeefinder.data.query

import org.bson.Document

/** The `2dsphere` index `$geoNear` walks and `$geoWithin` selects from. */
const val LOCATION_INDEX = "loc_2dsphere"

/** The text index `$text` reads, over the two fields a person searches by. */
const val NAME_INDEX = "name_brand_text"

/**
 * Builds both indexes the application queries through.
 *
 * One command rather than two: `createIndexes` takes a list, and a half-indexed collection is a
 * state no screen has a sensible thing to show for. It is idempotent — an index that already
 * exists with the same specification is reported, not rebuilt — which is what lets this run on
 * every start rather than only after seeding.
 *
 * The weights say a match on the place's own name beats a match on the chain it belongs to, so
 * searching "insomnia" ranks the branch called that above every branch of a chain called that.
 */
fun createIndexesCommand(): Document = Document("createIndexes", PLACES_COLLECTION)
    .append(
        "indexes",
        listOf(
            Document("key", Document("loc", "2dsphere")).append("name", LOCATION_INDEX),
            Document("key", Document("name", "text").append("brand", "text"))
                .append("name", NAME_INDEX)
                .append("weights", Document("name", 10).append("brand", 5)),
        ),
    )

/** How many places are stored, which is how seeding decides whether it has anything to do. */
fun countPlacesCommand(): Document = Document("count", PLACES_COLLECTION)

/**
 * Inserts one batch of seed documents.
 *
 * `ordered: false` because the batches are independent: a document the engine rejects should cost
 * that document rather than the rest of the batch behind it.
 */
fun insertPlacesCommand(documents: List<Document>): Document {
    require(documents.isNotEmpty()) { "an insert of no documents is a command the engine rejects" }
    return Document("insert", PLACES_COLLECTION)
        .append("documents", documents)
        .append("ordered", false)
}
