package app.toque.core

import app.toque.core.backup.Android
import app.toque.core.backup.Bilan
import app.toque.core.backup.Cuisson
import app.toque.core.backup.EtatModif
import app.toque.core.backup.Iso
import app.toque.core.backup.Mode
import app.toque.core.backup.Portee
import app.toque.core.backup.Registre
import app.toque.core.backup.RobotId
import app.toque.core.backup.Secours
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** Le moteur de la commande `camybrith`, sur un faux Android (pm, am, dumpsys simulés). */
class SecoursTest {
    private val lidl = "com.tecpal.device.mc30"
    private val svc = "$lidl.SerialPortMC30Service"
    private val t0 = Iso.parse("2026-11-27T10:00:00+01:00")!!

    /** Faux Android : paquets installés, composants désactivés, processus, commandes reçues. */
    private class FauxAndroid(val lidl: String) : Secours.Systeme {
        val installes = mutableSetOf(lidl, RobotId.APP, RobotId.PILOTE)
        val desactives = mutableMapOf<String, MutableSet<String>>()
        val processus = mutableSetOf(RobotId.APP, RobotId.PILOTE)
        val commandes = ArrayList<List<String>>()
        var libre = 500L * 1024 * 1024
        var lidlDeclareHome = true

        fun desactive(cible: String) = desactives.getOrPut(cible.substringBefore('/')) { mutableSetOf() }.add(Android.classe(cible))
        fun actif(cible: String) = Android.classe(cible) !in desactives[cible.substringBefore('/')].orEmpty()

        override fun run(vararg args: String): Secours.Systeme.Res {
            val a = args.toList()
            commandes += a
            return when {
                a == listOf("id", "-u") -> Secours.Systeme.Res(0, "0\n")
                a[0] == "pm" && a[1] == "path" -> if (a[2] in installes) Secours.Systeme.Res(0, "package:/data/app/${a[2]}/base.apk") else Secours.Systeme.Res(1, "")
                a[0] == "pm" && (a[1] == "enable" || a[1] == "disable") -> {
                    val pkg = a[2].substringBefore('/')
                    if (pkg !in installes) return Secours.Systeme.Res(0, "", "Error: java.lang.IllegalArgumentException: Unknown package")
                    if (a[1] == "disable") desactive(a[2]) else desactives[pkg]?.remove(Android.classe(a[2]))
                    Secours.Systeme.Res(0, "Component {${a[2]}} new state: ${a[1]}d")
                }
                a[0] == "dumpsys" && a[1] == "package" -> {
                    val d = desactives[a[2]].orEmpty()
                    Secours.Systeme.Res(0, "Packages:\n  Package [${a[2]}]\n    versionName=1.4.3-rapide\n    User 0: installed=true\n" +
                        (if (d.isEmpty()) "" else "      disabledComponents:\n" + d.joinToString("") { "        $it\n" }) +
                        "  Queries:\n")
                }
                a[0] == "am" && a[1] == "force-stop" -> { processus.remove(a[2]); Secours.Systeme.Res(0, "") }
                a[0] == "pidof" -> Secours.Systeme.Res(if (a[1] in processus) 0 else 1, if (a[1] in processus) "1234" else "")
                a[0] == "cmd" && "android.intent.category.HOME" in a -> {
                    val home = if (actif(RobotId.HOME_ALIAS) && RobotId.APP in installes) RobotId.HOME_ALIAS
                    else if (lidlDeclareHome) "$lidl/.MainActivity" else "com.android.settings/.FallbackHome"
                    Secours.Systeme.Res(0, "priority=0 preferredOrder=0\n$home\n")
                }
                a[0] == "cmd" && "android.intent.category.LAUNCHER" in a -> Secours.Systeme.Res(0, "priority=0\n${a.last()}/.MainActivity\n")
                a[0] == "dumpsys" && a[1] == "activity" -> Secours.Systeme.Res(0, "  mResumedActivity: ActivityRecord{abc u0 $lidl/.MainActivity t3}\n")
                a[0] == "settings" -> Secours.Systeme.Res(0, "null\n")
                else -> Secours.Systeme.Res(0, "")
            }
        }

        override fun libre() = libre
        override fun total() = 4000L * 1024 * 1024
        override fun pause(ms: Long) {}
    }

