package io.github.jeroenvervaeke.coffeefinder.engine

import android.content.Context
import io.github.jeroenvervaeke.coffeefinder.data.query.COFFEE_DATABASE
import io.github.jeroenvervaeke.embeddedmongodb.EmbeddedMongo
import io.github.jeroenvervaeke.embeddedmongodb.MongoDatabase
import io.github.jeroenvervaeke.embeddedmongodb.StorageOptions
import java.io.File

/**
 * Opens the real engine, and is the whole of this application's contact with the native library.
 *
 * Deliberately this short. Naming the directory, the storage limits and the database is all there
 * is to do — everything above it is [MongoDatabase] and the collections on it, which are plain
 * Kotlin and live in `:data` where they are tested without an engine at all.
 *
 * The [Context] overload of `open`, not the one without: only that one can ask the platform how
 * much room the volume can give, and the first sign of a full one is the process being killed
 * rather than a command failing.
 */
class EmbeddedMongoOpener(private val context: Context) : DatabaseOpener {
    override suspend fun open(directory: File, options: StorageOptions): OpenDatabase {
        val engine = EmbeddedMongo.open(context, directory, options)
        return object : OpenDatabase {
            override val mongo: MongoDatabase = engine.database(COFFEE_DATABASE)

            override fun close() = engine.close()
        }
    }
}
