package app.toque.backup

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.provider.Settings
import app.toque.core.backup.BackupContent
import app.toque.core.backup.BackupData
import app.toque.core.backup.Manifest
import app.toque.core.backup.Migrations
import app.toque.core.backup.Secrets
import app.toque.core.backup.Validation
import app.toque.core.i18n.tr
import app.toque.core.json.Json
import app.toque.core.json.obj
import app.toque.core.json.str
import app.toque.core.machine.RootShell
import java.io.File
import java.util.zip.ZipFile

/**
 * Reads and restores a backup (spec 007, US7, T062–T063; research.md D15). Logical restore, no
 * folder copy: favorites and own/imported recipes replaced in `recettes.db` in ONE SQLite
 * transaction (all or nothing), then the settings. Before starting, the current state is saved as
 * an automatic backup, and a `restauration-en-cours` file lets the restore be replayed (it is
 * idempotent) if the power goes off between the two steps.
 */
object BackupRestorer {
    private const val PENDING = "restauration-en-cours"

    /** What a restore would replace, shown before confirming. */
    class Preview(val name: String, val data: BackupData, val manifest: Manifest, val errors: List<String>, val warnings: List<String>) {
        val ok: Boolean get() = errors.isEmpty()
    }

    class Result(val ok: Boolean, val message: String)

    /** Copies the backup out of /data/adb (root) and checks it. */
    fun read(c: Context, name: String): Preview? {
        val local = fetch(c, name) ?: return null
        try {
            val raw = ZipFile(local).use { z -> z.entries().toList().associate { e -> e.name to z.getInputStream(e).readBytes() } }
            val manifest = Manifest.parse(String(raw[Manifest.NAME] ?: return null))
            val check = Validation.check(manifest, raw, versionCode(c), robot(c))
            val data = BackupData(Migrations.toCurrent(manifest.format, raw - Manifest.NAME))
            return Preview(name, data, manifest, check.errors, check.warnings)
        } catch (e: Exception) {
            return Preview(name, BackupData(emptyMap()), Manifest("", "", 0, 0, "", emptyList(), emptyMap(), emptyList()),
                listOf(tr("Sauvegarde illisible : %s", e.message ?: "")), emptyList())
        } finally {
            local.delete()
        }
    }

    /** Full restore. The caller refuses it during a cooking program. */
    fun restore(c: Context, name: String, stopAfterTransaction: Boolean = false): Result {
        val preview = read(c, name) ?: return Result(false, tr("Sauvegarde introuvable : %s", name))
        if (!preview.ok) return Result(false, tr("Sauvegarde refusée, rien n'a été modifié :") + "\n" + preview.errors.joinToString("\n") { "• $it" })
        val auto = BackupWriter.create(c)
        if (!auto.ok) return Result(false, tr("Restauration annulée : la sauvegarde automatique de l'état actuel a échoué (%s)", auto.message))
        File(c.filesDir, PENDING).writeText(Json.write(linkedMapOf("sauvegarde" to name, "avant" to auto.name)))
        apply(c, preview.data, stopAfterTransaction)
        File(c.filesDir, PENDING).delete()
        return Result(true, tr("Restauration faite : %d favoris, %d recettes perso. Avant, l'état actuel a été sauvegardé (%s).",
            preview.data.favorites.size, preview.data.recipes.size, auto.name ?: ""))
    }

    /** At app start: replays a restore cut off by a power failure. */
    fun resumeIfNeeded(c: Context) {
        val f = File(c.filesDir, PENDING)
        if (!f.isFile) return
        val name = try { Json.parse(f.readText()).obj().str("sauvegarde") } catch (_: Exception) { null }
        val preview = name?.let { read(c, it) }
        if (preview != null && preview.ok) apply(c, preview.data, false)
        f.delete()
    }

    private fun apply(c: Context, data: BackupData, stopAfterTransaction: Boolean) {
        val db = SQLiteDatabase.openDatabase(c.getDatabasePath("recettes.db").path, null, SQLiteDatabase.OPEN_READWRITE)
        db.use { d ->
            d.beginTransaction()
            try {
                d.delete("favorites", null, null)
                for (id in data.favorites) d.insertWithOnConflict("favorites", null, ContentValues().apply { put("id", id) }, SQLiteDatabase.CONFLICT_REPLACE)
                // Lidl recipes stay as they are: only the user's own and imported ones are replaced.
                d.delete("recipes", "id NOT LIKE 'mc-%' AND id NOT LIKE 'web-%'", null)
                for (r in data.recipes) d.insertWithOnConflict("recipes", null, ContentValues().apply {
                    put("id", r["id"] as String)
                    put("title", r["title"] as? String ?: "")
                    put("json", r["json"] as String)
                    put("updated", (r["updated"] as? Number)?.toLong() ?: 0L)
                }, SQLiteDatabase.CONFLICT_REPLACE)
                d.setTransactionSuccessful()
            } finally {
                d.endTransaction()
            }
        }
        // Test bench only (B8 restore cut): stop dead between the transaction and the settings.
        if (stopAfterTransaction) { RootShell.run("touch /data/adb/camybrith/bench-stopped", 5_000); while (true) Thread.sleep(60_000) }
        val e = c.getSharedPreferences("toque", Context.MODE_PRIVATE).edit()
        for ((k, v) in Secrets.filter(data.settings)) when (v) {
            is Boolean -> e.putBoolean(k, v)
            is String -> e.putString(k, v)
            is Number -> if (k in INT_KEYS) e.putInt(k, v.toInt()) else e.putString(k, v.toString())
        }
        e.commit()
    }

    /** Settings stored as Int in Prefs (the backup's JSON numbers have no type). */
    private val INT_KEYS = setOf("knobLeft", "knobPress", "knobRight", "mqttPort", "screenSleepMin", "twinPort")

    private fun fetch(c: Context, name: String): File? {
        if (!Regex("camybrith-[0-9]{8}-[0-9]{6}\\.camybrith").matches(name)) return null
        val local = File(c.cacheDir, name)
        // Not yet collected by the module: still in the app's own folder.
        val pending = File(File(c.filesDir, BackupWriter.PENDING_DIR), name)
        if (pending.isFile) return pending.copyTo(local, overwrite = true)
        val r = RootShell.run("cp ${BackupWriter.DIR}/$name ${local.path} && chmod 644 ${local.path} && echo ok", 15_000)
        // Without root (test bench), a copy put there beforehand by the bench is used as is.
        return if (r.out.contains("ok") || local.isFile) local.takeIf { it.isFile } else null
    }

    private fun versionCode(c: Context): Int = try {
        @Suppress("DEPRECATION") c.packageManager.getPackageInfo(c.packageName, 0).versionCode
    } catch (_: Exception) { 0 }

    private fun robot(c: Context) = BackupContent.robotFingerprint(Settings.Secure.getString(c.contentResolver, Settings.Secure.ANDROID_ID) ?: "")
}
