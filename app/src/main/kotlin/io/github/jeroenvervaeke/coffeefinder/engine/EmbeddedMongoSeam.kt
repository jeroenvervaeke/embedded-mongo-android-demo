package io.github.jeroenvervaeke.coffeefinder.engine

import io.github.jeroenvervaeke.coffeefinder.data.MongoSeam
import io.github.jeroenvervaeke.coffeefinder.data.query.COFFEE_DATABASE
import io.github.jeroenvervaeke.embeddedmongodb.EmbeddedMongo
import kotlinx.coroutines.flow.Flow
import org.bson.Document

/**
 * The whole of this application's contact with the native engine.
 *
 * Deliberately this short. Everything else — the pipelines, the parsing, the seeding, the screen
 * state — is in `:data`, above [MongoSeam], and is tested on the JVM against a fake. What is left
 * here is the part that cannot be: naming the database, and handing a command to the library.
 *
 * Neither call reaches the engine on the main thread. `EmbeddedMongo.command` suspends onto the
 * library's own database thread, and `documents` is a flow already `flowOn` it.
 */
class EmbeddedMongoSeam(private val database: EmbeddedMongo) : MongoSeam {
    override suspend fun command(command: Document): Document =
        database.command(COFFEE_DATABASE, command)

    override fun documents(command: Document): Flow<Document> =
        database.documents(COFFEE_DATABASE, command)
}
