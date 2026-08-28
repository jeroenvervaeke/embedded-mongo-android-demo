package io.github.jeroenvervaeke.coffeefinder.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.jeroenvervaeke.coffeefinder.data.finder.MapFinder
import io.github.jeroenvervaeke.coffeefinder.data.finder.MapState
import io.github.jeroenvervaeke.coffeefinder.data.finder.NearbyFinder
import io.github.jeroenvervaeke.coffeefinder.data.model.Coordinates
import io.github.jeroenvervaeke.coffeefinder.ui.theme.PlaceMarker

/**
 * The map, and what the engine was asked for it.
 *
 * Panning and pinching change the polygon in the `$geoWithin`, so the count under the map is the
 * number of documents the engine returned for exactly the rectangle on screen.
 */
@Composable
fun MapScreen(
    finder: MapFinder,
    nearby: NearbyFinder,
    onPick: (Coordinates) -> Unit,
    modifier: Modifier = Modifier,
) {
    val camera by finder.camera.collectAsStateWithLifecycle()
    val state by finder.state.collectAsStateWithLifecycle()
    val origin by nearby.asked.collectAsStateWithLifecycle()
    val places = (state as? MapState.Ready)?.places.orEmpty()

    Column(modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            PlacesCanvas(
                camera = camera,
                places = places,
                origin = origin.origin,
                onGesture = finder::moveBy,
                onAspectRatio = finder::resizedTo,
                onPick = onPick,
                dotColour = PlaceMarker,
                originColour = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxSize(),
            )
            if (state is MapState.Searching) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
            // Zoomed into a street, there is otherwise no way back out that does not involve a
            // great many pinches.
            TextButton(
                onClick = finder::frameIreland,
                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
            ) {
                Text("Whole island")
            }
        }

        Text(
            text = when (val current = state) {
                MapState.Searching -> "Asking the engine what is in view…"
                is MapState.Failed -> "The query failed: ${current.reason}"
                is MapState.Ready -> "${current.places.size} places in view. " +
                    "Drag and pinch to change the polygon; tap to measure from there."
            },
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(16.dp),
        )
    }
}
