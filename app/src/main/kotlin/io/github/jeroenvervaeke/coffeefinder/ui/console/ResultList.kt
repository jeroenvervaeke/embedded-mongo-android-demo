package io.github.jeroenvervaeke.coffeefinder.ui.console

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.jeroenvervaeke.coffeefinder.data.model.NearbyPlace
import io.github.jeroenvervaeke.coffeefinder.data.model.PlaceCategory
import io.github.jeroenvervaeke.coffeefinder.data.model.PlaceId
import io.github.jeroenvervaeke.coffeefinder.ui.theme.Console
import io.github.jeroenvervaeke.coffeefinder.ui.theme.MONO_LABEL

/** Whether a result is read as a row or as the document the engine stored. */
enum class ResultView { HUMAN, BSON }

/** Everything under the peek, which is everything a shut sheet does not show. */
data class ResultListState(
    val places: List<NearbyPlace>,
    val matching: Long,
    val view: ResultView,
    val selected: PlaceId?,
    val search: String,
    val category: PlaceCategory?,
    val failure: String?,
    val searching: Boolean,
)

/**
 * The list the sheet opens into: what to search for, what to keep, and the documents themselves.
 *
 * None of this is on screen while the sheet is shut: it sits below the peek, which is below the
 * bottom edge of the phone. A minimised map is a map, a count and a radius.
 */
@Composable
fun ResultList(
    state: ResultListState,
    onSearch: (String) -> Unit,
    onCategory: (PlaceCategory?) -> Unit,
    onView: (ResultView) -> Unit,
    onSelect: (PlaceId?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        TextField(
            value = state.search,
            onValueChange = onSearch,
            singleLine = true,
            placeholder = { Text("\$text search over name and brand", style = MONO_LABEL) },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Console.Ink.copy(alpha = 0.55f),
                unfocusedContainerColor = Console.Ink.copy(alpha = 0.55f),
                focusedIndicatorColor = Console.Spring.copy(alpha = 0.5f),
                unfocusedIndicatorColor = Console.Line,
            ),
            textStyle = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        )

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            PlaceCategory.entries.forEach { category ->
                Chip(
                    label = "cat: ${category.stored}",
                    selected = category == state.category,
                    // Tapping the selected chip clears it, which is the only way back to everything.
                    onClick = { onCategory(category.takeIf { it != state.category }) },
                )
            }
        }

        HorizontalDivider(color = Console.Line)

        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 11.dp, bottom = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "RESULT SET · ${state.places.size} DOCS",
                style = MONO_LABEL,
                color = Console.Faint,
                modifier = Modifier.weight(1f),
            )
            ViewSwitch(state.view, onView)
        }

        when {
            state.failure != null -> Notice("The engine refused the query:\n${state.failure}")
            state.searching && state.places.isEmpty() -> Notice("Asking the engine…")
            state.places.isEmpty() ->
                Notice("No documents matched.\nWiden the radius, or drop a \$match stage.")
            // Weighted rather than filling: the header rows above it keep their size and the
            // list takes what is left, so a short screen loses list and not the controls.
            else -> LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 28.dp),
            ) {
                items(state.places, key = { it.place.id.value }) { nearby ->
                    val chosen = state.selected == nearby.place.id
                    val select = { onSelect(nearby.place.id.takeIf { !chosen }) }
                    when (state.view) {
                        ResultView.HUMAN -> PlaceRow(nearby, chosen, select)
                        ResultView.BSON -> DocumentRow(nearby, chosen, select)
                    }
                }
            }
        }
    }
}

@Composable
private fun ViewSwitch(view: ResultView, onView: (ResultView) -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(8.dp)).border(1.dp, Console.Edge, RoundedCornerShape(8.dp)),
    ) {
        ResultView.entries.forEach { entry ->
            val selected = entry == view
            Text(
                text = entry.name,
                style = MONO_LABEL,
                color = if (selected) Console.Ink else Console.Faint,
                modifier = Modifier
                    .background(if (selected) Console.Spring else Console.Ink.copy(alpha = 0f))
                    .clickable { onView(entry) }
                    .padding(horizontal = 9.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        style = MONO_LABEL,
        color = if (selected) Console.Spring else Console.Label,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) Console.Spring.copy(alpha = 0.14f) else Console.Ink.copy(alpha = 0.4f))
            .border(
                width = 1.dp,
                color = if (selected) Console.Spring.copy(alpha = 0.5f) else Console.Edge,
                shape = RoundedCornerShape(8.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 6.dp),
    )
}

@Composable
private fun Notice(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = Console.Faint,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 34.dp),
    )
}
