package app.toque.editeur

import app.toque.core.machine.Accessory
import app.toque.core.machine.Limits
import app.toque.core.machine.TemperatureTable
import app.toque.core.recipes.StepMachine
import app.toque.core.recipes.StepProgram
import app.toque.core.smart.SmartProgram
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Les limites du formulaire sont celles de l'appli : tout réglage proposé passe ses contrôles. */
class RulesTest {
    private var saved = TemperatureTable.current

    @Before fun smart() { saved = TemperatureTable.current; TemperatureTable.current = TemperatureTable.SMART }
    @After fun restore() { TemperatureTable.current = saved }

    private fun core(m: Machine) = StepMachine(
        StepProgram.of(m.program.key)!!, m.seconds, m.speed, m.tempC, m.reverse,
        if (m.whisk) Accessory.WHISK else Accessory.BLADE, m.pulses,
    )

    @Test fun sameProgramsAndLimitsAsTheApp() {
        assertEquals(StepProgram.values().map { it.key }.toSet(), Program.values().map { it.key }.toSet())
        assertEquals(Limits.MAX_SPEED_HEATED, Rules.MAX_SPEED_HEATED)
        assertEquals(Limits.MAX_SPEED_REVERSE, Rules.MAX_SPEED_REVERSE)
        assertEquals(Accessory.WHISK.maxSpeed, Rules.MAX_SPEED_WHISK)
        assertEquals((Limits.MAX_MS_HIGH_SPEED / 1000).toInt(), Rules.MAX_SECONDS_HIGH_SPEED)
        assertEquals((Limits.MAX_MS_DEFAULT / 1000).toInt(), Rules.spec(Program.MANUAL).maxS)
        assertEquals((Limits.MAX_MS_KNEAD / 1000).toInt(), Rules.spec(Program.KNEAD).maxS)
        assertEquals((Limits.MAX_MS_TOAST / 1000).toInt(), Rules.spec(Program.TOAST).maxS)
        for (p in Program.values()) {
            val smart = StepProgram.of(p.key)!!.smart ?: continue
            val s = Rules.spec(p)
            assertTrue(p.name, s.minS >= smart.minS && s.maxS <= smart.maxS)
            if (smart.heated) assertEquals(p.name, listOf(smart.minC, smart.maxC, smart.defaultC), listOf(s.minC, s.maxC, s.defaultC))
        }
        assertTrue(Rules.TEMPERATURES.all { TemperatureTable.SMART.levelFor(it) > 0 })
    }

    @Test fun everyOfferedSettingPassesTheAppChecks() {
        for (p in Program.values()) {
            val d = Rules.defaults(p)
            assertEquals(p.name, emptyList<Any>(), core(d).violations())
            val s = Rules.spec(p)
            for (t in s.temperatures) {
                val m = Rules.normalize(d.copy(tempC = t))
                assertEquals("$p $t", emptyList<Any>(), core(m).violations())
            }
        }
        for (t in listOf(0) + Rules.TEMPERATURES) for (rev in listOf(false, true)) for (whisk in listOf(false, true)) {
            val base = Machine(Program.MANUAL, 60, 1, t, rev, whisk)
            for (speed in 1..Rules.maxSpeed(base)) {
                val m = base.copy(speed = speed)
                assertEquals(m, Rules.normalize(m))
                assertEquals("$m", emptyList<Any>(), core(m).violations())
                assertEquals("$m", emptyList<Any>(), core(m.copy(seconds = Rules.maxSeconds(m))).violations())
            }
        }
    }

    @Test fun normalizeCapsTheSpeed() {
        assertEquals(3, Rules.normalize(Machine(Program.MANUAL, 60, 9, 100)).speed)
        assertEquals(3, Rules.normalize(Machine(Program.MANUAL, 60, 9, reverse = true)).speed)
        assertEquals(4, Rules.normalize(Machine(Program.MANUAL, 60, 9, whisk = true)).speed)
        assertEquals(10, Rules.normalize(Machine(Program.MANUAL, 60, 10)).speed)
        assertEquals(1, Rules.normalize(Machine(Program.MANUAL, 60, 0)).speed)
        assertEquals(100, Rules.normalize(Machine(Program.MANUAL, 60, 1, 98)).tempC)
        assertEquals(120, Rules.normalize(Machine(Program.STEAM, 60, 3, 0)).tempC)
        assertEquals(900, Rules.maxSeconds(Machine(Program.MANUAL, 60, 7)))
    }
}
