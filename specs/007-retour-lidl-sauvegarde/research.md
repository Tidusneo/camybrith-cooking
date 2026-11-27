# Research: Retour au fonctionnement Lidl et sauvegarde des données

**Feature**: 007-retour-lidl-sauvegarde | **Date**: 2026-11-27

Code étudié : `app/.../recipes/SyncService.kt` (`BootReceiver`), `app/.../recipes/LidlSync.kt`
(`components`, `setVendorHardware`), `core/.../machine/RobotModel.kt`,
`app/.../ui/screens/SettingsScreen.kt` (`prepareHardware`, `restoreVendor`),
`app/.../ui/MainActivity.kt` (`openVendorApp`), `app/.../Prefs.kt`, `app/.../recipes/RecipeRepo.kt`,
`app/src/main/AndroidManifest.xml`, `pilote/src/main/AndroidManifest.xml`, `outils/*.sh`,
`modules/*`, `docs/GUIDE.md` (étapes 1.4, 5, 7, 12).

## Ce que Camybrith modifie aujourd'hui sur le robot

| Modification | Qui la fait | Effet sur l'appli Lidl |
|---|---|---|
| Service moteur de l'appli Lidl désactivé (`pm disable <vendorPackage>/<…hardwareServiceSuffix>`) | `LidlSync.setVendorHardware(false)` au passage en Machine réelle et à chaque synchro | **bloquant** : l'appli Lidl ne commande plus la machine |
| Lancement de Camybrith au démarrage et 20 s après (`BootReceiver`, si `Prefs.bootLaunch`) ; démarrage du pilote | `BootReceiver` | **bloquant** : Camybrith passe devant l'appli Lidl et prend la machine |
| Camybrith déclaré écran d'accueil (`HOME` sur `MainActivity`) | manifeste | gênant (choix d'écran d'accueil) |
| Service d'accessibilité de Navigation Bar activé ou retiré (`settings put secure …`) | `outils/barre.sh` | aucun |
| Module Magisk d'animation de démarrage | `outils/animation.sh` | aucun (esthétique) |
| Applis Camybrith (interface, pilote) | `outils/installer.sh` | aucun tant que les deux points bloquants sont annulés |
| Magisk, Navigation Bar, HTTP File Server, options développeur, module ADB Wi-Fi | guide, étapes 5 à 7 | aucun ; ils forment le **socle** (root et accès Wi-Fi) que l'utilisateur garde |

Le pilote (`app.toque.pilote`) n'a **aucun** démarrage automatique : seul `BootReceiver` le lance.
Le « Revenir à l'appli d'origine » actuel (`restoreVendor`) fait déjà l'essentiel (Démo,
`bootLaunch = false`, service Lidl réactivé, lancement de l'appli Lidl) mais seulement depuis
l'interface, sans trace, sans vérification, et pas si l'interface est cassée.

## Décisions

### D1. Le registre des modifications, et qui l'écrit

- **Decision**: un fichier `/data/adb/camybrith/registre.json` (root, 700), écrit **uniquement**
  par la commande `camybrith noter …` du module de secours. L'interface (par `RootShell`), les
  scripts `outils/` (par `adb shell su -c`) et le module lui-même passent tous par elle, **avant**
  de faire la modification.
- **Rationale**: un seul écrivain → écritures atomiques (fichier temporaire puis renommage) et
  format garanti ; `/data/adb` survit à la désinstallation des applis (research de la version
  précédente de cette spec, toujours valable).
- **Alternatives considered**: registre dans les données de l'appli (perdu à la désinstallation,
  illisible si l'appli est cassée) ; un registre par composant (pas d'ordre global pour annuler).

### D2. Types de modifications et portée

