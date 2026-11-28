/*
 * Protocole série de la base du Monsieur Cuisine Smart (appli d'origine com.tecpal.device.mc30,
 * version 1.2.14.15, versionCode 10214015).
 *
 * Mise à jour d'après l'appli 2.20.2.2 (versionCode 22002002) installée sur le robot de référence :
 * même enveloppe, même trame d'état de 48 octets, mais commande de 38 octets (champ lumière sur
 * 1 octet au lieu de 2). Vérifié octet pour octet contre le constructeur de trames de cette version.
 *
 * Adapté du « Monsieur Cuisine Developer SDK » (licence MIT, voir
 * docs/LICENSE-monsieur-cuisine-developer-sdk.txt). Comme pour le Connect, les faits de protocole
 * viennent de l'analyse statique de l'appli d'origine : rien ici n'a encore été mesuré sur une vraie
 * machine. En particulier, inconnus à ce jour : la correspondance entre niveaux moteur bruts et
 * vitesse réelle, l'existence d'accusés de réception, les sécurités propres à la base, le
 * déroulé des programmes spéciaux.
 *
 * Liaison : /dev/ttyMT1, 9600 bauds (choix de l'appli d'origine ; réglages électriques inconnus).
 *
 * Enveloppe des commandes : AA | longueur u16 BE (taille totale moins 1) | 02 | bitmap 4F 42 | sous-type 00
 *             | séquence | opcode u16 BE | champs | somme.
 * Somme : somme des octets 1 à n-2 modulo 256 (ni CRC, ni authentification).
 * Commande : 38 octets avec l'appli 2.20 (charge utile 29), 39 octets avec la 1.2.14 (charge utile 30),
 * 35 octets dans l'ancienne forme (charge utile 26). État reçu : 48 octets (charge utile 39).
 * L'appli 2.20 renvoie la commande complète toutes les 200 ms ; au repos, elle envoie 0802 avec la
 * fonction 0 et tous les champs à zéro.
 *
 * Observé sur le robot de référence (capture passive du 21/11/2026, firmware 3090) :
 * - la base répond à chaque commande en 100 ms environ, par une trame de 53 octets dont l'en-tête est
 *   AA | longueur | 01 0F 00 00 | séquence, suivie de 44 octets (les 39 champs connus, puis 5 octets à 0) ;
 * - la séquence de la réponse est celle de la commande : c'est l'accusé de réception ;
 * - vitesse, fonction, niveau de température et compte à rebours de la réponse sont l'écho de la
 *   commande, pas des mesures ; la base ne décompte pas le temps, c'est l'appli qui envoie l'arrêt ;
 * - poids en grammes (990 lu pour 990 affichés), bit 0x80 couvercle ouvert, bit 0x40 bol absent,
 *   état de balance 4 en surcharge, 2 pendant une cuisson ;
 * - température réelle en °C, mais seulement par paliers de la table (37, 40, 45…) et 0 sous 37 °C ;
 *   à 70 °C demandés, 500 ml d'eau : 37 °C atteints en 106 s, 55 °C en 169 s ; température renvoyée à 0.
 * Comme l'appli 2.20, on lit donc la séquence à l'octet 7 et les champs à partir de l'octet 8.
 *
 * Volontairement absents : les trames de mise à jour du microcontrôleur (FExx) et l'opcode 0805,
 * dont le sens est inconnu. Toque ne doit jamais les émettre.
 */
package app.toque.core.protocol

import app.toque.core.i18n.tr
import java.io.IOException

class McsProtocolException(message: String) : IOException(message)

/** Enveloppe décodée, sans interprétation des champs. */
class McsFrame(
    val raw: ByteArray,
    /** Octets 3 à 6 : 02 4F 42 00 dans nos commandes, 01 0F 00 00 dans les réponses de la base. */
    val header: ByteArray,
    val sequence: Int,
    val opcode: Int,
    /** Charge utile, opcode compris. */
    val payload: ByteArray,
) {
    val hex: String get() = Mcs.hex(raw)
}

