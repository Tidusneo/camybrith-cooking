# Feature Specification: Planning des repas de la semaine

**Feature Branch**: `004-planning-semaine`

**Created**: 2026-11-27

**Status**: Draft

**Input**: User description: "Rajoute l'idée de planning de la semaine" : placer des recettes sur
les jours de la semaine ; la liste « à cuisiner » et la liste de courses se remplissent seules ; le
robot propose la recette du jour.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Planifier sa semaine depuis le téléphone (Priority: P1)

Le dimanche, l'utilisateur ouvre le planning sur son téléphone : 7 jours, midi et soir. Il place
des recettes sur les repas voulus (par recherche ou depuis sa liste « à cuisiner »), choisit le
nombre de portions, et peut noter un repas sans recette (« restes », « resto »).

**Why this priority**: c'est le cœur de la fonctionnalité ; tout le reste en découle.

**Independent Test**: remplir 5 repas, fermer l'appli, la rouvrir sur un autre téléphone associé
et retrouver le même planning.

**Acceptance Scenarios**:

1. **Given** le planning de la semaine est vide, **When** l'utilisateur place une recette sur
   mardi soir pour 4, **Then** elle apparaît sur ce créneau avec sa photo et ses portions.
2. **Given** une recette est sur mardi soir, **When** l'utilisateur la fait glisser sur jeudi
   midi, **Then** elle change de créneau sans rien perdre.
3. **Given** un créneau, **When** l'utilisateur y écrit « restes », **Then** le créneau affiche
   cette note et n'ajoute rien aux courses.
4. **Given** une semaine passée, **When** l'utilisateur la consulte, **Then** il retrouve ce qui
   était prévu et ce qui a réellement été cuisiné.

---

### User Story 2 - Faire ses courses à partir du planning (Priority: P1)

Une fois la semaine planifiée, l'utilisateur génère la liste de courses de la semaine : elle reprend
toutes les recettes planifiées, avec leurs portions, suivant les règles de la liste de courses de
la spec 002 (regroupement, décochage, envoi dans Notion).

**Why this priority**: c'est le principal bénéfice concret du planning.

**Independent Test**: planifier 4 recettes, générer la liste, vérifier que chaque ingrédient y est
avec la bonne quantité totale.

**Acceptance Scenarios**:

1. **Given** 4 recettes planifiées, **When** l'utilisateur génère la liste de la semaine,
   **Then** elle contient les ingrédients des 4 recettes, quantités ajustées aux portions.
2. **Given** l'utilisateur ne veut faire les courses que jusqu'à mercredi, **When** il choisit
   cette plage, **Then** seuls les repas de la plage sont pris en compte.
3. **Given** la liste a été envoyée, **When** l'utilisateur ajoute une recette au planning,
   **Then** il peut envoyer uniquement les ingrédients manquants en complément.

---

### User Story 3 - Le robot propose le repas du jour (Priority: P2)

Le soir, l'écran du robot (accueil cuisine ou écran Maison) affiche « Ce soir : curry de pois
chiches, 4 portions ». Un appui ouvre la recette en mode guidé ; rien ne démarre seul.

**Why this priority**: c'est ce qui rend le planning présent au quotidien, mais il peut fonctionner
sans.

**Independent Test**: planifier un repas pour le créneau en cours, vérifier qu'il s'affiche sur le
robot et s'ouvre en un appui.

**Acceptance Scenarios**:

1. **Given** une recette est planifiée ce soir, **When** l'utilisateur regarde l'accueil après
   16 h, **Then** le repas du soir est mis en avant.
2. **Given** la recette du jour a été cuisinée jusqu'au bout, **When** la cuisson se termine,
   **Then** le créneau est marqué « cuisiné ».
3. **Given** aucun repas n'est planifié, **When** l'utilisateur regarde l'accueil, **Then** rien
   de particulier ne s'affiche.

---

### User Story 4 - Rappels de préparation (Priority: P3)

Pour une recette qui demande d'anticiper (décongeler, faire tremper, mariner), le robot rappelle la
veille ou le matin : « Sors le poulet du congélateur pour ce soir ».

