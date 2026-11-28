package app.toque.core.smart

import app.toque.core.i18n.tr
import app.toque.core.machine.Accessory
import app.toque.core.machine.Clock
import app.toque.core.machine.EventType
import app.toque.core.machine.JobKind
import app.toque.core.machine.JobSpec
import app.toque.core.machine.JobState
import app.toque.core.machine.Limits
import app.toque.core.machine.LinkState
import app.toque.core.machine.MachineCore
import app.toque.core.machine.MachineEvent
import app.toque.core.machine.PauseReason
import app.toque.core.machine.Phase
import app.toque.core.machine.Programs
import app.toque.core.machine.SafetyPolicy
import app.toque.core.machine.Setting
import app.toque.core.machine.Snapshot
import app.toque.core.machine.TemperatureTable
import app.toque.core.machine.Verdict
import app.toque.core.machine.Violation
import app.toque.core.protocol.Direction
import app.toque.core.protocol.Mcc
import app.toque.core.protocol.Mcs
import app.toque.core.protocol.McsCommand
import app.toque.core.protocol.McsProtocolException
import app.toque.core.protocol.McsSequence
import app.toque.core.protocol.McsStreamParser
import app.toque.core.protocol.McsTelemetry
import app.toque.core.protocol.Operation

/*
 * Pilotage du Monsieur Cuisine Smart.
 *
 * Fondé sur ce que la base du robot de référence a montré (écoutes passives du 21/11/2026,
 * 3 338 réponses appariées une à une avec la commande qui les a provoquées) :
 *  - l'appli d'origine envoie la commande complète en continu (toutes les 200 ms à l'arrêt,
 *    environ 310 ms pendant une cuisson) ; Toque l'envoie toutes les 200 ms ;
 *  - chaque commande reçoit une réponse portant la même séquence, en 100 ms en général
 *    (321 ms au pire) : c'est l'accusé de réception ;
 *  - la réponse renvoie en écho opcode, vitesse, fonction, niveau de température et compte à
 *    rebours, identiques à la commande dans 100 % des cas : ce ne sont PAS des mesures ;
 *  - la base ne décompte pas le temps : l'appli tient la minuterie et envoie l'arrêt elle-même ;
 *  - température réelle par paliers de la table, 0 sous 37 °C, conservée après l'arrêt de la chauffe ;
 *  - bit 0x80 : couvercle ouvert ; bit 0x40 : bol absent ; poids en grammes, figé pendant la cuisson.
 *
 * Conséquences pour la sécurité :
 *  - sans mesure du moteur, un arrêt est confirmé par l'accusé d'une trame d'arrêt dont l'écho dit
 *    « vitesse 0, fonction 0 », puis par un délai de ralentissement avant tout changement de sens ;
 *  - le minuteur ne tourne qu'une fois l'écho du réglage demandé reçu ;
 *  - un écho différent de la commande, une réponse à une séquence jamais envoyée (un autre programme
 *    parle à la base, l'appli d'origine par exemple) ou une liaison muette arrêtent tout.
 *
 * Les niveaux de température de [JobSpec] sont ceux de la table du Smart (0..20, 40 °C compris) :
 * l'interface et les recettes utilisent [TemperatureTable.current], réglée sur le Smart.
 *
 * Acceptés, chacun vérifié sur une écoute de l'appli d'origine : cuisson manuelle (fonction 1, phases
 * de repos comprises), saisir/rissoler (fonction 4, cycle 5 s vitesse 1 / 30 s arrêt en sens inverse,
 * chauffe continue), vapeur (fonction 3, 120 °C, vitesse 1 en sens normal), malaxer/pétrir
 * (fonction 5, vitesse 4, sens selon la phase), Turbo (fonction 198, trame de 35 octets, 3 s au plus
 * par appui) et tare. La base répond fonction 1 à toutes sauf 0 et 198.
 *
 * Filet de sécurité de la base elle-même : sans commande pendant environ 4 s, elle arrête le moteur
 * (essai du 21/11/2026, appli d'origine fermée de force en pleine marche).
 */

/** Traduction des réglages de Toque vers la trame du Smart. */
object SmartWire {
    /** Sur le Smart, les niveaux de Toque sont ceux de sa propre table (0..20, voir [TemperatureTable.SMART]). */
    fun temperatureLevel(level: Int): Int {
        require(level in 0..Mcs.MAX_TEMPERATURE_LEVEL) { "niveau hors table du Smart" }
        return level
    }

    /** Vitesse affichée 0..10 → niveau moteur 0..20 (l'appli d'origine envoie le double). */
    fun speedLevel(speed: Int): Int = speed * 2

    fun reverse(direction: Direction): Boolean = direction == Direction.COUNTER_CLOCKWISE

    /** Fonction de la trame selon l'opération : seules celles observées sur le robot sont émises. */
    fun function(operation: Operation): Int = when (operation) {
        Operation.STANDARD -> Mcs.FN_MANUAL
        Operation.TOAST -> Mcs.FN_ROAST
        Operation.STEAM -> Mcs.FN_STEAM
        Operation.KNEAD -> Mcs.FN_KNEAD
        else -> throw IllegalArgumentException("opération non prise en charge sur le Smart : $operation")
    }

    /**
     * Vitesse affichée réellement envoyée. Vapeur : l'appli du Smart tourne à vitesse 1 en sens normal
     * pendant toute la cuisson (écoute du 21/11/2026) ; Toque fait de même sur le Smart, même si son
     * programme vapeur du Connect garde la lame à l'arrêt.
     */
    fun speed(setting: Setting): Int = if (setting.operation == Operation.STEAM) maxOf(1, setting.speed) else setting.speed
}

enum class SmartFaultCode {
    COMM_LOST, WRITE_FAILED, TRANSPORT_FAILED, BOARD_ERROR, BOARD_REFUSED, ECHO_MISMATCH, FOREIGN_TRAFFIC,
    STOP_TIMEOUT, START_TIMEOUT,
}

data class SmartFault(val code: SmartFaultCode, val message: String, val boardCode: Int = 0)