/**
 * Compteur de séquence de l'appli d'origine : +1 modulo 256, 0x66 sauté (0x65 → 0x67), 0 permis
 * au passage par 255. Ce n'est pas un accusé de réception : la base répond avec son propre compteur.
 */
class McsSequence(start: Int = 0) {
    private var value = start

    init {
        require(start in 0..255) { "séquence hors plage 0..255" }
    }

    @Synchronized
    fun next(): Int {
        value = (value + 1) and 0xFF
        if (value == 0x66) value = 0x67
        return value
    }
}

/** Disposition des champs d'une commande, selon la version de l'appli d'origine imitée. */
enum class McsLayout(val payloadSize: Int, val lightBytes: Int, val remarkBytes: Int) {
    /** Appli 2.20.2.2 : lumière sur 1 octet, 4 octets de remarques. Trame de 38 octets. */
    APP_2_20(29, 1, 4),
    /** Appli 1.2.14.15 (SDK) : lumière sur 2 octets, 4 octets de remarques. Trame de 39 octets. */
    APP_1_2_14(30, 2, 4),
    /** Ancienne forme de la 1.2.14.15 : lumière sur 2 octets, sans remarques. Trame de 35 octets. */
    LEGACY(26, 2, 0);

    val frameSize: Int get() = payloadSize + 9
}

/** Une commande (opcode 0801, 0802 ou 0803), avec les valeurs brutes de la trame. */
class McsCommand(
    val opcode: Int = Mcs.OP_CONFIG,
    /** Niveau moteur brut 0..20 (vitesse affichée × 2). */
    val speedLevel: Int = 0,
    val reverse: Boolean = false,
    /** Indice dans [Mcs.TEMPERATURE_LEVELS], 0..20. */
    val temperatureLevel: Int = 0,
    val constantTemperature: Int = 0,
    val function: Int = Mcs.FN_STOP,
    /** Compte à rebours envoyé à la base, en secondes. */
    val seconds: Int = 0,
    val scaleMode: Int = 0,
    val turboLock: Int = 0,
    val button: Int = 0,
    val layout: McsLayout = Mcs.DEFAULT_LAYOUT,
)

/** Une trame d'état de 48 octets, décodée comme le fait l'appli d'origine. */
class McsTelemetry(
    val frame: McsFrame,
    val firmwareVersion: Int,
    val stateRaw: Int,
    val stateRemarks: Int,
    val errorCode: Int,
    val returnCode: Int,
    /** Octets signés, comme les lit l'appli d'origine. */
    val temperatureLevel: Int,
    val returnedTemperature: Int,
    val actualTemperature: Int,
    val temperatureUnit: Int,
    val speedLevel: Int,
    val directionRaw: Int,
    val turboKey: Int,
    val function: Int,
    val weightUnit: Int,
    val weightDecimals: Int,
    val weightRaw: Int,
    val scaleState: Int,
    val calibrationStep: Int,
    val currentSeconds: Int,
    val countdownSeconds: Int,
    val appointmentSeconds: Int,
    val lightRaw: Int,
    val buttonRaw: Int,
    val warnings: List<String>,
) {
    val raw: ByteArray get() = frame.raw
    val hex: String get() = frame.hex

    // Bits de l'octet d'état, tels que l'appli d'origine les nomme (polarité électrique non vérifiée).
    val lidOpen: Boolean get() = stateRaw and 0x80 != 0
    val cupNtcAbsent: Boolean get() = stateRaw and 0x40 != 0
    val temperatureHighBit: Boolean get() = stateRaw and 0x20 != 0
    val motorClockwiseBit: Boolean get() = stateRaw and 0x08 != 0
    val scaleStable: Boolean get() = stateRaw and 0x04 != 0

    /** Vitesse affichée par l'appli d'origine : niveau ÷ 2, tronqué vers zéro. */
    val uiSpeed: Int get() = speedLevel / 2
    val directionSigned: Int get() = if (directionRaw < 128) directionRaw else directionRaw - 256
    val errorDetected: Boolean get() = errorCode != 0
    val errorName: String get() = Mcs.ERROR_NAMES[errorCode] ?: "UNKNOWN"
    val countdownMillis: Long get() = countdownSeconds * 1000L
    /** Octets au-delà des 39 champs connus (5 octets à 0 sur le robot de référence). */
    val extraBytes: ByteArray get() = frame.payload.copyOfRange(Mcs.RX_PAYLOAD_SIZE, frame.payload.size)
    val scaleOverload: Boolean get() = scaleState == 4

    /** Poids en grammes si l'unité est 0 (seul cas connu), sinon null. */
    val weightGrams: Double?
        get() = if (weightUnit != 0) null else weightRaw / Math.pow(10.0, weightDecimals.toDouble())
}

