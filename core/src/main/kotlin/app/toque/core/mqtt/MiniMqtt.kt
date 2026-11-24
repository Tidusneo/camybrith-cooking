package app.toque.core.mqtt

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.IOException
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Client MQTT 3.1.1 minimal, en publication seule (QoS 0). Camybrith n'écoute aucun sujet : rien
 * de ce qui arrive du réseau ne peut commander le robot.
 *
 * Testament : si la liaison tombe, le courtier publie [willPayload] (conservé) sur [willTopic].
 */
class MiniMqtt(
    private val host: String,
    private val port: Int,
    private val clientId: String,
    private val user: String? = null,
    private val password: String? = null,
    private val willTopic: String? = null,
    private val willPayload: String = "offline",
    val keepAliveSec: Int = 60,
) {
    private var socket: Socket? = null
    private var out: OutputStream? = null

    val connected: Boolean get() = socket?.isConnected == true && socket?.isClosed == false

    @Synchronized
    fun connect(timeoutMs: Int = 5_000) {
        close()
        val s = Socket()
        try {
            s.connect(InetSocketAddress(host, port), timeoutMs)
            s.soTimeout = timeoutMs
            s.tcpNoDelay = true
            val o = s.getOutputStream()
            o.write(connectPacket())
            o.flush()
            val input = DataInputStream(s.getInputStream())
            val head = input.readUnsignedByte()
            val len = input.readUnsignedByte()
            val body = ByteArray(len).also { input.readFully(it) }
            if (head != 0x20 || len != 2) throw IOException("réponse inattendue du courtier")
            when (body[1].toInt()) {
                0 -> Unit
                4, 5 -> throw IOException("identifiant ou mot de passe refusé")
                else -> throw IOException("connexion refusée (code ${body[1]})")
            }
            s.soTimeout = 0
            socket = s
            out = o
        } catch (e: Exception) {
            try { s.close() } catch (_: Exception) {}
            throw e
        }
    }

    @Synchronized
    fun publish(topic: String, payload: String, retain: Boolean = true) {
        val o = out ?: throw IOException("non connecté")
        val body = ByteArrayOutputStream()
        writeString(body, topic)
        body.write(payload.toByteArray(Charsets.UTF_8))
        o.write(packet(0x30 or (if (retain) 1 else 0), body.toByteArray()))
        o.flush()
    }

    @Synchronized
    fun ping() {
        val o = out ?: throw IOException("non connecté")
        o.write(byteArrayOf(0xC0.toByte(), 0))
        o.flush()
    }

    /** Fermeture propre : le testament n'est pas publié, on publie soi-même « offline » avant. */
    @Synchronized
    fun close() {
        try { out?.write(byteArrayOf(0xE0.toByte(), 0)); out?.flush() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
        socket = null
        out = null
    }

    internal fun connectPacket(): ByteArray {
        val b = ByteArrayOutputStream()
        writeString(b, "MQTT")
        b.write(4) // MQTT 3.1.1
        var flags = 0x02 // session propre
        if (willTopic != null) flags = flags or 0x04 or 0x20 // testament conservé, QoS 0
        if (!user.isNullOrEmpty()) flags = flags or 0x80
        if (!user.isNullOrEmpty() && !password.isNullOrEmpty()) flags = flags or 0x40
        b.write(flags)
        b.write(keepAliveSec ushr 8); b.write(keepAliveSec and 0xFF)
        writeString(b, clientId)
        if (willTopic != null) { writeString(b, willTopic); writeString(b, willPayload) }
        if (!user.isNullOrEmpty()) writeString(b, user)
        if (!user.isNullOrEmpty() && !password.isNullOrEmpty()) writeString(b, password)
        return packet(0x10, b.toByteArray())
    }

    companion object {
        internal fun packet(type: Int, body: ByteArray): ByteArray {
            val b = ByteArrayOutputStream()
            b.write(type)
            var n = body.size
            do {
                var d = n % 128
                n /= 128
                if (n > 0) d = d or 0x80
                b.write(d)
            } while (n > 0)
            b.write(body)
            return b.toByteArray()
        }

        private fun writeString(b: ByteArrayOutputStream, s: String) {
            val bytes = s.toByteArray(Charsets.UTF_8)
            b.write(bytes.size ushr 8); b.write(bytes.size and 0xFF)
            b.write(bytes)
        }
    }
}
