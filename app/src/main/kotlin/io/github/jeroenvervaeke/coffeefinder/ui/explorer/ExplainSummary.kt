package io.github.jeroenvervaeke.coffeefinder.ui.explorer

import org.bson.Document

/** One line of an explain plan, as the screen prints it. */
data class ExplainRow(val label: String, val value: String)

/**
 * The few numbers worth pulling out of an explain plan, in the order a person reads them.
 *
 * Everything here is optional, because an explain reply is shaped by the pipeline it explains:
 * a `$group` has no index to name, and a version of the engine may nest the same field somewhere
 * else. Anything not found is left out rather than guessed at, and the whole plan is printed
 * underneath either way, so this is a summary of a document that is also on screen, not a
 * substitute for it.
 */
fun explainSummary(plan: Document): List<ExplainRow> {
    // An aggregation explains itself as a list of stages, and everything worth reading is inside
    // the first of them -- `$cursor` for an ordinary pipeline, `$geoNearCursor` for one that
    // starts at the index. A find-shaped plan carries the same fields at the top instead, so both
    // are searched and the first hit wins.
    val roots = listOfNotNull(plan, plan.firstStagePlan())

    return buildList {
        plan.path("command", "aggregate")?.let { add(ExplainRow("namespace", "coffee.$it")) }
        roots.firstValueOf(INDEX_NAMES)?.let { add(ExplainRow("indexName", it)) }
        roots.firstValueOf(STAGE_NAMES)?.let { add(ExplainRow("stage", it)) }
        roots.firstValueOf(RETURNED)?.let { add(ExplainRow("nReturned", it)) }
        roots.firstValueOf(EXAMINED)?.let { add(ExplainRow("totalDocsExamined", it)) }
        roots.firstValueOf(MILLIS)?.let { add(ExplainRow("executionTimeMillis", it)) }
    }
}

/**
 * What the first stage of an aggregation explain holds, which is the plan for the query under it.
 *
 * The stage is named for what it is (`$cursor`, `$geoNearCursor`), so the name is read rather
 * than assumed: a version of the engine that adds another kind of cursor stage still works here.
 */
private fun Document.firstStagePlan(): Document? {
    val stages = this["stages"] as? List<*> ?: return null
    val first = stages.firstOrNull() as? Document ?: return null
    return first.values.filterIsInstance<Document>().firstOrNull()
}

/** The first of [paths] any of these roots actually carries, as text. */
private fun List<Document>.firstValueOf(paths: List<List<String>>): String? =
    paths.firstNotNullOfOrNull { path -> firstNotNullOfOrNull { root -> root.path(*path.toTypedArray()) } }

/**
 * A value several levels down, or `null` if any level is missing or is not a document.
 *
 * Written here rather than with `getEmbedded`, which throws when the path runs into something
 * that is not a document, and an explain plan is exactly the reply whose shape cannot be
 * assumed.
 */
private fun Document.path(vararg keys: String): String? {
    var current: Any? = this
    keys.forEach { key -> current = (current as? Document)?.get(key) ?: return null }
    return current?.toString()
}

private val INDEX_NAMES = listOf(
    listOf("queryPlanner", "winningPlan", "inputStage", "indexName"),
    listOf("queryPlanner", "winningPlan", "queryPlan", "inputStage", "indexName"),
    listOf("queryPlanner", "winningPlan", "indexName"),
)

private val STAGE_NAMES = listOf(
    listOf("queryPlanner", "winningPlan", "inputStage", "stage"),
    listOf("queryPlanner", "winningPlan", "stage"),
)

private val RETURNED = listOf(
    listOf("executionStats", "nReturned"),
    listOf("stages", "nReturned"),
)

private val EXAMINED = listOf(listOf("executionStats", "totalDocsExamined"))

private val MILLIS = listOf(listOf("executionStats", "executionTimeMillis"))
