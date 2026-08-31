package io.github.jeroenvervaeke.coffeefinder.data.query

import io.github.jeroenvervaeke.coffeefinder.data.model.Confidence
import io.github.jeroenvervaeke.coffeefinder.data.model.PlaceCategory
import org.bson.Document

/**
 * What a place has to be, apart from where it is.
 *
 * One object rather than three parameters threaded through every query, because the count under
 * the map and the list under it have to be asking the same question: a filter that reached one
 * and not the other would put a number on screen that the rows below it contradict.
 *
 * Each field is a stage the console screen can switch on and off, which is why they end up as a
 * query document rather than being applied after the fact: the point of the screen is that the
 * engine did the filtering.
 */
data class PlaceCriteria(
    val category: PlaceCategory? = null,
    /** Overture's confidence in the place, from 0 to 1. Kept at or above this. */
    val minimumConfidence: Confidence? = null,
    /** Whether to keep only the 745 places Overture attached a chain to. */
    val brandedOnly: Boolean = false,
) {
    /**
     * These criteria as a query document, or `null` when they constrain nothing.
     *
     * `null` rather than an empty document: an empty `query` on a `$geoNear` and an absent one
     * mean the same thing to the engine, and the pipeline the console screen prints should not
     * carry a stage that says nothing.
     */
    fun asQuery(): Document? {
        val query = Document()
        category?.let { query.append("cat", it.stored) }
        minimumConfidence?.let { query.append("confidence", Document("\$gte", it.value)) }
        if (brandedOnly) query.append("brand", Document("\$exists", true))
        return query.takeIf { it.isNotEmpty() }
    }

    /** Whether anything is constrained at all, which decides whether a `$match` is worth a stage. */
    val isEmpty: Boolean get() = asQuery() == null

    companion object {
        /** Every coffee place, which is what the screen opens on. */
        val NONE = PlaceCriteria()
    }
}
