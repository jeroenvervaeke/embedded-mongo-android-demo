package io.github.jeroenvervaeke.coffeefinder.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.jeroenvervaeke.coffeefinder.data.finder.MapFinder
import io.github.jeroenvervaeke.coffeefinder.data.finder.MapState
import io.github.jeroenvervaeke.coffeefinder.data.finder.NearbyFinder
import io.github.jeroenvervaeke.coffeefinder.data.finder.NearbyState
import org.bson.Document

/**
 * The two commands behind the two screens, as they were sent.
 *
 * This is the screen that settles the question the rest of the application only implies: what is
 * running is MongoDB, not a hand-rolled index with a MongoDB-shaped name on it. Both documents
 * come out of the state that produced the results, so neither can be a plausible reconstruction.
 */
@Composable
fun PipelineScreen(nearby: NearbyFinder, map: MapFinder, modifier: Modifier = Modifier) {
    val list by nearby.state.collectAsStateWithLifecycle()
    val viewport by map.state.collectAsStateWithLifecycle()

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text(
            text = "These are the commands the engine ran for what the other two screens are " +
                "showing, straight from the reply that produced them.",
            style = MaterialTheme.typography.bodyMedium,
        )

        Pipeline("Near me", (list as? NearbyState.Ready)?.command, (list as? NearbyState.Ready)?.places?.size)
        Pipeline("Map", (viewport as? MapState.Ready)?.command, (viewport as? MapState.Ready)?.places?.size)
    }
}

@Composable
private fun Pipeline(title: String, command: Document?, documents: Int?) {
    Text(
        text = if (documents == null) title else "$title — $documents documents",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
    )
    Card(Modifier.fillMaxWidth()) {
        Text(
            text = command?.pretty() ?: "No query has finished yet.",
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            // A pipeline is nested deeply enough that wrapping it would cost more than scrolling.
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(12.dp),
        )
    }
}
