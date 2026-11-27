package app.toque.backup

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Started as root by `camybrith sauvegarder` (spec 007, T054): creates a backup in the background.
 * Not exported: only root (the rescue module) can reach it.
 */
class BackupReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, intent: Intent) {
        val pending = goAsync()
        Thread {
            try {
                val restore = intent.getStringExtra(EXTRA_RESTORE)
                // Restoring is done from the interface (contracts/ui.md); this path exists only
                // for the emulator test bench (B8), recognised by a debug property nobody sets on
                // the robot (readable without root).
                if (restore != null && benchProperty())
                    BackupRestorer.restore(c.applicationContext, restore, intent.getBooleanExtra(EXTRA_BENCH_STOP, false))
                else BackupWriter.create(c.applicationContext)
            } finally {
                pending.finish()
            }
        }.start()
    }

    private fun benchProperty(): Boolean = try {
        Runtime.getRuntime().exec(arrayOf("getprop", BENCH_PROPERTY)).inputStream.bufferedReader().readText().trim() == "1"
    } catch (_: Exception) {
        false
    }

    companion object {
        const val BENCH_PROPERTY = "debug.camybrith.bench"
        const val EXTRA_RESTORE = "restaurer"
        const val EXTRA_BENCH_STOP = "arret-apres-transaction"
    }
}
