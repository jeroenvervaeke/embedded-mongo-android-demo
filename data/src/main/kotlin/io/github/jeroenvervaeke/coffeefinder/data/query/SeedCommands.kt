package io.github.jeroenvervaeke.coffeefinder.data.query

import org.bson.Document

/** Holds the one document recording that a seed finished. */
const val SEED_COLLECTION = "seed"

const val SEED_MARKER_ID = "places"

/**
 * Reads the marker that says the collection holds a whole seed.
 *
 * Counting the places instead would be a worse question: a run killed part-way through leaves a
 * collection that is not empty and not complete, and a count cannot tell that from a finished
 * seed. The marker is written last, so its presence means every document went in — and it records
 * how many, so the two can be checked against each other rather than the marker being believed.
 */
fun findSeedMarkerCommand(): Document = Document("find", SEED_COLLECTION)
    .append("filter", Document("_id", SEED_MARKER_ID))
    .append("limit", 1)

fun writeSeedMarkerCommand(documents: Int): Document = Document("insert", SEED_COLLECTION)
    .append("documents", listOf(Document("_id", SEED_MARKER_ID).append("documents", documents)))

/** Throws away a half-written seed so the next attempt starts from an empty collection. */
fun dropPlacesCommand(): Document = Document("drop", PLACES_COLLECTION)

/** Clears a marker that no longer describes what is stored, so a fresh one can be written. */
fun dropSeedMarkerCommand(): Document = Document("drop", SEED_COLLECTION)
