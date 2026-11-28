package app.toque

import android.content.Context
import android.content.pm.ApplicationInfo
import app.toque.core.machine.RootShell

/**
 * Garde-fou de la version rapide. Sur le Smart, la compilation « au fil de l'eau » d'Android 8.1
 * (JIT) recompile de travers des fonctions très appelées : tr() renvoyait null et l'interface
 * plantait en boucle dès l'ouverture (28/11/2026, bêta installée avec « adb install » sans l'étape
 * de compilation). L'installateur et la mise à jour par Réglages compilent toute l'appli d'avance
 * (« cmd package compile -m speed ») ; ici, au démarrage, on vérifie que c'est bien fait et,
 * sinon, on le fait avec le root, puis on rouvre l'appli.
 */
object CompileGuard {
    private const val LOCK = "/data/local/tmp/camybrith-compilation.lock"

    /** Vrai si une compilation vient d'être lancée : l'interface l'annonce et attend. */
    @Volatile var compiling = false
        private set

    /**
     * À appeler au démarrage, avant d'ouvrir l'interface. Une seule vérification par version
     * installée (quelques centaines de millisecondes) : ensuite, plus rien.
     */
    fun check(context: Context, log: Logbook) {
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_VM_SAFE_MODE != 0) return // prudente
        if (android.os.Build.VERSION.SDK_INT < 24) return // Android 6 compile déjà à l'installation
        val pkg = context.packageName
        val store = context.getSharedPreferences("compilation", Context.MODE_PRIVATE)
        @Suppress("DEPRECATION")
        val version = try { context.packageManager.getPackageInfo(pkg, 0).versionCode } catch (_: Exception) { -1 }
        if (store.getInt("verifiee", 0) == version) return
        try {
            if (!RootShell.available()) return
            val state = RootShell.run("dumpsys package $pkg | grep -o 'compilation_filter=[a-z-]*'", 8_000).out
            // « speed » ou « everything » exactement : « quicken » et « speed-profile » laissent le JIT
            // recompiler au fil de l'eau, ce qui est précisément ce qui casse tr() sur ce robot.
            val levels = state.split(Regex("\\s+")).filter { it.startsWith("compilation_filter=") }.map { it.removePrefix("compilation_filter=") }
            if (levels.isNotEmpty() && levels.all { it == "speed" || it == "everything" }) {
                store.edit().putInt("verifiee", version).apply()
                return
            }
            // Une compilation déjà en cours (verrou de moins de 5 minutes) : on attend la réouverture.
            if (RootShell.run("find $LOCK -mmin -5 2>/dev/null", 5_000).out.trim().isNotEmpty()) {
                compiling = true
                return
            }
            // État illisible, ou compilation déjà tentée pour cette version : ne jamais boucler.
            if (levels.isEmpty() || store.getInt("tentee", 0) == version) {
                log.write("interface", "compilation non confirmée (${state.trim().ifEmpty { "état illisible" }}) : on continue sans")
                store.edit().putInt("verifiee", version).apply()
                return
            }
            compiling = true
            store.edit().putInt("tentee", version).commit()
            log.write("interface", "appli pas compilée d'avance (${state.trim().ifEmpty { "état inconnu" }}) : compilation puis réouverture")
            RootShell.run(
                "( touch $LOCK; cmd package compile -m speed -f $pkg > /dev/null 2>&1; rm -f $LOCK; " +
                    "am force-stop $pkg; sleep 1; am start -n $pkg/app.toque.ui.MainActivity ) < /dev/null > /dev/null 2>&1 &",
                10_000,
            )
        } catch (e: Exception) {
            log.write("interface", "vérification de la compilation impossible : $e")
        }
    }
}