object Mcs {
    const val DEVICE_PATH = "/dev/ttyMT1"
    const val DEVICE_PATH_ALLWINNER = "/dev/ttyS2"
    const val BAUD_RATE = 9600

    const val HEADER = 0xAA
    /** Disposition utilisée par défaut : celle de l'appli installée sur le robot de référence. */
    val DEFAULT_LAYOUT = McsLayout.APP_2_20
    val TX_SIZE: Int get() = DEFAULT_LAYOUT.frameSize
    const val RX_SIZE = 48
    /** Champs connus de la trame d'état (charge utile de 39 octets dans le SDK, 44 sur le robot). */
    const val RX_PAYLOAD_SIZE = 39
    const val HEADER_SIZE = 8
    const val MIN_FRAME = HEADER_SIZE + 3
    /** Plus grande trame acceptée en lecture (les trames normales font 48 octets). */
    const val MAX_FRAME = 300

    val BITMAP = byteArrayOf(0x4F, 0x42)
    /** En-tête (octets 3 à 6) des réponses de la base sur le robot de référence. */
    val BOARD_HEADER = byteArrayOf(0x01, 0x0F, 0x00, 0x00)

    /** Instantané (sens exact de la réponse non vérifié). */
    const val OP_QUERY = 0x0801
    /** Réglage et arrêt. */
    const val OP_CONFIG = 0x0802
    /** Balance ; le mode 1 sert à la tare, le reste est inconnu. */
    const val OP_SCALE = 0x0803

    /** Opcodes qu'on peut émettre. */
    val SENDABLE_OPCODES: Set<Int> = setOf(OP_QUERY, OP_CONFIG, OP_SCALE)
    /** Opcodes acceptés dans une trame d'état de 48 octets (0805 : sens inconnu, lu seulement). */
    val TELEMETRY_OPCODES: Set<Int> = setOf(0x0801, 0x0802, 0x0803, 0x0805)

    // Codes « function » de l'appli d'origine. Un code ne décrit pas le déroulé complet d'un
    // programme ; plusieurs programmes partagent parfois le même code.
    const val FN_STOP = 0
    const val FN_MANUAL = 1
    const val FN_STEAM = 3
    const val FN_ROAST = 4
    const val FN_KNEAD = 5
    const val FN_SHORT_CLEANING = 6
    const val FN_LONG_CLEANING = 7
    const val FN_EGG = 8
    const val FN_SOUS_VIDE = 9
    const val FN_SLOW_COOK = 10
    const val FN_FERMENTATION = 11
    const val FN_PUREE = 12
    const val FN_SMOOTHIE = 13
    const val FN_SLEEP = 15
    const val FN_TURBO = 198

    /**
     * Fonction renvoyée par la base pour une fonction envoyée (écoutes du 21/11/2026, 13 programmes) :
     * 0 et 198 (Turbo) reviennent tels quels, toutes les autres reviennent en 1.
     */
    fun echoFunction(function: Int): Int = if (function == FN_STOP || function == FN_TURBO) function else FN_MANUAL

    /** Trame Turbo de l'appli d'origine (o7.a) : ancienne mise en page, 35 octets, bouton 6, 5 s. */
    fun encodeTurbo(sequence: Int): ByteArray = encode(
        McsCommand(turboLock = 10, function = FN_TURBO, seconds = 5, button = 6, layout = McsLayout.LEGACY), sequence,
    )

