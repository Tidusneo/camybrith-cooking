package app.toque.core

import app.toque.core.json.Json
import app.toque.core.json.list
import app.toque.core.json.obj
import app.toque.core.machine.ControllerConfig
import app.toque.core.machine.EventType
import app.toque.core.machine.JobKind
import app.toque.core.machine.JobSpec
import app.toque.core.machine.JobState
import app.toque.core.machine.LinkState
import app.toque.core.machine.MachineLoop
import app.toque.core.machine.MachineEvent
import app.toque.core.machine.MonotonicClock
import app.toque.core.machine.PauseReason
import app.toque.core.machine.Phase
import app.toque.core.machine.Programs
import app.toque.core.machine.RobotModel
import app.toque.core.machine.SafetyPolicy
import app.toque.core.machine.Setting
import app.toque.core.machine.Snapshot
import app.toque.core.machine.TemperatureTable
import app.toque.core.recipes.StepMachine
import app.toque.core.recipes.StepProgram
import app.toque.core.protocol.Direction
import app.toque.core.protocol.Mcc
import app.toque.core.protocol.Mcs
import app.toque.core.protocol.McsCommand
import app.toque.core.protocol.Operation
import app.toque.core.smart.SmartConfig
import app.toque.core.smart.SmartController
import app.toque.core.smart.SmartFaultCode
import app.toque.core.smart.SmartSimBoard
import app.toque.core.smart.SmartSimTransport
import app.toque.core.smart.SmartWire
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Trame émise, décodée pour les assertions. */
class SmartTx(val atMs: Long, val raw: ByteArray) {
    private val p = raw.copyOfRange(8, raw.size - 1)
    val seq = raw[7].toInt() and 0xFF
    val opcode = ((p[0].toInt() and 0xFF) shl 8) or (p[1].toInt() and 0xFF)
    val temperatureLevel = p[2].toInt()
    val constantTemperature = p[4].toInt() and 0xFF
    val speedLevel = p[8].toInt()
    val reverse = p[9].toInt() != 0
    val function = p[11].toInt() and 0xFF
    val seconds = (p[17].toInt() and 0xFF) * 3600 + (p[18].toInt() and 0xFF) * 60 + (p[19].toInt() and 0xFF)
    val running get() = opcode == Mcs.OP_CONFIG && function == Mcs.FN_MANUAL
    val payload: ByteArray get() = p
}

/**
 * Banc d'essai du Smart : contrôleur + base simulée, une trame toutes les 200 ms, réponse 100 ms
 * plus tard (le délai médian mesuré sur le robot), sans fil d'exécution.
 */
class SmartBench(config: SmartConfig = SmartConfig()) {
    val clock = FakeClock()
    val board = SmartSimBoard(clock)
    val c = SmartController(clock, config)
    val events = ArrayList<MachineEvent>()
    val tx = ArrayList<SmartTx>()
    /** Réponses injectées en plus (trafic d'un autre programme). */
    val extra = ArrayList<ByteArray>()

    val diagLines = ArrayList<String>()

    init {
        c.listener = { events += it }
        c.diag = { diagLines += it }
        board.reset()
        c.onTransportUp("banc")
    }

    fun step(ms: Long = 200) {
        var left = ms
        while (left > 0) {
            clock.now += 100
            val frame = c.tick()
            tx += SmartTx(clock.now - 1_000_000L, frame)
            val replies = board.feed(frame)
            clock.now += 100
            for (r in replies) c.onBytes(r)
            for (r in extra) c.onBytes(r)
            extra.clear()
            left -= 200
        }
    }

    fun ready(): SmartBench {
        step(800)
        assertEquals(LinkState.READY, c.link)
        return this
    }

    val snap get() = c.snapshot()
    fun last() = tx.last()
    fun runningFrames() = tx.count { it.running }
}

class SmartControllerTest {
    private val cw = Direction.CLOCKWISE
    private val ccw = Direction.COUNTER_CLOCKWISE

    /** Sur le Smart, les niveaux de Toque sont ceux de la table du Smart. */
    private fun connectLevel(celsius: Int) = Mcs.levelForCelsius(celsius).also { require(it >= 0) }

    @Test fun armsOnlyAfterAcknowledgedStops() {
        val b = SmartBench()
        b.step(200)
        assertEquals(LinkState.ARMING, b.c.link)
        assertFalse(b.c.startJob(Programs.manual(1, 0, cw, 5_000)).ok)
        b.ready()
        assertTrue(b.tx.all { it.opcode == Mcs.OP_CONFIG && it.speedLevel == 0 && it.function == 0 && it.seconds == 0 })
    }