**Why this priority**: confort ; dépend des notifications (spec 005) et de l'information
d'anticipation dans les recettes.

**Independent Test**: planifier une recette marquée « trempage la veille », vérifier le rappel.

**Acceptance Scenarios**:

1. **Given** une recette planifiée mercredi demande un trempage la veille, **When** mardi
   18 h arrive, **Then** un rappel est envoyé par les canaux de notification choisis.
2. **Given** l'utilisateur ajoute lui-même un rappel à un repas, **When** l'heure arrive,
   **Then** le rappel est envoyé.

---

### Edge Cases

- Recette planifiée qui disparaît lors d'une synchro Lidl : le créneau la garde, marquée « plus
  disponible ».
- Même recette deux fois dans la semaine : ses ingrédients sont comptés deux fois.
- Changement d'heure ou de fuseau : les créneaux restent attachés au jour local.
- Planning modifié depuis deux téléphones en même temps : la dernière modification d'un créneau
  gagne, sans effacer les autres créneaux.
- Aucun téléphone : le planning se consulte et se modifie aussi sur le robot.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Le système MUST offrir un planning hebdomadaire de 7 jours avec 2 créneaux par jour
  (midi, soir) ; l'ajout d'autres créneaux (petit-déjeuner, goûter) MAY être activé dans les
  réglages.
- **FR-002**: L'utilisateur MUST pouvoir placer, déplacer, retirer une recette ou une note libre
  sur un créneau, et choisir les portions, depuis le téléphone et depuis le robot.
- **FR-003**: Le planning MUST être le même sur le robot et tous les téléphones associés.
- **FR-004**: Le système MUST générer une liste de courses à partir d'une plage de jours du
  planning, avec les règles de la liste de courses existante (spec 002), y compris l'envoi dans
  Notion.
- **FR-005**: Le système MUST permettre d'envoyer un complément de liste ne contenant que ce qui a
  été ajouté depuis le dernier envoi.
- **FR-006**: Le robot MUST mettre en avant le repas du créneau en cours (midi jusqu'à 15 h, soir
  ensuite) ; l'ouvrir MUST NOT démarrer de cuisson.
- **FR-007**: Une recette cuisinée jusqu'au bout MUST marquer son créneau « cuisiné » ; l'historique
  des semaines passées MUST rester consultable au moins 12 semaines.
- **FR-008**: Le système MUST envoyer les rappels de préparation (anticipation notée dans la recette
  ou ajoutée par l'utilisateur) via les notifications de la spec 005.
- **FR-009**: Le planning MUST être conservé au redémarrage, aux mises à jour et dans la sauvegarde
  manuelle (spec 007).
- **FR-010**: Le planning et ses messages MUST être disponibles en français, anglais et espagnol.

### Key Entities

- **Semaine** : date de début, créneaux.
- **Créneau** : jour, repas (midi, soir…), contenu (recette + portions, ou note libre), état
  (prévu, cuisiné), rappels.
- **Rappel de préparation** : créneau, moment (veille, matin, heure précise), texte.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Planifier 7 repas pour la semaine prend moins de 5 minutes depuis le téléphone.
- **SC-002**: La liste de courses d'une semaine de 7 repas est générée en moins de 10 secondes, avec
  100 % des ingrédients des recettes planifiées.
- **SC-003**: Le repas du jour s'ouvre en 1 appui depuis l'accueil du robot.
- **SC-004**: 100 % des rappels de préparation programmés sont envoyés à l'heure prévue
  (à 1 minute près) quand le robot est allumé.

## Assumptions

- Dépend de la spec 002 (appli web, liste « à cuisiner », liste de courses, Notion) ; le planning
  en est une extension.
- La semaine commence le lundi ; ce choix est réglable.
- L'information « à anticiper » n'existe pas toujours dans les recettes Lidl ; l'utilisateur peut
  l'ajouter lui-même.
- Les rappels ne sont envoyés que si le robot est allumé (ou si le relais de la spec 003 les
  relaie) ; un robot éteint ne garantit pas les rappels.
- La proposition automatique de menus (« planifie-moi une semaine équilibrée ») relève de
  l'assistant IA (spec 003), via des actions de planning qui pourront lui être ajoutées.
