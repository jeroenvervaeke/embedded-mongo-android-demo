package io.github.jeroenvervaeke.coffeefinder.data.query

import org.bson.Document

/** The field the tally counts into. */
const val TALLY_FIELD = "n"

/**
 * How many places there are of each category, most first.
 *
 * What the about screen draws its donut from. A `$group` rather than four counts, and a query
 * rather than four numbers written into the source: the distribution is a property of whatever
 * seed is installed, and a figure on a screen that nothing measured is a figure that goes stale
 * the first time the seed is rebuilt.
 */
fun categoryTallyPipeline(): List<Document> = listOf(
    Document("\$group", Document("_id", "\$cat").append(TALLY_FIELD, Document("\$sum", 1))),
    Document("\$sort", Document(TALLY_FIELD, -1)),
)
