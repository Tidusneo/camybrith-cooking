# Home Assistant (MQTT)

Camybrith Cooking can send the robot's state to Home Assistant over MQTT. It is **read only**:
Camybrith subscribes to no topic, so nothing coming from the network can start the motor or the
heater. Off by default.

## Getting started

1. In Home Assistant, install an MQTT broker (for example the *Mosquitto broker* add‑on) and the
   **MQTT** integration. Create a user for the robot.
2. On the robot: *Settings › Home Assistant*. Turn on *Send the state over MQTT*, enter the server
   address (Home Assistant's IP), the port (1883), the user and password, then *Save*. The status
   line should read "Connected to …".
3. In Home Assistant, a **Camybrith Cooking** device shows up on its own (MQTT discovery), with its
   sensors.

## Topics

All under `camybrith/` (can be changed in the settings), retained by the broker:

| Topic | Value |
|---|---|
| `availability` | `online` or `offline` (also published by the broker if the robot disappears) |
| `status` | `idle`, `running`, `paused`, `fault`, `disconnected` |
| `program` | name of the running program, or `none` |
| `temperature` | bowl temperature in °C (the Smart reports 0 below 37 °C) |
| `target_temperature` | requested temperature in °C, 0 when idle |
| `speed` | current speed, 0 when idle |
| `weight` | weight on the scale, in grams |
| `remaining` | time remaining, in seconds |
| `lid` | `ON` lid open, `OFF` closed |
| `fault` | current alert message, or `none` |

Values are sent at most once per second, only when they change. Sending runs while the app is
open on the robot.

## Example automation

A notification when cooking is done:

```yaml
automation:
  - alias: Camybrith, cooking done
    trigger:
      - platform: state
        entity_id: sensor.camybrith_camybrith_status
        from: running
        to: idle
    action:
      - service: notify.notify
        data:
          message: "The Monsieur Cuisine is done."
```