    private fun banc(): Triple<FauxAndroid, File, ArrayList<String>> {
        val f = FauxAndroid(lidl)
        f.desactive("$lidl/$svc")
        val dir = Files.createTempDirectory("camybrith").toFile()
        // L'interface écrit l'état de cuisson dès son ouverture (pilote actif, rien en cours).
        File(dir, "cuisson.json").writeText(Cuisson.json(false, "", Iso.format(t0)))
        return Triple(f, dir, ArrayList())
    }

    private fun secours(f: FauxAndroid, dir: File, out: ArrayList<String>, t: Long = t0) = Secours(f, dir, { t }, { out += it })

    @Test fun inscrireDeduitLesModifications() {
        val (f, dir, out) = banc()
        assertEquals(0, secours(f, dir, out).main(arrayOf("inscrire")))
        val reg = Registre.parse(File(dir, "registre.json").readText())
        val lidlScope = reg.actives(Portee.LIDL).map { it.cible }.toSet()
        assertEquals(setOf("$lidl/$svc", RobotId.BOOT_RECEIVER, RobotId.HOME_ALIAS), lidlScope)
        assertTrue(reg.modifications.all { it.deduit })
        assertTrue(File(dir, "reserve").length() == 2L * 1024 * 1024)
        assertEquals(1, secours(f, dir, out).main(arrayOf("inscrire")))
    }

    @Test fun retourLidlComplet() {
        val (f, dir, out) = banc()
        val s = secours(f, dir, out)
        s.main(arrayOf("inscrire"))
        assertEquals(0, s.main(arrayOf("retour-lidl")))
        assertTrue(f.actif("$lidl/$svc"))
        assertFalse(f.actif(RobotId.BOOT_RECEIVER))
        assertFalse(f.actif(RobotId.HOME_ALIAS))
        assertTrue(f.processus.isEmpty())
        assertFalse(File(dir, "op.json").exists())
        assertTrue(Mode.parse(File(dir, "mode.json").readText()).lidl)
        val bilan = Bilan.parse(File(dir, "bilans").listFiles()!!.single().readText())
        assertTrue(bilan.texte(), bilan.reussi)
        assertEquals(3, bilan.annulees.size)
        assertTrue(Registre.parse(File(dir, "registre.json").readText()).modifications
            .filter { it.portee == Portee.LIDL }.all { it.etat == EtatModif.ANNULEE })
        // force-stop avant les annulations par pm, redémarrage à la fin.
        val iStop = f.commandes.indexOf(listOf("am", "force-stop", RobotId.APP))
        val iPm = f.commandes.indexOfFirst { it.size > 1 && it[0] == "pm" && it[1] == "enable" }
        assertTrue(iStop in 0 until iPm)
        assertEquals(listOf("svc", "power", "reboot"), f.commandes.last())
        // Un second retour, une fois en mode Lidl, ne trouve plus rien à annuler mais réussit.
        assertEquals(0, secours(f, dir, out).main(arrayOf("retour-lidl", "--sans-redemarrage")))
    }

    @Test fun garantiesSansRegistre() {
        // Module installé mais registre jamais initialisé : l'essentiel est quand même remis.
        val (f, dir, out) = banc()
        assertEquals(0, secours(f, dir, out).main(arrayOf("retour-lidl", "--sans-redemarrage")))
        assertTrue(f.actif("$lidl/$svc"))
        assertFalse(f.actif(RobotId.BOOT_RECEIVER))
        assertFalse(f.actif(RobotId.HOME_ALIAS))
        val bilan = Bilan.parse(File(dir, "bilans").listFiles()!!.single().readText())
        assertTrue(bilan.texte(), bilan.reussi)
        assertEquals(3, bilan.corrections.size)
    }

    @Test fun refusPendantUneCuisson() {
        val (f, dir, out) = banc()
        File(dir, "cuisson.json").writeText(Cuisson.json(true, "Soupe", Iso.format(t0)))
        assertEquals(1, secours(f, dir, out, t0 + 10_000).main(arrayOf("retour-lidl")))
        assertTrue(f.actif(RobotId.HOME_ALIAS))
        // Interface plantée pendant un programme : inconnu → refus, sauf --forcer et --secours.
        assertEquals(1, secours(f, dir, out, t0 + 120_000).main(arrayOf("retour-lidl")))
        assertEquals(0, secours(f, dir, out, t0 + 120_000).main(arrayOf("retour-lidl", "--forcer", "--sans-redemarrage")))
    }

