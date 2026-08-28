package io.github.jeroenvervaeke.coffeefinder.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** The four things this application does, which is also its navigation bar. */
private enum class Destination(val label: String, val icon: ImageVector) {
    NEARBY("Near me", Icons.Filled.Search),
    MAP("Map", Icons.Filled.Place),
    PIPELINE("Pipeline", Icons.AutoMirrored.Filled.List),
    ABOUT("About", Icons.Filled.Info),
}

@Composable
fun CoffeeFinderApp(model: FinderViewModel) {
    val startup by model.startup.collectAsStateWithLifecycle()

    when (val state = startup) {
        is Startup.Preparing -> StartupScreen(state.progress)
        is Startup.Failed -> StartupFailureScreen(state.reason)
        is Startup.Ready -> Finder(state, model)
    }
}

@Composable
private fun Finder(ready: Startup.Ready, model: FinderViewModel) {
    var destination by rememberSaveable { mutableStateOf(Destination.NEARBY) }
    val locationSource by model.locationSource.collectAsStateWithLifecycle()

    Scaffold(
        bottomBar = {
            NavigationBar {
                Destination.entries.forEach { entry ->
                    NavigationBarItem(
                        selected = entry == destination,
                        onClick = { destination = entry },
                        icon = { Icon(entry.icon, contentDescription = null) },
                        label = { Text(entry.label) },
                    )
                }
            }
        },
    ) { padding ->
        val content = Modifier.padding(padding)
        when (destination) {
            Destination.NEARBY -> NearbyScreen(ready.nearby, locationSource, model::locate, content)
            Destination.MAP -> MapScreen(ready.map, ready.nearby, model::measureFrom, content)
            Destination.PIPELINE -> PipelineScreen(ready.nearby, ready.map, content)
            Destination.ABOUT -> AboutScreen(content)
        }
    }
}
