# Feature Specification: Appli web sur le téléphone, liste « à cuisiner » et courses vers Notion

**Feature Branch**: `002-webapp-telephone-courses`

**Created**: 2026-11-27

**Status**: Draft

**Input**: User description: "Je voudrais pouvoir avec une webapp, accessible depuis mon téléphone,
voir ce qui se passe et potentiellement wishlist des recettes, ce qui me permettra de générer une
liste de courses mise directement dans un Notion."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Suivre le robot depuis le téléphone (Priority: P1)

Pendant qu'une soupe cuit, l'utilisateur est dans le salon. Il ouvre l'appli web sur son téléphone
et voit l'état du robot : programme ou recette en cours, étape, temps restant, température,
vitesse, poids sur la balance, couvercle, alerte éventuelle. L'écran se met à jour tout seul.

**Why this priority**: c'est le besoin « voir ce qui se passe » ; il est utile seul et sert de
base aux autres parcours.

**Independent Test**: lancer une cuisson manuelle de 3 minutes, suivre le temps restant et la
température sur le téléphone, ouvrir le couvercle et voir l'alerte apparaître.

**Acceptance Scenarios**:

1. **Given** le robot cuit, **When** l'utilisateur ouvre l'appli web, **Then** il voit le
   programme, le temps restant et la température, mis à jour au moins toutes les 2 secondes.
2. **Given** une alerte survient sur le robot (couvercle ouvert, fin de cuisson, erreur),
   **When** l'appli web est ouverte, **Then** l'alerte s'affiche en évidence en moins de
   3 secondes.
3. **Given** le robot est éteint ou injoignable, **When** l'utilisateur ouvre l'appli web,
   **Then** elle indique « Robot hors ligne » et l'heure de la dernière information reçue.

---

### User Story 2 - Constituer sa liste « à cuisiner » (Priority: P1)

Sur son téléphone, l'utilisateur parcourt et recherche les recettes du robot (Lidl et les
siennes), avec photos et notes. Il ajoute celles qu'il veut cuisiner cette semaine à sa liste
**à cuisiner**, et ajuste le nombre de portions de chacune. Sur le robot, ces recettes
apparaissent en haut de l'accueil cuisine : il n'a plus qu'à choisir et lancer.

**Why this priority**: c'est le cœur du besoin « wishlist » et la source de la liste de courses.

**Independent Test**: ajouter 3 recettes depuis le téléphone, vérifier qu'elles apparaissent sur
le robot, en retirer une sur le robot et vérifier qu'elle disparaît du téléphone.

**Acceptance Scenarios**:

1. **Given** l'appli web est ouverte, **When** l'utilisateur cherche « curry » et ajoute une
   recette à sa liste, **Then** elle apparaît dans « à cuisiner » sur le téléphone et sur le robot
   en moins de 5 secondes.
2. **Given** une recette de la liste prévue pour 4 portions, **When** l'utilisateur choisit
   6 portions, **Then** les quantités utilisées pour les courses sont multipliées par 1,5.
3. **Given** une recette de la liste a été cuisinée sur le robot jusqu'au bout, **When** la
   cuisson se termine, **Then** le robot propose de la retirer de la liste.
4. **Given** l'utilisateur sélectionne une recette « à cuisiner » sur le téléphone,
   **When** il l'ouvre, **Then** il ne peut que la consulter : aucun bouton ne lance de cuisson.

---

### User Story 3 - Générer la liste de courses et l'envoyer dans Notion (Priority: P2)

Depuis la liste « à cuisiner », l'utilisateur génère sa liste de courses. Les ingrédients de
toutes les recettes sont regroupés et additionnés (« 300 g + 200 g de carottes » devient
« 500 g de carottes »). Il décoche ce qu'il a déjà dans ses placards, puis envoie la liste dans
Notion. Au supermarché, il la retrouve dans Notion sur son téléphone et coche au fur et à mesure.

**Why this priority**: c'est l'aboutissement voulu, mais il dépend de la liste « à cuisiner ».

**Independent Test**: avec 2 recettes partageant un ingrédient, générer la liste, vérifier
l'addition, décocher un article, envoyer, et vérifier le contenu dans Notion.

**Acceptance Scenarios**:

1. **Given** 2 recettes utilisent des carottes en grammes, **When** la liste est générée,
   **Then** une seule ligne carottes affiche la somme, avec les recettes concernées.
