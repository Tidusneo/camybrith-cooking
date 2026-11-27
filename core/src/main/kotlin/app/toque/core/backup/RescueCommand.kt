package app.toque.core.backup

import app.toque.core.json.Json
import app.toque.core.machine.RobotModel
import java.io.File
import java.io.IOException

/**
 * Engine of the rescue module's `camybrith` command (contracts/cli.md). Run as root on the robot by
 * `app_process` (research.md D16), so it does not depend on the interface: it works even if the
 * interface is broken or uninstalled. Every system access goes through [Device], so it can be
 * tested without a robot.
 *
 * Exit codes: 0 success, 1 expected refusal, 2 unexpected state (stop, nothing more is done).
 * Messages are in French: they are shown to the user.
 */
class RescueCommand(
    private val device: Device,
    private val root: File,
    private val clock: () -> Long = System::currentTimeMillis,
    private val out: (String) -> Unit = ::println,
) {
    interface Device {
        class Result(val code: Int, val out: String, val err: String = "") { val ok get() = code == 0 }

        fun run(vararg args: String): Result
        fun freeBytes(): Long
        fun totalBytes(): Long
        fun pause(ms: Long) { Thread.sleep(ms) }
        /** Test bench only: blocks here until the emulator is cut off. */
        fun benchCut() { while (true) Thread.sleep(60_000) }
    }

    class Stop(val code: Int, message: String) : Exception(message)

    // On-disk names are defined by data-model.md.
    private val registryFile get() = File(root, "registre.json")
    private val journalFile get() = File(root, "op.json")
    private val modeFile get() = File(root, "mode.json")
    private val cookingFile get() = File(root, "cuisson.json")
    private val robotFile get() = File(root, "robot.json")
    private val reserveFile get() = File(root, "reserve")
    private val gestureFile get() = File(root, "demarrages-courts")
    private val reportsDir get() = File(root, "bilans")
    private var storageFull = false
    private var json = false

    fun main(args: Array<String>): Int = try {
        val a = args.toMutableList()
        json = a.remove("--json")
        when (a.firstOrNull()) {
            "etat" -> status()
            "noter" -> note(a.drop(1))
            "noter-fin" -> noteDone(a.drop(1))
            "inscrire" -> enrol()
            "retour-lidl" -> {
                val origin = when {
                    "--secours" in a -> Origin.RESCUE
                    "--geste" in a -> Origin.GESTURE
                    "--ordinateur" in a -> Origin.COMPUTER
                    else -> Origin.INTERFACE
                }
                backToLidl(origin, force = "--forcer" in a, reboot = "--sans-redemarrage" !in a)
            }
            "bilan" -> report(a.getOrNull(1))
            "demarrage" -> boot()
            "surveiller" -> watch(a.getOrNull(1)?.toLongOrNull() ?: throw Stop(1, "usage : surveiller <instant du démarrage, en s>"))
            "reprendre" -> resume(origin = if ("--ordinateur" in a) Origin.COMPUTER else Origin.INTERFACE)
            "retrait" -> remove(withNavBar = "--avec-barre" in a, withFileServer = "--avec-serveur" in a)
            "sauvegarder" -> backup()
            else -> {
                out("Usage : camybrith etat|noter|noter-fin|inscrire|retour-lidl|bilan [--json]")
                1
            }
        }
    } catch (e: Stop) {
        if (json) out(Json.write(linkedMapOf("ok" to false, "code" to e.code, "erreur" to e.message)))
        else out((if (e.code == 2) "ÉTAT INATTENDU, rien n'a été fait : " else "Refusé : ") + e.message)
        e.code
    } catch (e: RegistryException) {
        if (json) out(Json.write(linkedMapOf("ok" to false, "code" to 2, "erreur" to e.message)))
        else out("ÉTAT INATTENDU, rien n'a été fait : ${e.message}")
        2
    }

    // ------------------------------------------------------------------ files

    private fun prepare() {
        if (device.run("id", "-u").out.trim() != "0") throw Stop(2, "la commande doit être lancée en root")
        if (!root.isDirectory && !root.mkdirs()) throw Stop(2, "impossible de créer $root")
        device.run("chmod", "700", root.path)
    }

    /**
     * Atomic write (temporary file then rename). Out of space: the reserve is deleted and the write
     * retried once. False if it still fails (storage full).
     */
    private fun write(f: File, text: String): Boolean {
        fun attempt(): Boolean = try {
            f.parentFile?.mkdirs()
            val tmp = File(f.parentFile, ".${f.name}.tmp")
            // Synced to disk before the rename: a power cut never leaves an empty or half-written file.
            java.io.FileOutputStream(tmp).use { o -> o.write(text.toByteArray()); o.fd.sync() }
            if (!tmp.renameTo(f)) { tmp.delete(); false } else true
        } catch (_: IOException) {
            false
        }
        if (attempt()) return true
        if (reserveFile.delete() && attempt()) { storageFull = true; return true }
        storageFull = true
        return false
    }

    private fun read(f: File): String? = try { if (f.isFile) f.readText() else null } catch (_: IOException) { null }

    private fun registry(): Registry = read(registryFile)?.let { Registry.parse(it) } ?: Registry()

    private fun now() = Iso.format(clock())

    // ------------------------------------------------------------------ robot

    private fun installed(pkg: String) = device.run("pm", "path", pkg).let { it.ok && it.out.contains("package:") }

    private fun robot(): RobotId? = RobotId.parse(read(robotFile)) ?: when {
        installed(RobotModel.SMART.vendorPackage) -> RobotId(RobotModel.SMART.vendorPackage, RobotModel.SMART.hardwareServiceSuffix)
        installed(RobotModel.CONNECT.vendorPackage) -> RobotId(RobotModel.CONNECT.vendorPackage, RobotModel.CONNECT.hardwareServiceSuffix)
        else -> null
    }

    private fun disabled(pkg: String): Set<String> = AndroidOutput.disabledComponents(device.run("dumpsys", "package", pkg).out)

    /** Lidl app motor service currently disabled, as `package/class`, or null. */
    private fun disabledLidlService(r: RobotId): String? =
        disabled(r.vendorPackage).firstOrNull { it.endsWith(r.hardwareServiceSuffix) }?.let { "${r.vendorPackage}/$it" }

    private fun componentEnabled(target: String): Boolean {
        val pkg = target.substringBefore('/')
        if (!installed(pkg)) return false
        return AndroidOutput.className(target) !in disabled(pkg)
    }

    private fun pilotRunning() = device.run("pidof", RobotId.PILOT).out.isNotBlank()

    /** Test bench hook: with the `bench` marker file, stops dead at the requested step. */
    private fun benchHook(step: Step) {
        if (File(root, "bench").isFile && System.getenv("CAMYBRITH_BENCH_STOP") == step.key) {
            write(File(root, "bench-stopped"), step.key)
            device.benchCut()
        }
    }

    // ------------------------------------------------------------------ etat

    private fun status(): Int {
        prepare()
        val reg = try { registry() } catch (e: RegistryException) { throw Stop(2, e.message ?: "registre illisible") }
        val mode = Mode.parse(read(modeFile))
        val free = device.freeBytes()
        val total = device.totalBytes()
        val op = read(journalFile)
        val last = reports().lastOrNull()
        val lidl = reg.undoPlan(Scope.LIDL)
        if (json) {
            out(Json.write(linkedMapOf(
                "ok" to true, "mode" to if (mode.lidl) "lidl" else "camybrith", "depuis" to mode.since,
                "origine" to mode.origin?.key, "libre" to free, "total" to total,
                "alerte" to Space.isLow(free, total), "reserve" to reserveFile.isFile,
                "actives" to Scope.values().associate { it.key to reg.active(it).size },
                "lidl" to lidl.map { linkedMapOf("id" to it.id, "type" to it.type.key, "cible" to it.target, "avant" to it.before) },
                "operation" to op?.let { try { Journal.parse(it).type.key } catch (_: RegistryException) { "illisible" } },
                "dernierBilan" to last?.name,
            )))
            return 0
        }
        out("Mode du robot : ${if (mode.lidl) "appli Lidl depuis le ${mode.since} (${mode.origin?.key ?: "?"})" else "Camybrith"}")
        out("Espace libre : ${Space.readable(free)} (${Space.percentFree(free, total)} %)" +
            if (Space.isLow(free, total)) " : MÉMOIRE PRESQUE PLEINE" else "")
        out("Réserve de secours : ${if (reserveFile.isFile) "présente" else "absente"}")
        out("Interface : ${version(RobotId.APP)} · pilote : ${version(RobotId.PILOT)}")
        out("Modifications actives : " + Scope.values().joinToString(", ") { "${reg.active(it).size} ${it.key}" })
        lidl.forEach { out("  ${it.id}. ${it.label}") }
        if (op != null) out("Opération en cours : $op")
        out("Dernier bilan : ${last?.name ?: "aucun"}")
        return 0
    }

    private fun version(pkg: String): String =
        Regex("versionName=(\\S+)").find(device.run("dumpsys", "package", pkg).out)?.groupValues?.get(1) ?: "absente"

    // ------------------------------------------------------------------ noter

    private fun note(a: List<String>): Int {
        if (a.size != 6) throw Stop(1, "usage : noter <type> <cible> <avant> <apres> <portee> <origine>")
        val type = ChangeType.of(a[0]) ?: throw Stop(1, "type inconnu : ${a[0]}")
        val scope = Scope.of(a[4]) ?: throw Stop(1, "portée inconnue : ${a[4]}")
        Targets.error(type, a[1])?.let { throw Stop(1, it) }
        prepare()
        val reg = registry()
        val (updated, id) = try {
            reg.note(type, a[1], a[2], a[3], scope, a[5], now())
        } catch (e: RegistryException) {
            throw Stop(1, e.message ?: "refusé")
        }
        if (!write(registryFile, updated.toJson())) throw Stop(1, "mémoire pleine : registre non écrit, ne pas faire la modification")
        out(if (json) Json.write(linkedMapOf("ok" to true, "id" to id)) else id.toString())
        return 0
    }

    private fun noteDone(a: List<String>): Int {
        val id = a.getOrNull(0)?.toIntOrNull() ?: throw Stop(1, "usage : noter-fin <id> ok|echec")
        val ok = when (a.getOrNull(1)) { "ok" -> true; "echec" -> false; else -> throw Stop(1, "usage : noter-fin <id> ok|echec") }
        prepare()
        val reg = try { registry().noteDone(id, ok) } catch (e: RegistryException) { throw Stop(1, e.message ?: "id inconnu") }
        if (!write(registryFile, reg.toJson())) throw Stop(1, "mémoire pleine : registre non écrit")
        if (json) out(Json.write(linkedMapOf("ok" to true)))
        return 0
    }

    // ------------------------------------------------------------------ inscrire (research.md D8)

    /** Retroactive enrolment: records what is already modified, without changing anything. */
    private fun enrol(): Int {
        prepare()
        val existing = registry()
        if (existing.changes.isNotEmpty()) throw Stop(1, "registre déjà initialisé")
        var reg = existing
        val date = now()
        fun add(type: ChangeType, target: String, before: String, after: String, scope: Scope) {
            reg = reg.note(type, target, before, after, scope, "module", date, inferred = true).first
        }
        robot()?.let { r -> disabledLidlService(r)?.let { add(ChangeType.COMPONENT, it, "enabled", "disabled", Scope.LIDL) } }
        if (installed(RobotId.APP)) {
            for (c in listOf(RobotId.BOOT_RECEIVER, RobotId.HOME_ALIAS))
                if (componentEnabled(c)) add(ChangeType.STARTUP, c, Undo.ABSENT, "enabled", Scope.LIDL)
            add(ChangeType.APP, RobotId.APP, Undo.ABSENT, "installee", Scope.REMOVAL)
        }
        if (installed(RobotId.PILOT)) add(ChangeType.APP, RobotId.PILOT, Undo.ABSENT, "installee", Scope.REMOVAL)
        for (p in listOf(NAVBAR, HTTP_FILE_SERVER)) if (installed(p)) add(ChangeType.APP, p, Undo.ABSENT, "installee", Scope.REMOVAL)
        val access = device.run("settings", "get", "secure", "enabled_accessibility_services").out.trim()
        if (access.contains(NAVBAR)) add(ChangeType.SETTING, "secure/enabled_accessibility_services", Undo.ABSENT, access, Scope.REMOVAL)
        if (File("/data/adb/modules/$ANIMATION").isDirectory) add(ChangeType.MODULE, ANIMATION, Undo.ABSENT, "installe", Scope.REMOVAL)
        if (installed(MAGISK)) add(ChangeType.APP, MAGISK, Undo.ABSENT, "installee", Scope.BASE)
        if (File("/data/adb/modules/$ADB_WIFI").isDirectory) add(ChangeType.MODULE, ADB_WIFI, Undo.ABSENT, "installe", Scope.BASE)
        if (!write(registryFile, reg.toJson())) throw Stop(1, "mémoire pleine : registre non écrit")
        if (!reserveFile.isFile && Space.canRecreateReserve(device.freeBytes())) createReserve()
        out(if (json) Json.write(linkedMapOf("ok" to true, "inscrites" to reg.changes.size))
        else "Registre initialisé : ${reg.changes.size} modification(s) déduite(s).")
        reg.changes.forEach { if (!json) out("  ${it.id}. [${it.scope.key}] ${it.label}") }
        return 0
    }

    private fun createReserve() {
        try {
            reserveFile.outputStream().use { o -> val b = ByteArray(64 * 1024); repeat((Space.RESERVE_BYTES / b.size).toInt()) { o.write(b) } }
        } catch (_: IOException) {
            reserveFile.delete()
        }
    }

    // ------------------------------------------------------------------ back to the Lidl app (D5, D6, D15)

    /**
     * Back to the Lidl app. The journal is written first but never blocks: when storage is full
     * the return goes on without it (research.md D15, D16). [resuming]: finishing an interrupted
     * return at boot (no refusal, no restart: the Lidl app is opened instead).
     */
    fun backToLidl(
        origin: Origin, force: Boolean = false, reboot: Boolean = true, resuming: Boolean = false,
        /** No restart: the Lidl app is opened directly (rescue, gesture, resume). */
        openLidl: Boolean = resuming,
        crashes: Int = 0,
    ): Int {
        prepare()
        storageFull = false
        if (!resuming && journalFile.isFile) throw Stop(1, "une opération est déjà en cours (${read(journalFile)})")
        if (!resuming && origin != Origin.RESCUE && origin != Origin.GESTURE) {
            when (Cooking.evaluate(read(cookingFile), pilotRunning(), clock())) {
                Cooking.State.RUNNING -> throw Stop(1, "cuisson en cours (${Cooking.program(read(cookingFile))}) : arrêtez d'abord le programme")
                Cooking.State.UNKNOWN -> if (!force) throw Stop(1, "état de cuisson inconnu (interface arrêtée pendant un programme ?)")
                Cooking.State.IDLE -> {}
            }
        }
        var reg = try { registry() } catch (e: RegistryException) { null }
        val fixes = ArrayList<String>()
        if (reg == null) fixes += "registre illisible : seules les garanties essentielles ont été appliquées"

        var journal = Journal(OperationType.BACK_TO_LIDL, origin, Step.START, now())
        write(journalFile, journal.toJson())
        benchHook(Step.START)

        device.run("am", "force-stop", RobotId.APP)
        device.run("am", "force-stop", RobotId.PILOT)
        journal = journal.next(Step.STOP); write(journalFile, journal.toJson()); benchHook(Step.STOP)

        val undone = ArrayList<Change>()
        val failures = LinkedHashMap<Int, String>()
        reg?.undoPlan(Scope.LIDL)?.forEach { c ->
            val r = device.run(*Undo.command(c).toTypedArray())
            if (AndroidOutput.ok(r.code, r.out, r.err)) {
                undone += c
                reg = reg!!.mark(c.id, ChangeState.UNDONE)
            } else {
                val reason = (r.err.ifBlank { r.out }).trim().take(200).ifEmpty { "code ${r.code}" }
                failures[c.id] = reason
                reg = reg!!.mark(c.id, ChangeState.FAILED, reason)
            }
        }
        reg?.let { write(registryFile, it.toJson()) }
        journal = journal.next(Step.UNDO); write(journalFile, journal.toJson()); benchHook(Step.UNDO)

        val robot = robot()
        val checks = verify(robot, fixes)
        journal = journal.next(Step.CHECK); write(journalFile, journal.toJson()); benchHook(Step.CHECK)

        write(modeFile, Mode(true, now(), origin).toJson())
        val report = Report(OperationType.BACK_TO_LIDL, now(), origin, undone, failures, checks, fixes, storageFull)
        writeReport(report)
        if (origin == Origin.RESCUE || origin == Origin.GESTURE)
            write(File(root, "secours.json"), Json.write(linkedMapOf(
                "date" to now(), "origine" to origin.key, "version" to version(RobotId.APP), "plantages" to crashes)))
        journal = journal.next(Step.DONE); write(journalFile, journal.toJson()); benchHook(Step.DONE)
        journalFile.delete()

        out(if (json) Json.write(linkedMapOf("ok" to report.succeeded, "bilan" to Report.fileName(report.date))) else report.text())
        if (openLidl) { openLidl(robot); showBubble() }
        else if (reboot) {
            if (!json) out("Redémarrage du robot…")
            // Android writes component states to disk only about 10 s after a change: give it that
            // time, otherwise the Camybrith state may come back at the next boot.
            device.pause(DISK_WRITE_WAIT_MS)
            device.run("sync")
            // Clean restart (PowerManager): Android saves component states before shutting down,
            // which the raw `reboot` command does not guarantee.
            if (!device.run("svc", "power", "reboot").ok) device.run("reboot")
        }
        return 0
    }

    // ------------------------------------------------------------------ back to Camybrith (US4)

    /**
     * Reapplies the `lidl` changes undone by the last return (increasing id), back to Camybrith
     * mode, then opens Camybrith, which redoes its Machine réelle checks. The module cannot know
     * whether the Lidl app is cooking: the interface and the computer script ask the user first.
     */
    private fun resume(origin: Origin): Int {
        prepare()
        if (journalFile.isFile) throw Stop(1, "une opération est déjà en cours (${read(journalFile)})")
        if (!Mode.parse(read(modeFile)).lidl) throw Stop(1, "aucun retour à l'appli Lidl à reprendre : le robot est déjà sur Camybrith")
        if (!installed(RobotId.APP)) throw Stop(1, "Camybrith Cooking n'est pas installé : réinstalle-le d'abord (bash outils/installer.sh)")
        var reg = registry()
        val lastReturn = reports().asReversed().asSequence()
            .mapNotNull { f -> try { Report.parse(read(f) ?: "") } catch (_: RegistryException) { null } }
            .firstOrNull { it.type == OperationType.BACK_TO_LIDL }
        val ids = lastReturn?.undone?.map { it.id } ?: reg.changes.filter { it.scope == Scope.LIDL && it.state == ChangeState.UNDONE }.map { it.id }
        var journal = Journal(OperationType.RESUME, origin, Step.START, now())
        write(journalFile, journal.toJson())
        val robot = robot()
        // The Lidl app lets go of the serial port before its motor service is disabled again.
        robot?.let { device.run("am", "force-stop", it.vendorPackage) }
        val failures = LinkedHashMap<Int, String>()
        val redone = ArrayList<Change>()
        for (c in reg.reapplyPlan(ids)) {
            val r = device.run(*Undo.reapply(c).toTypedArray())
            if (AndroidOutput.ok(r.code, r.out, r.err)) {
                redone += c
                reg = reg.mark(c.id, ChangeState.ACTIVE)
            } else {
                failures[c.id] = (r.err.ifBlank { r.out }).trim().take(200).ifEmpty { "code ${r.code}" }
            }
        }
        write(registryFile, reg.toJson())
        journal = journal.next(Step.REAPPLY); write(journalFile, journal.toJson())
        val checks = ArrayList<Check>()
        for ((target, name) in listOf(RobotId.BOOT_RECEIVER to "démarrage de Camybrith actif", RobotId.HOME_ALIAS to "Camybrith écran d'accueil"))
            checks += Check(name, componentEnabled(target))
        if (robot != null) checks += Check("appli Lidl coupée du moteur", disabledLidlService(robot) != null)
        journal = journal.next(Step.CHECK); write(journalFile, journal.toJson())
        if (failures.isNotEmpty() || checks.any { !it.ok }) {
            // Half-resumed is the unsafe state: go back to the Lidl app (the safe direction).
            journalFile.delete()
            out("Reprise incomplète, retour à l'appli Lidl : " + (failures.values + checks.filter { !it.ok }.map { it.name }).joinToString("; "))
            backToLidl(origin, reboot = false, resuming = true)
            return 2
        }
        write(modeFile, Mode(false, now(), origin).toJson())
        File(root, "secours.json").delete()
        val report = Report(OperationType.RESUME, now(), origin, redone, failures, checks)
        writeReport(report)
        journal = journal.next(Step.DONE); write(journalFile, journal.toJson())
        journalFile.delete()
        device.run("cmd", "package", "set-home-activity", RobotId.HOME_ALIAS)
        device.run("am", "start", "-n", "${RobotId.APP}/.ui.MainActivity", "--ez", "camybrith_resume", "true")
        out(if (json) Json.write(linkedMapOf("ok" to true, "bilan" to Report.fileName(report.date))) else report.text())
        return 0
    }

    // ------------------------------------------------------------------ backup (US6, T054)

    /**
     * Asks the interface to create a backup (it owns the data) and waits for the file. Refused
     * when the interface is missing. The broadcast reaches it even when it is stopped.
     */
    private fun backup(): Int {
        prepare()
        if (!installed(RobotId.APP)) throw Stop(1, "interface Camybrith absente : pas de données à sauvegarder")
        val dir = File(root, "sauvegardes")
        val before = dir.list()?.toSet() ?: emptySet()
        device.run("am", "broadcast", "-f", "32", "-n", "${RobotId.APP}/.backup.BackupReceiver")
        for (i in 0 until 60) {
            val created = (dir.list()?.toSet() ?: emptySet()) - before
            created.firstOrNull { it.endsWith(".camybrith") }?.let {
                out(if (json) Json.write(linkedMapOf("ok" to true, "sauvegarde" to it)) else "Sauvegarde créée : $it")
                return 0
            }
            device.pause(1_000)
        }
        throw Stop(1, "l'interface n'a pas créé de sauvegarde (place insuffisante ? ouvrez Réglages › Sauvegardes)")
    }

    // ------------------------------------------------------------------ full removal (US5)

    /**
     * Full removal of Camybrith: back to the Lidl app (no restart), then undo the `retrait` scope
     * (Camybrith apps, boot animation, accessibility setting; Navigation Bar and HTTP File Server
     * only if asked), never the `socle` (root and Wi-Fi access stay), and finally the rescue module
     * itself and /data/adb/camybrith. The computer script copies the registry beforehand.
     */
    private fun remove(withNavBar: Boolean, withFileServer: Boolean, resuming: Boolean = false): Int {
        prepare()
        if (!resuming) {
            val code = backToLidl(Origin.COMPUTER, reboot = false)
            if (code != 0) return code
        }
        var reg = try { registry() } catch (e: RegistryException) { throw Stop(2, e.message ?: "registre illisible") }
        var journal = Journal(OperationType.REMOVAL, Origin.COMPUTER, Step.START, now())
        write(journalFile, journal.toJson())
        journal = journal.next(Step.STOP); write(journalFile, journal.toJson())
        val kept = ArrayList<String>()
        if (!withNavBar) kept += NAVBAR
        if (!withFileServer) kept += HTTP_FILE_SERVER
        val undone = ArrayList<Change>()
        val failures = LinkedHashMap<Int, String>()
        for (c in reg.undoPlan(Scope.REMOVAL)) {
            // Keeping Navigation Bar also keeps the accessibility setting that switches it on.
            if (kept.any { c.target == it || c.target.startsWith("$it/") || (c.type == ChangeType.SETTING && c.after.contains(it)) }) continue
            val r = device.run(*Undo.command(c).toTypedArray())
            if (AndroidOutput.ok(r.code, r.out, r.err) || (c.type == ChangeType.APP && !installed(c.target))) {
                undone += c
                reg = reg.mark(c.id, ChangeState.UNDONE)
            } else {
                failures[c.id] = (r.err.ifBlank { r.out }).trim().take(200).ifEmpty { "code ${r.code}" }
                reg = reg.mark(c.id, ChangeState.FAILED, failures[c.id])
            }
        }
        write(registryFile, reg.toJson())
        journal = journal.next(Step.UNDO); write(journalFile, journal.toJson())
        val checks = listOf(RobotId.APP, RobotId.PILOT).map { Check("$it désinstallé", !installed(it)) }
        journal = journal.next(Step.CHECK); write(journalFile, journal.toJson())
        val report = Report(OperationType.REMOVAL, now(), Origin.COMPUTER, undone, failures, checks)
        out(if (json) Json.write(linkedMapOf("ok" to report.succeeded)) else report.text())
        if (!report.succeeded) {
            // Something was not removed: keep the module and the registry, so it can be retried.
            writeReport(report)
            journalFile.delete()
            return 2
        }
        // Last of all: the rescue module itself, then its data (nothing is written after this).
        device.run("touch", "$MODULE_DIR/remove")
        journalFile.delete()
        root.deleteRecursively()
        if (!json) out("Camybrith est retiré. Le module de secours disparaîtra au prochain démarrage ; le root et l'accès Wi-Fi restent.")
        return 0
    }

    /** Final verification (research.md D6), with guarantees: the essentials are fixed even outside the registry. */
    private fun verify(robot: RobotId?, fixes: MutableList<String>): List<Check> {
        val c = ArrayList<Check>()
        if (robot == null) {
            c += Check("appli Lidl installée", false, "aucune appli Lidl trouvée")
        } else {
            disabledLidlService(robot)?.let { svc ->
                if (device.run("pm", "enable", svc).let { AndroidOutput.ok(it.code, it.out, it.err) }) fixes += "service moteur Lidl réactivé ($svc)"
            }
            c += Check("service moteur Lidl activé", disabledLidlService(robot) == null)
            val launcher = AndroidOutput.resolved(device.run("cmd", "package", "resolve-activity", "--brief",
                "-a", "android.intent.action.MAIN", "-c", "android.intent.category.LAUNCHER", robot.vendorPackage).out)
            c += Check("appli Lidl lançable", launcher?.startsWith(robot.vendorPackage + "/") == true, launcher ?: "aucune activité")
        }
        if (installed(RobotId.APP)) {
            for ((target, name) in listOf(RobotId.BOOT_RECEIVER to "démarrage de Camybrith désactivé",
                RobotId.HOME_ALIAS to "Camybrith n'est plus l'écran d'accueil")) {
                if (componentEnabled(target)) {
                    val r = device.run("pm", "disable", target)
                    if (AndroidOutput.ok(r.code, r.out, r.err)) fixes += "$target désactivé"
                }
                val enabled = componentEnabled(target)
                c += Check(name, !enabled, if (enabled) "toujours actif" else "")
            }
        }
        val home = AndroidOutput.resolved(device.run("cmd", "package", "resolve-activity", "--brief",
            "-a", "android.intent.action.MAIN", "-c", "android.intent.category.HOME").out)
        c += when {
            robot != null && home?.startsWith(robot.vendorPackage + "/") == true -> Check("écran d'accueil → appli Lidl", true, home)
            home?.startsWith(RobotId.APP + "/") == true -> Check("écran d'accueil → appli Lidl", false, "toujours Camybrith : $home")
            else -> Check("écran d'accueil → appli Lidl", true, "${home ?: "aucun"} : l'appli Lidl sera ouverte au démarrage par le module")
        }
        var alive = ""
        for (i in 0 until 6) {
            alive = listOf(RobotId.APP, RobotId.PILOT).filter { device.run("pidof", it).out.isNotBlank() }.joinToString()
            if (alive.isEmpty()) break
            device.run("am", "force-stop", RobotId.APP); device.run("am", "force-stop", RobotId.PILOT)
            device.pause(500)
        }
        c += Check("processus Camybrith arrêtés", alive.isEmpty(), alive)
        return c
    }

    /**
     * Shows the "Camybrith" bubble over the Lidl app (research.md D14). Started as root as a
     * foreground service: Android 8 refuses a plain service start for an app in the background.
     * If Camybrith is too broken to show it, the Lidl app simply works without it.
     */
    private fun showBubble() {
        if (!installed(RobotId.APP)) return
        device.run("appops", "set", RobotId.APP, "SYSTEM_ALERT_WINDOW", "allow")
        device.run("am", "start-foreground-service", "-n", "${RobotId.APP}/.ui.ReturnBubble", "--ez", "modeLidl", "true")
    }

    private fun openLidl(robot: RobotId?) {
        device.run("am", "start", "-a", "android.intent.action.MAIN", "-c", "android.intent.category.HOME")
        if (robot != null && AndroidOutput.foreground(device.run("dumpsys", "activity", "activities").out)?.startsWith(robot.vendorPackage) != true)
            device.run("monkey", "-p", robot.vendorPackage, "-c", "android.intent.category.LAUNCHER", "1")
    }

    private fun reports(): List<File> = (reportsDir.listFiles { f -> f.name.endsWith(".json") } ?: emptyArray()).sortedBy { it.name }

    private fun writeReport(r: Report) {
        if (!write(File(reportsDir, Report.fileName(r.date)), r.toJson())) return
        Report.toDelete(reports().map { it.name }).forEach { File(reportsDir, it).delete() }
    }

    private fun report(name: String?): Int {
        val f = if (name == null) reports().lastOrNull() else File(reportsDir, if (name.endsWith(".json")) name else "$name.json")
        if (f == null || !f.isFile || f.parentFile != reportsDir) throw Stop(1, "aucun bilan${name?.let { " nommé $it" } ?: ""}")
        val text = read(f) ?: throw Stop(1, "bilan illisible")
        out(if (json) text.replace("\n", "") else Report.parse(text).text())
        return 0
    }

    // ------------------------------------------------------------------ at boot (service.sh)

    /**
     * Called by `service.sh` after `sys.boot_completed`: reserve, power switch gesture, resuming an
     * interrupted operation (unfinished journal → return to the Lidl app finished), then, after a
     * return, check that the Lidl app is in the foreground.
     */
    private fun boot(): Int {
        prepare()
        if (!reserveFile.isFile && Space.canRecreateReserve(device.freeBytes())) createReserve()
        // Power switch gesture (data-model.md › Geste, research.md D13): 3 short boots in a row.
        val shortBoots = read(gestureFile)?.trim()?.toIntOrNull() ?: 0
        if (shortBoots >= GESTURE_THRESHOLD) {
            write(gestureFile, "0")
            if (!Mode.parse(read(modeFile)).lidl && !journalFile.isFile) {
                if (!json) out("Geste de l'interrupteur : $shortBoots démarrages courts, retour à l'appli Lidl.")
                backToLidl(Origin.GESTURE, reboot = false, openLidl = true)
            }
        }
        read(journalFile)?.let { text ->
            val j = try { Journal.parse(text) } catch (_: RegistryException) { null }
            when (j?.bootAction() ?: BootAction.FINISH_RETURN) {
                BootAction.CLEAR -> journalFile.delete()
                // An interrupted removal is finished, keeping Navigation Bar and HTTP File Server.
                BootAction.FINISH_REMOVAL -> return remove(withNavBar = false, withFileServer = false, resuming = true)
                else -> {
                    journalFile.delete()
                    backToLidl(j?.origin ?: Origin.RESCUE, reboot = false, resuming = true)
                }
            }
        }
        val mode = Mode.parse(read(modeFile))
        // In Lidl mode, the essentials are checked again at every boot (a power cut just after a
        // return may have made Android lose the component states) and fixed if needed.
        if (mode.lidl) {
            val fixes = ArrayList<String>()
            verify(robot(), fixes)
            if (fixes.isNotEmpty()) {
                openLidl(robot())
                if (!json) fixes.forEach { out("Corrigé au démarrage : $it") }
                write(File(root, "corrections-demarrage.json"), Json.write(linkedMapOf("date" to now(), "corrections" to fixes)))
            }
        }
        val last = reports().lastOrNull()
        if (mode.lidl && last != null) {
            val r = try { Report.parse(read(last) ?: "") } catch (_: RegistryException) { null }
            if (r != null && r.afterBoot == null) {
                val robot = robot()
                var front: String? = null
                for (i in 0 until 30) {
                    front = AndroidOutput.foreground(device.run("dumpsys", "activity", "activities").out)
                    if (front != null) break
                    device.pause(2_000)
                }
                if (robot != null && front?.startsWith(robot.vendorPackage) != true) {
                    openLidl(robot)
                    device.pause(3_000)
                    front = AndroidOutput.foreground(device.run("dumpsys", "activity", "activities").out)
                }
                val ok = robot != null && front?.startsWith(robot.vendorPackage) == true
                write(last, r.copy(afterBoot = Check("appli Lidl au premier plan", ok, front ?: "rien")).toJson())
            }
        }
        // In Lidl mode, the "Camybrith" bubble over the Lidl app is the way back (D14).
        if (Mode.parse(read(modeFile)).lidl) showBubble()
        if (!json) out("Démarrage traité.")
        return 0
    }

    /**
     * Rescue mode (research.md D7): started by `service.sh` right after boot, watches interface
     * crashes until 120 s after `sys.boot_completed` ([bootSec], in s); at 3, back to the Lidl app
     * without a restart. Nothing if the robot is already in Lidl mode.
     */
    private fun watch(bootSec: Long): Int {
        prepare()
        val bootTime = bootSec * 1000
        while (true) {
            if (Mode.parse(read(modeFile)).lidl) return 0
            val log = device.run("logcat", "-b", "crash", "-d", "-v", "epoch").out
            val crashes = RescueRule.crashes(log)
            val now = clock()
            if (RescueRule.shouldTrigger(crashes, bootTime, now, journalInProgress = journalFile.isFile)) {
                if (!json) out("Mode de secours : ${crashes.size} plantages de l'interface au démarrage, retour à l'appli Lidl.")
                return backToLidl(Origin.RESCUE, reboot = false, openLidl = true, crashes = crashes.size)
            }
            if (now >= bootTime + RescueRule.WINDOW_MS) return 0
            device.pause(5_000)
        }
    }

    companion object {
        const val GESTURE_THRESHOLD = 3
        const val MODULE_DIR = "/data/adb/modules/camybrith_secours"
        const val DISK_WRITE_WAIT_MS = 12_000L
        const val NAVBAR = "nu.nav.bar"
        const val HTTP_FILE_SERVER = "slowscript.httpfileserver"
        const val MAGISK = "com.topjohnwu.magisk"
        const val ANIMATION = "camybrith_boot"
        const val ADB_WIFI = "toque_adb_wifi"
    }
}

