package io.github.jeroenvervaeke.coffeefinder.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

/**
 * Attribution, licences and the disclaimer.
 *
 * The texts are read out of `assets/licenses`, which is where they ship: CDLA-Permissive-2.0
 * section 2.1 and the Foursquare NOTICE both require the agreement to travel with the data, and
 * naming a licence does not satisfy either. They are on screen, in full, from the files.
 */
@Composable
fun AboutScreen(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Coffee Offline", style = MaterialTheme.typography.headlineSmall)
        Text(
            text = "Every coffee place on the island of Ireland, in a MongoDB engine running " +
                "inside this application. No server, no network, no API key. The map is drawn " +
                "from the query results themselves — there are no tiles and no map SDK.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp),
        )

        Section("Not a MongoDB product") {
            Text(
                text = "This project is not supported by, endorsed by, or affiliated with " +
                    "MongoDB, Inc. It is an independent demonstration of an embedded build of " +
                    "the MongoDB engine, and it uses no MongoDB logo or branding.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Section("Data") {
            Text(
                text = "Places from the Overture Maps Foundation, overturemaps.org — release " +
                    "2026-08-19.0, accessed 2026-08-27. The data has been modified: it is " +
                    "filtered to four coffee categories inside a bounding box around Ireland, " +
                    "and reshaped into the documents this application stores.\n\n" +
                    "© 2026 Foursquare Labs, Inc. All rights reserved.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Section("Licences") {
            Text(
                text = "Contributing datasets are covered by the Community Data License " +
                    "Agreement — Permissive 2.0 (Meta, Microsoft, PinMeTo, Krick, RenderSEO, " +
                    "DAC, BrightQuery), the Apache License 2.0 with the Foursquare NOTICE, and " +
                    "CC0 1.0 (AllThePlaces). All four texts ship inside this application and " +
                    "are below in full — naming a licence does not satisfy it.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        LICENCE_DOCUMENTS.forEach { LicenceText(it.title, it.asset) }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    HorizontalDivider(Modifier.padding(vertical = 16.dp))
    Text(title, style = MaterialTheme.typography.titleMedium)
    Column(Modifier.padding(top = 8.dp)) { content() }
}

/** Collapsed by default: eleven kilobytes of Apache 2.0 is a licence, not a paragraph. */
@Composable
private fun LicenceText(title: String, asset: String) {
    var open by rememberSaveable { mutableStateOf(false) }

    HorizontalDivider(Modifier.padding(vertical = 16.dp))
    Text(
        text = if (open) "$title  ▾" else "$title  ▸",
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.fillMaxWidth().clickable { open = !open },
    )
    AnimatedVisibility(open) {
        // Read here rather than above, so opening the screen does not read five files nobody
        // has asked to see.
        val text by assetText(asset)
        Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.padding(12.dp),
            )
        }
    }
}
