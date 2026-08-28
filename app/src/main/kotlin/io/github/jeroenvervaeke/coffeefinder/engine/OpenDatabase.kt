package io.github.jeroenvervaeke.coffeefinder.engine

import io.github.jeroenvervaeke.coffeefinder.data.MongoSeam
import io.github.jeroenvervaeke.embeddedmongodb.StorageOptions
import java.io.File

/**
 * A database that is open: the seam onto it, and the handle that closes it.
 *
 * An interface rather than `EmbeddedMongo` itself so that [CoffeeDatabase] holds no engine type
 * at all. That is what makes its lifecycle — opened once, shared by every screen, not discarded
 * when one of them leaves — testable on the JVM, which matters more here than usual: the bug this
 * shape defends against only happens when a caller is cancelled mid-open.
 */
interface OpenDatabase {
    val seam: MongoSeam

    fun close()
}

/** Opens the one database this process may have. */
fun interface DatabaseOpener {
    suspend fun open(directory: File, options: StorageOptions): OpenDatabase
}