class SmartConfig(
    /** Âge maximal d'une réponse pour autoriser un démarrage. */
    val freshMs: Long = 800,
    /** Sans réponse depuis plus longtemps, la liaison est perdue et tout s'arrête. */
    val staleMs: Long = 1_500,
    /** Délai de perte de liaison au repos (sans programme en cours ni en pause). */
    val staleIdleMs: Long = staleMs,
    /** Envoyer constantTemperature dans les trames de consigne manuelle (profil R818). */
    val sendConstantTemperature: Boolean = false,
    /** Accusés consécutifs d'une trame d'arrêt exigés avant d'autoriser une commande. */
    val armingSamples: Int = 3,
    /** Délai pour obtenir l'accusé d'une trame d'arrêt. */
    val stopTimeoutMs: Long = 3_000,
    /** Délai pour obtenir l'écho du réglage demandé après un démarrage. */
    val startTimeoutMs: Long = 3_000,
    /** Temps laissé au couteau pour ralentir, après l'arrêt confirmé, avant de repartir dans l'autre sens. */
    val spinDownMs: Long = 2_000,
    /** Une pause plus longue annule le programme. */
    val pauseMaxMs: Long = 10 * 60_000L,
    /** Un retard d'ordonnanceur ne fait jamais avancer le minuteur de plus que ceci. */
    val maxTickGapMs: Long = 500,
    /** Refuser de démarrer, et mettre en pause, quand la base signale l'absence du bol. */
    val requireBowl: Boolean = true,
    /** Réponses à des séquences inconnues tolérées dans la fenêtre ci-dessous. */
    val foreignLimit: Int = 3,
    val foreignWindowMs: Long = 3_000,
)

/** Sous-vide : compte à rebours envoyé pendant le préchauffage (appli d'origine : 1 h) et durée maximale. */
private const val PREHEAT_COUNTDOWN_S = 3600
private const val PREHEAT_MAX_MS = 3_600_000L

/** Cœur de pilotage du Smart. Un seul fil d'exécution doit l'appeler, [tick] toutes les 200 ms. */
class SmartController(private val clock: Clock, var config: SmartConfig = SmartConfig()) : MachineCore {
    override var listener: ((MachineEvent) -> Unit)? = null
    /** Journal de diagnostic (trames en cas d'écho inattendu, coupures de la base). */
    var diag: ((String) -> Unit)? = null

    private val parser = McsStreamParser()
    private val frames = ArrayList<ByteArray>(4)
    private val sequence = McsSequence()

    /** Écho attendu pour chaque séquence envoyée et pas encore acquittée, dans l'ordre d'envoi. */
    private val sent = LinkedHashMap<Int, Expected>()
    private val foreign = ArrayDeque<Long>()

    private class Expected(
        val opcode: Int, val speedLevel: Int, val function: Int, val temperatureLevel: Int, val seconds: Int,
        val reverse: Boolean = false,
    ) {
        /** Trame envoyée, pour le journal de diagnostic. */
        var hex = ""
        val isStop: Boolean get() = opcode == Mcs.OP_CONFIG && speedLevel == 0 && function == Mcs.FN_STOP && temperatureLevel == 0
    }

    var latest: McsTelemetry? = null
        private set
    private var latestAtMs = Long.MIN_VALUE / 4

    var link = LinkState.OFFLINE
        private set
    private var armingCount = 0
    var fault: SmartFault? = null
        private set
    override var transportName = ""
    var transportUp = false
        private set

    /** Accusés d'arrêt reçus : barrière avant un démarrage ou un changement de sens. */
    private var stopAcks = 0L
    /** Dernier écho d'un réglage moteur en marche : le couteau peut encore tourner peu après. */
    private var lastRunAckAt = Long.MIN_VALUE / 4
    private var lastRunReverse = false

    /** Turbo maintenu : début de l'appui (null sinon). */
    private var turboStartedAt: Long? = null
    val turboActive: Boolean get() = turboStartedAt != null
    /** Accusés montrant le réglage en cours : le minuteur ne démarre qu'avec eux. */
    private var runAcks = 0L

    private var tarePending = false
    private var wire = Setting.STOP
    private var wireSeconds = 0
    private var lastLidOpen = false
    private var lastBowlAbsent = false

    var txFrames = 0L
        private set
    var rxFrames = 0L
        private set
    var lastTxHex = ""
        private set

    private class Barrier(val afterStopAcks: Long, val sinceMs: Long, val holdMs: Long = 0)

    private inner class ActiveJob(var spec: JobSpec, now: Long) {
        var state = JobState.STARTING
        var phaseIndex = 0
        var phaseElapsed = 0L
        var totalElapsed = 0L
        var lastUpdate = now
        var barrier: Barrier? = Barrier(stopAcks, now)
        var waitingStop: Barrier? = null
        /** Réglage envoyé, en attente de son écho (null une fois confirmé). */
        var awaitingRun: Long? = null
        var runAcksAtStart = 0L
        /** Compte à rebours envoyé pendant ce tronçon de marche, constant comme dans l'appli d'origine. */
        var runSeconds = 0
        var pauseReason: PauseReason? = null
        var pausedAt = 0L
        var endReason = ""
        var everStarted = false

        /** Programme propre au Smart (mixer, mijoter, sous-vide…), null pour un travail classique. */
        val program: SmartProgram? = SmartProgram.of(spec.smartProgram)
        var motion: SmartMotion? = null
        var preheating = program?.preheat == true
        var preheatSince = now

        val phase: Phase get() = spec.phases[phaseIndex]
        val active: Boolean get() = state == JobState.STARTING || state == JobState.RUNNING || state == JobState.PAUSED
        val ended: Boolean get() = state == JobState.FINISHED || state == JobState.ABORTED
    }

    private var job: ActiveJob? = null

    override val busy: Boolean get() = job?.ended == false || turboActive

    // ------------------------------------------------------------------ réception

    override fun onBytes(data: ByteArray, length: Int) {
        frames.clear()
        parser.feed(data, length, clock.nowMs(), frames)
        for (f in frames) onFrame(f)
    }