    @Test fun coupureEnCoursTermineeAuDemarrage() {
        val (f, dir, out) = banc()
        secours(f, dir, out).main(arrayOf("inscrire"))
        // Coupure juste après l'arrêt des processus : journal à l'étape « arret », rien d'annulé.
        File(dir, "op.json").writeText("""{"type":"retour-lidl","origine":"interface","etape":"arret","date":""}""")
        assertEquals(1, secours(f, dir, out).main(arrayOf("retour-lidl")))
        assertEquals(0, secours(f, dir, out).main(arrayOf("demarrage")))
        assertFalse(File(dir, "op.json").exists())
        assertTrue(f.actif("$lidl/$svc"))
        assertFalse(f.actif(RobotId.HOME_ALIAS))
        assertTrue(Mode.parse(File(dir, "mode.json").readText()).lidl)
        assertFalse(f.commandes.contains(listOf("svc", "power", "reboot")))
        val bilan = Bilan.parse(File(dir, "bilans").listFiles()!!.single().readText())
        assertTrue(bilan.apresDemarrage!!.ok)
    }

    @Test fun modeLidlReverifieAuDemarrage() {
        // Coupure juste après un retour : Android a perdu l'état des composants, le journal est effacé.
        val (f, dir, out) = banc()
        val s = secours(f, dir, out)
        s.main(arrayOf("inscrire"))
        s.main(arrayOf("retour-lidl", "--sans-redemarrage"))
        f.desactive("$lidl/$svc")
        f.desactives[RobotId.APP]?.clear()
        assertEquals(0, secours(f, dir, out).main(arrayOf("demarrage")))
        assertTrue(f.actif("$lidl/$svc"))
        assertFalse(f.actif(RobotId.HOME_ALIAS))
        assertTrue(File(dir, "corrections-demarrage.json").isFile)
    }

    @Test fun journalIllisibleTermineVersLidl() {
        val (f, dir, out) = banc()
        File(dir, "op.json").writeText("{abîmé")
        assertEquals(0, secours(f, dir, out).main(arrayOf("demarrage")))
        assertTrue(f.actif("$lidl/$svc"))
        assertFalse(File(dir, "op.json").exists())
    }

    @Test fun memoirePleineLeRetourAboutitQuandMeme() {
        val (f, dir, out) = banc()
        secours(f, dir, out).main(arrayOf("inscrire"))
        dir.setWritable(false)
        try {
            assertEquals(out.joinToString("\n"), 0, secours(f, dir, out).main(arrayOf("retour-lidl", "--sans-redemarrage")))
            assertTrue(f.actif("$lidl/$svc"))
            assertFalse(f.actif(RobotId.HOME_ALIAS))
        } finally {
            dir.setWritable(true)
        }
    }

    @Test fun noterPuisNoterFin() {
        val (f, dir, out) = banc()
        val s = secours(f, dir, out)
        assertEquals(0, s.main(arrayOf("noter", "composant", "$lidl/$svc", "enabled", "disabled", "lidl", "interface")))
        assertEquals("1", out.last())
        assertEquals(1, s.main(arrayOf("noter", "commande", "rm", "-", "-", "lidl", "interface")))
        assertEquals(1, s.main(arrayOf("noter", "composant", "x", "enabled", "disabled", "lidl", "interface")))
        assertEquals(0, s.main(arrayOf("noter-fin", "1", "echec")))
        assertTrue(Registre.parse(File(dir, "registre.json").readText()).modifications.isEmpty())
        assertEquals(0, s.main(arrayOf("etat", "--json")))
        assertTrue(out.last().contains("\"mode\":\"camybrith\""))
    }

    @Test fun lectureDesSortiesAndroid() {
        val dump = """
            |    User 0: ceDataInode=1 installed=true
            |      disabledComponents:
            |        com.tecpal.device.mc30.SerialPortMC30Service
            |        com.tecpal.device.mc30.Autre
            |      enabledComponents:
            |        com.tecpal.device.mc30.Ok
        """.trimMargin()
        assertEquals(setOf("com.tecpal.device.mc30.SerialPortMC30Service", "com.tecpal.device.mc30.Autre"), Android.desactives(dump))
        assertEquals("app.toque.ui.Accueil", Android.classe("app.toque/.ui.Accueil"))
        assertEquals("com.x/.Main", Android.resolu("priority=0 preferredOrder=0 match=0x108000\ncom.x/.Main\n"))
        assertEquals(null, Android.resolu("No activity found\n"))
        assertEquals("com.x/.Main", Android.premierPlan("    mResumedActivity: ActivityRecord{5a1 u0 com.x/.Main t12}"))
    }
}
