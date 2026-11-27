# Feature Specification: Écran de veille utile

**Feature Branch**: `006-ecran-veille-utile`

**Created**: 2026-11-27

**Status**: Draft

**Input**: User description: "Rajoute l'idée d'écran de veille utile" : au lieu d'un écran noir,
afficher l'heure, la météo, l'agenda familial ou des photos quand le robot ne sert pas.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Horloge et météo quand le robot ne sert pas (Priority: P1)

Après quelques minutes sans utilisation, l'écran du robot passe en veille utile : grande horloge,
date, météo du jour (température, ciel, pluie). Toucher l'écran ou tourner la molette revient à
l'écran précédent, sans rien déclencher.

**Why this priority**: c'est l'usage le plus fréquent et le plus simple ; il donne une raison de
regarder le robot chaque jour.

**Independent Test**: attendre le délai, vérifier l'horloge et la météo, toucher l'écran et
vérifier qu'on revient à l'écran précédent sans action.

**Acceptance Scenarios**:

1. **Given** le robot est au repos depuis le délai choisi, **When** le délai expire, **Then**
   l'écran de veille utile s'affiche avec l'heure exacte et la date.
2. **Given** la météo est disponible, **When** l'écran de veille s'affiche, **Then** il montre la
   température actuelle, le ciel et le risque de pluie du jour.
3. **Given** l'écran de veille est affiché, **When** l'utilisateur touche l'écran ou tourne la
   molette, **Then** l'écran précédent revient et ce geste ne déclenche aucune action.

---

### User Story 2 - Agenda familial et repas du jour (Priority: P2)

L'écran de veille montre aussi les prochains événements de l'agenda familial (« 17 h : piscine
Léo ») et le repas prévu ce soir (spec 004).

**Why this priority**: forte valeur au quotidien, mais dépend de sources externes.

**Independent Test**: ajouter un événement dans l'agenda choisi, le voir apparaître sur la veille.

**Acceptance Scenarios**:

1. **Given** un agenda est choisi, **When** l'écran de veille s'affiche, **Then** les 3 prochains
   événements du jour et du lendemain sont visibles.
2. **Given** un repas est planifié ce soir, **When** l'écran de veille s'affiche, **Then** il est
   indiqué.

---

### User Story 3 - Cadre photo (Priority: P3)

L'utilisateur choisit un album ; l'écran de veille fait défiler les photos, avec l'heure en
surimpression.

**Why this priority**: agréable mais non essentiel.

**Independent Test**: choisir un album, vérifier le défilement et l'heure en surimpression.

**Acceptance Scenarios**:

1. **Given** un album est choisi, **When** l'écran de veille s'affiche, **Then** une nouvelle photo
   s'affiche toutes les 30 secondes (réglable), avec l'heure.

---

### User Story 4 - Nuit et économie de l'écran (Priority: P2)

La nuit (plage réglable), l'écran de veille baisse fortement la luminosité ou s'éteint
complètement, au choix ; les éléments se déplacent légèrement pour ne pas marquer l'écran.

**Why this priority**: évite la lumière gênante la nuit et préserve la tablette.

**Independent Test**: régler une plage de nuit qui commence dans 2 minutes, vérifier la baisse de
luminosité.

**Acceptance Scenarios**:

1. **Given** l'heure entre dans la plage de nuit, **When** l'écran de veille est affiché,
   **Then** il applique le choix de nuit (sombre ou éteint).
2. **Given** l'utilisateur a choisi « écran éteint » comme veille, **When** le délai expire,
   **Then** le robot se comporte comme aujourd'hui (écran éteint).

---

### Edge Cases

- Cuisson en cours : pas d'écran de veille ; l'écran de cuisson reste affiché (ou un affichage
  simplifié très lisible du temps restant et de la température).
- Alerte : elle remplace immédiatement l'écran de veille, avec son son.
- Pesée en cours (poids qui change) : la veille ne se déclenche pas.
- Météo ou agenda indisponible : la zone correspondante est masquée, l'horloge reste.
- Heure du robot fausse : l'horloge se recale sur l'heure du réseau ; sans réseau, elle affiche
  l'heure interne.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Le système MUST proposer comme veille : écran éteint (comportement actuel), horloge
  seule, horloge + informations (météo, agenda, repas du jour), ou cadre photo.
- **FR-002**: Le délai avant la veille MUST reprendre le réglage existant de mise en veille de
  l'écran.
- **FR-003**: Le premier geste (toucher, molette) en veille MUST seulement réveiller l'écran,
  sans déclencher d'action.
- **FR-004**: La veille MUST NOT s'activer pendant une cuisson, une pesée ou une alerte ; une alerte
  MUST interrompre la veille immédiatement.
- **FR-005**: La météo, l'agenda et les photos MUST venir de sources choisies par l'utilisateur ;
  une source indisponible MUST être masquée sans message bloquant.
- **FR-006**: Une plage de nuit MUST permettre d'assombrir fortement ou d'éteindre l'écran.
- **FR-007**: Les éléments affichés en veille MUST se déplacer légèrement à intervalles réguliers
  pour éviter le marquage de l'écran.
- **FR-008**: L'écran de veille MUST respecter la langue et le thème (clair, sombre) du robot.
- **FR-009**: L'écran de veille MUST rester sobre pour la tablette : affichage fluide, sans
  ralentir le robot au réveil.

### Key Entities

- **Réglage de veille** : mode (éteint, horloge, informations, photos), plage de nuit, choix de
  nuit, album, agenda, source météo.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: L'heure affichée est juste à la minute près dans 100 % des vérifications avec un
  réseau disponible.
- **SC-002**: Le réveil depuis la veille prend moins d'une demi-seconde.
- **SC-003**: Une alerte remplace l'écran de veille en moins d'une seconde dans 100 % des essais.
- **SC-004**: Une veille de 8 heures ne provoque ni ralentissement ni plantage de l'appli.

## Assumptions

- La météo, l'agenda et les photos viennent de Home Assistant (entités météo et agenda, album
  média) pour rester sur le réseau local et réutiliser la connexion de l'écran Maison (spec 001).
- Sans Home Assistant, seuls l'horloge et le repas du jour sont disponibles.
- La luminosité de l'anneau de la molette en veille reste celle du réglage existant.
