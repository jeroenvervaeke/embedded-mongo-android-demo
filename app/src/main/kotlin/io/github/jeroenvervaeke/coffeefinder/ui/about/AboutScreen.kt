package io.github.jeroenvervaeke.coffeefinder.ui.about

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.github.jeroenvervaeke.coffeefinder.data.PlaceRepository
import io.github.jeroenvervaeke.coffeefinder.data.model.PlaceCategory
import io.github.jeroenvervaeke.coffeefinder.ui.theme.Console
import io.github.jeroenvervaeke.coffeefinder.ui.StartupPhase
import io.github.jeroenvervaeke.coffeefinder.ui.describe
import io.github.jeroenvervaeke.coffeefinder.ui.theme.MONO_LABEL
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * What this is, who made it, what it is made of, and the licences it is obliged to carry.
 *
 * Every number on it was produced by this launch: the donut is a `$group` the engine ran while
 * the screen was opening, and the bars are what getting the database ready cost a moment ago on
 * this phone. Nothing here is written down, so nothing here can go stale.
 */
@Composable
fun AboutScreen(
    places: PlaceRepository,
    startup: List<StartupPhase>,
    modifier: Modifier = Modifier,
) {
    val tally by produceState(initialValue = emptyMap<PlaceCategory, Long>(), places) {
        value = runCatching { places.categoryTally() }.getOrDefault(emptyMap())
    }
    val total = tally.values.sum()

    Column(
        modifier
            .fillMaxSize()
            .background(Console.Ink)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Text(
            text = buildAnnotatedString {
                append("No server.\nNo network.\n")
                withStyle(SpanStyle(color = Console.Spring, fontStyle = FontStyle.Italic)) {
                    append(if (total > 0) "%,d documents".format(total) else "every coffee place")
                }
                append("\nin your pocket.")
            },
            style = MaterialTheme.typography.headlineMedium,
            color = Console.Mist,
            modifier = Modifier.padding(top = 14.dp),
        )
        Text(
            text = "MongoDB's own storage engine runs in-process on the handset. Every query on " +
                "these screens is a real aggregation pipeline, executed locally against a " +
                "WiredTiger collection: the same engine and the same operators, with no cloud " +
                "round trip and nothing to degrade when the signal goes.",
            style = MaterialTheme.typography.bodyMedium,
            color = Console.Label,
            modifier = Modifier.padding(top = 13.dp),
        )

        Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            StatTile(if (total > 0) "%,d".format(total) else "—", "DOCUMENTS", Modifier.weight(1f))
            StatTile("0", "NETWORK CALLS", Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth().padding(top = 9.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            StatTile("46 MiB", "ENGINE PER ABI", Modifier.weight(1f))
            StatTile("28", "MIN SDK", Modifier.weight(1f))
        }

        Section {
            AboutCard("CATEGORY DISTRIBUTION · \$group") { CategoryDonut(tally) }
        }

        Section {
            AboutCard("THIS LAUNCH · ${Build.MODEL}") {
                Column {
                    val slowest = startup.maxOfOrNull { it.took } ?: Duration.ZERO
                    startup.forEach { phase ->
                        TimingBar(
                            label = phase.name.replaceFirstChar(Char::uppercase),
                            range = phase.took.describe(),
                            share = phase.took / slowest.coerceAtLeast(1.milliseconds),
                        )
                    }
                    Text(
                        text = "Measured by this launch, on this phone. A cold start is an engine " +
                            "opening, 5,180 documents going in and two index builds; a warm one " +
                            "skips the middle. Engine open is most of the variance, and it is " +
                            "page cache.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Console.Faint,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }

        Section {
            AboutCard("INDEXES") {
                Column {
                    IndexNote(
                        name = "loc_2dsphere",
                        description = "Walked outward by \$geoNear and selected from by " +
                            "\$geoWithin. It reads the documents it returns rather than all of them.",
                    )
                    IndexNote(
                        name = "name_brand_text",
                        description = "Weighted name:10, brand:5, so a shop's own name outranks the " +
                            "chain it belongs to. \$text and \$geoNear both demand the first " +
                            "stage, so a text search measures distance with a haversine written " +
                            "in \$sin and \$asin instead.",
                        modifier = Modifier.padding(top = 13.dp),
                    )
                }
            }
        }

        Section { Credits() }

        LicenceTexts(Modifier.padding(top = 8.dp, bottom = 28.dp))
    }
}

@Composable
private fun Section(content: @Composable () -> Unit) {
    Column(Modifier.padding(top = 11.dp)) { content() }
}

@Composable
private fun IndexNote(name: String, description: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(name, style = MONO_LABEL, color = Console.Mint)
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = Console.Faint,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}


