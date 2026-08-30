package io.github.jeroenvervaeke.coffeefinder.data.explorer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.bson.Document

class PipelineParsingTest {
    @Test
    fun `an array of stages is read as the pipeline it is`() {
        val stages = parsePipeline("""[ { "${'$'}match": { "cat": "cafe" } }, { "${'$'}limit": 5 } ]""")

        assertEquals(2, stages.size)
        assertEquals(Document("cat", "cafe"), stages.first()["\$match"])
        assertEquals(5, stages.last()["\$limit"])
    }

    @Test
    fun `a single stage on its own is a pipeline of one, because that is what people paste`() {
        val stages = parsePipeline("""{ "${'$'}count": "places" }""")

        assertEquals(listOf(Document("\$count", "places")), stages)
    }

    @Test
    fun `whitespace and newlines around the pipeline are not an error`() {
        assertEquals(1, parsePipeline("\n\n  [ { \"\$count\": \"n\" } ]  \n").size)
    }

    @Test
    fun `nested documents survive the wrapping this uses to read an array`() {
        val stages = parsePipeline(
            """[ { "${'$'}match": { "loc": { "${'$'}geoWithin": { "${'$'}centerSphere": [ [ 1.5, 2.5 ], 0.01 ] } } } } ]""",
        )

        val within = (stages.single()["\$match"] as Document)["loc"] as Document
        assertTrue(within.containsKey("\$geoWithin"))
    }

    @Test
    fun `text that is not JSON is turned around with the parser's own complaint`() {
        val rejected = assertFailsWith<PipelineFormatException> { parsePipeline("[ { oops ") }

        assertTrue(rejected.message!!.isNotBlank())
    }

    @Test
    fun `an empty pipeline is refused rather than sent to the engine`() {
        assertFailsWith<PipelineFormatException> { parsePipeline("   ") }
        assertFailsWith<PipelineFormatException> { parsePipeline("[]") }
    }

    @Test
    fun `something that is neither an array nor an object says so`() {
        val rejected = assertFailsWith<PipelineFormatException> { parsePipeline("\$geoNear") }

        assertTrue(rejected.message!!.contains("starts with"))
    }

    @Test
    fun `an array holding something that is not a stage names which one`() {
        val rejected = assertFailsWith<PipelineFormatException> {
            parsePipeline("""[ { "${'$'}count": "n" }, 7 ]""")
        }

        assertTrue(rejected.message!!.contains("stage 2"))
    }
}
