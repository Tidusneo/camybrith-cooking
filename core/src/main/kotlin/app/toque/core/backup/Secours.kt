package app.toque.core.backup

import app.toque.core.json.Json
import app.toque.core.machine.RobotModel
import java.io.File
import java.io.IOException

/**
 * Moteur de la commande `camybrith` du module de secours (contracts/cli.md). Lancé en root sur le
 * robot par `app_process` (research.md D16), donc indépendant de l'interface : il marche même si
 * elle est cassée ou désinstallée. Tout accès au système passe par [Systeme], ce qui permet de le
 * tester sans robot.
 *
 * Codes : 0 succès, 1 refus attendu, 2 état inattendu (arrêt, rien de plus n'est fait).
 */
class Secours(
    private val sys: Systeme,
    private val racine: File,
    private val horloge: () -> Long = System::currentTimeMillis,
    private val sortie: (String) -> Unit = ::println,
) {
    interface Systeme {
        class Res(val code: Int, val out: String, val err: String = "") { val ok get() = code == 0 }

        fun run(vararg args: String): Res
        fun libre(): Long
        fun total(): Long
        fun pause(ms: Long) { Thread.sleep(ms) }
        /** Banc d'essai seulement : bloque ici jusqu'à la coupure de l'émulateur. */
        fun coupure() { while (true) Thread.sleep(60_000) }
    }

    class Arret(val code: Int, message: String) : Exception(message)

    private val registreF get() = File(racine, "registre.json")
    private val opF get() = File(racine, "op.json")
    private val modeF get() = File(racine, "mode.json")
    private val cuissonF get() = File(racine, "cuisson.json")
    private val robotF get() = File(racine, "robot.json")
    private val reserveF get() = File(racine, "reserve")
    private val bilansD get() = File(racine, "bilans")
    private var memoirePleine = false
    private var json = false

    fun main(args: Array<String>): Int = try {
        val a = args.toMutableList()
        json = a.remove("--json")
        when (a.firstOrNull()) {
            "etat" -> etat()
            "noter" -> noter(a.drop(1))
            "noter-fin" -> noterFin(a.drop(1))
            "inscrire" -> inscrire()
            "retour-lidl" -> {
                val origine = when {
                    "--secours" in a -> Origine.SECOURS
                    "--geste" in a -> Origine.GESTE
                    "--ordinateur" in a -> Origine.ORDINATEUR
                    else -> Origine.INTERFACE
                }
                retourLidl(origine, forcer = "--forcer" in a, redemarrer = "--sans-redemarrage" !in a)
            }
            "bilan" -> bilan(a.getOrNull(1))
            "demarrage" -> demarrage()
            "reprendre", "retrait", "sauvegarder" -> throw Arret(1, "« ${a[0]} » n'est pas encore disponible dans cette version du module.")
            else -> {
                sortie("Usage : camybrith etat|noter|noter-fin|inscrire|retour-lidl|bilan [--json]")
                1
            }
        }
    } catch (e: Arret) {
        if (json) sortie(Json.write(linkedMapOf("ok" to false, "code" to e.code, "erreur" to e.message)))
        else sortie((if (e.code == 2) "ÉTAT INATTENDU, rien n'a été fait : " else "Refusé : ") + e.message)
        e.code
    } catch (e: RegistreException) {
        if (json) sortie(Json.write(linkedMapOf("ok" to false, "code" to 2, "erreur" to e.message)))
        else sortie("ÉTAT INATTENDU, rien n'a été fait : ${e.message}")
        2
    }

    // ------------------------------------------------------------------ fichiers

    private fun preparer() {
        if (sys.run("id", "-u").out.trim() != "0") throw Arret(2, "la commande doit être lancée en root")
        if (!racine.isDirectory && !racine.mkdirs()) throw Arret(2, "impossible de créer $racine")
        sys.run("chmod", "700", racine.path)
    }

    /**
     * Écriture atomique (fichier temporaire puis renommage). Faute de place : la réserve est
     * supprimée et l'écriture réessayée une fois. Faux si elle échoue quand même (mémoire pleine).
     */
    private fun ecrire(f: File, texte: String): Boolean {
        fun essai(): Boolean = try {
            f.parentFile?.mkdirs()
            val tmp = File(f.parentFile, ".${f.name}.tmp")
            // Forcé sur le disque avant le renommage : une coupure de courant ne laisse jamais un
            // fichier vide ou à moitié écrit.
            java.io.FileOutputStream(tmp).use { o -> o.write(texte.toByteArray()); o.fd.sync() }
            if (!tmp.renameTo(f)) { tmp.delete(); false } else true
        } catch (_: IOException) {
            false
        }
        if (essai()) return true
        if (reserveF.delete() && essai()) { memoirePleine = true; return true }
        memoirePleine = true
        return false
    }

    private fun lire(f: File): String? = try { if (f.isFile) f.readText() else null } catch (_: IOException) { null }

    private fun registre(): Registre = lire(registreF)?.let { Registre.parse(it) } ?: Registre()

    private fun maintenant() = Iso.format(horloge())

    // ------------------------------------------------------------------ robot

    private fun installe(pkg: String) = sys.run("pm", "path", pkg).let { it.ok && it.out.contains("package:") }

    private fun robot(): RobotId? = RobotId.parse(lire(robotF)) ?: when {
        installe(RobotModel.SMART.vendorPackage) -> RobotId(RobotModel.SMART.vendorPackage, RobotModel.SMART.hardwareServiceSuffix)
        installe(RobotModel.CONNECT.vendorPackage) -> RobotId(RobotModel.CONNECT.vendorPackage, RobotModel.CONNECT.hardwareServiceSuffix)
        else -> null
    }

    private fun desactives(pkg: String): Set<String> = Android.desactives(sys.run("dumpsys", "package", pkg).out)

    /** Service moteur de l'appli Lidl actuellement désactivé, en `paquet/classe`, ou null. */
    private fun serviceLidlDesactive(r: RobotId): String? =
        desactives(r.vendorPackage).firstOrNull { it.endsWith(r.hardwareServiceSuffix) }?.let { "${r.vendorPackage}/$it" }

    private fun composantActif(cible: String): Boolean {
        val pkg = cible.substringBefore('/')
        if (!installe(pkg)) return false
        return Android.classe(cible) !in desactives(pkg)
    }

    private fun piloteActif() = sys.run("pidof", RobotId.PILOTE).out.isNotBlank()

    private fun banc(e: Etape) {
        if (File(racine, "banc").isFile && System.getenv("CAMYBRITH_BANC_ARRET") == e.cle) {
            ecrire(File(racine, "banc-arrete"), e.cle)
            sys.coupure()
        }
    }

    // ------------------------------------------------------------------ etat

    private fun etat(): Int {
        preparer()
        val reg = try { registre() } catch (e: RegistreException) { throw Arret(2, e.message ?: "registre illisible") }
        val mode = Mode.parse(lire(modeF))
        val libre = sys.libre()
        val total = sys.total()
        val op = lire(opF)
        val dernier = bilans().lastOrNull()
        val lidl = reg.planAnnulation(Portee.LIDL)
        if (json) {
            sortie(Json.write(linkedMapOf(
                "ok" to true, "mode" to if (mode.lidl) "lidl" else "camybrith", "depuis" to mode.depuis,
                "origine" to mode.origine?.cle, "libre" to libre, "total" to total,
                "alerte" to Space.alerte(libre, total), "reserve" to reserveF.isFile,
                "actives" to Portee.values().associate { it.cle to reg.actives(it).size },
                "lidl" to lidl.map { linkedMapOf("id" to it.id, "type" to it.type.cle, "cible" to it.cible, "avant" to it.avant) },
                "operation" to op?.let { try { Journal.parse(it).type.cle } catch (_: RegistreException) { "illisible" } },
                "dernierBilan" to dernier?.name,
            )))
            return 0
        }
        sortie("Mode du robot : ${if (mode.lidl) "appli Lidl depuis le ${mode.depuis} (${mode.origine?.cle ?: "?"})" else "Camybrith"}")
        sortie("Espace libre : ${Space.lisible(libre)} (${Space.pourcentLibre(libre, total)} %)" +
            if (Space.alerte(libre, total)) " : MÉMOIRE PRESQUE PLEINE" else "")
        sortie("Réserve de secours : ${if (reserveF.isFile) "présente" else "absente"}")
        sortie("Interface : ${version(RobotId.APP)} · pilote : ${version(RobotId.PILOTE)}")
        sortie("Modifications actives : " + Portee.values().joinToString(", ") { "${reg.actives(it).size} ${it.cle}" })
        lidl.forEach { sortie("  ${it.id}. ${it.libelle}") }
        if (op != null) sortie("Opération en cours : $op")
        sortie("Dernier bilan : ${dernier?.name ?: "aucun"}")
        return 0
    }

    private fun version(pkg: String): String =
        Regex("versionName=(\\S+)").find(sys.run("dumpsys", "package", pkg).out)?.groupValues?.get(1) ?: "absente"

    // ------------------------------------------------------------------ noter

    private fun noter(a: List<String>): Int {
        if (a.size != 6) throw Arret(1, "usage : noter <type> <cible> <avant> <apres> <portee> <origine>")
        val type = TypeModif.of(a[0]) ?: throw Arret(1, "type inconnu : ${a[0]}")
        val portee = Portee.of(a[4]) ?: throw Arret(1, "portée inconnue : ${a[4]}")
        Cibles.erreur(type, a[1])?.let { throw Arret(1, it) }
        preparer()
        val reg = registre()
        val (nouveau, id) = try {
            reg.noter(type, a[1], a[2], a[3], portee, a[5], maintenant())
        } catch (e: RegistreException) {
            throw Arret(1, e.message ?: "refusé")
        }
        if (!ecrire(registreF, nouveau.toJson())) throw Arret(1, "mémoire pleine : registre non écrit, ne pas faire la modification")
        sortie(if (json) Json.write(linkedMapOf("ok" to true, "id" to id)) else id.toString())
        return 0
    }

    private fun noterFin(a: List<String>): Int {
        val id = a.getOrNull(0)?.toIntOrNull() ?: throw Arret(1, "usage : noter-fin <id> ok|echec")
        val ok = when (a.getOrNull(1)) { "ok" -> true; "echec" -> false; else -> throw Arret(1, "usage : noter-fin <id> ok|echec") }
        preparer()
        val reg = try { registre().noterFin(id, ok) } catch (e: RegistreException) { throw Arret(1, e.message ?: "id inconnu") }
        if (!ecrire(registreF, reg.toJson())) throw Arret(1, "mémoire pleine : registre non écrit")
        if (json) sortie(Json.write(linkedMapOf("ok" to true)))
        return 0
    }

    // ------------------------------------------------------------------ inscrire (research.md D8)

    private fun inscrire(): Int {
        preparer()
        val existant = registre()
        if (existant.modifications.isNotEmpty()) throw Arret(1, "registre déjà initialisé")
        var reg = existant
        val date = maintenant()
        fun ajoute(type: TypeModif, cible: String, avant: String, apres: String, portee: Portee) {
            reg = reg.noter(type, cible, avant, apres, portee, "module", date, deduit = true).first
        }
        robot()?.let { r -> serviceLidlDesactive(r)?.let { ajoute(TypeModif.COMPOSANT, it, "enabled", "disabled", Portee.LIDL) } }
        if (installe(RobotId.APP)) {
            for (c in listOf(RobotId.BOOT_RECEIVER, RobotId.HOME_ALIAS))
                if (composantActif(c)) ajoute(TypeModif.DEMARRAGE, c, Annulation.ABSENT, "enabled", Portee.LIDL)
            ajoute(TypeModif.APPLI, RobotId.APP, Annulation.ABSENT, "installee", Portee.RETRAIT)
        }
        if (installe(RobotId.PILOTE)) ajoute(TypeModif.APPLI, RobotId.PILOTE, Annulation.ABSENT, "installee", Portee.RETRAIT)
        for (p in listOf(NAVBAR, HTTP_FILE_SERVER)) if (installe(p)) ajoute(TypeModif.APPLI, p, Annulation.ABSENT, "installee", Portee.RETRAIT)
        val access = sys.run("settings", "get", "secure", "enabled_accessibility_services").out.trim()
        if (access.contains(NAVBAR)) ajoute(TypeModif.REGLAGE, "secure/enabled_accessibility_services", Annulation.ABSENT, access, Portee.RETRAIT)
        if (File("/data/adb/modules/$ANIMATION").isDirectory) ajoute(TypeModif.MODULE, ANIMATION, Annulation.ABSENT, "installe", Portee.RETRAIT)
        if (installe(MAGISK)) ajoute(TypeModif.APPLI, MAGISK, Annulation.ABSENT, "installee", Portee.SOCLE)
        if (File("/data/adb/modules/$ADB_WIFI").isDirectory) ajoute(TypeModif.MODULE, ADB_WIFI, Annulation.ABSENT, "installe", Portee.SOCLE)
        if (!ecrire(registreF, reg.toJson())) throw Arret(1, "mémoire pleine : registre non écrit")
        if (!reserveF.isFile && Space.peutRecreerReserve(sys.libre())) creerReserve()
        sortie(if (json) Json.write(linkedMapOf("ok" to true, "inscrites" to reg.modifications.size))
        else "Registre initialisé : ${reg.modifications.size} modification(s) déduite(s).")
        reg.modifications.forEach { if (!json) sortie("  ${it.id}. [${it.portee.cle}] ${it.libelle}") }
        return 0
    }

    private fun creerReserve() {
        try {
            reserveF.outputStream().use { o -> val b = ByteArray(64 * 1024); repeat((Space.RESERVE_OCTETS / b.size).toInt()) { o.write(b) } }
        } catch (_: IOException) {
            reserveF.delete()
        }
    }

    // ------------------------------------------------------------------ retour à l'appli Lidl (D5, D6, D15)

    /**
     * Retour à l'appli Lidl. Le journal est tenté en premier mais ne bloque jamais : mémoire pleine,
     * le retour se fait sans lui (research.md D15, D16). [reprise] : fin d'un retour interrompu,
     * au démarrage (pas de refus, pas de redémarrage : l'appli Lidl est ouverte à la place).
     */
    fun retourLidl(origine: Origine, forcer: Boolean = false, redemarrer: Boolean = true, reprise: Boolean = false): Int {
        preparer()
        memoirePleine = false
        if (!reprise && opF.isFile) throw Arret(1, "une opération est déjà en cours (${lire(opF)})")
        if (!reprise && origine != Origine.SECOURS && origine != Origine.GESTE) {
            when (Cuisson.evaluer(lire(cuissonF), piloteActif(), horloge())) {
                Cuisson.Etat.EN_COURS -> throw Arret(1, "cuisson en cours (${Cuisson.programme(lire(cuissonF))}) : arrêtez d'abord le programme")
                Cuisson.Etat.INCONNU -> if (!forcer) throw Arret(1, "état de cuisson inconnu (interface arrêtée pendant un programme ?)")
                Cuisson.Etat.LIBRE -> {}
            }
        }
        var reg = try { registre() } catch (e: RegistreException) { null }
        val corrections = ArrayList<String>()
        if (reg == null) corrections += "registre illisible : seules les garanties essentielles ont été appliquées"

        var journal = Journal(TypeOperation.RETOUR_LIDL, origine, Etape.DEBUT, maintenant())
        ecrire(opF, journal.toJson())
        banc(Etape.DEBUT)

        sys.run("am", "force-stop", RobotId.APP)
        sys.run("am", "force-stop", RobotId.PILOTE)
        journal = journal.vers(Etape.ARRET); ecrire(opF, journal.toJson()); banc(Etape.ARRET)

        val annulees = ArrayList<Modification>()
        val echecs = LinkedHashMap<Int, String>()
        reg?.planAnnulation(Portee.LIDL)?.forEach { m ->
            val r = sys.run(*Annulation.commande(m).toTypedArray())
            if (Android.ok(r.code, r.out, r.err)) {
                annulees += m
                reg = reg!!.marquer(m.id, EtatModif.ANNULEE)
            } else {
                val raison = (r.err.ifBlank { r.out }).trim().take(200).ifEmpty { "code ${r.code}" }
                echecs[m.id] = raison
                reg = reg!!.marquer(m.id, EtatModif.ECHEC, raison)
            }
        }
        reg?.let { ecrire(registreF, it.toJson()) }
        journal = journal.vers(Etape.ANNULATION); ecrire(opF, journal.toJson()); banc(Etape.ANNULATION)

        val robot = robot()
        val controles = verifier(robot, corrections)
        journal = journal.vers(Etape.VERIFICATION); ecrire(opF, journal.toJson()); banc(Etape.VERIFICATION)

        ecrire(modeF, Mode(true, maintenant(), origine).toJson())
        val bilan = Bilan(TypeOperation.RETOUR_LIDL, maintenant(), origine, annulees, echecs, controles, corrections, memoirePleine)
        ecrireBilan(bilan)
        if (origine == Origine.SECOURS || origine == Origine.GESTE)
            ecrire(File(racine, "secours.json"), Json.write(linkedMapOf("date" to maintenant(), "origine" to origine.cle, "version" to version(RobotId.APP))))
        journal = journal.vers(Etape.TERMINE); ecrire(opF, journal.toJson()); banc(Etape.TERMINE)
        opF.delete()

        sortie(if (json) Json.write(linkedMapOf("ok" to bilan.reussi, "bilan" to Bilan.nomFichier(bilan.date))) else bilan.texte())
        if (reprise) ouvrirLidl(robot)
        else if (redemarrer) {
            if (!json) sortie("Redémarrage du robot…")
            sys.run("sync")
            // Redémarrage propre (PowerManager) : Android écrit l'état des composants avant de
            // s'éteindre, ce que la commande « reboot » brute ne garantit pas.
            if (!sys.run("svc", "power", "reboot").ok) sys.run("reboot")
        }
        return 0
    }

    /** Vérification finale (research.md D6), avec garanties : l'essentiel est corrigé même hors registre. */
    private fun verifier(robot: RobotId?, corrections: MutableList<String>): List<Controle> {
        val c = ArrayList<Controle>()
        if (robot == null) {
            c += Controle("appli Lidl installée", false, "aucune appli Lidl trouvée")
        } else {
            serviceLidlDesactive(robot)?.let { svc ->
                if (sys.run("pm", "enable", svc).let { Android.ok(it.code, it.out, it.err) }) corrections += "service moteur Lidl réactivé ($svc)"
            }
            c += Controle("service moteur Lidl activé", serviceLidlDesactive(robot) == null)
            val lanceur = Android.resolu(sys.run("cmd", "package", "resolve-activity", "--brief",
                "-a", "android.intent.action.MAIN", "-c", "android.intent.category.LAUNCHER", robot.vendorPackage).out)
            c += Controle("appli Lidl lançable", lanceur?.startsWith(robot.vendorPackage + "/") == true, lanceur ?: "aucune activité")
        }
        if (installe(RobotId.APP)) {
            for ((cible, nom) in listOf(RobotId.BOOT_RECEIVER to "démarrage de Camybrith désactivé",
                RobotId.HOME_ALIAS to "Camybrith n'est plus l'écran d'accueil")) {
                if (composantActif(cible)) {
                    val r = sys.run("pm", "disable", cible)
                    if (Android.ok(r.code, r.out, r.err)) corrections += "$cible désactivé"
                }
                val actif = composantActif(cible)
                c += Controle(nom, !actif, if (actif) "toujours actif" else "")
            }
        }
        val home = Android.resolu(sys.run("cmd", "package", "resolve-activity", "--brief",
            "-a", "android.intent.action.MAIN", "-c", "android.intent.category.HOME").out)
        c += when {
            robot != null && home?.startsWith(robot.vendorPackage + "/") == true -> Controle("écran d'accueil → appli Lidl", true, home)
            home?.startsWith(RobotId.APP + "/") == true -> Controle("écran d'accueil → appli Lidl", false, "toujours Camybrith : $home")
            else -> Controle("écran d'accueil → appli Lidl", true, "${home ?: "aucun"} : l'appli Lidl sera ouverte au démarrage par le module")
        }
        var vivants = ""
        for (i in 0 until 6) {
            vivants = listOf(RobotId.APP, RobotId.PILOTE).filter { sys.run("pidof", it).out.isNotBlank() }.joinToString()
            if (vivants.isEmpty()) break
            sys.run("am", "force-stop", RobotId.APP); sys.run("am", "force-stop", RobotId.PILOTE)
            sys.pause(500)
        }
        c += Controle("processus Camybrith arrêtés", vivants.isEmpty(), vivants)
        return c
    }

    private fun ouvrirLidl(robot: RobotId?) {
        sys.run("am", "start", "-a", "android.intent.action.MAIN", "-c", "android.intent.category.HOME")
        if (robot != null && Android.premierPlan(sys.run("dumpsys", "activity", "activities").out)?.startsWith(robot.vendorPackage) != true)
            sys.run("monkey", "-p", robot.vendorPackage, "-c", "android.intent.category.LAUNCHER", "1")
    }

    private fun bilans(): List<File> = (bilansD.listFiles { f -> f.name.endsWith(".json") } ?: emptyArray()).sortedBy { it.name }

    private fun ecrireBilan(b: Bilan) {
        if (!ecrire(File(bilansD, Bilan.nomFichier(b.date)), b.toJson())) return
        Bilan.aSupprimer(bilans().map { it.name }).forEach { File(bilansD, it).delete() }
    }

    private fun bilan(nom: String?): Int {
        val f = if (nom == null) bilans().lastOrNull() else File(bilansD, if (nom.endsWith(".json")) nom else "$nom.json")
        if (f == null || !f.isFile || f.parentFile != bilansD) throw Arret(1, "aucun bilan${nom?.let { " nommé $it" } ?: ""}")
        val texte = lire(f) ?: throw Arret(1, "bilan illisible")
        sortie(if (json) texte.replace("\n", "") else Bilan.parse(texte).texte())
        return 0
    }

    // ------------------------------------------------------------------ au démarrage (service.sh)

    /**
     * Appelé par `service.sh` après `sys.boot_completed` : réserve, reprise d'une opération
     * interrompue (journal non terminé → retour à l'appli Lidl terminé), puis contrôle après un
     * retour : l'appli Lidl est-elle au premier plan ?
     */
    private fun demarrage(): Int {
        preparer()
        if (!reserveF.isFile && Space.peutRecreerReserve(sys.libre())) creerReserve()
        lire(opF)?.let { texte ->
            val j = try { Journal.parse(texte) } catch (_: RegistreException) { null }
            when (j?.repriseAuDemarrage() ?: ActionDemarrage.TERMINER_RETOUR) {
                ActionDemarrage.EFFACER -> opF.delete()
                else -> {
                    opF.delete()
                    retourLidl(j?.origine ?: Origine.SECOURS, redemarrer = false, reprise = true)
                }
            }
        }
        val mode = Mode.parse(lire(modeF))
        // En mode Lidl, l'essentiel est revérifié à chaque démarrage (une coupure juste après un
        // retour peut avoir fait perdre à Android l'état des composants) et corrigé si besoin.
        if (mode.lidl) {
            val corrections = ArrayList<String>()
            verifier(robot(), corrections)
            if (corrections.isNotEmpty()) {
                if (!json) corrections.forEach { sortie("Corrigé au démarrage : $it") }
                ecrire(File(racine, "corrections-demarrage.json"), Json.write(linkedMapOf("date" to maintenant(), "corrections" to corrections)))
            }
        }
        val dernier = bilans().lastOrNull()
        if (mode.lidl && dernier != null) {
            val b = try { Bilan.parse(lire(dernier) ?: "") } catch (_: RegistreException) { null }
            if (b != null && b.apresDemarrage == null) {
                val robot = robot()
                var devant: String? = null
                for (i in 0 until 30) {
                    devant = Android.premierPlan(sys.run("dumpsys", "activity", "activities").out)
                    if (devant != null) break
                    sys.pause(2_000)
                }
                if (robot != null && devant?.startsWith(robot.vendorPackage) != true) {
                    ouvrirLidl(robot)
                    sys.pause(3_000)
                    devant = Android.premierPlan(sys.run("dumpsys", "activity", "activities").out)
                }
                val ok = robot != null && devant?.startsWith(robot.vendorPackage) == true
                ecrire(dernier, b.copy(apresDemarrage = Controle("appli Lidl au premier plan", ok, devant ?: "rien")).toJson())
            }
        }
        if (!json) sortie("Démarrage traité.")
        return 0
    }

    companion object {
        const val NAVBAR = "nu.nav.bar"
        const val HTTP_FILE_SERVER = "slowscript.httpfileserver"
        const val MAGISK = "com.topjohnwu.magisk"
        const val ANIMATION = "camybrith_boot"
        const val ADB_WIFI = "toque_adb_wifi"
    }
}

