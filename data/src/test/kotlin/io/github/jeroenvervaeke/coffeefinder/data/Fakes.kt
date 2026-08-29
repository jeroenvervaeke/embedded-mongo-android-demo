package io.github.jeroenvervaeke.coffeefinder.data

import io.github.jeroenvervaeke.coffeefinder.data.model.Coordinates
import io.github.jeroenvervaeke.coffeefinder.data.model.Ireland
import java.util.concurrent.Executors
import kotlin.time.Duration
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import org.bson.Document

/**
 * A [MongoSeam] that answers from Kotlin and records what it was asked.
 *
 * The same shape as the library's own `FakeEngine`, one level up: there the seam is BSON bytes,
 * here it is the commands this application sends. Everything above it — pipelines, parsing,
 * seeding, screen state — is therefore testable with no engine at all.
 */
class FakeMongo(
    private val commandReply: (Document) -> Document = { okReply() },
    private val queryResults: (Document) -> List<Document> = { emptyList() },
    /**
     * How long the engine takes to answer, for the tests that measure that.
     *
     * A `delay` rather than a sleep, so it costs virtual time on the test scheduler and no real
     * time at all -- which is what lets a test assert an exact duration.
     */
    private val answersIn: Duration = Duration.ZERO,
) : MongoSeam {
    private val issued = mutableListOf<Document>()
    private val ran = mutableListOf<String>()

    val commands: List<Document> get() = issued

    val lastCommand: Document get() = issued.last()

    /** The threads the seam was used on, which is how "not on the caller's thread" is checked. */
    val threads: List<String> get() = ran

    override suspend fun command(command: Document): Document {
        issued += command
        ran += Thread.currentThread().name
        delay(answersIn)
        return commandReply(command)
    }

    override fun documents(command: Document): Flow<Document> = flow {
        issued += command
        ran += Thread.currentThread().name
        delay(answersIn)
        queryResults(command).forEach { emit(it) }
    }
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

/** The stages of an `aggregate` command, which is what most of these tests are asserting about. */
fun Document.pipeline(): List<Document> {
    @Suppress("UNCHECKED_CAST")
    return this["pipeline"] as List<Document>
}

/** The first stage carrying [name]. First rather than only: a pipeline can hold two `$match`es. */
fun Document.stage(name: String): Document =
    pipeline().first { it.containsKey(name) }.get(name) as Document

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
fun TestScope.placesIn(mongo: MongoSeam) = PlaceRepository(mongo, StandardTestDispatcher(testScheduler))
