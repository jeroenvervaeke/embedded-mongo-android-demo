package io.github.jeroenvervaeke.coffeefinder.ui.explorer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import io.github.jeroenvervaeke.coffeefinder.data.explorer.ExplorerResult
import io.github.jeroenvervaeke.coffeefinder.data.explorer.PIPELINE_PRESETS
import io.github.jeroenvervaeke.coffeefinder.data.explorer.PipelineExplorer
import io.github.jeroenvervaeke.coffeefinder.data.explorer.STAGE_SNIPPETS
import io.github.jeroenvervaeke.coffeefinder.ui.theme.Console
import io.github.jeroenvervaeke.coffeefinder.ui.theme.MONO_LABEL
import kotlinx.coroutines.launch

/** Whether the explorer is showing what came back or what the engine says it did. */
enum class ExplorerPane { RESULTS, EXPLAIN }

/**
 * Somewhere to type a pipeline and run it against the collection behind the map.
 *
 * Not a sandbox and not a simulation: the text goes to the same engine, in the same process, as
 * every other query in this application. An unsupported stage comes back as MongoDB's own
 * refusal, and `EXPLAIN` is the engine's plan rather than a guess at one.
 */
@Composable
fun ExplorerScreen(explorer: PipelineExplorer, modifier: Modifier = Modifier) {
    var text by rememberSaveable { mutableStateOf(PIPELINE_PRESETS.first().text) }
    var preset by rememberSaveable { mutableStateOf<String?>(PIPELINE_PRESETS.first().name) }
    var pane by rememberSaveable { mutableStateOf(ExplorerPane.RESULTS) }
    // Not saved across a process death: a result is a query away, and the documents behind one
    // are not something to carry through a bundle.
    var result by remember { mutableStateOf<ExplorerResult?>(null) }
    val scope = rememberCoroutineScope()

    val run: (ExplorerPane) -> Unit = { which ->
        pane = which
        scope.launch {
            result = when (which) {
                ExplorerPane.RESULTS -> explorer.run(text)
                ExplorerPane.EXPLAIN -> explorer.explain(text)
            }
        }
    }

    // The screen opens on a preset, so it opens on that preset's results rather than on nothing.
    LaunchedEffect(Unit) { if (result == null) result = explorer.run(text) }

    Column(
        modifier
            .fillMaxSize()
            .background(Console.Ink)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
    ) {
        Text("Explorer", style = MaterialTheme.typography.headlineMedium, color = Console.Mist)
        Text(
            text = "db.places.aggregate( pipeline )",
            style = MONO_LABEL,
            color = Console.Faint,
            modifier = Modifier.padding(top = 6.dp, bottom = 4.dp),
        )

        Heading("PRESETS")
        SnippetRow(
            snippets = PIPELINE_PRESETS,
            selected = preset,
            onPick = { chosen ->
                text = chosen.text
                preset = chosen.name
            },
        )

        Heading("INSERT STAGE")
        SnippetRow(
            snippets = STAGE_SNIPPETS,
            selected = null,
            onPick = { snippet ->
                text = text.withStageAppended(snippet.text)
                preset = null
            },
        )

        Heading("PIPELINE")
        PipelineEditor(text, onText = { text = it; preset = null })

        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Text(
                text = "▶ RUN",
                style = MONO_LABEL,
                color = Console.Ink,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Console.Spring)
                    .clickable { run(ExplorerPane.RESULTS) }
                    .padding(horizontal = 15.dp, vertical = 11.dp),
            )
            ResultMessage(result, Modifier.weight(1f))
        }

        Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ExplorerPane.entries.forEach { entry ->
                PaneTab(entry, entry == pane) { run(entry) }
            }
        }

        ExplorerOutput(result, pane, Modifier.padding(top = 10.dp, bottom = 24.dp))
    }
}

@Composable
private fun Heading(text: String) {
    Text(
        text = text,
        style = MONO_LABEL,
        color = Console.Faint,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun PaneTab(pane: ExplorerPane, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = pane.name,
        style = MONO_LABEL,
        color = if (selected) Console.Spring else Console.Label,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) Console.Spring.copy(alpha = 0.12f) else Console.Panel)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}
