package io.github.jeroenvervaeke.coffeefinder.ui.console

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.jeroenvervaeke.coffeefinder.data.model.NearbyPlace
import io.github.jeroenvervaeke.coffeefinder.ui.summary
import io.github.jeroenvervaeke.coffeefinder.ui.theme.Console
import io.github.jeroenvervaeke.coffeefinder.ui.theme.MONO_LABEL

/**
 * One result as a person reads it: what it is called, where it is, and how far the engine measured
 * it to be.
 *
 * The tile is generated from the name — see [tileBrush]. There are no photographs in this
 * application and there is no way to fetch one, so a row that wanted an image had to draw it.
 */
@Composable
fun PlaceRow(
    nearby: NearbyPlace,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val place = nearby.place
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(ROW_SHAPE)
            .background(if (selected) Console.Spring.copy(alpha = 0.09f) else Console.Ink.copy(alpha = 0f))
            .border(1.dp, if (selected) Console.Spring.copy(alpha = 0.3f) else Console.Ink.copy(alpha = 0f), ROW_SHAPE)
            .clickable(onClick = onSelect)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(tileBrush(place.name)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = initialsOf(place.name),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = Console.Ink.copy(alpha = 0.8f),
            )
        }

        Column(Modifier.weight(1f).padding(horizontal = 11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = place.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = Console.Mist,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    text = place.category.stored,
                    style = MONO_LABEL,
                    color = Console.Faint,
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .border(1.dp, Console.Line, RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 3.dp),
                )
            }
            Text(
                text = place.summary(),
                style = MONO_LABEL,
                color = Console.Faint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp),
            )
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(nearby.distance.describe(), style = MONO_LABEL, color = Console.Spring)
            ConfidenceBar(place.confidence.value, Modifier.padding(top = 7.dp))
        }
    }
}

/** Overture's confidence in the place, as a detail rather than as a number nobody asked for. */
@Composable
private fun ConfidenceBar(confidence: Double, modifier: Modifier = Modifier) {
    Box(
        modifier
            .width(34.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(Console.Mint.copy(alpha = 0.16f)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(confidence.toFloat())
                .size(height = 3.dp, width = 34.dp)
                .background(Console.Mint),
        )
    }
}

private val ROW_SHAPE = RoundedCornerShape(13.dp)
