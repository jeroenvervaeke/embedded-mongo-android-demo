package io.github.jeroenvervaeke.coffeefinder.ui

import android.util.Log
import io.github.jeroenvervaeke.coffeefinder.data.seed.SeedProgress
import kotlin.time.Duration
import kotlin.time.TimeSource

/**
 * What starting up and each query actually cost, as logcat lines.
 *
 * The README makes numbers of both — how long the first launch takes, what a settled pan of the
 * map costs — and both are claims about a device rather than about this code. An emulator
 * sharing a host with three others answers a different question, and from outside the process
 * there is no way to check them on a phone short of filming the screen. So the application says
 * it out loud, and `adb logcat -s CoffeeTimings` is the whole measuring apparatus.
 *
 * Logged rather than shown, because the useful form of a pan latency is a hundred of them, and a
 * hundred numbers are read with a pipe and not with an eye. The single most recent one is on the
 * map and pipeline screens, which is the form a person can use.
 */
const val TIMINGS_TAG = "CoffeeTimings"

/**
 * Where a timing line goes.
 *
 * A seam for the same reason the clock is one, and for a sharper reason: `android.util.Log` is a
 * stub that throws "not mocked" under JVM unit tests, so a class that calls it inline is a class
 * that cannot be tested at all. What [StartupTimer] decides — which phases are reported, and that
 * a dozen inserting progresses collapse into one line rather than a dozen — is worth a test, so
 * the logger is passed in exactly as the clock is.
 */
fun interface TimingSink {
    fun line(message: String)
}

/** Where the lines go in the application. */
val LOGCAT: TimingSink = TimingSink { message -> Log.i(TIMINGS_TAG, message) }

/** One line per finished query: which screen asked, how much came back, and how long it took. */
fun logQuery(screen: String, documents: Int, took: Duration, to: TimingSink = LOGCAT) {
    to.line("$screen: $documents documents in ${took.describe()}")
}

/**
 * Times a start-up, one line per phase the seeder leaves behind.
 *
 * Per phase rather than one total, because the total is the least informative version of it: an
 * Ireland-sized seed is a bulk insert and two index builds, and which of those dominates on a
 * real phone is exactly what an emulator could not say. The phases come from [SeedProgress],
 * which the seeder already publishes for the screen — nothing here asks the engine anything.
 *
 * "Startup" rather than "cold start": the same phases are walked when the database is already
 * seeded, minus the inserting, and calling that a cold start would put a false label on the log
 * of every launch after the first.
 *
 * This measures from the first ask, so it excludes process start and the dex loading in front of
 * it. What covers that end is `ReportDrawnWhen`, whose number the system prints itself.
 *
 * The clock starts at construction and the first phase is the engine open, because that is the
 * one part the seeder cannot report: it has already happened by the time there is a
 * [SeedProgress] to publish. Construct this immediately before asking for the database.
 */
class StartupTimer(
    private val clock: TimeSource = TimeSource.Monotonic,
    private val to: TimingSink = LOGCAT,
) {
    private val began = clock.markNow()
    private var phase: String? = "opening the engine"
    private var phaseBegan = began

    /** Called for every progress the seeder publishes; prints a line when it moves on. */
    fun reached(progress: SeedProgress) = when (progress) {
        SeedProgress.Checking -> enter("counting what is already there")
        is SeedProgress.Inserting -> enter("inserting")
        SeedProgress.Indexing -> enter("building the 2dsphere and text indexes")
        is SeedProgress.Ready -> finish(progress.places)
    }

    private fun enter(next: String) {
        // The seeder publishes a progress per batch, so without this every batch would print its
        // own line and "inserting took 220 ms" would never be a number anyone could read.
        if (next == phase) return
        report()
        phase = next
        phaseBegan = clock.markNow()
    }

    private fun finish(places: Long) {
        report()
        // Cleared so a seeder that somehow published twice cannot report the last phase twice.
        phase = null
        to.line("startup: $places places ready in ${began.elapsedNow().describe()}")
    }

    private fun report() {
        phase?.let { to.line("startup: $it took ${phaseBegan.elapsedNow().describe()}") }
    }
}
