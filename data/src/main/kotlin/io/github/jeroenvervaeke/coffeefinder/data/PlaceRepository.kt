package io.github.jeroenvervaeke.coffeefinder.data

import io.github.jeroenvervaeke.coffeefinder.data.model.Coordinates
import io.github.jeroenvervaeke.coffeefinder.data.model.Metres
import io.github.jeroenvervaeke.coffeefinder.data.model.NearbyPlace
import io.github.jeroenvervaeke.coffeefinder.data.model.Place
import io.github.jeroenvervaeke.coffeefinder.data.model.PlaceCategory
import io.github.jeroenvervaeke.coffeefinder.data.model.Viewport
import io.github.jeroenvervaeke.coffeefinder.data.parse.toNearbyPlace
import io.github.jeroenvervaeke.coffeefinder.data.parse.toPlace
import io.github.jeroenvervaeke.coffeefinder.data.query.nearestPipeline
import io.github.jeroenvervaeke.coffeefinder.data.query.searchPipeline
import io.github.jeroenvervaeke.coffeefinder.data.query.viewportPipeline
import io.github.jeroenvervaeke.embeddedmongodb.MongoCollection
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
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
 * The three questions this application asks about coffee places.
 *
 * Each is a pipeline handed to [places] and read back as domain types. The library builds the
 * `aggregate` command, opens the cursor and pages it; what is left here is the pipeline, the
 * parsing, and where the parsing runs.
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
        limit: Int,
        maxDistance: Metres? = null,
        category: PlaceCategory? = null,
    ): Queried<NearbyPlace> =
        run(nearestPipeline(from, limit, maxDistance, category), Document::toNearbyPlace)

    suspend fun search(
        text: String,
        from: Coordinates,
        limit: Int,
        category: PlaceCategory? = null,
        maxDistance: Metres? = null,
    ): Queried<NearbyPlace> =
        run(searchPipeline(text, from, limit, category, maxDistance), Document::toNearbyPlace)

    suspend fun inViewport(viewport: Viewport, limit: Int): Queried<Place> =
        run(viewportPipeline(viewport, limit), Document::toPlace)

    private suspend fun <T> run(pipeline: List<Document>, read: (Document) -> T): Queried<T> {
        val query = places.aggregate(pipeline)
        return withContext(decodeOn) { Queried(query.asFlow().map(read).toList(), query.command()) }
    }
}
