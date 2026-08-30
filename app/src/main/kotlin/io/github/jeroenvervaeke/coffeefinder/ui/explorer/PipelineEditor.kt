package io.github.jeroenvervaeke.coffeefinder.ui.explorer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.github.jeroenvervaeke.coffeefinder.data.explorer.PipelinePreset
import io.github.jeroenvervaeke.coffeefinder.ui.console.highlightJson
import io.github.jeroenvervaeke.coffeefinder.ui.theme.Console
import io.github.jeroenvervaeke.coffeefinder.ui.theme.MONO_CODE
import io.github.jeroenvervaeke.coffeefinder.ui.theme.MONO_LABEL

/**
 * Where a pipeline is written: a line gutter, mono text, and the same colouring the console over
 * the map uses.
 *
 * The colouring is a [VisualTransformation] rather than a second layer drawn behind the field.
 * It only adds spans, so the text it hands back is the text that was typed, character for
 * character — which is what keeps the cursor where the finger put it.
 */
@Composable
fun PipelineEditor(text: String, onText: (String) -> Unit, modifier: Modifier = Modifier) {
    val scroll = rememberScrollState()

    Row(
        modifier
            .fillMaxWidth()
            .height(210.dp)
            .clip(SHAPE)
            .background(Console.Ink.copy(alpha = 0.6f))
            .border(1.dp, Console.Edge, SHAPE),
    ) {
        Column(
            Modifier
                .width(34.dp)
                .background(Console.Ink.copy(alpha = 0.5f))
                .verticalScroll(scroll)
                .padding(vertical = 9.dp, horizontal = 4.dp),
            horizontalAlignment = androidx.compose.ui.Alignment.End,
        ) {
            repeat(text.count { it == '\n' } + 1) { line ->
                Text("${line + 1}", style = MONO_CODE, color = Console.Mint.copy(alpha = 0.24f))
            }
        }
        BasicTextField(
            value = text,
            onValueChange = onText,
            textStyle = MONO_CODE.copy(color = Console.Mint),
            cursorBrush = SolidColor(Console.Spring),
            visualTransformation = JsonColours,
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scroll)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 9.dp),
        )
    }
}

/** Presets and single stages, both of which are one tap into the editor. */
@Composable
fun SnippetRow(
    snippets: List<PipelinePreset>,
    selected: String?,
    onPick: (PipelinePreset) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        snippets.forEach { snippet ->
            val on = snippet.name == selected
            Text(
                text = snippet.name,
                style = MONO_LABEL,
                color = if (on) Console.Spring else Console.Label,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (on) Console.Spring.copy(alpha = 0.14f) else Console.Ink.copy(alpha = 0.4f))
                    .border(
                        width = 1.dp,
                        color = if (on) Console.Spring.copy(alpha = 0.5f) else Console.Edge,
                        shape = RoundedCornerShape(8.dp),
                    )
                    .clickable { onPick(snippet) }
                    .padding(horizontal = 9.dp, vertical = 6.dp),
            )
        }
    }
}

/**
 * A stage dropped into a pipeline that is already there.
 *
 * Inserted before the closing bracket, with the comma the stage in front of it now needs. Text
 * rather than documents, because what is in the editor may not parse yet — this is a typing aid,
 * not an edit to a pipeline.
 */
fun String.withStageAppended(stage: String): String {
    val trimmed = trimEnd()
    val close = trimmed.lastIndexOf(']')
    if (close < 0) return "$trimmed\n$stage"
    val head = trimmed.take(close).trimEnd()
    val comma = if (head.endsWith('}') || head.endsWith(']')) "," else ""
    return "$head$comma\n  $stage\n]"
}

private object JsonColours : VisualTransformation {
    override fun filter(text: androidx.compose.ui.text.AnnotatedString) =
        TransformedText(highlightJson(text.text), OffsetMapping.Identity)
}

private val SHAPE = RoundedCornerShape(14.dp)