    @Test fun manualJobStreamsTheSettingThenStopsOnItsOwnTimer() {
        val b = SmartBench().ready()
        val v = b.c.startJob(Programs.manual(2, 0, cw, 10_000))
        assertTrue(v.message, v.ok)
        b.step(1_000)
        assertEquals(JobState.RUNNING.name, b.snap.jobState)
        val run = b.tx.filter { it.running }
        assertTrue(run.isNotEmpty())
        // Comme l'appli d'origine : vitesse ×2, fonction 1, compte à rebours constant de 10 s.
        assertTrue(run.all { it.speedLevel == 4 && it.temperatureLevel == 0 && it.seconds == 10 && !it.reverse })
        b.step(12_000)
        assertEquals(JobState.FINISHED.name, b.snap.jobState)
        assertTrue(b.events.any { it.type == EventType.JOB_FINISHED })
        assertFalse(b.last().running)
        // 10 s de marche à 200 ms par trame, plus le premier écho : jamais plus de 10,4 s.
        val n = b.runningFrames()
        assertTrue("trames de marche : $n", n in 50..52)
    }

    @Test fun firstRunFrameMatchesTheOriginalAppByteForByte() {
        // Capture du robot de référence : l'appli d'origine, 70 °C, vitesse 1, 3 min.
        val text = javaClass.getResourceAsStream("/mcs/robot-capture-2026-11-21-chauffe.json")!!.bufferedReader().use { it.readText() }
        val original = Json.parse(text).obj()["tx"].list().map { Mcs.parseHex(it as String) }
            .map { SmartTx(0, it) }.first { it.running }
        assertEquals(8, original.temperatureLevel)
        val b = SmartBench().ready()
        assertTrue(b.c.startJob(Programs.manual(1, connectLevel(70), cw, 180_000)).ok)
        b.step(600)
        val mine = b.tx.first { it.running }
        assertEquals(Mcs.hex(original.payload), Mcs.hex(mine.payload))
    }

    @Test fun smartTableOffers40CAndAllConnectTemperatures() {
        val smart = TemperatureTable.SMART
        assertEquals(20, smart.maxLevel)
        assertEquals(2, smart.levelFor(40))
        assertEquals(-1, TemperatureTable.CONNECT.levelFor(40))
        for (level in 0..19) assertTrue(smart.levelFor(TemperatureTable.CONNECT.celsius(level)) >= 0)
        assertEquals(1, SmartWire.temperatureLevel(connectLevel(37)))
        assertEquals(8, SmartWire.temperatureLevel(connectLevel(70)))
        assertEquals(20, SmartWire.temperatureLevel(connectLevel(130)))
        assertEquals(TemperatureTable.SMART, RobotModel.SMART.temperatures)
    }

    @Test fun fortyDegreesRunsOnTheSmart() {
        val b = SmartBench().ready()
        val v = b.c.startJob(Programs.manual(1, connectLevel(40), cw, 10_000))
        assertTrue(v.message, v.ok)
        b.step(1_000)
        assertTrue(b.last().running)
        assertEquals(2, b.last().temperatureLevel)
        assertEquals(40, b.snap.targetTempC)
        // 130 °C (niveau 20) existe ; au delà, refus.
        b.c.stop(); b.step(600); b.c.clearFinished()
        assertTrue(b.c.startJob(Programs.manual(1, 20, cw, 10_000)).ok)
        b.c.stop(); b.step(600); b.c.clearFinished()
        assertFalse(b.c.startJob(Programs.manual(1, 21, cw, 10_000)).ok)
    }

    /** Saisir de l'appli d'origine, capture du 21/11/2026 : 130 °C, 1 min, vitesse 1, sens inverse. */
    @Test fun roastMatchesTheOriginalAppByteForByte() {
        val b = SmartBench().ready()
        val v = b.c.startJob(Programs.toast(60_000, connectLevel(130)))
        assertTrue(v.message, v.ok)
        b.step(8_000)
        val run = b.tx.filter { it.function == Mcs.FN_ROAST }
        assertEquals(Mcs.hex(Mcs.parseHex("0802140000000000020100040000000000000100000000000000000000")), Mcs.hex(run.first().payload))
        assertEquals(Mcs.hex(Mcs.parseHex("0802140000000000000100040000000000000100000000000000000000")), Mcs.hex(run.last().payload))
        assertEquals("", b.snap.faultCode)
    }

