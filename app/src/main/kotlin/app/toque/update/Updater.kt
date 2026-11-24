package app.toque.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Handler
import android.os.Looper
import app.toque.ToqueApp
import app.toque.core.i18n.tr
import app.toque.core.machine.RobotModel
import app.toque.core.machine.RootShell
import app.toque.core.update.Release
import app.toque.core.update.Releases
import app.toque.machine.Ipc
import app.toque.ui.MainActivity
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.SSLException

/**
 * Mises à jour de Camybrith Cooking depuis les versions publiées sur GitHub (dépôt du projet).
 *
 * Une version comprend deux applis qui doivent rester de la même génération : le pilote moteur
 * (app.toque.pilote) et l'interface (app.toque, variante rapide ou prudente, la même que celle
 * installée). Les deux sont téléchargées et vérifiées, puis installées avec le root, pilote
 * d'abord ; la variante rapide est ensuite compilée par Android (comme outils/installer.sh, le
 * JIT d'Android 8.1 faisant planter l'interface sur ce processeur), puis l'interface est relancée.
 * Toutes les méthodes sont bloquantes : à appeler hors du fil de l'interface.
 */
class Updater(private val context: Context) {
    class Failure(message: String) : Exception(message)

    /** Fichiers téléchargés : l'interface, et le pilote s'il est plus récent que celui installé. */
    class Download(val ui: File, val pilot: File?)

    private val log = ToqueApp.of(context).log

    @Suppress("DEPRECATION")
    private val info = context.packageManager.getPackageInfo(context.packageName, 0)
    val installedVersion: String = info.versionName ?: "0"
    @Suppress("DEPRECATION")
    val installedCode: Int = info.versionCode
    private val variant = Releases.variantOf(installedVersion)
    private val safeMode = context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_VM_SAFE_MODE != 0
    @Suppress("DEPRECATION")
    private val pilotCode: Int = try { context.packageManager.getPackageInfo(Ipc.PILOT_PACKAGE, 0).versionCode } catch (_: Exception) { -1 }

    /** Dernière version publiée (lève [Failure] avec un message lisible). */
    fun latest(): Release {
        val c = open(Releases.LATEST_URL, "application/vnd.github+json")
        try {
            when (val code = c.responseCode) {
                200 -> {}
                404 -> throw Failure(tr("Aucune version publiée sur GitHub pour l'instant"))
                403, 429 -> throw Failure(tr("GitHub limite les vérifications : réessayez dans une heure"))
                else -> throw Failure(tr("GitHub a répondu %d", code))
            }
            val text = c.inputStream.bufferedReader().use { it.readText() }
            val r = try { Releases.parse(text, variant) } catch (e: IllegalArgumentException) { throw Failure(tr("Réponse GitHub illisible")) }
            log.write("maj", "dernière version publiée : ${r.version} (installée : $installedVersion)")
            return r
        } catch (e: IOException) {
            throw network(e)
        } finally {
            c.disconnect()
        }
    }

    fun isNewer(r: Release): Boolean = Releases.isNewer(r.version, installedVersion)

    /** Télécharge l'interface (et le pilote s'il est plus récent) et les vérifie. */
    fun download(r: Release, progress: (Int) -> Unit): Download {
        val uiUrl = r.apkUrl ?: throw Failure(tr("Cette version ne contient pas d'appli à installer"))
        val dir = File(context.cacheDir, "maj")
        dir.mkdirs()
        dir.listFiles()?.forEach { it.delete() }
        val withPilot = r.pilotUrl != null
        val total = r.apkSize + (if (withPilot) r.pilotSize else 0L)
        var before = 0L
        fun part(n: Long) { if (total > 0) progress(((before + n) * 100 / total).toInt().coerceIn(0, 100)) }
        val ui = fetch(uiUrl, File(dir, "interface.apk"), r.apkSize, ::part)
        verify(ui)
        before += ui.length()
        var pilot: File? = null
        if (withPilot) {
            val f = fetch(r.pilotUrl!!, File(dir, "pilote.apk"), r.pilotSize, ::part)
            pilot = verifyPilot(f)
        }
        progress(100)
        log.write("maj", "téléchargée : ${r.apkName} (${ui.length() / 1024} Ko)" +
            (if (pilot != null) " + ${r.pilotName}" else " ; pilote déjà à jour"))
        return Download(ui, pilot)
    }

