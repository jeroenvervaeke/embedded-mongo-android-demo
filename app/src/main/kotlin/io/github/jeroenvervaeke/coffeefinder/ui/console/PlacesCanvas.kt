package io.github.jeroenvervaeke.coffeefinder.ui.console

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.jeroenvervaeke.coffeefinder.data.geo.Camera
import io.github.jeroenvervaeke.coffeefinder.data.geo.CanvasPoint
import io.github.jeroenvervaeke.coffeefinder.data.geo.Projection
import io.github.jeroenvervaeke.coffeefinder.data.model.Coordinates
import io.github.jeroenvervaeke.coffeefinder.data.model.Metres
import io.github.jeroenvervaeke.coffeefinder.data.model.Place
import io.github.jeroenvervaeke.coffeefinder.data.model.PlaceId
import io.github.jeroenvervaeke.coffeefinder.ui.theme.Console

/** What the map is drawn from, and what the tap and pinch on it mean. */
data class MapSurface(
    val camera: Camera,
    val places: List<Place>,
    val origin: Coordinates,
    /** The places the last query returned, drawn as the answer rather than as the ground. */
    val inResults: Set<PlaceId>,
    val radius: Metres,
    /** The row a person tapped, drawn white so the list and the map point at the same place. */
    val selected: PlaceId? = null,
)

/**
 * The map: one dot per coffee place the last `$geoWithin` returned, with the radius the headline
 * counts inside drawn over them.
 *
 * There is no map SDK here, no tiles and no API key. Ireland is recognisable because five
 * thousand coffee places are enough to draw its towns and its coast road: the shape is the data,
 * not a basemap under it.
 *
 * The dots are projected from the *live* camera rather than from the viewport that was queried,
 * so a pan or a pinch moves them with the fingers while the query behind them catches up.
 */
@Composable
fun PlacesCanvas(
    surface: MapSurface,
    onGesture: (eastFraction: Double, northFraction: Double, zoom: Double) -> Unit,
    onAspectRatio: (Double) -> Unit,
    onPick: (Coordinates) -> Unit,
    modifier: Modifier = Modifier,
) {
    var canvas by remember { mutableStateOf(IntSize.Zero) }
    val aspectRatio = if (canvas.height == 0) Camera.PORTRAIT_ASPECT_RATIO
    else canvas.width.toDouble() / canvas.height

    val projection = remember(surface.camera, aspectRatio, canvas) {
        canvas.takeIf { it.width > 0 && it.height > 0 }?.let {
            Projection(surface.camera.viewport(aspectRatio), it.width.toFloat(), it.height.toFloat())
        }
    }

    // Read through a holder rather than captured: keying the pointer input on anything that
    // changes during a gesture would restart the detector mid-drag, cancelling the very drag that
    // caused the change.
    val latest by rememberUpdatedState(GestureTarget(projection, onGesture, onPick))

    val dots = remember(surface.places, surface.inResults, projection) {
        projection?.let { onto ->
            surface.places.partitionedDots(onto, surface.inResults)
        } ?: Dots(emptyList(), emptyList())
    }

    val chosen = remember(surface.selected, surface.places, projection) {
        projection?.let { onto ->
            surface.places.firstOrNull { it.id == surface.selected }
                ?.let { onto.toCanvas(it.coordinates) }
                ?.let { Offset(it.x, it.y) }
        }
    }

    Canvas(
        modifier
            .semantics { contentDescription = MAP_DESCRIPTION }
            .onSizeChanged { size ->
                canvas = size
                // Both dimensions, not just the height: a zero *width* gives an aspect ratio of
                // 0.0, which Camera refuses -- and it would refuse it from inside the layout
                // pass, where there is nothing to catch it. A canvas with no area is a normal
                // thing to be handed for one frame.
                if (size.width > 0 && size.height > 0) {
                    onAspectRatio(size.width.toDouble() / size.height)
                }
            }
            // Pan and pinch. One detector for both, because a two-finger gesture is usually both.
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    latest.dragged(pan.x / size.width, pan.y / size.height, zoom)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { tap -> latest.tapped(tap.x, tap.y) }
            },
    ) {
        val here = projection?.let { onto ->
            onto.toCanvas(surface.origin).let { Offset(it.x, it.y) } to onto
        }
        here?.let { (centre, _) -> drawGlow(centre) }
        drawPoints(dots.ground, PointMode.Points, Console.Marker, 3.dp.toPx(), StrokeCap.Round)
        here?.let { (centre, onto) ->
            drawRadius(centre, onto.pixelsPer(surface.radius, surface.origin))
        }
        drawPoints(dots.found, PointMode.Points, Console.Spring, 5.dp.toPx(), StrokeCap.Round)
        chosen?.let { point ->
            drawCircle(SelectedMarker, radius = 5.dp.toPx(), center = point)
            drawCircle(
                color = SelectedMarker.copy(alpha = 0.3f),
                radius = 10.dp.toPx(),
                center = point,
                style = Stroke(width = 2.dp.toPx()),
            )
        }
        here?.let { (centre, _) -> drawPin(centre) }
    }
}

