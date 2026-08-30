package io.github.jeroenvervaeke.coffeefinder.data.query

import org.bson.Document

/** The field `$count` writes its answer into. */
const val COUNT_FIELD = "n"

/**
 * The same selection, counted in the engine instead of read out of it.
 *
 * The headline on the map is how many places are inside the radius, and the list under it is the
 * first fifty of them. Counting by asking for the documents and calling `size` would be a
 * different question with a `$limit` in it; this appends `$count` to the pipeline the list was
 * built from instead, so the two cannot drift apart.
 *
 * [stages] must be the uncapped form of that pipeline — the builders leave the `$limit` off when
 * they are given a `null` limit — because a `$count` after a `$limit` counts the cap.
 */
fun counting(stages: List<Document>): List<Document> {
    require(stages.none { it.containsKey("\$limit") }) {
        "counting a pipeline that still has its \$limit would count the cap, not the matches"
    }
    return stages + Document("\$count", COUNT_FIELD)
}
