package io.github.jeroenvervaeke.coffeefinder.data.model

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MetresTest {
    @Test
    fun `a walk is described in metres`() {
        assertEquals("450 m", Metres(450.4).describe(Locale.ROOT))
    }

    @Test
    fun `the decimal separator is the reader's, because this text goes on screen`() {
        assertEquals("1.2 km", Metres(1_200.0).describe(Locale.UK))
        assertEquals("1,2 km", Metres(1_200.0).describe(Locale.FRANCE))
    }

    @Test
    fun `a kilometre and beyond is described in kilometres`() {
        assertEquals("1.0 km", Metres(1000.0).describe(Locale.ROOT))
        assertEquals("12.3 km", Metres(12_345.0).describe(Locale.ROOT))
    }

    @Test
    fun `a distance just under a kilometre is not rounded into a metre count no one writes`() {
        // Rounds to 1000 metres, which has to be read as a kilometre rather than printed as
        // "1000 m".
        assertEquals("1.0 km", Metres(999.6).describe(Locale.ROOT))
        assertEquals("999 m", Metres(999.4).describe(Locale.ROOT))
    }

    @Test
    fun `a negative distance is refused rather than described`() {
        assertFailsWith<IllegalArgumentException> { Metres(-1.0) }
    }

    @Test
    fun `a distance that is not a number is refused`() {
        assertFailsWith<IllegalArgumentException> { Metres(Double.NaN) }
    }
}

class ViewportTest {
    @Test
    fun `the centre is derived from the corners, so it cannot disagree with them`() {
        val viewport = Viewport(west = -10.0, south = 51.0, east = -5.0, north = 55.0)

        assertEquals(Coordinates(-7.5, 53.0), viewport.centre)
    }

    @Test
    fun `a place inside the box is inside the viewport`() {
        val viewport = Viewport(west = -10.0, south = 51.0, east = -5.0, north = 55.0)

        assertTrue(Coordinates(-6.2603, 53.3498) in viewport)
        assertTrue(Coordinates(-2.0, 53.3498) !in viewport)
    }

    @Test
    fun `both corners are inside, because the bounds are closed at each end`() {
        val viewport = Viewport(west = -10.0, south = 51.0, east = -5.0, north = 55.0)

        // The map queries the box it is showing; a place exactly on the north or east edge is
        // drawn, so it has to count as inside.
        assertTrue(Coordinates(-10.0, 51.0) in viewport)
        assertTrue(Coordinates(-5.0, 55.0) in viewport)
    }

    @Test
    fun `a box that wraps the antimeridian is refused, because MongoDB reads it as the rest of Earth`() {
        assertFailsWith<IllegalArgumentException> {
            Viewport(west = 170.0, south = 51.0, east = -170.0, north = 55.0)
        }
    }

    @Test
    fun `an empty box is refused`() {
        assertFailsWith<IllegalArgumentException> {
            Viewport(west = -5.0, south = 55.0, east = -5.0, north = 51.0)
        }
    }
}

class PlaceCategoryTest {
    @Test
    fun `every category the seed can hold is known`() {
        assertEquals(
            listOf("cafe", "coffee_shop", "cafeteria", "coffee_roastery"),
            PlaceCategory.entries.map(PlaceCategory::stored),
        )
    }

    @Test
    fun `a category the seed does not use is not invented`() {
        assertNull(PlaceCategory.of("bar"))
    }
}

class AddressTest {
    @Test
    fun `only the parts Overture gave are shown`() {
        assertEquals("Main St, Letterkenny", Address(street = "Main St", locality = "Letterkenny").oneLine())
    }

    @Test
    fun `an address with nothing in it reads as nothing rather than as commas`() {
        assertEquals("", Address(region = "CO").oneLine())
    }
}

class CoordinatesTest {
    @Test
    fun `a latitude off the planet is refused`() {
        assertFailsWith<IllegalArgumentException> { Coordinates(longitude = 0.0, latitude = 91.0) }
    }

    @Test
    fun `a longitude off the planet is refused`() {
        assertFailsWith<IllegalArgumentException> { Coordinates(longitude = 181.0, latitude = 0.0) }
    }
}
