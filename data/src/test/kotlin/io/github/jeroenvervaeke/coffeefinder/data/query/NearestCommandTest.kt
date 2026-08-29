package io.github.jeroenvervaeke.coffeefinder.data.query

import io.github.jeroenvervaeke.coffeefinder.data.DUBLIN
import io.github.jeroenvervaeke.coffeefinder.data.model.Metres
import io.github.jeroenvervaeke.coffeefinder.data.model.PlaceCategory
import io.github.jeroenvervaeke.coffeefinder.data.pipeline
import io.github.jeroenvervaeke.coffeefinder.data.stage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import org.bson.Document

class NearestCommandTest {
    @Test
    fun `the whole command is an aggregate over places with a cursor`() {
        val command = nearestCommand(DUBLIN, limit = 5)

        assertEquals(
            Document("aggregate", "places")
                .append(
                    "pipeline",
                    listOf(
                        Document(
                            "\$geoNear",
                            Document("near", Document("type", "Point").append("coordinates", listOf(-6.2603, 53.3498)))
                                .append("distanceField", "distance")
                                .append("spherical", true),
                        ),
                        Document("\$limit", 5),
                    ),
                )
                .append("cursor", Document()),
            command,
        )
    }

    @Test
    fun `geoNear leads the pipeline, because the index walk is where the documents come from`() {
        val pipeline = nearestCommand(DUBLIN, limit = 5, category = PlaceCategory.CAFE).pipeline()

        assertEquals("\$geoNear", pipeline.first().keys.single())
    }

    @Test
    fun `a maximum distance is passed in metres`() {
        val command = nearestCommand(DUBLIN, limit = 5, maxDistance = Metres.ofKilometres(2.5))

        assertEquals(2500.0, command.stage("\$geoNear")["maxDistance"])
    }

    @Test
    fun `a category becomes geoNear's own query rather than a stage in front of it`() {
        val command = nearestCommand(DUBLIN, limit = 5, category = PlaceCategory.COFFEE_ROASTERY)

        assertEquals(Document("cat", "coffee_roastery"), command.stage("\$geoNear")["query"])
        assertEquals(2, command.pipeline().size)
    }

    @Test
    fun `an unfiltered search sends no query, rather than an empty one`() {
        assertFalse(nearestCommand(DUBLIN, limit = 5).stage("\$geoNear").containsKey("query"))
    }

    @Test
    fun `a category and a distance cap are both applied, not one or the other`() {
        val geoNear = nearestCommand(
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
        assertFailsWith<IllegalArgumentException> { nearestCommand(DUBLIN, limit = 0) }
    }
}
