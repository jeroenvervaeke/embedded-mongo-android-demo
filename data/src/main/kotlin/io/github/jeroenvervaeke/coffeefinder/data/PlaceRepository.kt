package io.github.jeroenvervaeke.coffeefinder.data

import io.github.jeroenvervaeke.coffeefinder.data.model.Coordinates
import io.github.jeroenvervaeke.coffeefinder.data.model.Metres
import io.github.jeroenvervaeke.coffeefinder.data.model.NearbyPlace
import io.github.jeroenvervaeke.coffeefinder.data.model.Place
import io.github.jeroenvervaeke.coffeefinder.data.model.PlaceCategory
import io.github.jeroenvervaeke.coffeefinder.data.model.Viewport
import io.github.jeroenvervaeke.coffeefinder.data.parse.toNearbyPlace
import io.github.jeroenvervaeke.coffeefinder.data.parse.toPlace
import io.github.jeroenvervaeke.coffeefinder.data.query.nearestCommand
import io.github.jeroenvervaeke.coffeefinder.data.query.searchCommand
import io.github.jeroenvervaeke.coffeefinder.data.query.viewportCommand
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
 * claim the pipeline screen is making.
 */
data class Queried<T>(val results: List<T>, val command: Document)

/**
 * The three questions this application asks about coffee places.
 *
 * [decodeOn] is where replies are turned into [io.github.jeroenvervaeke.coffeefinder.data.model.Place]s.
 * It matters: without it the parsing runs wherever the *collector* is, which on Android is the
 * main thread — six thousand documents of BSON on it for every settled pan of the map.
 *
 * `withContext` rather than `flowOn`, and the difference is the whole point: `flowOn` moves the
 * upstream, while the terminal `toList` still resumes in the caller's context. Both finders
 * collect from `stateIn(viewModelScope, …)`, so that context is `Main.immediate`.
 */
class PlaceRepository(
    private val mongo: MongoSeam,
    private val decodeOn: CoroutineDispatcher = Dispatchers.Default,
) {
    suspend fun nearest(
        from: Coordinates,
        limit: Int,
        maxDistance: Metres? = null,
        category: PlaceCategory? = null,
    ): Queried<NearbyPlace> =
        run(nearestCommand(from, limit, maxDistance, category), Document::toNearbyPlace)

    suspend fun search(
        text: String,
        from: Coordinates,
        limit: Int,
        category: PlaceCategory? = null,
        maxDistance: Metres? = null,
    ): Queried<NearbyPlace> =
        run(searchCommand(text, from, limit, category, maxDistance), Document::toNearbyPlace)

    suspend fun inViewport(viewport: Viewport, limit: Int): Queried<Place> =
        run(viewportCommand(viewport, limit), Document::toPlace)

    private suspend fun <T> run(command: Document, read: (Document) -> T): Queried<T> =
        withContext(decodeOn) { Queried(mongo.documents(command).map(read).toList(), command) }
}
