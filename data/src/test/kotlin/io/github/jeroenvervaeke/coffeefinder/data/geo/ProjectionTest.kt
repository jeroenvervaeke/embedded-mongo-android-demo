package io.github.jeroenvervaeke.coffeefinder.data.geo

import io.github.jeroenvervaeke.coffeefinder.data.model.Coordinates
import io.github.jeroenvervaeke.coffeefinder.data.model.Viewport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ProjectionTest {
    @Test
    fun `the north west corner is the top left pixel`() {
        assertEquals(CanvasPoint(0f, 0f), projection().toCanvas(Coordinates(-10.0, 55.0)))
    }

    @Test
    fun `the south east corner is the bottom right pixel`() {
        assertEquals(CanvasPoint(200f, 400f), projection().toCanvas(Coordinates(-5.0, 51.0)))
    }

    @Test
    fun `north is up, which is the opposite of the way pixels count`() {
        val north = projection().toCanvas(Coordinates(-7.5, 54.0))
        val south = projection().toCanvas(Coordinates(-7.5, 52.0))

        assertEquals(true, north.y < south.y, "north was at ${north.y} and south at ${south.y}")
    }

    @Test
    fun `the centre of the viewport is the centre of the canvas`() {
        assertEquals(CanvasPoint(100f, 200f), projection().toCanvas(Coordinates(-7.5, 53.0)))
    }

    @Test
    fun `a tap comes back as the coordinates it was drawn from`() {
        val place = Coordinates(-6.2603, 53.3498)

        val roundTripped = projection().toCoordinates(projection().toCanvas(place))

        assertEquals(place.longitude, roundTripped.longitude, absoluteTolerance = 1e-4)
        assertEquals(place.latitude, roundTripped.latitude, absoluteTolerance = 1e-4)
    }

    @Test
    fun `a canvas with no area is refused rather than dividing by zero`() {
        assertFailsWith<IllegalArgumentException> { Projection(BOX, width = 0f, height = 400f) }
    }

    private fun projection() = Projection(BOX, width = 200f, height = 400f)
}

private val BOX = Viewport(west = -10.0, south = 51.0, east = -5.0, north = 55.0)
