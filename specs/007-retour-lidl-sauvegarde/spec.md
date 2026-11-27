# Feature Specification: Retour au fonctionnement Lidl et sauvegarde des données

**Feature Branch**: `007-retour-lidl-sauvegarde`

**Created**: 2026-11-27

**Status**: Draft

**Input**: User description: "Sauvegarde, bonne idée, je veux un moyen de backup manuellement" ;
« je veux absolument pas brick l'appareil, et que je sauvegarde les états pour pouvoir rollback,
safe at first ».

## Clarifications

### Session 2026-11-27

- Q: Que veut dire « retour arrière » ? → A: Revenir à un robot qui fonctionne **uniquement avec
  l'appli Lidl d'origine**, si besoin. Ce n'est pas revenir à une version précédente de
  Camybrith après une mise à jour.
- Q: Faut-il aussi retirer le root (réécrire le démarrage d'origine) ? → A: Non, le robot peut
  rester rooté. Le retour se fait sans PC Windows ni SP Flash Tool.
- Q: Et si Camybrith est figé en cours de journée, sans Mac sous la main ? → A: Geste à
  l'interrupteur : allumer puis éteindre le robot 3 fois de suite, chaque fois en moins de
  2 minutes, déclenche le retour à l'appli Lidl. Ce geste ne va que dans ce sens.
- Q: Comment revenir sur Camybrith depuis le robot, l'appli Lidl n'ayant pas de liste d'applis ?
  → A: Une bulle « Camybrith » affichée par-dessus l'appli Lidl en mode Lidl ; la toucher ouvre
  Camybrith, qui propose de reprendre.
- Q: Que se passe-t-il si la mémoire du robot est pleine ? → A: Le retour à l'appli Lidl doit
  aboutir quand même ; une réserve de place est gardée pour cela ; les sauvegardes vérifient
  l'espace avant de commencer ; la restauration ne copie pas de gros dossiers ; l'espace libre est
  affiché et une alerte prévient sous 10 %.
- Q: « Sauvegarder les états » ? → A: Garder la trace de chaque modification faite au robot, avec
  la valeur d'avant, pour pouvoir toutes les défaire.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Revenir à l'appli Lidl en un geste (Priority: P1)

Un jour, l'utilisateur veut (ou doit) utiliser son robot comme avant : il choisit « Revenir à
l'appli Lidl » dans les Réglages de Camybrith. Après confirmation et un redémarrage, le robot
démarre sur l'appli Monsieur Cuisine d'origine, qui pilote de nouveau le moteur, la chauffe et la
balance, avec ses recettes. Camybrith ne s'ouvre plus et ne commande plus rien.

**Why this priority**: c'est le filet de sécurité demandé : ne jamais rester bloqué avec
Camybrith.

**Independent Test**: depuis Camybrith en mode Machine réelle, revenir à l'appli Lidl, redémarrer,
puis faire une pesée et une cuisson courte avec l'appli Lidl.

**Acceptance Scenarios**:

1. **Given** Camybrith pilote le robot et aucune cuisson n'est en cours, **When** l'utilisateur
   choisit « Revenir à l'appli Lidl » et confirme, **Then** après le redémarrage l'appli Lidl
   s'ouvre seule et pilote moteur, chauffe et balance.
2. **Given** le retour est fait, **When** le robot redémarre plusieurs fois, **Then** c'est
   toujours l'appli Lidl qui s'ouvre et Camybrith ne prend jamais la machine.
3. **Given** une cuisson est en cours, **When** l'utilisateur demande le retour, **Then** il est
   refusé jusqu'à la fin de la cuisson.
4. **Given** le retour est fait, **When** l'utilisateur consulte le bilan affiché, **Then** il voit
   chaque modification annulée et un rappel : ne jamais accepter de mise à jour **système** Lidl
   tant que le robot est rooté.

---

### User Story 2 - Revenir à l'appli Lidl depuis l'ordinateur, même si Camybrith est cassé (Priority: P1)

Camybrith ne démarre plus ou l'écran est inutilisable. Depuis son Mac, l'utilisateur lance une
commande qui fait exactement le même retour que l'US1.

**Why this priority**: le retour doit marcher précisément quand Camybrith ne marche plus.

**Independent Test**: désactiver volontairement l'interface de Camybrith, lancer la commande
depuis le Mac, vérifier que le robot repart sur l'appli Lidl.

**Acceptance Scenarios**:

1. **Given** l'interface de Camybrith plante au démarrage, **When** l'utilisateur lance la commande
   de retour depuis son Mac, **Then** le robot redémarre sur l'appli Lidl et la commande affiche le
   bilan.
2. **Given** le robot ne répond pas sur le réseau, **When** l'utilisateur lance la commande,
   **Then** elle l'indique clairement et ne fait rien.

---

### User Story 3 - Mode de secours : automatique ou par l'interrupteur (Priority: P1)

Si Camybrith plante plusieurs fois de suite au démarrage, le robot fait seul le retour à l'appli
Lidl, sans ordinateur, et l'indique. Si Camybrith est figé ou inutilisable à un autre moment,
l'utilisateur allume puis éteint le robot à l'interrupteur 3 fois de suite (chaque fois en moins
de 2 minutes) : au démarrage suivant, le robot est sur l'appli Lidl.

**Why this priority**: une famille doit pouvoir cuisiner même si personne ne sait réparer.

**Independent Test**: installer une version d'essai qui plante au démarrage, vérifier que le robot
finit sur l'appli Lidl, utilisable.

**Acceptance Scenarios**:

1. **Given** Camybrith plante 3 fois en moins de 2 minutes après le démarrage, **When** cela se
   produit, **Then** le robot bascule sur l'appli Lidl, qui pilote la machine.
2. **Given** le mode de secours s'est déclenché, **When** l'utilisateur rouvre Camybrith par la
   bulle, **Then** un message explique ce qui s'est passé et propose de reprendre Camybrith ou de
   rester sur l'appli Lidl.
3. **Given** Camybrith est figé, **When** l'utilisateur allume puis éteint le robot 3 fois de
   suite, chaque fois en moins de 2 minutes, puis le rallume, **Then** le robot démarre sur
   l'appli Lidl, sans écran ni ordinateur nécessaires.
4. **Given** le robot est déjà sur l'appli Lidl, **When** l'utilisateur fait le geste,
   **Then** rien ne change (le geste ne ramène jamais vers Camybrith).
5. **Given** le robot reste allumé plus de 2 minutes, **When** il est ensuite éteint et rallumé,
   **Then** le décompte du geste repart de zéro.

---

### User Story 4 - Revenir à Camybrith ensuite (Priority: P2)

Après un retour à l'appli Lidl, l'utilisateur veut retrouver Camybrith. L'appli Lidl n'a pas de
liste d'applis : en mode Lidl, une petite bulle « Camybrith » est affichée par-dessus elle. La
toucher ouvre Camybrith, qui propose « Reprendre Camybrith ». La commande depuis le Mac fait la
même chose.

**Why this priority**: le retour à l'appli Lidl doit être un choix réversible, pas un adieu.

**Independent Test**: faire un retour à l'appli Lidl puis revenir à Camybrith, vérifier que les
favoris et le mode Machine réelle sont retrouvés.

**Acceptance Scenarios**:

1. **Given** le robot est en mode Lidl, **When** il démarre, **Then** la bulle « Camybrith »
   apparaît par-dessus l'appli Lidl, petite, dans un coin, et peut être déplacée ou réduite à un
   onglet sur le bord, sans jamais disparaître complètement.
2. **Given** le robot est revenu à l'appli Lidl avec Camybrith toujours installé, **When**
   l'utilisateur touche la bulle, choisit « Reprendre Camybrith » et confirme, **Then** Camybrith redevient l'écran
   d'accueil et reprend la machine, données intactes.
3. **Given** l'utilisateur reprend Camybrith, **When** la reprise se fait, **Then** les mêmes
   vérifications que le premier passage en Machine réelle sont faites (root, couvercle, base).
4. **Given** Camybrith est cassé au point de ne pas pouvoir afficher la bulle, **When** le robot
   démarre en mode Lidl, **Then** l'appli Lidl fonctionne normalement, sans bulle.

---

### User Story 5 - Retirer complètement Camybrith (Priority: P2)

L'utilisateur ne veut plus de Camybrith : une commande depuis le Mac fait le retour à l'appli Lidl,
puis retire les applis de Camybrith et les modules du projet, au choix en gardant une sauvegarde
de ses données. Le root et l'accès par le Wi-Fi peuvent rester.

**Why this priority**: utile mais rare.

**Independent Test**: retirer Camybrith, vérifier que plus aucune appli ni module du projet n'est
présent et que l'appli Lidl fonctionne.

**Acceptance Scenarios**:

1. **Given** Camybrith est installé, **When** l'utilisateur lance le retrait complet et confirme,
   **Then** il ne reste ni appli Camybrith, ni animation de démarrage, ni module de secours, et
   l'appli Lidl fonctionne.
2. **Given** l'utilisateur a demandé de garder une sauvegarde, **When** le retrait se termine,
   **Then** la sauvegarde de ses données est sur son Mac.

---

### User Story 6 - Sauvegarder ses données à la demande et les récupérer (Priority: P2)

L'utilisateur appuie sur « Sauvegarder maintenant » (ou lance une commande depuis le Mac) :
favoris, recettes perso, réglages, listes et planning sont enregistrés dans un fichier daté, qu'il
récupère sur son Mac.

**Why this priority**: demande explicite (« backup manuellement »), mais ne protège pas le
fonctionnement du robot comme les US1 à US3.

**Independent Test**: créer une sauvegarde, la récupérer sur le Mac, vérifier son contenu et
l'absence de secrets.

**Acceptance Scenarios**:

1. **Given** le robot est au repos, **When** l'utilisateur appuie sur « Sauvegarder maintenant »,
   **Then** une sauvegarde datée est créée en moins de 30 secondes avec un résumé.
2. **Given** une sauvegarde existe, **When** l'utilisateur lance la commande de récupération sur le
   Mac, **Then** le fichier est copié dans un dossier local.

---

### User Story 7 - Restaurer ses données (Priority: P3)

Après une réinstallation, l'utilisateur restaure une sauvegarde : il voit ce qui sera remplacé,
confirme, et retrouve ses données.

**Why this priority**: complète la sauvegarde ; moins urgent que le retour à l'appli Lidl.

**Independent Test**: sauvegarder, supprimer un favori et une recette perso, restaurer, vérifier
qu'ils reviennent.

**Acceptance Scenarios**:

1. **Given** une sauvegarde valide, **When** l'utilisateur la restaure et confirme, **Then** ses
   données reviennent, et les secrets à ressaisir sont signalés.
2. **Given** une sauvegarde abîmée ou d'une version plus récente, **When** il la choisit,
   **Then** elle est refusée avant toute modification, avec la raison.

---

### Edge Cases

- Coupure de courant pendant un retour à l'appli Lidl : au redémarrage, le retour se termine tout
  seul ; le robot ne reste jamais à moitié entre les deux.
- Une modification du registre ne peut pas être annulée (composant introuvable après une mise à
  jour de l'appli Lidl) : le retour continue pour les autres, le bilan signale clairement le
  problème, et l'appli Lidl reste priorisée.
- Modification faite à la main par l'utilisateur, hors registre : le bilan ne peut pas la connaître ;
  une vérification finale contrôle tout de même que l'appli Lidl a bien la machine.
- Appli Lidl mise à jour (depuis le Play Store ou Lidl) pendant que Camybrith était actif : le
  retour fonctionne avec la nouvelle version.
- L'utilisateur fait le retour alors que le robot n'a jamais été passé en Machine réelle : rien à
  défaire côté machine, le retour réussit sans erreur.
- Mémoire pleine pour une sauvegarde : refus clair avant de commencer, avec proposition de
  supprimer les plus anciennes.
- Mémoire pleine au moment d'un retour à l'appli Lidl (ou du geste, ou du mode de secours) : le
  retour aboutit quand même ; le bilan indique ce qui n'a pas pu être écrit.
- Mémoire presque pleine (moins de 10 % libre) : Camybrith prévient, car Android devient instable
  dans ce cas, avec ou sans Camybrith.
- Coupures de courant rapprochées (orage) qui imitent le geste de l'interrupteur : le robot passe
  sur l'appli Lidl ; c'est sans danger et réversible par la bulle.
- Geste fait pendant une cuisson : couper le courant arrête déjà la cuisson ; au démarrage, rien
  ne tourne et le retour se fait sans question.

## Requirements *(mandatory)*

### Functional Requirements

**Registre des modifications**

- **FR-001**: Chaque modification du robot faite par Camybrith, ses scripts ou ses modules
  (composant de l'appli Lidl désactivé, écran d'accueil, réglage Android modifié, module installé,
  appli installée, démarrage automatique) MUST être inscrite dans un registre, avec la valeur
  d'avant et l'action qui l'annule, **avant** d'être faite.
- **FR-002**: Le registre MUST survivre à la désinstallation de Camybrith et MUST pouvoir être
  copié sur l'ordinateur.

**Retour à l'appli Lidl**

- **FR-003**: Le système MUST permettre de revenir au fonctionnement avec la seule appli Lidl
  depuis les Réglages de Camybrith et depuis une commande sur l'ordinateur ; cette commande MUST
  marcher même si l'interface de Camybrith ne démarre plus.
- **FR-004**: Le retour MUST : arrêter la commande de la machine par Camybrith, annuler les
  modifications du registre dans l'ordre inverse, faire de l'appli Lidl l'écran de démarrage,
  empêcher Camybrith de se lancer au démarrage, puis redémarrer le robot.
- **FR-005**: Le retour MUST se terminer par une vérification (appli Lidl activée et lancée au
  démarrage, aucun composant Camybrith actif sur la machine) et un bilan lisible.
- **FR-006**: Le retour MUST être refusé pendant une cuisson et MUST reprendre tout seul après une
  coupure de courant.
- **FR-007**: Le retour MUST NOT écrire dans les partitions du système et MUST NOT retirer le root
  ni l'accès par le Wi-Fi.
- **FR-008**: Après 3 plantages de l'interface en moins de 2 minutes après le démarrage, le
  système MUST faire le retour à l'appli Lidl automatiquement (mode de secours).
- **FR-016**: Allumer puis éteindre le robot 3 fois de suite, chaque fois en moins de 2 minutes,
  MUST déclencher le retour à l'appli Lidl au démarrage suivant, sans écran ni ordinateur. Ce
  geste MUST NOT ramener vers Camybrith. Rester allumé plus de 2 minutes MUST remettre le décompte
  à zéro.
- **FR-009**: Le bilan MUST rappeler de ne jamais accepter une mise à jour système Lidl tant que le
  robot est rooté ; ce rappel MUST être affiché sur l'écran du robot avant le redémarrage (retour
  depuis le robot) ou par la commande (retour depuis l'ordinateur). L'appli Lidl elle-même n'est
  jamais modifiée.

**Reprise et retrait**

- **FR-010**: L'utilisateur MUST pouvoir reprendre Camybrith après un retour, avec les mêmes
  vérifications que le premier passage en Machine réelle.
- **FR-017**: En mode Lidl, une bulle « Camybrith » MUST être affichée par-dessus l'appli Lidl à
  chaque démarrage ; elle MUST pouvoir être déplacée ou réduite, MUST NOT pouvoir être supprimée
  depuis le robot, et la toucher MUST ouvrir Camybrith avec la proposition de reprise.
- **FR-011**: L'utilisateur MUST pouvoir retirer complètement Camybrith (applis, modules du projet,
  données, registre) depuis l'ordinateur, après un retour à l'appli Lidl, en proposant d'abord une
  sauvegarde.

**Sauvegarde des données**

- **FR-012**: L'utilisateur MUST pouvoir créer une sauvegarde à la demande depuis le robot et
  depuis l'ordinateur, et la récupérer sur l'ordinateur.
- **FR-013**: Une sauvegarde MUST contenir favoris, recettes importées et personnelles, réglages,
  liste « à cuisiner », planning et carnet (quand ils existent) ; elle MUST NOT contenir de secret
  ni les recettes Lidl elles-mêmes.
- **FR-014**: Chaque sauvegarde MUST être un fichier daté, avec la version de Camybrith et un
  contrôle d'intégrité ; le robot MUST garder au plus les 5 dernières.
- **FR-015**: La restauration MUST afficher un résumé, demander confirmation, être tout ou rien,
  refuser une sauvegarde abîmée ou plus récente que l'appli, et être refusée pendant une cuisson.
  Elle MUST NOT demander plus d'espace libre que la taille de la sauvegarde plus une marge.

**Espace de stockage**

- **FR-018**: Le retour à l'appli Lidl (depuis le robot, l'ordinateur, le mode de secours ou le
  geste) MUST aboutir même si la mémoire du robot est pleine ; les actions qui rendent la machine
  à l'appli Lidl MUST passer avant toute écriture de fichier.