    /** Niveau de température (0..20) → °C, table de l'appli d'origine. */
    val TEMPERATURE_LEVELS: IntArray = intArrayOf(
        0, 37, 40, 45, 50, 55, 60, 65, 70, 75, 80,
        85, 90, 95, 100, 105, 110, 115, 120, 125, 130,
    )
    const val MAX_TEMPERATURE_LEVEL = 20
    const val MAX_SPEED_LEVEL = 20
    const val MAX_UI_SPEED = 10
    /** Durée maximale de la cuisson manuelle dans l'appli d'origine (99 min). */
    const val MAX_MANUAL_SECONDS = 5940
    /** Capacité du champ minuterie : 255 h 59 min 59 s. */
    const val MAX_TIMER_SECONDS = 255 * 3600 + 3599

    val ERROR_NAMES: Map<Int, String> = mapOf(
        0 to "NORMAL",
        1 to "SERIAL_PORT_ERROR",
        3 to "MOTOR_FAILURE",
        4 to "VOLTAGE_OVERLOAD",
        5 to "MOTOR_ERROR",
        6 to "DRY_BURN_ALARM",
        7 to "OVER_TEMPERATURE_PROTECTION",
        8 to "TIMEOUT_PROTECTION",
        9 to "OTHER_ERROR",
    )

    fun celsiusForLevel(level: Int): Int = TEMPERATURE_LEVELS[level.coerceIn(0, MAX_TEMPERATURE_LEVEL)]

    /** Niveau exact pour une température de la table, sinon -1. */
    fun levelForCelsius(celsius: Int): Int = TEMPERATURE_LEVELS.indexOf(celsius)

    /** Niveau le plus proche sans dépasser la température demandée (0 si < 37 °C). */
    fun levelAtMost(celsius: Int): Int {
        var best = 0
        for (i in TEMPERATURE_LEVELS.indices) if (TEMPERATURE_LEVELS[i] <= celsius) best = i
        return best
    }

    private fun u8(b: Byte): Int = b.toInt() and 0xFF
    private fun u16(p: ByteArray, i: Int): Int = (u8(p[i]) shl 8) or u8(p[i + 1])
    private fun s16(p: ByteArray, i: Int): Int = u16(p, i).toShort().toInt()
    private fun s32(p: ByteArray, i: Int): Int =
        (u8(p[i]) shl 24) or (u8(p[i + 1]) shl 16) or (u8(p[i + 2]) shl 8) or u8(p[i + 3])
    private fun hms(p: ByteArray, i: Int): Int = u8(p[i]) * 3600 + u8(p[i + 1]) * 60 + u8(p[i + 2])

    private fun check(value: Int, range: IntRange, name: String) {
        if (value !in range) throw McsProtocolException("$name hors plage ${range.first}..${range.last}")
    }

    /** Somme des octets 1 à n-2 (en-tête et somme exclus), modulo 256. */
    fun checksum(raw: ByteArray): Int {
        var sum = 0
        for (i in 1 until raw.size - 1) sum = (sum + u8(raw[i])) and 0xFF
        return sum
    }

    /** Construit l'enveloppe autour d'une charge utile (opcode compris). */
    fun pack(payload: ByteArray, sequence: Int, subtype: Int = 0, bitmap: ByteArray = BITMAP): ByteArray {
        check(sequence, 0..255, "séquence")
        check(subtype, 0..255, "sous-type")
        if (payload.size < 2) throw McsProtocolException("charge utile sans opcode")
        // L'appli 2.20 lit toujours un en-tête de 8 octets : le bitmap fait donc 2 octets.
        if (bitmap.size != 2) throw McsProtocolException("taille de bitmap")
        val total = 7 + bitmap.size + payload.size
        if (total > 65536) throw McsProtocolException("trame trop longue")
        val out = ByteArray(total)
        out[0] = HEADER.toByte()
        out[1] = ((total - 1) ushr 8).toByte()
        out[2] = (total - 1).toByte()
        out[3] = bitmap.size.toByte()
        bitmap.copyInto(out, 4)
        out[4 + bitmap.size] = subtype.toByte()
        out[5 + bitmap.size] = sequence.toByte()
        payload.copyInto(out, 6 + bitmap.size)
        out[out.size - 1] = checksum(out).toByte()
        return out
    }

