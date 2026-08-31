package io.github.jeroenvervaeke.coffeefinder.ui.about

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import io.github.jeroenvervaeke.coffeefinder.data.model.PlaceCategory
import io.github.jeroenvervaeke.coffeefinder.ui.theme.Console
import io.github.jeroenvervaeke.coffeefinder.ui.theme.MONO_LABEL

/** A figure, and what it is a figure of. */
@Composable
fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(CARD_SHAPE)
            .background(Brush.linearGradient(listOf(Console.Forest.copy(alpha = 0.22f), Console.Panel)))
            .border(1.dp, Console.Line, CARD_SHAPE)
            .padding(13.dp),
    ) {
        Text(value, style = MaterialTheme.typography.headlineSmall, color = Console.Mist)
        Text(label, style = MONO_LABEL, color = Console.Faint, modifier = Modifier.padding(top = 7.dp))
    }
}

/** A titled panel, which is what everything on this screen is. */
@Composable
fun AboutCard(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(CARD_SHAPE)
            .background(Console.Panel.copy(alpha = 0.6f))
            .border(1.dp, Console.Line, CARD_SHAPE)
            .padding(15.dp),
    ) {
        Text(title, style = MONO_LABEL, color = Console.Faint, modifier = Modifier.padding(bottom = 13.dp))
        content()
    }
}

/**
 * The category split, counted by the engine a moment ago.
 *
 * Drawn as arcs rather than as a bar chart because three of the four categories are rounding
 * errors (5 cafeterias and 1 roastery against 5,180 documents), and a ring makes "almost all of
 * it is two categories" legible in a way four bars do not. Each slice gets a visible minimum so
 * the roastery is on the screen at all.
 */
@Composable
fun CategoryDonut(tally: Map<PlaceCategory, Long>, modifier: Modifier = Modifier) {
    val counted = tally.values.sum()
    // Before the `$group` comes back there is nothing to divide by, and nothing worth claiming:
    // an empty ring and a dash, rather than a donut drawn as though one document had been found.
    val total = counted.coerceAtLeast(1L)

    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(112.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(112.dp)) {
                val stroke = 13.dp.toPx()
                val inset = stroke / 2
                val arcSize = Size(size.width - stroke, size.height - stroke)
                drawArc(
                    color = Console.Mint.copy(alpha = 0.09f),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke),
                )
                var start = -90f
                PlaceCategory.entries.forEach { category ->
                    val share = (tally[category] ?: 0L).toFloat() / total
                    val sweep = maxOf(share * 360f, if (tally.containsKey(category)) MINIMUM_SLICE else 0f)
                    drawArc(
                        color = category.colour(),
                        startAngle = start,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(width = stroke),
                    )
                    start += sweep
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (counted > 0) "%,d".format(counted) else "—",
                    style = MaterialTheme.typography.titleLarge,
                    color = Console.Mist,
                )
                Text("DOCUMENTS", style = MONO_LABEL, color = Console.Faint)
            }
        }

        Column(
            Modifier.padding(start = 15.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            PlaceCategory.entries.forEach { category ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(category.colour()))
                    Text(
                        text = category.stored,
                        style = MONO_LABEL,
                        color = Console.Mint,
                        modifier = Modifier.padding(start = 7.dp).weight(1f),
                    )
                    Text(
                        text = tally[category]?.let { "%,d".format(it) } ?: "—",
                        style = MONO_LABEL,
                        color = Console.Faint,
                    )
                }
            }
        }
    }
}

/** One phase, drawn as what it cost against the slowest phase of the same launch. */
@Composable
fun TimingBar(label: String, range: String, share: Double, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(bottom = 11.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(label, style = MONO_LABEL, color = Console.Label, modifier = Modifier.weight(1f))
            Text(range, style = MONO_LABEL, color = Console.Spring)
        }
        // A weighted row rather than a fraction of the width: the bar is this phase against the
        // longest one, so the shape of a start-up is readable at a glance.
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Console.Mint.copy(alpha = 0.1f)),
        ) {
            val filled = share.toFloat().coerceIn(SLIVER, 1f)
            Box(
                Modifier
                    .weight(filled)
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Brush.horizontalGradient(listOf(Console.Forest, Console.Spring))),
            )
            Spacer(Modifier.weight((1f - filled).coerceAtLeast(SLIVER)))
        }
    }
}

/** A weight of zero is not allowed, and a phase that took no time at all is a normal thing. */
private const val SLIVER = 0.0001f

private fun PlaceCategory.colour(): Color = when (this) {
    PlaceCategory.CAFE -> Console.Spring
    PlaceCategory.COFFEE_SHOP -> Console.Blue
    PlaceCategory.CAFETERIA -> Console.Mint
    PlaceCategory.COFFEE_ROASTERY -> Console.Lavender
}

/** Enough of a slice that one document in five thousand is still visible. */
private const val MINIMUM_SLICE = 2.5f

private val CARD_SHAPE = RoundedCornerShape(16.dp)
