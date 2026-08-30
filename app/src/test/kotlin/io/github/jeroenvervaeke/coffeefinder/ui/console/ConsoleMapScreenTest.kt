package io.github.jeroenvervaeke.coffeefinder.ui.console

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import io.github.jeroenvervaeke.coffeefinder.data.model.Coordinates
import io.github.jeroenvervaeke.coffeefinder.data.model.Ireland
import io.github.jeroenvervaeke.coffeefinder.location.LocationSource
import io.github.jeroenvervaeke.coffeefinder.ui.FakeEngine
import io.github.jeroenvervaeke.coffeefinder.ui.FakeStartup
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
class ConsoleMapScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `the headline is how many places the engine counted inside the radius`() {
        show(FakeStartup(FakeEngine(matching = 412)))

        compose.onNodeWithText("412").assertIsDisplayed()
        compose.onNode(hasText("coffee places within 1.0 km", substring = true)).assertIsDisplayed()
    }

    @Test
    fun `a minimised map shows the count and not the result set`() {
        show(FakeStartup())

        compose.onNodeWithText("RESULT SET · 1 DOCS").assertIsNotDisplayed()
        compose.onNodeWithText("HUMAN").assertIsNotDisplayed()
        compose.onNodeWithText("BSON").assertIsNotDisplayed()
    }

    @Test
    fun `tapping the count opens the list over the map`() {
        show(FakeStartup())

        openSheet()

        compose.onNodeWithText("RESULT SET · 1 DOCS").assertIsDisplayed()
        compose.onNodeWithText("Two Pups Coffee").assertIsDisplayed()
    }

    @Test
    fun `the namespace sits at the top of the screen, above everything else`() {
        show(FakeStartup())

        val namespace = compose.onNodeWithText(NAMESPACE).assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        val peek = compose.onNodeWithContentDescription(PEEK_DESCRIPTION)
            .fetchSemanticsNode().boundsInRoot

        assertTrue(namespace.top < peek.top, "the console is not above the sheet")
        with(compose.density) {
            assertTrue(namespace.top.toDp() < 80.dp, "the console is ${namespace.top.toDp()} down")
        }
    }

    @Test
    fun `a radius preset re-asks the engine with that radius`() {
        val startup = FakeStartup()
        show(startup)

        compose.onNodeWithText("500 m").performClick()
        startup.settle()
        compose.waitForIdle()

        val geoNear = startup.engine.lastResultPipeline.first()["\$geoNear"] as Document
        assertEquals(500.0, geoNear["maxDistance"])
        compose.onNode(hasText("coffee places within 500 m", substring = true)).assertIsDisplayed()
    }

    @Test
    fun `switching a stage off changes the pipeline the engine is sent`() {
        val startup = FakeStartup()
        show(startup)

        compose.onNodeWithText(NAMESPACE).performClick()
        compose.onNodeWithText("\$limit 50").performClick()
        startup.settle()
        compose.waitForIdle()

        assertTrue(
            startup.engine.lastResultPipeline.none { it.containsKey("\$limit") },
            "the \$limit stage was switched off and the pipeline still has one",
        )
    }

    @Test
    fun `a tap on the map drops the pin somewhere else`() {
        val startup = FakeStartup()
        val picked = mutableListOf<Coordinates>()
        show(startup, onPick = { picked += it })

        compose.onNodeWithContentDescription(MAP_DESCRIPTION).performTouchInput {
            click(Offset(centerX - width / 4f, centerY - height / 4f))
        }
        compose.waitForIdle()

        assertEquals(1, picked.size)
        assertTrue(picked.single().latitude > Ireland.DUBLIN.latitude)
    }

    @Test
    fun `pinching the map zooms the camera in`() {
        val startup = FakeStartup()
        show(startup)
        val before = startup.map.camera.value.latitudeSpan

        compose.onNodeWithContentDescription(MAP_DESCRIPTION).performTouchInput {
            val centre = Offset(centerX, centerY)
            down(0, centre - Offset(40f, 0f))
            down(1, centre + Offset(40f, 0f))
            moveTo(0, centre - Offset(160f, 0f))
            moveTo(1, centre + Offset(160f, 0f))
            up(0)
            up(1)
        }
        compose.waitForIdle()

        assertTrue(
            startup.map.camera.value.latitudeSpan < before,
            "a pinch outward left the camera spanning $before degrees",
        )
    }

    @Test
    fun `the island button frames the island, and the radius button comes back`() {
        val startup = FakeStartup()
        show(startup)
        val framedOnRadius = startup.map.camera.value.latitudeSpan

        compose.onNodeWithContentDescription(FRAME_ISLAND).performClick()
        compose.waitForIdle()
        val island = startup.map.camera.value.latitudeSpan

        compose.onNodeWithContentDescription(FRAME_RADIUS).performClick()
        compose.waitForIdle()

        assertTrue(island > framedOnRadius, "the island button did not widen the camera")
        assertEquals(framedOnRadius, startup.map.camera.value.latitudeSpan)
    }

    @Test
    fun `a failed query says so where the list would be`() {
        val startup = FakeStartup(FakeEngine(failWith = "the engine is closed"))
        show(startup)

        openSheet()

        compose.onNode(hasText("the engine is closed", substring = true)).assertIsDisplayed()
    }

    @Test
    fun `a result can be read as the document it is`() {
        show(FakeStartup(FakeEngine(results = listOf(placeReply(name = "Kaph")))))

        openSheet()
        compose.onNodeWithText("BSON").performClick()
        compose.waitForIdle()

        compose.onNode(hasText("\"cat\": \"coffee_shop\"", substring = true)).assertIsDisplayed()
    }

    /**
     * Opens the sheet the way a finger does: a tap on the count itself.
     *
     * Not on the middle of the peek, which is where the radius slider is -- a tap there is a tap
     * on the slider, as it is on a phone.
     */
    private fun openSheet() {
        compose.onNodeWithContentDescription(PEEK_DESCRIPTION).performTouchInput {
            click(Offset(centerX, height * 0.2f))
        }
        compose.waitForIdle()
    }

    private fun show(
        startup: FakeStartup,
        onPick: (Coordinates) -> Unit = {},
    ) {
        startup.settle()
        compose.setContent {
            CoffeeFinderTheme {
                ConsoleMapScreen(
                    nearby = startup.nearby,
                    map = startup.map,
                    locationSource = LocationSource.FALLBACK,
                    onLocate = {},
                    onPick = onPick,
                )
            }
        }
        compose.waitForIdle()
    }
}
