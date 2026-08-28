package io.github.jeroenvervaeke.coffeefinder.data.geo

import io.github.jeroenvervaeke.coffeefinder.data.model.Coordinates
import io.github.jeroenvervaeke.coffeefinder.data.model.Viewport

/** A position on the canvas, in pixels from its top left corner. */
data class CanvasPoint(val x: Float, val y: Float)

/**
 * Turns coordinates into canvas pixels for one [viewport] on one canvas.
 *
 * Plate carrée, and deliberately so: the aspect correction that would otherwise be this
 * projection's job has already happened in [Camera.viewport], which sized the box to the canvas
 * rather than the other way round. What is left is a linear map, which is also what makes the
 * inverse exact — and the inverse is what turns a tap into a place.
 *
 * Latitude is flipped because north is up on a map and down the screen is where y grows.
 */
class Projection(private val viewport: Viewport, private val width: Float, private val height: Float) {
    init {
        require(width > 0 && height > 0) { "a ${width}x$height canvas has nothing to draw on" }
    }

    fun toCanvas(coordinates: Coordinates) = CanvasPoint(
        x = (((coordinates.longitude - viewport.west) / viewport.widthDegrees) * width).toFloat(),
        y = (((viewport.north - coordinates.latitude) / viewport.heightDegrees) * height).toFloat(),
    )

    fun toCoordinates(point: CanvasPoint) = Coordinates(
        longitude = viewport.west + (point.x / width) * viewport.widthDegrees,
        latitude = viewport.north - (point.y / height) * viewport.heightDegrees,
    )
}