- **Decision**: liste fermée de types, chacun avec son annulation :
  `composant` (activer/désactiver un composant d'appli → remettre l'état d'avant),
  `reglage` (réglage Android `namespace/clé` → remettre la valeur d'avant ou la supprimer),
  `demarrage` (composant de Camybrith qui le lance au démarrage ou en fait l'écran d'accueil →
  le désactiver), `module` (module Magisk installé → le retirer), `appli` (appli installée → la
  désinstaller). Chaque modification a une **portée** :
  - `lidl` : annulée par le retour à l'appli Lidl (service moteur Lidl, démarrage de Camybrith) ;
  - `retrait` : annulée seulement par le retrait complet (applis Camybrith, animation, Navigation
    Bar et HTTP File Server avec confirmation, réglage d'accessibilité) ;
  - `socle` : jamais annulée par ces commandes (Magisk, ADB Wi-Fi, options développeur), car le
    root et l'accès Wi-Fi restent (clarification de la spec).
- **Rationale**: le retour à l'appli Lidl doit être court et sûr ; on ne défait que ce qui empêche
  l'appli Lidl de fonctionner, et on garde Camybrith installé pour pouvoir le reprendre (US4).
- **Alternatives considered**: tout défaire à chaque retour (plus long, rend la reprise lourde) ;
  types libres avec commande shell d'annulation stockée (dangereux : le registre deviendrait un
  vecteur d'exécution arbitraire).

### D3. Empêcher Camybrith de reprendre la main sans toucher à ses données

- **Decision**: le retour désactive deux composants de Camybrith par `pm disable` (inscrits en
  portée `lidl` comme le reste) : le récepteur `BootReceiver` et l'alias d'écran d'accueil
  `.ui.Accueil` (le filtre `HOME` passe de `MainActivity` à cet alias). L'icône (`LAUNCHER` sur
  `MainActivity`) reste, pour la reprise. Puis `am force-stop` de l'interface et du pilote.
- **Rationale**: marche même si l'interface ne démarre plus (pas besoin de modifier ses
  préférences) ; entièrement réversible ; aucune donnée touchée (US4 : données intactes).
- **Alternatives considered**: modifier `toque.xml` depuis le shell (fragile, format interne) ;
  désactiver toute l'appli (`pm disable-user`, empêche la reprise depuis l'icône).

### D4. Une seule implémentation du retour : le module de secours

- **Decision**: module Magisk `modules/secours/` (aucun dossier `system/`) avec la commande
  `camybrith` (sous-commandes `etat`, `noter`, `retour-lidl`, `reprendre`, `bilan`, `inscrire`,
  `sauvegarder`) et `service.sh`. Le bouton de l'interface, le script Mac et
  le mode de secours appellent tous `camybrith retour-lidl`.
- **Rationale**: une seule logique testée ; marche sans l'interface (US2, US3).

### D5. Déroulé du retour et reprise après coupure

- **Decision**: `retour-lidl` : refus si cuisson en cours ; journal d'opération
  (`op.json`, étapes `debut → arret → annulation → verification → termine`) ; arrêt de l'interface
  et du pilote ; annulation des modifications de portée `lidl` dans l'ordre inverse (une
  annulation qui échoue est notée et n'arrête pas les autres) ; vérification finale ; bilan ;
  redémarrage. Au démarrage, `service.sh` reprend un journal non terminé **dans le même sens**
  (finir le retour), car l'objectif sûr est l'appli Lidl.
- **Rationale**: chaque annulation est idempotente (remettre un état), donc rejouer est sans
  risque ; finir vers l'appli Lidl est toujours le choix le plus sûr (principe VII).

### D6. Vérification finale

