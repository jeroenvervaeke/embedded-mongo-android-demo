package io.github.jeroenvervaeke.coffeefinder.ui.console

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.jeroenvervaeke.coffeefinder.data.finder.MapFinder
import io.github.jeroenvervaeke.coffeefinder.data.model.Coordinates
import io.github.jeroenvervaeke.coffeefinder.data.model.Metres
import io.github.jeroenvervaeke.coffeefinder.location.LocationSource
import io.github.jeroenvervaeke.coffeefinder.ui.theme.Console
import io.github.jeroenvervaeke.coffeefinder.ui.theme.MONO_LABEL

/**
 * Frames the map on the radius the count is counting inside: once when the screen opens, and
 * again when the device finally says where it is.
 *
 * Not on every change of origin: a tap on the map is a change of origin, and a map that jumped
 * back to the middle every time somebody dropped a pin would be unusable. Nor after a pan or a
 * pinch: [MapFinder.frameOnUnlessMoved] leaves a camera somebody has taken over alone, which is
 * also what stops a trip to another tab and back from re-framing it.
 */
@Composable
fun FrameOnFirstFix(map: MapFinder, origin: Coordinates, radius: Metres, source: LocationSource) {
    LaunchedEffect(source == LocationSource.DEVICE) { map.frameOnUnlessMoved(origin, radius) }
}

/** Where the queries are measured from, and the way to ask the device again. */
@Composable
fun OriginBadge(source: LocationSource, onLocate: () -> Unit, modifier: Modifier = Modifier) {
    val (colour, text) = when (source) {
        LocationSource.ASKING -> Console.Faint to "finding you…"
        LocationSource.DEVICE -> Console.Spring to "measured from your location"
        LocationSource.FALLBACK -> Console.Amber to "no location · measuring from Dublin"
        LocationSource.TIMED_OUT -> Console.Amber to "location timed out · tap to try again"
        LocationSource.PICKED -> Console.Blue to "measured from the pin you dropped"
    }

    Row(
        modifier
            .clip(RoundedCornerShape(99.dp))
            .background(Console.Panel.copy(alpha = 0.9f))
            .border(1.dp, Console.Line, RoundedCornerShape(99.dp))
            .clickable(onClick = onLocate)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(5.dp).clip(CircleShape).background(colour))
        Text(text, style = MONO_LABEL, color = Console.Label, modifier = Modifier.padding(start = 7.dp))
    }
}

/**
 * The two ways back to a view worth having: the radius, and the island.
 *
 * Both matter because both are reachable by hand and neither is easy to get back to: pinching
 * out from a street to the whole of Ireland is a dozen gestures.
 */
@Composable
fun MapActions(
    onFrameRadius: () -> Unit,
    onFrameIreland: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(9.dp)) {
        MapAction(FRAME_RADIUS, onFrameRadius) { colour -> CrosshairIcon(colour) }
        MapAction(FRAME_ISLAND, onFrameIreland) { colour -> IrelandIcon(colour) }
    }
}

/** What a test and a screen reader call the two buttons over the map. */
const val FRAME_RADIUS = "Frame the radius"

const val FRAME_ISLAND = "Frame the island"

@Composable
private fun MapAction(
    description: String,
    onClick: () -> Unit,
    icon: @Composable (Color) -> Unit,
) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Console.Panel.copy(alpha = 0.92f))
            .border(1.dp, Console.Line, CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        icon(Console.Mint)
    }
}

/** Kept out of the palette because it is only ever the ring around a chosen result. */
internal val SelectedMarker = Color.White