    val invalidFrames: Long get() = parser.invalidFrames

    private fun onFrame(raw: ByteArray) {
        val t = try {
            Mcs.decodeTelemetry(raw)
        } catch (e: McsProtocolException) {
            return
        }
        val now = clock.nowMs()
        rxFrames++
        latest = t
        latestAtMs = now

        val expected = sent[t.frame.sequence]
        if (expected != null) {
            // Les séquences envoyées avant celle-ci ne seront plus acquittées.
            val it = sent.keys.iterator()
            while (it.hasNext()) {
                val k = it.next()
                it.remove()
                if (k == t.frame.sequence) break
            }
        } else {
            foreign.addLast(now)
            while (foreign.isNotEmpty() && now - foreign.first() > config.foreignWindowMs) foreign.removeFirst()
            if (foreign.size >= config.foreignLimit && fault?.code != SmartFaultCode.FOREIGN_TRAFFIC)
                raiseFault(
                    SmartFaultCode.FOREIGN_TRAFFIC,
                    tr("Un autre programme parle à la base (l'appli d'origine ?) : tout est arrêté"),
                )
        }

        if (expected != null && !echoMatches(expected, t)) {
            if (boardStoppedForInterlock(expected, t)) {
                // Couvercle ouvert ou bol retiré en marche : la base a coupé d'elle-même et le dit dans
                // sa réponse (observé le 21/11/2026). C'est une pause, pas une panne : traitée plus bas.
                diag("la base coupe (couvercle ou bol) : TX ${expected.hex} RX ${t.hex}")
            } else {
                diag("écho différent : TX ${expected.hex} RX ${t.hex}")
                if (fault?.code != SmartFaultCode.ECHO_MISMATCH)
                    raiseFault(SmartFaultCode.ECHO_MISMATCH, tr("La base n'applique pas la commande envoyée : tout est arrêté"))
            }
        }
        val stopAck = expected != null && expected.isStop && t.speedLevel == 0 && t.function == Mcs.FN_STOP
        if (stopAck) stopAcks++
        if (expected != null && !expected.isStop && expected.opcode == Mcs.OP_CONFIG && echoMatches(expected, t)) {
            runAcks++
            if (expected.speedLevel > 0 || expected.function == Mcs.FN_TURBO) {
                lastRunAckAt = now
                lastRunReverse = expected.reverse
            }
        }

        when (link) {
            LinkState.OFFLINE, LinkState.LOST -> {
                armingCount = 0
                setLink(LinkState.ARMING)
                if (stopAck && t.errorCode == 0) armingCount = 1
            }
            LinkState.ARMING -> {
                if (stopAck && t.errorCode == 0) armingCount++ else if (expected != null) armingCount = 0
                if (armingCount >= config.armingSamples) setLink(LinkState.READY)
            }
            LinkState.READY -> Unit
        }

        if (t.lidOpen != lastLidOpen) {
            lastLidOpen = t.lidOpen
            if (t.lidOpen) {
                turboStartedAt = null
                emit(EventType.LID_OPENED, tr("Couvercle ouvert"))
                pauseIfRunning(PauseReason.LID, now, null)
            } else emit(EventType.LID_CLOSED, tr("Couvercle fermé"))
        }
        if (t.cupNtcAbsent != lastBowlAbsent) {
            lastBowlAbsent = t.cupNtcAbsent
            if (t.cupNtcAbsent && config.requireBowl) pauseIfRunning(PauseReason.SAFETY, now, tr("Bol retiré"))
        }
        if (t.errorCode != 0 && fault?.code != SmartFaultCode.BOARD_ERROR)
            raiseFault(SmartFaultCode.BOARD_ERROR, Mcs.boardErrorMessage(t.errorCode), t.errorCode)
        // Couvercle ouvert ou bol retiré : la base refuse aussi de tourner (code 3, observé le 22/11/2026).
        // C'est la même mise en sécurité que ci-dessus, déjà traitée comme une pause : pas une panne.
        val interlocked = t.lidOpen || (t.cupNtcAbsent && config.requireBowl)
        if (t.returnCode != 0 && fault == null) {
            if (interlocked) diag("la base refuse (code ${t.returnCode}) : couvercle ou bol, pause")
            else raiseFault(SmartFaultCode.BOARD_REFUSED, tr("La base refuse la commande (code %d)", t.returnCode), t.returnCode)
        }
    }

    /**
     * Réponse qui diffère de la commande parce que la base s'est mise en sécurité : couvercle ouvert ou
     * bol absent, moteur arrêté, jamais plus de chauffe que demandé. Toute autre différence est une panne.
     */
    private fun boardStoppedForInterlock(e: Expected, t: McsTelemetry): Boolean =
        e.opcode == Mcs.OP_CONFIG && t.frame.opcode == e.opcode && (t.lidOpen || t.cupNtcAbsent) &&
            t.speedLevel == 0 && t.temperatureLevel <= e.temperatureLevel

    private fun diag(line: String) {
        val d = diag ?: return
        try {
            d(line)
        } catch (_: Throwable) {
        }
    }

    private fun echoMatches(e: Expected, t: McsTelemetry): Boolean {
        if (t.frame.opcode != e.opcode) return false
        if (e.opcode != Mcs.OP_CONFIG) return true
        return t.speedLevel == e.speedLevel && t.function == Mcs.echoFunction(e.function) &&
            t.temperatureLevel == e.temperatureLevel && t.countdownSeconds == e.seconds
    }

    private fun pauseIfRunning(reason: PauseReason, now: Long, detail: String?) {
        val j = job ?: return
        if (j.state == JobState.RUNNING || j.state == JobState.STARTING) pauseJob(j, reason, now, detail)
    }

    // ------------------------------------------------------------------ liaison

    override fun onTransportUp(name: String) {
        transportName = name
        transportUp = true
        parser.reset()
        sent.clear()
        foreign.clear()
    }

    override fun onTransportDown(reason: String) {
        val wasUp = transportUp
        transportUp = false
        parser.reset()
        sent.clear()
        if (link != LinkState.OFFLINE) setLink(LinkState.OFFLINE)
        if (wasUp && job?.active == true) raiseFault(SmartFaultCode.TRANSPORT_FAILED, tr("Liaison interrompue : %s", reason))
    }