- **Decision**: le retour est « réussi » si : le service moteur Lidl est activé ; l'appli Lidl a
  une activité de lancement ; l'intention d'écran d'accueil (`HOME`) se résout vers l'appli Lidl
  (`cmd package resolve-activity` ; si elle n'en déclare pas, c'est noté dans le bilan et
  `service.sh` l'ouvre au démarrage à la place de Camybrith) ; `BootReceiver` et `.ui.Accueil` de Camybrith sont désactivés ; aucun
  processus `app.toque` ni `app.toque.pilote` ne tourne. Après le redémarrage, `service.sh` vérifie
  en plus que l'appli Lidl est au premier plan et l'inscrit dans le bilan.
- Les noms du paquet Lidl et du suffixe du service moteur viennent de `RobotModel` ; l'interface
  les écrit dans `/data/adb/camybrith/robot.json` (lu par le module), pour ne pas les dupliquer
  en shell.

### D7. Mode de secours

- **Decision**: `service.sh` compte pendant 120 s après `sys.boot_completed` les plantages de
  `app.toque` dans `logcat -b crash` ; à 3, `camybrith retour-lidl --secours` (pose
  `secours.json`). La règle de décision est une fonction pure dans `core` (testée avec des journaux
  d'exemple) ; le shell n'en est qu'une transcription courte, couverte par le banc d'essai.
- **Rationale**: inchangé par rapport à l'étude précédente ; seul le résultat (retour à l'appli
  Lidl) change.

### D8. Inscription rétroactive

- **Decision**: `camybrith inscrire` (lancé à l'installation du module) relève l'état actuel et
  inscrit les modifications déjà présentes avec `deduit: true` : service moteur Lidl désactivé
  (avant : activé), démarrage de Camybrith (`BootReceiver` et alias d'accueil, actifs dès
  l'installation puisque `bootLaunch` vaut vrai par défaut ; portée `lidl`), applis Camybrith, Navigation Bar et HTTP File Server installées, réglage
  d'accessibilité (avant : vide), module d'animation, plus le socle (Magisk, ADB Wi-Fi).
- **Rationale**: les installations faites avant cette version n'ont pas de registre (hypothèse de
  la spec).

### D12. Savoir si une cuisson est en cours

- **Decision**: l'interface écrit `/data/adb/camybrith/cuisson.json` (`occupe`, `programme`,
  `maj`) à chaque démarrage, pause et fin de programme, et le rafraîchit toutes les 30 s pendant
  un programme. Le module refuse une opération si `occupe` est vrai et `maj` a moins de 60 s.
  L'état n'est « inconnu » que si le processus du pilote tourne **et** que le fichier est périmé
  (interface plantée) : c'est le seul cas où la commande Mac demande confirmation ; le mode de
  secours et le geste de l'interrupteur continuent (arrêter est sûr). Après un redémarrage, le
  pilote ne tourne pas tant que Camybrith ne l'a pas lancé : pas de cuisson, aucune question.
- **Rationale**: en Machine réelle, le pilote tourne en permanence, même au repos : « pilote
  actif » ne veut pas dire « cuisson en cours ». L'interface connaît déjà cet état
  (`snapshot.busy`) ; aucun code n'est ajouté au pilote (principe II). Arrêter le pilote
  pendant une cuisson n'est pas dangereux : la carte coupe le moteur et la chauffe environ 4 s
  après la dernière commande (principe I : on n'arrête jamais de façon dangereuse, on ne démarre
  jamais seul).
- **Alternatives considered**: « pilote actif » (bloquerait tout retour en Machine réelle) ;
  état publié par le pilote (code ajouté au pilote) ; lecture du port série par le module
  (conflit avec le pilote).

### D13. Geste de secours à l'interrupteur

- **Decision**: compteur de démarrages courts, sans horloge. `post-fs-data.sh` du module
  (exécuté tôt à chaque démarrage) incrémente `demarrages-courts` et force l'écriture sur disque
  (`sync`). `service.sh` le remet à zéro quand le robot est resté allumé 2 minutes après
  `sys.boot_completed`. Si le compteur atteint 3 au démarrage, et que le robot n'est pas déjà en
  mode Lidl, `service.sh` lance `camybrith retour-lidl --geste` (bilan marqué « geste ») et remet
  le compteur à zéro. Le geste ne va jamais vers Camybrith.
- **Rationale**: marche quand l'écran est figé et sans ordinateur ; ne dépend pas de l'heure (qui
  peut être fausse au démarrage, avant la synchronisation réseau). Un faux déclenchement (coupures
  rapprochées) ne coûte qu'un passage sur l'appli Lidl, réversible par la bulle.
- **Alternatives considered**: horodatages des démarrages (heure non fiable tôt au démarrage) ;
  geste à la molette (géré par l'appli, donc inutilisable si elle est figée) ; geste à double sens
  (on ne saurait pas dans quel mode on tombe, et un Camybrith figé reviendrait).
- **Règles pour `post-fs-data.sh`** (le démarrage l'attend, principe VII) : il ne fait que lire,
  incrémenter et réécrire le compteur, puis `sync` ; pas de boucle, pas d'attente, pas de réseau,
  pas d'appel à `pm` ou `am` ; toute erreur est ignorée (`exit 0` dans tous les cas) ; un compteur
  absent ou illisible repart à 0. Tout le reste se fait dans `service.sh`, qui ne bloque pas le
  démarrage.
- **À vérifier**: durée réelle d'un démarrage du Smart (le délai de 2 minutes compte à partir de
  `sys.boot_completed`, donc ne dépend pas de la durée du démarrage) ; texte du guide pour
  expliquer le geste simplement (« allumer, attendre le logo, éteindre ; trois fois »).

### D14. Bulle de retour vers Camybrith en mode Lidl

- **Decision**: réutiliser `ReturnBubble` (déjà déplaçable, déjà affichée par-dessus l'appli Lidl
  quand on l'ouvre depuis Camybrith). En mode Lidl, `service.sh` la démarre à chaque démarrage par
  `am startservice` en root avec l'extra `modeLidl`. Dans ce mode, la bulle ne peut être que
  déplacée ou réduite à un onglet sur le bord, jamais fermée ; la toucher ouvre `MainActivity`
  (icône restée active), qui affiche le bandeau de reprise.
- **Rationale**: l'appli Lidl n'a pas de liste d'applis et le bouton Accueil y ramène ; la bulle
  est le seul accès simple à Camybrith depuis le robot. Interdire de la fermer évite de se
  retrouver sans chemin de retour sans ordinateur.
- **À vérifier**: qu'un démarrage en root (`am startservice` lancé par `su`) accepte un service
  non exporté sur Android 8.1 ; sinon, ajouter à Camybrith un récepteur protégé par une
  permission de signature, activé seulement en mode Lidl (inscrit au registre).
- **Alternatives considered**: icône dans la barre de navigation (Navigation Bar n'offre pas de
  raccourci vers une appli) ; passer par les réglages Android (trop long, menu usine).

### D15. Mémoire pleine

- **Ordres de grandeur** : registre, bilans, journal, `mode.json`, `cuisson.json`, compteur : au
  total moins de 100 Ko. Une sauvegarde : quelques centaines de Ko à quelques Mo (pas de photos ni
  de recettes Lidl) ; 5 au plus. L'espace réel du robot sera mesuré une fois rooté.
- **Decision**:
  1. **Réserve** : `/data/adb/camybrith/reserve` (2 Mo) créée à l'installation du module et
     recréée par `service.sh` quand il y a de la place. Toute écriture du module qui échoue faute
     de place supprime la réserve puis réessaie une fois.
  2. **Retour à l'appli Lidl, ordre des actions** : `am force-stop`, `pm enable/disable` (pas
     d'écriture dans les fichiers de l'utilisateur) **avant** journal, bilan et `mode.json`. Si
     même la réserve ne suffit pas, le retour se termine sans journal ; le bilan (écrit plus tard
     si possible) porte « mémoire pleine ». Ce cas ne déclenche jamais le code 2 « état
     inattendu » : il est attendu.
  3. **Restauration sans copie** : la restauration est logique et faite par l'interface, pas par
     le module : favoris et recettes perso remplacés dans `recettes.db` en **une transaction
     SQLite** (tout ou rien par construction), réglages écrits ensuite. Avant de commencer,
     l'état actuel est exporté en une petite sauvegarde automatique (même format, quelques Mo au
     plus). Un fichier `restauration-en-cours` dans les données de l'appli permet de rejouer la
     restauration au prochain démarrage si une coupure survient entre les deux étapes. Aucun
     dossier n'est copié.
  4. **Avant une sauvegarde** : espace libre ≥ taille estimée + 50 Mo, sinon refus et
     proposition de supprimer les plus anciennes (FR-019).
  5. **Alerte** : espace libre de `/data` (`df`) affiché dans Réglages et `camybrith etat` ;
     alerte sous 10 % (FR-020). Seuils dans `core` (fonction pure testée).
  6. **Banc d'essai** : scénario B14, mémoire de l'émulateur remplie à 100 %.
- **Rationale**: le filet de sécurité doit marcher précisément dans les situations dégradées
  (principe VII) ; un Android dont `/data` est plein devient instable pour toutes les applis, mais
  les actions de retour (`pm`, `am`) passent par le système et restent possibles.
- **Alternatives considered**: refuser le retour si la mémoire est pleine (contraire au principe
  VII) ; copie complète du dossier de l'appli avant restauration (centaines de Mo à cause des
  photos) ; restauration par remplacement de dossiers depuis le module (copie, `chown`,
  `restorecon` : plus risqué et plus gourmand).

