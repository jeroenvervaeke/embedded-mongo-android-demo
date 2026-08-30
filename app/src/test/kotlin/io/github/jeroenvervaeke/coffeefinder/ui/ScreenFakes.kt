package io.github.jeroenvervaeke.coffeefinder.ui

import io.github.jeroenvervaeke.coffeefinder.data.PlaceRepository
import io.github.jeroenvervaeke.coffeefinder.data.explorer.PipelineExplorer
import io.github.jeroenvervaeke.coffeefinder.data.finder.MapFinder
import io.github.jeroenvervaeke.coffeefinder.data.finder.NearbyFinder
import io.github.jeroenvervaeke.coffeefinder.data.query.COFFEE_DATABASE
import io.github.jeroenvervaeke.coffeefinder.data.query.places
import io.github.jeroenvervaeke.embeddedmongodb.CommandRunner
import io.github.jeroenvervaeke.embeddedmongodb.MongoCollection
import io.github.jeroenvervaeke.embeddedmongodb.MongoDatabase
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.bson.Document

/**
 * An engine for the screen tests: it answers a `$count` with a number and everything else with
 * places, and remembers what it was asked.
 *
 * The same seam the data layer's tests use — the library's [CommandRunner] — so a screen can be
 * driven with no engine, no emulator and no seed, while every pipeline it sends is real.
 */
class FakeEngine(
    private val results: List<Document> = listOf(placeReply()),
    private val matching: Int = 1,
    private val failWith: String? = null,
) : CommandRunner {
    private val issued = mutableListOf<Document>()

    val commands: List<Document> get() = issued

    /** The last pipeline that was not a count, which is the one behind what is on screen. */
    val lastResultPipeline: List<Document>
        get() = issued.last { !it.counts() }.pipeline()

    val collection: MongoCollection get() = MongoDatabase(this, COFFEE_DATABASE).places()

    override suspend fun runCommand(database: String, command: Document): Document {
        issued += command
        failWith?.let { throw IllegalStateException(it) }
        val rows = if (command.counts()) listOf(Document("n", matching)) else results
        return Document("ok", 1.0).append(
            "cursor",
            Document("id", 0L)
                .append("ns", "$COFFEE_DATABASE.places")
                .append("firstBatch", rows),
        )
    }
}

/** Everything a ready application holds, wired onto [engine]. */
class FakeStartup(
    val engine: FakeEngine = FakeEngine(),
    private val scheduler: TestCoroutineScheduler = TestCoroutineScheduler(),
) {
    private val dispatcher = UnconfinedTestDispatcher(scheduler)
    private val scope = CoroutineScope(dispatcher)

    val places = PlaceRepository(engine.collection, dispatcher)

    val nearby = NearbyFinder(places, scope)

    val map = MapFinder(places, scope)

    val explorer = PipelineExplorer(engine.collection)

    val ready = Startup.Ready(nearby, map, explorer, places, STARTUP_PHASES)

    /** Runs the debounces and the queries behind them, in virtual time. */
    fun settle() = scheduler.advanceUntilIdle()
}

/** A stored place, in the shape the seed writes and every pipeline returns. */
fun placeReply(
    id: String = "0024f54f-43a8-49f8-bdce-a22076983f95",
    name: String = "Two Pups Coffee",
    category: String = "coffee_shop",
    confidence: Double = 0.93,
    longitude: Double = -6.2769,
    latitude: Double = 53.3369,
    distance: Double = 241.0,
    brand: String? = null,
): Document = Document("_id", id)
    .append("name", name)
    .append("cat", category)
    .also { document -> brand?.let { document.append("brand", it) } }
    .append("confidence", confidence)
    .append("addr", Document("street", "Francis Street").append("locality", "Dublin 8"))
    .append("loc", Document("type", "Point").append("coordinates", listOf(longitude, latitude)))
    .append("distance", distance)

/** The stages of an `aggregate` command. */
fun Document.pipeline(): List<Document> =
    (this["pipeline"] as? List<*>).orEmpty().filterIsInstance<Document>()

/** Whether a command is the `$count` behind the headline rather than a query for documents. */
fun Document.counts(): Boolean = pipeline().any { it.containsKey("\$count") }

/** What a launch reports having cost, for a screen that shows it. */
val STARTUP_PHASES = listOf(
    StartupPhase("opening the engine", 1_200.milliseconds),
    StartupPhase("building the 2dsphere and text indexes", 85.milliseconds),
)