/** The description a test and a screen reader both find the map by. */
const val MAP_DESCRIPTION = "Map of coffee places"

private class Dots(val ground: List<Offset>, val found: List<Offset>)

/**
 * The dots, split into what the query returned and what is merely there.
 *
 * Two lists rather than one drawn twice: `drawPoints` takes a colour per call, and splitting once
 * per query beats testing set membership per dot per frame.
 */
private fun List<Place>.partitionedDots(onto: Projection, inResults: Set<PlaceId>): Dots {
    val ground = ArrayList<Offset>(size)
    val found = ArrayList<Offset>(inResults.size)
    forEach { place ->
        val point = onto.toCanvas(place.coordinates)
        val dot = Offset(point.x, point.y)
        if (place.id in inResults) found += dot else ground += dot
    }
    return Dots(ground, found)
}

/** How many pixels a distance covers here, measured by projecting it rather than by assuming it. */
private fun Projection.pixelsPer(radius: Metres, origin: Coordinates): Float {
    val here = toCanvas(origin)
    val northwards = Coordinates(
        longitude = origin.longitude,
        latitude = (origin.latitude + radius.value / METRES_PER_DEGREE_LATITUDE).coerceAtMost(85.0),
    )
    return kotlin.math.abs(toCanvas(northwards).y - here.y)
}

/** The ground under the pin, lit enough to say where the query is measured from. */
private fun DrawScope.drawGlow(centre: Offset) {
    val radius = size.minDimension * GLOW_FRACTION
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Console.Forest.copy(alpha = 0.35f), Console.Ink.copy(alpha = 0f)),
            center = centre,
            radius = radius,
        ),
        radius = radius,
        center = centre,
    )
}

/** The radius the headline counts inside: a dashed edge, and a quiet half-way ring inside it. */
private fun DrawScope.drawRadius(centre: Offset, radius: Float) {
    if (radius <= 1f) return
    drawCircle(Console.Spring.copy(alpha = 0.06f), radius, centre)
    drawCircle(
        color = Console.Spring.copy(alpha = 0.22f),
        radius = radius / 2,
        center = centre,
        style = Stroke(width = 1.dp.toPx()),
    )
    drawCircle(
        color = Console.Spring.copy(alpha = 0.55f),
        radius = radius,
        center = centre,
        style = Stroke(
            width = 1.5.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 7.dp.toPx())),
        ),
    )
}

/** Where the queries are measured from: a crosshair, so it reads as an instrument and not a blob. */
private fun DrawScope.drawPin(centre: Offset) {
    val ring = 9.dp.toPx()
    val arm = 15.dp.toPx()
    drawCircle(Console.Evergreen.copy(alpha = 0.85f), ring, centre)
    drawCircle(Console.Spring, ring, centre, style = Stroke(width = 1.5.dp.toPx()))
    drawCircle(Console.Spring, 3.dp.toPx(), centre)
    listOf(
        Offset(-arm, 0f) to Offset(-ring - 1.dp.toPx(), 0f),
        Offset(arm, 0f) to Offset(ring + 1.dp.toPx(), 0f),
        Offset(0f, -arm) to Offset(0f, -ring - 1.dp.toPx()),
        Offset(0f, arm) to Offset(0f, ring + 1.dp.toPx()),
    ).forEach { (from, to) ->
        drawLine(Console.Spring, centre + from, centre + to, strokeWidth = 1.5.dp.toPx())
    }
}

/**
 * What the pointer handlers need, held in one object so they can read the current one without the
 * detector being restarted every time it changes.
 */
private class GestureTarget(
    private val projection: Projection?,
    private val onGesture: (Double, Double, Double) -> Unit,
    private val onPick: (Coordinates) -> Unit,
) {
    /**
     * A drag moves the ground under the finger, so the camera moves the other way along x. Along
     * y the two agree, because dragging down reveals what is north and y grows downwards.
     */
    fun dragged(panFractionX: Float, panFractionY: Float, zoom: Float) {
        onGesture(-panFractionX.toDouble(), panFractionY.toDouble(), zoom.toDouble())
    }

    fun tapped(x: Float, y: Float) {
        projection?.let { onPick(it.toCoordinates(CanvasPoint(x, y))) }
    }
}

private const val METRES_PER_DEGREE_LATITUDE = 110_574.0

/** How much of the shorter side the glow under the pin covers. */
private const val GLOW_FRACTION = 0.62f
