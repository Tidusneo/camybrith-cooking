# Feature Specification: Notifications (téléphone et enceintes de la maison)

**Feature Branch**: `005-notifications`

**Created**: 2026-11-27

**Status**: Draft

**Input**: User description: "Rajoute l'idée de notification" : être prévenu sur le téléphone ou
par les enceintes de la maison quand la cuisson est finie, quand une action est attendue, ou en cas
d'alerte.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Être prévenu quand le robot a besoin de moi (Priority: P1)

L'utilisateur lance une soupe et quitte la cuisine. Il reçoit sur son téléphone : « Soupe de
carottes : terminé », ou « Étape 3 : ajoute la crème », ou « Alerte : couvercle mal fermé ». Toucher
la notification ouvre l'appli web sur l'état du robot.

**Why this priority**: c'est le besoin principal : ne plus rester devant le robot.

**Independent Test**: lancer une cuisson de 2 minutes, quitter la pièce, recevoir la notification de
fin sur le téléphone en moins de 10 secondes.

**Acceptance Scenarios**:

1. **Given** une cuisson se termine, **When** l'événement survient, **Then** une notification
   « terminé » arrive sur les téléphones choisis en moins de 10 secondes.
2. **Given** une recette guidée attend une action (ajouter un ingrédient, peser), **When** le
   robot s'arrête en attente, **Then** une notification décrit l'action attendue.
3. **Given** une alerte survient (couvercle, surchauffe, erreur de la base), **When** elle
   apparaît sur le robot, **Then** une notification marquée urgente est envoyée.
4. **Given** l'utilisateur touche une notification, **When** l'appli web s'ouvre, **Then** elle
   montre l'état actuel du robot.

---

### User Story 2 - Annonce vocale sur les enceintes de la maison (Priority: P2)

Quand la cuisson est terminée, l'enceinte du salon annonce « La soupe est prête ». La nuit (plage
réglable), les annonces vocales sont coupées et seules les notifications silencieuses restent.

**Why this priority**: confortable quand le téléphone n'est pas à portée, mais secondaire.

**Independent Test**: choisir une enceinte, lancer une cuisson courte, entendre l'annonce.

**Acceptance Scenarios**:

1. **Given** une enceinte est choisie pour les annonces, **When** la cuisson se termine,
   **Then** l'annonce est jouée sur cette enceinte.
2. **Given** l'heure est dans la plage « ne pas déranger », **When** un événement non urgent
   survient, **Then** aucune annonce vocale n'est jouée.
3. **Given** l'heure est dans la plage « ne pas déranger », **When** une alerte urgente survient,
   **Then** l'annonce est quand même jouée.

---

### User Story 3 - Choisir quoi recevoir et où (Priority: P2)

Dans les Réglages, l'utilisateur choisit pour chaque type d'événement (fin de cuisson, action
attendue, alerte, poids atteint, minuteur, rappel du planning) les canaux : téléphone(s), enceinte,
rien.

**Why this priority**: évite les notifications envahissantes ; nécessaire pour un usage durable.

**Independent Test**: désactiver « poids atteint », peser jusqu'au poids cible, vérifier qu'aucune
notification n'arrive.

**Acceptance Scenarios**:

1. **Given** « poids atteint » est désactivé, **When** le poids cible est atteint, **Then** rien
   n'est envoyé.
2. **Given** un bouton « Envoyer une notification d'essai », **When** l'utilisateur l'utilise,
   **Then** chaque canal choisi reçoit un message d'essai.

---

### Edge Cases

- Serveur domotique ou réseau indisponible : les notifications sont perdues sans gêner la cuisine ;
  le robot l'indique dans les Réglages (« dernier envoi échoué à 18 h 02 »).
- Plusieurs événements en rafale (alerte qui se répète) : regroupés, au plus 1 notification par
  type toutes les 30 secondes.
- L'utilisateur est devant le robot (écran touché dans la dernière minute) : les notifications non
  urgentes sont retenues.
- Robot éteint : aucune notification ; « robot hors ligne » est signalé une seule fois si
  l'utilisateur l'a demandé.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Le système MUST émettre une notification pour : fin de cuisson, action attendue dans
  une recette guidée, alerte de cuisson, poids cible atteint, fin de minuteur, rappel du planning
  (spec 004).
- **FR-002**: Le système MUST pouvoir notifier les téléphones de la maison et annoncer à voix haute
  sur les enceintes de la maison, par l'intermédiaire du serveur domotique de l'utilisateur.
- **FR-003**: Chaque type d'événement MUST avoir son réglage de canaux (téléphone, enceinte, aucun).
- **FR-004**: Les alertes MUST être marquées urgentes et MUST ignorer la plage « ne pas déranger ».
- **FR-005**: Le système MUST regrouper les événements répétés (au plus une notification par type
  toutes les 30 secondes) et MUST retenir les notifications non urgentes quand l'utilisateur
  utilise le robot.
- **FR-006**: Une notification MUST permettre d'ouvrir l'état du robot ; elle MUST NOT proposer
  d'action qui démarre ou reprenne une cuisson (constitution, principe I).
- **FR-007**: L'échec d'un envoi MUST NOT gêner la cuisine et MUST être visible dans les Réglages.
- **FR-008**: Les notifications MUST être désactivées par défaut et MUST être rédigées dans la
  langue du robot (français, anglais, espagnol).
- **FR-009**: Le système MUST proposer une notification d'essai par canal.

### Key Entities

- **Événement** : type, urgence, texte, recette et étape concernées, heure.
- **Règle de notification** : type d'événement, canaux, actif ou non.
- **Plage « ne pas déranger »** : heure de début, heure de fin.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 95 % des notifications de fin de cuisson arrivent sur le téléphone en moins de
  10 secondes.
- **SC-002**: 100 % des alertes déclenchent une notification, y compris pendant la plage « ne pas
  déranger ».
- **SC-003**: Une alerte répétée 10 fois en 1 minute produit au plus 2 notifications.
- **SC-004**: La configuration des notifications prend moins de 5 minutes.

## Assumptions

- Les notifications passent par Home Assistant (appli compagnon sur le téléphone, enceintes
  connectées à Home Assistant), qui reçoit déjà l'état du robot. Sans Home Assistant, cette
  fonctionnalité n'est pas disponible dans cette version.
- Une notification poussée directement par le relais (spec 003), sans Home Assistant, est hors
  périmètre de cette version.
- Le robot ne fait que signaler des événements : la façon exacte dont Home Assistant les diffuse
  peut être fournie sous forme d'automatisation prête à importer.