- **FR-019**: Avant une sauvegarde, le système MUST vérifier qu'il reste au moins la taille estimée
  de la sauvegarde plus 50 Mo ; sinon il MUST refuser clairement et proposer de supprimer les
  sauvegardes les plus anciennes.
- **FR-020**: L'espace libre du robot MUST être affiché dans les Réglages et par la commande d'état
  sur l'ordinateur ; sous 10 % d'espace libre, Camybrith MUST prévenir l'utilisateur.

### Key Entities

- **Modification** : ce qui a été changé, valeur d'avant, action d'annulation, date, origine
  (appli, script, module), état (active, annulée, échec d'annulation).
- **Registre** : liste ordonnée des modifications d'un robot.
- **Bilan de retour** : modifications annulées, échecs, résultat de la vérification finale.
- **Sauvegarde** : date, version de Camybrith, identité du robot (anonymisée), contenu résumé,
  contrôle d'intégrité.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Le retour à l'appli Lidl prend moins de 3 minutes, redémarrage compris, et 2 gestes
  sur le robot ou 1 commande sur l'ordinateur.
- **SC-002**: Après un retour, l'appli Lidl réussit une pesée et une cuisson courte dans 100 % des
  essais.
- **SC-003**: Avec une version de Camybrith qui plante au démarrage, le robot redevient utilisable
  avec l'appli Lidl sans intervention dans 100 % des essais.
- **SC-004**: Sur 20 retours interrompus par une coupure de courant à des moments aléatoires, le
  robot finit toujours sur l'appli Lidl fonctionnelle.
- **SC-005**: Après un retour puis une reprise, 100 % des favoris et réglages de Camybrith sont
  retrouvés.
- **SC-007**: Le geste de l'interrupteur ramène le robot sur l'appli Lidl dans 100 % des essais
  (10 essais), et ne se déclenche jamais lors d'un usage normal (100 redémarrages espacés de plus
  de 2 minutes).
