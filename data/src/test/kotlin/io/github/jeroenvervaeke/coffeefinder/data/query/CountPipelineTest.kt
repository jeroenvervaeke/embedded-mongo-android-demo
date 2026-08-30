package io.github.jeroenvervaeke.coffeefinder.data.query

import io.github.jeroenvervaeke.coffeefinder.data.DUBLIN
import io.github.jeroenvervaeke.coffeefinder.data.model.Metres
import io.github.jeroenvervaeke.coffeefinder.data.stage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.bson.Document

class CountPipelineTest {
    @Test
    fun `counting appends a count stage and changes nothing in front of it`() {
        val selection = nearestPipeline(DUBLIN, limit = null, maxDistance = Metres(750.0))

        val counting = counting(selection)

        assertEquals(selection, counting.dropLast(1))
        assertEquals(Document("\$count", "n"), counting.last())
    }

    @Test
    fun `the counted pipeline is still measured from the same point and radius`() {
        val counting = counting(nearestPipeline(DUBLIN, limit = null, maxDistance = Metres(750.0)))

        assertEquals(750.0, counting.stage("\$geoNear")["maxDistance"])
    }

    @Test
    fun `counting a capped pipeline is refused, because it would count the cap`() {
        val capped = nearestPipeline(DUBLIN, limit = 50, maxDistance = Metres(750.0))

        assertFailsWith<IllegalArgumentException> { counting(capped) }
    }

    @Test
    fun `a text selection counts what the text matched`() {
        val counting = counting(searchPipeline("insomnia", DUBLIN, limit = null))

        assertEquals("\$match", counting.first().keys.single())
        assertEquals(Document("\$count", "n"), counting.last())
    }
}