    /**
     * Vérifie et découpe une trame complète, à la taille exacte (pas d'octets en trop). Comme l'appli
     * 2.20, l'en-tête fait toujours 8 octets : séquence à l'octet 7, champs à partir de l'octet 8.
     */
    fun parse(raw: ByteArray): McsFrame {
        if (raw.size < MIN_FRAME || u8(raw[0]) != HEADER) throw McsProtocolException("en-tête ou trame incomplète")
        if (u16(raw, 1) + 1 != raw.size) throw McsProtocolException("longueur déclarée")
        if (checksum(raw) != u8(raw[raw.size - 1])) throw McsProtocolException("somme de contrôle")
        val payload = raw.copyOfRange(HEADER_SIZE, raw.size - 1)
        return McsFrame(raw.copyOf(), raw.copyOfRange(3, 7), u8(raw[7]), u16(payload, 0), payload)
    }

    private fun putHms(p: ByteArray, i: Int, seconds: Int) {
        check(seconds, 0..MAX_TIMER_SECONDS, "durée")
        p[i] = (seconds / 3600).toByte()
        p[i + 1] = (seconds / 60 % 60).toByte()
        p[i + 2] = (seconds % 60).toByte()
    }

    /** Encode une commande. Refuse les valeurs hors plage au lieu de les corriger. */
    fun encode(c: McsCommand, sequence: Int): ByteArray {
        if (c.opcode !in SENDABLE_OPCODES) throw McsProtocolException("opcode non autorisé")
        check(c.speedLevel, 0..MAX_SPEED_LEVEL, "niveau moteur")
        check(c.temperatureLevel, 0..MAX_TEMPERATURE_LEVEL, "niveau de température")
        check(c.constantTemperature, 0..255, "température constante")
        check(c.function, 0..255, "fonction")
        check(c.scaleMode, 0..255, "mode balance")
        check(c.turboLock, 0..255, "turbo")
        check(c.button, 0..255, "bouton")
        val l = c.layout
        val p = ByteArray(l.payloadSize)
        p[0] = (c.opcode ushr 8).toByte()
        p[1] = c.opcode.toByte()
        p[2] = c.temperatureLevel.toByte()
        // Particularité de l'appli d'origine : l'octet haut de la température constante vaut
        // toujours 0 ((byte)(T << 8)), l'octet bas porte T. Unité (5) et remarques (6, 7) à zéro.
        p[3] = 0
        p[4] = c.constantTemperature.toByte()
        p[8] = c.speedLevel.toByte()
        p[9] = (if (c.reverse) 1 else 0).toByte()
        p[10] = c.turboLock.toByte()
        p[11] = c.function.toByte()
        // 12 : corps de balance (0) ; 13 : mode balance ; 14..16 : heure courante (0).
        p[13] = c.scaleMode.toByte()
        putHms(p, 17, c.seconds) // compte à rebours ; 20..22 : rendez-vous (0)
        // Puis lumière (1 ou 2 octets, 0), bouton, remarques (0 ou 4 octets, 0).
        p[23 + l.lightBytes] = c.button.toByte()
        return pack(p, sequence)
    }

