package app.toque.machine

import app.toque.Prefs
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import app.toque.core.i18n.I18n
import app.toque.core.i18n.tr
import app.toque.core.machine.Accessory
import app.toque.core.machine.EventType
import app.toque.core.machine.JobSpec
import app.toque.core.machine.LinkState
import app.toque.core.machine.MachineEvent
import app.toque.core.machine.Snapshot
import app.toque.core.machine.Verdict

/**
 * Côté interface : liaison avec le pilote moteur (appli app.toque.pilote, service MachineService).
 * Tous les rappels arrivent sur le fil principal.
 */
class MachineClient(private val context: Context) {
    var snapshot = Snapshot()
        private set
    val connected: Boolean get() = service != null

    private val snapshotListeners = ArrayList<(Snapshot) -> Unit>()
    private val eventListeners = ArrayList<(MachineEvent) -> Unit>()
    private val pending = HashMap<Int, (Verdict) -> Unit>()
    private var nextId = 1
    private var service: Messenger? = null
    private var bound = false
    private val main = Handler(Looper.getMainLooper())
    private val logWaiting = ArrayList<(String) -> Unit>()

    /** Version du pilote, connue après la liaison (« » tant qu'il n'a pas répondu). */
    var pilotVersion = ""
        private set
    /** Le pilote parle une autre version des messages : il faut installer les deux applis ensemble. */
    var pilotMismatch = false
        private set
    /** Pilote absent : l'appli app.toque.pilote n'est pas installée. */
    var pilotMissing = false
        private set

    /** Réglages de pilotage tirés des réglages de l'interface. */
    fun config(): PilotConfig {
        val p = Prefs(context)
        return PilotConfig(
            mode = p.mode.key, model = p.robotModel.key, twinHost = p.twinHost, twinPort = p.twinPort,
            serialDevice = p.serialDevice, vendorPackage = p.vendorPackage, requireBowl = p.requireBowl, hotRamp = p.hotRamp,
        )
    }

