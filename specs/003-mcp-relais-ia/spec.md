# Feature Specification: Parler au robot avec Claude ou Gemini (serveur MCP et relais sécurisé)

**Feature Branch**: `003-mcp-relais-ia`

**Created**: 2026-11-27

**Status**: Draft

**Input**: User description: "Je voudrais avoir une commande vocale pour demander des choses à mon
robot, branché à Claude." Précisé ensuite : le robot n'a pas de micro ; passer par un serveur MCP,
avec un petit relais installé sur un serveur personnel, écrit en Go et sécurisé ; pouvoir parler
avec Claude ou Gemini, pas uniquement Claude.

## Clarifications

### Session 2026-11-27

- Q: Le robot a-t-il un micro ? → A: Non documenté ; la commande vocale d'origine passe par Google
  Assistant. La voix est donc confiée à l'appli de l'assistant (téléphone ou ordinateur), le robot
  fournissant des outils à l'assistant.
- Q: Quels assistants ? → A: Claude et Gemini ; plus généralement tout assistant compatible avec
  le protocole standard d'outils (MCP), sans fonctionnement propre à un fournisseur.
- Q: Comment rendre le robot accessible à l'assistant depuis le téléphone ? → A: Par un relais installé
  sur un serveur appartenant à l'utilisateur ; le robot se connecte au relais, jamais l'inverse.

## User Scenarios & Testing *(mandatory)*

Dans cette spec, « l'assistant » désigne Claude ou Gemini, au choix de l'utilisateur.

### User Story 1 - Demander à son assistant depuis le Mac, à la maison (Priority: P1)

Dans Claude ou Gemini sur son Mac, l'utilisateur écrit ou dit : « Qu'est-ce que le robot est en train de
faire ? », « Trouve-moi trois recettes avec des poireaux », « Ajoute le gratin à ma liste à cuisiner
pour 6 ». L'assistant interroge le robot et répond ; le robot reflète les changements (liste mise à jour,
recette ouverte).

**Why this priority**: c'est la version la plus simple et la plus sûre (rien n'est exposé hors de
la maison) ; elle valide les outils donnés à l'assistant avant tout accès à distance.

**Independent Test**: depuis Claude puis depuis Gemini sur le Mac, demander l'état du robot pendant
une pesée, puis ajouter une recette à la liste et la voir apparaître sur le robot.

**Acceptance Scenarios**:

1. **Given** le robot pèse 180 g, **When** l'utilisateur demande « combien il y a dans le bol ? »,
   **Then** l'assistant répond « environ 180 g ».
2. **Given** l'utilisateur demande trois recettes aux poireaux, **When** l'assistant cherche,
   **Then** il propose des recettes réellement présentes sur le robot, avec leur nom exact.
3. **Given** l'utilisateur demande d'ajouter une recette pour 6, **When** l'assistant agit,
   **Then** elle apparaît dans la liste « à cuisiner » du robot avec 6 portions.

---

### User Story 2 - Préparer une cuisson par l'assistant, sans jamais la démarrer (Priority: P1)

L'utilisateur demande : « Prépare 20 minutes à 100 degrés vitesse 1 » ou « Ouvre la recette du
risotto à l'étape 2 ». Le robot affiche le réglage prêt avec « Appuie pour démarrer ». Si
l'utilisateur demande « lance-le », l'assistant répond qu'il faut appuyer sur le robot. « Mets en
pause » met en pause une cuisson en cours.

**Why this priority**: c'est ce qui rend l'intégration utile en cuisine, et c'est là que la règle
de sécurité n° 1 doit être prouvée.

**Independent Test**: demander à l'assistant de préparer une cuisson puis de la lancer de 10 façons
différentes ; vérifier que rien ne démarre et que la cuisson se lance à la main.

**Acceptance Scenarios**:

1. **Given** le robot est au repos, **When** l'assistant prépare 20 min / 100 °C / vitesse 1,
   **Then** le robot affiche ce réglage à l'arrêt avec « Appuie pour démarrer ».
2. **Given** n'importe quelle demande de démarrage, **When** l'assistant essaie d'agir, **Then** aucune
   action disponible ne démarre ni ne reprend une cuisson, et l'assistant explique pourquoi.
3. **Given** une demande hors des limites de sécurité, **When** l'assistant essaie de la préparer,
   **Then** le robot la refuse et la raison est renvoyée à l'assistant.
4. **Given** une cuisson est en cours, **When** l'utilisateur demande une pause, **Then** la
   cuisson se met en pause en moins de 3 secondes.
5. **Given** une cuisson est en cours, **When** l'assistant essaie de préparer un autre réglage,
   **Then** le robot refuse tant que la cuisson n'est pas terminée ou arrêtée.

---

### User Story 3 - Parler au robot depuis l'iPhone, de n'importe où (Priority: P2)

