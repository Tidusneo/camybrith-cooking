package app.toque

import android.os.Handler
import android.os.Looper
import app.toque.core.i18n.tr
import app.toque.core.machine.Snapshot
import app.toque.core.mqtt.MiniMqtt
import app.toque.core.mqtt.RobotStatus

/**
 * Publication de l'état du robot vers Home Assistant, en MQTT. Lecture seule : Camybrith ne
 * s'abonne à aucun sujet, rien venant du réseau ne peut démarrer le moteur ou la chauffe.
 *
 * Désactivé par défaut. Une seconde au plus entre deux envois ; seuls les sujets qui ont changé
 * partent, avec l'option « conservé » pour que Home Assistant ait l'état dès sa connexion.
 */
class HomeAssistant(private val app: ToqueApp) {
    @Volatile private var latest: Snapshot? = null
    @Volatile private var worker: Thread? = null
    @Volatile var status: String = ""
        private set
    var listener: ((String) -> Unit)? = null
    private val main = Handler(Looper.getMainLooper())
    private var unsubscribe: (() -> Unit)? = null

    /** (Re)lance selon les réglages : à l'ouverture de l'appli et après un enregistrement. */
    @Synchronized
    fun restart() {
        worker?.interrupt()
        worker = null
        unsubscribe?.invoke()
        unsubscribe = null
        val p = app.prefs
        if (!p.mqttEnabled || p.mqttHost.isBlank()) {
            setStatus(if (p.mqttEnabled) tr("Indiquez l'adresse du serveur MQTT") else "")
            return
        }
        unsubscribe = app.machine.onSnapshot { latest = it }
        val t = Thread({ run(p.mqttHost.trim(), p.mqttPort, p.mqttUser, p.mqttPassword, p.mqttTopic.trim().trimEnd('/').ifEmpty { "camybrith" }) }, "home-assistant")
        t.isDaemon = true
        worker = t
        t.start()
    }

    private fun versionName(): String =
        try { app.packageManager.getPackageInfo(app.packageName, 0).versionName ?: "?" } catch (_: Exception) { "?" }

    private fun setStatus(s: String) {
        status = s
        main.post { listener?.invoke(s) }
    }

    private fun run(host: String, port: Int, user: String, password: String, base: String) {
        val me = Thread.currentThread()
        var wait = 5_000L
        while (worker === me) {
            val m = MiniMqtt(host, port, "camybrith-" + base.replace('/', '-'), user, password, "$base/availability")
            try {
                setStatus(tr("Connexion à %s…", host))
                m.connect()
                app.log.write("mqtt", "connecté à $host:$port")
                m.publish("$base/availability", "online")
                for ((topic, payload) in RobotStatus.discovery(base, versionName(), app.prefs.activeRobot.label)) m.publish(topic, payload)
                setStatus(tr("Connecté à %s", host))
                wait = 5_000L
                val sent = HashMap<String, String>()
                var lastPing = System.currentTimeMillis()
                while (worker === me) {
                    latest?.let { s ->
                        for ((k, v) in RobotStatus.values(s)) if (sent[k] != v) { m.publish("$base/$k", v); sent[k] = v }
                    }
                    if (System.currentTimeMillis() - lastPing > m.keepAliveSec * 500L) { m.ping(); lastPing = System.currentTimeMillis() }
                    Thread.sleep(1_000)
                }
                try { m.publish("$base/availability", "offline") } catch (_: Exception) {}
                m.close()
                return
            } catch (_: InterruptedException) {
                try { m.publish("$base/availability", "offline") } catch (_: Exception) {}
                m.close()
                return
            } catch (e: Exception) {
                m.close()
                if (worker !== me) return
                val why = e.message ?: e.javaClass.simpleName
                app.log.write("mqtt", "échec : $why")
                setStatus(tr("Pas de connexion : %s. Nouvel essai dans %d s.", why, (wait / 1000).toInt()))
                try { Thread.sleep(wait) } catch (_: InterruptedException) { return }
                wait = (wait * 2).coerceAtMost(120_000L)
            }
        }
    }
}
