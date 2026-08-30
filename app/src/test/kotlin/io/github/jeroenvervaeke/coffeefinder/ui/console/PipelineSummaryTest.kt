package io.github.jeroenvervaeke.coffeefinder.ui.console

import io.github.jeroenvervaeke.coffeefinder.data.model.Ireland
import io.github.jeroenvervaeke.coffeefinder.data.model.Metres
import io.github.jeroenvervaeke.coffeefinder.data.query.PlaceCriteria
import io.github.jeroenvervaeke.coffeefinder.data.query.nearestPipeline
import io.github.jeroenvervaeke.coffeefinder.data.query.searchPipeline
import io.github.jeroenvervaeke.coffeefinder.data.query.viewportPipeline
import io.github.jeroenvervaeke.coffeefinder.data.model.PlaceCategory
import kotlin.test.Test
import kotlin.test.assertEquals
import org.bson.Document

class PipelineSummaryTest {
    @Test
    fun `a stage is its operator and the one word that says which one it is`() {
        val command = command(
            nearestPipeline(
                Ireland.DUBLIN,
                limit = 50,
                maxDistance = Metres(1_000.0),
                criteria = PlaceCriteria(category = PlaceCategory.CAFE),
            ),
        )

        assertEquals(listOf("\$geoNear near", "\$limit 50"), command.stageLabels())
    }

    @Test
    fun `a text search reads as the stages it is, in order`() {
        val command = command(searchPipeline("kaph", Ireland.DUBLIN, limit = 10))

        assertEquals(
            listOf("\$match \$text", "\$set distance", "\$sort distance", "\$limit 10"),
            command.stageLabels(),
        )
    }

    @Test
    fun `a command with no pipeline in it has no stages rather than throwing`() {
        assertEquals(emptyList(), Document("ok", 1.0).stageLabels())
    }

    @Test
    fun `a geoNear can only have been served by the 2dsphere index`() {
        val command = command(nearestPipeline(Ireland.DUBLIN, limit = 5))

        assertEquals("loc_2dsphere", command.indexBehind())
    }

    @Test
    fun `a geoWithin is served by the same index, because it selects on the same shape`() {
        val command = command(viewportPipeline(Ireland.EXTENT, limit = 100))

        assertEquals("loc_2dsphere", command.indexBehind())
    }

    @Test
    fun `a text match is served by the text index`() {
        val command = command(searchPipeline("kaph", Ireland.DUBLIN, limit = 10))

        assertEquals("name_brand_text", command.indexBehind())
    }

    @Test
    fun `a pipeline no index can serve is reported as a collection scan`() {
        val command = command(listOf(Document("\$match", Document("cat", "cafe"))))

        assertEquals(NO_INDEX, command.indexBehind())
    }

    @Test
    fun `a command with nothing in it is a collection scan rather than a crash`() {
        assertEquals(NO_INDEX, Document("aggregate", "places").indexBehind())
    }

    /** An `aggregate` command in the shape the library builds, which is what the console reads. */
    private fun command(stages: List<Document>) =
        Document("aggregate", "places").append("pipeline", stages).append("cursor", Document())
}
