package io.github.jeroenvervaeke.coffeefinder.data.query

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.bson.Document

class IndexCommandsTest {
    @Test
    fun `both indexes the application queries through are built in one command`() {
        assertEquals(
            Document("createIndexes", "places").append(
                "indexes",
                listOf(
                    Document("key", Document("loc", "2dsphere")).append("name", "loc_2dsphere"),
                    Document("key", Document("name", "text").append("brand", "text"))
                        .append("name", "name_brand_text")
                        .append("weights", Document("name", 10).append("brand", 5)),
                ),
            ),
            createIndexesCommand(),
        )
    }

    @Test
    fun `a place's own name outranks the chain it belongs to`() {
        val text = indexes().single { it.getString("name") == NAME_INDEX }
        val weights = text["weights"] as Document

        assertEquals(true, (weights.getInteger("name")) > (weights.getInteger("brand")))
    }

    @Test
    fun `places are counted by reading them rather than from collection metadata`() {
        // `{count: "places"}` is answered from metadata, and this project has measured that number
        // reading 0 against a true count of ~90,000 after an unclean shutdown -- which is what
        // Android killing the process mid-seed is. Seeding decides whether to trust its marker by
        // comparing it against this, so the number has to be the real one.
        assertEquals(
            Document("aggregate", "places")
                .append("pipeline", listOf(Document("\$count", "count")))
                .append("cursor", Document()),
            countPlacesCommand(),
        )
    }

    @Test
    fun `an insert names its documents and does not stop at the first the engine refuses`() {
        val documents = listOf(Document("_id", "a"))

        assertEquals(
            Document("insert", "places").append("documents", documents).append("ordered", false),
            insertPlacesCommand(documents),
        )
    }

    @Test
    fun `an empty insert is refused here rather than by the engine`() {
        assertFailsWith<IllegalArgumentException> { insertPlacesCommand(emptyList()) }
    }

    private fun indexes(): List<Document> {
        @Suppress("UNCHECKED_CAST")
        return createIndexesCommand()["indexes"] as List<Document>
    }
}
