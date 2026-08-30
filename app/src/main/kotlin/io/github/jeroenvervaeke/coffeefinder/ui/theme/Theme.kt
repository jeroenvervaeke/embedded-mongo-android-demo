package io.github.jeroenvervaeke.coffeefinder.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * The console palette: MongoDB's own greens on an evergreen ground.
 *
 * Dark only, and deliberately: this screen is an instrument. The map is a few thousand dots and a
 * radius ring drawn over a dark ground, and the readouts are meant to look like a readout — a
 * light scheme would need a second set of decisions for every one of them, and a dynamic-colour
 * one can leave the dots and the ground a step apart.
 */
object Console {
    val Ink = Color(0xFF00121A)
    val Evergreen = Color(0xFF001E2B)
    val Panel = Color(0xFF04202C)
    val PanelRaised = Color(0xFF062A38)

    /** The one colour that means "this is the answer". Used sparingly enough to keep meaning it. */
    val Spring = Color(0xFF00ED64)
    val Forest = Color(0xFF00684A)
    val Mint = Color(0xFFC3F3D7)
    val Mist = Color(0xFFF9FBFA)
    val Blue = Color(0xFF0498EC)
    val Lavender = Color(0xFFF9EBFF)
    val Amber = Color(0xFFFFB86B)
    val Red = Color(0xFFFF8A8A)

    /** Mint at the four weights the screens actually use, named by what they are for. */
    val Line = Mint.copy(alpha = 0.13f)
    val Edge = Mint.copy(alpha = 0.26f)
    val Label = Mint.copy(alpha = 0.55f)
    val Faint = Mint.copy(alpha = 0.36f)

    /** A place on the map that no current query returned. */
    val Marker = Mint.copy(alpha = 0.28f)
}

private val ConsoleScheme = darkColorScheme(
    primary = Console.Spring,
    onPrimary = Console.Ink,
    secondary = Console.Blue,
    onSecondary = Console.Ink,
    tertiary = Console.Mint,
    background = Console.Ink,
    onBackground = Console.Mist,
    surface = Console.Panel,
    onSurface = Console.Mist,
    surfaceVariant = Console.PanelRaised,
    onSurfaceVariant = Console.Label,
    outline = Console.Edge,
    outlineVariant = Console.Line,
    error = Console.Red,
    onError = Console.Ink,
)

@Composable
fun CoffeeFinderTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = ConsoleScheme, typography = ConsoleTypography, content = content)
}
