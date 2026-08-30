package io.github.jeroenvervaeke.coffeefinder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.jeroenvervaeke.coffeefinder.data.seed.SeedProgress
import io.github.jeroenvervaeke.coffeefinder.ui.theme.Console
import io.github.jeroenvervaeke.coffeefinder.ui.theme.MONO_LABEL

/** The first few seconds of a cold start, which is the seed going into the engine. */
@Composable
fun StartupScreen(progress: SeedProgress, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().background(Console.Ink).padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "coffee.places",
            style = MONO_LABEL,
            color = Console.Spring,
            modifier = Modifier
                .clip(RoundedCornerShape(99.dp))
                .border(1.dp, Console.Line, RoundedCornerShape(99.dp))
                .padding(horizontal = 12.dp, vertical = 7.dp),
        )
        Text(
            text = describe(progress),
            style = MaterialTheme.typography.titleLarge,
            color = Console.Mist,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 22.dp),
        )
        LinearProgressIndicator(
            color = Console.Spring,
            trackColor = Console.Edge,
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
        )
        Text(
            text = "Every coffee place on the island of Ireland, going into a MongoDB engine " +
                "running inside this app. No network, no API key.",
            style = MaterialTheme.typography.bodyMedium,
            color = Console.Label,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

@Composable
fun StartupFailureScreen(reason: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().background(Console.Ink).padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "The database would not open",
            style = MaterialTheme.typography.titleLarge,
            color = Console.Mist,
            textAlign = TextAlign.Center,
        )
        Text(
            text = reason,
            style = MONO_LABEL,
            color = Console.Red,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 14.dp),
        )
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(
                containerColor = Console.Spring,
                contentColor = Console.Ink,
            ),
            modifier = Modifier.padding(top = 22.dp),
        ) {
            Text("Try again", style = MONO_LABEL)
        }
    }
}

private fun describe(progress: SeedProgress): String = when (progress) {
    SeedProgress.Checking -> "Opening the engine…"
    is SeedProgress.Inserting -> "Storing ${"%,d".format(progress.inserted)} coffee places…"
    SeedProgress.Indexing -> "Building the 2dsphere and text indexes…"
    is SeedProgress.Ready -> "${"%,d".format(progress.places)} coffee places, ready."
}
