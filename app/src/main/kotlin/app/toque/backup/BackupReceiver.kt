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
                BackupWriter.create(c.applicationContext)
            } finally {
                pending.finish()
            }
        }.start()
    }
}
