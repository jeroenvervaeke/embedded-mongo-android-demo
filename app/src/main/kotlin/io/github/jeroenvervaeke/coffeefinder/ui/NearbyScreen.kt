package io.github.jeroenvervaeke.coffeefinder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.activity.compose.ReportDrawnWhen
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.jeroenvervaeke.coffeefinder.data.finder.NearbyFinder
import io.github.jeroenvervaeke.coffeefinder.data.finder.NearbyState
import io.github.jeroenvervaeke.coffeefinder.data.model.Metres
import io.github.jeroenvervaeke.coffeefinder.data.model.PlaceCategory
import io.github.jeroenvervaeke.coffeefinder.location.LocationSource

/**
 * The list: a search box, the category filter, and the places nearest wherever the queries are
 * being measured from.
 *
 * An empty box is not an empty query — it is `$geoNear` from here, which is the question the
 * screen exists to answer. Typing swaps it for `$text`, still ordered by distance.
 */
@Composable
fun NearbyScreen(
    finder: NearbyFinder,
    locationSource: LocationSource,
    onLocate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val asked by finder.asked.collectAsStateWithLifecycle()
    val state by finder.state.collectAsStateWithLifecycle()

    // A cold start here is an engine opening, five thousand documents going in and two index
    // builds, and the framework can see none of it. Without a report of its own this application
    // emits no `Fully drawn` at all -- measured, not assumed: the uninstrumented build logged
    // only `Displayed`, which is the spinner. Reported on the first list of results instead,
    // `Fully drawn` in logcat measures the whole of a cold start, dex loading included.
    ReportDrawnWhen { state is NearbyState.Ready }

    Column(modifier.fillMaxSize()) {
        OutlinedTextField(
            value = asked.text,
            onValueChange = finder::searchFor,
            label = { Text("Search by name or brand") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )

        CategoryFilter(asked.category, finder::filterBy)
        DistanceFilter(asked.maxDistance, finder::limitTo)
        Origin(locationSource, onLocate)
        HorizontalDivider()

        when (val current = state) {
            NearbyState.Searching -> Centred { CircularProgressIndicator() }
            is NearbyState.Failed -> Centred { Text("The query failed: ${current.reason}") }
            is NearbyState.Ready -> if (current.places.isEmpty()) {
                Centred { Text("Nothing matched. Try a shorter search, or fewer filters.") }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(current.places, key = { it.place.id.value }) { PlaceRow(it) }
                }
            }
        }
    }
}

@Composable
private fun CategoryFilter(selected: PlaceCategory?, onSelect: (PlaceCategory?) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PlaceCategory.entries.forEach { category ->
            FilterChip(
                selected = category == selected,
                // Tapping the selected chip clears it, which is the only way back to everything.
                onClick = { onSelect(category.takeIf { it != selected }) },
                label = { Text(category.label) },
            )
        }
    }
}

/**
 * How far is too far. `null` is "no cap", which is what `$geoNear` does without a `maxDistance`
 * and is the only sensible default on an island where a lot of it is not near anything.
 */
@Composable
private fun DistanceFilter(selected: Metres?, onSelect: (Metres?) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RANGES.forEach { range ->
            FilterChip(
                selected = range.value == selected?.value,
                onClick = { onSelect(range.takeIf { it.value != selected?.value }) },
                label = { Text("within ${range.describe()}") },
            )
        }
    }
}

@Composable
private fun Origin(source: LocationSource, onLocate: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = when (source) {
                LocationSource.ASKING -> "Finding you…"
                LocationSource.DEVICE -> "Measured from your location"
                LocationSource.FALLBACK -> "No location — measured from Dublin"
                LocationSource.TIMED_OUT ->
                    "Gave up waiting for a location — measured from Dublin. Tap to try again."
                LocationSource.PICKED -> "Measured from the point you tapped on the map"
            },
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onLocate) {
            Icon(Icons.Filled.LocationOn, contentDescription = "Use my location")
        }
    }
}

@Composable
private fun Centred(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        content()
    }
}

/** Walking, cycling and driving distance, roughly. */
private val RANGES = listOf(
    Metres.ofKilometres(1.0),
    Metres.ofKilometres(5.0),
    Metres.ofKilometres(25.0),
)
