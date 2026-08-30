package io.github.jeroenvervaeke.coffeefinder.ui.explorer

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import io.github.jeroenvervaeke.coffeefinder.data.explorer.PipelineExplorer
import io.github.jeroenvervaeke.coffeefinder.ui.FakeEngine
import io.github.jeroenvervaeke.coffeefinder.ui.pipeline
import io.github.jeroenvervaeke.coffeefinder.ui.placeReply
import io.github.jeroenvervaeke.coffeefinder.ui.theme.CoffeeFinderTheme
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.bson.Document
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ExplorerScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `it opens on a preset and on that preset's results`() {
        val engine = show()

        compose.onNodeWithText("NEAREST").assertIsDisplayed()
        assertEquals("\$geoNear", engine.lastPipeline.first().keys.single())
        compose.onNode(hasText("Two Pups Coffee", substring = true)).assertIsDisplayed()
    }

    @Test
    fun `running what was typed sends exactly that pipeline`() {
        val engine = show()

        typePipeline("""[ { "${'$'}match": { "cat": "cafeteria" } } ]""")
        compose.onNodeWithText("▶ RUN").performClick()
        compose.waitForIdle()

        assertEquals(
            listOf(Document("\$match", Document("cat", "cafeteria"))),
            engine.lastPipeline,
        )
    }

    @Test
    fun `a preset replaces what is in the editor`() {
        val engine = show()

        compose.onNodeWithText("CATEGORY ROLLUP").performClick()
        compose.onNodeWithText("▶ RUN").performClick()
        compose.waitForIdle()

        assertEquals("\$group", engine.lastPipeline.first().keys.single())
    }

    @Test
    fun `a stage chip appends a stage to the pipeline already there`() {
        val engine = show()

        compose.onNodeWithText("\$count").performClick()
        compose.onNodeWithText("▶ RUN").performClick()
        compose.waitForIdle()

        assertEquals("\$count", engine.lastPipeline.last().keys.single())
    }

    @Test
    fun `text that is not a pipeline is refused without reaching the engine`() {
        val engine = show()
        val before = engine.commands.size

        typePipeline("[ { oops")
        compose.onNodeWithText("▶ RUN").performClick()
        compose.waitForIdle()

        compose.onNode(hasText("JSON:", substring = true)).assertIsDisplayed()
        assertEquals(before, engine.commands.size)
    }

    @Test
    fun `a pipeline the engine refuses is reported in the engine's own words`() {
        show(FakeEngine(failWith = "\$geoNear is only valid as the first stage"))

        compose.onNodeWithText("▶ RUN").performClick()
        compose.waitForIdle()

        // Twice over: beside the RUN button, and where the results would have been.
        compose.onAllNodesWithText("only valid as the first stage", substring = true)
            .onFirst()
            .assertIsDisplayed()
    }

    @Test
    fun `EXPLAIN asks the engine to explain the same pipeline`() {
        val engine = show()

        compose.onNodeWithText("EXPLAIN").performClick()
        compose.waitForIdle()

        val explained = engine.commands.last()["explain"] as Document
        assertEquals("places", explained["aggregate"])
        assertTrue(explained.pipeline().first().containsKey("\$geoNear"))
    }

    @Test
    fun `the results pane comes back by running again`() {
        show()
        compose.onNodeWithText("EXPLAIN").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("RESULTS").performClick()
        compose.waitForIdle()

        compose.onNode(hasText("Two Pups Coffee", substring = true)).assertIsDisplayed()
    }

    /**
     * The pipeline the explorer last sent.
     *
     * Every command on this screen is one somebody asked for, `$count` stages included -- there
     * is no map here quietly counting behind them.
     */
    private val FakeEngine.lastPipeline: List<Document> get() = commands.last().pipeline()

    private fun typePipeline(text: String) {
        compose.onNode(hasSetTextAction()).performTextClearance()
        compose.onNode(hasSetTextAction()).performTextInput(text)
    }

    private fun show(engine: FakeEngine = FakeEngine(results = listOf(placeReply()))): FakeEngine {
        compose.setContent {
            CoffeeFinderTheme { ExplorerScreen(PipelineExplorer(engine.collection)) }
        }
        compose.waitForIdle()
        return engine
    }
}
