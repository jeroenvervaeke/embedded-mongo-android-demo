package io.github.jeroenvervaeke.coffeefinder.ui

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.jeroenvervaeke.coffeefinder.data.geo.Camera
import io.github.jeroenvervaeke.coffeefinder.data.geo.CanvasPoint
import io.github.jeroenvervaeke.coffeefinder.data.geo.Projection
import io.github.jeroenvervaeke.coffeefinder.data.model.Coordinates
import io.github.jeroenvervaeke.coffeefinder.data.model.Place

/**
 * The map: one dot per coffee place the last `$geoWithin` returned.
 *
 * There is no map SDK here, no tiles and no API key. Ireland is recognisable because five
 * thousand coffee places are enough to draw its towns and its coast road — the shape is the data,
 * not a basemap under it.
 *
 * The dots are projected from the *live* camera rather than from the viewport that was queried,
 * so a pan moves them with the finger while the query behind it catches up.
 */
@Composable
fun PlacesCanvas(
    camera: Camera,
    places: List<Place>,
    origin: Coordinates,
    onGesture: (eastFraction: Double, northFraction: Double, zoom: Double) -> Unit,
    onAspectRatio: (Double) -> Unit,
    onPick: (Coordinates) -> Unit,
    dotColour: Color,
    originColour: Color,
    modifier: Modifier = Modifier,
) {
    var canvas by remember { mutableStateOf(IntSize.Zero) }
    val aspectRatio = if (canvas.height == 0) Camera.PORTRAIT_ASPECT_RATIO
    else canvas.width.toDouble() / canvas.height

    val projection = remember(camera, aspectRatio, canvas) {
        canvas.takeIf { it.width > 0 && it.height > 0 }?.let {
            Projection(camera.viewport(aspectRatio), it.width.toFloat(), it.height.toFloat())
        }
    }

    // Read through a holder rather than captured: keying the pointer input on anything that
    // changes during a gesture would restart the detector mid-drag, cancelling the very drag that
    // caused the change.
    val latest by rememberUpdatedState(GestureTarget(projection, onGesture, onPick))

    val dots = remember(places, projection) {
        projection?.let { onto ->
            places.map { place ->
                val point = onto.toCanvas(place.coordinates)
                Offset(point.x, point.y)
            }
        } ?: emptyList()
    }

    Canvas(
        modifier
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
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    latest.dragged(pan.x / size.width, pan.y / size.height, zoom)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { tap -> latest.tapped(tap.x, tap.y) }
            },
    ) {
        if (dots.isNotEmpty()) {
            drawPoints(dots, PointMode.Points, dotColour, strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
        }
        projection?.let {
            val here = it.toCanvas(origin)
            drawCircle(originColour, radius = 7.dp.toPx(), center = Offset(here.x, here.y))
            drawCircle(
                color = dotColour,
                radius = 7.dp.toPx(),
                center = Offset(here.x, here.y),
                style = Stroke(width = 2.dp.toPx()),
            )
        }
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
