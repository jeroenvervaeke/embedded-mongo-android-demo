package io.github.jeroenvervaeke.coffeefinder.data.query

import io.github.jeroenvervaeke.coffeefinder.data.DUBLIN
import io.github.jeroenvervaeke.coffeefinder.data.model.Metres
import io.github.jeroenvervaeke.coffeefinder.data.model.PlaceCategory
import io.github.jeroenvervaeke.coffeefinder.data.stage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import org.bson.Document

class NearestPipelineTest {
    @Test
    fun `the whole pipeline is a geoNear over the index and a limit`() {
        assertEquals(
            listOf(
                Document(
                    "\$geoNear",
                    Document("near", Document("type", "Point").append("coordinates", listOf(-6.2603, 53.3498)))
                        .append("distanceField", "distance")
                        .append("spherical", true),
                ),
                Document("\$limit", 5),
            ),
            nearestPipeline(DUBLIN, limit = 5),
        )
    }

    @Test
    fun `geoNear leads the pipeline, because the index walk is where the documents come from`() {
        val pipeline = nearestPipeline(DUBLIN, limit = 5, category = PlaceCategory.CAFE)

        assertEquals("\$geoNear", pipeline.first().keys.single())
    }

    @Test
    fun `a maximum distance is passed in metres`() {
        val pipeline = nearestPipeline(DUBLIN, limit = 5, maxDistance = Metres.ofKilometres(2.5))

        assertEquals(2500.0, pipeline.stage("\$geoNear")["maxDistance"])
    }

    @Test
    fun `a category becomes geoNear's own query rather than a stage in front of it`() {
        val pipeline = nearestPipeline(DUBLIN, limit = 5, category = PlaceCategory.COFFEE_ROASTERY)

        assertEquals(Document("cat", "coffee_roastery"), pipeline.stage("\$geoNear")["query"])
        assertEquals(2, pipeline.size)
    }

    @Test
    fun `an unfiltered search sends no query, rather than an empty one`() {
        assertFalse(nearestPipeline(DUBLIN, limit = 5).stage("\$geoNear").containsKey("query"))
    }

    @Test
    fun `a category and a distance cap are both applied, not one or the other`() {
        val geoNear = nearestPipeline(
            from = DUBLIN,
            limit = 5,
            maxDistance = Metres.ofKilometres(2.0),
            category = PlaceCategory.CAFE,
        ).stage("\$geoNear")

        assertEquals(2000.0, geoNear["maxDistance"])
        assertEquals(Document("cat", "cafe"), geoNear["query"])
    }

    @Test
    fun `a limit of zero is refused rather than sent as a query returning nothing`() {
        assertFailsWith<IllegalArgumentException> { nearestPipeline(DUBLIN, limit = 0) }
    }
}
