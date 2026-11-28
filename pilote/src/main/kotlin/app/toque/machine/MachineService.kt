package app.toque.machine

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import app.toque.Logbook
import app.toque.core.i18n.I18n
import app.toque.core.i18n.tr
import app.toque.core.machine.Accessory
import app.toque.core.machine.ControllerConfig
import app.toque.core.machine.JobState
import app.toque.core.machine.MachineEvent
import app.toque.core.machine.MachineLoop
import app.toque.core.machine.MonotonicClock
import app.toque.core.machine.RobotModel
import app.toque.core.machine.RootSerialTransport
import app.toque.core.machine.TemperatureTable
import app.toque.core.machine.SimBoard
import app.toque.core.machine.SimTransport
import app.toque.core.machine.Snapshot
import app.toque.core.machine.TcpTransport
import app.toque.core.machine.Transport
import app.toque.core.machine.Verdict
import app.toque.core.protocol.Direction
import app.toque.core.smart.SmartController
import app.toque.core.smart.SmartSimBoard
import app.toque.core.smart.SmartSimTransport

/**
 * Pilote moteur : appli à part (app.toque.pilote), interprétée (vmSafeMode) parce que le
 * compilateur d'Android 8.1 du Smart recompile parfois des fonctions de travers. Un plantage de
 * l'interface n'interrompt jamais un programme en cours, qui se termine et s'arrête normalement.
 * Sans réglages reçus de l'interface, il démarre en démo : il ne touche jamais au robot sans
 * qu'on le lui ait demandé.
 */