    override fun onWriteFailed(reason: String) {
        raiseFault(SmartFaultCode.WRITE_FAILED, tr("Impossible d'envoyer les commandes : %s", reason))
    }

    // ------------------------------------------------------------------ émission

    override fun tick(): ByteArray {
        val now = clock.nowMs()
        frames.clear()
        parser.expire(now, frames)
        for (f in frames) onFrame(f)
        val timeout = if (job != null || turboActive) config.staleMs else config.staleIdleMs
        if ((link == LinkState.READY || link == LinkState.ARMING) && now - latestAtMs > timeout) {
            setLink(LinkState.LOST)
            if (job?.active == true) raiseFault(SmartFaultCode.COMM_LOST, tr("Plus de réponse de la base moteur : tout est arrêté"))
        }
        updateJob(now)

        updateTurbo(now)

        val seq = sequence.next()
        val target = desired()
        val frame: ByteArray
        if (turboActive && target.operation == Operation.TURBO) {
            frame = Mcs.encodeTurbo(seq)
            remember(seq, Expected(Mcs.OP_CONFIG, 0, Mcs.FN_TURBO, 0, 5).also { it.hex = Mcs.hex(frame) })
            wire = target
            wireSeconds = 5
        } else if (runningProgram() != null) {
            val j = runningProgram()!!
            val p = j.program!!
            val m = j.motion ?: p.motion(j.totalElapsed)
            val t = j.spec.exactTemperatureC
            val speed = SmartWire.speedLevel(m.speed)
            val level = p.wireLevel(t)
            val seconds = j.runSeconds
            frame = Mcs.encode(
                McsCommand(
                    speedLevel = speed, temperatureLevel = level, constantTemperature = p.wireExact(t),
                    reverse = m.reverse, function = p.function, seconds = seconds,
                ),
                seq,
            )
            remember(seq, Expected(Mcs.OP_CONFIG, speed, p.function, level, seconds, m.reverse && m.speed > 0).also { it.hex = Mcs.hex(frame) })
            wire = Setting(Operation.STANDARD, m.speed, level, if (m.reverse) Direction.COUNTER_CLOCKWISE else Direction.CLOCKWISE)
            wireSeconds = seconds
        } else if (tarePending && target.isRest) {
            tarePending = false
            frame = Mcs.encodeTare(seq)
            remember(seq, Expected(Mcs.OP_SCALE, 0, Mcs.FN_STOP, 0, 0).also { it.hex = Mcs.hex(frame) })
            wire = Setting.STOP
            wireSeconds = 0
        } else {
            val rest = target.isRest
            val speed = if (rest) 0 else SmartWire.speedLevel(SmartWire.speed(target))
            val level = if (rest) 0 else SmartWire.temperatureLevel(target.temperatureLevel)
            val function = if (rest) Mcs.FN_STOP else SmartWire.function(target.operation)
            val seconds = if (rest) 0 else job?.runSeconds ?: 0
            val constantTemperature = if (config.sendConstantTemperature && !rest && level > 0) Mcs.celsiusForLevel(level) else 0
            frame = Mcs.encode(
                McsCommand(
                    speedLevel = speed, temperatureLevel = level, constantTemperature = constantTemperature,
                    reverse = !rest && SmartWire.reverse(target.direction),
                    function = function, seconds = seconds,
                ),
                seq,
            )
            val reverse = !rest && SmartWire.reverse(target.direction)
            remember(seq, Expected(Mcs.OP_CONFIG, speed, function, level, seconds, reverse).also { it.hex = Mcs.hex(frame) })
            wire = if (rest) Setting.STOP else target
            wireSeconds = seconds
        }
        txFrames++
        lastTxHex = Mcs.hex(frame)
        return frame
    }

    private fun remember(seq: Int, e: Expected) {
        sent.remove(seq)
        sent[seq] = e
        while (sent.size > 16) sent.remove(sent.keys.first())
    }

    override fun stopFrame(): ByteArray {
        val seq = sequence.next()
        remember(seq, Expected(Mcs.OP_CONFIG, 0, Mcs.FN_STOP, 0, 0))
        return Mcs.encodeStop(seq)
    }

    /** Programme du Smart en marche, à émettre tel quel (null sinon). */
    private fun runningProgram(): ActiveJob? {
        if (fault != null || link != LinkState.READY || turboActive) return null
        val j = job ?: return null
        if (j.program == null || j.state != JobState.RUNNING || j.waitingStop != null) return null
        return j
    }

    private fun desired(): Setting {
        if (fault != null || link != LinkState.READY) return Setting.STOP
        if (turboActive) return Programs.turboSetting()
        val j = job ?: return Setting.STOP
        if (j.state != JobState.RUNNING || j.waitingStop != null) return Setting.STOP
        val s = j.phase.setting
        return if (s.isRest) Setting.STOP else s
    }

    // ------------------------------------------------------------------ commandes

    private fun fresh(now: Long): Boolean = latest != null && now - latestAtMs <= config.freshMs

    private fun baseChecks(now: Long): List<Violation> {
        val v = ArrayList<Violation>(2)
        when (link) {
            LinkState.OFFLINE -> v += Violation("offline", tr("Base moteur non connectée"))
            LinkState.LOST -> v += Violation("lost", tr("Connexion à la base perdue"))
            LinkState.ARMING -> v += Violation("arming", tr("Connexion en cours, patientez"))
            LinkState.READY -> Unit
        }
        if (fault != null) v += Violation("fault", tr("Acquittez d'abord l'alerte : %s", fault!!.message))
        val t = latest
        if (t == null || !fresh(now)) {
            if (v.isEmpty()) v += Violation("stale", tr("Pas de mesure récente de la base"))
            return v
        }
        if (t.lidOpen) v += Violation("lid", tr("Fermez et verrouillez le couvercle"))
        if (config.requireBowl && t.cupNtcAbsent) v += Violation("bowl", tr("Bol non détecté"))
        if (t.errorCode != 0) v += Violation("board_error", Mcs.boardErrorMessage(t.errorCode))
        return v
    }

