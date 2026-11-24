package app.toque

import android.app.ActivityManager
import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Process
import app.toque.core.i18n.I18n
import app.toque.core.machine.TemperatureTable
import app.toque.machine.MachineClient
import app.toque.recipes.RecipeRepo
import app.toque.ui.MainActivity
import java.util.Locale

class ToqueApp : Application() {
    lateinit var prefs: Prefs
        private set
    lateinit var log: Logbook
        private set
    val machine: MachineClient by lazy { MachineClient(this) }
    val recipes: RecipeRepo by lazy { RecipeRepo(this) }
    val homeAssistant: HomeAssistant by lazy { HomeAssistant(this) }
    /** Dernière anomalie du compilateur repérée (vide si aucune) : affichée par l'interface. */
    @Volatile var compilerProblem = ""
    val compilerWatch: CompilerWatch by lazy { CompilerWatch(log) { what -> compilerProblem = what; compilerListener?.invoke(what) } }
    var compilerListener: ((String) -> Unit)? = null

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        log = Logbook(this)
        TemperatureTable.current = prefs.activeRobot.temperatures
        LanguageFiles.install(this, log)
        I18n.select(I18n.resolve(prefs.language, Locale.getDefault().language).code)
        if (!isMainProcess()) return
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                log.crash("interface", error)
                val now = System.currentTimeMillis()
                val recent = now - prefs.lastCrashMs < 10_000
                prefs.lastCrashMs = now
                if (recent) throw error // évite une boucle de relances
                // Relance discrète de l'interface ; le processus machine, lui, continue.
                val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                val pi = PendingIntent.getActivity(this, 1, intent, PendingIntent.FLAG_ONE_SHOT)
                (getSystemService(Context.ALARM_SERVICE) as AlarmManager).set(AlarmManager.RTC, System.currentTimeMillis() + 800, pi)
                Process.killProcess(Process.myPid())
                System.exit(10)
            } catch (_: Throwable) {
                previous?.uncaughtException(thread, error)
            }
        }
        log.write("interface", "démarrage")
        TimeSync.start(this, prefs, log)
        homeAssistant.restart()
        // Version prudente (tout interprété) : rien à surveiller.
        if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_VM_SAFE_MODE == 0) compilerWatch.start()
    }

    private fun isMainProcess(): Boolean {
        val pid = Process.myPid()
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val name = am.runningAppProcesses?.firstOrNull { it.pid == pid }?.processName ?: return true
        return name == packageName
    }

    companion object {
        fun of(c: Context): ToqueApp = c.applicationContext as ToqueApp
    }
}