    /**
     * Cuisson manuelle, comme l'assistant de l'appli d'origine : vitesse affichée 0..10,
     * température de la table, durée 0..5940 s.
     */
    fun encodeManual(
        speed: Int,
        celsius: Int,
        seconds: Int,
        reverse: Boolean,
        sequence: Int,
        layout: McsLayout = DEFAULT_LAYOUT,
    ): ByteArray {
        check(speed, 0..MAX_UI_SPEED, "vitesse")
        check(seconds, 0..MAX_MANUAL_SECONDS, "durée manuelle")
        val level = levelForCelsius(celsius)
        if (level < 0) throw McsProtocolException("température absente de la table")
        return encode(
            McsCommand(
                speedLevel = speed * 2, temperatureLevel = level, function = FN_MANUAL,
                seconds = seconds, reverse = reverse, layout = layout,
            ),
            sequence,
        )
    }

    /** Arrêt, et trame de repos de l'appli 2.20 : réglage 0802 tout à zéro (fonction « stop »). */
    fun encodeStop(sequence: Int): ByteArray = encode(McsCommand(opcode = OP_CONFIG, function = FN_STOP), sequence)

    /** Interrogation : 0801, tous les champs à zéro. */
    fun encodeQuery(sequence: Int): ByteArray = encode(McsCommand(opcode = OP_QUERY), sequence)

    /** Tare de la balance (opcode 0803, mode 1). */
    fun encodeTare(sequence: Int): ByteArray = encode(McsCommand(opcode = OP_SCALE, scaleMode = 1), sequence)

    /** Décode une trame d'état : 48 octets dans le SDK, 53 octets sur le robot de référence. */
    fun decodeTelemetry(raw: ByteArray): McsTelemetry {
        val f = parse(raw)
        val p = f.payload
        if (f.opcode !in TELEMETRY_OPCODES || p.size < RX_PAYLOAD_SIZE)
            throw McsProtocolException("pas une trame d'état")
        if (u8(p[32]) > 59 || u8(p[33]) > 59) throw McsProtocolException("compte à rebours invalide")
        val warnings = ArrayList<String>(0)
        val speed = p[17].toInt()
        val tempLevel = p[9].toInt()
        val error = u8(p[7])
        if (speed !in 0..MAX_SPEED_LEVEL) warnings += "Niveau moteur hors 0..20"
        if (tempLevel !in 0..MAX_TEMPERATURE_LEVEL) warnings += "Niveau de température hors 0..20"
        if (error !in ERROR_NAMES) warnings += "Erreur non répertoriée"
        if (u8(p[4]) and 0x13 != 0) warnings += "Bits d'état inconnus"
        if (f.opcode == 0x0805) warnings += "Opcode 0805 (sens inconnu)"
        if (!f.header.contentEquals(BOARD_HEADER) && !f.header.contentEquals(byteArrayOf(0x02, 0x4F, 0x42, 0x00)))
            warnings += "En-tête inconnu"
        for (i in RX_PAYLOAD_SIZE until p.size) if (p[i].toInt() != 0) { warnings += "Octets supplémentaires non nuls"; break }
        return McsTelemetry(
            frame = f,
            firmwareVersion = u16(p, 2),
            stateRaw = u8(p[4]),
            stateRemarks = u16(p, 5),
            errorCode = error,
            returnCode = u8(p[8]),
            temperatureLevel = tempLevel,
            returnedTemperature = s16(p, 10),
            actualTemperature = s16(p, 12),
            temperatureUnit = p[14].toInt(),
            speedLevel = speed,
            directionRaw = u8(p[18]),
            turboKey = p[19].toInt(),
            function = u8(p[20]),
            weightUnit = u8(p[21]) ushr 4,
            weightDecimals = u8(p[21]) and 0x0F,
            weightRaw = s32(p, 22),
            scaleState = u8(p[26]),
            calibrationStep = u8(p[27]),
            currentSeconds = hms(p, 28),
            countdownSeconds = hms(p, 31),
            appointmentSeconds = hms(p, 34),
            lightRaw = u8(p[37]),
            buttonRaw = u8(p[38]),
            warnings = warnings,
        )
    }

