package io.github.jeroenvervaeke.coffeefinder.data

import kotlinx.coroutines.flow.Flow
import org.bson.Document

/**
 * Every call this application makes into MongoDB, and the only place the engine is reachable from.
 *
 * The whole data layer sits above this interface, so the aggregation pipelines, the seeding and
 * the screen state are all exercised on the JVM against a scripted implementation — no device, no
 * emulator, no compiled engine. `:app` supplies the one implementation that talks to
 * `EmbeddedMongo`, and it is a dozen lines of delegation.
 *
 * There is no `database` parameter: this application has one database and naming it at every call
 * site would only be a chance to name it differently once.
 */
interface MongoSeam {
    /**
     * Runs one command and returns its reply.
     *
     * @throws Exception whatever the implementation raises for a command the engine rejected.
     */
    suspend fun command(command: Document): Document

    /**
     * Runs a cursor-returning command and emits every document it produces, paging as the
     * collector consumes them.
     */
    fun documents(command: Document): Flow<Document>
}
