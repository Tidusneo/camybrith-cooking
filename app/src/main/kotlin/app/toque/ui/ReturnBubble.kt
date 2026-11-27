package app.toque.ui

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import app.toque.Prefs
import app.toque.core.i18n.tr
import app.toque.core.machine.RootShell

/**
 * Bouton flottant « Toque », affiché par-dessus l'appli d'origine. Le robot n'a ni bouton
 * Accueil ni barre de navigation : c'est le seul moyen de revenir à Toque. On peut le déplacer.
 */
class ReturnBubble : Service() {
    private var bubble: View? = null
    private lateinit var wm: WindowManager
    /**
     * Spec 007, Lidl mode (robot back on the Lidl app): the bubble is the only way back to
     * Camybrith, so it can be moved or folded into a tab on the edge, but never closed.
     */
    private var lidlMode = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.getBooleanExtra(EXTRA_LIDL_MODE, false) == true) {
            lidlMode = true
            // Started as root by the rescue module while Camybrith is in the background: Android 8
            // only allows it as a foreground service, which needs a notification.
            startAsForeground()
        }
        // Relancé par le système alors que Toque est déjà au premier plan : rien à afficher.
        if (!lidlMode && !Prefs(this).returnBubble) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (bubble == null && !show()) stopSelf()
        return START_STICKY
    }

    private fun show(): Boolean {
        if (!canShow(this)) return false
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val dm = resources.displayMetrics
        Ui.init(dm.widthPixels, dm.heightPixels)

        val v = LinearLayout(this)
        v.orientation = LinearLayout.HORIZONTAL
        v.setGravity(Gravity.CENTER_VERTICAL)
        v.setPadding(Ui.u(18), Ui.u(12), Ui.u(22), Ui.u(12))
        v.background = Ui.round(Ui.ACCENT, 32f, 0x66000000, 2f)
        v.elevation = Ui.uf(8f)
        v.addView(Icon(this, Icon.Kind.BACK, DARK), LinearLayout.LayoutParams(Ui.u(26), Ui.u(26)).apply { rightMargin = Ui.u(8) })
        val label = Ui.text(this, if (lidlMode) "Camybrith" else tr("Retour à Camybrith Cooking"), 19f, DARK, Ui.MEDIUM)
        v.addView(label)

        @Suppress("DEPRECATION")
        val type = if (Build.VERSION.SDK_INT >= 26) TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        )
        lp.gravity = Gravity.TOP or Gravity.START
        val prefs = getSharedPreferences(POSITION, Context.MODE_PRIVATE)
        lp.x = prefs.getInt("x", Ui.u(16))
        lp.y = prefs.getInt("y", dm.heightPixels - Ui.u(84))
        // Folded into a tab on the left edge (Lidl mode only): just the arrow, half hidden.
        fun applyFolded(folded: Boolean) {
            label.visibility = if (folded) View.GONE else View.VISIBLE
            if (folded) lp.x = 0
            try { if (bubble != null) wm.updateViewLayout(v, lp) } catch (_: RuntimeException) {}
        }
        if (lidlMode) applyFolded(prefs.getBoolean(FOLDED, false))
        var downAt = 0L

        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var dragging = false
        v.setOnTouchListener { view, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY; startX = lp.x; startY = lp.y; dragging = false
                    downAt = e.eventTime
                    view.alpha = 0.85f
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (!dragging && dx * dx + dy * dy > Ui.u(12) * Ui.u(12)) dragging = true
                    if (dragging) {
                        lp.x = maxOf(0, startX + dx.toInt())
                        lp.y = maxOf(0, startY + dy.toInt())
                        try {
                            wm.updateViewLayout(view, lp)
                        } catch (_: RuntimeException) {
                        }
                    }
                }
                MotionEvent.ACTION_UP -> {
                    view.alpha = 1f
                    if (dragging) prefs.edit().putInt("x", lp.x).putInt("y", lp.y).apply()
                    else if (lidlMode && e.eventTime - downAt >= LONG_PRESS_MS) {
                        val folded = !prefs.getBoolean(FOLDED, false)
                        prefs.edit().putBoolean(FOLDED, folded).apply()
                        applyFolded(folded)
                    } else backToToque()
                }
                MotionEvent.ACTION_CANCEL -> view.alpha = 1f
            }
            true
        }
        return try {
            wm.addView(v, lp)
            bubble = v
            true
        } catch (_: RuntimeException) {
            false
        }
    }

    private fun backToToque() {
        startActivity(Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
        // In Lidl mode the bubble stays: MainActivity stops it while it is in front and starts it
        // again when it leaves, so there is always a way back.
        if (!lidlMode) stopSelf()
    }

    private fun startAsForeground() {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.createNotificationChannel(android.app.NotificationChannel(CHANNEL, "Camybrith", android.app.NotificationManager.IMPORTANCE_MIN))
        val n = android.app.Notification.Builder(this, CHANNEL)
            .setSmallIcon(app.toque.R.drawable.ic_launcher)
            .setContentTitle(tr("Robot sur l'appli Lidl"))
            .setContentText(tr("Touchez la bulle « Camybrith » pour revenir à Camybrith Cooking."))
            .build()
        startForeground(NOTIFICATION_ID, n)
    }

    override fun onDestroy() {
        bubble?.let {
            try {
                wm.removeView(it)
            } catch (_: RuntimeException) {
            }
        }
        bubble = null
        super.onDestroy()
    }

    companion object {
        private const val POSITION = "bulle"
        private const val FOLDED = "reduite"
        private const val LONG_PRESS_MS = 600L
        private const val CHANNEL = "bulle"
        private const val NOTIFICATION_ID = 7
        /** Extra set by the rescue module (spec 007, research.md D14). */
        const val EXTRA_LIDL_MODE = "modeLidl"
        private const val DARK = 0xFF1A0E05.toInt()
        /** WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY (API 26), valeur fixe pour compiler en API 23. */
        private const val TYPE_APPLICATION_OVERLAY = 2038

        fun canShow(c: Context): Boolean = Settings.canDrawOverlays(c)

        /**
         * Autorise l'affichage par-dessus les autres applis : accordé par root (Android 6 le
         * demande sinon dans ses paramètres, inaccessibles sur le robot). Hors du fil principal.
         */
        fun ensurePermission(c: Context): Boolean {
            if (canShow(c)) return true
            RootShell.run("appops set ${c.packageName} SYSTEM_ALERT_WINDOW allow", 8_000)
            return canShow(c)
        }

        fun start(c: Context) {
            c.startService(Intent(c, ReturnBubble::class.java))
        }

        /** The bubble of Lidl mode, which cannot be closed (spec 007). */
        fun startLidlMode(c: Context) {
            val i = Intent(c, ReturnBubble::class.java).putExtra(EXTRA_LIDL_MODE, true)
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i) else c.startService(i)
        }

        fun stop(c: Context) {
            c.stopService(Intent(c, ReturnBubble::class.java))
        }
    }
}
