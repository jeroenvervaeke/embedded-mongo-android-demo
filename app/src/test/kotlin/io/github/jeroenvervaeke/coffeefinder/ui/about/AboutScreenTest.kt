package io.github.jeroenvervaeke.coffeefinder.ui.about

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.jeroenvervaeke.coffeefinder.data.PlaceRepository
import io.github.jeroenvervaeke.coffeefinder.ui.FakeEngine
import io.github.jeroenvervaeke.coffeefinder.ui.StartupPhase
import kotlin.time.Duration.Companion.milliseconds
import io.github.jeroenvervaeke.coffeefinder.ui.theme.CoffeeFinderTheme
import org.bson.Document
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AboutScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `it says who built it`() {
        show()

        compose.onNodeWithText("Jeroen Vervaeke").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `the credits name what it is built on`() {
        show()

        compose.onNodeWithText("embedded-mongodb").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Overture Maps Foundation").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `it says out loud that it is not a MongoDB product`() {
        show()

        compose.onNodeWithText("Not a MongoDB product").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `the headline counts what the engine counted`() {
        show(tally = mapOf("cafe" to 2_931, "coffee_shop" to 2_243, "cafeteria" to 5, "coffee_roastery" to 1))

        compose.onAllNodesWithText("5,180", substring = true).onFirst().assertIsDisplayed()
    }

    @Test
    fun `every category is in the legend, with what the engine counted for it`() {
        show(tally = mapOf("cafe" to 2_931, "coffee_shop" to 2_243, "cafeteria" to 5, "coffee_roastery" to 1))

        compose.onNodeWithText("coffee_roastery").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("1").assertIsDisplayed()
    }

    @Test
    fun `a tally the engine would not answer leaves the screen standing`() {
        show(FakeEngine(failWith = "the engine is closed"))

        compose.onNodeWithText("Jeroen Vervaeke").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("NETWORK CALLS").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `the timings are this launch's own, named after the phases that produced them`() {
        show()

        compose.onNodeWithText("Opening the engine").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("1.20 s").assertIsDisplayed()
        compose.onNodeWithText("Inserting").assertIsDisplayed()
    }

    @Test
    fun `a launch that reported no phases leaves the rest of the screen standing`() {
        compose.setContent {
            CoffeeFinderTheme { AboutScreen(PlaceRepository(FakeEngine().collection), emptyList()) }
        }
        compose.waitForIdle()

        compose.onNodeWithText("Jeroen Vervaeke").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `a licence opens where it is, and its text comes from the file it ships in`() {
        show()

        compose.onNodeWithText("Apache License 2.0  ▸").performScrollTo().performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Apache License 2.0  ▾").assertIsDisplayed()
        // The body is read from assets off the main thread, so it lands a beat after the tap.
        compose.waitUntil(TEXT_TIMEOUT) {
            compose.onAllNodesWithText("TERMS AND CONDITIONS", substring = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    private fun show(engine: FakeEngine = FakeEngine(), tally: Map<String, Int> = emptyMap()) {
        val serving = if (tally.isEmpty()) engine else FakeEngine(
            results = tally.map { (category, count) -> Document("_id", category).append("n", count) },
        )
        compose.setContent {
            CoffeeFinderTheme { AboutScreen(PlaceRepository(serving.collection), PHASES) }
        }
        compose.waitForIdle()
    }
}

/** Long enough for a file read, short enough that a stuck one still fails the test. */
private const val TEXT_TIMEOUT = 5_000L

/** A launch that opened an engine, seeded it and built its indexes, as the timer reports one. */
private val PHASES = listOf(
    StartupPhase("opening the engine", 1_200.milliseconds),
    StartupPhase("inserting", 250.milliseconds),
    StartupPhase("building the 2dsphere and text indexes", 85.milliseconds),
)