class MachineService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private val clients = ArrayList<Messenger>()
    private var loop: MachineLoop? = null
    private var sim: SimBoard? = null
    private var smartSim: SmartSimBoard? = null
    private var lastSent: Snapshot? = null
    private var lastSentAt = 0L
    private var lastNotified = ""
    private var config = PilotConfig()
    private lateinit var log: Logbook

    private val messenger = Messenger(Handler(Looper.getMainLooper()) { msg ->
        try {
            handle(msg)
        } catch (t: Throwable) {
            log.write("machine", "message ${msg.what} en échec : $t")
        }
        true
    })

    override fun onCreate() {
        super.onCreate()
        log = Logbook(this)
        config = PilotConfig.load(this) ?: PilotConfig()
        loadLanguages()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                loop?.emergencyStop()
                log.crash("machine", error)
            } catch (_: Throwable) {
            }
            previous?.uncaughtException(thread, error)
        }
        startForeground(NOTIFICATION_ID, notification(tr("Base moteur"), tr("Démarrage…")))
        createLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    override fun onDestroy() {
        loop?.shutdown()
        loop = null
        super.onDestroy()
    }

    private fun createLoop() {
        loop?.shutdown()
        val cfg = config
        val mode = cfg.mode
        // Le jumeau numérique du SDK ne parle que le protocole du Connect.
        val model = if (mode == "twin") RobotModel.CONNECT else RobotModel.of(cfg.model) ?: RobotModel.SMART
        TemperatureTable.current = model.temperatures
        sim = null
        smartSim = null
        val factory: () -> Transport = when (mode) {
            "twin" -> {
                val host = cfg.twinHost
                val port = cfg.twinPort
                ({ TcpTransport(host, port) })
            }
            "hardware" -> {
                val dev = cfg.serialDevice.ifBlank { model.serialDevice }
                val vendor = cfg.vendorPackage.ifBlank { model.vendorPackage }
                ({ RootSerialTransport(dev, model.baud, vendor) })
            }
            else -> if (model.isSmart) {
                val board = SmartSimBoard(MonotonicClock)
                smartSim = board
                ({ SmartSimTransport(board) })
            } else {
                val board = SimBoard(MonotonicClock)
                sim = board
                ({ SimTransport(board) })
            }
        }
        val ctl = ControllerConfig(requireBowl = cfg.requireBowl, hotRamp = cfg.hotRamp)
        val l = MachineLoop(
            transportFactory = factory,
            config = ctl,
            periodMs = model.periodMs,
            onSnapshot = { s -> main.post { publish(s) } },
            onEvent = { e -> main.post { broadcastEvent(e) } },
            log = { line -> log.write("machine", line) },
            controllerFactory = { clock ->
                model.controller(clock, ctl).also { c ->
                    (c as? SmartController)?.diag = { line -> log.write("smart", line) }
                }
            },
        )
        loop = l
        l.start()
        log.write("machine", "boucle démarrée en mode $mode, ${model.label}")
    }

    private fun publish(s: Snapshot) {
        val now = System.currentTimeMillis()
        val prev = lastSent
        val changed = prev == null || prev.copy(sampleAgeMs = 0, txFrames = 0, rxFrames = 0, txHex = "") !=
            s.copy(sampleAgeMs = 0, txFrames = 0, rxFrames = 0, txHex = "")
        if (!changed && now - lastSentAt < 1_000) return
        lastSent = s
        lastSentAt = now
        val m = Message.obtain(null, Ipc.SNAPSHOT)
        m.data = Ipc.snapshotToBundle(s)
        send(m)
        updateNotification(s)
    }

    private fun updateNotification(s: Snapshot) {
        val text = when {
            s.hasFault -> tr("Alerte : %s", s.faultMessage)
            s.state == JobState.RUNNING -> tr("%s · reste %s", s.jobLabel, formatClock(s.remainingMs))
            s.state == JobState.PAUSED -> tr("%s en pause", s.jobLabel)
            else -> s.link.label + if (s.transportName.isNotEmpty()) " · ${s.transportName}" else ""
        }
        if (text == lastNotified) return
        lastNotified = text
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, notification(tr("Base moteur"), text))
    }

    private fun broadcastEvent(e: MachineEvent) {
        val m = Message.obtain(null, Ipc.EVENT)
        m.data = Bundle().apply {
            putString("type", e.type.name)
            putString("message", e.message)
        }
        send(m)
        log.write("évènement", "${e.type} ${e.message}")
    }

    private fun send(template: Message) {
        val dead = ArrayList<Messenger>()
        for (c in clients) {
            try {
                val m = Message.obtain(template)
                c.send(m)
            } catch (_: RemoteException) {
                dead += c
            }
        }
        clients.removeAll(dead)
        template.recycle()
    }

    private fun reply(to: Messenger?, id: Int, v: Verdict) {
        if (to == null) return
        val m = Message.obtain(null, Ipc.REPLY, id, 0)
        m.data = Ipc.verdictToBundle(v)
        try {
            to.send(m)
        } catch (_: RemoteException) {
        }
    }

    private fun handle(msg: Message) {
        val l = loop ?: return
        val to = msg.replyTo
        val id = msg.arg1
        val data = msg.data
        data.getString(Ipc.LANG)?.let { if (it != I18n.code) I18n.select(it) }
        when (msg.what) {
            Ipc.REGISTER -> {
                if (to != null && clients.none { it.binder == to.binder }) clients += to
                lastSent = null
                hello(to)
                // Réglages de l'interface : appliqués s'ils ont changé et qu'aucun programme ne tourne.
                PilotConfig.fromBundle(data.getBundle("config"))?.let { cfg -> if (cfg != config) reconfigure(cfg, null, 0) }
            }
            Ipc.READ_LOG -> if (to != null) {
                val m = Message.obtain(null, Ipc.LOG_TEXT, id, 0)
                m.data = Bundle().apply { putString("text", log.read()) }
                try { to.send(m) } catch (_: RemoteException) {}
            }
            Ipc.CLEAR_LOG -> log.clear()
            Ipc.UNREGISTER -> clients.removeAll { it.binder == to?.binder }
            Ipc.START_JOB -> {
                val spec = Ipc.bundleToJob(data)
                if (spec == null) reply(to, id, Verdict.refuse("ipc", tr("Programme illisible")))
                else l.post { c -> reply(to, id, c.startJob(spec)) }
            }
            Ipc.PAUSE -> l.post { c -> reply(to, id, c.pause()) }
            Ipc.RESUME -> {
                val hot = data.getBoolean("hot")
                l.post { c -> reply(to, id, c.resume(hot)) }
            }
            Ipc.STOP -> l.post { c -> reply(to, id, c.stop()) }
            Ipc.CLEAR -> l.post { c -> c.clearFinished(); reply(to, id, Verdict.OK) }
            Ipc.ADJUST -> {
                val speed = if (data.containsKey("speed")) data.getInt("speed") else null
                val temp = if (data.containsKey("temp")) data.getInt("temp") else null
                val dir = if (data.containsKey("reverse"))
                    (if (data.getBoolean("reverse")) Direction.COUNTER_CLOCKWISE else Direction.CLOCKWISE) else null
                val total = if (data.containsKey("totalMs")) data.getLong("totalMs") else null
                val hot = data.getBoolean("hot")
                l.post { c -> reply(to, id, c.adjust(speed, temp, dir, total, hot)) }
            }
            Ipc.TURBO_DOWN -> {
                val acc = try { Accessory.valueOf(data.getString("accessory") ?: "") } catch (_: Exception) { Accessory.BLADE }
                l.post { c -> reply(to, id, c.turboPress(acc)) }
            }
            Ipc.TURBO_UP -> l.post { c -> c.turboRelease() }
            Ipc.TARE -> l.post { c -> c.tare() }
            Ipc.ACK_FAULT -> l.post { c -> reply(to, id, c.acknowledgeFault()) }
            Ipc.RECONFIGURE -> reconfigure(PilotConfig.fromBundle(data.getBundle("config")) ?: config, to, id)
            Ipc.SUSPEND_PORT -> Thread {
                val ok = try { l.suspendPort() } catch (_: Exception) { false }
                reply(to, id, if (ok) Verdict.OK else Verdict.refuse("busy", tr("Arrêtez d'abord le programme en cours")))
            }.start()
            Ipc.RESUME_PORT -> {
                l.resumePort()
                reply(to, id, Verdict.OK)
            }
            Ipc.SIM -> {
                val b = sim
                if (b != null) {
                    if (data.containsKey("lid")) b.lidOpen = data.getBoolean("lid")
                    if (data.containsKey("error")) b.errorCode = data.getInt("error")
                    if (data.containsKey("weight")) b.contentWeightG = data.getInt("weight")
                }
                val sb = smartSim
                if (sb != null) {
                    if (data.containsKey("lid")) sb.lidOpen = data.getBoolean("lid")
                    if (data.containsKey("error")) sb.errorCode = data.getInt("error")
                    if (data.containsKey("weight")) sb.contentWeightG = data.getInt("weight")
                }
            }
        }
    }

    private fun hello(to: Messenger?) {
        to ?: return
        val m = Message.obtain(null, Ipc.HELLO)
        m.data = Bundle().apply {
            putInt("protocol", Ipc.PROTOCOL)
            putString("version", try { packageManager.getPackageInfo(packageName, 0).versionName } catch (_: Exception) { "?" })
        }
        try { to.send(m) } catch (_: RemoteException) {}
    }

    /** Nouveaux réglages : refusés pendant un programme, sinon gardés et la boucle redémarre. */
    private fun reconfigure(cfg: PilotConfig, to: Messenger?, id: Int) {
        val l = loop
        Thread {
            val busy = try { l?.call { it.busy } ?: false } catch (_: Exception) { true }
            main.post {
                if (busy) {
                    reply(to, id, Verdict.refuse("busy", tr("Impossible pendant un programme")))
                } else {
                    config = cfg
                    cfg.save(this)
                    createLoop()
                    reply(to, id, Verdict.OK)
                }
            }
        }.start()
    }

    /** Langues : mêmes fichiers que l'interface (assets/lang), pour des messages traduits. */
    private fun loadLanguages() {
        try {
            val list = assets.list("lang").orEmpty().filter { it.endsWith(".json") }.map { f ->
                I18n.parse(f.removeSuffix(".json"), assets.open("lang/$f").bufferedReader().use { it.readText() })
            }
            I18n.install(list)
        } catch (t: Throwable) {
            log.write("machine", "langues : $t")
        }
    }

    private fun notification(title: String, text: String): Notification {
        val ui = Intent().setClassName(Ipc.UI_PACKAGE, Ipc.UI_ACTIVITY)
        val open = PendingIntent.getActivity(this, 0, ui, PendingIntent.FLAG_UPDATE_CURRENT)
        @Suppress("DEPRECATION")
        val b = if (Build.VERSION.SDK_INT >= 26) {
            ensureChannel()
            Notification.Builder::class.java.getConstructor(Context::class.java, String::class.java).newInstance(this, CHANNEL)
        } else Notification.Builder(this)
        return b.setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .setContentIntent(open)
            .build()
    }

    /** Canal de notification (Android 8+), créé par réflexion pour rester compatible Android 6. */
    private fun ensureChannel() {
        try {
            val cls = Class.forName("android.app.NotificationChannel")
            val channel = cls.getConstructor(String::class.java, CharSequence::class.java, Int::class.javaPrimitiveType)
                .newInstance(CHANNEL, tr("Base moteur"), 2)
            val nm = getSystemService(Context.NOTIFICATION_SERVICE)
            nm.javaClass.getMethod("createNotificationChannel", cls).invoke(nm, channel)
        } catch (_: Throwable) {
        }
    }

    companion object {
        private const val NOTIFICATION_ID = 7
        private const val CHANNEL = "machine"

        fun formatClock(ms: Long): String {
            val s = (ms + 999) / 1000
            return if (s >= 3600) String.format("%d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60)
            else String.format("%02d:%02d", s / 60, s % 60)
        }
    }
}
