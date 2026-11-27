package app.toque.core.backup

import app.toque.core.json.Json
import app.toque.core.json.bool
import app.toque.core.json.obj
import app.toque.core.json.str
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** ISO 8601 dates with offset ("2026-11-27T14:03:00+01:00"), without java.time (Android 6). */
object Iso {
    fun format(ms: Long, zone: TimeZone = TimeZone.getDefault()): String {
        val f = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.ROOT)
        f.timeZone = zone
        val s = f.format(Date(ms))
        return s.substring(0, s.length - 2) + ":" + s.substring(s.length - 2)
    }

    /** Milliseconds, or null if unreadable. Accepts "+01:00", "+0100" and "Z". */
    fun parse(s: String?): Long? {
        if (s == null || s.length < 19) return null
        val t = when {
            s.endsWith("Z") -> s.dropLast(1) + "+0000"
            s.length >= 6 && s[s.length - 3] == ':' && (s[s.length - 6] == '+' || s[s.length - 6] == '-') ->
                s.substring(0, s.length - 3) + s.substring(s.length - 2)
            else -> s
        }
        return try {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.ROOT).apply { isLenient = false }.parse(t)?.time
        } catch (_: ParseException) {
            null
        }
    }
}

/** Cooking state (`cuisson.json`, written by the interface; data-model.md › État de cuisson). */
object Cooking {
    enum class State { IDLE, RUNNING, UNKNOWN }

    const val FRESHNESS_MS = 60_000L

    fun json(busy: Boolean, program: String, updated: String): String =
        Json.write(linkedMapOf("occupe" to busy, "programme" to if (busy) program else "", "maj" to updated))

    /**
     * Pilot stopped → no cooking, whatever the file says. Pilot running: `occupe` true and `maj`
     * less than 60 s old → cooking; `occupe` false → idle; file missing, unreadable, or `occupe`
     * true but stale → unknown (interface crashed during a program).
     */
    fun evaluate(file: String?, pilotRunning: Boolean, now: Long): State {
        if (!pilotRunning) return State.IDLE
        val m = try { file?.let { Json.parse(it).obj() } } catch (_: Json.ParseException) { null } ?: return State.UNKNOWN
        val busy = m.bool("occupe") ?: return State.UNKNOWN
        if (!busy) return State.IDLE
        val updated = Iso.parse(m.str("maj")) ?: return State.UNKNOWN
        return if (now - updated in 0 until FRESHNESS_MS) State.RUNNING else State.UNKNOWN
    }

    fun program(file: String?): String =
        try { file?.let { Json.parse(it).obj().str("programme") } } catch (_: Json.ParseException) { null } ?: ""
}

/** Robot mode (`mode.json`, single source of truth; data-model.md › Mode du robot). */
data class Mode(val lidl: Boolean, val since: String = "", val origin: Origin? = null) {
    fun toJson(): String = Json.write(linkedMapOf(
        "mode" to if (lidl) "lidl" else "camybrith", "depuis" to since, "origine" to origin?.key))

    companion object {
        /** Missing or unreadable = Camybrith (data-model: missing = `camybrith`). */
        fun parse(text: String?): Mode {
            val m = try { text?.let { Json.parse(it).obj() } } catch (_: Json.ParseException) { null } ?: return Mode(false)
            return Mode(m.str("mode") == "lidl", m.str("depuis") ?: "", Origin.of(m.str("origine")))
        }
    }
}

/** Robot identity for the module (`robot.json`, written by the interface; research.md D6). */
data class RobotId(
    val vendorPackage: String,
    val hardwareServiceSuffix: String,
    val camybrithBootReceiver: String = BOOT_RECEIVER,
    val camybrithHomeAlias: String = HOME_ALIAS,
) {
    fun toJson(): String = Json.write(linkedMapOf(
        "vendorPackage" to vendorPackage, "hardwareServiceSuffix" to hardwareServiceSuffix,
        "camybrithBootReceiver" to camybrithBootReceiver, "camybrithHomeAlias" to camybrithHomeAlias))

    companion object {
        const val APP = "app.toque"
        const val PILOT = "app.toque.pilote"
        const val BOOT_RECEIVER = "app.toque/app.toque.recipes.BootReceiver"
        const val HOME_ALIAS = "app.toque/app.toque.ui.Accueil"

        /** Strict reading: off-format values are refused (they end up in `pm` commands). */
        fun parse(text: String?): RobotId? {
            val m = try { text?.let { Json.parse(it).obj() } } catch (_: Json.ParseException) { null } ?: return null
            val pkg = m.str("vendorPackage") ?: return null
            val suffix = m.str("hardwareServiceSuffix") ?: return null
            val boot = m.str("camybrithBootReceiver") ?: BOOT_RECEIVER
            val home = m.str("camybrithHomeAlias") ?: HOME_ALIAS
            if (!Targets.isPackage(pkg) || !Regex("[A-Za-z][A-Za-z0-9_]{0,80}").matches(suffix)) return null
            if (Targets.error(ChangeType.STARTUP, boot) != null || Targets.error(ChangeType.STARTUP, home) != null) return null
            return RobotId(pkg, suffix, boot, home)
        }
    }
}