    /** Ce que le Smart sait faire aujourd'hui, en plus des règles communes de [SafetyPolicy]. */
    private fun smartChecks(spec: JobSpec): List<Violation> {
        if (spec.kind == JobKind.TURBO)
            return listOf(Violation("smart_kind", tr("%s : pas encore disponible sur le Smart", spec.kind.label)))
        val v = ArrayList<Violation>(1)
        for (p in spec.phases) {
            val s = p.setting
            if (s.isRest) continue
            if (s.temperatureLevel !in 0..Mcs.MAX_TEMPERATURE_LEVEL)
                v += Violation("smart_temp", tr("Température absente de la table du Smart"))
        }
        return v
    }

    private fun actualTemp(): Int? = latest?.actualTemperature

    override fun startJob(spec: JobSpec): Verdict {
        val now = clock.nowMs()
        if (job?.ended == false) return Verdict.refuse("busy", tr("Un programme est déjà en cours"))
        if (turboActive) return Verdict.refuse("busy", tr("Turbo en cours"))
        val v = ArrayList(baseChecks(now))
        val program = SmartProgram.of(spec.smartProgram)
        if (spec.smartProgram.isNotEmpty()) {
            if (program == null) v += Violation("smart_program", tr("Programme inconnu"))
            else v += programChecks(program, spec.totalMs, spec.exactTemperatureC, spec.hotBlendConfirmed)
        } else {
            v += smartChecks(spec)
            if (v.none { it.code == "smart_kind" }) v += SafetyPolicy.checkJob(spec, actualTemp(), Mcs.MAX_TEMPERATURE_LEVEL)
        }
        if (v.isNotEmpty()) return Verdict.refuse(v)
        job = ActiveJob(spec, now).also { it.barrier = Barrier(stopAcks, now, spinDownHold(spec.phases.firstOrNull { p -> !p.setting.isRest }?.setting, now)) }
        return Verdict.OK
    }

    /** Règles d'un programme du Smart : bornes du programme et contenu chaud pour les vitesses rapides. */
    private fun programChecks(p: SmartProgram, durationMs: Long, celsius: Int, hotBlendConfirmed: Boolean): List<Violation> {
        val v = ArrayList(p.check(durationMs, celsius))
        val hot = actualTemp()
        if (hot != null && hot >= Limits.HOT_C && p.maxSpeed >= 4 && !hotBlendConfirmed)
            v += Violation(SafetyPolicy.HOT_BLEND, tr("Contenu chaud (%d °C) : confirmez le mixage à vitesse %d", hot, p.maxSpeed))
        return v
    }

    /** Attente avant de repartir dans l'autre sens si le couteau tournait il y a moins de [SmartConfig.spinDownMs]. */
    private fun spinDownHold(next: Setting?, now: Long): Long {
        if (next == null || next.speed == 0) return 0
        if (SmartWire.reverse(next.direction) == lastRunReverse) return 0
        return (config.spinDownMs - (now - lastRunAckAt)).coerceAtLeast(0)
    }

    override fun pause(): Verdict {
        val j = job ?: return Verdict.refuse("idle", tr("Aucun programme"))
        if (j.state != JobState.RUNNING && j.state != JobState.STARTING) return Verdict.refuse("state", tr("Rien à mettre en pause"))
        pauseJob(j, PauseReason.USER, clock.nowMs(), null)
        return Verdict.OK
    }

    override fun resume(hotBlendConfirmed: Boolean): Verdict {
        val now = clock.nowMs()
        val j = job ?: return Verdict.refuse("idle", tr("Aucun programme"))
        if (j.state != JobState.PAUSED) return Verdict.refuse("state", tr("Le programme n'est pas en pause"))
        if (hotBlendConfirmed && !j.spec.hotBlendConfirmed) j.spec = j.spec.copy(hotBlendConfirmed = true)
        val v = ArrayList(baseChecks(now))
        val p = j.program
        if (p != null) v += programChecks(p, j.spec.totalMs, j.spec.exactTemperatureC, j.spec.hotBlendConfirmed)
        else if (!j.phase.setting.isRest)
            v += SafetyPolicy.checkSetting(j.phase.setting, j.spec.kind, j.spec.accessory, actualTemp(), j.spec.hotBlendConfirmed, Mcs.MAX_TEMPERATURE_LEVEL)
        if (v.isNotEmpty()) return Verdict.refuse(v)
        j.state = JobState.STARTING
        j.barrier = Barrier(stopAcks, now)
        j.waitingStop = null
        j.awaitingRun = null
        j.pauseReason = null
        j.lastUpdate = now
        return Verdict.OK
    }

    /** Arrêt demandé par l'utilisateur : toujours accepté, la trame suivante est un arrêt. */
    override fun stop(): Verdict {
        val j = job ?: return Verdict.OK
        if (!j.ended) {
            j.state = JobState.ABORTED
            j.endReason = tr("Arrêté")
            emit(EventType.JOB_ABORTED, tr("Arrêté"))
        }
        return Verdict.OK
    }

    override fun clearFinished() {
        if (job?.ended == true) job = null
    }