Au supermarché ou dans le salon, l'utilisateur utilise l'appli Claude ou Gemini sur son iPhone, à l'écrit ou
à la voix : « Il reste combien de temps ? », « Qu'est-ce qu'il me faut pour la liste à cuisiner de
la semaine ? ». L'assistant passe par le relais installé sur le serveur de l'utilisateur.

**Why this priority**: c'est le confort recherché (la voix sur le téléphone), mais il demande le
relais et une sécurité sans faille.

**Independent Test**: avec le téléphone en 4G (hors du Wi-Fi), demander l'état du robot, puis
couper le relais et vérifier que l'assistant indique que le robot est injoignable.

**Acceptance Scenarios**:

1. **Given** le relais est en service et le robot y est connecté, **When** l'utilisateur interroge
   l'assistant depuis l'iPhone en 4G, **Then** il obtient l'état réel du robot.
2. **Given** le robot est éteint, **When** l'utilisateur interroge l'assistant, **Then** l'assistant indique
   que le robot est hors ligne depuis telle heure.
3. **Given** quelqu'un sans autorisation trouve l'adresse du relais, **When** il essaie de
   l'utiliser, **Then** il n'obtient aucune information et la tentative est journalisée.

---

### User Story 4 - Installer, associer et révoquer en sécurité (Priority: P2)

L'utilisateur installe le relais sur son serveur à partir d'un seul fichier et d'une
configuration courte. Sur le robot, il active l'accès à distance, saisit l'adresse du relais et
confirme le code affiché des deux côtés. Il ajoute ensuite le relais comme connecteur dans Claude
et/ou dans Gemini, et se connecte avec ses identifiants du relais. Depuis le robot ou le relais, il peut à tout moment tout couper ou révoquer un
accès.

**Why this priority**: indispensable avant tout usage à distance ; la révocation est une
condition de confiance.

**Independent Test**: associer le robot, connecter l'assistant, révoquer l'accès de l'assistant depuis le
relais, vérifier que l'assistant ne peut plus rien faire, puis désactiver l'accès à distance sur le
robot et vérifier qu'il ne se connecte plus au relais.

**Acceptance Scenarios**:

1. **Given** un relais neuf, **When** le robot s'y associe, **Then** les deux affichent le même
   code et l'association n'est faite qu'après confirmation sur le robot.
2. **Given** l'assistant est connecté au relais, **When** l'utilisateur révoque cet accès, **Then** la
   demande suivante de l'assistant est refusée.
3. **Given** l'utilisateur désactive l'accès à distance sur le robot, **When** le relais tente
   quoi que ce soit, **Then** le robot n'a plus aucune connexion avec lui.

---

### Edge Cases

- Connexion entre le robot et le relais coupée : le robot se reconnecte seul ; pendant la coupure,
  l'assistant reçoit « robot hors ligne » ; la cuisine n'est jamais affectée.
- Relais compromis ou malveillant : il ne peut rien obtenir de plus que les actions autorisées,
  car le robot applique lui-même toutes les limites (aucun démarrage, limites de sécurité).
- Contenu d'une recette contenant des instructions destinées à tromper l'assistant : les seules actions
  possibles restant sans danger, l'effet est limité à des lectures ou à des préparations visibles.
- Demandes en rafale (erreur ou abus) : limitées en nombre ; au-delà, refus temporaire.
- Deux clients en même temps (Mac et iPhone) : les actions sont traitées une à une ; la liste
  « à cuisiner » reste cohérente.
- Alerte de cuisson pendant un échange : l'alerte reste prioritaire sur l'écran du robot.
- Robot sous une version de Camybrith plus ancienne que le relais (ou l'inverse) : message clair
  d'incompatibilité au lieu d'un comportement imprévisible.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Le système MUST offrir à l'assistant des actions sur le robot : lire l'état, chercher et
  lire des recettes, gérer la liste « à cuisiner », générer la liste de courses, ouvrir une
  recette à une étape, préparer une cuisson ou une pesée, mettre en pause.
- **FR-002**: Les actions offertes à l'assistant MUST NOT démarrer, reprendre ou modifier une cuisson,
  seule la pause étant permise (constitution, principe I) ; tout démarrage MUST être confirmé par
  un appui sur le robot.
- **FR-003**: Le robot MUST appliquer lui-même ces interdictions et les limites de sécurité, quel
  que soit ce que demande le relais ou l'assistant.
- **FR-004**: Le robot MUST NOT accepter de connexion venant d'Internet ; c'est lui qui ouvre la
  connexion vers le relais, et seulement si l'accès à distance est activé.
- **FR-005**: Un assistant sur le Mac de la maison MUST pouvoir utiliser ces actions sans passer
  par Internet.
- **FR-006**: Un assistant sur le téléphone MUST pouvoir utiliser ces actions par le relais, depuis
  n'importe quel réseau.
- **FR-016**: Les actions MUST suivre le protocole standard d'outils pour assistants (MCP) et
  fonctionner à l'identique avec Claude et Gemini, sans comportement propre à un fournisseur ;
  chaque assistant connecté MUST être un accès client distinct, révocable séparément.
- **FR-017**: L'authentification du relais MUST suivre le standard d'autorisation attendu par les
  assistants compatibles, pour que Claude comme Gemini puissent s'y connecter.
- **FR-007**: Le relais MUST exiger une authentification de l'utilisateur avant toute action,
  chiffrer tous les échanges, et permettre de révoquer un accès immédiatement.
- **FR-008**: L'association entre le robot et le relais MUST passer par un code affiché et
  confirmé sur le robot ; elle MUST pouvoir être retirée depuis le robot et depuis le relais.
- **FR-009**: Le relais MUST limiter le nombre de demandes et journaliser les accès (qui, quand,
  quelle action, résultat) sans y écrire de secret ni de contenu inutile.
- **FR-010**: Le robot MUST afficher quand une action vient d'un assistant et lequel (par exemple « Préparé par
  Gemini ») et garder la liste des dernières actions reçues, consultable dans les Réglages.
