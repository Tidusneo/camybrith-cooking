# Implementation Plan: Retour au fonctionnement Lidl et sauvegarde des données

**Branch**: `007-retour-lidl-sauvegarde` | **Date**: 2026-11-27 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/007-retour-lidl-sauvegarde/spec.md`

## Summary

Garantir qu'on peut toujours remettre le robot en fonctionnement avec la seule appli Lidl (root
conservé), depuis le robot, depuis le Mac, ou automatiquement si Camybrith plante, puis reprendre
Camybrith ; et sauvegarder/restaurer les données à la demande.

Approche ([research.md](research.md)) : un **registre des modifications** tenu par un **module
Magisk de secours** (sans superposition système) ; tout ce qui modifie le robot s'y inscrit avant
d'agir ; le **retour à l'appli Lidl** annule les modifications de portée `lidl` (service moteur
Lidl, démarrage de Camybrith) puis vérifie et redémarre ; la **reprise** les réapplique ; le
**mode de secours** déclenche le retour après 3 plantages, et le **geste de l'interrupteur**
(3 démarrages courts) le déclenche sans écran ni ordinateur ; en mode Lidl, une **bulle
« Camybrith »** permet de revenir sur Camybrith depuis le robot. Les règles sont en Kotlin pur dans
`core/` et testées ; un **banc d'essai sur émulateur** valide tout sans toucher au robot.

## Technical Context

**Language/Version**: Kotlin (JDK 17 pour la compilation, cible Android API 23–28) ; shell
`/system/bin/sh` (toybox, Android 8.1) pour le module ; bash pour `outils/`.

**Primary Dependencies**: aucune nouvelle dans les applis. `core/json/Json.kt`, `RootShell`,
Magisk (déjà requis). Banc d'essai : SDK Android (émulateur, `avdmanager`, `sdkmanager`).

**Storage**: `/data/adb/camybrith/` (registre, journal, bilans, `robot.json`, secours,
sauvegardes) ; lecture de `recettes.db` et des `shared_prefs` existants.

**Testing**: JUnit (`core/src/test/kotlin/app/toque/core/`) ; banc d'essai émulateur
(`outils/banc-essai.sh`, quickstart §B) ; `shellcheck` en CI ; validation finale sur le robot
(quickstart §C).

**Target Platform**: Monsieur Cuisine Smart (Android 8.1, rooté Magisk 31) ; Connect best effort ;
Mac, Linux, WSL pour `outils/`.

**Project Type**: appli Android (interface + pilote) + module Magisk + scripts + module Gradle de
test (`banc/fausse-lidl`).

**Performance Goals**: retour à l'appli Lidl < 3 min redémarrage compris (SC-001) ; sauvegarde
< 30 s.

**Constraints**: aucune écriture sur une partition système ; root et accès Wi-Fi conservés ;
appli Lidl jamais modifiée ; refus pendant une cuisson ; reprise après coupure ; pas de commande
libre dans le registre.

**Scale/Scope**: une quinzaine de modifications par robot ; 10 bilans ; 5 sauvegardes.

**Prérequis poste de développement** : JDK 17 absent du Mac de l'utilisateur au moment de
l'étude (nécessaire pour Gradle et `sdkmanager`) ; image d'émulateur Android 8.1 à installer.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principe | Vérification | État |
|---|---|---|
| I. Sécurité de cuisson | Retour, reprise, retrait et restauration refusés pendant une cuisson, connue par `cuisson.json` écrit par l'interface (research D12) ; si l'interface est morte, arrêt du pilote seulement après confirmation (Mac) ou en secours, ce qui ne fait qu'arrêter la machine ; le retour arrête le pilote avant de rendre la machine à l'appli Lidl ; rien ne démarre moteur ou chauffe. | ✅ |
| II. Pilote isolé | Aucun code ajouté au pilote ; il est seulement arrêté. | ✅ |
| III. Cœur testable | Registre (ordre, fusion, validation des cibles), journal, règle de secours, sauvegardes dans `core/` avec JUnit. | ✅ |
| IV. Local d'abord | Tout est local ; aucun secret dans une sauvegarde ; registre et bilans copiables sur le Mac à la demande. | ✅ |
| V. Sobriété | Opérations courtes, hors cuisson ; zip en flux ; pas de photos dans les sauvegardes. | ✅ |
| VI. Accessible et réversible | Écrans traduits ; scripts dans le style existant ; reprise de Camybrith ; guides à compléter. | ✅ |
| VII. Ne jamais briquer, toujours revenir à l'appli Lidl | Registre avant toute modification ; retour depuis le robot, le Mac et en secours ; aucune partition touchée ; module retirable ; arrêt sur état inattendu (code 2) ; appli Lidl jamais modifiée. | ✅ |

**Re-check après la phase 1** : ✅. Écritures limitées à la liste du contrat
([contracts/cli.md](contracts/cli.md)).

## Project Structure

### Documentation (this feature)

```text
specs/007-retour-lidl-sauvegarde/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── cli.md
│   └── ui.md
└── tasks.md             # /speckit-tasks
```

### Source Code (repository root)

```text
core/src/main/kotlin/app/toque/core/backup/
├── Registry.kt          # Modification, Registre : validation des cibles, fusion, plan d'annulation
├── Operation.kt         # journal d'opération et transitions
├── RescueRule.kt        # décision du mode de secours
├── Space.kt             # seuils d'espace (alerte 10 %, sauvegarde + 50 Mo)
├── Manifest.kt          # manifeste de sauvegarde
├── Secrets.kt           # clés secrètes (liste fermée)
├── Validation.kt        # vérification d'une sauvegarde
└── Migrations.kt        # conversions de format
core/src/test/kotlin/app/toque/core/
├── RegistryTest.kt
├── RescueRuleTest.kt
└── BackupTest.kt