    /** Modifie une cuisson manuelle en cours (vitesse, température, sens, durée totale). */
    override fun adjust(speed: Int?, temperatureLevel: Int?, direction: Direction?, totalMs: Long?, hotBlendConfirmed: Boolean): Verdict {
        val now = clock.nowMs()
        val j = job ?: return Verdict.refuse("idle", tr("Aucun programme"))
        if (j.ended || j.state == JobState.STOPPING) return Verdict.refuse("state", tr("Programme terminé"))
        if (j.spec.kind != JobKind.MANUAL || j.spec.phases.size != 1 || j.program != null)
            return Verdict.refuse("kind", tr("Réglage fixe pour ce programme"))
        val old = j.phase.setting
        val s = old.copy(
            speed = speed ?: old.speed,
            temperatureLevel = temperatureLevel ?: old.temperatureLevel,
            direction = direction ?: old.direction,
        )
        val oldTotal = j.spec.totalMs
        val newTotal = totalMs ?: oldTotal
        val spec = j.spec.copy(
            phases = listOf(Phase(s, newTotal)),
            totalMs = newTotal,
            hotBlendConfirmed = j.spec.hotBlendConfirmed || hotBlendConfirmed,
        )
        val v = ArrayList(smartChecks(spec))
        v += SafetyPolicy.checkJob(spec, actualTemp(), Mcs.MAX_TEMPERATURE_LEVEL)
        if (newTotal < j.totalElapsed + 1_000) v += Violation("past", tr("Durée déjà écoulée"))
        if (v.isNotEmpty()) return Verdict.refuse(v)
        j.spec = spec
        if (j.state == JobState.RUNNING && j.waitingStop == null) {
            if (s.direction != old.direction) {
                j.waitingStop = Barrier(stopAcks, now, config.spinDownMs)
            } else if (s != old || newTotal != oldTotal) {
                // Nouveau réglage ou nouvelle durée : nouveau compte à rebours, minuteur suspendu jusqu'à l'écho.
                startRun(j, now)
            }
        }
        return Verdict.OK
    }

    /**
     * Turbo maintenu tant que l'utilisateur appuie, 3 s au plus par appui. Trame de l'appli d'origine
     * (fonction 198, 35 octets), renvoyée toutes les 200 ms comme elle le fait pendant l'appui.
     */
    override fun turboPress(accessory: Accessory): Verdict {
        val now = clock.nowMs()
        val j = job
        if (j != null && !j.ended) return Verdict.refuse("busy", tr("Arrêtez d'abord le programme en cours"))
        if (turboActive) return Verdict.OK
        val v = ArrayList(baseChecks(now))
        v += SafetyPolicy.checkSetting(Programs.turboSetting(), JobKind.TURBO, accessory, actualTemp(), false, Mcs.MAX_TEMPERATURE_LEVEL)
        if (lastRunReverse && now - lastRunAckAt < config.spinDownMs)
            v += Violation("moving", tr("Attendez l'arrêt complet du moteur"))
        if (v.isNotEmpty()) return Verdict.refuse(v)
        turboStartedAt = now
        return Verdict.OK
    }

    override fun turboRelease() {
        turboStartedAt = null
    }

    private fun updateTurbo(now: Long) {
        val since = turboStartedAt ?: return
        if (fault != null || link != LinkState.READY || latest?.lidOpen == true || (config.requireBowl && latest?.cupNtcAbsent == true)) {
            turboStartedAt = null
            return
        }
        if (now - since >= Limits.MAX_MS_TURBO_PRESS) {
            turboStartedAt = null
            emit(EventType.TURBO_END, tr("Turbo limité à %d s par appui", Limits.MAX_MS_TURBO_PRESS / 1000))
        }
    }

    /** Tare de la balance, envoyée à l'arrêt uniquement. Ignorée pendant un programme. */
    override fun tare() {
        if (!busy) tarePending = true
    }

    override fun acknowledgeFault(): Verdict {
        val f = fault ?: return Verdict.OK
        val now = clock.nowMs()
        val t = latest
        val idleEcho = t != null && fresh(now) && t.speedLevel == 0 && t.function == Mcs.FN_STOP
        val ok = when (f.code) {
            SmartFaultCode.BOARD_ERROR -> t != null && fresh(now) && t.errorCode == 0
            SmartFaultCode.BOARD_REFUSED -> t != null && fresh(now) && t.returnCode == 0
            SmartFaultCode.COMM_LOST, SmartFaultCode.TRANSPORT_FAILED, SmartFaultCode.WRITE_FAILED -> transportUp && t != null && fresh(now)
            SmartFaultCode.FOREIGN_TRAFFIC -> idleEcho && foreign.none { now - it <= config.foreignWindowMs }
            SmartFaultCode.ECHO_MISMATCH, SmartFaultCode.STOP_TIMEOUT, SmartFaultCode.START_TIMEOUT -> idleEcho
        }
        if (!ok) return Verdict.refuse("fault_active", tr("La cause est toujours présente : %s", f.message))
        fault = null
        foreign.clear()
        if (link == LinkState.READY) {
            // Repartir d'une liaison vérifiée : de nouveaux accusés d'arrêt sont exigés.
            setLink(LinkState.ARMING)
            armingCount = 0
        }
        emit(EventType.FAULT_CLEARED, tr("Alerte acquittée"))
        return Verdict.OK
    }

    // ------------------------------------------------------------------ déroulement

    private fun barrierMet(b: Barrier, now: Long): Boolean =
        stopAcks > b.afterStopAcks && fresh(now) && now - b.sinceMs >= b.holdMs

