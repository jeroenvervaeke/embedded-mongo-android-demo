package io.github.jeroenvervaeke.coffeefinder.location

import io.github.jeroenvervaeke.coffeefinder.data.model.Coordinates
import io.github.jeroenvervaeke.coffeefinder.data.query.COFFEE_DATABASE
import io.github.jeroenvervaeke.coffeefinder.data.query.places
import io.github.jeroenvervaeke.embeddedmongodb.CommandRunner
import io.github.jeroenvervaeke.embeddedmongodb.MongoCollection
import io.github.jeroenvervaeke.embeddedmongodb.MongoDatabase
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import org.bson.Document

/**
 * The provider that never calls back, which is the failure the budget exists for.
 *
 * It also records being called off, because giving up quietly and leaving the radios running for
 * an answer nobody will read is a different bug wearing the same screen.
 */
class SilentLocator : Locator {
    var cancelled = false
        private set

    override suspend fun fix(): Coordinates? = try {
        awaitCancellation()
    } catch (givenUp: CancellationException) {
        cancelled = true
        throw givenUp
    }
}

/** Answers each ask in turn, each after a delay of its own. */
class ScriptedLocator(private val answers: List<Answer>) : Locator {
    private var asked = 0

    override suspend fun fix(): Coordinates? {
        val answer = answers.getOrNull(asked++) ?: error("asked $asked times, scripted ${answers.size}")
        delay(answer.after)
        return answer.where
    }
}

class Answer(val after: Duration, val where: Coordinates?)

/**
 * Enough of the engine for a `NearbyFinder` to run, and a count of the queries it ran.
 *
 * Counted where the command is sent rather than where the query is built, so it counts the
 * queries the engine would actually have been asked.
 */
class CountingEngine : CommandRunner {
    private var ran = 0

    val queries: Int get() = ran

    /** The places collection on this engine, which is what a repository is built from. */
    val places: MongoCollection get() = MongoDatabase(this, COFFEE_DATABASE).places()

    override suspend fun runCommand(database: String, command: Document): Document {
        ran++
        return Document("ok", 1.0).append(
            "cursor",
            Document("id", 0L).append("ns", "$COFFEE_DATABASE.places").append("firstBatch", emptyList<Document>()),
        )
    }
}

val CORK = Coordinates(longitude = -8.4756, latitude = 51.8985)

val GALWAY = Coordinates(longitude = -9.0568, latitude = 53.2707)

/** Shorter than the shipped budget, so nothing here can be reading that one by accident. */
val BUDGET = 4.seconds