/** Lecture des sorties des commandes Android (fonctions pures, testées). */
object Android {
    /** `pm` répond parfois « Error: … » avec le code 0 : les deux sont vérifiés. */
    fun ok(code: Int, out: String, err: String): Boolean =
        code == 0 && !out.contains("Error:") && !err.contains("Error:") && !err.contains("Exception")

    /** Classes listées sous « disabledComponents: » dans `dumpsys package <paquet>`. */
    fun desactives(dumpsys: String): Set<String> {
        val res = HashSet<String>()
        var niveau = -1
        for (ligne in dumpsys.lines()) {
            val ind = ligne.length - ligne.trimStart().length
            val t = ligne.trim()
            if (niveau >= 0) {
                if (t.isEmpty() || ind <= niveau) niveau = -1 else { res += t; continue }
            }
            if (t == "disabledComponents:") niveau = ind
        }
        return res
    }

    /** `paquet/classe` → nom complet de la classe (« paquet/.X » → « paquet.X »). */
    fun classe(cible: String): String {
        val pkg = cible.substringBefore('/')
        val c = cible.substringAfter('/')
        return if (c.startsWith(".")) pkg + c else c
    }

    /** Composant rendu par `cmd package resolve-activity --brief` (dernière ligne `paquet/classe`). */
    fun resolu(sortie: String): String? =
        sortie.lines().map { it.trim() }.lastOrNull { it.contains('/') && !it.contains(' ') }

