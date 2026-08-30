package io.github.jeroenvervaeke.coffeefinder.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp

/**
 * The three tab icons, drawn rather than shipped.
 *
 * Line art at one weight, in the same voice as MongoDB's own icon set — and drawn here because
 * the alternative is a vector-drawable resource per icon for three shapes that are a dozen points
 * each.
 *
 * Every path is written in a 24-unit box and the canvas is scaled to it, so the coordinates below
 * read as the sketch they came from.
 */
@Composable
fun DestinationIcon(destination: Destination, colour: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(ICON)) {
        when (destination) {
            Destination.MAP -> drawMapIcon(colour)
            Destination.EXPLORER -> drawExplorerIcon(colour)
            Destination.ABOUT -> drawAboutIcon(colour)
        }
    }
}

/** A folded map: three panels, two of them creased. */
private fun DrawScope.drawMapIcon(colour: Color) {
    outline(colour) {
        moveTo(9f, 3f)
        lineTo(3f, 5.5f)
        lineTo(3f, 20.5f)
        lineTo(9f, 18f)
        lineTo(15f, 21f)
        lineTo(21f, 18.5f)
        lineTo(21f, 3.5f)
        lineTo(15f, 6f)
        close()
    }
    outline(colour.copy(alpha = CREASE)) {
        moveTo(9f, 3f)
        lineTo(9f, 18f)
        moveTo(15f, 6f)
        lineTo(15f, 21f)
    }
}

/** A console: a window, a prompt, and a line typed at it. */
private fun DrawScope.drawExplorerIcon(colour: Color) {
    outline(colour) {
        addRoundRect(RoundRect(left = 2.5f, top = 4f, right = 21.5f, bottom = 20f, radiusX = 2.5f, radiusY = 2.5f))
        moveTo(6.5f, 10f)
        lineTo(9f, 12.2f)
        lineTo(6.5f, 14.4f)
        moveTo(11.5f, 15f)
        lineTo(16.5f, 15f)
    }
}

/** A leaf, which is what MongoDB's own mark is made of. */
private fun DrawScope.drawAboutIcon(colour: Color) {
    outline(colour) {
        moveTo(12f, 3.5f)
        cubicTo(16f, 9f, 17.5f, 12f, 17.5f, 15f)
        cubicTo(17.5f, 18.6f, 15.1f, 20.6f, 12f, 21.5f)
        cubicTo(8.9f, 20.6f, 6.5f, 18.6f, 6.5f, 15f)
        cubicTo(6.5f, 12f, 8f, 9f, 12f, 3.5f)
        close()
    }
    outline(colour.copy(alpha = CREASE)) {
        moveTo(12f, 21f)
        lineTo(12f, 10f)
    }
}

/**
 * Strokes a path written in the 24-unit box.
 *
 * The canvas is scaled rather than each coordinate, so the stroke is divided by the same factor —
 * otherwise a 21 dp icon and a 40 dp one would be drawn at different weights.
 */
private fun DrawScope.outline(colour: Color, build: Path.() -> Unit) {
    val unit = size.minDimension / BOX
    scale(unit, unit, Offset.Zero) {
        drawPath(
            path = Path().apply(build),
            color = colour,
            style = Stroke(
                width = STROKE.toPx() / unit,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
}

/** The box the coordinates above are written in, which is what an SVG icon set would use. */
private const val BOX = 24f

/** The fold lines and the leaf's vein sit behind the outline rather than beside it. */
private const val CREASE = 0.55f

private val STROKE = 1.4.dp

/** Big enough to read in a tab bar without crowding the label under it. */
private val ICON = 21.dp