### D9. Le rappel « pas de mise à jour système Lidl »

- **Decision**: affiché sur l'écran du robot avant le redémarrage (retour depuis l'interface),
  imprimé par la commande Mac, et inscrit dans le bilan. L'appli Lidl n'est jamais modifiée pour
  l'afficher (FR-009 précisé en conséquence).

### D10. Sauvegarde des données

- **Decision**: inchangée par rapport à l'étude précédente : fichier `.camybrith` (zip) avec
  manifeste et données logiques JSON (favoris et recettes IMPORTED/USER de `recettes.db`, réglages
  de `toque.xml` filtrés, `pilote.xml`) ; liste fermée de clés secrètes (`mqttPassword`, futurs
  jetons), clé inconnue exclue ; empreintes SHA-256 ; 5 sauvegardes conservées dans
  `/data/adb/camybrith/sauvegardes/` ; empreinte anonymisée du robot.

### D11. Banc d'essai sur émulateur

- **Decision**: `outils/banc-essai.sh` crée un émulateur Android 8.1 (API 27, image `default`
  sans Play Store, donc `adb root` possible), y installe une **fausse appli Lidl** (nouveau module
  Gradle `banc/fausse-lidl` : même nom de paquet que l'appli Smart, une activité de lancement et un
  service dont le nom finit par le suffixe moteur), Camybrith en mode Démo, le module de secours
  copié dans `/data/adb/modules/`, et une doublure de la commande `magisk`. Il déroule
  automatiquement les scénarios du quickstart (§B) et affiche OK/ÉCHEC par scénario.