    /** Activité au premier plan d'après `dumpsys activity activities`. */
    fun premierPlan(dumpsys: String): String? =
        Regex("mResumedActivity: ActivityRecord\\{\\S+ \\S+ (\\S+)").find(dumpsys)?.groupValues?.get(1)
}

/** Point d'entrée lancé par `app_process` depuis le module de secours (research.md D16). */
object SecoursMain {
    @JvmStatic
    fun main(args: Array<String>) {
        val racine = File(System.getenv("CAMYBRITH_RACINE") ?: "/data/adb/camybrith")
        val sys = object : Secours.Systeme {
            override fun run(vararg args: String): Secours.Systeme.Res = try {
                val p = ProcessBuilder(*args).start()
                p.outputStream.close()
                val err = StringBuilder()
                val te = Thread { err.append(p.errorStream.bufferedReader().readText()) }.apply { start() }
                val out = p.inputStream.bufferedReader().readText()
                te.join()
                Secours.Systeme.Res(p.waitFor(), out, err.toString())
            } catch (e: IOException) {
                Secours.Systeme.Res(-1, "", e.message ?: "erreur")
            }
            override fun libre() = File("/data").usableSpace
            override fun total() = File("/data").totalSpace
        }
        System.exit(Secours(sys, racine).main(args))
    }
}
