package io.github.jeroenvervaeke.coffeefinder.data.seed

import io.github.jeroenvervaeke.coffeefinder.data.MongoSeam
import io.github.jeroenvervaeke.coffeefinder.data.query.countPlacesCommand
import io.github.jeroenvervaeke.coffeefinder.data.query.createIndexesCommand
import io.github.jeroenvervaeke.coffeefinder.data.query.dropPlacesCommand
import io.github.jeroenvervaeke.coffeefinder.data.query.dropSeedMarkerCommand
import io.github.jeroenvervaeke.coffeefinder.data.query.findSeedMarkerCommand
import io.github.jeroenvervaeke.coffeefinder.data.query.insertPlacesCommand
import io.github.jeroenvervaeke.coffeefinder.data.query.writeSeedMarkerCommand
import java.io.IOException
import java.io.InputStream
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow

/**
 * Puts the shipped coffee places into the database, once, and keeps the indexes in place.
 *
 * Seeding is the only write this application makes, so everything that decides *whether* to write
 * is here: the marker that says a previous run finished, and the drop that clears one that did
 * not. Everything it emits is a [SeedProgress], because on a cold start this is the screen.
 */
class Seeder(
    private val mongo: MongoSeam,
    private val batchSize: Int = DEFAULT_BATCH_SIZE,
    private val readOn: CoroutineDispatcher = Dispatchers.IO,
) {
    init {
        require(batchSize > 0) { "a batch of $batchSize documents inserts nothing" }
    }

    /**
     * Seeds if it has to, indexes either way, and reports what it is doing.
     *
     * [gzippedSeed] opens the shipped seed. A function rather than a stream so that collecting
     * this flow twice reads the seed twice rather than reading a stream that is already at its
     * end — and so that the asset is not held open on the path where nothing needs it.
     *
     * The whole flow runs on [readOn], because opening the asset, inflating it and decoding 5,180
     * documents is all work that would otherwise happen wherever the collector is — which, for a
     * progress screen, is the main thread.
     *
     * The indexes are built after the documents rather than before them: one build over a full
     * collection instead of 5,180 incremental updates. They are also built on every start, not
     * only after a seed, because `createIndexes` is idempotent and an index that has gone missing
     * is otherwise a silent collection scan.
     */
    fun seed(gzippedSeed: () -> InputStream): Flow<SeedProgress> = flow {
        emit(SeedProgress.Checking)

        val recorded = recordedCount()
        val stored = countPlaces()
        // The marker says how many documents a finished run put in. Believing its existence alone
        // would leave the application permanently showing an empty map if the collection were
        // ever emptied under it -- by a failed index build, a partial recovery, a future
        // migration -- because nothing would ever seed again.
        if (recorded != stored) {
            if (recorded != null) mongo.command(dropSeedMarkerCommand())
            // A collection with documents but no matching marker holds a prefix of the seed, or
            // something else entirely. Either way the cheap fix is to start again.
            if (stored > 0) mongo.command(dropPlacesCommand())
            emit(SeedProgress.Inserting(0))
            val inserted = insertAll(gzippedSeed)
            if (inserted == 0) {
                // Marking an empty seed as finished would be irreversible: every later start
                // would agree the database was seeded, and it would hold nothing.
                throw IOException("the seed asset held no documents")
            }
            mongo.command(writeSeedMarkerCommand(inserted))
        }

        emit(SeedProgress.Indexing)
        mongo.command(createIndexesCommand())
        emit(SeedProgress.Ready(countPlaces()))
    }.flowOn(readOn)

    private suspend fun FlowCollector<SeedProgress>.insertAll(gzippedSeed: () -> InputStream): Int {
        var inserted = 0
        gzippedSeed().use { compressed ->
            GZIPInputStream(compressed).use { seed ->
                bsonDocuments(seed).chunked(batchSize).forEach { batch ->
                    mongo.command(insertPlacesCommand(batch))
                    inserted += batch.size
                    emit(SeedProgress.Inserting(inserted))
                }
            }
        }
        return inserted
    }

    /** How many documents the last finished run put in, or `null` if none ever finished. */
    private suspend fun recordedCount(): Int? =
        mongo.documents(findSeedMarkerCommand()).firstOrNull()
            ?.let { (it["documents"] as? Number)?.toInt() }

    private suspend fun countPlaces(): Int {
        val reply = mongo.command(countPlacesCommand())
        return (reply["n"] as? Number)?.toInt()
            ?: throw IOException("counting places returned no `n` (fields: ${reply.keys.joinToString()})")
    }
}

/**
 * Documents per `insert`.
 *
 * Far below MongoDB's own batch limits, and chosen for the progress bar rather than for the
 * engine: a batch is one journalled write, so this is also how often the seed is durable.
 */
private const val DEFAULT_BATCH_SIZE = 500
