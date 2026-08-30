package io.github.jeroenvervaeke.coffeefinder.ui.explorer

import io.github.jeroenvervaeke.coffeefinder.data.explorer.parsePipeline
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StageAppendTest {
    @Test
    fun `a stage added to a pipeline of one gets the comma the first one now needs`() {
        val pipeline = """[
  { "${'$'}match": { "cat": "cafe" } }
]"""

        val grown = pipeline.withStageAppended("""{ "${'$'}limit": 20 }""")

        assertEquals(2, parsePipeline(grown).size)
        assertEquals("\$limit", parsePipeline(grown).last().keys.single())
    }

    @Test
    fun `a stage added to an empty pipeline needs no comma`() {
        val grown = "[\n]".withStageAppended("""{ "${'$'}count": "n" }""")

        assertEquals(1, parsePipeline(grown).size)
    }

    @Test
    fun `trailing whitespace after the bracket does not become a stage of its own`() {
        val grown = "[\n]\n\n  ".withStageAppended("""{ "${'$'}count": "n" }""")

        assertTrue(grown.trimEnd().endsWith("]"))
        assertEquals(1, parsePipeline(grown).size)
    }

    @Test
    fun `text with no closing bracket keeps what was typed and adds the stage under it`() {
        val grown = "[ { \"\$match\": {} }".withStageAppended("""{ "${'$'}limit": 5 }""")

        assertTrue(grown.startsWith("[ { \"\$match\": {} }"))
        assertTrue(grown.trimEnd().endsWith("""{ "${'$'}limit": 5 }"""))
    }

    @Test
    fun `the stages already there are not rewritten`() {
        val pipeline = parsePipeline(
            """[ { "${'$'}match": { "cat": "cafe" } }, { "${'$'}sort": { "confidence": -1 } } ]"""
                .withStageAppended("""{ "${'$'}limit": 3 }"""),
        )

        assertEquals(listOf("\$match", "\$sort", "\$limit"), pipeline.map { it.keys.single() })
    }
}
