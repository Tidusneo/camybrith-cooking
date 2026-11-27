# Feature Specification: Écran Maison (domotique) avec accès à la cuisine

**Feature Branch**: `001-ecran-maison-domotique`

**Created**: 2026-11-27

**Status**: Draft

**Input**: User description: "Une interface domotique par défaut sur le robot, pour piloter ma maison,
et dans cette interface pouvoir lancer la partie cuisine." (fonctionnalité proposée dans la
conversation, la commande ayant été lancée sans description)

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Piloter la maison depuis le robot (Priority: P1)

En passant dans la cuisine, l'utilisateur regarde l'écran du robot : il affiche l'écran **Maison**,
avec des tuiles pour ses lumières, ses volets et ses scènes. Il touche une tuile (ou la sélectionne à
la molette) pour allumer la lumière de la cuisine, baisser un volet ou lancer la scène « Dîner ».
L'état de chaque tuile se met à jour.

**Why this priority**: c'est la raison d'être de la fonctionnalité : faire du robot, posé en
permanence dans la cuisine, un panneau de commande de la maison.

**Independent Test**: avec un serveur domotique configuré et 4 tuiles choisies, allumer puis
éteindre une lumière depuis le robot, et vérifier que la lumière réelle change et que la tuile
reflète son état.

**Acceptance Scenarios**:

1. **Given** le serveur domotique est configuré et une lumière est éteinte, **When** l'utilisateur
   touche sa tuile, **Then** la lumière s'allume et la tuile affiche « allumée » en moins de
   2 secondes.
2. **Given** une lumière est allumée depuis un autre appareil de la maison, **When** l'écran Maison
   est affiché, **Then** la tuile passe à « allumée » sans action de l'utilisateur.
3. **Given** une tuile de volet est sélectionnée, **When** l'utilisateur tourne la molette,
   **Then** la position demandée du volet change par paliers et est envoyée quand il s'arrête.
4. **Given** une tuile de scène, **When** l'utilisateur la touche, **Then** la scène est lancée et
   un retour visuel bref le confirme.

---

### User Story 2 - Passer à la cuisine en un geste (Priority: P1)

Depuis l'écran Maison, l'utilisateur touche la grande tuile **Cuisine** et arrive sur l'accueil
cuisine actuel (recette en cours, cuisson manuelle, balance, recettes). Un bouton ou un geste
simple le ramène à l'écran Maison.

**Why this priority**: l'écran Maison ne doit jamais rendre la cuisine plus difficile d'accès ;
sans cela, la fonctionnalité dégrade l'usage principal du robot.

**Independent Test**: depuis l'écran Maison, atteindre la cuisine, lancer une pesée, revenir à
Maison, sans configuration domotique particulière.

**Acceptance Scenarios**:

1. **Given** l'écran Maison est affiché, **When** l'utilisateur touche la tuile Cuisine,
   **Then** l'accueil cuisine s'affiche en moins d'une seconde.
2. **Given** l'utilisateur est sur l'accueil cuisine, **When** il utilise le retour vers Maison,
   **Then** l'écran Maison s'affiche avec l'état à jour des tuiles.
3. **Given** l'écran Maison est l'écran d'accueil, **When** le robot démarre, **Then** l'écran
   Maison s'affiche en premier.

---

### User Story 3 - Ne jamais perdre de vue la cuisson (Priority: P1)

Une cuisson est en cours. L'utilisateur revient sur l'écran Maison pour éteindre une lumière. Un
bandeau permanent montre le programme, le temps restant et la température ; le toucher ramène à
l'écran de cuisson. Si une alerte survient (couvercle ouvert, fin de cuisson, erreur), elle
s'affiche par-dessus l'écran Maison.

**Why this priority**: exigence de sécurité (constitution, principe I) : aucun écran ne doit
masquer une alerte de cuisson.

**Independent Test**: lancer une cuisson courte, aller sur Maison, ouvrir le couvercle, vérifier
que l'alerte apparaît par-dessus et que la cuisson est en pause.

**Acceptance Scenarios**:

1. **Given** une cuisson est en cours, **When** l'utilisateur affiche l'écran Maison, **Then** un
   bandeau montre le programme, le temps restant et la température, mis à jour chaque seconde.
2. **Given** l'écran Maison est affiché pendant une cuisson, **When** une alerte de cuisson
   survient, **Then** l'alerte s'affiche immédiatement au premier plan avec son son habituel.
3. **Given** l'écran Maison est affiché pendant une cuisson, **When** l'utilisateur touche le
   bandeau, **Then** l'écran de cuisson s'affiche.

---

### User Story 4 - Choisir et configurer ses tuiles (Priority: P2)

