package io.github.jeroenvervaeke.coffeefinder.data.query

import io.github.jeroenvervaeke.coffeefinder.data.FakeMongo
import io.github.jeroenvervaeke.embeddedmongodb.createIndexes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.bson.Document

class PlaceIndexesTest {
    @Test
    fun `both indexes the application queries through are built in one command`() = runTest {
        val mongo = FakeMongo()

        val names = mongo.places.createIndexes(placeIndexes())

        assertEquals(listOf(LOCATION_INDEX, NAME_INDEX), names)
        assertEquals(
            Document("createIndexes", "places").append(
                "indexes",
                listOf(
                    Document("key", Document("loc", "2dsphere")).append("name", LOCATION_INDEX),
                    Document("key", Document("name", "text").append("brand", "text"))
                        .append("name", NAME_INDEX)
                        .append("weights", Document("name", 10).append("brand", 5)),
                ),
            ),
            mongo.lastCommand,
        )
    }

    @Test
    fun `a place's own name outranks the chain it belongs to`() {
        val text = placeIndexes().single { it.options.name == NAME_INDEX }
        val weights = text.options.weights as Document

        assertTrue(weights.getInteger("name") > weights.getInteger("brand"))
    }
}
