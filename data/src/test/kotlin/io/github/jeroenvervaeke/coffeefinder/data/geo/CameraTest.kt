package io.github.jeroenvervaeke.coffeefinder.data.geo

import io.github.jeroenvervaeke.coffeefinder.data.model.Coordinates
import io.github.jeroenvervaeke.coffeefinder.data.model.Ireland
import kotlin.math.cos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CameraTest {
    @Test
    fun `the viewport is as tall as the camera says`() {
        val viewport = camera(latitudeSpan = 4.0).viewport(aspectRatio = 0.5)

        assertEquals(4.0, viewport.heightDegrees, absoluteTolerance = 1e-9)
        assertEquals(53.5, viewport.centre.latitude, absoluteTolerance = 1e-9)
    }

    @Test
    fun `longitude is widened by the cosine of the latitude, so Ireland is not drawn stretched`() {
        val latitude = 53.5
        val viewport = camera(latitudeSpan = 4.0).viewport(aspectRatio = 0.5)

        // Half a degree of longitude here covers cos(53.5 degrees) of the ground half a degree of
        // latitude does, so the box has to be that much wider to hold a square of ground.
        assertEquals(
            4.0 * 0.5 / cos(Math.toRadians(latitude)),
            viewport.widthDegrees,
            absoluteTolerance = 1e-9,
        )
    }

    @Test
    fun `a wider canvas shows more of the world rather than the same box stretched`() {
        val narrow = camera().viewport(aspectRatio = 0.5).widthDegrees
        val wide = camera().viewport(aspectRatio = 1.5).widthDegrees

        assertEquals(3.0, wide / narrow, absoluteTolerance = 1e-9)
    }

    @Test
    fun `the opening camera holds the whole extent the seed was extracted with`() {
        val viewport = Camera.IRELAND.viewport(aspectRatio = 1.0)

        assertTrue(Coordinates(Ireland.EXTENT.west, Ireland.EXTENT.south) in viewport)
        assertTrue(Coordinates(Ireland.EXTENT.east, Ireland.EXTENT.north) in viewport)
    }

    @Test
    fun `framing a wide island on a narrow screen shows more latitude than the island has`() {
        val narrow = Camera.covering(Ireland.EXTENT, aspectRatio = 0.4)

        assertTrue(
            narrow.latitudeSpan > Ireland.EXTENT.heightDegrees,
            "a portrait screen was framed at ${narrow.latitudeSpan} degrees",
        )
        assertTrue(Coordinates(Ireland.EXTENT.west, Ireland.EXTENT.south) in narrow.viewport(0.4))
    }

    @Test
    fun `framing a tall island on a wide screen is limited by its height instead`() {
        val wide = Camera.covering(Ireland.EXTENT, aspectRatio = 3.0)

        assertEquals(Ireland.EXTENT.heightDegrees * 1.06, wide.latitudeSpan, absoluteTolerance = 1e-9)
    }

    @Test
    fun `framing leaves the coast inside the screen rather than flush against it`() {
        val viewport = Camera.covering(Ireland.EXTENT, aspectRatio = 0.55).viewport(0.55)

        assertTrue(viewport.west < Ireland.EXTENT.west, "the west edge landed at ${viewport.west}")
        assertTrue(viewport.east > Ireland.EXTENT.east, "the east edge landed at ${viewport.east}")
    }

    @Test
    fun `zooming in halves what is on screen`() {
        assertEquals(2.0, camera(latitudeSpan = 4.0).zoomedBy(2.0).latitudeSpan, absoluteTolerance = 1e-9)
    }

    @Test
    fun `zooming in past a street stops at one`() {
        assertEquals(Camera.MINIMUM_SPAN, camera().zoomedBy(1e9).latitudeSpan)
    }

    @Test
    fun `zooming out past the island stops at it`() {
        assertEquals(Camera.MAXIMUM_SPAN, camera().zoomedBy(1e-9).latitudeSpan)
    }

    @Test
    fun `a zoom factor that is not a scale is refused`() {
        assertFailsWith<IllegalArgumentException> { camera().zoomedBy(0.0) }
    }

    @Test
    fun `panning north moves by that fraction of what is on screen`() {
        val panned = camera(latitudeSpan = 4.0).panned(0.0, 0.25, aspectRatio = 0.5)

        assertEquals(53.5 + 1.0, panned.centre.latitude, absoluteTolerance = 1e-9)
    }

    @Test
    fun `panning east moves the same distance as panning north, not the same degrees`() {
        val start = camera(latitudeSpan = 4.0)
        val east = start.panned(0.25, 0.0, aspectRatio = 1.0).centre.longitude - start.centre.longitude
        val north = start.panned(0.0, 0.25, aspectRatio = 1.0).centre.latitude - start.centre.latitude

        assertEquals(north / cos(Math.toRadians(53.5)), east, absoluteTolerance = 1e-9)
    }

    @Test
    fun `panning cannot leave the world`() {
        val panned = camera(latitudeSpan = 4.0).panned(0.0, 100.0, aspectRatio = 0.5)

        assertTrue(panned.centre.latitude <= 85.0, "panned to ${panned.centre.latitude}")
    }

    @Test
    fun `a camera showing a span no screen could use is refused`() {
        assertFailsWith<IllegalArgumentException> { camera(latitudeSpan = 90.0) }
    }

    @Test
    fun `a movement too small to redraw is not a movement`() {
        val start = camera(latitudeSpan = 4.0)

        assertTrue(start.showsSameAs(start.panned(0.0, 1e-6, aspectRatio = 0.5)))
    }

    @Test
    fun `a movement of a fraction of the screen is a movement`() {
        val start = camera(latitudeSpan = 4.0)

        assertTrue(!start.showsSameAs(start.panned(0.0, 0.1, aspectRatio = 0.5)))
    }

    @Test
    fun `whether two cameras show the same map does not depend on which is asked`() {
        // distinctUntilChanged takes this as an equivalence relation. Scaling the tolerance by the
        // receiver's own span broke that, and this zoom is inside the band where it showed: the
        // difference in spans falls between the two cameras' own tolerances.
        val wide = camera(latitudeSpan = 4.0)
        val narrow = wide.zoomedBy(1.0010005)

        assertEquals(
            wide.showsSameAs(narrow),
            narrow.showsSameAs(wide),
            "asked one way it was ${wide.showsSameAs(narrow)}, the other ${narrow.showsSameAs(wide)}",
        )
    }

    @Test
    fun `the sideways tolerance is in longitude, which is the narrower degree`() {
        // A shift of 0.005 degrees of longitude at this latitude is less ground than 0.004
        // degrees of latitude, so it is within a tolerance stated as latitude -- which is what
        // the threshold has to be converted into to mean the same distance on screen.
        val start = camera(latitudeSpan = 4.0)
        val nudged = Camera(
            Coordinates(start.centre.longitude + 0.005, start.centre.latitude),
            start.latitudeSpan,
        )

        assertTrue(start.showsSameAs(nudged), "a sideways nudge of 0.005 degrees redrew the map")
    }

    @Test
    fun `a zoom is a movement even when the centre stayed put`() {
        val start = camera(latitudeSpan = 4.0)

        assertTrue(!start.showsSameAs(start.zoomedBy(2.0)))
    }

    private fun camera(latitudeSpan: Double = 4.0) =
        Camera(Coordinates(longitude = -8.0, latitude = 53.5), latitudeSpan)
}
