package io.github.jeroenvervaeke.coffeefinder.data.seed

import io.github.jeroenvervaeke.coffeefinder.data.query.placeIndexes
import io.github.jeroenvervaeke.embeddedmongodb.MongoCollection
import io.github.jeroenvervaeke.embeddedmongodb.countDocuments
import io.github.jeroenvervaeke.embeddedmongodb.createIndexes
import io.github.jeroenvervaeke.embeddedmongodb.drop
import io.github.jeroenvervaeke.embeddedmongodb.insertMany
import io.github.jeroenvervaeke.embeddedmongodb.insertOne
import java.io.IOException
import java.io.InputStream
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.bson.Document

/** The `_id` of the one document that records a finished seed. */
const val SEED_MARKER_ID = "places"

/**
 * Puts the shipped coffee places into the database, once, and keeps the indexes in place.
 *
 * Seeding is the only write this application makes, so everything that decides *whether* to write
 * is here: the marker that says a previous run finished, and the drop that clears one that did
 * not. Everything it emits is a [SeedProgress], because on a cold start this is the screen.
 */
class Seeder(
    private val places: MongoCollection,
    private val markers: MongoCollection,
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
        // Counted by reading the documents rather than from collection metadata: after an unclean
        // shutdown -- which is what Android killing the process mid-seed is -- the metadata count
        // has been measured reading 0 against about 90,000 stored documents, and this is the
        // number that decides whether to seed again. `countDocuments` is the honest one.
        val stored = places.countDocuments()
        // The marker says how many documents a finished run put in. Believing its existence alone
        // would leave the application permanently showing an empty map if the collection were
        // ever emptied under it -- by a failed index build, a partial recovery, a future
        // migration -- because nothing would ever seed again.
        if (recorded != stored) {
            if (recorded != null) markers.drop()
            // A collection with documents but no matching marker holds a prefix of the seed, or
            // something else entirely. Either way the cheap fix is to start again.
            if (stored > 0) places.drop()
            emit(SeedProgress.Inserting(0))
            val inserted = insertAll(gzippedSeed)
            if (inserted == 0L) {
                // Marking an empty seed as finished would be irreversible: every later start
                // would agree the database was seeded, and it would hold nothing.
                throw IOException("the seed asset held no documents")
            }
            markers.insertOne(Document("_id", SEED_MARKER_ID).append("documents", inserted))
        }

        emit(SeedProgress.Indexing)
        places.createIndexes(placeIndexes())
        emit(SeedProgress.Ready(places.countDocuments()))
    }.flowOn(readOn)

    private suspend fun FlowCollector<SeedProgress>.insertAll(gzippedSeed: () -> InputStream): Long {
        var inserted = 0L
        gzippedSeed().use { compressed ->
            GZIPInputStream(compressed).use { seed ->
                bsonDocuments(seed).chunked(batchSize).forEach { batch ->
                    // Unordered because the batches are independent: a document the engine
                    // rejects should cost that document rather than the rest of the batch.
                    places.insertMany(batch, ordered = false)
                    inserted += batch.size
                    emit(SeedProgress.Inserting(inserted))
                }
            }
        }
        return inserted
    }

    /** How many documents the last finished run put in, or `null` if none ever finished. */
    private suspend fun recordedCount(): Long? =
        markers.find(Document("_id", SEED_MARKER_ID)).firstOrNull()
            ?.let { marker ->
                (marker["documents"] as? Number)?.toLong()
                    ?: throw IOException(
                        "the seed marker carries no document count (fields: ${marker.keys.joinToString()})",
                    )
            }
}

/**
 * Documents per `insert`.
 *
 * Far below MongoDB's own batch limits, and chosen for the progress bar rather than for the
 * engine: a batch is one journalled write, so this is also how often the seed is durable.
 */
private const val DEFAULT_BATCH_SIZE = 500
