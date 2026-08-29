package io.github.jeroenvervaeke.coffeefinder.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The About screen is the application's licence compliance, so the texts it names have to be
 * there. They were once not: the assets moved and four of the paths did not, which no compiler
 * and no other test could see.
 */
class LicenceDocumentsTest {
    @Test
    fun `every text the About screen names is actually shipped`() {
        val missing = LICENCE_DOCUMENTS.filterNot { asset(it.asset).isFile }

        assertEquals(emptyList(), missing.map { it.asset })
    }

    @Test
    fun `none of them ships empty`() {
        val empty = LICENCE_DOCUMENTS.filter { asset(it.asset).length() == 0L }

        assertEquals(emptyList(), empty.map { it.asset })
    }

    @Test
    fun `the three licences the data is under are all named`() {
        val titles = LICENCE_DOCUMENTS.map { it.title }

        assertTrue(titles.any { it.contains("Community Data License") }, "$titles")
        assertTrue(titles.any { it.contains("Apache License 2.0") }, "$titles")
        assertTrue(titles.any { it.contains("CC0 1.0") }, "$titles")
    }

    @Test
    fun `the Foursquare NOTICE is shipped in full rather than summarised`() {
        val notice = asset(LICENCE_DOCUMENTS.single { it.title.contains("NOTICE") }.asset).readText()

        // The two things the NOTICE itself requires a redistributor to preserve.
        assertTrue(notice.contains("Foursquare"), notice)
        assertTrue(notice.contains("Apache License"), notice)
    }

    private fun asset(path: String) = File("src/main/assets", path)
}