    @Test fun roastCyclesFiveSecondsOnThirtyOffWithConstantCountdown() {
        val b = SmartBench().ready()
        assertTrue(b.c.startJob(Programs.toast(80_000, connectLevel(130))).ok)
        b.step(90_000)
        assertEquals(JobState.FINISHED.name, b.snap.jobState)
        assertEquals("", b.snap.faultCode)
        val run = b.tx.filter { it.function == Mcs.FN_ROAST }
        assertTrue(run.map { listOf(it.temperatureLevel, it.reverse, it.seconds, it.speedLevel) }.toSet().toString(), run.all { it.temperatureLevel == 20 && it.reverse && it.seconds == 80 && it.speedLevel in setOf(0, 2) })
        // Durées des paliers : 5 s à vitesse 1, 30 s à l'arrêt, chauffe continue.
        val runs = ArrayList<Pair<Int, Int>>()
        for (t in run) if (runs.isEmpty() || runs.last().first != t.speedLevel) runs += t.speedLevel to 1 else runs[runs.size - 1] = t.speedLevel to runs.last().second + 1
        assertEquals(listOf(2, 0, 2, 0, 2, 0), runs.map { it.first })
        assertTrue("$runs", runs[0].second in 25..27 && runs[1].second in 149..152 && runs[2].second in 24..27)
        assertTrue(b.board.modelTemperature > 30)
    }

    /** Cuisson vapeur de l'appli d'origine, capture du 21/11/2026 : 1 min, 120 °C, vitesse 1, sens normal. */
    @Test fun steamMatchesTheOriginalAppByteForByte() {
        val b = SmartBench().ready()
        val v = b.c.startJob(Programs.steam(60_000, connectLevel(120)))
        assertTrue(v.message, v.ok)
        b.step(3_000)
        val run = b.tx.filter { it.function == Mcs.FN_STEAM }
        assertTrue(run.isNotEmpty())
        assertEquals(Mcs.hex(Mcs.parseHex("0802120000000000020000030000000000000100000000000000000000")), Mcs.hex(run.first().payload))
        b.step(60_000)
        assertEquals(JobState.FINISHED.name, b.snap.jobState)
        assertEquals("", b.snap.faultCode)
        assertTrue(b.board.modelTemperature > 30)
    }

    /** Malaxer « pâte molle » de l'appli d'origine : 15 s vitesse 4 en sens inverse puis sens normal. */
    @Test fun kneadStartsLikeTheOriginalApp() {
        val b = SmartBench().ready()
        val v = b.c.startJob(Programs.knead(60_000))
        assertTrue(v.message, v.ok)
        b.step(70_000)
        assertEquals(JobState.FINISHED.name, b.snap.jobState)
        assertEquals("", b.snap.faultCode)
        val run = b.tx.filter { it.function == Mcs.FN_KNEAD }
        assertEquals(Mcs.hex(Mcs.parseHex("0802000000000000080100050000000000000100000000000000000000")), Mcs.hex(run.first().payload))
        assertTrue(run.all { it.speedLevel == 8 && it.temperatureLevel == 0 })
        // Sens inverse d'abord, puis sens normal après un arrêt confirmé.
        assertTrue(run.first().reverse && !run.last().reverse)
        val lastRev = b.tx.indexOfLast { it.function == Mcs.FN_KNEAD && it.reverse }
        val firstFwd = b.tx.indexOfFirst { it.function == Mcs.FN_KNEAD && !it.reverse }
        assertTrue(b.tx.subList(lastRev + 1, firstFwd).all { it.function == 0 && it.speedLevel == 0 })
    }

    @Test fun turboFrameMatchesTheOriginalApp() {
        assertEquals(Mcs.hex(Mcs.parseHex("aa0022024f420002080200000000000000000ac600000000000000050000000000069c")), Mcs.hex(Mcs.encodeTurbo(2)))
    }

