package io.github.jeroenvervaeke.coffeefinder.ui.console

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.unit.dp
import io.github.jeroenvervaeke.coffeefinder.data.model.Coordinates
import io.github.jeroenvervaeke.coffeefinder.data.model.Ireland
import kotlin.math.cos

/** The pin and the ring around it: what the button frames, drawn as what it frames. */
@Composable
fun CrosshairIcon(colour: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(ICON)) {
        val centre = Offset(size.width / 2, size.height / 2)
        val ring = size.minDimension * 0.30f
        val arm = size.minDimension * 0.46f
        val line = 1.4.dp.toPx()
        drawCircle(colour, radius = ring, center = centre, style = Stroke(width = line))
        drawCircle(colour, radius = size.minDimension * 0.07f, center = centre)
        listOf(
            Offset(-arm, 0f) to Offset(-ring - line * 2, 0f),
            Offset(arm, 0f) to Offset(ring + line * 2, 0f),
            Offset(0f, -arm) to Offset(0f, -ring - line * 2),
            Offset(0f, arm) to Offset(0f, ring + line * 2),
        ).forEach { (from, to) -> drawLine(colour, centre + from, centre + to, strokeWidth = line) }
    }
}

/**
 * Ireland, drawn as its own coastline.
 *
 * The outline is a coarse ring of real coordinates (Malin Head, Mizen Head, Erris Head and the
 * rest) projected the same way the map projects a place: longitude narrowed by the cosine of the
 * latitude it is at. So the icon is the shape the map draws, at 20 dp, rather than a squashed
 * approximation of it.
 */
@Composable
fun IrelandIcon(colour: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(ICON)) {
        val path = irelandPath(size)
        drawPath(path, colour.copy(alpha = 0.18f))
        drawPath(
            path = path,
            color = colour,
            style = Stroke(width = 1.3.dp.toPx(), join = StrokeJoin.Round, cap = StrokeCap.Round),
        )
    }
}

/**
 * The coastline as a path filling [area], with the island's proportions kept.
 *
 * Built rather than pasted as SVG so the projection is the one the rest of the application uses:
 * plate carrée with the longitudes narrowed, which is what stops Ireland being 40% too wide.
 */
private fun DrawScope.irelandPath(area: Size): Path {
    val narrowing = cos(Math.toRadians(Ireland.EXTENT.centre.latitude))
    val west = COAST.minOf { it.longitude }
    val east = COAST.maxOf { it.longitude }
    val south = COAST.minOf { it.latitude }
    val north = COAST.maxOf { it.latitude }

    val widthDegrees = ((east - west) * narrowing).toFloat()
    val heightDegrees = (north - south).toFloat()
    val scale = minOf(area.width / widthDegrees, area.height / heightDegrees)
    val marginX = (area.width - widthDegrees * scale) / 2
    val marginY = (area.height - heightDegrees * scale) / 2

    return Path().apply {
        COAST.forEachIndexed { index, point ->
            val x = marginX + ((point.longitude - west) * narrowing).toFloat() * scale
            val y = marginY + (north - point.latitude).toFloat() * scale
            if (index == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
}

/**
 * The coast, clockwise from Malin Head.
 *
 * Forty points rather than a dozen: fewer and the west coast reads as teeth, smoothed and it
 * reads as a bean. This is the shape at about 15 km of detail, which is what a 22 dp icon can
 * hold: Donegal and Cork are in it, individual headlands are not.
 */
private val COAST = listOf(
    Coordinates(-7.37, 55.38), // Malin Head
    Coordinates(-6.95, 55.20),
    Coordinates(-6.50, 55.22), // Portrush
    Coordinates(-6.20, 55.20), // Fair Head
    Coordinates(-5.90, 54.95),
    Coordinates(-5.70, 54.72), // Larne
    Coordinates(-5.50, 54.60),
    Coordinates(-5.45, 54.40), // Ards
    Coordinates(-5.60, 54.25), // Strangford
    Coordinates(-6.05, 54.05), // Carlingford
    Coordinates(-6.15, 53.72),
    Coordinates(-6.05, 53.35), // Dublin
    Coordinates(-6.05, 53.00), // Wicklow
    Coordinates(-6.20, 52.70),
    Coordinates(-6.35, 52.35),
    Coordinates(-6.55, 52.18), // Carnsore Point
    Coordinates(-7.05, 52.13), // Hook Head
    Coordinates(-7.60, 51.95), // Dungarvan
    Coordinates(-8.30, 51.80), // Cork Harbour
    Coordinates(-8.90, 51.58), // Galley Head
    Coordinates(-9.55, 51.48), // Baltimore
    Coordinates(-9.90, 51.55), // Mizen Head
    Coordinates(-9.80, 51.75), // Bantry Bay
    Coordinates(-10.20, 51.85), // Dursey
    Coordinates(-9.95, 52.05), // Kenmare
    Coordinates(-10.45, 52.13), // Dingle
    Coordinates(-9.85, 52.35), // Tralee Bay
    Coordinates(-9.93, 52.56), // Loop Head
    Coordinates(-9.45, 52.70), // Shannon estuary
    Coordinates(-9.35, 53.05),
    Coordinates(-9.05, 53.25), // Galway
    Coordinates(-9.85, 53.40), // Connemara
    Coordinates(-10.15, 53.55), // Slyne Head
    Coordinates(-9.75, 53.75), // Clew Bay
    Coordinates(-10.10, 53.97), // Achill
    Coordinates(-9.85, 54.25), // Erris Head
    Coordinates(-9.15, 54.30), // Killala
    Coordinates(-8.55, 54.30), // Sligo
    Coordinates(-8.85, 54.63), // Donegal Bay
    Coordinates(-9.05, 54.68), // Slieve League
    Coordinates(-8.45, 54.95),
    Coordinates(-8.30, 55.15), // Bloody Foreland
)

/** Big enough to read at a glance, small enough to sit over the map rather than on it. */
private val ICON = 22.dp