    private fun updateJob(now: Long) {
        val j = job ?: return
        val dt = (now - j.lastUpdate).coerceIn(0, config.maxTickGapMs)
        j.lastUpdate = now
        when (j.state) {
            JobState.STARTING -> {
                val b = j.barrier ?: Barrier(stopAcks, now).also { j.barrier = it }
                if (barrierMet(b, now)) {
                    j.barrier = null
                    if (!enterPhase(j, now)) return
                    j.state = JobState.RUNNING
                    if (j.everStarted) emit(EventType.JOB_RESUMED, j.spec.label) else emit(EventType.JOB_STARTED, j.spec.label)
                    j.everStarted = true
                } else if (now - b.sinceMs > config.stopTimeoutMs + b.holdMs) {
                    raiseFault(SmartFaultCode.STOP_TIMEOUT, tr("La base ne confirme pas l'arrêt : démarrage annulé"))
                }
            }
            JobState.RUNNING -> {
                val ws = j.waitingStop
                if (ws != null) {
                    if (barrierMet(ws, now)) {
                        j.waitingStop = null
                        enterPhase(j, now)
                    } else if (now - ws.sinceMs > config.stopTimeoutMs + ws.holdMs) {
                        raiseFault(SmartFaultCode.STOP_TIMEOUT, tr("La base ne confirme pas l'arrêt entre deux phases"))
                    }
                    return
                }
                val since = j.awaitingRun
                if (since != null) {
                    if (runAcks > j.runAcksAtStart) {
                        j.awaitingRun = null
                    } else {
                        if (now - since > config.startTimeoutMs)
                            raiseFault(SmartFaultCode.START_TIMEOUT, tr("La base ne confirme pas le démarrage : tout est arrêté"))
                        return
                    }
                }
                val p = j.program
                if (p != null) {
                    runProgram(j, p, dt, now)
                    return
                }
                j.totalElapsed += dt
                j.phaseElapsed += dt
                if (j.totalElapsed >= j.spec.totalMs) {
                    j.state = JobState.STOPPING
                    j.barrier = Barrier(stopAcks, now)
                    return
                }
                if (j.phaseElapsed >= j.phase.durationMs) advancePhase(j, now)
            }
            JobState.STOPPING -> {
                val b = j.barrier ?: Barrier(stopAcks, now).also { j.barrier = it }
                if (barrierMet(b, now)) {
                    j.state = JobState.FINISHED
                    j.endReason = tr("Terminé")
                    emit(EventType.JOB_FINISHED, j.spec.label)
                } else if (now - b.sinceMs > config.stopTimeoutMs) {
                    j.state = JobState.FINISHED
                    j.endReason = tr("Terminé")
                    raiseFault(SmartFaultCode.STOP_TIMEOUT, tr("La base ne confirme pas l'arrêt à la fin du programme"))
                }
            }
            JobState.PAUSED -> {
                if (now - j.pausedAt > config.pauseMaxMs) {
                    j.state = JobState.ABORTED
                    j.endReason = tr("Pause trop longue : programme annulé")
                    emit(EventType.JOB_ABORTED, j.endReason)
                }
            }
            JobState.FINISHED, JobState.ABORTED -> Unit
        }
    }

    /** Lance un tronçon de marche : compte à rebours figé, minuteur suspendu jusqu'à l'écho. */
    private fun startRun(j: ActiveJob, now: Long, keepCountdown: Boolean = false) {
        val remaining = (j.spec.totalMs - j.totalElapsed).coerceAtLeast(0)
        // Comme l'appli d'origine : la durée restante, arrondie à la seconde supérieure, figée pendant
        // toute une marche continue (saisir garde 60 s pendant ses cycles). La base ne la décompte
        // pas dans ses réponses ; c'est notre minuteur qui envoie l'arrêt.
        val cap = if (j.program != null) Mcs.MAX_TIMER_SECONDS else Mcs.MAX_MANUAL_SECONDS
        if (j.preheating) j.runSeconds = PREHEAT_COUNTDOWN_S
        else if (!keepCountdown || j.runSeconds <= 0)
            j.runSeconds = ((remaining + 999) / 1000).toInt().coerceIn(1, cap)
        j.awaitingRun = now
        j.runAcksAtStart = runAcks
    }

    /**
     * Déroulé d'un programme du Smart : préchauffage éventuel (minuteur arrêté), puis mouvements
     * du programme selon le temps écoulé. Changement de mouvement : nouveau compte à rebours (le
     * temps restant, comme l'appli d'origine) ; changement de sens moteur lancé : arrêt confirmé
     * et ralentissement d'abord.
     */
    private fun runProgram(j: ActiveJob, p: SmartProgram, dt: Long, now: Long) {
        if (j.preheating) {
            val target = TemperatureTable.SMART.celsius(TemperatureTable.SMART.levelAtMost(j.spec.exactTemperatureC))
            val actual = latest?.actualTemperature ?: 0
            if ((actual >= 37 && actual >= target) || now - j.preheatSince > PREHEAT_MAX_MS) {
                j.preheating = false
                diag("préchauffage terminé à $actual °C")
                startRun(j, now)
                emit(EventType.PHASE_CHANGED, j.spec.label)
            }
            return
        }
        j.totalElapsed += dt
        j.phaseElapsed += dt
        if (j.totalElapsed >= j.spec.totalMs) {
            j.state = JobState.STOPPING
            j.barrier = Barrier(stopAcks, now)
            return
        }
        val m = p.motion(j.totalElapsed)
        val old = j.motion
        if (m == old) return
        j.motion = m
        val spinning = old != null && old.speed > 0
        if (m.speed > 0 && spinning && old!!.reverse != m.reverse) {
            j.waitingStop = Barrier(stopAcks, now, config.spinDownMs)
        } else if (m.speed > 0 && !spinning && m.reverse != lastRunReverse && now - lastRunAckAt < config.spinDownMs) {
            j.waitingStop = Barrier(stopAcks, now, config.spinDownMs - (now - lastRunAckAt))
        } else {
            startRun(j, now)
        }
        emit(EventType.PHASE_CHANGED, j.spec.label)
    }

    /** Vérifie qu'on peut (re)lancer la phase courante ; sinon met en pause. */
    private fun enterPhase(j: ActiveJob, now: Long, keepCountdown: Boolean = false): Boolean {
        val p = j.program
        if (p != null) {
            val v = ArrayList(baseChecks(now))
            v += programChecks(p, j.spec.totalMs, j.spec.exactTemperatureC, j.spec.hotBlendConfirmed)
            if (v.isNotEmpty()) {
                pauseJob(j, if (latest?.lidOpen == true) PauseReason.LID else PauseReason.SAFETY, now, v.first().message)
                return false
            }
            j.motion = p.motion(j.totalElapsed)
            if (j.preheating && !j.everStarted) j.preheatSince = now
            startRun(j, now)
            return true
        }
        val s = j.phase.setting
        if (s.isRest) {
            j.awaitingRun = null
            return true
        }
        val v = ArrayList(baseChecks(now))
        v += SafetyPolicy.checkSetting(s, j.spec.kind, j.spec.accessory, actualTemp(), j.spec.hotBlendConfirmed, Mcs.MAX_TEMPERATURE_LEVEL)
        if (v.isNotEmpty()) {
            val reason = if (latest?.lidOpen == true) PauseReason.LID else PauseReason.SAFETY
            pauseJob(j, reason, now, v.first().message)
            return false
        }
        startRun(j, now, keepCountdown)
        return true
    }