/** Parsing of Android command output (pure functions, tested). */
object AndroidOutput {
    /** `pm` sometimes answers "Error: …" with exit code 0: both are checked. */
    fun ok(code: Int, out: String, err: String): Boolean =
        code == 0 && !out.contains("Error:") && !err.contains("Error:") && !err.contains("Exception")

    /** Classes listed under "disabledComponents:" in `dumpsys package <package>`. */
    fun disabledComponents(dumpsys: String): Set<String> {
        val res = HashSet<String>()
        var level = -1
        for (line in dumpsys.lines()) {
            val indent = line.length - line.trimStart().length
            val t = line.trim()
            if (level >= 0) {
                if (t.isEmpty() || indent <= level) level = -1 else { res += t; continue }
            }
            if (t == "disabledComponents:") level = indent
        }
        return res
    }

    /** `package/class` → full class name ("package/.X" → "package.X"). */
    fun className(target: String): String {
        val pkg = target.substringBefore('/')
        val c = target.substringAfter('/')
        return if (c.startsWith(".")) pkg + c else c
    }

    /** Component returned by `cmd package resolve-activity --brief` (last `package/class` line). */
    fun resolved(output: String): String? =
        output.lines().map { it.trim() }.lastOrNull { it.contains('/') && !it.contains(' ') }