Dans les Réglages, l'utilisateur renseigne l'adresse de son serveur domotique et son jeton d'accès,
puis choisit dans la liste des équipements disponibles ceux à afficher, leur ordre et leur nom
court. Il peut aussi choisir si le robot démarre sur l'écran Maison ou sur l'accueil cuisine.

**Why this priority**: indispensable pour un usage réel, mais une première version peut être
validée avec une configuration minimale.

**Independent Test**: configurer le serveur, choisir 6 équipements, réordonner, redémarrer le robot
et vérifier que les tuiles et l'ordre sont conservés.

**Acceptance Scenarios**:

1. **Given** une adresse et un jeton valides, **When** l'utilisateur valide, **Then** le réglage
   affiche « Connecté » et la liste des équipements pris en charge.
2. **Given** un jeton invalide, **When** l'utilisateur valide, **Then** un message clair indique
   que l'accès est refusé, sans afficher le jeton.
3. **Given** l'utilisateur choisit « Démarrer sur l'accueil cuisine », **When** le robot redémarre,
   **Then** l'accueil cuisine s'affiche en premier et l'écran Maison reste accessible.

---

### User Story 5 - Ouvrir l'appli Monsieur Cuisine d'origine (Priority: P2)

Pour une recette ou une fonction qu'il ne trouve que dans l'appli Lidl, l'utilisateur touche la
tuile **Appli d'origine** sur l'écran Maison. Après confirmation, Camybrith rend la main à la
machine et l'appli Monsieur Cuisine d'origine s'ouvre. Plus tard, il revient à Camybrith (bouton
Accueil ou redémarrage du robot) et retrouve l'écran Maison.

**Why this priority**: filet de sécurité utile (principe VI, réversibilité), mais pas nécessaire
au quotidien.

**Independent Test**: robot au repos, toucher la tuile, confirmer, lancer une pesée dans l'appli
d'origine, revenir à Camybrith et vérifier que l'écran Maison s'affiche.

**Acceptance Scenarios**:

1. **Given** aucune cuisson n'est en cours, **When** l'utilisateur touche la tuile Appli d'origine
   et confirme, **Then** Camybrith libère la machine et l'appli d'origine s'ouvre en moins de
   5 secondes.
2. **Given** une cuisson est en cours, **When** l'utilisateur touche la tuile, **Then** un message
   indique qu'il faut d'abord arrêter la cuisson, et rien d'autre ne se passe.
3. **Given** l'appli d'origine est ouverte, **When** l'utilisateur revient à Camybrith,
   **Then** Camybrith ne reprend la machine que lorsque l'appli d'origine ne l'utilise plus, et
   l'indique à l'écran tant que ce n'est pas le cas.

---

### Edge Cases

- Serveur domotique injoignable (Wi-Fi coupé, serveur arrêté) : l'écran Maison affiche les tuiles
  grisées avec « Maison hors ligne », la tuile Cuisine reste utilisable, et la reconnexion est
  automatique.
- Domotique jamais configurée : le robot démarre sur l'accueil cuisine ; l'écran Maison propose
  seulement de le configurer.
- Commande refusée ou sans effet (équipement indisponible) : la tuile revient à son état réel et
  un court message l'indique.
- Équipement supprimé ou renommé côté domotique : sa tuile est marquée « introuvable » au lieu de
  disparaître silencieusement.
- Écran en veille : un appui ou un mouvement de molette réveille sur l'écran Maison sans déclencher
  de tuile.
- Plus de tuiles que l'écran n'en contient : défilement à la molette ou au doigt.
- Version Connect du robot : même comportement, la fonctionnalité ne dépend pas du modèle.
- Appli d'origine absente ou désactivée : la tuile Appli d'origine est grisée avec une explication.
- Robot redémarré pendant que l'appli d'origine était ouverte : Camybrith redémarre sur l'écran
  choisi et reprend la machine selon son réglage habituel.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Le système MUST offrir un écran Maison affichant des tuiles d'équipements domotiques
  et une tuile Cuisine toujours présente et visible sans défilement.
- **FR-002**: Le système MUST permettre de choisir l'écran de démarrage (Maison ou accueil
  cuisine) ; la valeur par défaut est Maison une fois la domotique configurée, cuisine sinon.
- **FR-003**: Le système MUST prendre en charge, dans cette version : lumières (marche/arrêt,
  intensité si disponible), interrupteurs et prises, volets (ouvrir, fermer, position), scènes et
  scripts (lancer), capteurs en lecture seule (température, humidité, ouverture).
- **FR-004**: Le système MUST NOT proposer dans cette version les serrures, alarmes, portes de
  garage ni aucun équipement de sécurité de la maison.
- **FR-005**: Chaque tuile MUST refléter l'état réel de l'équipement, y compris les changements
  faits depuis un autre appareil, en moins de 3 secondes.
