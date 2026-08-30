package io.github.jeroenvervaeke.coffeefinder.data

import io.github.jeroenvervaeke.coffeefinder.data.model.Coordinates
import io.github.jeroenvervaeke.coffeefinder.data.model.Metres
import io.github.jeroenvervaeke.coffeefinder.data.model.NearbyPlace
import io.github.jeroenvervaeke.coffeefinder.data.model.Place
import io.github.jeroenvervaeke.coffeefinder.data.model.PlaceCategory
import io.github.jeroenvervaeke.coffeefinder.data.model.Viewport
import io.github.jeroenvervaeke.coffeefinder.data.parse.PlaceFormatException
import io.github.jeroenvervaeke.coffeefinder.data.parse.toNearbyPlace
import io.github.jeroenvervaeke.coffeefinder.data.parse.toPlace
import io.github.jeroenvervaeke.coffeefinder.data.query.COUNT_FIELD
import io.github.jeroenvervaeke.coffeefinder.data.query.PlaceCriteria
import io.github.jeroenvervaeke.coffeefinder.data.query.TALLY_FIELD
import io.github.jeroenvervaeke.coffeefinder.data.query.categoryTallyPipeline
import io.github.jeroenvervaeke.coffeefinder.data.query.counting
import io.github.jeroenvervaeke.coffeefinder.data.query.nearestPipeline
import io.github.jeroenvervaeke.coffeefinder.data.query.searchPipeline
import io.github.jeroenvervaeke.coffeefinder.data.query.viewportPipeline
import io.github.jeroenvervaeke.embeddedmongodb.MongoCollection
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import org.bson.Document

/**
 * Results, and the command that produced them.
 *
 * The command travels with its own results rather than being rebuilt for display, so the pipeline
 * the application shows cannot drift from the one the engine actually ran — which is the whole
 * claim the pipeline screen is making. It comes from `AggregateQuery.command()`, which is what
 * the library sends rather than a description of it.
 */
data class Queried<T>(val results: List<T>, val command: Document)

/**
 * The four questions this application asks about coffee places.
 *
 * Each is a pipeline handed to [places] and read back as domain types, by handing the parsing
 * function to the query itself. The library builds the `aggregate` command, opens the cursor and
 * pages it; what is left here is the pipeline, the parsing, and where the parsing runs.
 *
 * [decodeOn] is where replies are turned into [Place]s. It matters: without it the parsing runs
 * wherever the *collector* is, which on Android is the main thread — six thousand documents of
 * BSON on it for every settled pan of the map.
 *
 * `withContext` rather than `flowOn`, and the difference is the whole point: `flowOn` moves the
 * upstream, while the terminal `toList` still resumes in the caller's context. Both finders
 * collect from `stateIn(viewModelScope, …)`, so that context is `Main.immediate`.
 */
class PlaceRepository(
    private val places: MongoCollection,
    private val decodeOn: CoroutineDispatcher = Dispatchers.Default,
) {
    suspend fun nearest(
        from: Coordinates,
        limit: Int?,
        maxDistance: Metres? = null,
        criteria: PlaceCriteria = PlaceCriteria.NONE,
    ): Queried<NearbyPlace> =
        run(nearestPipeline(from, limit, maxDistance, criteria), Document::toNearbyPlace)

    suspend fun search(
        text: String,
        from: Coordinates,
        limit: Int?,
        criteria: PlaceCriteria = PlaceCriteria.NONE,
        maxDistance: Metres? = null,
    ): Queried<NearbyPlace> =
        run(searchPipeline(text, from, limit, criteria, maxDistance), Document::toNearbyPlace)

    /**
     * How many places the same question matches, uncapped.
     *
     * The number the map leads with, and the reason it is a query of its own: the list is fifty
     * documents because a screen holds fifty, while "how many are within a kilometre" is a
     * property of the radius. `$count` answers it in the engine, so the phone is never sent 400
     * documents to call `size` on.
     *
     * The empty [text] takes the same `$geoNear` the list did; a search takes the same `$text`.
     * Either way it is [counting] applied to the pipeline that produced the rows.
     */
    suspend fun count(
        from: Coordinates,
        within: Metres,
        criteria: PlaceCriteria = PlaceCriteria.NONE,
        text: String = "",
    ): Long {
        val selection = if (text.isBlank()) {
            nearestPipeline(from, limit = null, maxDistance = within, criteria = criteria)
        } else {
            searchPipeline(text, from, limit = null, criteria = criteria, maxDistance = within)
        }
        // No row at all is the shape a `$count` over an empty selection has, and it is zero.
        // Collected rather than asked for through `firstOrNull`, which would append a `$limit`
        // to a pipeline that already emits at most one row.
        val counted = places.aggregate(counting(selection)).asFlow().firstOrNull() ?: return 0
        return (counted[COUNT_FIELD] as? Number)?.toLong()
            ?: throw IllegalStateException("the count reply carried no `$COUNT_FIELD`")
    }

    suspend fun inViewport(viewport: Viewport, limit: Int): Queried<Place> =
        run(viewportPipeline(viewport, limit), Document::toPlace)

    /**
     * How many places of each category the collection holds.
     *
     * Measured rather than remembered: the about screen draws this, and a distribution written
     * into the source would be a claim about a seed rather than a reading of the one installed.
     * A category the application does not know is a seed and an application that disagree, which
     * is a failure and not a slice to leave out.
     */
    suspend fun categoryTally(): Map<PlaceCategory, Long> = withContext(decodeOn) {
        places.aggregate(categoryTallyPipeline()).asFlow().toList().associate { row ->
            val stored = row["_id"] as? String
                ?: throw PlaceFormatException("a tally row carried no category")
            val category = PlaceCategory.of(stored)
                ?: throw PlaceFormatException("the tally holds the unknown category `$stored`")
            category to ((row[TALLY_FIELD] as? Number)?.toLong() ?: 0L)
        }
    }

    private suspend fun <T> run(pipeline: List<Document>, read: (Document) -> T): Queried<T> {
        val query = places.aggregate(pipeline)
        return withContext(decodeOn) { Queried(query.asFlow(read).toList(), query.command()) }
    }
}
