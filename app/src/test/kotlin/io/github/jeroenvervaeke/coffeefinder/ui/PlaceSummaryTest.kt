package io.github.jeroenvervaeke.coffeefinder.ui

import io.github.jeroenvervaeke.coffeefinder.data.model.Address
import io.github.jeroenvervaeke.coffeefinder.data.model.Confidence
import io.github.jeroenvervaeke.coffeefinder.data.model.Coordinates
import io.github.jeroenvervaeke.coffeefinder.data.model.Place
import io.github.jeroenvervaeke.coffeefinder.data.model.PlaceCategory
import io.github.jeroenvervaeke.coffeefinder.data.model.PlaceId
import kotlin.test.Test
import kotlin.test.assertEquals

class PlaceSummaryTest {
    @Test
    fun `a chain branch with an address shows both`() {
        val place = place(brand = "Insomnia", address = Address(street = "Main St", locality = "Sligo"))

        assertEquals("Insomnia · Main St, Sligo", place.summary())
    }

    @Test
    fun `an independent with an address shows the address alone`() {
        val place = place(address = Address(locality = "Sligo"))

        assertEquals("Sligo", place.summary())
    }

    @Test
    fun `a chain branch Overture gave no address for shows the chain alone`() {
        assertEquals("Insomnia", place(brand = "Insomnia").summary())
    }

    @Test
    fun `a place with neither falls back to what kind of place it is`() {
        assertEquals("Coffee shop", place().summary())
    }

    @Test
    fun `a chain branch whose address fields were all empty shows the chain, not a dangling separator`() {
        // The brand survives, so the outer fallback never runs and only the inner one can stop
        // an empty address becoming "Insomnia · ".
        val place = place(brand = "Insomnia", address = Address(region = "CO"))

        assertEquals("Insomnia", place.summary())
    }

    @Test
    fun `an address holding only a region is not an address worth showing`() {
        // A region on its own ("CO") locates nothing, so the row falls back to the category.
        assertEquals("Coffee shop", place(address = Address(region = "CO")).summary())
    }

    private fun place(brand: String? = null, address: Address? = null) = Place(
        id = PlaceId("id"),
        name = "The House Of Pretzels",
        category = PlaceCategory.COFFEE_SHOP,
        confidence = Confidence(0.77),
        coordinates = Coordinates(longitude = -7.33, latitude = 52.78),
        brand = brand,
        address = address,
    )
}
