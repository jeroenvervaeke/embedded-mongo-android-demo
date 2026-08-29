package io.github.jeroenvervaeke.coffeefinder.data.query

import io.github.jeroenvervaeke.coffeefinder.data.CORK
import io.github.jeroenvervaeke.coffeefinder.data.DUBLIN
import io.github.jeroenvervaeke.coffeefinder.data.model.Metres
import io.github.jeroenvervaeke.coffeefinder.data.model.PlaceCategory
import io.github.jeroenvervaeke.coffeefinder.data.pipeline
import io.github.jeroenvervaeke.coffeefinder.data.stage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.bson.Document

class SearchCommandTest {
    @Test
    fun `the text match leads the pipeline, which is the only place MongoDB accepts it`() {
        val pipeline = searchCommand("insomnia", DUBLIN, limit = 10).pipeline()

        assertEquals(
            Document("\$match", Document("\$text", Document("\$search", "insomnia"))),
            pipeline.first(),
        )
    }

    @Test
    fun `results are sorted by distance and cut to the limit, in that order`() {
        val pipeline = searchCommand("insomnia", DUBLIN, limit = 10).pipeline()

        assertEquals(Document("\$sort", Document("distance", 1)), pipeline[pipeline.size - 2])
        assertEquals(Document("\$limit", 10), pipeline.last())
    }

    @Test
    fun `the distance is computed in the engine rather than on the client`() {
        val set = searchCommand("insomnia", DUBLIN, limit = 10).pipeline()
            .single { it.containsKey("\$set") }["\$set"] as Document

        assertEquals(setOf("distance"), set.keys)
        assertTrue(set["distance"] is Document)
    }

    @Test
    fun `the distance is measured from the point the caller asked about`() {
        // Without this, a search that ignored `from` entirely would pass every other test here:
        // they all search from the same place.
        val fromCork = searchCommand("insomnia", CORK, limit = 10).stage("\$set")["distance"]

        assertEquals(distanceFromExpression(CORK), fromCork)
        assertNotEquals(distanceFromExpression(DUBLIN), fromCork)
    }

    @Test
    fun `a category and a distance cap are both applied, not one or the other`() {
        val stages = searchCommand(
            text = "insomnia",
            from = DUBLIN,
            limit = 10,
            category = PlaceCategory.CAFE,
            maxDistance = Metres(2500.0),
        ).pipeline()

        assertEquals(Document("cat", "cafe"), (stages[1]["\$match"] as Document))
        assertEquals(
            Document("distance", Document("\$lte", 2500.0)),
            (stages[3]["\$match"] as Document),
        )
    }

    @Test
    fun `a category filter follows the text match rather than joining it`() {
        val pipeline = searchCommand("insomnia", DUBLIN, limit = 10, PlaceCategory.CAFE).pipeline()

        assertEquals(Document("\$match", Document("cat", "cafe")), pipeline[1])
    }

    @Test
    fun `no category and no distance cap mean no extra stages`() {
        assertEquals(4, searchCommand("insomnia", DUBLIN, limit = 10).pipeline().size)
    }

    @Test
    fun `a distance cap is applied to the distance this pipeline computed`() {
        val pipeline = searchCommand("insomnia", DUBLIN, limit = 10, maxDistance = Metres(2500.0))
            .pipeline()

        // After the $set that computed it, and before the $sort that orders by it.
        assertEquals(
            Document("\$match", Document("distance", Document("\$lte", 2500.0))),
            pipeline[pipeline.size - 3],
        )
    }

    @Test
    fun `the distance cap follows the stage that computed the distance`() {
        val stages = searchCommand("insomnia", DUBLIN, limit = 10, maxDistance = Metres(2500.0))
            .pipeline().map { it.keys.single() }

        assertEquals(listOf("\$match", "\$set", "\$match", "\$sort", "\$limit"), stages)
    }

    @Test
    fun `a blank search is refused rather than sent as one matching everything`() {
        assertFailsWith<IllegalArgumentException> { searchCommand("   ", DUBLIN, limit = 10) }
    }
}
