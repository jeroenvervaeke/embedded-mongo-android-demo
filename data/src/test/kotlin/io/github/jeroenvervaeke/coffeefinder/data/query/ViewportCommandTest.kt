package io.github.jeroenvervaeke.coffeefinder.data.query

import io.github.jeroenvervaeke.coffeefinder.data.model.Viewport
import io.github.jeroenvervaeke.coffeefinder.data.pipeline
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.bson.Document

class ViewportCommandTest {
    @Test
    fun `the viewport becomes a closed anticlockwise GeoJSON ring`() {
        val match = viewportCommand(BOX, limit = 100).pipeline().first()["\$match"] as Document
        val geometry = ((match["loc"] as Document)["\$geoWithin"] as Document)["\$geometry"]

        assertEquals(
            Document("type", "Polygon").append(
                "coordinates",
                listOf(
                    listOf(
                        listOf(-10.0, 51.0),
                        listOf(-5.0, 51.0),
                        listOf(-5.0, 55.0),
                        listOf(-10.0, 55.0),
                        listOf(-10.0, 51.0),
                    ),
                ),
            ),
            geometry,
        )
    }

    @Test
    fun `the reply is cut down to what a dot and its label need`() {
        val project = viewportCommand(BOX, limit = 100).pipeline().last()["\$project"] as Document

        // The values matter as much as the keys: a 0 here excludes the field instead of keeping
        // it, and `loc` excluded is a map with nothing to draw.
        assertEquals(
            Document("name", 1).append("cat", 1).append("brand", 1)
                .append("confidence", 1).append("loc", 1),
            project,
        )
    }

    @Test
    fun `the limit is applied before the projection, so nothing is shaped that is thrown away`() {
        val pipeline = viewportCommand(BOX, limit = 100).pipeline()

        assertEquals(listOf("\$match", "\$limit", "\$project"), pipeline.map { it.keys.single() })
        assertEquals(100, pipeline[1]["\$limit"])
    }

    @Test
    fun `a limit of zero is refused rather than sent as a query returning nothing`() {
        assertFailsWith<IllegalArgumentException> { viewportCommand(BOX, limit = 0) }
    }
}

private val BOX = Viewport(west = -10.0, south = 51.0, east = -5.0, north = 55.0)
