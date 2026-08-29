package io.github.jeroenvervaeke.coffeefinder.data.query

import io.github.jeroenvervaeke.coffeefinder.data.CORK
import io.github.jeroenvervaeke.coffeefinder.data.DUBLIN
import io.github.jeroenvervaeke.coffeefinder.data.model.Coordinates
import io.github.jeroenvervaeke.coffeefinder.data.placeDocument
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DistanceExpressionTest {
    @Test
    fun `a place at the origin is no distance away`() {
        assertEquals(0.0, distanceTo(DUBLIN, from = DUBLIN), absoluteTolerance = 1e-6)
    }

    @Test
    fun `Dublin to Cork comes out at the distance it is`() {
        // 220.23 km on the sphere MongoDB measures on. The tolerance is tight on purpose: a
        // radius that was wrong by even a percent would move this by 2.2 km.
        assertEquals(220_230.3, distanceTo(CORK, from = DUBLIN), absoluteTolerance = 50.0)
    }

    @Test
    fun `the measurement is symmetric, which a longitude and latitude swap would not be`() {
        assertEquals(distanceTo(CORK, from = DUBLIN), distanceTo(DUBLIN, from = CORK), absoluteTolerance = 1e-6)
    }

    @Test
    fun `a degree of longitude in Ireland is shorter than a degree of latitude`() {
        val east = distanceTo(Coordinates(DUBLIN.longitude + 1, DUBLIN.latitude), from = DUBLIN)
        val north = distanceTo(Coordinates(DUBLIN.longitude, DUBLIN.latitude + 1), from = DUBLIN)

        assertTrue(east < north, "a degree east measured $east m and a degree north $north m")
    }

    @Test
    fun `the same sphere is used as geoNear's, so the two are comparable`() {
        // A quarter turn along the equator is a quarter of the circumference, which pins the
        // radius the expression was built with. Written out rather than expressed through
        // EARTH_RADIUS_METRES: the claim is that the constant is MongoDB's own
        // kRadiusOfEarthInMeters, and a test that names the constant on both sides cannot say so.
        val quarterTurn = distanceTo(Coordinates(90.0, 0.0), from = Coordinates(0.0, 0.0))

        assertEquals(6_378_100.0 * Math.PI / 2, quarterTurn, absoluteTolerance = 1.0)
    }

    private fun distanceTo(place: Coordinates, from: Coordinates): Double {
        val document = placeDocument(longitude = place.longitude, latitude = place.latitude)
        return (evaluate(distanceFromExpression(from), document) as Number).toDouble()
    }
}