2. **Given** des unités impossibles à additionner (« 2 carottes » et « 200 g de carottes »),
   **When** la liste est générée, **Then** les deux quantités sont affichées sur la même ligne
   sans conversion inventée.
3. **Given** l'utilisateur a décoché « sel » et « huile », **When** il envoie la liste,
   **Then** Notion reçoit tous les autres articles, chacun cochable, avec la date et les recettes
   d'origine.
4. **Given** Notion refuse l'envoi (accès retiré, Internet coupé), **When** l'utilisateur envoie,
   **Then** un message clair l'indique, la liste reste disponible sur le téléphone et peut être
   renvoyée ou copiée en texte.

---

### User Story 4 - Relier l'appli web et Notion une fois pour toutes (Priority: P2)

L'utilisateur ouvre l'adresse indiquée par le robot sur son téléphone, saisit le code affiché sur
l'écran du robot pour associer son téléphone, puis ajoute l'appli à l'écran d'accueil de son
téléphone. Dans les réglages, il connecte son espace Notion et choisit où ranger les listes.

**Why this priority**: nécessaire avant le premier usage, mais fait une seule fois.

**Independent Test**: associer un nouveau téléphone, puis vérifier qu'un autre téléphone non
associé du même Wi-Fi ne voit rien.

**Acceptance Scenarios**:

1. **Given** un téléphone jamais associé, **When** il ouvre l'adresse du robot, **Then** il doit
   saisir le code à usage unique affiché sur le robot avant de voir quoi que ce soit.
2. **Given** l'utilisateur retire l'association d'un téléphone depuis le robot, **When** ce
   téléphone rouvre l'appli, **Then** il doit de nouveau saisir un code.
3. **Given** une connexion Notion valide, **When** l'utilisateur choisit l'emplacement des
   listes, **Then** un envoi d'essai y crée une liste visible.

---

### Edge Cases

- Recette supprimée ou modifiée par la synchro Lidl alors qu'elle est dans la liste « à cuisiner » :
  elle reste dans la liste, marquée « plus disponible » si elle a disparu.
- Ingrédients sans quantité (« sel », « poivre ») : une seule ligne, sans quantité.
- Même ingrédient écrit différemment (« oignon », « oignons », « oignon jaune ») : regroupés quand
  le nom est identique au pluriel et aux accents près ; sinon lignes séparées.
- Deux téléphones modifient la liste en même temps : la dernière modification gagne, sans perte
  des recettes ajoutées par l'autre.
- Le robot redémarre : la liste « à cuisiner » et les téléphones associés sont conservés.
- Robot hors ligne quand l'utilisateur est au supermarché : la liste de courses déjà envoyée reste
  consultable dans Notion.
- Envoi Notion répété par erreur : le second envoi du même jour met à jour la même liste au lieu
  d'en créer une deuxième.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Le système MUST fournir une appli web utilisable sur le navigateur d'un téléphone,
  installable sur son écran d'accueil, sans appli à télécharger d'un magasin.
- **FR-002**: L'appli web MUST afficher en direct l'état du robot : statut, programme ou recette
  et étape en cours, temps restant, température, vitesse, poids, couvercle, alerte.
- **FR-003**: L'appli web MUST NOT pouvoir démarrer, reprendre, modifier ou arrêter une cuisson,
  ni commander le moteur, la chauffe ou la balance (constitution, principe I).
- **FR-004**: L'appli web MUST permettre de parcourir, rechercher et consulter les recettes
  présentes sur le robot (recettes Lidl et recettes importées).
- **FR-005**: L'utilisateur MUST pouvoir ajouter et retirer des recettes de la liste « à cuisiner »
  et choisir le nombre de portions de chacune, depuis le téléphone ou depuis le robot.
- **FR-006**: La liste « à cuisiner » MUST être la même sur le robot et sur tous les téléphones
  associés, et MUST apparaître en tête de l'accueil cuisine du robot.
- **FR-007**: Le système MUST générer une liste de courses à partir de la liste « à cuisiner », en
  regroupant les ingrédients identiques, en additionnant les quantités de même unité et en
  appliquant le nombre de portions choisi.
- **FR-008**: Le système MUST NOT convertir entre unités incompatibles ; les quantités non
  additionnables MUST rester visibles côte à côte.
- **FR-009**: L'utilisateur MUST pouvoir décocher des articles avant l'envoi et ajouter des
  articles libres (« papier toilette »).