- **SC-008**: Depuis le mode Lidl, revenir sur Camybrith prend au plus 3 gestes sur le robot.
- **SC-009**: Mémoire du robot remplie volontairement à 100 % : le retour à l'appli Lidl, le geste
  de l'interrupteur et le démarrage fonctionnent dans 100 % des essais, et une sauvegarde est
  refusée proprement.
- **SC-006**: Une sauvegarde se crée en moins de 30 secondes, ne contient aucun secret, et sa
  restauration rend 100 % des favoris, recettes perso, listes et planning.

## Assumptions

- Le robot reste rooté (Magisk) et accessible par le Wi-Fi après un retour ; le retrait du root
  (réécriture du démarrage d'origine avec SP Flash Tool) reste décrit par le guide et hors de cette
  fonctionnalité.
- Les modifications faites pendant l'installation **avant** l'existence du registre (applis
  d'aide de l'étape 5.3, options développeur) sont inscrites rétroactivement lors de la première
  installation de la version qui apporte le registre, d'après ce que le guide fait faire.
- Le retour à une version précédente de Camybrith après une mise à jour est hors périmètre :
  en cas de mauvaise mise à jour, le retour à l'appli Lidl (ou le mode de secours) suffit, puis
  l'utilisateur réinstalle la version voulue depuis l'ordinateur.
- La sauvegarde complète de la mémoire faite à l'étape 2 du guide reste indispensable ; cette
  fonctionnalité ne la remplace pas.
