package app.toque.core.backup

import app.toque.core.json.Json
import app.toque.core.json.bool
import app.toque.core.json.int
import app.toque.core.json.list
import app.toque.core.json.obj
import app.toque.core.json.str
import java.security.MessageDigest

/**
 * Manual backup of the user's data (spec 007, US6; data-model.md › Sauvegarde): a `.camybrith`
 * zip holding a manifest and logical JSON data. No secrets, no Lidl content, no photos.
 */
object Secrets {
    /** Keys that hold a secret: never in a backup (closed list, data-model.md). */
    val SECRET_KEYS = setOf("mqttPassword")

    /**
     * Settings declared safe to back up. Any key not listed here is left out (a new setting is
     * excluded until someone decides it is safe). Transient state (mode, last sync, crash time…)
     * is not backed up either: restoring "Machine réelle" must go through its checks.
     */
    val SAFE_KEYS = setOf(
        "autoSync", "bootLaunch", "chainSteps", "hotRamp", "keySound", "knobColours", "knobLeft",
        "knobPress", "knobRight", "knobStandby", "language", "mqttEnabled", "mqttHost", "mqttPort",
        "mqttTopic", "mqttUser", "requireBowl", "robot", "screenSleepMin", "serialDevice", "sound",
        "startupSoundBoot", "theme", "timeZone", "twinHost", "twinPort", "vendorPackage", "welcomeDone",
    )

    fun filter(settings: Map<String, Any?>): Map<String, Any?> =
        settings.filterKeys { it in SAFE_KEYS && it !in SECRET_KEYS }

    /** Secrets the user will have to type again after a restore (only those that were set). */
    fun toReenter(settings: Map<String, Any?>): List<String> =
        SECRET_KEYS.filter { (settings[it] as? String)?.isNotEmpty() == true }.sorted()
}

object BackupContent {
    /** Lidl recipes (copied from the Lidl app or from monsieur-cuisine.com): never backed up. */
    fun isLidlRecipe(id: String): Boolean = id.startsWith("mc-") || id.startsWith("web-")

    fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    /** Anonymised robot fingerprint: never the serial number itself. */
    fun robotFingerprint(serial: String): String = sha256(("camybrith:" + serial).toByteArray()).take(16)

    /** Backup file name: `camybrith-AAAAMMJJ-HHMMSS.camybrith`. */
    fun fileName(isoDate: String): String = "camybrith-" + Report.fileName(isoDate).removeSuffix(".json") + ".camybrith"

    const val KEPT = 5

    /** Backups to delete so that only [KEPT] remain (names sort by date). */
    fun toDelete(names: Collection<String>): List<String> =
        names.filter { it.endsWith(".camybrith") }.sorted().dropLast(KEPT)
}

data class BackupFile(val path: String, val size: Long, val sha256: String) {
    fun toJson(): Map<String, Any?> = linkedMapOf("path" to path, "size" to size, "sha256" to sha256)
}

/** Manifest of a backup (data-model.md › Sauvegarde). JSON keys are the ones of the spec. */
data class Manifest(
    val createdAt: String,
    val appVersionName: String,
    val appVersionCode: Int,
    val pilotVersionCode: Int,
    val robot: String,
    val sections: List<String>,
    val summary: Map<String, Int>,
    val files: List<BackupFile>,
    val format: Int = FORMAT,
) {
    fun toJson(): String = Json.write(linkedMapOf(
        "format" to format, "createdAt" to createdAt,
        "app" to linkedMapOf("versionName" to appVersionName, "versionCode" to appVersionCode),
        "pilot" to linkedMapOf("versionCode" to pilotVersionCode),
        "robot" to robot, "sections" to sections, "summary" to summary,
        "files" to files.map { it.toJson() }, "secretsIncluded" to false,
    ), pretty = true)

    companion object {
        const val FORMAT = 1
        const val NAME = "manifest.json"

        fun parse(text: String): Manifest {
            val m = try { Json.parse(text).obj() } catch (e: Json.ParseException) {
                throw RegistryException("manifeste illisible : ${e.message}")
            }
            if (m.bool("secretsIncluded") != false) throw RegistryException("sauvegarde refusée : elle contiendrait des secrets")
            return Manifest(
                createdAt = m.str("createdAt") ?: "",
                appVersionName = m["app"].obj().str("versionName") ?: "",
                appVersionCode = m["app"].obj().int("versionCode") ?: 0,
                pilotVersionCode = m["pilot"].obj().int("versionCode") ?: 0,
                robot = m.str("robot") ?: "",
                sections = m["sections"].list().mapNotNull { it as? String },
                summary = m["summary"].obj().mapValues { (_, v) -> (v as? Number)?.toInt() ?: 0 },
                files = m["files"].list().map { e ->
                    val o = e.obj()
                    BackupFile(o.str("path") ?: "", (o["size"] as? Number)?.toLong() ?: -1, o.str("sha256") ?: "")
                },
                format = m.int("format") ?: 0,
            )
        }
    }
}