- **Rationale**: le robot de l'utilisateur n'est pas encore rooté ; tout le cœur de la
  fonctionnalité (registre, retour, reprise, secours, coupure) se teste sans lui.
- **Limites connues**: pas de vrai Magisk, pas de vrai service moteur Lidl ni port série, pas le
  processeur MediaTek. La validation finale reste sur le robot (quickstart §C).
- **À vérifier**: disponibilité d'une image `system-images;android-27;default;arm64-v8a` pour le
  Mac Apple Silicon de l'utilisateur (sinon API 28, la plus proche) ; un **JDK 17** est requis
  (absent du Mac au moment de l'étude : `sdkmanager` et Gradle en ont besoin).

### D16. Moteur de la commande en Kotlin, lancé par `app_process` (décidé à l'implémentation)

- **Decision**: la logique de `camybrith` (registre, journal, retour, vérification, reprise au
  démarrage) est écrite une seule fois en Kotlin dans `core/backup/Secours.kt`, testée en JUnit sur
  un faux Android (`SecoursTest`). Le module Magisk contient `camybrith.apk` (module Gradle
  `moteur-secours`, jamais installé) et un lanceur shell de quelques lignes :
  `CLASSPATH=camybrith.apk app_process /system/bin app.toque.core.backup.SecoursMain`.
- **Rationale**: le shell d'Android 8.1 (toybox) n'a aucun outil pour lire ou écrire du JSON ; une
  transcription en shell du registre (fusion, ordre d'annulation, validation des cibles) aurait été
  fragile et non testable (principe III). `app_process` ne dépend pas de l'interface : le moteur
  marche si elle est cassée ou désinstallée (US2, US3). Validé sur l'émulateur Android 8.1.
- **Précisions apportées en même temps** (principe VII) :
  - **Journal d'abord, sans bloquer** : le journal est écrit avant `am force-stop` et les `pm`,
    mais une écriture impossible (mémoire pleine) n'arrête jamais le retour (D15). Sans journal
    écrit avant les `pm`, une coupure pendant les annulations laisserait le robot à moitié revenu.
  - **Écritures forcées sur le disque** (`fsync` avant renommage) et **redémarrage propre**
    (`svc power reboot`, qui laisse Android écrire l'état des composants ; `reboot` en secours).
  - **Garanties hors registre** : si une modification essentielle manque au registre (module
    installé après coup, registre illisible), le retour remet quand même le service moteur Lidl et
    désactive `BootReceiver` et `.ui.Accueil` ; c'est noté dans le bilan (`corrections`).
  - **En mode Lidl, revérification à chaque démarrage** (`service.sh`) : une coupure juste après un
    retour peut faire perdre à Android l'état des composants ; il est remis et noté
    (`corrections-demarrage.json`).
  - **Reprise d'un retour interrompu sans redémarrer** : au démarrage, le retour est terminé puis
    l'appli Lidl est ouverte directement (pas de risque de boucle de redémarrages).
  - **Module absent** : le premier passage en Machine réelle est refusé (il ne serait pas inscrit) ;
    mais la recoupure de l'appli Lidl après une session Lidl se fait toujours, car la laisser sur le
    port série pendant que Camybrith pilote serait dangereux (principe I).
  - **`cuisson.json` et `robot.json`** sont écrits directement par l'interface (`su`, écriture
    atomique), sans lancer le moteur : toutes les 30 s pendant un programme, il faut rester léger.
- **Alternatives considered**: tout en shell (voir plus haut) ; moteur dans l'interface (inutilisable
  quand elle est cassée) ; binaire natif (chaîne de compilation en plus, pas de tests JUnit).

