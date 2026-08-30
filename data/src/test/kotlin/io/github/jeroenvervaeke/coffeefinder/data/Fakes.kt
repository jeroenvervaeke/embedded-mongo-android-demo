package io.github.jeroenvervaeke.coffeefinder.data

import io.github.jeroenvervaeke.coffeefinder.data.model.Coordinates
import io.github.jeroenvervaeke.coffeefinder.data.model.Ireland
import io.github.jeroenvervaeke.coffeefinder.data.query.COFFEE_DATABASE
import io.github.jeroenvervaeke.coffeefinder.data.query.PLACES_COLLECTION
import io.github.jeroenvervaeke.coffeefinder.data.query.places
import io.github.jeroenvervaeke.coffeefinder.data.query.seedMarkers
import io.github.jeroenvervaeke.embeddedmongodb.CommandRunner
import io.github.jeroenvervaeke.embeddedmongodb.MongoDatabase
import java.util.concurrent.Executors
import kotlin.time.Duration
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import org.bson.Document

/**
 * A [CommandRunner] that answers from Kotlin and records what it was asked.
 *
 * The same seam the library's own tests use, one level up: there it stands in for the JNI bridge,
 * here for the whole engine. Everything above it — pipelines, parsing, seeding, screen state — is
 * therefore testable with no engine at all.
 *
 * [queryResults] answers the commands that open a cursor, wrapped in the reply shape the engine
 * uses, and [commandReply] answers the rest. An insert is answered with the `n` a real engine
 * reports, because the library checks it.
 */
class FakeMongo(
    private val commandReply: (Document) -> Document = ::insertAware,
    private val queryResults: (Document) -> List<Document> = { emptyList() },
    /**
     * How long the engine takes to answer, for the tests that measure that.
     *
     * A `delay` rather than a sleep, so it costs virtual time on the test scheduler and no real
     * time at all -- which is what lets a test assert an exact duration.
     */
    private val answersIn: Duration = Duration.ZERO,
) : CommandRunner {
    private val issued = mutableListOf<Document>()
    private val ran = mutableListOf<String>()

    val commands: List<Document> get() = issued

    val lastCommand: Document get() = issued.last()

    /** The threads the engine was reached on, which is how "not on the caller's thread" is checked. */
    val threads: List<String> get() = ran

    /** The database every query in this application goes through, and the two collections in it. */
    val database = MongoDatabase(this, COFFEE_DATABASE)

    val places = database.places()

    val markers = database.seedMarkers()

    override suspend fun runCommand(database: String, command: Document): Document {
        issued += command
        ran += Thread.currentThread().name
        delay(answersIn)
        return if (command.opensCursor()) cursorReply(command) else commandReply(command)
    }

    private fun cursorReply(command: Document): Document = okReply(
        "cursor" to Document("id", EXHAUSTED)
            .append("ns", "$COFFEE_DATABASE.${command.collection()}")
            .append("firstBatch", queryResults(command)),
    )
}

/** The engine reports a cursor holding nothing more as id 0, which is every reply this fake gives. */
private const val EXHAUSTED = 0L

/** The commands whose reply is a cursor rather than a result. */
private fun Document.opensCursor(): Boolean =
    keys.first() in setOf("aggregate", "find", "listIndexes", "listCollections")

/** The collection a command names, which is the value of its first field. */
private fun Document.collection(): String = values.first() as? String ?: PLACES_COLLECTION

/**
 * The default reply: `ok`, plus the `n` an insert is expected to carry.
 *
 * Without the count the library would report every insert as having stored fewer documents than
 * it was given, which is a check worth having and not one worth restating in every test.
 */
private fun insertAware(command: Document): Document =
    if (command.containsKey("insert")) {
        okReply("n" to (command["documents"] as List<*>).size)
    } else {
        okReply()
    }

fun okReply(vararg fields: Pair<String, Any?>): Document =
    Document("ok", 1.0).also { reply -> fields.forEach { (key, value) -> reply[key] = value } }

/** A stored place, in the shape the seed writes and every pipeline returns. */
fun placeDocument(
    id: String = "0024f54f-43a8-49f8-bdce-a22076983f95",
    name: String = "The House Of Pretzels",
    category: String = "coffee_shop",
    confidence: Double = 0.77,
    longitude: Double = -7.3328486165998115,
    latitude: Double = 52.78725023958558,
    brand: String? = null,
    address: Document? = Document("street", "Market Cross").append("locality", "Kilkenny"),
): Document = Document("_id", id)
    .append("name", name)
    .append("cat", category)
    .also { document -> brand?.let { document.append("brand", it) } }
    .append("confidence", confidence)
    .also { document -> address?.let { document.append("addr", it) } }
    .append("loc", Document("type", "Point").append("coordinates", listOf(longitude, latitude)))

/** The stages of an `aggregate` command, which is what the library built around a pipeline. */
fun Document.pipeline(): List<Document> {
    @Suppress("UNCHECKED_CAST")
    return this["pipeline"] as List<Document>
}

/** The first stage carrying [name]. First rather than only: a pipeline can hold two `$match`es. */
fun List<Document>.stage(name: String): Document =
    first { it.containsKey(name) }.get(name) as Document

/** The same Dublin the application falls back to, not a second copy of its coordinates. */
val DUBLIN = Ireland.DUBLIN

val CORK = Coordinates(longitude = -8.4756, latitude = 51.8985)

/**
 * A dispatcher backed by one thread with a name worth asserting on.
 *
 * Used to check that work which must not run on the caller's thread does not: on Android the
 * caller is the main thread, and neither BSON decoding nor gzip inflation belongs there.
 */
fun namedDispatcher(name: String): ExecutorCoroutineDispatcher =
    Executors.newSingleThreadExecutor { runnable -> Thread(runnable, name) }.asCoroutineDispatcher()

/**
 * A repository whose decoding runs on the test scheduler rather than on `Dispatchers.Default`.
 *
 * Without this the finders' debounces would be advancing virtual time while the decoding ran on
 * real threads, which is how a suite starts failing one run in fifty.
 */
fun TestScope.placesIn(mongo: FakeMongo) =
    PlaceRepository(mongo.places, StandardTestDispatcher(testScheduler))