    private fun fetch(url: String, file: File, size: Long, progress: (Long) -> Unit): File {
        val c = open(url, "application/octet-stream")
        try {
            if (c.responseCode != 200) throw Failure(tr("Téléchargement refusé (code %d)", c.responseCode))
            var done = 0L
            var last = 0L
            c.inputStream.use { input ->
                FileOutputStream(file).use { out ->
                    val buf = ByteArray(32 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (done - last > 64 * 1024) { last = done; progress(done) }
                    }
                }
            }
            if (size > 0 && done != size) {
                file.delete()
                throw Failure(tr("Téléchargement incomplet, réessayez"))
            }
            return file
        } catch (e: IOException) {
            file.delete()
            throw network(e)
        } finally {
            c.disconnect()
        }
    }

    /** Le pilote téléchargé, ou null s'il n'est pas plus récent que celui déjà installé. */
    @Suppress("DEPRECATION")
    private fun verifyPilot(file: File): File? {
        val pi = context.packageManager.getPackageArchiveInfo(file.path, 0)
        if (pi == null || pi.packageName != Ipc.PILOT_PACKAGE) {
            file.delete()
            throw Failure(tr("Le fichier téléchargé n'est pas le pilote moteur de Camybrith Cooking"))
        }
        if (pi.versionCode < pilotCode) { file.delete(); throw Failure(tr("Cette version est plus ancienne que celle installée")) }
        if (pi.versionCode == pilotCode) { file.delete(); return null }
        return file
    }

    @Suppress("DEPRECATION")
    private fun verify(file: File) {
        val pi = context.packageManager.getPackageArchiveInfo(file.path, 0)
        if (pi == null) { file.delete(); throw Failure(tr("Le fichier téléchargé n'est pas une appli valide")) }
        if (pi.packageName != context.packageName) { file.delete(); throw Failure(tr("Le fichier téléchargé n'est pas Camybrith Cooking")) }
        if (pi.versionCode <= installedCode) { file.delete(); throw Failure(tr("Cette version n'est pas plus récente que celle installée")) }
    }

    /**
     * Installe la mise à jour avec le root : pilote d'abord, puis interface, compilation de la
     * variante rapide, et relance. Android arrête l'appli pendant l'installation : en cas de
     * succès, la fonction ne revient pas.
     */
    fun install(d: Download) {
        if (!RootShell.available()) throw Failure(tr("Mise à jour impossible sans accès root (Magisk)"))
        val dir = "/data/local/tmp"
        val out = "$dir/camybrith-maj.log"
        val ui = "$dir/camybrith-maj-interface.apk"
        val pilot = "$dir/camybrith-maj-pilote.apk"
        val copy = RootShell.run(
            "rm -f $out && cat '${d.ui.absolutePath}' > $ui && chmod 644 $ui" +
                (if (d.pilot != null) " && cat '${d.pilot.absolutePath}' > $pilot && chmod 644 $pilot" else "") +
                " && echo ok",
            90_000,
        )
        if (!copy.out.contains("ok")) throw Failure(tr("Copie de la mise à jour impossible : %s", copy.err.trim()))
        log.write("maj", "installation (root)" + (if (d.pilot != null) " du pilote et" else "") + " de l'interface")
        val pkg = context.packageName
        val launch = "am start -n $pkg/${MainActivity::class.java.name}"
        // Pas de compilation pour la variante prudente, ni sous Android 6 (déjà compilée à l'installation).
        val compile = if (!safeMode && android.os.Build.VERSION.SDK_INT >= 24)
            "echo compilation >> $out; cmd package compile -m speed -f $pkg >> $out 2>&1; am force-stop $pkg; " else ""
        val pilotStep = if (d.pilot != null)
            "echo pilote >> $out; pm install -r $pilot >> $out 2>&1 || exit 1; " else ""
        // Lancé en arrière-plan : Android arrête l'appli pendant l'installation, la suite continue sans elle.
        val script = "( ${pilotStep}echo interface >> $out; pm install -r $ui >> $out 2>&1 || exit 1; " +
            "$compile sleep 1; $launch > /dev/null 2>&1; rm -f $ui $pilot ) < /dev/null > /dev/null 2>&1 &"
        RootShell.run(script, 10_000)
        val deadline = System.currentTimeMillis() + 240_000
        while (System.currentTimeMillis() < deadline) {
            try { Thread.sleep(2_000) } catch (_: InterruptedException) { return }
            val result = RootShell.run("cat $out 2>/dev/null", 5_000).out.trim()
            if (result.contains("Failure") || result.contains("Error")) {
                log.write("maj", "échec : $result")
                RootShell.run("rm -f $ui $pilot", 5_000)
                throw Failure(explain(result))
            }
        }
        throw Failure(tr("L'installation ne répond pas. Redémarrez le robot puis réessayez."))
    }