- **FR-011**: L'accès à distance MUST être désactivé par défaut ; son indisponibilité MUST NOT
  gêner la cuisine ni les autres fonctions.
- **FR-012**: Les secrets (clés d'association, jetons) MUST rester sur le robot et sur le relais,
  et MUST NOT apparaître à l'écran après saisie, dans les journaux ou dans les réponses à l'assistant.
- **FR-013**: Les réponses fournies à l'assistant MUST respecter la langue du robot pour les noms de
  recettes et les messages (français, anglais, espagnol).
- **FR-014**: Le relais MUST s'installer à partir d'un seul fichier et d'une configuration courte,
  documentés en français et en anglais, et MUST pouvoir tourner sur un serveur domestique ou un
  serveur loué.
- **FR-015**: En cas de versions incompatibles entre le robot et le relais, les deux MUST refuser
  de travailler ensemble avec un message explicite.

### Key Entities

- **Action** : nom, description pour l'assistant, paramètres, niveau (lecture, préparation, pause).
- **Association robot–relais** : identité du robot, date, état (active, révoquée).
- **Accès client** : client autorisé (Claude, Gemini…), date d'autorisation, dernier usage, état.
- **Entrée de journal** : date, client, action, résultat (réussie, refusée et pourquoi).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Une question sur l'état du robot reçoit une réponse fondée sur l'état réel en moins
  de 5 secondes dans 90 % des cas, depuis le Mac comme depuis l'iPhone, avec Claude comme avec
  Gemini.
- **SC-002**: Pour chaque assistant (Claude et Gemini), sur 50 tentatives de démarrage (formulations
  variées, y compris des
  tentatives détournées), 0 démarre le moteur ou la chauffe.
- **SC-003**: Sur une série d'essais d'accès sans autorisation au relais, 100 % sont refusés sans
  révéler la moindre information sur le robot.
- **SC-004**: Après une révocation, 100 % des demandes suivantes du client révoqué sont refusées.
- **SC-005**: L'installation du relais et l'association avec le robot et l'assistant prennent moins de
  30 minutes pour quelqu'un qui sait administrer son serveur.
- **SC-006**: Relais coupé, une cuisson manuelle de 5 minutes se lance et se termine normalement
  sur le robot.

## Assumptions

- La voix est fournie par l'appli de l'assistant (mode vocal sur téléphone ou ordinateur) ; le robot n'a
  pas besoin de micro. Le bon fonctionnement du mode vocal avec les connecteurs est à vérifier.
- Les connecteurs personnalisés de Claude et de Gemini se connectent depuis les serveurs de leur
  fournisseur : le relais doit donc être joignable depuis Internet, avec un certificat valide. Le
  robot, lui, ne l'est jamais.
- L'utilisateur dispose d'un serveur personnel (chez lui ou loué) et d'un nom de domaine ou d'un
  moyen de publier une adresse sécurisée ; il sait l'administrer.
- Choix de l'utilisateur : le relais est écrit en Go et distribué comme un seul exécutable
  (contrainte de la constitution 1.1.0, à reprendre dans le plan).
- L'utilisateur a un compte Claude et/ou Gemini permettant d'ajouter un serveur d'outils
  personnalisé ; aucune clé d'assistant n'est stockée sur le robot ni sur le relais.
- Chez Gemini, l'ajout d'un serveur d'outils personnalisé dans l'appli grand public semble limité
  (selon les sources : compte personnel, langue anglaise, certains pays). Sur le Mac, les outils en
  ligne de commande des deux fournisseurs acceptent un serveur local. La disponibilité réelle pour
  l'utilisateur en France est à vérifier ; la spec ne dépend pas d'un fournisseur en particulier.
- La liste « à cuisiner » et la liste de courses sont celles de la spec 002 ; l'envoi dans Notion
  peut être fait par l'assistant grâce à son propre connecteur Notion.
- Le pilotage de la maison par l'assistant passe par Home Assistant directement, pas par le robot ;
  il est hors périmètre.