    @Test fun turboRunsWhilePressedAtMostThreeSeconds() {
        val b = SmartBench().ready()
        val p1 = b.c.turboPress()
        assertTrue("appui " + p1.message, p1.ok)
        b.step(1_000)
        assertTrue(b.snap.faultCode + b.events.toString(), b.snap.turboActive)
        assertTrue("trame " + Mcs.hex(b.last().raw), b.last().function == Mcs.FN_TURBO && b.last().raw.size == 35)
        b.c.turboRelease()
        b.step(400)
        assertFalse("relâché", b.snap.turboActive)
        assertEquals("relâché", 0, b.last().function)
        // Appui maintenu : coupé à 3 s.
        val p2 = b.c.turboPress()
        assertTrue("second appui " + p2.message, p2.ok)
        b.step(4_000)
        assertFalse("3 s", b.snap.turboActive)
        assertTrue("fin", b.events.any { it.type == EventType.TURBO_END })
        val pressed = b.tx.takeLast(20).count { it.function == Mcs.FN_TURBO }
        assertTrue("$pressed", pressed in 14..16)
        assertEquals("alerte", "", b.snap.faultCode)
        // Refus : programme en cours, couvercle ouvert, contenu chaud.
        val sj = b.c.startJob(Programs.manual(1, 0, cw, 10_000))
        assertTrue("programme " + sj.message, sj.ok)
        assertFalse("pendant programme", b.c.turboPress().ok)
        b.c.stop(); b.step(600); b.c.clearFinished()
        b.board.lidOpen = true; b.step(400)
        assertFalse("couvercle", b.c.turboPress().ok)
        b.board.lidOpen = false; b.step(400)
        val tp = b.c.turboPress()
        assertTrue("après fermeture " + tp.message, tp.ok)
        b.board.lidOpen = true; b.step(400)
        assertFalse("ouvert pendant turbo", b.snap.turboActive)
        assertEquals(0, b.last().speedLevel)
    }

    @Test fun reversingRightAfterARunWaitsForTheBlade() {
        val b = SmartBench().ready()
        assertTrue(b.c.startJob(Programs.manual(3, 0, cw, 5_000)).ok)
        b.step(7_000)
        assertEquals(JobState.FINISHED.name, b.snap.jobState)
        b.c.clearFinished()
        assertTrue(b.c.startJob(Programs.manual(1, 0, ccw, 5_000)).ok)
        val start = b.tx.size
        b.step(3_000)
        val lastForward = b.tx.indexOfLast { it.running && !it.reverse }
        val firstReverse = b.tx.indexOfFirst { it.running && it.reverse }
        assertTrue(firstReverse >= start)
        assertTrue(b.tx[firstReverse].atMs - b.tx[lastForward].atMs >= 2_000)
    }

    @Test fun recipeTemperaturesFollowTheRobotTable() {
        val step = StepMachine(StepProgram.MANUAL, seconds = 60, speed = 1, tempC = 40)
        val before = TemperatureTable.current
        try {
            TemperatureTable.current = TemperatureTable.CONNECT
            assertFalse(step.valid)
            TemperatureTable.current = TemperatureTable.SMART
            assertTrue(step.violations().toString(), step.valid)
            assertEquals(2, step.temperatureLevel)
            assertEquals(2, step.toJob("essai")!!.phases.single().setting.temperatureLevel)
            assertEquals(8, StepMachine(StepProgram.MANUAL, 60, 1, 70).temperatureLevel)
            // Saisir des recettes du Smart : la température de la recette est gardée.
            val roast = StepMachine(StepProgram.TOAST, seconds = 300, tempC = 120).toJob("essai")!!
            assertTrue(roast.phases.all { it.setting.temperatureLevel == 18 })
            assertTrue(StepMachine(StepProgram.TOAST, seconds = 300).toJob("essai")!!.phases.all { it.setting.temperatureLevel == 20 })
        } finally {
            TemperatureTable.current = before
        }
    }

    @Test fun timerWaitsForTheBoardToEchoTheSetting() {
        val b = SmartBench().ready()
        b.board.ignoreCommands = true
        assertTrue(b.c.startJob(Programs.manual(2, 0, cw, 30_000)).ok)
        b.step(1_000)
        // La base répond toujours « arrêt » : écho différent, alerte et arrêt.
        assertEquals(SmartFaultCode.ECHO_MISMATCH.name, b.snap.faultCode)
        assertEquals(JobState.ABORTED.name, b.snap.jobState)
        assertEquals(0L, b.c.elapsedMs)
        b.step(400)
        assertFalse(b.last().running)
    }

