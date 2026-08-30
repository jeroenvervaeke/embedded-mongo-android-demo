package io.github.jeroenvervaeke.coffeefinder.data.query

import io.github.jeroenvervaeke.embeddedmongodb.IndexModel
import io.github.jeroenvervaeke.embeddedmongodb.IndexOptions
import io.github.jeroenvervaeke.embeddedmongodb.Indexes
import org.bson.Document

/** The `2dsphere` index `$geoNear` walks and `$geoWithin` selects from. */
const val LOCATION_INDEX = "loc_2dsphere"

/** The text index `$text` reads, over the two fields a person searches by. */
const val NAME_INDEX = "name_brand_text"

/**
 * Both indexes the application queries through.
 *
 * Named rather than left to MongoDB's own naming so that the two constants above can be checked
 * against what `listIndexes` reports.
 *
 * The weights say a match on the place's own name beats a match on the chain it belongs to, so
 * searching "insomnia" ranks the branch called that above every branch of a chain called that.
 */
fun placeIndexes(): List<IndexModel> = listOf(
    IndexModel(Indexes.geo2dsphere("loc"), IndexOptions(name = LOCATION_INDEX)),
    IndexModel(
        // One compound text index rather than two, because a collection may hold only one.
        Indexes.compoundIndex(Indexes.text("name"), Indexes.text("brand")),
        IndexOptions(name = NAME_INDEX, weights = Document("name", 10).append("brand", 5)),
    ),
)