app/src/main/kotlin/app/toque/backup/
├── Rescue.kt            # appels à `camybrith` (RootShell), écriture de robot.json
├── BackupWriter.kt
├── BackupReader.kt
└── BackupRestorer.kt    # restauration logique en transaction (research D15)
app/src/main/kotlin/app/toque/recipes/LidlSync.kt         # passe par `camybrith noter` avant pm disable/enable
app/src/main/kotlin/app/toque/ui/screens/SettingsScreen.kt  # Revenir à l'appli Lidl, Sauvegardes, Modifications
app/src/main/kotlin/app/toque/ui/MainActivity.kt          # bandeau après retour / secours / geste
app/src/main/kotlin/app/toque/ui/ReturnBubble.kt          # mode Lidl : bulle non fermable
app/src/main/AndroidManifest.xml                          # HOME sur l'alias .ui.Accueil
app/src/main/assets/lang/{en,es}.json

modules/secours/          # module Magisk, sans dossier system/
├── META-INF/com/google/android/{update-binary,updater-script}
├── module.prop          # id=camybrith_secours
├── post-fs-data.sh      # compteur du geste de l'interrupteur
├── service.sh           # reprise d'opération, mode de secours, geste, bulle, contrôle après retour
└── camybrith            # commande (contracts/cli.md)

banc/fausse-lidl/         # module Gradle de test : fausse appli Lidl pour l'émulateur
outils/
├── secours.sh  origine.sh  retirer.sh  sauvegarde.sh  banc-essai.sh   # nouveaux
└── installer.sh  barre.sh  animation.sh                               # inscrivent au registre

settings.gradle.kts             # inclut banc:fausse-lidl
.github/workflows/release.yml   # zip du module de secours ; shellcheck
.gitignore                      # sauvegardes/
docs/GUIDE.md, docs/GUIDE.en.md # section 12 réécrite ; installation du module après l'étape 8
assistant/camybrith-installer/SKILL.md
```

**Structure Decision**: séparation existante respectée (`core/` pur, `app/`, rien dans
`pilote/`, modules Magisk dans `modules/`, scripts dans `outils/`) ; un module Gradle de test
`banc/` pour la fausse appli Lidl, jamais publiée.

## Ordre de livraison conseillé

1. `core/backup` (registre, journal, règle de secours) + tests.
2. Banc d'essai (émulateur, fausse appli Lidl, doublure `magisk`).
3. Module de secours : `etat`, `noter`, `inscrire`, puis `retour-lidl` et `reprendre` ; scénarios
   B1 à B5 et B7.
4. Branchements : `LidlSync`, alias d'accueil, écrans, `outils/origine.sh`, scripts existants.
5. Mode de secours et geste de l'interrupteur ; B6, B10, B11. Bulle et reprise ; B4, B12.
6. Sauvegardes et restauration sans copie ; B8. Retrait ; B9. Mémoire pleine ; B14.
7. Guides, CHANGELOG ; validation sur le robot (quickstart §C) dès qu'il est rooté.

## Complexity Tracking

Aucune violation de la constitution. Le module Gradle `banc/fausse-lidl` est un outil de test,
non livré aux utilisateurs.