    @Test fun silentBoardStopsEverything() {
        val b = SmartBench().ready()
        assertTrue(b.c.startJob(Programs.manual(3, 0, cw, 60_000)).ok)
        b.step(2_000)
        assertTrue(b.last().running)
        b.board.silent = true
        b.step(2_000)
        assertEquals(LinkState.LOST, b.c.link)
        assertEquals(SmartFaultCode.COMM_LOST.name, b.snap.faultCode)
        assertEquals(JobState.ABORTED.name, b.snap.jobState)
        assertFalse(b.last().running)
        // La reprise de la liaison ne relance rien et l'alerte reste à acquitter.
        b.board.silent = false
        b.step(1_000)
        assertEquals(LinkState.READY, b.c.link)
        assertTrue(b.tx.takeLast(5).none { it.running })
        assertTrue(b.c.acknowledgeFault().ok)
        assertEquals(LinkState.ARMING, b.c.link)
        b.step(800)
        assertEquals(LinkState.READY, b.c.link)
    }

    @Test fun lidOpeningPausesAndResumeNeedsItClosed() {
        val b = SmartBench().ready()
        assertTrue(b.c.startJob(Programs.manual(2, 0, cw, 60_000)).ok)
        b.step(3_000)
        val before = b.c.elapsedMs
        b.board.lidOpen = true
        b.step(400)
        assertEquals(JobState.PAUSED.name, b.snap.jobState)
        assertEquals(PauseReason.LID.name, b.snap.pauseReason)
        // La base coupe d'elle-même et le dit dans sa réponse : c'est une pause, pas une alerte.
        assertEquals("", b.snap.faultCode)
        assertTrue(b.diagLines.any { it.startsWith("la base coupe") })
        assertFalse(b.last().running)
        b.step(2_000)
        assertTrue(b.c.elapsedMs - before <= 400)
        val refused = b.c.resume()
        assertFalse(refused.ok)
        assertTrue(refused.has("lid"))
        b.board.lidOpen = false
        b.step(400)
        assertTrue(b.c.resume().ok)
        b.step(1_000)
        assertEquals(JobState.RUNNING.name, b.snap.jobState)
        assertTrue(b.last().running)
        // Après une reprise, le compte à rebours envoyé est la durée restante.
        assertTrue(b.last().seconds in 56..58)
    }

    /** Robot réel (22/11/2026) : couvercle ouvert en marche, la base répond aussi « refusé, code 3 ». */
    @Test fun lidOpenRefusalCode3IsAPauseThatResumes() {
        val b = SmartBench().ready()
        assertTrue(b.c.startJob(Programs.manual(2, 0, cw, 10_000)).ok)
        b.step(3_000)
        b.board.lidOpen = true
        b.board.returnCode = 3
        b.step(600)
        assertEquals(JobState.PAUSED.name, b.snap.jobState)
        assertEquals(PauseReason.LID.name, b.snap.pauseReason)
        assertEquals("", b.snap.faultCode)
        assertFalse(b.last().running)
        b.board.lidOpen = false
        b.board.returnCode = 0
        b.step(400)
        assertTrue(b.c.resume().ok)
        b.step(1_000)
        assertEquals(JobState.RUNNING.name, b.snap.jobState)
        assertTrue(b.last().running)
    }

    @Test fun refusalWithLidClosedIsStillAFault() {
        val b = SmartBench().ready()
        assertTrue(b.c.startJob(Programs.manual(2, 0, cw, 10_000)).ok)
        b.step(2_000)
        b.board.returnCode = 3
        b.step(400)
        assertEquals(SmartFaultCode.BOARD_REFUSED.name, b.snap.faultCode)
        assertFalse(b.last().running)
    }

    @Test fun lidOpenWithoutBoardCutStillPausesAndStops() {
        val b = SmartBench().ready()
        b.board.interlock = false
        assertTrue(b.c.startJob(Programs.manual(2, 0, cw, 60_000)).ok)
        b.step(2_000)
        b.board.lidOpen = true
        b.step(400)
        assertEquals(JobState.PAUSED.name, b.snap.jobState)
        assertEquals("", b.snap.faultCode)
        assertFalse(b.last().running)
        assertEquals(0, b.last().speedLevel)
    }

    @Test fun echoShowingMoreThanAskedIsAFaultEvenLidOpen() {
        val b = SmartBench().ready()
        assertTrue(b.c.startJob(Programs.manual(1, 0, cw, 60_000)).ok)
        b.step(2_000)
        // Réponse forgée : couvercle ouvert mais moteur plus rapide que demandé.
        val other = SmartSimBoard(b.clock)
        other.interlock = false
        other.lidOpen = true
        val seq = SmartTx(0, b.c.tick()).seq
        val forged = other.feed(Mcs.encode(McsCommand(speedLevel = 10, function = Mcs.FN_MANUAL, seconds = 58), seq))
        for (r in forged) b.c.onBytes(r)
        assertEquals(SmartFaultCode.ECHO_MISMATCH.name, b.snap.faultCode)
    }