    private fun advancePhase(j: ActiveJob, now: Long) {
        val phases = j.spec.phases
        var next = j.phaseIndex + 1
        if (next >= phases.size) {
            if (!j.spec.cyclic) {
                j.state = JobState.STOPPING
                j.barrier = Barrier(stopAcks, now)
                return
            }
            next = 0
        }
        val cur = j.phase.setting
        val nxt = phases[next].setting
        j.phaseIndex = next
        j.phaseElapsed = 0
        when {
            nxt.isRest -> j.awaitingRun = null
            !cur.isRest && cur.direction != nxt.direction -> j.waitingStop = Barrier(stopAcks, now, config.spinDownMs)
            cur.isRest && now - lastRunAckAt < config.spinDownMs && wireDirectionBeforeRest(j) != nxt.direction ->
                j.waitingStop = Barrier(stopAcks, now, config.spinDownMs - (now - lastRunAckAt))
            else -> if (!enterPhase(j, now, keepCountdown = !cur.isRest)) return
        }
        emit(EventType.PHASE_CHANGED, j.spec.label)
    }

    /** Sens de la dernière phase active avant le repos qui vient de finir. */
    private fun wireDirectionBeforeRest(j: ActiveJob): Direction {
        val phases = j.spec.phases
        for (k in 1..phases.size) {
            val p = phases[(j.phaseIndex - 1 - k + 2 * phases.size) % phases.size].setting
            if (!p.isRest) return p.direction
        }
        return phases[j.phaseIndex].setting.direction
    }

    private fun pauseJob(j: ActiveJob, reason: PauseReason, now: Long, detail: String?) {
        j.state = JobState.PAUSED
        j.pauseReason = reason
        j.pausedAt = now
        j.waitingStop = null
        j.barrier = null
        j.awaitingRun = null
        emit(EventType.JOB_PAUSED, detail ?: reason.label)
    }

    private fun raiseFault(code: SmartFaultCode, message: String, boardCode: Int = 0) {
        fault = SmartFault(code, message, boardCode)
        tarePending = false
        turboStartedAt = null
        val j = job
        if (j != null && !j.ended) {
            j.state = JobState.ABORTED
            j.endReason = message
            emit(EventType.JOB_ABORTED, message)
        }
        emit(EventType.FAULT, message)
    }

    private fun setLink(state: LinkState) {
        if (link == state) return
        link = state
        if (state != LinkState.ARMING) armingCount = 0
        emit(EventType.LINK_CHANGED, state.label)
    }

    private fun emit(type: EventType, message: String) {
        val l = listener ?: return
        try {
            l(MachineEvent(type, message))
        } catch (_: Throwable) {
        }
    }

    // ------------------------------------------------------------------ état

    val jobState: JobState? get() = job?.state
    val pauseReason: PauseReason? get() = job?.pauseReason
    val elapsedMs: Long get() = job?.totalElapsed ?: 0
    val wireSetting: Setting get() = wire
    val wireCountdown: Int get() = wireSeconds

    override fun snapshot(): Snapshot {
        val now = clock.nowMs()
        val t = latest
        val j = job
        val s = j?.phase?.setting
        return Snapshot(
            link = link,
            transportName = transportName,
            transportUp = transportUp,
            faultCode = fault?.code?.name ?: "",
            faultMessage = fault?.message ?: "",
            hasSample = t != null,
            sampleAgeMs = if (t == null) -1 else now - latestAtMs,
            actualTempC = t?.actualTemperature ?: 0,
            targetTempC = if (t == null) 0 else Mcs.celsiusForLevel(t.temperatureLevel),
            // Le Smart ne mesure pas le régime : vitesse et chauffe sont l'écho de la commande.
            rpm = 0,
            speedLevel = t?.uiSpeed ?: 0,
            weightG = t?.weightGrams?.toInt() ?: 0,
            scaleStable = t?.scaleStable ?: false,
            scaleState = if (t?.scaleOverload == true) "overload" else "",
            lidOpen = t?.lidOpen ?: false,
            bowlDetected = t != null && !t.cupNtcAbsent,
            motorOn = (t?.speedLevel ?: 0) > 0,
            heaterOn = (t?.temperatureLevel ?: 0) > 0,
            boardErrorCode = t?.errorCode ?: 0,
            firmware = t?.firmwareVersion ?: 0,
            rxHex = t?.hex ?: "",
            jobActive = j != null,
            jobKind = j?.spec?.kind?.name ?: "",
            jobLabel = j?.spec?.label ?: "",
            jobState = j?.state?.name ?: "",
            pauseReason = j?.pauseReason?.name ?: "",
            endReason = j?.endReason ?: "",
            phaseIndex = j?.phaseIndex ?: 0,
            phaseCount = j?.spec?.phases?.size ?: 0,
            elapsedMs = j?.totalElapsed ?: 0,
            totalMs = j?.spec?.totalMs ?: 0,
            remainingMs = if (j == null) 0 else (j.spec.totalMs - j.totalElapsed).coerceAtLeast(0),
            waitingStop = j != null && (j.waitingStop != null || j.awaitingRun != null || (j.preheating && j.state == JobState.RUNNING) ||
                j.state == JobState.STARTING || j.state == JobState.STOPPING),
            jobSpeed = s?.speed ?: 0,
            jobTempLevel = s?.temperatureLevel ?: 0,
            jobReverse = s?.direction == Direction.COUNTER_CLOCKWISE,
            accessory = j?.spec?.accessory?.name ?: Accessory.BLADE.name,
            hotBlendConfirmed = j?.spec?.hotBlendConfirmed ?: false,
            turboActive = turboActive,
            wireOperation = if (wire.isRest) Operation.STOP.name else wire.operation.name,
            wireSpeed = wire.speed,
            wireTempLevel = wire.temperatureLevel,
            txHex = lastTxHex,
            txFrames = txFrames,
            rxFrames = rxFrames,
            invalidFrames = parser.invalidFrames,
        )
    }
}
