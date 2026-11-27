package app.toque.core

import app.toque.core.backup.AndroidOutput
import app.toque.core.backup.ChangeState
import app.toque.core.backup.Cooking
import app.toque.core.backup.Iso
import app.toque.core.backup.Mode
import app.toque.core.backup.Registry
import app.toque.core.backup.Report
import app.toque.core.backup.RescueCommand
import app.toque.core.backup.RobotId
import app.toque.core.backup.Scope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** The `camybrith` command engine, on a fake Android (pm, am, dumpsys simulated). */
class RescueCommandTest {
    private val lidl = "com.tecpal.device.mc30"
    private val svc = "$lidl.SerialPortMC30Service"
    private val t0 = Iso.parse("2026-11-27T10:00:00+01:00")!!

    /** Fake Android: installed packages, disabled components, processes, commands received. */
    private class FakeAndroid(val lidl: String) : RescueCommand.Device {
        val installed = mutableSetOf(lidl, RobotId.APP, RobotId.PILOT)
        val disabled = mutableMapOf<String, MutableSet<String>>()
        val processes = mutableSetOf(RobotId.APP, RobotId.PILOT)
        val commands = ArrayList<List<String>>()
        var lidlDeclaresHome = true
        var crashLog = ""

        fun disable(target: String) = disabled.getOrPut(target.substringBefore('/')) { mutableSetOf() }.add(AndroidOutput.className(target))
        fun enabled(target: String) = AndroidOutput.className(target) !in disabled[target.substringBefore('/')].orEmpty()

        private fun res(code: Int, out: String, err: String = "") = RescueCommand.Device.Result(code, out, err)

        override fun run(vararg args: String): RescueCommand.Device.Result {
            val a = args.toList()
            commands += a
            return when {
                a == listOf("id", "-u") -> res(0, "0\n")
                a[0] == "pm" && a[1] == "path" -> if (a[2] in installed) res(0, "package:/data/app/${a[2]}/base.apk") else res(1, "")
                a[0] == "pm" && (a[1] == "enable" || a[1] == "disable") -> {
                    val pkg = a[2].substringBefore('/')
                    if (pkg !in installed) return res(0, "", "Error: java.lang.IllegalArgumentException: Unknown package")
                    if (a[1] == "disable") disable(a[2]) else disabled[pkg]?.remove(AndroidOutput.className(a[2]))
                    res(0, "Component {${a[2]}} new state: ${a[1]}d")
                }
                a[0] == "dumpsys" && a[1] == "package" -> {
                    val d = disabled[a[2]].orEmpty()
                    res(0, "Packages:\n  Package [${a[2]}]\n    versionName=1.4.3-rapide\n    User 0: installed=true\n" +
                        (if (d.isEmpty()) "" else "      disabledComponents:\n" + d.joinToString("") { "        $it\n" }) +
                        "  Queries:\n")
                }
                a[0] == "pm" && a[1] == "uninstall" -> { installed.remove(a[2]); processes.remove(a[2]); res(0, "Success") }
                a[0] == "am" && a[1] == "force-stop" -> { processes.remove(a[2]); res(0, "") }
                a[0] == "pidof" -> res(if (a[1] in processes) 0 else 1, if (a[1] in processes) "1234" else "")
                a[0] == "cmd" && "android.intent.category.HOME" in a -> {
                    val home = if (enabled(RobotId.HOME_ALIAS) && RobotId.APP in installed) RobotId.HOME_ALIAS
                    else if (lidlDeclaresHome) "$lidl/.MainActivity" else "com.android.settings/.FallbackHome"
                    res(0, "priority=0 preferredOrder=0\n$home\n")
                }
                a[0] == "cmd" && "android.intent.category.LAUNCHER" in a -> res(0, "priority=0\n${a.last()}/.MainActivity\n")
                a[0] == "dumpsys" && a[1] == "activity" -> res(0, "  mResumedActivity: ActivityRecord{abc u0 $lidl/.MainActivity t3}\n")
                a[0] == "settings" -> res(0, "null\n")
                a[0] == "logcat" -> res(0, crashLog)
                else -> res(0, "")
            }
        }

