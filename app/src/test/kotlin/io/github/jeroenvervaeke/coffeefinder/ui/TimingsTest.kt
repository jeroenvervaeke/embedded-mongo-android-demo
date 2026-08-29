package io.github.jeroenvervaeke.coffeefinder.ui

import io.github.jeroenvervaeke.coffeefinder.data.seed.SeedProgress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TestTimeSource

class TimingsTest {
    @Test
    fun `each phase is its own line, timed from where the one before it ended`() {
        val lines = Recorded()
        val clock = TestTimeSource()
        val timer = StartupTimer(clock, lines)

        clock += 400.milliseconds
        timer.reached(SeedProgress.Checking)
        clock += 5.milliseconds
        timer.reached(SeedProgress.Inserting(1_000))
        clock += 220.milliseconds
        timer.reached(SeedProgress.Indexing)
        clock += 100.milliseconds
        timer.reached(SeedProgress.Ready(5_180))

        assertEquals(
            listOf(
                "startup: opening the engine took 400.0 ms",
                "startup: counting what is already there took 5.0 ms",
                "startup: inserting took 220.0 ms",
                "startup: building the 2dsphere and text indexes took 100.0 ms",
                "startup: 5180 places ready in 725.0 ms",
            ),
            lines.all,
        )
    }

    @Test
    fun `the seeder's progress per batch is one inserting line, not one per batch`() {
        val lines = Recorded()
        val clock = TestTimeSource()
        val timer = StartupTimer(clock, lines)
        timer.reached(SeedProgress.Checking)

        // The phase starts at the first batch, so the eleven that follow it are its duration.
        timer.reached(SeedProgress.Inserting(inserted = 500))
        repeat(11) {
            clock += 20.milliseconds
            timer.reached(SeedProgress.Inserting(inserted = (it + 2) * 500))
        }
        timer.reached(SeedProgress.Indexing)

        assertEquals(
            listOf("startup: inserting took 220.0 ms"),
            lines.all.filter { it.contains("inserting") },
        )
    }

    @Test
    fun `a database that was already seeded reports no inserting at all`() {
        val lines = Recorded()
        val clock = TestTimeSource()
        val timer = StartupTimer(clock, lines)

        clock += 450.milliseconds
        timer.reached(SeedProgress.Checking)
        clock += 12.milliseconds
        timer.reached(SeedProgress.Ready(5_180))

        assertTrue(lines.all.none { it.contains("inserting") }, "reported: ${lines.all}")
        assertEquals("startup: 5180 places ready in 462.0 ms", lines.all.last())
    }

    @Test
    fun `a start-up that never gets past opening reports nothing`() {
        val lines = Recorded()

        StartupTimer(TestTimeSource(), lines)

        // The engine phase is opened at construction, so it must not be reported until something
        // ends it -- otherwise a database that never opened would print a duration for it.
        assertEquals(emptyList(), lines.all)
    }

    @Test
    fun `a finished query names the screen, the documents and the time`() {
        val lines = Recorded()

        logQuery("map", 5_180, 62.6.milliseconds, lines)

        assertEquals(listOf("map: 5180 documents in 62.6 ms"), lines.all)
    }
}

/** A [TimingSink] that keeps what it was told, which is what makes these assertions possible. */
private class Recorded : TimingSink {
    private val written = mutableListOf<String>()

    val all: List<String> get() = written

    override fun line(message: String) {
        written += message
    }
}
