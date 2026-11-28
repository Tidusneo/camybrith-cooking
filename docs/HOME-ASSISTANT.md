# Home Assistant (MQTT)

Camybrith Cooking peut envoyer l'état du robot à Home Assistant par MQTT. C'est **en lecture
seule** : Camybrith n'écoute aucun sujet, rien venant du réseau ne peut démarrer le moteur ou la
chauffe. Désactivé par défaut.

## Mise en route

1. Dans Home Assistant, installe un courtier MQTT (par exemple le module complémentaire
   *Mosquitto broker*) et l'intégration **MQTT**. Crée un utilisateur pour le robot.
2. Sur le robot : *Réglages › Home Assistant*. Active *Envoyer l'état en MQTT*, indique l'adresse
   du serveur (l'IP de Home Assistant), le port (1883), l'utilisateur et le mot de passe, puis
   *Enregistrer*. La ligne d'état doit afficher « Connecté à … ».
3. Dans Home Assistant, un appareil **Camybrith Cooking** apparaît tout seul (découverte MQTT),
   avec ses capteurs.

## Sujets

Tous sous `camybrith/` (modifiable dans les réglages), conservés par le courtier :

| Sujet | Valeur |
|---|---|
| `availability` | `online` ou `offline` (aussi publié par le courtier si le robot disparaît) |
| `status` | `idle`, `running`, `paused`, `fault`, `disconnected` |
| `program` | nom du programme en cours, ou `none` |
| `temperature` | température du bol en °C (le Smart renvoie 0 sous 37 °C) |
| `target_temperature` | température demandée en °C, 0 au repos |
| `speed` | vitesse en cours, 0 au repos |
| `weight` | poids sur la balance, en grammes |
| `remaining` | temps restant, en secondes |
| `lid` | `ON` couvercle ouvert, `OFF` fermé |
| `fault` | message de l'alerte en cours, ou `none` |

Les valeurs partent au plus une fois par seconde, seulement quand elles changent. L'envoi tourne
tant que l'appli est ouverte sur le robot.

## Exemple d'automatisation

Une notification quand la cuisson est terminée :

```yaml
automation:
  - alias: Camybrith, cuisson terminée
    trigger:
      - platform: state
        entity_id: sensor.camybrith_camybrith_status
        from: running
        to: idle
    action:
      - service: notify.notify
        data:
          message: "Le Monsieur Cuisine a terminé."
```
