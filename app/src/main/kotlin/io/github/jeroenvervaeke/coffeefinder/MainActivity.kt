package io.github.jeroenvervaeke.coffeefinder

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.jeroenvervaeke.coffeefinder.ui.CoffeeFinderApp
import io.github.jeroenvervaeke.coffeefinder.ui.FinderViewModel
import io.github.jeroenvervaeke.coffeefinder.ui.theme.CoffeeFinderTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val model: FinderViewModel = viewModel()
            CoffeeFinderTheme {
                CoffeeFinderApp(model, onRequestLocation = rememberLocationRequest(model))
            }
        }
    }

    /**
     * What the "use my location" button does, and what runs once on a cold start.
     *
     * The four states of an Android permission, in the only order that behaves: already granted,
     * never asked, refused once, refused for good. The last is the one that needs the system
     * settings screen: `shouldShowRequestPermissionRationale` reads false both before the first
     * ask and after a permanent refusal, so it takes [asked] to tell those apart.
     */
    @Composable
    private fun rememberLocationRequest(model: FinderViewModel): () -> Unit {
        // Asked for either way, and answered either way: `locate` is what moves the screen off
        // "asking", and a refusal has to reach it as surely as a grant does.
        val permission = rememberLauncherForActivityResult(RequestPermission()) { model.locate() }
        var asked by rememberSaveable { mutableStateOf(false) }

        val ask: (Boolean) -> Unit = remember(model) {
            { mayOpenSettings ->
                when {
                    model.hasLocationPermission() -> model.locate()
                    !asked || shouldShowRequestPermissionRationale(LOCATION) -> {
                        asked = true
                        permission.launch(LOCATION)
                    }
                    // Refused for good: the system will not show the dialog again, so the only
                    // way back is the settings screen. Without this the button does nothing at
                    // all, for the life of the install.
                    mayOpenSettings -> openApplicationSettings()
                    // The same state, reached by nobody asking. Opening system settings the user
                    // did not ask to see is a strange way to start an application, and leaving it
                    // at that is worse: the screen would sit on "Finding you…" with nothing
                    // looking. Say what is true instead: there is no permission, so it is Dublin.
                    else -> model.locate()
                }
            }
        }
        val request: () -> Unit = remember(ask) { { ask(true) } }

        // Runs once on a cold start, and has to: `asked` survives a process death and the
        // ViewModel does not, so a restored process reaches `locate` no other way, whether it
        // already holds the permission or will never hold it.
        LaunchedEffect(Unit) { ask(false) }
        return request
    }

    private fun openApplicationSettings() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", packageName, null)),
        )
    }
}

private const val LOCATION = Manifest.permission.ACCESS_COARSE_LOCATION