    /** Foreground activity according to `dumpsys activity activities`. */
    fun foreground(dumpsys: String): String? =
        Regex("mResumedActivity: ActivityRecord\\{\\S+ \\S+ (\\S+)").find(dumpsys)?.groupValues?.get(1)
}

/** Entry point started by `app_process` from the rescue module (research.md D16). */
object RescueMain {
    @JvmStatic
    fun main(args: Array<String>) {
        val root = File(System.getenv("CAMYBRITH_ROOT") ?: "/data/adb/camybrith")
        val device = object : RescueCommand.Device {
            override fun run(vararg args: String): RescueCommand.Device.Result = try {
                val p = ProcessBuilder(*args).start()
                p.outputStream.close()
                val err = StringBuilder()
                val te = Thread { err.append(p.errorStream.bufferedReader().readText()) }.apply { start() }
                val out = p.inputStream.bufferedReader().readText()
                te.join()
                RescueCommand.Device.Result(p.waitFor(), out, err.toString())
            } catch (e: IOException) {
                RescueCommand.Device.Result(-1, "", e.message ?: "erreur")
            }
            override fun freeBytes() = File("/data").usableSpace
            override fun totalBytes() = File("/data").totalSpace
        }
        System.exit(RescueCommand(device, root).main(args))
    }
}
