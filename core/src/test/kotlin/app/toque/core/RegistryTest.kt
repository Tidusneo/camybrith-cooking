package app.toque.core

import app.toque.core.backup.BootAction
import app.toque.core.backup.ChangeState
import app.toque.core.backup.ChangeType
import app.toque.core.backup.Cooking
import app.toque.core.backup.Iso
import app.toque.core.backup.Journal
import app.toque.core.backup.Mode
import app.toque.core.backup.OperationType
import app.toque.core.backup.Origin
import app.toque.core.backup.Registry
import app.toque.core.backup.RegistryException
import app.toque.core.backup.Report
import app.toque.core.backup.RobotId
import app.toque.core.backup.Scope
import app.toque.core.backup.Step
import app.toque.core.backup.Undo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.TimeZone

class RegistryTest {
    private val d = "2026-11-27T10:00:00+01:00"
    private val svc = "com.tecpal.device.mc30/com.tecpal.device.mc30.SerialPortMC30Service"

    private fun refused(block: () -> Unit) {
        try { block(); fail("should have been refused") } catch (_: RegistryException) {}
    }

    @Test fun invalidTargetsAreRefused() {
        val r = Registry()
        refused { r.note(ChangeType.COMPONENT, "no-slash", "enabled", "disabled", Scope.LIDL, "interface", d) }
        refused { r.note(ChangeType.COMPONENT, "com.x/; rm -rf /", "enabled", "disabled", Scope.LIDL, "interface", d) }
        refused { r.note(ChangeType.SETTING, "unknown/key", "a", "b", Scope.REMOVAL, "barre.sh", d) }
        refused { r.note(ChangeType.APP, "notapackage", "absent", "installee", Scope.REMOVAL, "installer.sh", d) }
        refused { r.note(ChangeType.MODULE, "../etc", "absent", "installe", Scope.REMOVAL, "animation.sh", d) }
        refused { r.note(ChangeType.SETTING, "secure/x", "a", "b\nc", Scope.REMOVAL, "barre.sh", d) }
        r.note(ChangeType.SETTING, "secure/enabled_accessibility_services", "absent", "nu.nav.bar/nu.nav.bar.service.NavigationBarService", Scope.REMOVAL, "barre.sh", d)
    }

    @Test fun mergeKeepsOriginalState() {
        var (r, id1) = Registry().note(ChangeType.COMPONENT, svc, "enabled", "disabled", Scope.LIDL, "interface", d)
        val (r2, id2) = r.note(ChangeType.COMPONENT, svc, "disabled", "disabled", Scope.LIDL, "interface", "2026-11-28T10:00:00+01:00")
        assertEquals(id1, id2)
        assertEquals(1, r2.changes.size)
        assertEquals("enabled", r2.changes[0].before)
        assertEquals("2026-11-28T10:00:00+01:00", r2.changes[0].date)
        // Once undone, a new record starts a new entry.
        r = r2.mark(id1, ChangeState.UNDONE)
        val (r3, id3) = r.note(ChangeType.COMPONENT, svc, "enabled", "disabled", Scope.LIDL, "interface", d)
        assertTrue(id3 > id1)
        assertEquals(2, r3.changes.size)
    }

    @Test fun failedAttemptRemovesANewChange() {
        val (r, id) = Registry().note(ChangeType.COMPONENT, svc, "enabled", "disabled", Scope.LIDL, "interface", d)
        assertTrue(r.noteDone(id, false).changes.isEmpty())
        val ok = r.noteDone(id, true)
        assertTrue(ok.changes[0].confirmed)
        // Merged into a confirmed change: a failure does not remove the original state.
        val (r2, id2) = ok.note(ChangeType.COMPONENT, svc, "disabled", "disabled", Scope.LIDL, "interface", d)
        assertEquals(1, r2.noteDone(id2, false).changes.size)
        refused { r.noteDone(99, true) }
    }

    @Test fun undoByDecreasingIdWithinScope() {
        var r = Registry()
        r = r.note(ChangeType.APP, "app.toque", "absent", "installee", Scope.REMOVAL, "installer.sh", d).first
        r = r.note(ChangeType.STARTUP, RobotId.BOOT_RECEIVER, "absent", "enabled", Scope.LIDL, "installer.sh", d).first
        r = r.note(ChangeType.STARTUP, RobotId.HOME_ALIAS, "absent", "enabled", Scope.LIDL, "installer.sh", d).first
        r = r.note(ChangeType.COMPONENT, svc, "enabled", "disabled", Scope.LIDL, "interface", d).first
        r = r.note(ChangeType.APP, "com.topjohnwu.magisk", "absent", "installee", Scope.BASE, "guide", d).first
        val plan = r.undoPlan(Scope.LIDL)
        assertEquals(listOf(4, 3, 2), plan.map { it.id })
        assertTrue(plan.none { it.scope != Scope.LIDL })
        assertEquals(listOf("pm", "enable", svc), Undo.command(plan[0]))
        assertEquals(listOf("pm", "disable", RobotId.HOME_ALIAS), Undo.command(plan[1]))
        // Reapply: increasing id, only what the last return undid.
        val undone = plan.fold(r) { acc, c -> acc.mark(c.id, ChangeState.UNDONE) }
        assertEquals(listOf(2, 4), undone.reapplyPlan(listOf(4, 2)).map { it.id })
        assertEquals(listOf("pm", "disable", svc), Undo.reapply(undone.changes.first { it.id == 4 }))
    }

