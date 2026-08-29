package io.github.jeroenvervaeke.coffeefinder.data.parse

import io.github.jeroenvervaeke.coffeefinder.data.model.Address
import io.github.jeroenvervaeke.coffeefinder.data.model.Confidence
import io.github.jeroenvervaeke.coffeefinder.data.model.Coordinates
import io.github.jeroenvervaeke.coffeefinder.data.model.PlaceCategory
import io.github.jeroenvervaeke.coffeefinder.data.model.PlaceId
import io.github.jeroenvervaeke.coffeefinder.data.placeDocument
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import org.bson.Document

class PlaceParsingTest {
    @Test
    fun `a seed document is read field for field`() {
        val place = placeDocument(brand = "Insomnia").toPlace()

        assertEquals(PlaceId("0024f54f-43a8-49f8-bdce-a22076983f95"), place.id)
        assertEquals("The House Of Pretzels", place.name)
        assertEquals(PlaceCategory.COFFEE_SHOP, place.category)
        assertEquals(Confidence(0.77), place.confidence)
        assertEquals("Insomnia", place.brand)
        assertEquals(Address(street = "Market Cross", locality = "Kilkenny"), place.address)
    }

    @Test
    fun `coordinates are read longitude first, the way GeoJSON stores them`() {
        val place = placeDocument(longitude = -6.2603, latitude = 53.3498).toPlace()

        assertEquals(Coordinates(longitude = -6.2603, latitude = 53.3498), place.coordinates)
    }

    @Test
    fun `a place Overture gave no address keeps none rather than an empty one`() {
        assertNull(placeDocument(address = null).toPlace().address)
    }

    @Test
    fun `a geo query's distance is read alongside the place`() {
        val document = placeDocument().append("distance", 1234.5)

        assertEquals(1234.5, document.toNearbyPlace().distance.value)
    }

    @Test
    fun `a distance the engine computed as an integer is still a distance`() {
        val document = placeDocument().append("distance", 1234)

        assertEquals(1234.0, document.toNearbyPlace().distance.value)
    }

    @Test
    fun `a category the application does not know is reported rather than dropped`() {
        val failure = assertFailsWith<PlaceFormatException> { placeDocument(category = "bar").toPlace() }

        assertEquals(true, failure.message?.contains("bar"))
    }

    @Test
    fun `a geometry that is not a point is reported rather than read as its first vertex`() {
        val document = placeDocument()
            .append("loc", Document("type", "LineString").append("coordinates", listOf(1.0, 2.0)))

        assertFailsWith<PlaceFormatException> { document.toPlace() }
    }

    @Test
    fun `a point carrying an altitude is reported rather than half read`() {
        val document = placeDocument()
            .append("loc", Document("type", "Point").append("coordinates", listOf(1.0, 2.0, 3.0)))

        assertFailsWith<PlaceFormatException> { document.toPlace() }
    }

    @Test
    fun `a missing name is reported rather than becoming an unnamed place`() {
        assertFailsWith<PlaceFormatException> { placeDocument().apply { remove("name") }.toPlace() }
    }

    @Test
    fun `a confidence that is not a number is reported`() {
        assertFailsWith<PlaceFormatException> { placeDocument().append("confidence", "high").toPlace() }
    }

    @Test
    fun `a confidence outside zero to one is reported`() {
        assertFailsWith<IllegalArgumentException> { placeDocument(confidence = 1.5).toPlace() }
    }

    @Test
    fun `a projection that dropped the distance is reported rather than measured as zero`() {
        assertFailsWith<PlaceFormatException> { placeDocument().toNearbyPlace() }
    }
}