    @Test fun removingTheBowlPauses() {
        val b = SmartBench().ready()
        assertTrue(b.c.startJob(Programs.manual(1, 0, cw, 60_000)).ok)
        b.step(1_000)
        b.board.bowlPresent = false
        b.step(400)
        assertEquals(JobState.PAUSED.name, b.snap.jobState)
        assertEquals("", b.snap.faultCode)
        assertFalse(b.last().running)
        assertFalse(b.c.resume().ok)
    }

    @Test fun anotherProgramTalkingToTheBoardIsAFault() {
        val b = SmartBench().ready()
        assertTrue(b.c.startJob(Programs.manual(1, 0, cw, 60_000)).ok)
        b.step(1_000)
        // Réponses à des séquences que Toque n'a pas envoyées, comme si l'appli d'origine pilotait aussi.
        val other = SmartSimBoard(b.clock)
        for (i in 0 until 3) {
            val seq = (b.last().seq + 100 + i) and 0xFF
            b.extra += other.feed(Mcs.encodeStop(if (seq == 0x66) 0x67 else seq))
            b.step(200)
        }
        assertEquals(SmartFaultCode.FOREIGN_TRAFFIC.name, b.snap.faultCode)
        assertEquals(JobState.ABORTED.name, b.snap.jobState)
        b.step(400)
        assertFalse(b.last().running)
        assertFalse(b.c.acknowledgeFault().ok)
        b.step(3_200)
        assertTrue(b.c.acknowledgeFault().ok)
    }

    @Test fun boardErrorAbortsAndStaysUntilCleared() {
        val b = SmartBench().ready()
        assertTrue(b.c.startJob(Programs.manual(1, connectLevel(100), cw, 60_000)).ok)
        b.step(1_000)
        b.board.errorCode = 6
        b.step(400)
        assertEquals(SmartFaultCode.BOARD_ERROR.name, b.snap.faultCode)
        assertTrue(b.snap.faultMessage.isNotEmpty())
        assertFalse(b.last().running)
        assertFalse(b.c.acknowledgeFault().ok)
        b.board.errorCode = 0
        b.step(400)
        assertTrue(b.c.acknowledgeFault().ok)
    }

    @Test fun refusesWhatTheSmartCannotDoYet() {
        val b = SmartBench().ready()
        val turboJob = JobSpec(JobKind.TURBO, "turbo", listOf(Phase(Programs.turboSetting(), 1_000)), 1_000, cyclic = false)
        val v = b.c.startJob(turboJob)
        assertFalse(v.ok)
        assertTrue(v.has("smart_kind"))
        b.step(1_000)
        assertTrue(b.tx.none { it.running || it.function != 0 })
    }

    @Test fun commonSafetyRulesStillApply() {
        val b = SmartBench().ready()
        assertTrue(b.c.startJob(Programs.manual(5, connectLevel(90), cw, 10_000)).message.isNotEmpty())
        assertFalse(b.c.startJob(Programs.manual(4, 0, ccw, 10_000)).ok)
        assertFalse(b.c.startJob(Programs.manual(2, 0, cw, 100 * 60_000L)).ok)
        assertFalse(b.c.startJob(Programs.manual(11, 0, cw, 10_000)).ok)
    }

    @Test fun hotContentNeedsConfirmationBeforeFastBlending() {
        val b = SmartBench()
        b.board.startTemperature = 82.0
        b.board.reset()
        b.ready()
        assertEquals(80, b.snap.actualTempC)
        val v = b.c.startJob(Programs.manual(6, 0, cw, 10_000))
        assertFalse(v.ok)
        assertTrue(v.has(SafetyPolicy.HOT_BLEND))
        assertTrue(b.c.startJob(Programs.manual(6, 0, cw, 10_000, hotBlendConfirmed = true)).ok)
    }

    @Test fun heatingReportsTableStepsLikeTheRobot() {
        val b = SmartBench().ready()
        assertTrue(b.c.startJob(Programs.manual(1, connectLevel(70), cw, 5 * 60_000L)).ok)
        val seen = LinkedHashSet<Int>()
        repeat(900) {
            b.step(200)
            seen += b.snap.actualTempC
        }
        assertEquals(listOf(0, 37, 40, 45, 50, 55, 60, 65, 70), seen.toList())
        assertEquals(70, b.snap.targetTempC)
    }

