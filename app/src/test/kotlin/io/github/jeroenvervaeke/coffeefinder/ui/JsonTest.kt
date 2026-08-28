package io.github.jeroenvervaeke.coffeefinder.ui

import io.github.jeroenvervaeke.coffeefinder.data.model.Coordinates
import io.github.jeroenvervaeke.coffeefinder.data.model.Metres
import io.github.jeroenvervaeke.coffeefinder.data.model.PlaceCategory
import io.github.jeroenvervaeke.coffeefinder.data.query.nearestCommand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.bson.Document

/**
 * The pipeline screen claims to be showing the command that was sent. These are the two things
 * that claim rests on: that the text is the document rather than a paraphrase of it, and that it
 * is readable once it is.
 */
class JsonTest {
    @Test
    fun `what is printed parses back into the command that was sent`() {
        val command = nearestCommand(
            from = Coordinates(longitude = -6.2603, latitude = 53.3498),
            limit = 25,
            maxDistance = Metres.ofKilometres(5.0),
            category = PlaceCategory.CAFE,
        )

        assertEquals(command, Document.parse(command.pretty()))
    }

    @Test
    fun `a pipeline is printed across lines rather than as one`() {
        val printed = nearestCommand(Coordinates(-6.2603, 53.3498), limit = 25).pretty()

        assertTrue(printed.lines().size > 5, "the whole command printed as ${printed.lines().size} line(s)")
    }

    @Test
    fun `numbers are printed as numbers rather than as extended JSON wrappers`() {
        // RELAXED mode, so a reader sees 5000.0 rather than {"$numberDouble": "5000.0"} -- this
        // is read by a person, not by a driver.
        val printed = nearestCommand(
            from = Coordinates(-6.2603, 53.3498),
            limit = 25,
            maxDistance = Metres.ofKilometres(5.0),
        ).pretty()

        assertTrue(printed.contains("5000.0"), printed)
        assertTrue(!printed.contains("\$numberDouble"), printed)
    }
}
