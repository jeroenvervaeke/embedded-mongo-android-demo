package io.github.jeroenvervaeke.coffeefinder.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** What the engine said, at reading size. */
val MONO = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 18.sp)

/** A caption in the engine's voice: index names, stage names, readouts. */
val MONO_LABEL = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 10.sp,
    lineHeight = 14.sp,
    fontWeight = FontWeight.Medium,
    letterSpacing = 0.8.sp,
)

/** A pipeline or a document, printed. Smaller, because it is read in blocks rather than in lines. */
val MONO_CODE = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 17.sp)

/**
 * Two voices, and the rule for which is which: monospace for anything the engine said, the
 * device's own sans for anything a person wrote.
 *
 * It is the whole typographic idea of the screen. A distance the engine measured, a millisecond
 * reading, an index name and a stage are set in mono; a coffee shop's name is not. The headline
 * count is serif, because it is the one number the screen exists to say.
 *
 * The families are the platform's — no font files are bundled and none are fetched. An
 * application whose point is that it makes no network call has no business downloading a
 * typeface, and 300 KB of woff2 per weight is a strange thing to ship to say "5,180".
 *
 * Declared after the three styles it reads: these are top-level properties, and one initialised
 * after its reader is null when the reader runs.
 */
val ConsoleTypography = Typography().let { defaults ->
    defaults.copy(
        displayLarge = TextStyle(
            fontFamily = FontFamily.Serif,
            fontSize = 56.sp,
            lineHeight = 56.sp,
            letterSpacing = (-1).sp,
        ),
        headlineMedium = defaults.headlineMedium.copy(fontFamily = FontFamily.Serif),
        headlineSmall = defaults.headlineSmall.copy(fontFamily = FontFamily.Serif),
        titleLarge = defaults.titleLarge.copy(fontFamily = FontFamily.Serif),
        labelSmall = MONO_LABEL,
        labelMedium = MONO.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
    )
}
