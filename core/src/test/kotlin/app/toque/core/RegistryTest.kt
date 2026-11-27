package app.toque.core

import app.toque.core.backup.ActionDemarrage
import app.toque.core.backup.Annulation
import app.toque.core.backup.Bilan
import app.toque.core.backup.Cuisson
import app.toque.core.backup.Etape
import app.toque.core.backup.EtatModif
import app.toque.core.backup.Iso
import app.toque.core.backup.Journal
import app.toque.core.backup.Mode
import app.toque.core.backup.Origine
import app.toque.core.backup.Portee
import app.toque.core.backup.Registre
import app.toque.core.backup.RegistreException
import app.toque.core.backup.RobotId
import app.toque.core.backup.TypeModif
import app.toque.core.backup.TypeOperation
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

    private fun refuse(bloc: () -> Unit) {
        try { bloc(); fail("aurait dû être refusé") } catch (_: RegistreException) {}
    }

    @Test fun ciblesInvalidesRefusees() {
        val r = Registre()
        refuse { r.noter(TypeModif.COMPOSANT, "pas-de-barre", "enabled", "disabled", Portee.LIDL, "interface", d) }
        refuse { r.noter(TypeModif.COMPOSANT, "com.x/; rm -rf /", "enabled", "disabled", Portee.LIDL, "interface", d) }
        refuse { r.noter(TypeModif.REGLAGE, "inconnu/cle", "a", "b", Portee.RETRAIT, "barre.sh", d) }
        refuse { r.noter(TypeModif.APPLI, "pasunpaquet", "absent", "installee", Portee.RETRAIT, "installer.sh", d) }
        refuse { r.noter(TypeModif.MODULE, "../etc", "absent", "installe", Portee.RETRAIT, "animation.sh", d) }
        refuse { r.noter(TypeModif.REGLAGE, "secure/x", "a", "b\nc", Portee.RETRAIT, "barre.sh", d) }
        r.noter(TypeModif.REGLAGE, "secure/enabled_accessibility_services", "absent", "nu.nav.bar/nu.nav.bar.service.NavigationBarService", Portee.RETRAIT, "barre.sh", d)
    }

    @Test fun fusionGardeEtatOrigine() {
        var (r, id1) = Registre().noter(TypeModif.COMPOSANT, svc, "enabled", "disabled", Portee.LIDL, "interface", d)
        val (r2, id2) = r.noter(TypeModif.COMPOSANT, svc, "disabled", "disabled", Portee.LIDL, "interface", "2026-11-28T10:00:00+01:00")
        assertEquals(id1, id2)
        assertEquals(1, r2.modifications.size)
        assertEquals("enabled", r2.modifications[0].avant)
        assertEquals("2026-11-28T10:00:00+01:00", r2.modifications[0].date)
        // Une fois annulée, une nouvelle inscription repart d'une nouvelle entrée.
        r = r2.marquer(id1, EtatModif.ANNULEE)
        val (r3, id3) = r.noter(TypeModif.COMPOSANT, svc, "enabled", "disabled", Portee.LIDL, "interface", d)
        assertTrue(id3 > id1)
        assertEquals(2, r3.modifications.size)
    }

    @Test fun noterFinEchecRetireUneNouvelle() {
        val (r, id) = Registre().noter(TypeModif.COMPOSANT, svc, "enabled", "disabled", Portee.LIDL, "interface", d)
        assertTrue(r.noterFin(id, false).modifications.isEmpty())
        val ok = r.noterFin(id, true)
        assertTrue(ok.modifications[0].confirme)
        // Fusion sur une modification confirmée : un échec ne retire pas l'état d'origine.
        val (r2, id2) = ok.noter(TypeModif.COMPOSANT, svc, "disabled", "disabled", Portee.LIDL, "interface", d)
        assertEquals(1, r2.noterFin(id2, false).modifications.size)
        refuse { r.noterFin(99, true) }
    }

    @Test fun annulationOrdreDecroissantEtPortees() {
        var r = Registre()
        r = r.noter(TypeModif.APPLI, "app.toque", "absent", "installee", Portee.RETRAIT, "installer.sh", d).first
        r = r.noter(TypeModif.DEMARRAGE, RobotId.BOOT_RECEIVER, "absent", "enabled", Portee.LIDL, "installer.sh", d).first
        r = r.noter(TypeModif.DEMARRAGE, RobotId.HOME_ALIAS, "absent", "enabled", Portee.LIDL, "installer.sh", d).first
        r = r.noter(TypeModif.COMPOSANT, svc, "enabled", "disabled", Portee.LIDL, "interface", d).first
        r = r.noter(TypeModif.APPLI, "com.topjohnwu.magisk", "absent", "installee", Portee.SOCLE, "guide", d).first
        val plan = r.planAnnulation(Portee.LIDL)
        assertEquals(listOf(4, 3, 2), plan.map { it.id })
        assertTrue(plan.none { it.portee != Portee.LIDL })
        assertEquals(listOf("pm", "enable", svc), Annulation.commande(plan[0]))
        assertEquals(listOf("pm", "disable", RobotId.HOME_ALIAS), Annulation.commande(plan[1]))
        // Réapplication : ordre croissant, seulement ce que le dernier retour a annulé.
        val annule = plan.fold(r) { acc, m -> acc.marquer(m.id, EtatModif.ANNULEE) }
        assertEquals(listOf(2, 4), annule.planReapplication(listOf(4, 2)).map { it.id })
        assertEquals(listOf("pm", "disable", svc), Annulation.reapplication(annule.modifications.first { it.id == 4 }))
    }

    @Test fun reglageAnnule() {
        val r = Registre().noter(TypeModif.REGLAGE, "secure/enabled_accessibility_services", "absent", "x/y.Z", Portee.RETRAIT, "barre.sh", d).first
        assertEquals(listOf("settings", "delete", "secure", "enabled_accessibility_services"), Annulation.commande(r.modifications[0]))
        val r2 = Registre().noter(TypeModif.REGLAGE, "secure/accessibility_enabled", "0", "1", Portee.RETRAIT, "barre.sh", d).first
        assertEquals(listOf("settings", "put", "secure", "accessibility_enabled", "0"), Annulation.commande(r2.modifications[0]))
    }

    @Test fun allerRetourJsonEtLectureStricte() {
        val r = Registre("abc").noter(TypeModif.COMPOSANT, svc, "enabled", "disabled", Portee.LIDL, "interface", d).first
        assertEquals(r, Registre.parse(r.toJson()))
        refuse { Registre.parse("""{"format":2,"modifications":[]}""") }
        refuse { Registre.parse("""{"format":1,"modifications":[{"id":1,"type":"commande","cible":"rm -rf /","apres":"x","portee":"lidl","etat":"active"}]}""") }
        refuse { Registre.parse("pas du json") }
    }

    @Test fun transitionsDuJournal() {
        var j = Journal(TypeOperation.RETOUR_LIDL, Origine.INTERFACE)
        j = j.vers(Etape.ARRET).vers(Etape.ANNULATION).vers(Etape.VERIFICATION).vers(Etape.TERMINE)
        assertEquals(Etape.TERMINE, j.etape)
        refuse { Journal(TypeOperation.RETOUR_LIDL, Origine.INTERFACE).vers(Etape.ANNULATION) }
        refuse { Journal(TypeOperation.RETOUR_LIDL, Origine.INTERFACE).vers(Etape.REAPPLICATION) }
        refuse { Journal(TypeOperation.REPRISE, Origine.INTERFACE).vers(Etape.ARRET) }
        assertEquals(j, Journal.parse(j.toJson()))
    }

    @Test fun repriseQuiTermineUnRetour() {
        for (e in listOf(Etape.DEBUT, Etape.ARRET, Etape.ANNULATION, Etape.VERIFICATION))
            assertEquals(ActionDemarrage.TERMINER_RETOUR, Journal(TypeOperation.RETOUR_LIDL, Origine.GESTE, e).repriseAuDemarrage())
        assertEquals(ActionDemarrage.EFFACER, Journal(TypeOperation.RETOUR_LIDL, Origine.GESTE, Etape.TERMINE).repriseAuDemarrage())
        assertEquals(ActionDemarrage.RETOUR_LIDL, Journal(TypeOperation.REPRISE, Origine.INTERFACE, Etape.REAPPLICATION).repriseAuDemarrage())
    }

    @Test fun bilansGardes() {
        val noms = (1..13).map { "202611%02d-100000.json".format(it) }
        assertEquals(noms.take(3), Bilan.aSupprimer(noms.shuffled()))
        assertEquals("20261127-100000.json", Bilan.nomFichier(d))
        val b = Bilan(TypeOperation.RETOUR_LIDL, d, Origine.INTERFACE, echecs = mapOf(3 to "refusé"))
        val relu = Bilan.parse(b.toJson())
        assertFalse(relu.reussi)
        assertEquals("refusé", relu.echecs[3])
        assertTrue(b.toJson().contains(Bilan.RAPPEL))
    }

    @Test fun datesIso() {
        val ms = Iso.parse(d)!!
        assertEquals(d, Iso.format(ms, TimeZone.getTimeZone("Europe/Paris")))
        assertEquals(ms, Iso.parse("2026-11-27T09:00:00Z"))
        assertNull(Iso.parse("hier"))
    }

    @Test fun etatDeCuisson() {
        val t = Iso.parse(d)!!
        val occupe = Cuisson.json(true, "Risotto", d)
        assertEquals(Cuisson.Etat.EN_COURS, Cuisson.evaluer(occupe, true, t + 30_000))
        assertEquals(Cuisson.Etat.INCONNU, Cuisson.evaluer(occupe, true, t + 90_000))
        assertEquals(Cuisson.Etat.LIBRE, Cuisson.evaluer(occupe, false, t + 30_000))
        assertEquals(Cuisson.Etat.LIBRE, Cuisson.evaluer(Cuisson.json(false, "", d), true, t + 3_600_000))
        assertEquals(Cuisson.Etat.INCONNU, Cuisson.evaluer(null, true, t))
        assertEquals(Cuisson.Etat.INCONNU, Cuisson.evaluer("{", true, t))
    }

    @Test fun modeEtIdentite() {
        assertFalse(Mode.parse(null).lidl)
        assertFalse(Mode.parse("abîmé").lidl)
        val m = Mode(true, d, Origine.GESTE)
        assertEquals(m, Mode.parse(m.toJson()))
        val id = RobotId("com.tecpal.device.mc30", "SerialPortMC30Service")
        assertEquals(id, RobotId.parse(id.toJson()))
        assertNull(RobotId.parse("""{"vendorPackage":"x; reboot","hardwareServiceSuffix":"S"}"""))
    }
}
