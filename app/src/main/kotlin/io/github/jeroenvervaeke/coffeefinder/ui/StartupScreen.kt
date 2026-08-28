package io.github.jeroenvervaeke.coffeefinder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.jeroenvervaeke.coffeefinder.data.seed.SeedProgress

/** The first few seconds of a cold start, which is the seed going into the engine. */
@Composable
fun StartupScreen(progress: SeedProgress, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Text(
            text = describe(progress),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 24.dp),
        )
        Text(
            text = "Every coffee place on the island of Ireland, going into a MongoDB engine " +
                "running inside this app. No network, no API key.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
fun StartupFailureScreen(reason: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("The database would not open", style = MaterialTheme.typography.titleMedium)
        Text(
            text = reason,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

private fun describe(progress: SeedProgress) = when (progress) {
    SeedProgress.Checking -> "Opening the database…"
    is SeedProgress.Inserting -> "Inserting places… ${progress.inserted}"
    SeedProgress.Indexing -> "Building the 2dsphere and text indexes…"
    is SeedProgress.Ready -> "${progress.places} places ready"
}
