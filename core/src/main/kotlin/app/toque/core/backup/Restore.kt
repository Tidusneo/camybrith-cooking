package app.toque.core.backup

import app.toque.core.json.Json
import app.toque.core.json.obj

/**
 * Checks a backup before restoring it (spec 007, US7, T059; data-model.md › Sauvegarde):
 * known format, every file present with its exact fingerprint and no extra file, not made by a
 * newer Camybrith, warning if it comes from another robot. Messages are French (shown).
 */
object Validation {
    class Result(val errors: List<String>, val warnings: List<String>) {
        val ok: Boolean get() = errors.isEmpty()
    }

    fun check(manifest: Manifest, entries: Map<String, ByteArray>, installedVersionCode: Int, thisRobot: String): Result {
        val errors = ArrayList<String>()
        val warnings = ArrayList<String>()
        if (manifest.format !in Migrations.KNOWN) errors += "format de sauvegarde inconnu (${manifest.format})"
        if (manifest.appVersionCode > installedVersionCode)
            errors += "sauvegarde faite par une version plus récente de Camybrith (${manifest.appVersionName}) : mettez d'abord Camybrith à jour"
        val listed = manifest.files.associateBy { it.path }
        for ((path, f) in listed) {
            val bytes = entries[path]
            when {
                bytes == null -> errors += "fichier manquant : $path"
                bytes.size.toLong() != f.size || BackupContent.sha256(bytes) != f.sha256 -> errors += "fichier abîmé : $path"
            }
        }
        (entries.keys - listed.keys - Manifest.NAME).forEach { errors += "fichier en trop : $it" }
        if (manifest.robot.isNotEmpty() && manifest.robot != thisRobot) warnings += "cette sauvegarde vient d'un autre robot"
        return Result(errors, warnings)
    }
}

/**
 * Format conversions, in memory only (T060): an older backup is read as the current format, the
 * file itself is never rewritten. Format 0 is an example used by the tests (settings named
 * `preferences.json`), so that the mechanism is in place for the day the format changes.
 */
object Migrations {
    val KNOWN = setOf(0, Manifest.FORMAT)

    /** Returns the entries as in the current format. */
    fun toCurrent(format: Int, entries: Map<String, ByteArray>): Map<String, ByteArray> = when (format) {
        0 -> entries.mapKeys { (k, _) -> if (k == "preferences.json") "settings.json" else k }
        else -> entries
    }
}

/** Logical content of a backup, read from its JSON entries (current format). */
class BackupData(entries: Map<String, ByteArray>) {
    val favorites: List<String> = entries["favorites.json"]?.let { b ->
        (Json.parse(String(b)) as? List<*>)?.mapNotNull { it as? String }
    } ?: emptyList()

    /** id, title, json, updated: exactly the rows of the `recipes` table. */
    val recipes: List<Map<String, Any?>> = entries["recipes.json"]?.let { b ->
        (Json.parse(String(b)) as? List<*>)?.map { it.obj() }?.filter { r ->
            val id = r["id"] as? String
            id != null && !BackupContent.isLidlRecipe(id) && r["json"] is String
        }
    } ?: emptyList()

    /** Settings, filtered again on reading (a hand-edited backup cannot bring a secret or unknown key). */
    val settings: Map<String, Any?> = entries["settings.json"]?.let { Secrets.filter(Json.parse(String(it)).obj()) } ?: emptyMap()
}
