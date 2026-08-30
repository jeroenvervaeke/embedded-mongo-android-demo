package io.github.jeroenvervaeke.coffeefinder.data.query

import io.github.jeroenvervaeke.coffeefinder.data.CORK
import io.github.jeroenvervaeke.coffeefinder.data.DUBLIN
import io.github.jeroenvervaeke.coffeefinder.data.model.Metres
import io.github.jeroenvervaeke.coffeefinder.data.model.PlaceCategory
import io.github.jeroenvervaeke.coffeefinder.data.stage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.bson.Document
import io.github.jeroenvervaeke.coffeefinder.data.query.PlaceCriteria

class SearchPipelineTest {
    @Test
    fun `the text match leads the pipeline, which is the only place MongoDB accepts it`() {
        val pipeline = searchPipeline("insomnia", DUBLIN, limit = 10)

        assertEquals(
            Document("\$match", Document("\$text", Document("\$search", "insomnia"))),
            pipeline.first(),
        )
    }

    @Test
    fun `results are sorted by distance and cut to the limit, in that order`() {
        val pipeline = searchPipeline("insomnia", DUBLIN, limit = 10)

        assertEquals(Document("\$sort", Document("distance", 1)), pipeline[pipeline.size - 2])
        assertEquals(Document("\$limit", 10), pipeline.last())
    }

    @Test
    fun `the distance is computed in the engine rather than on the client`() {
        val set = searchPipeline("insomnia", DUBLIN, limit = 10)
            .single { it.containsKey("\$set") }["\$set"] as Document

        assertEquals(setOf("distance"), set.keys)
        assertTrue(set["distance"] is Document)
    }

    @Test
    fun `the distance is measured from the point the caller asked about`() {
        // Without this, a search that ignored `from` entirely would pass every other test here:
        // they all search from the same place.
        val fromCork = searchPipeline("insomnia", CORK, limit = 10).stage("\$set")["distance"]

        assertEquals(distanceFromExpression(CORK), fromCork)
        assertNotEquals(distanceFromExpression(DUBLIN), fromCork)
    }

    @Test
    fun `a category and a distance cap are both applied, not one or the other`() {
        val stages = searchPipeline(
            text = "insomnia",
            from = DUBLIN,
            limit = 10,
            criteria = PlaceCriteria(category = PlaceCategory.CAFE),
            maxDistance = Metres(2500.0),
        )

        assertEquals(Document("cat", "cafe"), (stages[1]["\$match"] as Document))
        assertEquals(
            Document("distance", Document("\$lte", 2500.0)),
            (stages[3]["\$match"] as Document),
        )
    }

    @Test
    fun `a category filter follows the text match rather than joining it`() {
        val pipeline = searchPipeline("insomnia", DUBLIN, limit = 10, PlaceCriteria(category = PlaceCategory.CAFE))

        assertEquals(Document("\$match", Document("cat", "cafe")), pipeline[1])
    }

    @Test
    fun `no category and no distance cap mean no extra stages`() {
        assertEquals(4, searchPipeline("insomnia", DUBLIN, limit = 10).size)
    }

    @Test
    fun `a distance cap is applied to the distance this pipeline computed`() {
        val pipeline = searchPipeline("insomnia", DUBLIN, limit = 10, maxDistance = Metres(2500.0))

        // After the $set that computed it, and before the $sort that orders by it.
        assertEquals(
            Document("\$match", Document("distance", Document("\$lte", 2500.0))),
            pipeline[pipeline.size - 3],
        )
    }

    @Test
    fun `the distance cap follows the stage that computed the distance`() {
        val stages = searchPipeline("insomnia", DUBLIN, limit = 10, maxDistance = Metres(2500.0))
            .map { it.keys.single() }

        assertEquals(listOf("\$match", "\$set", "\$match", "\$sort", "\$limit"), stages)
    }

    @Test
    fun `a blank search is refused rather than sent as one matching everything`() {
        assertFailsWith<IllegalArgumentException> { searchPipeline("   ", DUBLIN, limit = 10) }
    }
}
