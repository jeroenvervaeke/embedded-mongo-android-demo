package io.github.jeroenvervaeke.coffeefinder.data.explorer

import io.github.jeroenvervaeke.embeddedmongodb.MongoCollection
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.TimeSource
import kotlin.time.measureTimedValue
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import org.bson.Document

/**
 * Runs a pipeline somebody typed, against the collection the rest of the application queries.
 *
 * The point of the screen this backs is that nothing is simulated: the text goes to the same
 * engine, in the same process, as the two queries behind the map — so an unsupported stage comes
 * back as MongoDB's own refusal rather than as a message this application made up.
 *
 * Results are read [batch] documents at a time and cut off there. A pipeline with no `$limit` in
 * it is a normal thing to type, and 5,180 documents decoded into a list is not what a screen
 * showing twenty of them should cost.
 */
class PipelineExplorer(
    private val places: MongoCollection,
    private val clock: TimeSource = TimeSource.Monotonic,
    private val batch: Int = DEFAULT_BATCH,
) {
    /** The namespace the explorer runs against, which the screen prints as its heading. */
    val namespace: String get() = places.namespace

    suspend fun run(text: String): ExplorerResult = attempt(text) { stages ->
        val query = places.aggregate(stages)
        val (documents, took) = clock.measureTimedValue { query.asFlow().take(batch).toList() }
        ExplorerResult.Ran(documents, took, query.command(), truncated = documents.size == batch)
    }

    /**
     * What the engine says it would do with the pipeline, as `explain` reports it.
     *
     * Asked of the engine rather than guessed from the stages: which index served a query is
     * exactly the claim a person opening this screen is checking, and a client-side guess at it
     * would be the one number on the screen that nothing verified.
     */
    suspend fun explain(text: String): ExplorerResult = attempt(text) { stages ->
        val command = Document("explain", places.aggregate(stages).command())
            .append("verbosity", "executionStats")
        val (reply, took) = clock.measureTimedValue { places.runCommand(command) }
        ExplorerResult.Explained(reply, took)
    }

    private suspend fun attempt(
        text: String,
        run: suspend (List<Document>) -> ExplorerResult,
    ): ExplorerResult = try {
        run(parsePipeline(text))
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (rejected: PipelineFormatException) {
        ExplorerResult.Rejected(rejected.message ?: "the pipeline could not be read")
    } catch (refused: Exception) {
        // Everything the engine refuses arrives here: an unsupported stage, a `$geoNear` that is
        // not first, a field the collection has no index for. Its own words are the answer.
        ExplorerResult.Refused(refused.message ?: refused.javaClass.simpleName)
    }
}

/** What came of running what somebody typed. */
sealed interface ExplorerResult {
    /** The engine ran it and answered. */
    data class Ran(
        val documents: List<Document>,
        val took: Duration,
        /** The `aggregate` command as it was sent, so the screen shows what ran. */
        val command: Document,
        /** Whether the cursor held more than was read. */
        val truncated: Boolean,
    ) : ExplorerResult

    /** The engine explained it: the winning plan, the index and the execution stats. */
    data class Explained(val plan: Document, val took: Duration) : ExplorerResult

    /** The text never reached the engine, because it is not a pipeline. */
    data class Rejected(val reason: String) : ExplorerResult

    /** The engine read it and would not run it. */
    data class Refused(val reason: String) : ExplorerResult
}

/** More documents than a phone screen shows, and few enough to decode without being noticed. */
private const val DEFAULT_BATCH = 25