    @Test fun reversingWaitsForAConfirmedStopAndSpinDown() {
        val b = SmartBench().ready()
        assertTrue(b.c.startJob(Programs.manual(2, 0, cw, 60_000)).ok)
        b.step(2_000)
        assertTrue(b.c.adjust(direction = ccw).ok)
        b.step(5_000)
        val idx = b.tx.indexOfFirst { it.running && it.reverse }
        assertTrue(idx > 0)
        val lastForward = b.tx.indexOfLast { it.running && !it.reverse }
        // Entre la dernière trame avant et la première trame arrière : uniquement des arrêts, au moins 2 s.
        assertTrue(b.tx.subList(lastForward + 1, idx).all { !it.running && it.speedLevel == 0 })
        assertTrue(b.tx[idx].atMs - b.tx[lastForward].atMs >= 2_000)
    }

    @Test fun adjustSendsTheNewSettingAndKeepsTheTimer() {
        val b = SmartBench().ready()
        assertTrue(b.c.startJob(Programs.manual(1, 0, cw, 30_000)).ok)
        b.step(5_000)
        assertTrue(b.c.adjust(speed = 3).ok)
        b.step(1_000)
        assertEquals(6, b.last().speedLevel)
        assertTrue(b.last().seconds in 24..26)
        b.step(30_000)
        assertEquals(JobState.FINISHED.name, b.snap.jobState)
        val n = b.runningFrames()
        assertTrue("trames de marche : $n", n in 150..154)
    }

    @Test fun restPhasesSendStopAndCountTime() {
        val spec = JobSpec(
            JobKind.MANUAL, "essai",
            listOf(
                Phase(Setting(Operation.STANDARD, 2, 0, cw), 3_000),
                Phase(Setting.rest(cw), 2_000),
                Phase(Setting(Operation.STANDARD, 2, 0, cw), 3_000),
            ),
            8_000, cyclic = false,
        )
        val b = SmartBench().ready()
        assertTrue(b.c.startJob(spec).ok)
        b.step(12_000)
        assertEquals(JobState.FINISHED.name, b.snap.jobState)
        val pattern = b.tx.map { it.running }.dropWhile { !it }.dropLastWhile { !it }
        val runs = ArrayList<Int>()
        var prev: Boolean? = null
        for (r in pattern) if (r != prev) { runs += 1; prev = r } else runs[runs.size - 1]++
        assertEquals("$runs", 3, runs.size)
        assertTrue("$runs", runs[1] in 9..11)
    }

    @Test fun tareIsSentOnceAndOnlyWhenIdle() {
        val b = SmartBench().ready()
        b.board.contentWeightG = 990
        b.step(400)
        assertEquals(990, b.snap.weightG)
        b.c.tare()
        b.step(600)
        assertEquals(1, b.tx.count { it.opcode == Mcs.OP_SCALE })
        assertEquals(0, b.snap.weightG)
        assertTrue(b.c.startJob(Programs.manual(1, 0, cw, 10_000)).ok)
        b.step(400)
        b.c.tare()
        b.step(2_000)
        assertEquals(1, b.tx.count { it.opcode == Mcs.OP_SCALE })
    }

    @Test fun userStopIsImmediate() {
        val b = SmartBench().ready()
        assertTrue(b.c.startJob(Programs.manual(3, 0, cw, 60_000)).ok)
        b.step(2_000)
        assertTrue(b.last().running)
        b.c.stop()
        b.step(200)
        assertFalse(b.last().running)
        assertEquals(JobState.ABORTED.name, b.snap.jobState)
        assertEquals(0, b.last().speedLevel)
    }

    @Test fun neverSendsAnythingButTheThreeKnownOpcodes() {
        val b = SmartBench().ready()
        b.c.startJob(Programs.manual(2, connectLevel(60), cw, 20_000))
        b.step(25_000)
        b.c.tare()
        b.step(1_000)
        assertTrue(b.tx.all { it.opcode in Mcs.SENDABLE_OPCODES })
        assertTrue(b.tx.all { it.raw.size == 38 })
        assertTrue(b.tx.none { it.seq == 0x66 })
    }

