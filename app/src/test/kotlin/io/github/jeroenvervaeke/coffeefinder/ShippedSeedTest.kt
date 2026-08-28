package io.github.jeroenvervaeke.coffeefinder

import io.github.jeroenvervaeke.coffeefinder.data.model.Ireland
import io.github.jeroenvervaeke.coffeefinder.data.model.PlaceCategory
import io.github.jeroenvervaeke.coffeefinder.data.parse.toPlace
import io.github.jeroenvervaeke.coffeefinder.data.seed.bsonDocuments
import java.io.File
import java.util.zip.GZIPInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Reads the seed this application actually ships, through the code that reads it on a device.
 *
 * The rest of the suite runs against documents the tests wrote themselves, which proves the
 * parser reads what the tests believe the seed looks like. This proves the seed looks like that
 * — the one claim a fake cannot make — and it is the only part of the data path that can be
 * checked without an engine.
 */
class ShippedSeedTest {
    @Test
    fun `every document in the shipped seed parses into a place`() {
        val places = seed().map { it.toPlace() }

        assertEquals(5_180, places.size)
        assertTrue(places.all { it.name.isNotBlank() })
    }

    @Test
    fun `every place is inside the extent the seed was extracted with`() {
        val outside = seed().map { it.toPlace() }.filterNot { it.coordinates in Ireland.EXTENT }

        assertEquals(emptyList(), outside.map { "${it.name} at ${it.coordinates}" })
    }

    @Test
    fun `every category in the seed is one the application knows`() {
        // A category the enum does not hold would make `toPlace` throw on that document, so this
        // says something more: all four are present, and the filter chips are not decoration.
        val categories = seed().map { it.toPlace().category }.toSet()

        assertEquals(PlaceCategory.entries.toSet(), categories)
    }

    @Test
    fun `the seed is a stream of documents rather than one document holding them`() {
        val first = seed().first()

        assertEquals(setOf("_id", "name", "cat", "confidence", "addr", "loc"), first.keys - "brand")
    }

    private fun seed(): List<org.bson.Document> {
        val file = File(SEED)
        assertTrue(file.isFile, "the seed is not at ${file.absolutePath}")
        return GZIPInputStream(file.inputStream()).use { bsonDocuments(it).toList() }
    }
}

/** Relative to the module directory, which is where Gradle runs a unit test from. */
private const val SEED = "src/main/assets/places/ireland.bson.gzip"
