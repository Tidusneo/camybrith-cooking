package app.toque.machine

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * Activité trampoline pour sortir le package du pilote de l'état « arrêté » (stopped)
 * après une première installation ou mise à jour, et démarrer le service en premier plan.
 * Protégée par la permission signature app.toque.permission.PILOTER.
 */
class PiloteLauncher : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            val i = Intent(this, MachineService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                startForegroundService(i)
            } else {
                startService(i)
            }
        } catch (_: Exception) {
        }
        finish()
    }
}
