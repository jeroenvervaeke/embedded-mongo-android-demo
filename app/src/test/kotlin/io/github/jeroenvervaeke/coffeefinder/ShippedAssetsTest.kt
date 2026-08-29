package io.github.jeroenvervaeke.coffeefinder

import io.github.jeroenvervaeke.coffeefinder.ui.LICENCE_DOCUMENTS
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Checks what AGP packages, not what the source tree holds.
 *
 * The two are not the same file. The asset merger rewrites some of them on the way through, and
 * it does so silently: an asset named `x.bson.gz` is inflated and packaged as `x.bson`, so the
 * application asks for a name that is not there and every launch fails. Nothing else in this
 * suite can see that, because everything else reads `src/main/assets`.
 *
 * Reads the merge task's output, which `app/build.gradle.kts` makes the unit tests depend on.
 */
class ShippedAssetsTest {
    @Test
    fun `the seed is packaged under the name the application asks for`() {
        assertTrue(packaged(SEED_ASSET).isFile, "the packaged assets are ${packagedNames()}")
    }

    @Test
    fun `the seed is packaged still compressed`() {
        // The gzip magic number. Inflated bytes here would mean the merger unpacked it, which is
        // exactly what it does to a `.gz`, and the reader would fail on the first document.
        val magic = packaged(SEED_ASSET).inputStream().use { byteArrayOf(it.read().toByte(), it.read().toByte()) }

        assertEquals(listOf(0x1F, 0x8B), magic.map { it.toInt() and 0xFF })
    }

    @Test
    fun `no asset uses an extension the asset merger rewrites`() {
        val rewritten = sourceAssets().filter { it.extension == "gz" }

        assertEquals(emptyList(), rewritten.map { it.name })
    }

    @Test
    fun `every licence text the About screen names is packaged too`() {
        val missing = LICENCE_DOCUMENTS.filterNot { packaged(it.asset).isFile }

        assertEquals(emptyList(), missing.map { it.asset })
    }

    @Test
    fun `nothing is packaged that the application never names`() {
        val named = (LICENCE_DOCUMENTS.map { it.asset } + SEED_ASSET).toSet()

        assertEquals(emptySet(), packagedNames() - named)
    }

    private fun packaged(asset: String) = File(MERGED, asset)

    private fun packagedNames(): Set<String> =
        File(MERGED).walkTopDown().filter(File::isFile).map { it.relativeTo(File(MERGED)).invariantSeparatorsPath }.toSet()

    private fun sourceAssets() = File("src/main/assets").walkTopDown().filter(File::isFile).toList()
}

/** Where `mergeDebugAssets` writes what the APK will hold. */
private const val MERGED = "build/intermediates/assets/debug/mergeDebugAssets"
