package io.github.jeroenvervaeke.coffeefinder.ui.explorer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.bson.Document

class ExplainSummaryTest {
    @Test
    fun `the index the plan names is pulled out of it`() {
        val rows = explainSummary(
            Document(
                "queryPlanner",
                Document("winningPlan", Document("inputStage", Document("indexName", "loc_2dsphere"))),
            ),
        )

        assertEquals(listOf(ExplainRow("indexName", "loc_2dsphere")), rows)
    }

    @Test
    fun `an index nested under a query plan is found there too`() {
        val rows = explainSummary(
            Document(
                "queryPlanner",
                Document(
                    "winningPlan",
                    Document("queryPlan", Document("inputStage", Document("indexName", "name_brand_text"))),
                ),
            ),
        )

        assertEquals("name_brand_text", rows.single().value)
    }

    @Test
    fun `the execution stats are reported in the order a person reads them`() {
        val rows = explainSummary(
            Document("command", Document("aggregate", "places")).append(
                "executionStats",
                Document("nReturned", 38).append("totalDocsExamined", 60)
                    .append("executionTimeMillis", 12),
            ),
        )

        assertEquals(
            listOf("namespace", "nReturned", "totalDocsExamined", "executionTimeMillis"),
            rows.map(ExplainRow::label),
        )
        assertEquals("coffee.places", rows.first().value)
    }

    @Test
    fun `an aggregation plan is read out of the cursor stage it nests everything in`() {
        // The shape the engine actually answers a `$geoNear` pipeline with, measured on a device.
        val rows = explainSummary(
            Document("explainVersion", "1").append(
                "stages",
                listOf(
                    Document(
                        "\$geoNearCursor",
                        Document(
                            "queryPlanner",
                            Document("winningPlan", Document("inputStage", Document("indexName", "loc_2dsphere"))),
                        ).append("executionStats", Document("nReturned", 25).append("totalDocsExamined", 25)),
                    ),
                    Document("\$limit", 50),
                ),
            ),
        )

        assertEquals(
            listOf(
                ExplainRow("indexName", "loc_2dsphere"),
                ExplainRow("nReturned", "25"),
                ExplainRow("totalDocsExamined", "25"),
            ),
            rows,
        )
    }

    @Test
    fun `a stages list holding something that is not a stage is not read as one`() {
        assertTrue(explainSummary(Document("stages", listOf("nonsense"))).isEmpty())
    }

    @Test
    fun `a plan with none of the fields summarises to nothing rather than to zeros`() {
        assertTrue(explainSummary(Document("ok", 1.0)).isEmpty())
    }

    @Test
    fun `a path that runs into something that is not a document is left out`() {
        val rows = explainSummary(Document("queryPlanner", "COLLSCAN").append("executionStats", 7))

        assertTrue(rows.isEmpty())
    }

    @Test
    fun `a plan naming only its stage says which stage`() {
        val rows = explainSummary(
            Document("queryPlanner", Document("winningPlan", Document("stage", "COLLSCAN"))),
        )

        assertEquals(listOf(ExplainRow("stage", "COLLSCAN")), rows)
    }
}