        override fun freeBytes() = 500L * 1024 * 1024
        override fun totalBytes() = 4000L * 1024 * 1024
        override fun pause(ms: Long) {}
    }

    private fun setup(): Triple<FakeAndroid, File, ArrayList<String>> {
        val f = FakeAndroid(lidl)
        f.disable("$lidl/$svc")
        val dir = Files.createTempDirectory("camybrith").toFile()
        // The interface writes the cooking state as soon as it opens (pilot running, nothing cooking).
        File(dir, "cuisson.json").writeText(Cooking.json(false, "", Iso.format(t0)))
        return Triple(f, dir, ArrayList())
    }

    private fun command(f: FakeAndroid, dir: File, out: ArrayList<String>, t: Long = t0) = RescueCommand(f, dir, { t }, { out += it })

    private fun lastReport(dir: File) = Report.parse(File(dir, "bilans").listFiles()!!.single().readText())

    @Test fun enrolInfersExistingChanges() {
        val (f, dir, out) = setup()
        assertEquals(0, command(f, dir, out).main(arrayOf("inscrire")))
        val reg = Registry.parse(File(dir, "registre.json").readText())
        val lidlScope = reg.active(Scope.LIDL).map { it.target }.toSet()
        assertEquals(setOf("$lidl/$svc", RobotId.BOOT_RECEIVER, RobotId.HOME_ALIAS), lidlScope)
        assertTrue(reg.changes.all { it.inferred })
        assertTrue(File(dir, "reserve").length() == 2L * 1024 * 1024)
        assertEquals(1, command(f, dir, out).main(arrayOf("inscrire")))
    }

    @Test fun fullReturnToLidl() {
        val (f, dir, out) = setup()
        val c = command(f, dir, out)
        c.main(arrayOf("inscrire"))
        assertEquals(0, c.main(arrayOf("retour-lidl")))
        assertTrue(f.enabled("$lidl/$svc"))
        assertFalse(f.enabled(RobotId.BOOT_RECEIVER))
        assertFalse(f.enabled(RobotId.HOME_ALIAS))
        assertTrue(f.processes.isEmpty())
        assertFalse(File(dir, "op.json").exists())
        assertTrue(Mode.parse(File(dir, "mode.json").readText()).lidl)
        val report = lastReport(dir)
        assertTrue(report.text(), report.succeeded)
        assertEquals(3, report.undone.size)
        assertTrue(Registry.parse(File(dir, "registre.json").readText()).changes
            .filter { it.scope == Scope.LIDL }.all { it.state == ChangeState.UNDONE })
        // force-stop before the pm undo commands, restart at the end.
        val iStop = f.commands.indexOf(listOf("am", "force-stop", RobotId.APP))
        val iPm = f.commands.indexOfFirst { it.size > 1 && it[0] == "pm" && it[1] == "enable" }
        assertTrue(iStop in 0 until iPm)
        assertEquals(listOf("svc", "power", "reboot"), f.commands.last())
        // A second return, once in Lidl mode, finds nothing left to undo but succeeds.
        assertEquals(0, command(f, dir, out).main(arrayOf("retour-lidl", "--sans-redemarrage")))
    }

    @Test fun guaranteesWithoutRegistry() {
        // Module installed but registry never initialised: the essentials are still restored.
        val (f, dir, out) = setup()
        assertEquals(0, command(f, dir, out).main(arrayOf("retour-lidl", "--sans-redemarrage")))
        assertTrue(f.enabled("$lidl/$svc"))
        assertFalse(f.enabled(RobotId.BOOT_RECEIVER))
        assertFalse(f.enabled(RobotId.HOME_ALIAS))
        val report = lastReport(dir)
        assertTrue(report.text(), report.succeeded)
        assertEquals(3, report.fixes.size)
    }