    private val incoming = Messenger(Handler(Looper.getMainLooper()) { msg ->
        when (msg.what) {
            Ipc.SNAPSHOT -> {
                snapshot = Ipc.bundleToSnapshot(msg.data)
                for (l in ArrayList(snapshotListeners)) l(snapshot)
            }
            Ipc.EVENT -> {
                val type = try { EventType.valueOf(msg.data.getString("type") ?: "") } catch (_: Exception) { null }
                if (type != null) {
                    val e = MachineEvent(type, msg.data.getString("message") ?: "")
                    for (l in ArrayList(eventListeners)) l(e)
                }
            }
            Ipc.REPLY -> pending.remove(msg.arg1)?.invoke(Ipc.bundleToVerdict(msg.data))
            Ipc.HELLO -> {
                pilotVersion = msg.data.getString("version").orEmpty()
                pilotMismatch = msg.data.getInt("protocol") != Ipc.PROTOCOL
            }
            Ipc.LOG_TEXT -> {
                val text = msg.data.getString("text").orEmpty()
                val waiting = ArrayList(logWaiting)
                logWaiting.clear()
                for (w in waiting) w(text)
            }
        }
        true
    })

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = Messenger(binder)
            sendRaw(Ipc.REGISTER, Bundle().apply { putBundle("config", config().toBundle()) }, null)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            snapshot = snapshot.copy(link = LinkState.OFFLINE, transportUp = false)
            for (l in ArrayList(snapshotListeners)) l(snapshot)
            // Android relance le service (START_STICKY) et rétablit la liaison tout seul.
        }
    }

    fun connect() {
        if (bound) return
        val intent = pilotIntent()
        pilotMissing = try {
            context.packageManager.getPackageInfo(Ipc.PILOT_PACKAGE, 0)
            false
        } catch (_: Exception) {
            true
        }
        if (pilotMissing) return
        startPilot(context)
        fun tryBind(attempt: Int) {
            if (bound) return
            try {
                bound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE or Context.BIND_IMPORTANT)
            } catch (_: Exception) {
            }
            if (!bound && attempt < 10) {
                main.postDelayed({ tryBind(attempt + 1) }, 600)
            }
        }
        tryBind(1)
    }

    /** Diffuse un état sans passer par le service : pour les captures d'écran de l'interface. */
    fun preview(s: Snapshot) {
        snapshot = s
        for (l in ArrayList(snapshotListeners)) l(s)
    }

    fun onSnapshot(l: (Snapshot) -> Unit): () -> Unit {
        snapshotListeners += l
        l(snapshot)
        return { snapshotListeners -= l }
    }

    fun onEvent(l: (MachineEvent) -> Unit): () -> Unit {
        eventListeners += l
        return { eventListeners -= l }
    }

    private fun sendRaw(what: Int, data: Bundle?, cb: ((Verdict) -> Unit)?) {
        val s = service
        if (s == null) {
            cb?.let { c -> main.post { c(Verdict.refuse("ipc", tr("Service machine indisponible, nouvelle tentative…"))) } }
            connect()
            return
        }
        val id = nextId++
        if (cb != null) {
            pending[id] = cb
            main.postDelayed({
                pending.remove(id)?.invoke(Verdict.refuse("timeout", tr("La base ne répond pas")))
            }, 5_000)
        }
        val m = Message.obtain(null, what, id, 0)
        m.replyTo = incoming
        // La langue voyage avec chaque commande : le processus machine rédige ses messages dans la même.
        m.data = (data ?: Bundle()).apply { putString(Ipc.LANG, I18n.code) }
        try {
            s.send(m)
        } catch (e: RemoteException) {
            pending.remove(id)?.invoke(Verdict.refuse("ipc", tr("Liaison avec le service perdue")))
        }
    }

    fun start(spec: JobSpec, cb: (Verdict) -> Unit) = sendRaw(Ipc.START_JOB, Ipc.jobToBundle(spec), cb)
    fun pause(cb: (Verdict) -> Unit = {}) = sendRaw(Ipc.PAUSE, null, cb)
    fun resume(hot: Boolean = false, cb: (Verdict) -> Unit) =
        sendRaw(Ipc.RESUME, Bundle().apply { putBoolean("hot", hot) }, cb)
    fun stop(cb: (Verdict) -> Unit = {}) = sendRaw(Ipc.STOP, null, cb)
    fun clearFinished() = sendRaw(Ipc.CLEAR, null, null)
    fun tare() = sendRaw(Ipc.TARE, null, null)
    fun acknowledge(cb: (Verdict) -> Unit) = sendRaw(Ipc.ACK_FAULT, null, cb)
    fun turboDown(accessory: Accessory, cb: (Verdict) -> Unit) =
        sendRaw(Ipc.TURBO_DOWN, Bundle().apply { putString("accessory", accessory.name) }, cb)
    fun turboUp() = sendRaw(Ipc.TURBO_UP, null, null)
    fun reconfigure(cb: (Verdict) -> Unit) = sendRaw(Ipc.RECONFIGURE, Bundle().apply { putBundle("config", config().toBundle()) }, cb)

    /** Journal du pilote (qui écrit le sien, dans son appli). Vide si le pilote ne répond pas. */
    fun readLog(cb: (String) -> Unit) {
        if (service == null) { cb(""); return }
        logWaiting += cb
        sendRaw(Ipc.READ_LOG, null, null)
        main.postDelayed({ if (logWaiting.remove(cb)) cb("") }, 3_000)
    }

    fun clearLog() = sendRaw(Ipc.CLEAR_LOG, null, null)
    fun suspendPort(cb: (Verdict) -> Unit) = sendRaw(Ipc.SUSPEND_PORT, null, cb)
    fun resumePort(cb: (Verdict) -> Unit = {}) = sendRaw(Ipc.RESUME_PORT, null, cb)

    fun adjust(speed: Int? = null, temp: Int? = null, reverse: Boolean? = null, totalMs: Long? = null,
               hot: Boolean = false, cb: (Verdict) -> Unit) {
        val b = Bundle()
        speed?.let { b.putInt("speed", it) }
        temp?.let { b.putInt("temp", it) }
        reverse?.let { b.putBoolean("reverse", it) }
        totalMs?.let { b.putLong("totalMs", it) }
        b.putBoolean("hot", hot)
        sendRaw(Ipc.ADJUST, b, cb)
    }

    /** Commandes de la base simulée (mode démo). */
    fun simulate(lid: Boolean? = null, error: Int? = null, weight: Int? = null) {
        val b = Bundle()
        lid?.let { b.putBoolean("lid", it) }
        error?.let { b.putInt("error", it) }
        weight?.let { b.putInt("weight", it) }
        sendRaw(Ipc.SIM, b, null)
    }
}

fun pilotIntent(): Intent = Intent().setComponent(ComponentName(Ipc.PILOT_PACKAGE, Ipc.PILOT_SERVICE))

/** Démarre le pilote (service au premier plan sur Android 8 : autorisé même en arrière-plan). */
fun startPilot(c: Context) {
    try {
        if (android.os.Build.VERSION.SDK_INT >= 26) c.startForegroundService(pilotIntent()) else c.startService(pilotIntent())
    } catch (_: Exception) {
    }
}
