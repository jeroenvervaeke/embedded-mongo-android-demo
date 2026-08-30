package io.github.jeroenvervaeke.coffeefinder.ui.console

import androidx.compose.ui.graphics.Brush
import io.github.jeroenvervaeke.coffeefinder.ui.theme.Console

/**
 * The one or two letters that stand for a place in a list.
 *
 * Punctuation is dropped rather than initialised, so `3fe` is `3F` and `Clement & Pekoe` is `CP`
 * rather than `C&`.
 */
fun initialsOf(name: String): String = name
    .split(WORDS)
    .mapNotNull { word -> word.firstOrNull { it.isLetterOrDigit() } }
    .take(2)
    .joinToString("") { it.uppercase() }

/**
 * A tile for a place with no photograph, and there are no photographs: this application ships one
 * BSON file and makes no network call, so every image on the screen is generated from the data.
 *
 * Six gradients from the palette rather than a hue taken from the hash: a free hue puts a pink
 * and an orange tile in a list that is otherwise entirely MongoDB's greens, which reads as a
 * different application's list. The pairing is chosen by the name, so the same shop is the same
 * colour on every launch.
 */
fun tileBrush(name: String): Brush = TILES[name.stableHash() % TILES.size]

/**
 * A hash that does not change between runs.
 *
 * `String.hashCode` would do on any one JVM, but the colour of a tile is part of what the screen
 * looks like, and "stable" here has to mean stable across versions of the platform too.
 */
private fun String.stableHash(): Int {
    var hash = 0
    forEach { character -> hash = (hash * 31 + character.code) and 0x7FFFFFFF }
    return hash
}

/**
 * The six pairings, all light enough for the dark initials that sit on them.
 *
 * Ordered so that neighbours in the list are unlikely to look alike, which is all the variety a
 * 40 dp square needs.
 */
private val TILES: List<Brush> = listOf(
    Console.Mint to Console.Spring,
    Console.Lavender to Console.Blue,
    Console.Spring to Console.Forest,
    Console.Mist to Console.Mint,
    Console.Blue to Console.Forest,
    Console.Lavender to Console.Spring,
).map { (from, to) -> Brush.linearGradient(listOf(from, to)) }

private val WORDS = Regex("\\s+")
