package app.toque.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.provider.Settings
import app.toque.core.backup.BackupContent
import app.toque.core.backup.BackupFile
import app.toque.core.backup.Iso
import app.toque.core.backup.Manifest
import app.toque.core.backup.Secrets
import app.toque.core.backup.Space
import app.toque.core.i18n.tr
import app.toque.core.json.Json
import app.toque.core.machine.RootShell
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Creates a manual backup (spec 007, US6, T053): favorites, the user's own and imported recipes
 * (never Lidl ones), settings without secrets, and the pilot settings. The zip is written in the
 * app's own folder [PENDING_DIR] (no root needed); it is then moved to /data/adb/camybrith/sauvegardes
 * (5 kept), right away when root is available, otherwise by the rescue module (which is root).
 */
object BackupWriter {
    const val DIR = "/data/adb/camybrith/sauvegardes"
    /** Read by the rescue module (RescueCommand.APP_PENDING_BACKUPS). */
    const val PENDING_DIR = "sauvegardes-a-ranger"
    private val NAME = Regex("camybrith-[0-9]{8}-[0-9]{6}\\.camybrith")

    class Result(val ok: Boolean, val message: String, val name: String? = null)

    class Entry(val name: String, val size: Long)

    fun create(c: Context): Result {
        val db = c.getDatabasePath("recettes.db")
        val estimated = (if (db.isFile) db.length() else 0L) + 1_000_000L
        if (!Space.canBackup(c.filesDir.usableSpace, estimated))
            return Result(false, tr("Pas assez de place pour une sauvegarde : supprimez d'anciennes sauvegardes ou des photos de recettes."))

        val files = LinkedHashMap<String, ByteArray>()
        val summary = LinkedHashMap<String, Int>()
        val sections = ArrayList<String>()
        if (db.isFile) {
            SQLiteDatabase.openDatabase(db.path, null, SQLiteDatabase.OPEN_READONLY).use { d ->
                val favorites = ArrayList<String>()
                d.rawQuery("SELECT id FROM favorites", null).use { cur -> while (cur.moveToNext()) favorites += cur.getString(0) }
                val recipes = ArrayList<Map<String, Any?>>()
                d.rawQuery("SELECT id, title, json, updated FROM recipes", null).use { cur ->
                    while (cur.moveToNext()) {
                        val id = cur.getString(0)
                        if (BackupContent.isLidlRecipe(id)) continue
                        recipes += linkedMapOf("id" to id, "title" to cur.getString(1), "json" to cur.getString(2), "updated" to cur.getLong(3))
                    }
                }
                files["favorites.json"] = Json.write(favorites).toByteArray()
                files["recipes.json"] = Json.write(recipes).toByteArray()
                sections += listOf("favorites", "recipes")
                summary["favorites"] = favorites.size
                summary["recipes"] = recipes.size
            }
        }
        val settings = Secrets.filter(c.getSharedPreferences("toque", Context.MODE_PRIVATE).all)
        files["settings.json"] = Json.write(settings).toByteArray()
        sections += "settings"
        summary["settings"] = settings.size
        val pilot = RootShell.run("cat /data/data/app.toque.pilote/shared_prefs/*.xml 2>/dev/null", 8_000).out
        if (pilot.isNotBlank()) {
            files["pilot.xml"] = pilot.toByteArray()
            sections += "pilot"
        }

        val now = Iso.format(System.currentTimeMillis())
        val name = BackupContent.fileName(now)
        val manifest = Manifest(
            createdAt = now,
            appVersionName = version(c, c.packageName).first,
            appVersionCode = version(c, c.packageName).second,
            pilotVersionCode = version(c, "app.toque.pilote").second,
            robot = BackupContent.robotFingerprint(Settings.Secure.getString(c.contentResolver, Settings.Secure.ANDROID_ID) ?: ""),
            sections = sections,
            summary = summary,
            files = files.map { (path, bytes) -> BackupFile(path, bytes.size.toLong(), BackupContent.sha256(bytes)) },
        )
        val pendingDir = File(c.filesDir, PENDING_DIR).apply { mkdirs() }
        val tmp = File(pendingDir, name)
        try {
            ZipOutputStream(FileOutputStream(tmp)).use { z ->
                for ((path, bytes) in files) { z.putNextEntry(ZipEntry(path)); z.write(bytes); z.closeEntry() }
                z.putNextEntry(ZipEntry(Manifest.NAME)); z.write(manifest.toJson().toByteArray()); z.closeEntry()
            }
        } catch (e: java.io.IOException) {
            tmp.delete()
            return Result(false, tr("Sauvegarde impossible : %s", e.message ?: ""))
        }
        // Moved right away when root is available; otherwise the rescue module collects it.
        val moved = RootShell.run("[ -d /data/adb/camybrith ] && mkdir -p $DIR && chmod 700 $DIR && cp ${tmp.path} $DIR/$name && chmod 600 $DIR/$name && echo ok", 20_000)
        if (moved.out.contains("ok")) {
            tmp.delete()
            BackupContent.toDelete(list().map { it.name }).forEach { delete(it) }
        }
        return Result(true, tr("Sauvegarde créée : %d favoris, %d recettes perso.", summary["favorites"] ?: 0, summary["recipes"] ?: 0), name)
    }

    /** Backups on the robot, newest first. */
    fun list(): List<Entry> =
        RootShell.run("ls -l $DIR 2>/dev/null", 8_000).out.lines().mapNotNull { line ->
            val parts = line.trim().split(Regex("\\s+"))
            val n = parts.lastOrNull() ?: return@mapNotNull null
            if (!NAME.matches(n)) return@mapNotNull null
            Entry(n, parts.getOrNull(4)?.toLongOrNull() ?: 0L)
        }.sortedByDescending { it.name }

    fun delete(name: String): Boolean = NAME.matches(name) && RootShell.run("rm -f $DIR/$name", 8_000).ok

    private fun version(c: Context, pkg: String): Pair<String, Int> = try {
        val info = c.packageManager.getPackageInfo(pkg, 0)
        @Suppress("DEPRECATION")
        (info.versionName ?: "") to info.versionCode
    } catch (_: Exception) {
        "" to 0
    }
}
