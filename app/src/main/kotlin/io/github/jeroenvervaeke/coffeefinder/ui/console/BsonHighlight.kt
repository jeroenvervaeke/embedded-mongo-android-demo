package io.github.jeroenvervaeke.coffeefinder.ui.console

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import io.github.jeroenvervaeke.coffeefinder.ui.theme.Console

/**
 * JSON coloured the way a shell colours it: operators loud, keys next, values quiet.
 *
 * Regex rather than a parser, and that is the right size for the job: this colours text that has
 * already been produced by the BSON library's own writer, so it is known to be well formed and
 * nothing here has to decide what it means. A wrong colour is a wrong colour, not a wrong
 * document.
 */
fun highlightJson(text: String): AnnotatedString = buildAnnotatedString {
    append(text)
    TOKENS.findAll(text).forEach { match ->
        val style = when {
            match.groups[OPERATOR] != null -> OPERATOR_STYLE
            match.groups[KEY] != null -> KEY_STYLE
            match.groups[STRING] != null -> STRING_STYLE
            match.groups[NUMBER] != null -> NUMBER_STYLE
            else -> BOOLEAN_STYLE
        }
        addStyle(style, match.range.first, match.range.last + 1)
    }
}

/**
 * A `$`-prefixed key is a stage or an operator, a bare one is a field, and the rest are values.
 *
 * The alternation is ordered: keys are matched before strings so that `"name": "Kaph"` colours
 * its two halves differently.
 */
private val TOKENS = Regex(
    """(?<operator>"\$[A-Za-z][\w.]*"(?=\s*:))""" +
        """|(?<key>"[^"]*"(?=\s*:))""" +
        """|(?<string>"(?:[^"\\]|\\.)*")""" +
        """|(?<number>-?\d+(?:\.\d+)?(?:[eE][-+]?\d+)?)""" +
        """|(?<boolean>\b(?:true|false|null)\b)""",
)

private const val OPERATOR = "operator"
private const val KEY = "key"
private const val STRING = "string"
private const val NUMBER = "number"

private val OPERATOR_STYLE = SpanStyle(color = Console.Spring, fontWeight = FontWeight.Bold)
private val KEY_STYLE = SpanStyle(color = Console.Mint)
private val STRING_STYLE = SpanStyle(color = Console.Lavender)
private val NUMBER_STYLE = SpanStyle(color = Console.Blue)
private val BOOLEAN_STYLE = SpanStyle(color = Console.Amber)