    /** Message lisible pour un code d'erreur de la base. */
    fun boardErrorMessage(code: Int): String = when (code) {
        1 -> tr("Erreur de communication signalée par la base")
        3 -> tr("Panne moteur")
        4 -> tr("Surtension secteur")
        5 -> tr("Erreur moteur")
        6 -> tr("Chauffe à sec : pas assez de liquide")
        7 -> tr("Surchauffe : laissez refroidir")
        8 -> tr("Durée maximale dépassée")
        9 -> tr("Autre erreur de la base")
        else -> tr("Erreur de la base (code %d)", code)
    }

    private val HEX = "0123456789ABCDEF".toCharArray()

    fun hex(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 3)
        for ((i, b) in bytes.withIndex()) {
            if (i > 0) sb.append(' ')
            val v = b.toInt() and 0xFF
            sb.append(HEX[v shr 4]).append(HEX[v and 0xF])
        }
        return sb.toString()
    }

    fun parseHex(text: String): ByteArray {
        val compact = text.filter { !it.isWhitespace() }
        if (compact.isEmpty() || compact.length % 2 != 0) throw McsProtocolException("hex invalide")
        return ByteArray(compact.length / 2) { compact.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }
}

/**
 * Découpage d'un flux d'octets : réassemble les trames fragmentées, accepte les trames collées,
 * se resynchronise après du bruit ou une trame corrompue. Un début de trame plausible mais
 * incomplet est gardé jusqu'à [expire], pour ne pas recouper à tort sur un octet AA interne.
 */
class McsStreamParser(private val maxFrame: Int = Mcs.MAX_FRAME, private val timeoutMillis: Long = 500) {
    init {
        require(maxFrame in Mcs.MIN_FRAME..65536 && timeoutMillis > 0)
    }

    private val buffer = ByteArray(maxFrame)
    private var count = 0
    private var since = -1L
    var discardedBytes = 0L
        private set
    var invalidFrames = 0L
        private set
    val bufferedBytes: Int get() = count

    fun reset() {
        count = 0
        since = -1L
    }

    private fun drop(n: Int) {
        System.arraycopy(buffer, n, buffer, 0, count - n)
        count -= n
        discardedBytes += n
        since = -1L
    }

    fun feed(
        data: ByteArray,
        length: Int = data.size,
        nowMillis: Long = System.nanoTime() / 1_000_000,
        out: MutableList<ByteArray> = ArrayList(2),
    ): MutableList<ByteArray> {
        var i = 0
        while (i < length) {
            val room = maxFrame - count
            val n = minOf(room, length - i)
            System.arraycopy(data, i, buffer, count, n)
            count += n
            i += n
            scan(nowMillis, out)
            if (count == maxFrame) { invalidFrames++; drop(1); scan(nowMillis, out) }
        }
        return out
    }

    /** Abandonne un début de trame resté incomplet trop longtemps, puis reprend le découpage. */
    fun expire(nowMillis: Long = System.nanoTime() / 1_000_000, out: MutableList<ByteArray> = ArrayList(1)): MutableList<ByteArray> {
        if (count > 0 && since >= 0 && nowMillis - since >= timeoutMillis) {
            invalidFrames++
            drop(1)
        }
        scan(nowMillis, out)
        return out
    }

    private fun scan(nowMillis: Long, out: MutableList<ByteArray>) {
        while (count > 0) {
            if ((buffer[0].toInt() and 0xFF) != Mcs.HEADER) {
                var next = 1
                while (next < count && (buffer[next].toInt() and 0xFF) != Mcs.HEADER) next++
                drop(next)
                continue
            }
            if (since < 0) since = nowMillis
            if (count < 3) return
            val total = (((buffer[1].toInt() and 0xFF) shl 8) or (buffer[2].toInt() and 0xFF)) + 1
            if (total !in Mcs.MIN_FRAME..maxFrame) { invalidFrames++; drop(1); continue }
            if (count < total) return
            val candidate = buffer.copyOf(total)
            try {
                Mcs.parse(candidate)
            } catch (e: McsProtocolException) {
                invalidFrames++
                drop(1)
                continue
            }
            out.add(candidate)
            System.arraycopy(buffer, total, buffer, 0, count - total)
            count -= total
            since = -1L
        }
    }
}
