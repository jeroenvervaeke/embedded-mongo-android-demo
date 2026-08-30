package io.github.jeroenvervaeke.coffeefinder.engine

import io.github.jeroenvervaeke.embeddedmongodb.MongoDatabase
import io.github.jeroenvervaeke.embeddedmongodb.StorageOptions
import java.io.File

/**
 * A database that is open: the [MongoDatabase] every query goes through, and the handle that
 * closes the engine behind it.
 *
 * An interface rather than `EmbeddedMongo` itself so that [CoffeeDatabase] holds no engine type
 * at all. That is what makes its lifecycle — opened once, shared by every screen, not discarded
 * when one of them leaves — testable on the JVM, which matters more here than usual: the bug this
 * shape defends against only happens when a caller is cancelled mid-open.
 */
interface OpenDatabase {
    val mongo: MongoDatabase

    fun close()
}

/** Opens the one database this process may have. */
fun interface DatabaseOpener {
    suspend fun open(directory: File, options: StorageOptions): OpenDatabase
}
