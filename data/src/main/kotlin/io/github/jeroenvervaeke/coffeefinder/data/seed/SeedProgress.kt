package io.github.jeroenvervaeke.coffeefinder.data.seed

/**
 * What the seeder is doing, for a screen to show while it does it.
 *
 * The insert count is reported without a total: the seed is read as a stream, so how many
 * documents are in it is not known until the last one has gone in.
 */
sealed interface SeedProgress {
    /** Asking the database whether it has been seeded already. */
    data object Checking : SeedProgress

    /** [inserted] documents have reached the collection so far. */
    data class Inserting(val inserted: Long) : SeedProgress

    /** Building the `2dsphere` and text indexes, which happens after the documents are in. */
    data object Indexing : SeedProgress

    /** The database holds [places] coffee places and is ready to be queried. */
    data class Ready(val places: Long) : SeedProgress
}
