package io.github.jeroenvervaeke.coffeefinder.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Roasted coffee, and the cream that goes in it. */
private val Roast = Color(0xFF6F4E37)
private val LightRoast = Color(0xFFC8A27A)
private val Cream = Color(0xFFFFF6EC)
private val Espresso = Color(0xFF241812)

/** The dot on the map, chosen to sit clearly on both schemes rather than on one. */
val PlaceMarker = Color(0xFFE08A3C)

private val Light = lightColorScheme(
    primary = Roast,
    onPrimary = Cream,
    secondary = LightRoast,
    background = Cream,
    surface = Cream,
    onBackground = Espresso,
    onSurface = Espresso,
)

private val Dark = darkColorScheme(
    primary = LightRoast,
    onPrimary = Espresso,
    secondary = Roast,
    background = Espresso,
    surface = Color(0xFF31221A),
    onBackground = Cream,
    onSurface = Cream,
)

/**
 * Deliberately not a dynamic-colour theme. The map is a few thousand dots on a flat ground, and
 * a wallpaper-derived palette can leave those two colours a step apart.
 */
@Composable
fun CoffeeFinderTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
