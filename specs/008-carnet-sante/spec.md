# Feature Specification: Carnet de santé du robot

**Feature Branch**: `008-carnet-sante`

**Created**: 2026-11-27

**Status**: Draft

**Input**: User description: "J'aime le carnet de santé" : heures de moteur, nombre de cuissons,
historique des erreurs, rappels d'entretien (joint, lame) et rapport exportable pour signaler un
problème.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Voir l'usage de son robot (Priority: P1)

Dans les Réglages, l'utilisateur ouvre le carnet de santé : depuis quand Camybrith pilote le robot,
heures de moteur, heures de chauffe, nombre de cuissons, utilisation du Turbo, cuissons par
semaine, recettes les plus cuisinées.

**Why this priority**: c'est la base du carnet, sur laquelle reposent les rappels et le rapport.

**Independent Test**: faire 3 cuissons de durées connues, vérifier que les compteurs augmentent
des bonnes valeurs.

**Acceptance Scenarios**:

1. **Given** le robot a fait 3 cuissons de 5, 10 et 20 minutes, **When** l'utilisateur ouvre le
   carnet, **Then** le nombre de cuissons a augmenté de 3 et le temps de moteur de 35 minutes
   (à la minute près).
2. **Given** une cuisson est interrompue, **When** elle s'arrête, **Then** seul le temps
   réellement écoulé est compté.
3. **Given** l'utilisateur consulte le carnet, **When** il regarde les statistiques, **Then** il
   voit le nombre de cuissons par semaine sur les 12 dernières semaines.

---

### User Story 2 - Historique des incidents (Priority: P1)

Le carnet liste les incidents : alertes de la base (avec leur code), surchauffes, pertes de
liaison avec la base, plantages de l'interface, avec date, heure et ce qui était en cours.

**Why this priority**: permet de repérer un problème qui revient (joint, capteur, carte) avant
qu'il ne s'aggrave.

**Independent Test**: provoquer une alerte « couvercle ouvert », vérifier qu'elle apparaît dans
l'historique avec l'heure et la cuisson concernée.

**Acceptance Scenarios**:

1. **Given** une alerte de la base survient, **When** l'utilisateur ouvre l'historique, **Then**
   elle y figure avec son code, son message, l'heure et le programme en cours.
2. **Given** le même incident se répète 3 fois en une semaine, **When** l'utilisateur ouvre le
   carnet, **Then** un message signale l'incident récurrent.

---

### User Story 3 - Rappels d'entretien (Priority: P2)

Selon l'usage réel, le robot rappelle : « Vérifie le joint du bol (40 h de moteur depuis le dernier
contrôle) », « Contrôle l'usure de la lame ». L'utilisateur marque l'entretien comme fait, ce qui
remet le compteur correspondant à zéro.

**Why this priority**: utile pour la durée de vie du robot, mais pas urgent.

**Independent Test**: régler un seuil bas, dépasser le seuil, vérifier le rappel, le marquer fait,
vérifier la remise à zéro.

**Acceptance Scenarios**:

1. **Given** le compteur du joint dépasse son seuil, **When** le robot est au repos sur l'accueil,
   **Then** un rappel discret s'affiche (jamais pendant une cuisson).
2. **Given** l'utilisateur marque « joint vérifié », **When** il valide, **Then** le compteur du
   joint repart de zéro et la date est notée dans le carnet.

---

### User Story 4 - Exporter un rapport pour demander de l'aide (Priority: P2)

En cas de problème, l'utilisateur touche « Exporter un rapport ». Il obtient un fichier (sur le
Mac, le téléphone ou à l'écran sous forme de code à recopier) avec la version de Camybrith, les
versions du robot, les compteurs et les derniers incidents, à joindre à une issue du projet.

**Why this priority**: facilite l'aide de la communauté ; utile mais occasionnel.

**Independent Test**: exporter un rapport et vérifier qu'il contient les versions, compteurs et
incidents, et aucune donnée personnelle.

**Acceptance Scenarios**:

1. **Given** l'utilisateur exporte un rapport, **When** il l'ouvre, **Then** il contient versions,
   compteurs et 50 derniers incidents.
2. **Given** l'utilisateur exporte un rapport, **When** il l'examine, **Then** il n'y trouve aucun
   secret, nom de réseau, adresse, numéro de série complet ni contenu de recette personnelle.

---

### Edge Cases

- Carnet commencé après l'installation de Camybrith : les compteurs démarrent à ce moment ; le
  carnet l'indique (« depuis le 03/12/2026 »).
- Remise à zéro volontaire des compteurs : possible, avec confirmation, et notée dans le carnet.
- Mémoire limitée : l'historique des incidents garde au moins 12 mois ou 1 000 entrées, les plus
  anciennes étant supprimées en premier.
- Coupure de courant pendant une cuisson : le temps compté jusqu'à la coupure est conservé (à une
  minute près).
- Retour à l'appli d'origine : le temps passé sous l'appli d'origine n'est pas compté ; le carnet
  le signale.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Le système MUST compter : temps de moteur, temps de chauffe, nombre de cuissons
  (terminées, interrompues), utilisations du Turbo, pesées, avec la date de début du comptage.
- **FR-002**: Le système MUST enregistrer chaque incident (alerte de la base avec code, perte de
  liaison, surchauffe, plantage de l'interface) avec date, heure et contexte.
- **FR-003**: Le système MUST signaler un incident identique répété au moins 3 fois en 7 jours.
- **FR-004**: Le système MUST proposer des rappels d'entretien (joint, lame, nettoyage approfondi)
  avec des seuils réglables ; un rappel MUST NOT s'afficher pendant une cuisson.
- **FR-005**: L'utilisateur MUST pouvoir marquer un entretien fait, ce qui remet son compteur à zéro
  et l'inscrit dans le carnet.
- **FR-006**: Le système MUST exporter un rapport lisible contenant versions, compteurs et
  incidents récents, sans aucune donnée personnelle ni secret.
- **FR-007**: Les compteurs et l'historique MUST survivre aux redémarrages, aux mises à jour et
  aux coupures de courant (perte maximale : 1 minute de comptage).
- **FR-008**: Le comptage MUST NOT ralentir ni perturber la commande de la machine.
- **FR-009**: Le carnet MUST faire partie de la sauvegarde manuelle (spec 007).
- **FR-010**: Le carnet MUST être disponible en français, anglais et espagnol.

### Key Entities

- **Compteurs** : temps de moteur, temps de chauffe, cuissons, Turbo, pesées, date de début.
- **Incident** : date, type, code, message, contexte (programme, recette, étape).
- **Entretien** : élément (joint, lame…), seuil, compteur depuis le dernier entretien, dates des
  entretiens.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Les compteurs de temps sont justes à 1 % près sur 10 cuissons de durées connues.
- **SC-002**: 100 % des alertes de la base apparaissent dans l'historique.
- **SC-003**: Un rapport s'exporte en moins de 10 secondes et 2 gestes.
- **SC-004**: Un relecteur ne trouve aucune donnée personnelle dans 100 % des rapports d'essai.

## Assumptions

- Le robot ne fournit pas d'historique d'usage antérieur : le comptage commence avec la
  fonctionnalité.
- Les seuils d'entretien par défaut sont des valeurs prudentes proposées par le projet (pas des
  préconisations du fabricant) ; l'utilisateur peut les modifier.
- Le rapport ne s'envoie nulle part automatiquement : c'est l'utilisateur qui choisit de le
  partager.