    @Test fun robotModelIsDetectedFromTheOriginalApp() {
        assertEquals(RobotModel.SMART, RobotModel.detect { it == "com.tecpal.device.mc30" })
        assertEquals(RobotModel.CONNECT, RobotModel.detect { it == "de.silpion.mc2" })
        assertEquals(RobotModel.CONNECT, RobotModel.detect { false })
        assertEquals(RobotModel.SMART_R818, RobotModel.detect({ false }, { true }))
        assertTrue(RobotModel.isHardwareService("com.tecpal.device.serialport.service.SerialPortMC30Service"))
        assertTrue(RobotModel.isHardwareService("de.silpion.mc2.HardwareMachineService"))
        assertFalse(RobotModel.isHardwareService("org.eclipse.paho.android.service.MqttService"))
        assertEquals("/dev/ttyMT1", RobotModel.SMART.serialDevice)
        assertEquals("/dev/ttyS2", RobotModel.SMART_R818.serialDevice)
        assertEquals(9600, RobotModel.SMART.baud)
        assertEquals(9600, RobotModel.SMART_R818.baud)
        assertTrue(RobotModel.SMART.isSmart)
        assertTrue(RobotModel.SMART_R818.isSmart)
        assertFalse(RobotModel.CONNECT.isSmart)
    }

    @Test fun staleIdleTimeoutOnlyAppliesWhenResting() {
        // Profil R818 avec staleIdleMs élargi à 4000 ms mais staleMs strict à 1500 ms
        val config = SmartConfig(staleMs = 1500, staleIdleMs = 4000)
        val b = SmartBench(config).ready()
        assertEquals(LinkState.READY, b.c.link)

        // Au repos : un silence de 2000 ms ne coupe pas la liaison
        b.clock.now += 2000
        b.c.tick()
        assertEquals(LinkState.READY, b.c.link)

        // Mais après 4100 ms au repos, la liaison est perdue
        b.clock.now += 2100
        b.c.tick()
        assertEquals(LinkState.LOST, b.c.link)

        // Réarmement
        b.ready()
        assertEquals(LinkState.READY, b.c.link)

        // En marche (programme manuel) : un silence de 1600 ms coupe immédiatement
        assertTrue(b.c.startJob(Programs.manual(1, 0, cw, 60_000L)).ok)
        b.step(200)
        assertEquals(LinkState.READY, b.c.link)
        b.clock.now += 1600
        b.c.tick()
        assertEquals(LinkState.LOST, b.c.link)
    }

    @Test fun constantTemperatureOnlySentWhenConfigured() {
        // MediaTek par défaut : constantTemperature = 0
        val bMtk = SmartBench(SmartConfig(sendConstantTemperature = false)).ready()
        assertTrue(bMtk.c.startJob(Programs.manual(1, connectLevel(70), cw, 60_000L)).ok)
        bMtk.step(400)
        assertEquals(0, bMtk.last().constantTemperature)

        // Profil R818 : constantTemperature = 70
        val bR818 = SmartBench(SmartConfig(sendConstantTemperature = true)).ready()
        assertTrue(bR818.c.startJob(Programs.manual(1, connectLevel(70), cw, 60_000L)).ok)
        bR818.step(400)
        assertEquals(70, bR818.last().constantTemperature)
    }

    /** La vraie boucle (fils d'émission et de réception) avec la base simulée du Smart. */
    @Test fun runsInTheRealLoop() {
        val board = SmartSimBoard(MonotonicClock)
        val last = java.util.concurrent.atomic.AtomicReference<Snapshot?>(null)
        val loop = MachineLoop(
            transportFactory = { SmartSimTransport(board) },
            clock = MonotonicClock,
            periodMs = RobotModel.SMART.periodMs,
            onSnapshot = { last.set(it) },
            controllerFactory = { RobotModel.SMART.controller(it, ControllerConfig()) },
        )
        loop.start()
        try {
            fun waitFor(ms: Long, what: (Snapshot) -> Boolean): Boolean {
                val end = System.currentTimeMillis() + ms
                while (System.currentTimeMillis() < end) {
                    val s = last.get()
                    if (s != null && what(s)) return true
                    Thread.sleep(20)
                }
                return false
            }
            assertTrue("liaison", waitFor(3_000) { it.ready })
            val v = loop.call { it.startJob(Programs.manual(2, 0, cw, 2_000)) }
            assertTrue(v.message, v.ok)
            assertTrue("marche", waitFor(2_000) { it.state == JobState.RUNNING && it.speedLevel == 2 })
            assertTrue("fin", waitFor(5_000) { it.state == JobState.FINISHED })
            assertTrue("arrêt", waitFor(1_000) { it.wireOperation == Operation.STOP.name && it.speedLevel == 0 })
            assertEquals("", last.get()!!.faultCode)
        } finally {
            loop.shutdown()
        }
    }
}