    @Test fun refusedWhileCooking() {
        val (f, dir, out) = setup()
        File(dir, "cuisson.json").writeText(Cooking.json(true, "Soupe", Iso.format(t0)))
        assertEquals(1, command(f, dir, out, t0 + 10_000).main(arrayOf("retour-lidl")))
        assertTrue(f.enabled(RobotId.HOME_ALIAS))
        // Interface crashed during a program: unknown → refused, unless --forcer or --secours.
        assertEquals(1, command(f, dir, out, t0 + 120_000).main(arrayOf("retour-lidl")))
        assertEquals(0, command(f, dir, out, t0 + 120_000).main(arrayOf("retour-lidl", "--forcer", "--sans-redemarrage")))
    }

    @Test fun interruptedReturnFinishedAtBoot() {
        val (f, dir, out) = setup()
        command(f, dir, out).main(arrayOf("inscrire"))
        // Power cut right after the processes were stopped: journal at step "arret", nothing undone.
        File(dir, "op.json").writeText("""{"type":"retour-lidl","origine":"interface","etape":"arret","date":""}""")
        assertEquals(1, command(f, dir, out).main(arrayOf("retour-lidl")))
        assertEquals(0, command(f, dir, out).main(arrayOf("demarrage")))
        assertFalse(File(dir, "op.json").exists())
        assertTrue(f.enabled("$lidl/$svc"))
        assertFalse(f.enabled(RobotId.HOME_ALIAS))
        assertTrue(Mode.parse(File(dir, "mode.json").readText()).lidl)
        assertFalse(f.commands.contains(listOf("svc", "power", "reboot")))
        assertTrue(lastReport(dir).afterBoot!!.ok)
    }

    @Test fun lidlModeCheckedAgainAtBoot() {
        // Power cut just after a return: Android lost the component states, the journal is gone.
        val (f, dir, out) = setup()
        val c = command(f, dir, out)
        c.main(arrayOf("inscrire"))
        c.main(arrayOf("retour-lidl", "--sans-redemarrage"))
        f.disable("$lidl/$svc")
        f.disabled[RobotId.APP]?.clear()
        assertEquals(0, command(f, dir, out).main(arrayOf("demarrage")))
        assertTrue(f.enabled("$lidl/$svc"))
        assertFalse(f.enabled(RobotId.HOME_ALIAS))
        assertTrue(File(dir, "corrections-demarrage.json").isFile)
    }

    @Test fun powerSwitchGesture() {
        val (f, dir, out) = setup()
        command(f, dir, out).main(arrayOf("inscrire"))
        File(dir, "demarrages-courts").writeText("3\n")
        assertEquals(0, command(f, dir, out).main(arrayOf("demarrage")))
        assertEquals("0", File(dir, "demarrages-courts").readText())
        assertTrue(f.enabled("$lidl/$svc"))
        assertFalse(f.enabled(RobotId.HOME_ALIAS))
        val m = Mode.parse(File(dir, "mode.json").readText())
        assertTrue(m.lidl)
        assertEquals("geste", m.origin?.key)
        assertFalse(f.commands.contains(listOf("svc", "power", "reboot")))
        // Already in Lidl mode: counter back to 0, nothing else.
        File(dir, "demarrages-courts").writeText("4")
        val n = f.commands.size
        command(f, dir, out).main(arrayOf("demarrage"))
        assertEquals("0", File(dir, "demarrages-courts").readText())
        assertTrue(f.commands.drop(n).none { it.take(2) == listOf("pm", "disable") && it[2] == RobotId.BOOT_RECEIVER })
    }

    @Test fun unreadableCounterHasNoEffect() {
        val (f, dir, out) = setup()
        File(dir, "demarrages-courts").writeText("\u0000abc")
        assertEquals(0, command(f, dir, out).main(arrayOf("demarrage")))
        assertTrue(f.enabled(RobotId.HOME_ALIAS))
        assertFalse(File(dir, "mode.json").exists())
    }

