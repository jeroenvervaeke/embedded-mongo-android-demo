package io.github.jeroenvervaeke.coffeefinder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.jeroenvervaeke.coffeefinder.data.model.Coordinates
import io.github.jeroenvervaeke.coffeefinder.location.LocationSource
import io.github.jeroenvervaeke.coffeefinder.ui.about.AboutScreen
import io.github.jeroenvervaeke.coffeefinder.ui.console.ConsoleMapScreen
import io.github.jeroenvervaeke.coffeefinder.ui.explorer.ExplorerScreen
import io.github.jeroenvervaeke.coffeefinder.ui.theme.Console
import io.github.jeroenvervaeke.coffeefinder.ui.theme.MONO_LABEL

/** The three things this application does, which is also its navigation bar. */
enum class Destination(val label: String) {
    MAP("MAP"),
    EXPLORER("EXPLORER"),
    ABOUT("ABOUT"),
}

@Composable
fun CoffeeFinderApp(model: FinderViewModel, onRequestLocation: () -> Unit) {
    val startup by model.startup.collectAsStateWithLifecycle()

    when (val state = startup) {
        is Startup.Preparing -> StartupScreen(state.progress)
        is Startup.Failed -> StartupFailureScreen(state.reason, model::retry)
        is Startup.Ready -> {
            val locationSource by model.locationSource.collectAsStateWithLifecycle()
            Finder(state, locationSource, onRequestLocation, model::measureFrom)
        }
    }
}

/**
 * The application once there is a database: three screens and the bar that switches them.
 *
 * Takes what it needs rather than the `ViewModel` that holds it, so the screens can be driven in
 * a test without an Android component and without an engine.
 */
@Composable
internal fun Finder(
    ready: Startup.Ready,
    locationSource: LocationSource,
    onRequestLocation: () -> Unit,
    onPick: (Coordinates) -> Unit,
) {
    var destination by rememberSaveable { mutableStateOf(Destination.MAP) }

    // Not a Scaffold: the map runs under the status bar and behind nothing, and a Scaffold's
    // content padding would push it down to make room for a bar that is drawn over it anyway.
    Column(Modifier.fillMaxSize().background(Console.Ink)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (destination) {
                Destination.MAP -> ConsoleMapScreen(
                    nearby = ready.nearby,
                    map = ready.map,
                    locationSource = locationSource,
                    onLocate = onRequestLocation,
                    onPick = onPick,
                )
                Destination.EXPLORER -> ExplorerScreen(ready.explorer)
                Destination.ABOUT -> AboutScreen(ready.places, ready.startup)
            }
        }
        ConsoleTabs(destination) { destination = it }
    }
}

@Composable
private fun ConsoleTabs(selected: Destination, onSelect: (Destination) -> Unit) {
    Column(Modifier.fillMaxWidth().background(Console.Evergreen)) {
        HorizontalDivider(color = Console.Line)
        Row(Modifier.fillMaxWidth().navigationBarsPadding()) {
            Destination.entries.forEach { destination ->
                Tab(destination, destination == selected, { onSelect(destination) }, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Tab(
    destination: Destination,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colour = if (selected) Console.Spring else Console.Faint
    Column(
        modifier.clickable(onClick = onSelect).height(64.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(width = 30.dp, height = 2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(if (selected) Console.Spring else Console.Ink.copy(alpha = 0f)),
        )
        DestinationIcon(destination, colour, Modifier.padding(top = 7.dp))
        Text(
            text = destination.label,
            style = MONO_LABEL,
            color = colour,
            modifier = Modifier.padding(top = 5.dp),
        )
    }
}
