package io.github.jeroenvervaeke.coffeefinder.engine

import android.content.Context
import io.github.jeroenvervaeke.coffeefinder.data.MongoSeam
import io.github.jeroenvervaeke.embeddedmongodb.EmbeddedMongo
import io.github.jeroenvervaeke.embeddedmongodb.StorageOptions
import java.io.File

/**
 * Opens the real engine.
 *
 * The [Context] overload of `open`, not the one without: only that one can ask the platform how
 * much room the volume can give, and the first sign of a full one is the process being killed
 * rather than a command failing.
 */
class EmbeddedMongoOpener(private val context: Context) : DatabaseOpener {
    override suspend fun open(directory: File, options: StorageOptions): OpenDatabase {
        val database = EmbeddedMongo.open(context, directory, options)
        return object : OpenDatabase {
            override val seam: MongoSeam = EmbeddedMongoSeam(database)

            override fun close() = database.close()
        }
    }
}
