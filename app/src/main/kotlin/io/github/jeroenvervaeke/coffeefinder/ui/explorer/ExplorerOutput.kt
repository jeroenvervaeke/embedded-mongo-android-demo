package io.github.jeroenvervaeke.coffeefinder.ui.explorer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.github.jeroenvervaeke.coffeefinder.data.explorer.ExplorerResult
import io.github.jeroenvervaeke.coffeefinder.ui.console.highlightJson
import io.github.jeroenvervaeke.coffeefinder.ui.describe
import io.github.jeroenvervaeke.coffeefinder.ui.pretty
import io.github.jeroenvervaeke.coffeefinder.ui.theme.Console
import io.github.jeroenvervaeke.coffeefinder.ui.theme.MONO_CODE
import io.github.jeroenvervaeke.coffeefinder.ui.theme.MONO_LABEL
import org.bson.Document

/** What the last run cost, or why there was no last run. */
@Composable
fun ResultMessage(result: ExplorerResult?, modifier: Modifier = Modifier) {
    val (colour, text) = when (result) {
        null -> Console.Faint to AnnotatedString("ready")
        is ExplorerResult.Ran -> Console.Label to buildAnnotatedString {
            withStyle(SpanStyle(color = Console.Spring, fontWeight = FontWeight.Bold)) {
                append(result.took.describe())
            }
            append(" · ${result.documents.size} docs")
            if (result.truncated) append(" · cursor holds more")
        }
        is ExplorerResult.Explained -> Console.Label to AnnotatedString("explained in ${result.took.describe()}")
        is ExplorerResult.Rejected -> Console.Red to AnnotatedString("JSON: ${result.reason}")
        is ExplorerResult.Refused -> Console.Red to AnnotatedString("MongoServerError: ${result.reason}")
    }
    Text(text, style = MONO_LABEL, color = colour, modifier = modifier)
}

/** The documents that came back, or the plan behind them. */
@Composable
fun ExplorerOutput(result: ExplorerResult?, pane: ExplorerPane, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        when {
            result is ExplorerResult.Ran && pane == ExplorerPane.RESULTS -> Documents(result)
            result is ExplorerResult.Explained -> Plan(result.plan)
            result is ExplorerResult.Rejected -> Notice(result.reason, Console.Red)
            result is ExplorerResult.Refused -> Notice(result.reason, Console.Red)
            result == null -> Notice("Nothing has run yet.", Console.Faint)
            // A pipeline that was run and then explained, or the other way round: the pane asks
            // for something this result does not hold, and running again is one tap away.
            else -> Notice("Tap ${pane.name} to ask the engine for it.", Console.Faint)
        }
    }
}

@Composable
private fun Documents(result: ExplorerResult.Ran) {
    if (result.documents.isEmpty()) {
        Notice("0 documents returned.", Console.Faint)
        return
    }
    result.documents.forEach { document -> JsonCard(document) }
    if (result.truncated) {
        Notice("The cursor holds more; this is the first batch.", Console.Faint)
    }
}

@Composable
private fun Plan(plan: Document) {
    val rows = explainSummary(plan)
    if (rows.isNotEmpty()) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(SHAPE)
                .background(Console.Ink.copy(alpha = 0.5f))
                .border(1.dp, Console.Line, SHAPE)
                .padding(13.dp),
        ) {
            rows.forEachIndexed { index, row ->
                if (index > 0) HorizontalDivider(color = Console.Line)
                Row(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
                    Text(row.label, style = MONO_LABEL, color = Console.Faint, modifier = Modifier.weight(1f))
                    Text(row.value, style = MONO_LABEL, color = Console.Mint, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
    Text(
        text = "THE PLAN, AS THE ENGINE REPORTED IT",
        style = MONO_LABEL,
        color = Console.Faint,
        modifier = Modifier.padding(top = 14.dp, bottom = 8.dp),
    )
    JsonCard(plan)
}

@Composable
private fun JsonCard(document: Document) {
    Text(
        text = highlightJson(document.pretty()),
        style = MONO_CODE,
        color = Console.Mint,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .clip(SHAPE)
            .background(Console.Ink.copy(alpha = 0.5f))
            .border(1.dp, Console.Line, SHAPE)
            .heightIn(max = 420.dp)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 11.dp, vertical = 9.dp),
    )
}

@Composable
private fun Notice(text: String, colour: androidx.compose.ui.graphics.Color) {
    Text(text, style = MONO_LABEL, color = colour, modifier = Modifier.padding(vertical = 18.dp))
}

private val SHAPE = RoundedCornerShape(12.dp)
