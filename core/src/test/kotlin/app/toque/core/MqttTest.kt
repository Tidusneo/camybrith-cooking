package app.toque.core

import app.toque.core.json.Json
import app.toque.core.json.obj
import app.toque.core.machine.JobState
import app.toque.core.machine.LinkState
import app.toque.core.machine.Snapshot
import app.toque.core.mqtt.MiniMqtt
import app.toque.core.mqtt.RobotStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.DataInputStream
import java.net.ServerSocket
import kotlin.concurrent.thread

class MqttTest {
    /** Courtier factice : accepte la connexion, puis note les paquets reçus (type, contenu). */
    private fun broker(code: Int, packets: MutableList<Pair<Int, ByteArray>>, count: Int): Pair<Int, Thread> {
        val server = ServerSocket(0)
        val t = thread {
            server.use { srv ->
                srv.accept().use { s ->
                    val input = DataInputStream(s.getInputStream())
                    fun read(): Pair<Int, ByteArray> {
                        val type = input.readUnsignedByte()
                        var len = 0; var mul = 1
                        while (true) { val d = input.readUnsignedByte(); len += (d and 0x7F) * mul; if (d and 0x80 == 0) break; mul *= 128 }
                        return type to ByteArray(len).also { input.readFully(it) }
                    }
                    packets += read()
                    s.getOutputStream().apply { write(byteArrayOf(0x20, 2, 0, code.toByte())); flush() }
                    repeat(count) { packets += read() }
                }
            }
        }
        return server.localPort to t
    }

    @Test fun connectsWithWillAndCredentialsThenPublishesRetained() {
        val packets = java.util.Collections.synchronizedList(ArrayList<Pair<Int, ByteArray>>())
        val (port, t) = broker(0, packets, 2)
        val m = MiniMqtt("127.0.0.1", port, "camybrith-test", "ha", "secret", "camybrith/availability")
        m.connect()
        m.publish("camybrith/weight", "195")
        m.ping()
        t.join(5_000)
        m.close()
        val connect = String(packets[0].second, Charsets.ISO_8859_1)
        assertEquals(0x10, packets[0].first)
        assertTrue(connect.contains("MQTT") && connect.contains("camybrith/availability") && connect.contains("offline"))
        assertTrue(connect.contains("ha") && connect.contains("secret"))
        assertEquals(0xE6, packets[0].second[7].toInt() and 0xFF) // nom, mot de passe, testament conservé, session propre
        assertEquals(0x31, packets[1].first) // PUBLISH conservé
        assertTrue(String(packets[1].second).endsWith("camybrith/weight195"))
        assertEquals(0xC0, packets[2].first)
    }

    @Test(expected = java.io.IOException::class) fun refusedCredentialsFail() {
        val (port, _) = broker(5, ArrayList(), 0)
        MiniMqtt("127.0.0.1", port, "x", "bad", "bad").connect()
    }

    @Test fun longPayloadUsesMultiByteLength() {
        val p = MiniMqtt.packet(0x30, ByteArray(300))
        assertEquals(0x30, p[0].toInt())
        assertEquals(0xAC, p[1].toInt() and 0xFF)
        assertEquals(0x02, p[2].toInt())
        assertEquals(303, p.size)
    }

    @Test fun statusFollowsTheMachine() {
        val ready = Snapshot(link = LinkState.READY, weightG = 195, actualTempC = 42, lidOpen = true)
        assertEquals("idle", RobotStatus.status(ready))
        assertEquals("disconnected", RobotStatus.status(Snapshot()))
        assertEquals("fault", RobotStatus.status(ready.copy(faultCode = "ECHO_MISMATCH")))
        assertEquals("paused", RobotStatus.status(ready.copy(jobState = JobState.PAUSED.name)))
        val running = ready.copy(jobState = JobState.RUNNING.name, jobLabel = "Cuisson", speedLevel = 2, targetTempC = 90, remainingMs = 61_500)
        val v = RobotStatus.values(running)
        assertEquals("running", v["status"])
        assertEquals("Cuisson", v["program"])
        assertEquals("62", v["remaining"])
        assertEquals("195", v["weight"])
        assertEquals("ON", v["lid"])
        assertEquals("0", RobotStatus.values(ready)["speed"])
    }

    @Test fun discoveryDescribesEverySensor() {
        val d = RobotStatus.discovery("camybrith", "1.4.0", "Monsieur Cuisine Smart")
        assertEquals(9, d.size)
        val lid = Json.parse(d.getValue("homeassistant/binary_sensor/camybrith_camybrith/lid/config")).obj()
        assertEquals("camybrith/lid", lid["state_topic"])
        assertEquals("opening", lid["device_class"])
        val w = Json.parse(d.getValue("homeassistant/sensor/camybrith_camybrith/weight/config")).obj()
        assertEquals("g", w["unit_of_measurement"])
        assertEquals("camybrith/availability", w["availability_topic"])
    }
}
