package io.github.jeroenvervaeke.coffeefinder.ui.about

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import io.github.jeroenvervaeke.coffeefinder.ui.LICENCE_DOCUMENTS
import io.github.jeroenvervaeke.coffeefinder.ui.assetText
import io.github.jeroenvervaeke.coffeefinder.ui.theme.Console
import io.github.jeroenvervaeke.coffeefinder.ui.theme.MONO_CODE
import io.github.jeroenvervaeke.coffeefinder.ui.theme.MONO_LABEL

/** Who built this, what it is built on, and what it is not. */
@Composable
fun Credits(modifier: Modifier = Modifier) {
    AboutCard("CREDITS", modifier) {
        Column {
            Text("Jeroen Vervaeke", style = MaterialTheme.typography.titleLarge, color = Console.Mist)
            Text(
                text = "Built the application, and the embedded-mongodb library it runs on.",
                style = MaterialTheme.typography.bodySmall,
                color = Console.Label,
                modifier = Modifier.padding(top = 5.dp),
            )

            Credit(
                title = "embedded-mongodb",
                detail = "github.com/jeroenvervaeke/embedded-mongo: MongoDB's engine, built for " +
                    "Android and driven over a JNI bridge. This application consumes it as an " +
                    "included build and owns none of the query machinery: the aggregate command, " +
                    "the cursor paging and the write checking are all the library's.",
            )
            Credit(
                title = "Overture Maps Foundation",
                detail = "overturemaps.org: places release 2026-08-19.0, accessed 2026-08-27, " +
                    "filtered to four coffee categories inside a box around Ireland and reshaped " +
                    "into the documents this application stores. © 2026 Foursquare Labs, Inc.",
            )
            Credit(
                title = "Not a MongoDB product",
                detail = "Not supported by, endorsed by, or affiliated with MongoDB, Inc. An " +
                    "independent demonstration of an embedded build of the MongoDB engine, using " +
                    "no MongoDB logo or branding.",
            )
        }
    }
}

/**
 * The licence texts, in full, from the files they ship in.
 *
 * CDLA-Permissive-2.0 section 2.1 and the Foursquare NOTICE both require the agreement to travel
 * with the data, and naming a licence does not satisfy either. Collapsed by default, because
 * eleven kilobytes of Apache 2.0 is a licence and not a paragraph.
 */
@Composable
fun LicenceTexts(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Text(
            text = "LICENCES SHIPPED WITH THE DATA",
            style = MONO_LABEL,
            color = Console.Faint,
            modifier = Modifier.padding(top = 18.dp, bottom = 4.dp),
        )
        LICENCE_DOCUMENTS.forEach { document -> LicenceText(document.title, document.asset) }
    }
}

@Composable
private fun Credit(title: String, detail: String) {
    Column(Modifier.padding(top = 15.dp)) {
        Text(title, style = MONO_LABEL, color = Console.Mint)
        Text(
            text = detail,
            style = MaterialTheme.typography.bodySmall,
            color = Console.Faint,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun LicenceText(title: String, asset: String) {
    var open by rememberSaveable { mutableStateOf(false) }

    HorizontalDivider(color = Console.Line, modifier = Modifier.padding(vertical = 12.dp))
    Text(
        text = if (open) "$title  ▾" else "$title  ▸",
        style = MaterialTheme.typography.bodyMedium,
        color = Console.Mint,
        modifier = Modifier.fillMaxWidth().clickable { open = !open },
    )
    AnimatedVisibility(open) {
        // Read here rather than above, so opening the screen does not read five files nobody has
        // asked to see.
        val text by assetText(asset)
        Text(
            text = text,
            style = MONO_CODE,
            color = Console.Label,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 9.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Console.Panel)
                .border(1.dp, Console.Line, RoundedCornerShape(12.dp))
                .horizontalScroll(rememberScrollState())
                .padding(12.dp),
        )
    }
}
