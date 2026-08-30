package io.github.jeroenvervaeke.coffeefinder.ui.console

import io.github.jeroenvervaeke.coffeefinder.data.query.LOCATION_INDEX
import io.github.jeroenvervaeke.coffeefinder.data.query.NAME_INDEX
import org.bson.Document

/** What a `COLLSCAN` is called in an explain plan, which is what to say when no index can serve. */
const val NO_INDEX = "COLLSCAN"

/**
 * The stages of an `aggregate` command, as the console prints them across the top of the map.
 *
 * Read out of the command that was sent rather than rebuilt from what the screen thinks it asked
 * for: the whole claim the console makes is that the line above the map is the pipeline below it.
 *
 * Each stage is its operator plus the one word that says which one it is — `$match cat`,
 * `$limit 50` — because `$match → $match → $match` tells a reader nothing.
 */
fun Document.stageLabels(): List<String> = stages().map(::label)

/**
 * Which index served the pipeline in this command.
 *
 * A reading rather than a guess for the two shapes this application sends: `$geoNear` can only be
 * answered by the one `2dsphere` index in the collection, and `$text` by the one text index —
 * MongoDB refuses both stages outright when their index is missing. Anything else is a collection
 * scan as far as this is concerned; a pipeline somebody typed is explained by the engine on the
 * explorer screen instead, which is where a guess would not be good enough.
 */
fun Document.indexBehind(): String {
    val first = stages().firstOrNull() ?: return NO_INDEX
    val name = first.keys.firstOrNull()
    val argument = first[name]
    return when {
        name == "\$geoNear" -> LOCATION_INDEX
        name == "\$match" && argument is Document && argument.containsKey("\$text") -> NAME_INDEX
        name == "\$match" && argument is Document && argument.mentionsGeometry() -> LOCATION_INDEX
        else -> NO_INDEX
    }
}

/** The pipeline of an `aggregate` command, or nothing at all when this is not one. */
private fun Document.stages(): List<Document> =
    (this["pipeline"] as? List<*>).orEmpty().filterIsInstance<Document>()

private fun label(stage: Document): String {
    val name = stage.keys.firstOrNull() ?: return "?"
    return when (val argument = stage[name]) {
        is Number -> "$name $argument"
        is String -> "$name $argument"
        is Document -> argument.keys.firstOrNull()?.let { "$name $it" } ?: name
        else -> name
    }
}

/** Whether a `$match` selects on a shape, which only the `2dsphere` index can answer. */
private fun Document.mentionsGeometry(): Boolean =
    values.filterIsInstance<Document>().any { it.containsKey("\$geoWithin") }