    private fun open(url: String, accept: String): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 30_000
        c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", "CamybrithCooking/$installedVersion (Android)")
        c.setRequestProperty("Accept", accept)
        return c
    }

    private fun network(e: IOException): Failure {
        log.write("maj", "réseau : $e")
        return when (e) {
            is SSLException -> Failure(tr("Connexion sécurisée à GitHub impossible. Vérifiez la date et l'heure du robot."))
            else -> Failure(tr("Pas de connexion à GitHub. Vérifiez le Wi-Fi du robot."))
        }
    }

    companion object {
        /** Écran à prévenir d'un échec signalé par l'installateur d'Android (fil principal). */
        @Volatile var onInstallError: ((String) -> Unit)? = null

        fun explain(result: String): String = when {
            result.contains("UPDATE_INCOMPATIBLE") || result.contains("SIGNATURE") ->
                tr("La version installée n'a pas la même signature. Désinstallez Camybrith Cooking une fois, puis installez la version de GitHub.")
            result.contains("INSUFFICIENT_STORAGE") -> tr("Espace de stockage insuffisant sur le robot")
            result.contains("VERSION_DOWNGRADE") -> tr("Cette version est plus ancienne que celle installée")
            result.contains("OLDER_SDK") -> tr("Cette version n'est pas compatible avec ce robot")
            else -> tr("Installation refusée : %s", result.lines().lastOrNull { it.isNotBlank() } ?: result)
        }
    }
}

/** Résultat de l'installateur d'Android (appareils non rootés). */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, intent: Intent) {
        val log = ToqueApp.of(c).log
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try { c.startActivity(confirm) } catch (e: Exception) { log.write("maj", "confirmation impossible : $e") }
            }
            PackageInstaller.STATUS_SUCCESS -> log.write("maj", "installée")
            else -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "code $status"
                log.write("maj", "échec : $message")
                val text = if (status == PackageInstaller.STATUS_FAILURE_ABORTED) tr("Installation annulée") else Updater.explain(message)
                Handler(Looper.getMainLooper()).post { Updater.onInstallError?.invoke(text) }
            }
        }
    }
}

/** Après une mise à jour : on note la nouvelle version et on relance l'écran d'accueil. */
class UpdatedReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val app = ToqueApp.of(c)
        val version = try { c.packageManager.getPackageInfo(c.packageName, 0).versionName } catch (_: Exception) { "?" }
        app.log.write("maj", "Camybrith Cooking mis à jour en version $version")
        try {
            c.startActivity(Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
        }
    }
}
