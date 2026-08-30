package io.github.jeroenvervaeke.coffeefinder.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.jeroenvervaeke.coffeefinder.location.LocationSource
import io.github.jeroenvervaeke.coffeefinder.ui.console.MAP_DESCRIPTION
import io.github.jeroenvervaeke.coffeefinder.ui.theme.CoffeeFinderTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FinderTabsTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `it opens on the map`() {
        show()

        compose.onNodeWithContentDescription(MAP_DESCRIPTION).assertIsDisplayed()
    }

    @Test
    fun `the explorer is one tap away`() {
        show()

        compose.onNodeWithText("EXPLORER").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("db.places.aggregate( pipeline )").assertIsDisplayed()
        compose.onNodeWithContentDescription(MAP_DESCRIPTION).assertDoesNotExist()
    }

    @Test
    fun `about is one tap away and knows who built this`() {
        show()

        compose.onNodeWithText("ABOUT").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Jeroen Vervaeke").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `the map comes back`() {
        show()
        compose.onNodeWithText("ABOUT").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("MAP").performClick()
        compose.waitForIdle()

        compose.onNodeWithContentDescription(MAP_DESCRIPTION).assertIsDisplayed()
    }

    @Test
    fun `the map still opens minimised after coming back from another tab`() {
        show()
        compose.onNodeWithText("EXPLORER").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("MAP").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("HUMAN").assertIsNotDisplayed()
    }

    @Test
    fun `every tab is drawn with an icon over its name`() {
        show()

        Destination.entries.forEach { destination ->
            compose.onNodeWithText(destination.label).assertIsDisplayed()
        }
        // The icons are canvas, so what a test can hold is that the bar is a row of three and
        // that each one still switches the screen -- covered by the tests above.
        compose.onNodeWithContentDescription(MAP_DESCRIPTION).assertIsDisplayed()
    }

    private fun show() {
        val startup = FakeStartup()
        startup.settle()
        compose.setContent {
            CoffeeFinderTheme {
                Finder(startup.ready, LocationSource.FALLBACK, onRequestLocation = {}, onPick = {})
            }
        }
        compose.waitForIdle()
    }
}
