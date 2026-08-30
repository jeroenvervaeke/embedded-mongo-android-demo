package io.github.jeroenvervaeke.coffeefinder.ui.console

import androidx.activity.compose.ReportDrawnWhen
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.jeroenvervaeke.coffeefinder.data.finder.MapFinder
import io.github.jeroenvervaeke.coffeefinder.data.finder.MapState
import io.github.jeroenvervaeke.coffeefinder.data.finder.NearbyFinder
import io.github.jeroenvervaeke.coffeefinder.data.finder.NearbyState
import io.github.jeroenvervaeke.coffeefinder.data.model.Confidence
import io.github.jeroenvervaeke.coffeefinder.data.model.Coordinates
import io.github.jeroenvervaeke.coffeefinder.data.model.PlaceId
import io.github.jeroenvervaeke.coffeefinder.location.LocationSource
import io.github.jeroenvervaeke.coffeefinder.ui.theme.Console

/**
 * The map, the count over it, and the documents behind them.
 *
 * One screen rather than the two it used to be. The map is the whole surface; the console at the
 * top says which pipeline drew it and what that cost; the sheet at the bottom carries the count,
 * and opens into the result set when it is dragged up.
 *
 * Pan and pinch move the camera, which changes the `$geoWithin` behind the dots. A tap drops the
 * pin, which changes the `$geoNear` behind the count.
 */
@Composable
fun ConsoleMapScreen(
    nearby: NearbyFinder,
    map: MapFinder,
    locationSource: LocationSource,
    onLocate: () -> Unit,
    onPick: (Coordinates) -> Unit,
    modifier: Modifier = Modifier,
) {
    val asked by nearby.asked.collectAsStateWithLifecycle()
    val results by nearby.state.collectAsStateWithLifecycle()
    val camera by map.camera.collectAsStateWithLifecycle()
    val inView by map.state.collectAsStateWithLifecycle()

    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    var consoleOpen by rememberSaveable { mutableStateOf(false) }
    var view by rememberSaveable { mutableStateOf(ResultView.HUMAN) }
    var selected by remember { mutableStateOf<PlaceId?>(null) }

    val ready = results as? NearbyState.Ready

    // A cold start here is an engine opening, five thousand documents going in and two index
    // builds, and the framework can see none of it. Reported on the first result set instead, so
    // `Fully drawn` in logcat measures the whole of a cold start, dex loading included.
    ReportDrawnWhen { results is NearbyState.Ready }

    // The map opens framed on the radius the count is counting inside, rather than on the island:
    // a 1 km ring drawn across the whole of Ireland is three pixels of nothing.
    FrameOnFirstFix(map, asked.origin, asked.radius, locationSource)

    Box(modifier.fillMaxSize().background(Console.Ink)) {
        PlacesCanvas(
            surface = MapSurface(
                camera = camera,
                places = (inView as? MapState.Ready)?.places.orEmpty(),
                origin = asked.origin,
                inResults = remember(ready) { ready?.places?.map { it.place.id }?.toSet().orEmpty() },
                radius = asked.radius,
                selected = selected,
            ),
            onGesture = map::moveBy,
            onAspectRatio = map::resizedTo,
            onPick = { coordinates ->
                selected = null
                onPick(coordinates)
                // The pin is under the sheet if the list is up, and the point of dropping one is
                // to look at where it landed.
                sheetOpen = false
            },
            modifier = Modifier.fillMaxSize(),
        )

        Column(Modifier.statusBarsPadding().padding(horizontal = 10.dp, vertical = 8.dp)) {
            ConsoleHud(
                readout = ConsoleReadout(
                    command = ready?.command,
                    took = ready?.took,
                    documents = ready?.places?.size ?: 0,
                    stages = stageToggles(asked, nearby),
                ),
                expanded = consoleOpen,
                onExpandedChange = { open ->
                    consoleOpen = open
                    // Both open at once leaves the map a strip in the middle.
                    if (open) sheetOpen = false
                },
            )
            OriginBadge(locationSource, onLocate, Modifier.padding(top = 8.dp))
        }

        MapActions(
            onFrameRadius = { map.frameOn(asked.origin, asked.radius) },
            onFrameIreland = map::frameIreland,
            // Bottom right, over the map and clear of the sheet: a thumb reaches it, and it is
            // out of the way of everything the screen is actually saying.
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 12.dp, bottom = MAP_ACTIONS_ABOVE_SHEET),
        )

        ResultSheet(
            expanded = sheetOpen,
            onExpandedChange = { sheetOpen = it },
            peek = {
                CountPeek(
                    matching = ready?.matching ?: 0,
                    listed = ready?.places?.size ?: 0,
                    radius = asked.radius,
                    onRadius = nearby::within,
                    searching = ready == null,
                )
            },
            content = {
                ResultList(
                    state = ResultListState(
                        places = ready?.places.orEmpty(),
                        matching = ready?.matching ?: 0,
                        view = view,
                        selected = selected,
                        search = asked.text,
                        category = asked.criteria.category,
                        failure = (results as? NearbyState.Failed)?.reason,
                        searching = ready == null,
                    ),
                    onSearch = nearby::searchFor,
                    onCategory = nearby::filterBy,
                    onView = { view = it },
                    onSelect = { selected = it },
                )
            },
        )
    }
}

/**
 * The stages of the map's pipeline a person can switch, and what switching one does.
 *
 * `$geoNear` is not among them: it is the stage that reads the index, so a pipeline without it is
 * a different question rather than the same one with a filter off.
 */
private fun stageToggles(asked: NearbyFinder.Request, nearby: NearbyFinder): List<StageToggle> =
    listOf(
        StageToggle(
            label = "\$match confidence ≥ .9",
            on = asked.criteria.minimumConfidence != null,
            onChange = { on -> nearby.requireConfidence(Confidence(HIGH_CONFIDENCE).takeIf { on }) },
        ),
        StageToggle(
            label = "\$match brand \$exists",
            on = asked.criteria.brandedOnly,
            onChange = nearby::requireBrand,
        ),
        StageToggle(
            label = "\$limit 50",
            on = asked.capped,
            onChange = nearby::capResults,
        ),
    )

/** Where Overture stops guessing: the top of its confidence range, and most of the collection. */
private const val HIGH_CONFIDENCE = 0.9

/**
 * How far the map's own buttons sit above the bottom of the screen.
 *
 * Above the shut sheet rather than behind it: the sheet's peek is about a fifth of a phone
 * screen, and a button under it is a button nobody can press.
 */
private val MAP_ACTIONS_ABOVE_SHEET = 250.dp
