package io.github.jeroenvervaeke.coffeefinder.ui.console

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.github.jeroenvervaeke.coffeefinder.ui.describe
import io.github.jeroenvervaeke.coffeefinder.ui.pretty
import io.github.jeroenvervaeke.coffeefinder.ui.theme.Console
import io.github.jeroenvervaeke.coffeefinder.ui.theme.MONO_CODE
import io.github.jeroenvervaeke.coffeefinder.ui.theme.MONO_LABEL
import kotlin.time.Duration
import org.bson.Document

/** A stage of the map's pipeline that a person can switch on and off. */
data class StageToggle(val label: String, val on: Boolean, val onChange: (Boolean) -> Unit)

/** What the console prints: the command that ran, what it cost, and what can be changed about it. */
data class ConsoleReadout(
    val command: Document?,
    val took: Duration?,
    val documents: Int,
    val stages: List<StageToggle>,
)

/**
 * The console over the map: the namespace, the pipeline that produced what is on screen, and what
 * it cost.
 *
 * It sits at the very top of the screen, under the status bar and nothing else. Tapping it opens
 * the stages a person can switch, and the command itself, read out of the reply that produced
 * the results, so the JSON here is the JSON that crossed the bridge.
 */
@Composable
fun ConsoleHud(
    readout: ConsoleReadout,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .clip(SHAPE)
            .background(Console.Panel.copy(alpha = 0.92f))
            .border(1.dp, Console.Line, SHAPE),
    ) {
        Row(
            Modifier.clickable { onExpandedChange(!expanded) }.padding(horizontal = 11.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(Console.Spring))
            Text(
                text = NAMESPACE,
                style = MONO_LABEL,
                color = Console.Mint,
                modifier = Modifier.padding(start = 8.dp).weight(1f),
            )
            Text(
                text = if (expanded) "HIDE ▴" else "PIPELINE ▾",
                style = MONO_LABEL,
                color = Console.Faint,
            )
        }

        readout.command?.let { command ->
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    .padding(start = 11.dp, end = 11.dp, bottom = 9.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(command.stageLine(), style = MONO_LABEL, color = Console.Spring)
            }
        }

        AnimatedVisibility(expanded) {
            Column {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                        .padding(horizontal = 11.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    readout.stages.forEach { stage ->
                        StageChip(stage)
                    }
                }
                readout.command?.let { command ->
                    val json = remember(command) { highlightJson(command.pretty()) }
                    Text(
                        text = json,
                        style = MONO_CODE,
                        color = Console.Mint,
                        modifier = Modifier
                            .padding(11.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Console.Ink.copy(alpha = 0.66f))
                            .border(1.dp, Console.Line, RoundedCornerShape(10.dp))
                            .heightIn(max = 190.dp)
                            .verticalScroll(rememberScrollState())
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 10.dp, vertical = 9.dp),
                    )
                }
            }
        }

        Text(
            text = readout.line(),
            style = MONO_LABEL,
            color = Console.Label,
            modifier = Modifier.padding(start = 11.dp, end = 11.dp, bottom = 10.dp),
        )
    }
}

/** The collection every query on this screen runs against. */
const val NAMESPACE = "coffee.places"

@Composable
private fun StageChip(stage: StageToggle) {
    Text(
        text = stage.label,
        style = MONO_LABEL,
        color = if (stage.on) Console.Spring else Console.Label,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (stage.on) Console.Spring.copy(alpha = 0.14f) else Console.Ink.copy(alpha = 0.4f))
            .border(
                width = 1.dp,
                color = if (stage.on) Console.Spring.copy(alpha = 0.5f) else Console.Edge,
                shape = RoundedCornerShape(8.dp),
            )
            .clickable { stage.onChange(!stage.on) }
            .padding(horizontal = 9.dp, vertical = 6.dp),
    )
}

/** `$geoNear → $match cat → $limit 50`, which is the pipeline in one line. */
private fun Document.stageLine(): String = stageLabels().joinToString("  →  ")

/** `12.4 ms · 38 docs · loc_2dsphere`, or what to say before the first reply. */
private fun ConsoleReadout.line(): AnnotatedString = buildAnnotatedString {
    if (command == null || took == null) {
        append("waiting for the engine…")
        return@buildAnnotatedString
    }
    withStyle(SpanStyle(color = Console.Spring, fontWeight = FontWeight.Bold)) {
        append(took.describe())
    }
    append(" · $documents docs · ")
    withStyle(SpanStyle(color = Console.Blue)) { append(command.indexBehind()) }
}

private val SHAPE = RoundedCornerShape(16.dp)
