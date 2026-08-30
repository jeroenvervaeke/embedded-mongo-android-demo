package io.github.jeroenvervaeke.coffeefinder.data.explorer

import io.github.jeroenvervaeke.coffeefinder.data.DUBLIN
import io.github.jeroenvervaeke.coffeefinder.data.model.Metres
import io.github.jeroenvervaeke.coffeefinder.data.query.nearestPipeline
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PipelinePresetsTest {
    @Test
    fun `every preset parses back into stages, because the editor opens on one`() {
        PIPELINE_PRESETS.forEach { preset ->
            val stages = parsePipeline(preset.text)

            assertTrue(stages.isNotEmpty(), "${preset.name} parsed to nothing")
        }
    }

    @Test
    fun `a preset built from a query builder is that query, not a copy of it`() {
        val nearest = PIPELINE_PRESETS.first { it.name == "NEAREST" }

        assertEquals(
            nearestPipeline(DUBLIN, limit = 50, maxDistance = Metres(1_500.0)),
            parsePipeline(nearest.text),
        )
    }

    @Test
    fun `the text search preset carries the haversine the application computes in the engine`() {
        val text = PIPELINE_PRESETS.first { it.name == "TEXT + HAVERSINE" }.text

        assertTrue(text.contains("\$asin"), "the distance expression is not in the preset")
        assertTrue(text.contains("\$degreesToRadians"))
    }

    @Test
    fun `every stage snippet is a stage on its own`() {
        STAGE_SNIPPETS.forEach { snippet ->
            val stage = parsePipeline(snippet.text).single()

            assertEquals(snippet.name, stage.keys.single())
        }
    }

    @Test
    fun `printing stages and reading them back gives the same stages`() {
        val stages = nearestPipeline(DUBLIN, limit = 7, maxDistance = Metres(1_234.5))

        assertEquals(stages, parsePipeline(stages.asPipelineText()))
    }
}