    @Test fun rescueModeAfterThreeCrashes() {
        val (f, dir, out) = setup()
        command(f, dir, out).main(arrayOf("inscrire"))
        val t = t0 / 1000
        f.crashLog = (1..3).joinToString("\n") { "${t + it}.000  99  99 E AndroidRuntime: Process: app.toque, PID: 99" }
        assertEquals(0, command(f, dir, out, t0 + 10_000).main(arrayOf("surveiller", t.toString())))
        assertTrue(f.enabled("$lidl/$svc"))
        assertTrue(File(dir, "secours.json").readText().contains("\"plantages\":3"))
        assertEquals("secours", Mode.parse(File(dir, "mode.json").readText()).origin?.key)
        // A single crash: nothing, the watch ends after 120 s.
        val (f2, dir2, out2) = setup()
        f2.crashLog = (1..1).joinToString("\n") { "${t + it}.000  99  99 E AndroidRuntime: Process: app.toque, PID: 99" }
        assertEquals(0, command(f2, dir2, out2, t0 + 130_000).main(arrayOf("surveiller", t.toString())))
        assertTrue(f2.enabled(RobotId.HOME_ALIAS))
    }

    @Test fun unreadableJournalFinishesTowardsLidl() {
        val (f, dir, out) = setup()
        File(dir, "op.json").writeText("{damaged")
        assertEquals(0, command(f, dir, out).main(arrayOf("demarrage")))
        assertTrue(f.enabled("$lidl/$svc"))
        assertFalse(File(dir, "op.json").exists())
    }

    @Test fun storageFullTheReturnStillSucceeds() {
        val (f, dir, out) = setup()
        command(f, dir, out).main(arrayOf("inscrire"))
        dir.setWritable(false)
        try {
            assertEquals(out.joinToString("\n"), 0, command(f, dir, out).main(arrayOf("retour-lidl", "--sans-redemarrage")))
            assertTrue(f.enabled("$lidl/$svc"))
            assertFalse(f.enabled(RobotId.HOME_ALIAS))
        } finally {
            dir.setWritable(true)
        }
    }

    @Test fun noteThenNoteDone() {
        val (f, dir, out) = setup()
        val c = command(f, dir, out)
        assertEquals(0, c.main(arrayOf("noter", "composant", "$lidl/$svc", "enabled", "disabled", "lidl", "interface")))
        assertEquals("1", out.last())
        assertEquals(1, c.main(arrayOf("noter", "commande", "rm", "-", "-", "lidl", "interface")))
        assertEquals(1, c.main(arrayOf("noter", "composant", "x", "enabled", "disabled", "lidl", "interface")))
        assertEquals(0, c.main(arrayOf("noter-fin", "1", "echec")))
        assertTrue(Registry.parse(File(dir, "registre.json").readText()).changes.isEmpty())
        assertEquals(0, c.main(arrayOf("etat", "--json")))
        assertTrue(out.last().contains("\"mode\":\"camybrith\""))
    }

    @Test fun resumeCamybrith() {
        val (f, dir, out) = setup()
        val c = command(f, dir, out)
        c.main(arrayOf("inscrire"))
        assertEquals(1, c.main(arrayOf("reprendre")))      // not in Lidl mode yet
        c.main(arrayOf("retour-lidl", "--sans-redemarrage"))
        command(f, dir, out).main(arrayOf("demarrage"))   // next boot, in Lidl mode: bubble shown
        assertTrue(f.commands.any { it.take(2) == listOf("am", "start-foreground-service") })
        assertEquals(0, command(f, dir, out).main(arrayOf("reprendre")))
        assertFalse(f.enabled("$lidl/$svc"))
        assertTrue(f.enabled(RobotId.BOOT_RECEIVER))
        assertTrue(f.enabled(RobotId.HOME_ALIAS))
        assertFalse(Mode.parse(File(dir, "mode.json").readText()).lidl)
        assertFalse(File(dir, "op.json").exists())
        assertTrue(Registry.parse(File(dir, "registre.json").readText()).active(Scope.LIDL).size == 3)
        assertTrue(f.commands.any { it.take(3) == listOf("am", "start", "-n") && it[3] == "app.toque/.ui.MainActivity" })
        // And back again: the same three changes are undone a second time.
        assertEquals(0, command(f, dir, out).main(arrayOf("retour-lidl", "--sans-redemarrage")))
        assertTrue(f.enabled("$lidl/$svc"))
        assertFalse(f.enabled(RobotId.HOME_ALIAS))
    }

