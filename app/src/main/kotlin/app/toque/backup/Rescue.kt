package app.toque.backup

import app.toque.core.backup.Cooking
import app.toque.core.backup.Iso
import app.toque.core.backup.Mode
import app.toque.core.backup.RobotId
import app.toque.core.i18n.tr
import app.toque.core.json.Json
import app.toque.core.json.list
import app.toque.core.json.obj
import app.toque.core.json.str
import app.toque.core.machine.RobotModel
import app.toque.core.machine.RootShell

/**
 * Link between the interface and the rescue module (spec 007): everything goes through the
 * module's `camybrith` command, run as root. The interface never touches the registry itself.
 */
object Rescue {
    private const val DIR = "/data/adb/camybrith"
    private val COMMANDS = listOf(
        "/data/adb/modules/camybrith_secours/camybrith",
        // Right after installation by Magisk, before the restart.
        "/data/adb/modules_update/camybrith_secours/camybrith",
    )

    @Volatile private var path: String? = null

    private fun quote(s: String) = "'" + s.replace("'", "'\\''") + "'"

    /** Path of the command if the module is installed, otherwise null. */
    fun command(): String? {
        path?.let { return it }
        val r = RootShell.run(COMMANDS.joinToString("; ") { "[ -f $it ] && echo $it" } + "; true", 8_000)
        return r.out.lines().firstOrNull { it.isNotBlank() }?.trim()?.also { path = it }
    }

    fun installed(): Boolean = command() != null

    private fun run(vararg args: String, timeoutMs: Long = 30_000): RootShell.Result {
        val c = command() ?: return RootShell.Result(-1, "", "module de secours absent")
        return RootShell.run("sh $c " + args.joinToString(" ") { quote(it) }, timeoutMs)
    }

    /**
     * Records a change BEFORE making it. Returns its id, or null if the module is missing or
     * refuses (the caller then does not make the change, unless not making it is dangerous: see
     * LidlSync.setVendorHardware).
     */
    fun note(type: String, target: String, before: String, after: String, scope: String): Int? {
        val r = run("noter", type, target, before, after, scope, "interface")
        return if (r.ok) r.out.lines().lastOrNull { it.isNotBlank() }?.trim()?.toIntOrNull() else null
    }

    fun noteDone(id: Int, ok: Boolean) {
        run("noter-fin", id.toString(), if (ok) "ok" else "echec")
    }

    class Status(val lidl: Boolean, val since: String, val origin: String?, val toUndo: List<String>, val storageLow: Boolean,
                 val changes: List<String> = emptyList())

    fun status(): Status? {
        val r = run("etat", "--json")
        if (!r.ok) return null
        val m = try { Json.parse(r.out.trim().lines().last()).obj() } catch (_: Exception) { return null }
        return Status(
            m.str("mode") == "lidl", m.str("depuis") ?: "", m.str("origine"),
            m["lidl"].list().map { e -> e.obj().let { label(it.str("type") ?: "", it.str("cible") ?: "") } },
            m["alerte"] == true,
            m["modifications"].list().map { e -> e.obj().let {
                val state = when (it.str("etat")) { "annulee" -> tr("annulée"); "echec" -> tr("échec"); else -> tr("active") }
                "${it.str("date")?.take(10) ?: ""} · ${label(it.str("type") ?: "", it.str("cible") ?: "")} · ${it.str("portee")} · $state" +
                    if (it["deduit"] == true) " · " + tr("relevée à l'installation du module") else " · " + (it.str("origine") ?: "")
            } },
        )
    }

    /** What a change means, in plain words (confirmation screen). */
    private fun label(type: String, target: String): String = when {
        target == RobotId.BOOT_RECEIVER -> tr("Camybrith Cooking ne s'ouvrira plus au démarrage")
        target == RobotId.HOME_ALIAS -> tr("Camybrith Cooking ne sera plus l'écran d'accueil")
        type == "composant" && RobotModel.isHardwareService(target.substringAfter('/')) -> tr("l'appli Lidl retrouve l'accès au moteur")
        else -> "$type $target"
    }

    /** Robot mode (`mode.json`, single source of truth, data-model.md › Mode du robot). */
    fun mode(timeoutMs: Long = 8_000): Mode = Mode.parse(RootShell.run("cat $DIR/mode.json 2>/dev/null", timeoutMs).out.ifBlank { null })

    /** Text of the last report, or null. */
    fun lastReport(): String? = run("bilan").let { if (it.ok) it.out.trim() else null }

    class Result(val code: Int, val text: String)

    /** Back to the Lidl app through the module: the robot restarts if all goes well. */
    fun backToLidl(): Result {
        val r = run("retour-lidl", timeoutMs = 120_000)
        return Result(r.code, (r.out + r.err).trim())
    }

    /** Back to Camybrith (US4): the module reopens MainActivity with EXTRA_RESUMED on success. */
    fun resume(): Result {
        val r = run("reprendre", timeoutMs = 90_000)
        return Result(r.code, (r.out + r.err).trim())
    }

    /** Robot identity read by the module (robot.json, research.md D6). */
    fun writeRobotId(model: RobotModel) {
        write("robot.json", RobotId(model.vendorPackage, model.hardwareServiceSuffix).toJson())
    }

    /**
     * Cooking state read by the module (cuisson.json, research.md D12): written directly, without
     * starting the command (called every 30 s during a program: it must stay light).
     */
    fun writeCooking(busy: Boolean, program: String) {
        write("cuisson.json", Cooking.json(busy, program.take(80), Iso.format(System.currentTimeMillis())))
    }

    private fun write(name: String, text: String) {
        RootShell.run("[ -d $DIR ] && printf '%s' ${quote(text)} > $DIR/.$name.tmp && mv $DIR/.$name.tmp $DIR/$name; true", 8_000)
    }
}
