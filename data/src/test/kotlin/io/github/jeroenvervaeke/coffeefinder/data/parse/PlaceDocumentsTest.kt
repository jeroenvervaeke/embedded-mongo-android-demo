package io.github.jeroenvervaeke.coffeefinder.data.parse

import io.github.jeroenvervaeke.coffeefinder.data.placeDocument
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import org.bson.Document

class PlaceDocumentsTest {
    @Test
    fun `a stored place written back out is the document it was read from`() {
        val stored = placeDocument(brand = "Insomnia")

        assertEquals(stored, stored.toPlace().asDocument())
    }

    @Test
    fun `a place Overture gave no brand carries no brand field, rather than a null one`() {
        val stored = placeDocument(brand = null)

        val written = stored.toPlace().asDocument()

        assertFalse(written.containsKey("brand"))
        assertEquals(stored, written)
    }

    @Test
    fun `a place with no address at all writes none`() {
        val stored = placeDocument(address = null)

        assertEquals(stored, stored.toPlace().asDocument())
    }

    @Test
    fun `only the address fields that were there are written`() {
        val stored = placeDocument(address = Document("locality", "Sligo"))

        val written = stored.toPlace().asDocument()["addr"] as Document

        assertEquals(listOf("locality"), written.keys.toList())
    }

    @Test
    fun `a result carries the distance the pipeline measured`() {
        val stored = placeDocument().append("distance", 241.5)

        assertEquals(stored, stored.toNearbyPlace().asDocument())
    }

    @Test
    fun `the coordinates keep their order, longitude first`() {
        val written = placeDocument(longitude = -6.26, latitude = 53.34).toPlace().asDocument()

        assertEquals(listOf(-6.26, 53.34), (written["loc"] as Document)["coordinates"])
    }
}
