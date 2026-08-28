package io.github.jeroenvervaeke.coffeefinder

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
            // Asked for either way, and answered either way: `locate` is what moves the screen off
            // "asking", and a refusal has to reach it as surely as a grant does.
            val permission = rememberLauncherForActivityResult(RequestPermission()) { model.locate() }
            // `asked` survives a rotation *and* a process death, so on its own it would leave a
            // restored process that already has the permission never calling `locate` -- the
            // launcher's callback is the only thing that does. Asking the permission itself
            // first closes that: granted means go straight to locating.
            var asked by rememberSaveable { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                when {
                    model.hasLocationPermission() -> model.locate()
                    // Refused once is an answer. The button on the list is how to change it.
                    !asked -> {
                        asked = true
                        permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                    }
                    else -> model.locate()
                }
            }

            CoffeeFinderTheme { CoffeeFinderApp(model) }
        }
    }
}