    @Test fun settingUndo() {
        val r = Registry().note(ChangeType.SETTING, "secure/enabled_accessibility_services", "absent", "x/y.Z", Scope.REMOVAL, "barre.sh", d).first
        assertEquals(listOf("settings", "delete", "secure", "enabled_accessibility_services"), Undo.command(r.changes[0]))
        val r2 = Registry().note(ChangeType.SETTING, "secure/accessibility_enabled", "0", "1", Scope.REMOVAL, "barre.sh", d).first
        assertEquals(listOf("settings", "put", "secure", "accessibility_enabled", "0"), Undo.command(r2.changes[0]))
    }

    @Test fun jsonRoundTripAndStrictReading() {
        val r = Registry("abc").note(ChangeType.COMPONENT, svc, "enabled", "disabled", Scope.LIDL, "interface", d).first
        assertEquals(r, Registry.parse(r.toJson()))
        refused { Registry.parse("""{"format":2,"modifications":[]}""") }
        refused { Registry.parse("""{"format":1,"modifications":[{"id":1,"type":"commande","cible":"rm -rf /","apres":"x","portee":"lidl","etat":"active"}]}""") }
        refused { Registry.parse("not json") }
    }

    @Test fun journalTransitions() {
        var j = Journal(OperationType.BACK_TO_LIDL, Origin.INTERFACE)
        j = j.next(Step.STOP).next(Step.UNDO).next(Step.CHECK).next(Step.DONE)
        assertEquals(Step.DONE, j.step)
        refused { Journal(OperationType.BACK_TO_LIDL, Origin.INTERFACE).next(Step.UNDO) }
        refused { Journal(OperationType.BACK_TO_LIDL, Origin.INTERFACE).next(Step.REAPPLY) }
        refused { Journal(OperationType.RESUME, Origin.INTERFACE).next(Step.STOP) }
        assertEquals(j, Journal.parse(j.toJson()))
    }

    @Test fun bootFinishesAnInterruptedReturn() {
        for (s in listOf(Step.START, Step.STOP, Step.UNDO, Step.CHECK))
            assertEquals(BootAction.FINISH_RETURN, Journal(OperationType.BACK_TO_LIDL, Origin.GESTURE, s).bootAction())
        assertEquals(BootAction.CLEAR, Journal(OperationType.BACK_TO_LIDL, Origin.GESTURE, Step.DONE).bootAction())
        assertEquals(BootAction.BACK_TO_LIDL, Journal(OperationType.RESUME, Origin.INTERFACE, Step.REAPPLY).bootAction())
    }

    @Test fun reportsKept() {
        val names = (1..13).map { "202611%02d-100000.json".format(it) }
        assertEquals(names.take(3), Report.toDelete(names.shuffled()))
        assertEquals("20261127-100000.json", Report.fileName(d))
        val r = Report(OperationType.BACK_TO_LIDL, d, Origin.INTERFACE, failures = mapOf(3 to "refusé"))
        val read = Report.parse(r.toJson())
        assertFalse(read.succeeded)
        assertEquals("refusé", read.failures[3])
        assertTrue(r.toJson().contains(Report.REMINDER))
    }

    @Test fun isoDates() {
        val ms = Iso.parse(d)!!
        assertEquals(d, Iso.format(ms, TimeZone.getTimeZone("Europe/Paris")))
        assertEquals(ms, Iso.parse("2026-11-27T09:00:00Z"))
        assertNull(Iso.parse("yesterday"))
    }

    @Test fun cookingState() {
        val t = Iso.parse(d)!!
        val busy = Cooking.json(true, "Risotto", d)
        assertEquals(Cooking.State.RUNNING, Cooking.evaluate(busy, true, t + 30_000))
        assertEquals(Cooking.State.UNKNOWN, Cooking.evaluate(busy, true, t + 90_000))
        assertEquals(Cooking.State.IDLE, Cooking.evaluate(busy, false, t + 30_000))
        assertEquals(Cooking.State.IDLE, Cooking.evaluate(Cooking.json(false, "", d), true, t + 3_600_000))
        assertEquals(Cooking.State.UNKNOWN, Cooking.evaluate(null, true, t))
        assertEquals(Cooking.State.UNKNOWN, Cooking.evaluate("{", true, t))
    }

    @Test fun modeAndIdentity() {
        assertFalse(Mode.parse(null).lidl)
        assertFalse(Mode.parse("damaged").lidl)
        val m = Mode(true, d, Origin.GESTURE)
        assertEquals(m, Mode.parse(m.toJson()))
        val id = RobotId("com.tecpal.device.mc30", "SerialPortMC30Service")
        assertEquals(id, RobotId.parse(id.toJson()))
        assertNull(RobotId.parse("""{"vendorPackage":"x; reboot","hardwareServiceSuffix":"S"}"""))
    }
}
