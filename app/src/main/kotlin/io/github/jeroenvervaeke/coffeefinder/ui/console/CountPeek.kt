package io.github.jeroenvervaeke.coffeefinder.ui.console

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.jeroenvervaeke.coffeefinder.data.model.Metres
import io.github.jeroenvervaeke.coffeefinder.ui.theme.Console
import io.github.jeroenvervaeke.coffeefinder.ui.theme.MONO_LABEL
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The headline: how many coffee places are inside the radius, and the radius itself.
 *
 * This is all the screen shows over the map when the list is down: a number, what it counts, and
 * the control that changes it. Everything else belongs to the list it opens into.
 */
@Composable
fun CountPeek(
    matching: Long,
    listed: Int,
    radius: Metres,
    onRadius: (Metres) -> Unit,
    searching: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = if (searching) "—" else COUNT.format(matching),
                style = MaterialTheme.typography.displayLarge,
                color = Console.Mist,
            )
            Column(Modifier.padding(start = 10.dp, bottom = 6.dp)) {
                Text(
                    text = "coffee places within ${radius.describe()}\nof the dropped pin",
                    style = MaterialTheme.typography.bodySmall,
                    color = Console.Label,
                )
                if (matching > listed) {
                    Text(
                        text = "the nearest $listed of them are listed",
                        style = MONO_LABEL,
                        color = Console.Amber,
                        modifier = Modifier.padding(top = 5.dp),
                    )
                }
            }
        }

        Slider(
            value = radius.value.toFloat(),
            // Snapped here rather than by `steps`, which would draw 54 tick marks across the
            // track and turn it into a dotted line.
            onValueChange = { onRadius(Metres(it.snappedToStep().toDouble())) },
            valueRange = MINIMUM_RADIUS..MAXIMUM_RADIUS,
            colors = SliderDefaults.colors(
                thumbColor = Console.Spring,
                activeTrackColor = Console.Spring,
                inactiveTrackColor = Console.Mint.copy(alpha = 0.16f),
            ),
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            PRESETS.forEach { preset ->
                RadiusPreset(preset, selected = preset.value == radius.value, onRadius, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun RadiusPreset(
    radius: Metres,
    selected: Boolean,
    onRadius: (Metres) -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        text = radius.describe(),
        style = MONO_LABEL,
        color = if (selected) Console.Spring else Console.Faint,
        textAlign = TextAlign.Center,
        modifier = modifier
            .clip(PRESET_SHAPE)
            .background(if (selected) Console.Spring.copy(alpha = 0.10f) else Console.Panel)
            .border(1.dp, if (selected) Console.Spring.copy(alpha = 0.45f) else Console.Line, PRESET_SHAPE)
            .clickable { onRadius(radius) }
            .padding(vertical = 7.dp),
    )
}

/** The radii worth one tap: a street, a block, a walk, and a long walk. */
private val PRESETS = listOf(Metres(250.0), Metres(500.0), Metres(1_000.0), Metres(2_000.0))

private const val MINIMUM_RADIUS = 250f

private const val MAXIMUM_RADIUS = 3_000f

/** Every 50 m, which is finer than a fingertip on a phone-width track and reads as a round number. */
private const val STEP = 50f

private fun Float.snappedToStep(): Float = (this / STEP).roundToInt() * STEP

/** Grouped, because "1,284" is a number and "1284" is a string of digits. */
private val COUNT = "%,d"

private fun String.format(value: Long) = String.format(Locale.getDefault(), this, value)

private val PRESET_SHAPE = RoundedCornerShape(7.dp)
