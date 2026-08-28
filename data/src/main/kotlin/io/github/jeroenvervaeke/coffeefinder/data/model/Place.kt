package io.github.jeroenvervaeke.coffeefinder.data.model

/** Overture's identifier for a place, kept distinct from every other string on the document. */
@JvmInline
value class PlaceId(val value: String) {
    init {
        require(value.isNotBlank()) { "a place id cannot be blank" }
    }
}

/**
 * How sure Overture is that the place exists and is described correctly, from 0 to 1.
 *
 * The seed keeps the whole tail rather than filtering it, so the number ships on every document
 * and deciding how much of it to trust is this application's call.
 */
@JvmInline
value class Confidence(val value: Double) {
    init {
        require(value in 0.0..1.0) { "confidence $value is outside 0..1" }
    }
}

/** Every address field Overture gives is optional; a place with none carries no address at all. */
data class Address(
    val street: String? = null,
    val locality: String? = null,
    val postcode: String? = null,
    val region: String? = null,
) {
    /** What to put under a name in a list: the parts that exist, in the order they are read. */
    fun oneLine(): String = listOfNotNull(street, locality, postcode).joinToString(", ")
}

/** One coffee place, as it is stored and as every screen reads it. */
data class Place(
    val id: PlaceId,
    val name: String,
    val category: PlaceCategory,
    val confidence: Confidence,
    val coordinates: Coordinates,
    val brand: String? = null,
    val address: Address? = null,
)

/** A [Place] with the distance the query measured to it, which only a geo query knows. */
data class NearbyPlace(val place: Place, val distance: Metres)
