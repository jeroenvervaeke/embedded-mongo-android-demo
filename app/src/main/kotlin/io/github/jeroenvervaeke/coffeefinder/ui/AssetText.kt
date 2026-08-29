package io.github.jeroenvervaeke.coffeefinder.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A text file shipped in `assets`, read once and off the main thread.
 *
 * The licence texts are up to eleven kilobytes; reading them during composition would be a file
 * read on the frame that shows them.
 */
@Composable
fun assetText(path: String): State<String> {
    val assets = LocalContext.current.assets
    return produceState(initialValue = "", path, assets) {
        value = withContext(Dispatchers.IO) {
            assets.open(path).bufferedReader().use { it.readText() }
        }
    }
}
