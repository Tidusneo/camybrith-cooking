package app.toque.core.mqtt

import app.toque.core.json.Json
import app.toque.core.machine.JobState
import app.toque.core.machine.LinkState
import app.toque.core.machine.Snapshot

/**
 * État du robot publié en MQTT, en lecture seule, et la découverte automatique de Home Assistant.
 *
 * Sujets, sous [base] (par défaut `camybrith`) : `availability` (online/offline), `status`
 * (idle, running, paused, fault, disconnected), `program`, `temperature`, `target_temperature`,
 * `speed`, `weight`, `remaining` (secondes), `lid` (ON ouvert, OFF fermé), `fault`. « none » quand il n'y a
 * pas de programme ou pas d'alerte : un message vide effacerait la valeur conservée par le courtier.
 */
object RobotStatus {
    const val DISCOVERY_PREFIX = "homeassistant"

    fun status(s: Snapshot): String = when {
        s.hasFault -> "fault"
        s.link != LinkState.READY -> "disconnected"
        s.running || s.turboActive -> "running"
        s.state == JobState.PAUSED -> "paused"
        else -> "idle"
    }

    /** Valeur de chaque sujet (sans le préfixe). */
    fun values(s: Snapshot): Map<String, String> = linkedMapOf(
        "status" to status(s),
        "program" to (if (s.busy) s.jobLabel.ifEmpty { "none" } else "none"),
        "temperature" to s.actualTempC.toString(),
        "target_temperature" to (if (s.busy) s.targetTempC else 0).toString(),
        "speed" to (if (s.busy) s.speedLevel else 0).toString(),
        "weight" to s.weightG.toString(),
        "remaining" to (if (s.busy) (s.remainingMs + 999) / 1000 else 0).toString(),
        "lid" to (if (s.lidOpen) "ON" else "OFF"),
        "fault" to (if (s.hasFault) s.faultMessage.ifEmpty { s.faultCode } else "none"),
    )

    private class Entity(val key: String, val name: String, val component: String = "sensor", val extra: Map<String, Any?> = emptyMap())

    private val entities = listOf(
        Entity("status", "Status", extra = mapOf("icon" to "mdi:chef-hat")),
        Entity("program", "Program", extra = mapOf("icon" to "mdi:silverware-fork-knife")),
        Entity("temperature", "Temperature", extra = mapOf("device_class" to "temperature", "unit_of_measurement" to "°C", "state_class" to "measurement")),
        Entity("target_temperature", "Target temperature", extra = mapOf("device_class" to "temperature", "unit_of_measurement" to "°C")),
        Entity("speed", "Speed", extra = mapOf("icon" to "mdi:fan")),
        Entity("weight", "Weight", extra = mapOf("device_class" to "weight", "unit_of_measurement" to "g", "state_class" to "measurement")),
        Entity("remaining", "Time remaining", extra = mapOf("device_class" to "duration", "unit_of_measurement" to "s")),
        Entity("lid", "Lid", component = "binary_sensor", extra = mapOf("device_class" to "opening")),
        Entity("fault", "Fault", extra = mapOf("icon" to "mdi:alert-circle")),
    )

    /** Messages de découverte : sujet de configuration → contenu JSON (à publier conservés). */
    fun discovery(base: String, version: String, model: String): Map<String, String> {
        val id = "camybrith_" + base.replace(Regex("[^A-Za-z0-9_]"), "_")
        val device = mapOf(
            "identifiers" to listOf(id),
            "name" to "Camybrith Cooking",
            "manufacturer" to "Camybrith",
            "model" to model,
            "sw_version" to version,
        )
        val out = LinkedHashMap<String, String>()
        for (e in entities) {
            val config = LinkedHashMap<String, Any?>()
            config["name"] = e.name
            config["unique_id"] = "${id}_${e.key}"
            config["object_id"] = "${id}_${e.key}"
            config["state_topic"] = "$base/${e.key}"
            config["availability_topic"] = "$base/availability"
            if (e.component == "binary_sensor") { config["payload_on"] = "ON"; config["payload_off"] = "OFF" }
            config.putAll(e.extra)
            config["device"] = device
            out["$DISCOVERY_PREFIX/${e.component}/$id/${e.key}/config"] = Json.write(config)
        }
        return out
    }
}
