package app.toque.core

import app.toque.core.backup.RescueRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Rescue mode rule (spec 007, US3) with excerpts of `logcat -b crash -v epoch`. */
class RescueRuleTest {
    private val t0 = 1_764_234_000_000L // sys.boot_completed

    private fun crash(dtSec: Int, pkg: String = "app.toque"): String {
        val t = (t0 / 1000 + dtSec).toString() + ".123"
        return """
            |$t  2345  2345 E AndroidRuntime: FATAL EXCEPTION: main
            |$t  2345  2345 E AndroidRuntime: Process: $pkg, PID: 2345
            |$t  2345  2345 E AndroidRuntime: java.lang.RuntimeException: Unable to create application app.toque.ToqueApp
        """.trimMargin()
    }

    private fun decide(log: String, nowSec: Int = 120, journal: Boolean = false) =
        RescueRule.shouldTrigger(RescueRule.crashes(log), t0, t0 + nowSec * 1000L, journal)

    @Test fun twoCrashesAreNotEnough() {
        assertFalse(decide(crash(5) + "\n" + crash(40)))
    }

    @Test fun threeCrashesInHundredSeconds() {
        assertTrue(decide(listOf(crash(0), crash(50), crash(100)).joinToString("\n")))
    }

    @Test fun threeCrashesInTwoHundredSeconds() {
        assertFalse(decide(listOf(crash(0), crash(100), crash(200)).joinToString("\n"), nowSec = 200))
    }

    @Test fun crashesDuringBootCount() {
        assertTrue(decide(listOf(crash(-20), crash(-10), crash(3)).joinToString("\n"), nowSec = 10))
    }

    @Test fun neverDuringAJournal() {
        assertFalse(decide(listOf(crash(0), crash(1), crash(2)).joinToString("\n"), journal = true))
    }

    @Test fun pilotAndOtherAppsDoNotCount() {
        val log = listOf(crash(1, "app.toque.pilote"), crash(2, "com.tecpal.device.mc30"), crash(3, "app.toque.pilote")).joinToString("\n")
        assertEquals(0, RescueRule.crashes(log).size)
        assertFalse(decide(log + "\n" + crash(4) + "\n" + crash(5)))
    }

    @Test fun nativeCrash() {
        val native = "${t0 / 1000 + 1}.500   812   812 F DEBUG   : pid: 2345, tid: 2345, name: app.toque  >>> app.toque <<<"
        assertEquals(1, RescueRule.crashes(native).size)
    }
}