- **FR-010**: Le système MUST envoyer la liste de courses dans l'espace Notion choisi par
  l'utilisateur, chaque article étant cochable dans Notion.
- **FR-011**: Si l'envoi vers Notion échoue, le système MUST le dire clairement et MUST permettre
  de réessayer ou de copier la liste en texte.
- **FR-012**: L'accès à l'appli web MUST être limité aux téléphones associés par un code affiché
  sur l'écran du robot ; l'association MUST pouvoir être retirée depuis le robot.
- **FR-013**: L'accès à l'appli web MUST être limité au réseau local de la maison ; le robot MUST
  NOT être joignable depuis Internet. Hors de la maison, l'utilisateur consulte la liste déjà
  envoyée dans Notion.
- **FR-014**: Les secrets (jeton Notion, codes d'association) MUST rester sur le robot et MUST NOT
  apparaître à l'écran après saisie, dans les journaux ou dans l'appli web.
- **FR-015**: Le service web MUST être désactivé par défaut, activable dans les Réglages du robot,
  et MUST NOT ralentir ni gêner la cuisine ou une cuisson en cours.
- **FR-016**: L'appli web et ses messages MUST être disponibles en français, anglais et espagnol,
  selon la langue du robot.
- **FR-017**: Dans Notion, chaque liste de courses MUST être une page, dans l'emplacement choisi
  par l'utilisateur, titrée avec sa date, contenant un article par case à cocher et la liste des
  recettes d'origine.

### Key Entities

- **Téléphone associé** : nom donné par l'utilisateur, date d'association, dernier accès.
- **Recette à cuisiner** : recette du robot, nombre de portions voulu, date d'ajout, état
  (à cuisiner, cuisinée, plus disponible).
- **Liste de courses** : date, recettes d'origine, articles ; envoyée ou non, et où dans Notion.
- **Article** : nom, quantités par unité, recettes d'origine, coché/décoché, libre ou issu d'une
  recette.
- **Connexion Notion** : jeton (secret), emplacement des listes, état (non configurée, connectée,
  accès refusé).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Depuis le téléphone, l'état du robot s'affiche en moins de 3 secondes après
  l'ouverture de l'appli, et suit le robot avec moins de 2 secondes de retard.
- **SC-002**: Ajouter une recette à la liste « à cuisiner » prend au plus 3 gestes à partir d'une
  recherche.
- **SC-003**: Pour une semaine de 5 recettes, la liste de courses est générée, triée et envoyée
  dans Notion en moins de 2 minutes, décochage compris.
- **SC-004**: Sur un jeu d'essai de 20 recettes Lidl, 100 % des ingrédients de même nom et même
  unité sont correctement additionnés, et aucune conversion d'unité n'est inventée.
- **SC-005**: Un téléphone non associé du même Wi-Fi n'obtient aucune information du robot dans
  100 % des essais.
- **SC-006**: Une cuisson lancée pendant que 2 téléphones suivent le robot se déroule sans
  différence de comportement ni ralentissement perceptible sur l'écran du robot.

## Clarifications

### Session 2026-11-27

- Q: Faut-il pouvoir suivre le robot et gérer la liste hors de la maison ? → A: Non, Wi-Fi de la
  maison uniquement ; hors de la maison, la liste se consulte dans Notion.
- Q: Quelle forme pour la liste dans Notion ? → A: Une page par liste, avec des cases à cocher.

## Assumptions

- L'appli web est servie par le robot lui-même sur le Wi-Fi de la maison ; aucun serveur
  extérieur n'est nécessaire, sauf Notion pour l'envoi de la liste.
- L'utilisateur possède un compte Notion et peut y autoriser une intégration ; d'autres
  destinations (Apple Rappels, Google Keep…) sont hors périmètre de cette version.
- Les recettes affichées sont celles déjà présentes sur le robot ; l'appli web n'ajoute pas de
  nouvelles sources de recettes.
- Le tri des articles par rayon du magasin est hors périmètre de cette version ; les articles sont
  triés par ordre alphabétique.
- Le suivi en direct sur le téléphone reprend les mêmes informations que celles déjà envoyées à
  Home Assistant ; la fonctionnalité Home Assistant existante n'est pas modifiée.
- La mise en avant de la liste « à cuisiner » sur l'accueil du robot complète l'écran Maison de la
  spec 001 sans en dépendre.