    @Test fun interruptedResumeGoesBackToLidl() {
        val (f, dir, out) = setup()
        val c = command(f, dir, out)
        c.main(arrayOf("inscrire"))
        c.main(arrayOf("retour-lidl", "--sans-redemarrage"))
        f.disable("$lidl/$svc")       // power cut during the reapply: half resumed
        File(dir, "op.json").writeText("""{"type":"reprise","origine":"interface","etape":"reapplication","date":""}""")
        assertEquals(0, command(f, dir, out).main(arrayOf("demarrage")))
        assertTrue(f.enabled("$lidl/$svc"))
        assertFalse(f.enabled(RobotId.HOME_ALIAS))
        assertTrue(Mode.parse(File(dir, "mode.json").readText()).lidl)
    }

    @Test fun fullRemovalKeepsRootAndNavBar() {
        val (f, dir, out) = setup()
        f.installed += listOf("nu.nav.bar", "slowscript.httpfileserver", "com.topjohnwu.magisk")
        val c = command(f, dir, out)
        c.main(arrayOf("inscrire"))
        assertEquals(out.joinToString("\n"), 0, c.main(arrayOf("retrait", "--avec-serveur")))
        assertFalse(RobotId.APP in f.installed)
        assertFalse(RobotId.PILOT in f.installed)
        assertFalse("slowscript.httpfileserver" in f.installed)
        assertTrue("nu.nav.bar" in f.installed)            // kept: not asked
        assertTrue("com.topjohnwu.magisk" in f.installed)  // base: never removed
        assertTrue(f.enabled("$lidl/$svc"))
        assertTrue(f.commands.contains(listOf("touch", "/data/adb/modules/camybrith_secours/remove")))
        assertFalse(dir.exists())
        assertFalse(f.commands.contains(listOf("svc", "power", "reboot")))
    }

    @Test fun returnWorksWhenTheInterfaceIsUninstalled() {
        val (f, dir, out) = setup()
        val c = command(f, dir, out)
        c.main(arrayOf("inscrire"))
        f.installed.remove(RobotId.APP)
        assertEquals(0, c.main(arrayOf("retour-lidl", "--sans-redemarrage")))
        val report = lastReport(dir)
        assertTrue(report.text(), report.succeeded)
        assertTrue(f.enabled("$lidl/$svc"))
    }

    @Test fun androidOutputParsing() {
        val dump = """
            |    User 0: ceDataInode=1 installed=true
            |      disabledComponents:
            |        com.tecpal.device.mc30.SerialPortMC30Service
            |        com.tecpal.device.mc30.Other
            |      enabledComponents:
            |        com.tecpal.device.mc30.Ok
        """.trimMargin()
        assertEquals(setOf("com.tecpal.device.mc30.SerialPortMC30Service", "com.tecpal.device.mc30.Other"), AndroidOutput.disabledComponents(dump))
        assertEquals("app.toque.ui.Accueil", AndroidOutput.className("app.toque/.ui.Accueil"))
        assertEquals("com.x/.Main", AndroidOutput.resolved("priority=0 preferredOrder=0 match=0x108000\ncom.x/.Main\n"))
        assertEquals(null, AndroidOutput.resolved("No activity found\n"))
        assertEquals("com.x/.Main", AndroidOutput.foreground("    mResumedActivity: ActivityRecord{5a1 u0 com.x/.Main t12}"))
    }
}
