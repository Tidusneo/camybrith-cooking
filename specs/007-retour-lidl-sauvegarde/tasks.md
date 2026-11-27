---

description: "Task list for 007-retour-lidl-sauvegarde"
---

# Tasks: Retour au fonctionnement Lidl et sauvegarde des données

**Input**: Design documents from `specs/007-retour-lidl-sauvegarde/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/cli.md, contracts/ui.md,
quickstart.md

**Tests**: pas de TDD demandé par la spec. Sont prévus : les tests JUnit imposés par la
constitution (principe III) pour tout ce qui est dans `core/`, et les scénarios du banc d'essai
sur émulateur (quickstart §B), qui servent de tests d'intégration.

**Organization**: une phase par parcours de la spec, dans l'ordre des priorités (US1 à US3 en P1,
US4 à US6 en P2, US7 en P3).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: parallélisable (fichiers différents, pas de dépendance sur une tâche non terminée)
- **[Story]**: parcours de la spec (US1 à US7)

## Path Conventions

Dépôt existant (plan.md › Project Structure) : `core/` (Kotlin pur), `app/`, `modules/`,
`outils/`, nouveau module Gradle de test `bench/fake-lidl/`. Rien dans `pilote/`.

---

## Phase 1: Setup

**Purpose**: outillage et squelettes, sans effet sur le robot

- [X] T001 Documenter le prérequis JDK 17 et l'installation de l'image d'émulateur Android 8.1 (API 27 `default`, à défaut API 28) dans la section « Fabriquer les APK soi-même » de `README.md` et `README.en.md`
- [X] T002 [P] Créer le paquet `core/src/main/kotlin/app/toque/core/backup/` et les fichiers de test vides `core/src/test/kotlin/app/toque/core/RegistryTest.kt`, `RescueRuleTest.kt`, `BackupTest.kt`
- [X] T003 [P] Créer le squelette du module Magisk `modules/secours/` : `module.prop` (id `camybrith_secours`), `META-INF/com/google/android/update-binary` et `updater-script` sur le modèle de `modules/adb-wifi/`, sans dossier `system/`
- [X] T004 [P] Créer le module Gradle de test `bench/fake-lidl/` (paquet identique à `RobotModel.SMART.vendorPackage`, une activité de lancement avec `HOME`, un service dont le nom finit par `RobotModel.SMART.hardwareServiceSuffix`) et l'inclure dans `settings.gradle.kts`, sans publication
- [X] T005 [P] Ajouter `sauvegardes/` à `.gitignore`
- [X] T006 [P] Ajouter `shellcheck` sur `modules/secours/*` et `outils/*.sh`, et l'empaquetage du zip du module de secours, dans `.github/workflows/release.yml`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: registre, journal, commande `camybrith` de base, banc d'essai, et inscription de toutes les modifications existantes

**⚠️ CRITICAL**: aucun parcours ne commence avant la fin de cette phase

> **Note d'implémentation (research.md D16)** : la logique de `camybrith` (T011 à T014, T021 à
> T023) est en Kotlin dans `core/backup/RescueCommand.kt`, lancée par `app_process` depuis le lanceur
> `modules/secours/camybrith` ; le journal côté shell (T013) est donc dans ce moteur. Le journal
> est tenté avant les `pm`, sans jamais bloquer le retour (précision de D15).

- [X] T007 [P] Modèle et règles du registre dans `core/src/main/kotlin/app/toque/core/backup/Registry.kt` : `data-model.md` Modification.id, Modification.type, Modification.cible (format vérifié selon le type, toute autre valeur refusée), Modification.avant, Modification.apres, Modification.portee, Modification.origine, Modification.date, Modification.deduit, Modification.etat, Modification.erreur, Registre.format ; règle de fusion de data-model.md (Modification › Règles) ; plan d'annulation par portée (Registre › Annulation) et plan de réapplication
- [X] T008 [P] Journal d'opération et bilan dans `core/src/main/kotlin/app/toque/core/backup/Operation.kt` : `data-model.md` Journal.type, Journal.etape (transitions dans l'ordre défini, toute autre refusée), Journal.origine ; règle de reprise au démarrage (Journal d'opération › Au démarrage) ; modèle du Bilan de retour (data-model.md › Bilan de retour, dont `rappel`) et conservation des 10 derniers
- [X] T009 [P] Règles d'espace dans `core/src/main/kotlin/app/toque/core/backup/Space.kt` : seuil d'alerte (< 10 %), seuil de sauvegarde (taille estimée + 50 Mo), selon `data-model.md` Espace de stockage ; tests dans `core/src/test/kotlin/app/toque/core/BackupTest.kt`
- [X] T010 Tests JUnit de T007 et T008 dans `core/src/test/kotlin/app/toque/core/RegistryTest.kt` (cibles invalides refusées, fusion qui garde l'état d'origine, ordre décroissant d'`id`, portées respectées, transitions invalides refusées, reprise qui termine un retour)
- [X] T011 Socle de `modules/secours/camybrith` selon `contracts/cli.md` : vérification root, création de `/data/adb/camybrith/` en 700, codes 0/1/2, option `--json`, écriture atomique (fichier temporaire puis renommage), réserve de 2 Mo et écriture qui, en cas d'échec faute de place, supprime la réserve puis réessaie une fois (`data-model.md` Espace de stockage, research.md D15), espace libre et réserve affichés par `etat`, refus si cuisson en cours selon `data-model.md` État de cuisson.occupe et État de cuisson.maj (règle de research.md D12 : état inconnu → refus, sauf `--forcer` et `--secours`), sous-commande `etat`
- [X] T012 Sous-commandes `noter` et `noter-fin` dans `modules/secours/camybrith` : écriture de `data-model.md` Modification (tous champs) dans `registre.json`, refus d'un type ou d'une cible hors format (même règles que T007)
- [X] T013 Journal d'opération côté shell dans `modules/secours/camybrith` (`op.json`, `data-model.md` Journal.type, Journal.etape, Journal.origine) : fonctions `op_debut`, `op_etape`, `op_fin`
- [X] T014 Sous-commande `inscrire` dans `modules/secours/camybrith` : inscription rétroactive (research.md D8) avec `data-model.md` Modification.deduit à `true`, et portée selon research.md D2
- [X] T015 Écriture de `/data/adb/camybrith/robot.json` (`data-model.md` Identité du robot pour le module : vendorPackage, hardwareServiceSuffix, camybrithBootReceiver, camybrithHomeAlias) et appels à `camybrith` par `RootShell` dans `app/src/main/kotlin/app/toque/backup/Rescue.kt` ; écriture de `cuisson.json` (`data-model.md` État de cuisson.occupe, État de cuisson.programme, État de cuisson.maj) à chaque changement de `snapshot.busy` et toutes les 30 s pendant un programme, branchée dans `app/src/main/kotlin/app/toque/ui/MainActivity.kt` ; aucun code dans `pilote/`
- [X] T016 Déplacer le filtre `HOME` de `MainActivity` vers un `activity-alias` `.ui.Accueil` dans `app/src/main/AndroidManifest.xml`, `LAUNCHER` restant sur `MainActivity`
- [X] T017 Inscrire au registre avant d'agir dans `app/src/main/kotlin/app/toque/recipes/LidlSync.kt` (`setVendorHardware` : `noter composant … portee lidl`, puis `noter-fin`), et dans `app/src/main/kotlin/app/toque/ui/screens/SettingsScreen.kt` (passage en Machine réelle) ; si le module est absent, proposer de l'installer et ne rien modifier. Le démarrage de Camybrith n'est pas inscrit ici mais à l'installation (T018, T014)
- [X] T018 [P] Inscrire au registre avant d'agir dans `outils/installer.sh` (type `appli`, portée `retrait` ; et type `demarrage` pour `BootReceiver` et `.ui.Accueil`, portée `lidl`, `data-model.md` Modification.avant = `absent` à la première installation), la mise à jour depuis le robot (`app/src/main/kotlin/app/toque/update/Updater.kt` : même paquet, couvert par la fusion de T007), `outils/barre.sh` (type `reglage`, portée `retrait`) et `outils/animation.sh` (type `module`, portée `retrait`) ; s'arrêter et proposer `outils/secours.sh installer` si le module manque
- [X] T019 [P] Script `outils/secours.sh` (`installer` : annonce, confirmation, installation du zip, `camybrith inscrire`, proposition de redémarrage ; `retirer` ; `etat`) sur le modèle de `outils/commun.sh`
- [X] T020 Banc d'essai `outils/banc-essai.sh` : création de l'émulateur (research.md D11), `adb root`, installation de `bench/fake-lidl`, de Camybrith en mode Démo, du module de secours dans `/data/adb/modules/camybrith_secours`, d'une doublure de `magisk` ; exécution d'un scénario par nom ou de tous, avec OK/ÉCHEC ; scénario B1 de `quickstart.md`

**Checkpoint**: `./gradlew :core:test` au vert ; `bash outils/banc-essai.sh B1` OK (passage en Machine réelle inscrit au registre avant d'agir)

---

## Phase 3: User Story 1 - Revenir à l'appli Lidl en un geste (Priority: P1) 🎯 MVP

**Goal**: depuis les Réglages, le robot repart sur la seule appli Lidl, vérifiée, avec bilan et rappel

**Independent Test**: quickstart §B scénarios B2, B3, B5 ; puis §C étapes 2 à 4 sur le robot

- [X] T021 [US1] Sous-commande `retour-lidl [--secours]` dans `modules/secours/camybrith` selon research.md D5 et D15 : `am force-stop` de l'interface et du pilote et annulations par `pm` **avant** toute écriture de fichier, puis journal (`data-model.md` Journal.etape) ; si la mémoire est pleine malgré la réserve, le retour aboutit sans journal et le bilan porte `memoirePleine` ; annulation des modifications `data-model.md` Modification.portee = `lidl` dans l'ordre de Registre › Annulation (un échec met Modification.etat à `echec` avec Modification.erreur et n'arrête pas les autres), vérification finale de research.md D6 à partir de `robot.json` (y compris la résolution de l'écran d'accueil vers l'appli Lidl), écriture de `mode.json` (`data-model.md` Mode du robot.mode = `lidl`, Mode du robot.depuis, Mode du robot.origine) à l'étape `termine`, écriture du bilan (`data-model.md` Bilan de retour), redémarrage
- [X] T022 [US1] Sous-commande `bilan [nom]` dans `modules/secours/camybrith`
- [X] T023 [US1] Dans `modules/secours/service.sh` : recréer la réserve si elle manque et que l'espace le permet ; après `sys.boot_completed`, terminer une opération dont `data-model.md` Journal.etape n'est pas `termine` (Journal d'opération › Au démarrage), puis contrôler après un retour que l'appli Lidl est au premier plan et l'ajouter au bilan
- [X] T024 [US1] Remplacer « Revenir à l'appli d'origine » (`restoreVendor`) par « Revenir à l'appli Lidl » dans `app/src/main/kotlin/app/toque/ui/screens/SettingsScreen.kt` selon `contracts/ui.md` : refus pendant une cuisson, liste de ce qui sera annulé, ce qui restera, rappel « pas de mise à jour système Lidl », appel `Rescue.retourLidl()` ; mode dégradé actuel si le module est absent, signalé comme tel
- [X] T025 [P] [US1] Textes de T024 en anglais et espagnol dans `app/src/main/assets/lang/en.json` et `app/src/main/assets/lang/es.json`
- [ ] T026 [US1] Scénarios B2, B3 et B5 dans `outils/banc-essai.sh` : B2 chronométré (échec au-delà de 3 min, SC-001) ; B5 avec arrêt brutal de l'émulateur à chaque étape du journal, puis mode `B5 aleatoire` de 20 coupures à des instants tirés au hasard (SC-004)

**Checkpoint**: B2, B3, B5 OK. Le robot peut toujours revenir à l'appli Lidl depuis Camybrith.

---

## Phase 4: User Story 2 - Revenir à l'appli Lidl depuis l'ordinateur (Priority: P1)

**Goal**: même retour depuis le Mac, même si l'interface est cassée

**Independent Test**: quickstart §B scénario B7 ; §C étape 6

- [X] T027 [US2] Script `outils/origine.sh` selon `contracts/cli.md` : connexion (`commun.sh`), robot injoignable → message clair et arrêt, état de cuisson inconnu → avertissement et confirmation avant `--forcer` (research.md D12), liste de ce qui sera annulé (lecture de `registre.json`), confirmation, `camybrith retour-lidl`, affichage du bilan et du rappel
- [X] T028 [US2] Sous-commande `registre` dans `outils/origine.sh` : copie de `registre.json` et des bilans dans `sauvegardes/`
- [ ] T029 [US2] Scénario B7 (interface désinstallée à la main) dans `outils/banc-essai.sh`

**Checkpoint**: B7 OK. Le retour marche sans l'interface.

---

## Phase 5: User Story 3 - Mode de secours : automatique ou par l'interrupteur (Priority: P1)

**Goal**: retour à l'appli Lidl sans intervention après 3 plantages au démarrage, ou par le geste de l'interrupteur quand l'écran est figé, et explication à la réouverture

**Independent Test**: quickstart §B scénarios B6, B10, B11, B13 ; §C étape 6 bis

- [X] T030 [P] [US3] Règle de décision dans `core/src/main/kotlin/app/toque/core/backup/RescueRule.kt` (`data-model.md` État de secours › Règle de décision)
- [X] T031 [US3] Tests JUnit de T030 dans `core/src/test/kotlin/app/toque/core/RescueRuleTest.kt` avec des extraits de `logcat -b crash` (2 plantages : non ; 3 en 100 s : oui ; 3 en 200 s : non ; journal d'opération en cours : non)
- [X] T032 [US3] Surveillance dans `modules/secours/service.sh` (après la reprise de T023) : comptage des plantages de `app.toque` pendant 120 s, à 3 `camybrith retour-lidl --secours`, écriture de `data-model.md` État de secours (secours.json : date, nombre de plantages, version en cause)
- [X] T033 [US3] Geste de l'interrupteur dans `modules/secours/post-fs-data.sh` (incrément de `demarrages-courts` puis `sync`, en respectant les règles de research.md D13 : aucune attente, aucun appel à `pm`/`am`, erreurs ignorées, compteur illisible remis à 0) et `modules/secours/service.sh` (remise à zéro après 2 min suivant `sys.boot_completed` ; à 3 si `data-model.md` Mode du robot.mode ≠ `lidl`, `camybrith retour-lidl --geste`) selon `data-model.md` Geste de l'interrupteur et research.md D13 ; option `--geste` dans `modules/secours/camybrith` (bilan avec `data-model.md` Journal.origine = `geste`)
- [X] T034 [US3] Bandeau à l'ouverture de Camybrith dans `app/src/main/kotlin/app/toque/ui/MainActivity.kt` : seulement si `data-model.md` Mode du robot.mode = `lidl`, message selon Mode du robot.origine (« suite à 3 plantages au démarrage » si `secours`, « suite au geste de l'interrupteur » si `geste`), lien vers le bilan, bouton « Rester sur l'appli Lidl » (`contracts/ui.md`)
- [X] T035 [US3] Ligne d'aide sur le geste de l'interrupteur sous « Revenir à l'appli Lidl » dans `app/src/main/kotlin/app/toque/ui/screens/SettingsScreen.kt` (`contracts/ui.md`)
- [X] T036 [P] [US3] Textes de T034 et T035 dans `app/src/main/assets/lang/en.json` et `app/src/main/assets/lang/es.json`
- [X] T037 [US3] Version d'essai qui plante au démarrage : propriété Gradle `-Pbench.crash=true` dans `app/build.gradle.kts` (ressource `R.bool.bench_crash`, fausse par défaut, jamais publiée) qui lève une exception dans `onCreate` de `app/src/main/kotlin/app/toque/ToqueApp.kt` ; scénario B6 dans `outils/banc-essai.sh`
- [ ] T038 [US3] Scénarios B10 (3 démarrages courts, 10 essais), B11 (100 redémarrages espacés, mode long) et B13 (compteur `demarrages-courts` corrompu ou illisible : démarrage normal, compteur remis à 0) dans `outils/banc-essai.sh`

**Checkpoint**: B6, B10, B11, B13 OK. Le robot reste utilisable même avec une interface cassée ou figée, sans ordinateur.

---

## Phase 6: User Story 4 - Revenir à Camybrith ensuite (Priority: P2)

**Goal**: reprendre Camybrith après un retour, par la bulle ou depuis le Mac, données intactes

**Independent Test**: quickstart §B scénarios B4 et B12 ; §C étapes 5 et 6 bis

- [X] T039 [US4] Sous-commande `reprendre` dans `modules/secours/camybrith` (`contracts/cli.md`) : journal `data-model.md` Journal.type = `reprise` avec ses étapes (Journal.etape), reprise interrompue abandonnée vers l'appli Lidl par `service.sh` ; écriture de `mode.json` (`data-model.md` Mode du robot.mode = `camybrith`) à l'étape `termine` ; réapplication des modifications de portée `lidl` annulées par le dernier retour (plan de réapplication de T007, `data-model.md` Modification.etat `annulee` → `active`), effacement de secours.json, ouverture de Camybrith ; refus si l'appli Lidl a une cuisson en cours
- [X] T040 [US4] Mode Lidl de la bulle dans `app/src/main/kotlin/app/toque/ui/ReturnBubble.kt` (extra `modeLidl` : déplaçable, appui long pour réduire en onglet sur le bord, pas de fermeture, un appui ouvre `MainActivity` sur le bandeau de reprise) selon `contracts/ui.md` › Bulle et research.md D14
- [X] T041 [US4] Démarrage de la bulle à chaque démarrage quand `data-model.md` Mode du robot.mode = `lidl`, dans `modules/secours/service.sh` (`am startservice` en root avec `modeLidl` ; si Android le refuse pour un service non exporté, récepteur protégé par permission de signature activé seulement en mode Lidl et inscrit au registre, research.md D14)
- [X] T042 [US4] Bouton « Reprendre Camybrith » dans le bandeau de `app/src/main/kotlin/app/toque/ui/MainActivity.kt` : `Rescue.reprendre()`, puis les vérifications de Machine réelle existantes (`prepareHardware` de `SettingsScreen.kt`)
- [X] T043 [US4] Sous-commande `reprendre` dans `outils/origine.sh`
- [X] T044 [P] [US4] Textes de T042 dans `app/src/main/assets/lang/en.json` et `app/src/main/assets/lang/es.json`
- [ ] T045 [US4] Scénario B4 dans `outils/banc-essai.sh` (favoris intacts après retour puis reprise)
- [ ] T046 [US4] Scénario B12 (bulle affichée, déplaçable, réductible, non fermable, reprise en 3 gestes au plus) dans `outils/banc-essai.sh`

**Checkpoint**: B4 et B12 OK. Le retour à l'appli Lidl est réversible depuis le robot.

---

## Phase 7: User Story 5 - Retirer complètement Camybrith (Priority: P2)

**Goal**: retirer applis, modules et données du projet, root et accès Wi-Fi conservés

**Independent Test**: quickstart §B scénario B9

- [X] T047 [US5] Sous-commande `retrait` dans `modules/secours/camybrith` : `retour-lidl` sans redémarrage, annulation des modifications `data-model.md` Modification.portee = `retrait` (Navigation Bar et HTTP File Server seulement si confirmé), jamais `socle`, puis retrait du module de secours et de `/data/adb/camybrith/` en dernier
- [X] T048 [US5] Script `outils/retirer.sh` : proposer une sauvegarde (`outils/sauvegarde.sh recuperer` si US6 est livré), copier le registre et les bilans (`origine.sh registre`), confirmation par élément optionnel, `camybrith retrait`
- [ ] T049 [US5] Scénario B9 dans `outils/banc-essai.sh`

**Checkpoint**: B9 OK

---

## Phase 8: User Story 6 - Sauvegarder ses données et les récupérer (Priority: P2)

**Goal**: sauvegarde datée sans secrets, créée sur le robot ou depuis le Mac, récupérée sur le Mac

**Independent Test**: quickstart §B scénario B8 (partie sauvegarde et récupération) ; §C étape 7

- [X] T050 [P] [US6] Manifeste de sauvegarde dans `core/src/main/kotlin/app/toque/core/backup/Manifest.kt` : `data-model.md` Sauvegarde.format, Sauvegarde.createdAt, Sauvegarde.app.versionName, Sauvegarde.app.versionCode, Sauvegarde.pilot.versionCode, Sauvegarde.robot, Sauvegarde.sections, Sauvegarde.summary, Sauvegarde.files, Sauvegarde.secretsIncluded
- [X] T051 [P] [US6] Filtre des secrets dans `core/src/main/kotlin/app/toque/core/backup/Secrets.kt` (`data-model.md` Sauvegarde › Clés secrètes exclues)
- [X] T052 [US6] Tests JUnit de T050 et T051 dans `core/src/test/kotlin/app/toque/core/BackupTest.kt` (aucune valeur de `mqttPassword` en sortie, clé inconnue exclue)
- [X] T053 [US6] Export dans `app/src/main/kotlin/app/toque/backup/BackupWriter.kt` : sections de `data-model.md` Sauvegarde.sections depuis `recettes.db` (favoris ; recettes de source IMPORTED et USER uniquement), `toque.xml` filtré (T051) et `pilote.xml` ; zip en flux, empreintes SHA-256 pour Sauvegarde.files, empreinte anonymisée pour Sauvegarde.robot ; vérification d'espace avant de commencer (`Space.kt`, FR-019 : refus clair et proposition de supprimer les plus anciennes) ; rangement dans `/data/adb/camybrith/sauvegardes/` et conservation des 5 dernières
- [X] T054 [US6] Sous-commande `sauvegarder` dans `modules/secours/camybrith` (déclenche l'export de l'interface, refus code 1 si elle est absente)
- [X] T055 [US6] Section « Sauvegardes » dans `app/src/main/kotlin/app/toque/ui/screens/SettingsScreen.kt` : espace libre et alerte sous 10 % (FR-020), Sauvegarder maintenant (pendant une cuisson : proposer à la fin), liste, Supprimer (`contracts/ui.md`)
- [X] T056 [US6] Script `outils/sauvegarde.sh` : `creer`, `lister`, `recuperer [nom]` vers `sauvegardes/`
- [X] T057 [P] [US6] Textes de T055 dans `app/src/main/assets/lang/en.json` et `app/src/main/assets/lang/es.json`
- [ ] T058 [US6] Partie sauvegarde et récupération du scénario B8 dans `outils/banc-essai.sh` : sauvegarde chronométrée (échec au-delà de 30 s, SC-006) et recherche de la valeur secrète dans tout le fichier

**Checkpoint**: une sauvegarde se crée et se récupère, sans secret

---

## Phase 9: User Story 7 - Restaurer ses données (Priority: P3)

**Goal**: restauration après résumé et confirmation, tout ou rien

**Independent Test**: quickstart §B scénario B8 (partie restauration) ; §C étape 7

- [X] T059 [P] [US7] Validation dans `core/src/main/kotlin/app/toque/core/backup/Validation.kt` : `data-model.md` Sauvegarde.format connu, Sauvegarde.files complet avec empreintes exactes et aucun fichier en trop, Sauvegarde.app.versionCode ≤ version installée (via `core/update/Releases.kt`), Sauvegarde.robot différent → avertissement
- [X] T060 [P] [US7] Conversions de format en mémoire dans `core/src/main/kotlin/app/toque/core/backup/Migrations.kt` (`data-model.md` Sauvegarde.format), avec une conversion d'exemple pour les tests
- [X] T061 [US7] Tests JUnit de T059 et T060 dans `core/src/test/kotlin/app/toque/core/BackupTest.kt` (fichier modifié d'un octet refusé, version plus récente refusée, autre robot averti)
- [X] T062 [US7] Lecture dans `app/src/main/kotlin/app/toque/backup/BackupReader.kt` : validation, résumé de ce qui sera remplacé, secrets à ressaisir, vérification d'espace (taille de la sauvegarde + marge, `Space.kt`)
- [X] T063 [US7] Restauration logique dans `app/src/main/kotlin/app/toque/backup/BackupRestorer.kt` selon research.md D15 : refus pendant une cuisson ; sauvegarde automatique de l'état actuel ; fichier `data-model.md` Restauration en cours ; favoris et recettes IMPORTED/USER remplacés dans `recettes.db` en une transaction SQLite ; réglages non secrets ensuite ; reprise idempotente à l'ouverture de l'appli si le fichier existe ; aucune copie de dossier
- [X] T064 [US7] Écran de restauration dans `app/src/main/kotlin/app/toque/ui/screens/SettingsScreen.kt` (`contracts/ui.md` › Restaurer)
- [X] T065 [US7] `envoyer FICHIER` et `restaurer NOM` dans `outils/sauvegarde.sh` (la restauration est déclenchée dans l'interface, qui doit être ouverte)
- [X] T066 [P] [US7] Textes de T064 dans `app/src/main/assets/lang/en.json` et `app/src/main/assets/lang/es.json`
- [ ] T067 [US7] Partie restauration du scénario B8 dans `outils/banc-essai.sh` (données revenues ; sauvegarde abîmée refusée sans rien modifier)
- [ ] T068 [US7] Coupure pendant une restauration (entre la transaction et les réglages) dans `outils/banc-essai.sh` : à la réouverture, restauration rejouée, données complètes

**Checkpoint**: B8 complet OK

---

## Phase 10: Polish & Cross-Cutting Concerns

- [ ] T069 Écran en lecture seule « Modifications du robot » (liste du registre) dans `app/src/main/kotlin/app/toque/ui/screens/SettingsScreen.kt` et ses textes dans `app/src/main/assets/lang/en.json` et `es.json` (`contracts/ui.md`)
- [ ] T070 [P] Réécrire la section 12 « Revenir en arrière » et ajouter l'installation du module de secours après l'étape 8 dans `docs/GUIDE.md` et `docs/GUIDE.en.md`
- [ ] T071 [P] Mettre à jour `assistant/camybrith-installer/SKILL.md` (installer le module de secours ; utiliser `outils/origine.sh` pour revenir à l'appli Lidl)
- [ ] T072 [P] Entrée dans `CHANGELOG.md` (français et anglais) et paragraphe dans `README.md`, `README.en.md`, `README.es.md`
- [ ] T073 Relire tous les chemins d'écriture de `modules/secours/` et des scripts `outils/` : uniquement ceux de `contracts/cli.md` (principe VII) ; vérifier que `modules/secours/post-fs-data.sh` respecte les règles de research.md D13 ; consigner le résultat dans `specs/007-retour-lidl-sauvegarde/checklists/requirements.md`
- [ ] T074 Scénario B14 (mémoire de l'émulateur remplie à 100 % : retour à l'appli Lidl, geste, démarrage, bilan `memoirePleine`, sauvegarde refusée, alerte d'espace) dans `outils/banc-essai.sh` (SC-009)
- [ ] T075 Dérouler `bash outils/banc-essai.sh` en entier (B1 à B14), puis `quickstart.md` §C sur le robot dès qu'il est rooté

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (1)** → **Foundational (2)** → parcours.
- **US1 (3)** : dépend de la phase 2. MVP.
- **US2 (4)** : dépend de US1 (`retour-lidl`).
- **US3 (5)** : dépend de US1 (`retour-lidl --secours`, reprise dans `service.sh`).
- **US4 (6)** : dépend de US1 ; complète le bandeau de US3 s'il est livré, sinon le crée.
- **US5 (7)** : dépend de US1 ; utilise US6 s'il est livré (proposition de sauvegarde).
- **US6 (8)** : dépend seulement de la phase 2.
- **US7 (9)** : dépend de US6.
- **Polish (10)** : après les parcours voulus.

### Within Each User Story

- Règles `core/` et tests avant le code qui les utilise.
- Commande `camybrith` avant les scripts `outils/` et les écrans qui l'appellent.
- Scénario du banc d'essai en fin de parcours ; traductions en dernier.

### Parallel Opportunities

- Phase 1 : T002 à T006.
- Phase 2 : T007 et T008 ; T018 et T019 en parallèle de T011 à T014 (T015 touche `MainActivity.kt`).
- US3 : T030 en parallèle de US2.
- US6 peut avancer en parallèle de US1 à US5 (autres fichiers sauf `SettingsScreen.kt` et `camybrith`).
- US7 : T059 et T060.
- Polish : T070 à T072.

---

## Parallel Example: Phase 2

```bash
Task: "Modèle et règles du registre dans core/src/main/kotlin/app/toque/core/backup/Registry.kt"
Task: "Journal d'opération et bilan dans core/src/main/kotlin/app/toque/core/backup/Operation.kt"
Task: "Script outils/secours.sh (installer, retirer, etat)"
```

## Parallel Example: User Story 6

```bash
Task: "Manifeste de sauvegarde dans core/src/main/kotlin/app/toque/core/backup/Manifest.kt"
Task: "Filtre des secrets dans core/src/main/kotlin/app/toque/core/backup/Secrets.kt"
```

---

## Implementation Strategy

### MVP First (User Story 1)

1. Phases 1 et 2 : registre, journal, module de base, banc d'essai ; tout se valide sans le robot.
2. Phase 3 (US1) : retour à l'appli Lidl depuis le robot.
3. **STOP et VALIDER** : B1, B2, B3, B5 sur l'émulateur.
4. Puis US2 et US3 pour compléter le filet (P1), avant toute publication.

### Incremental Delivery

1. US1 + US2 + US3 → publication « filet de sécurité » (validation §C sur le robot).
2. US4 → reprise.
3. US6 puis US7 → sauvegardes.
4. US5 → retrait complet.
5. Polish (guides, SKILL, CHANGELOG) à chaque publication.

### Prérequis

JDK 17 et image d'émulateur sur le Mac pour tout ce qui précède la validation §C. Robot rooté
(guide, étapes 1 à 8), sauvegarde complète de l'étape 2 en main, pour §C.

---

## Notes

- [P] = fichiers différents, pas de dépendance sur une tâche non terminée.
- Toute commande du module ou script `outils/` qui rencontre un état inattendu s'arrête (code 2) et ne fait rien de plus (principe VII).
- Commiter après chaque tâche ou groupe logique ; ne jamais commiter `sauvegardes/`, `outils/robot.conf`, `modules/adb-wifi/adb_keys`, d'APK, ni les photos du robot.
