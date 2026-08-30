package io.github.jeroenvervaeke.coffeefinder.data.query

import io.github.jeroenvervaeke.coffeefinder.data.model.Confidence
import io.github.jeroenvervaeke.coffeefinder.data.model.PlaceCategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.bson.Document

class PlaceCriteriaTest {
    @Test
    fun `criteria that constrain nothing produce no query at all`() {
        assertNull(PlaceCriteria.NONE.asQuery())
        assertTrue(PlaceCriteria.NONE.isEmpty)
    }

    @Test
    fun `a category becomes an equality on the stored name, not on the label`() {
        val query = PlaceCriteria(category = PlaceCategory.COFFEE_ROASTERY).asQuery()

        assertEquals(Document("cat", "coffee_roastery"), query)
    }

    @Test
    fun `a confidence floor keeps the tail at or above it`() {
        val query = PlaceCriteria(minimumConfidence = Confidence(0.9)).asQuery()

        assertEquals(Document("confidence", Document("\$gte", 0.9)), query)
    }

    @Test
    fun `branded only asks for the field to exist, because a chain is a field Overture omits`() {
        val query = PlaceCriteria(brandedOnly = true).asQuery()

        assertEquals(Document("brand", Document("\$exists", true)), query)
    }

    @Test
    fun `all three criteria are one query rather than the last one to be set`() {
        val query = PlaceCriteria(
            category = PlaceCategory.CAFE,
            minimumConfidence = Confidence(0.5),
            brandedOnly = true,
        ).asQuery()

        assertEquals(listOf("cat", "confidence", "brand"), query?.keys?.toList())
        assertFalse(
            PlaceCriteria(category = PlaceCategory.CAFE).isEmpty,
            "criteria with a category in them constrain something",
        )
    }

    @Test
    fun `an unbranded filter is not a filter, because false constrains nothing`() {
        assertNull(PlaceCriteria(brandedOnly = false).asQuery())
    }
}