- **FR-006**: Les tuiles MUST être utilisables au toucher et à la molette (sélection, action,
  réglage d'une valeur).
- **FR-007**: Pendant une cuisson, l'écran Maison MUST afficher un bandeau d'état de cuisson
  (programme, temps restant, température) qui ramène à l'écran de cuisson.
- **FR-008**: Toute alerte de cuisson MUST s'afficher au premier plan, par-dessus l'écran Maison,
  avec son signal sonore habituel.
- **FR-009**: Aucune action de l'écran Maison ni aucun message reçu du serveur domotique MUST NOT
  pouvoir démarrer, reprendre ou modifier une cuisson (moteur, chauffe).
- **FR-010**: Un problème de domotique (serveur injoignable, erreur, lenteur) MUST NOT bloquer,
  ralentir ni interrompre la cuisine ou une cuisson en cours.
- **FR-011**: L'utilisateur MUST pouvoir configurer l'adresse du serveur et le jeton d'accès ; le
  jeton MUST être stocké uniquement sur le robot et MUST NOT apparaître à l'écran après saisie, ni
  dans les journaux.
- **FR-012**: L'utilisateur MUST pouvoir choisir les équipements affichés, leur ordre et un nom
  court par tuile ; ces choix MUST survivre au redémarrage et aux mises à jour de l'appli.
- **FR-013**: L'écran Maison et ses réglages MUST être traduits en français, anglais et espagnol.
- **FR-014**: L'utilisateur MUST pouvoir désactiver complètement l'écran Maison ; le robot se
  comporte alors exactement comme avant la fonctionnalité.
- **FR-015**: L'écran Maison MUST proposer une tuile Appli d'origine qui, après confirmation,
  libère la machine et ouvre l'appli Monsieur Cuisine d'origine.
- **FR-016**: La tuile Appli d'origine MUST être refusée pendant une cuisson ou une pause de
  cuisson, avec un message expliquant pourquoi.
- **FR-017**: Camybrith et l'appli d'origine MUST NOT commander la machine en même temps ; au
  retour dans Camybrith, la reprise de la machine MUST être explicite et visible.

### Key Entities

- **Connexion domotique** : adresse du serveur, jeton d'accès (secret), état (non configurée,
  connectée, hors ligne, accès refusé).
- **Tuile** : équipement associé, type (lumière, interrupteur, volet, scène/script, capteur), nom
  court, position, état courant.
- **Préférence d'accueil** : écran de démarrage choisi (Maison ou cuisine), écran Maison activé ou
  non.
- **Propriétaire de la machine** : qui commande la base à un instant donné (Camybrith ou appli
  d'origine), jamais les deux.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Allumer ou éteindre une lumière depuis le robot prend au plus 2 gestes et le
  résultat est visible en moins de 2 secondes dans 95 % des cas sur le Wi-Fi de la maison.
- **SC-002**: Depuis l'écran Maison, l'accueil cuisine s'atteint en 1 geste et s'affiche en moins
  d'une seconde.
- **SC-003**: Dans 100 % des essais, une alerte de cuisson déclenchée pendant que l'écran Maison
  est affiché apparaît au premier plan en moins d'une seconde.
- **SC-004**: Avec 12 tuiles affichées, le défilement et la molette restent fluides sur la tablette
  du robot (aucune saccade perceptible lors d'un essai de 2 minutes).
- **SC-005**: Avec le serveur domotique coupé, une cuisson manuelle de 5 minutes se lance et se
  termine normalement, sans message bloquant.
- **SC-006**: Un nouvel utilisateur configure le serveur et ses 6 premières tuiles en moins de
  10 minutes, sans aide.

## Assumptions

- Le serveur domotique est **Home Assistant**, accessible sur le réseau local ; l'utilisateur sait
  créer un jeton d'accès longue durée. Les autres systèmes domotiques sont hors périmètre.
- La configuration MQTT existante (envoi de l'état du robot) reste indépendante et en lecture
  seule ; l'écran Maison est un sens « robot → maison » distinct.
- L'écran Maison n'affiche pas le tableau de bord web de Home Assistant mais des tuiles natives,
  conformément au principe V (sobriété sur la tablette).
- La configuration se fait sur le robot ; une configuration depuis le téléphone ou l'ordinateur est
  hors périmètre de cette version.
- Caméras, lecteurs multimédia, climatisation et thermostats sont hors périmètre de cette version.
- La tuile Appli d'origine réutilise le passage de relais existant (« Revenir à l'appli
  d'origine » dans Réglages › Machine) ; l'appli d'origine n'est jamais modifiée.
- Le robot est rooté et Camybrith Cooking installé ; la fonctionnalité sera validée sur le vrai
  robot (Android 8.1) avant publication.
