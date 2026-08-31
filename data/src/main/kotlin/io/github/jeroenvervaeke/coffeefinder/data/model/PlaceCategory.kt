package io.github.jeroenvervaeke.coffeefinder.data.model

/**
 * The Overture category a place carries, as an enum rather than the raw string.
 *
 * The seed is extracted with `categories.primary IN (...)` over exactly these four, so anything
 * else in a document means the seed and this application disagree, worth a failure at the
 * boundary rather than a silent gap in a filter later.
 */
enum class PlaceCategory(val stored: String, val label: String) {
    CAFE("cafe", "Café"),
    COFFEE_SHOP("coffee_shop", "Coffee shop"),
    CAFETERIA("cafeteria", "Cafeteria"),
    COFFEE_ROASTERY("coffee_roastery", "Roastery"),
    ;

    companion object {
        private val byStored = entries.associateBy(PlaceCategory::stored)

        fun of(stored: String): PlaceCategory? = byStored[stored]
    }
}
