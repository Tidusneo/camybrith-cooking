<!--
Sync Impact Report
- Version change: 1.2.0 → 2.0.0 (MAJOR: principe VII redéfini)
- Modified principles:
  VII. Ne jamais briquer, toujours pouvoir revenir → VII. Ne jamais briquer, toujours pouvoir
  revenir à l'appli Lidl (le « retour arrière » désigne le retour au fonctionnement avec la seule
  appli Lidl, root conservé ; la règle « garder la version précédente à chaque mise à jour » est
  retirée ; ajout du registre des modifications)
- Added sections: none
- Removed sections: none
- Templates: plan-template.md reads the constitution at runtime; not modified.
- Deferred TODOs: none
-->

# Camybrith Cooking Constitution

## Core Principles

### I. La sécurité de cuisson d'abord (NON NÉGOCIABLE)

- Les limites de l'appli d'origine MUST rester appliquées : vitesse 3 au plus en chauffe et en sens
  inverse, durées maximales, conditions du Turbo, vitesse limitée par l'accessoire monté.
- Le couvercle, les erreurs et la fraîcheur de l'état de la base MUST être vérifiés **avant**
  chaque démarrage ; une base qui ne répond plus MUST tout arrêter.
- **Rien venant du réseau ne peut démarrer le moteur ou la chauffe.** Home Assistant, téléphone,
  Claude/MCP ou toute autre source distante MAY préparer un programme ou une recette, mais le
  démarrage MUST être confirmé par une action physique sur le robot (écran ou molette).
- Rien ne redémarre tout seul après une pause ; une alerte reste affichée tant qu'elle n'a pas été
  lue. Les autres écrans (domotique comprise) MUST NOT masquer une alerte de cuisson.
- Toute spec qui touche au moteur, à la chauffe ou à la balance MUST décrire son effet sur ces
  règles et les tests qui le prouvent.

Raison : c'est un appareil de 1200 W avec une lame et de l'eau bouillante, utilisé par des
non-spécialistes.

### II. Pilote moteur isolé

- La commande de la base reste dans l'appli séparée `app.toque.pilote` ; l'interface (`app.toque`)
  lui parle par l'IPC de `commun/`.
- Les nouvelles fonctionnalités (domotique, réseau, recettes à distance, IA) MUST vivre hors du
  pilote. Le pilote ne reçoit que des changements strictement nécessaires à la commande machine.
- Un plantage de l'interface MUST NOT couper une cuisson en cours.

Raison : garder la partie qui fait tourner le moteur petite, stable et facile à auditer.

### III. Cœur testable sans Android

- La logique (protocole, sécurité, recettes, conversion, recherche, MQTT) MUST être dans `core/`, en
  Kotlin pur, avec des tests JUnit.
- Tout changement de protocole MUST être vérifié contre les captures et vecteurs de
  `core/src/test/resources/` ; tout bug corrigé dans `core/` SHOULD recevoir un test.
- `./gradlew :core:test` MUST passer avant toute publication.

Raison : le robot n'est pas toujours sous la main ; les tests sont le premier filet de sécurité.

### IV. Local et hors ligne d'abord

- Cuisiner MUST marcher sans Internet, sans compte et sans abonnement.
- Toute fonction réseau (synchro Lidl, Home Assistant, téléphone, Claude) MUST être optionnelle,
  désactivée tant qu'elle n'est pas configurée, et MUST échouer proprement sans gêner la cuisine.
- Les secrets (jetons, mots de passe) restent sur le robot ou l'ordinateur de l'utilisateur ; ils
  MUST NOT apparaître dans les journaux, le dépôt ou un service tiers.
- Le robot MUST NOT accepter de connexion venant d'Internet. Un accès à distance MAY passer par
  un relais appartenant à l'utilisateur, installé hors du robot, auquel le robot se connecte de
  lui-même. Ce relais MUST imposer une authentification forte, chiffrer ses échanges et n'exposer
  que des actions conformes au principe I ; le robot MUST appliquer lui-même ces limites, sans
  faire confiance au relais.

### V. Sobriété sur la tablette du robot

- Cible : Android 8.1 (minSdk 23, targetSdk 28), tablette modeste. Les écrans SHOULD être natifs et
  légers ; un contenu web lourd (WebView) MUST être validé sur le vrai robot avant d'être adopté.
- Les deux variantes (rapide, compilée à l'installation ; prudente, jamais compilée) MUST continuer
  à fonctionner.
- La molette et l'écran tactile du robot sont les entrées de référence : toute interface MUST être
  utilisable avec elles.

### VI. Accessible et réversible

- L'installation et les mises à jour MUST rester faisables par un débutant en suivant
  `docs/GUIDE.md` ; les règles d'or du guide (Download Only, pas de mise à jour système Lidl, pas
  d'effacement du cache, sauvegarde avant écriture) MUST NOT être affaiblies.
- Revenir à l'appli d'origine MUST toujours rester possible depuis les Réglages, et l'appli Lidl
  MUST NOT être modifiée ni supprimée.
- Les textes visibles passent par l'i18n (clé = texte français) et MUST exister en français,
  anglais et espagnol ; la documentation utilisateur existe au moins en français et en anglais.

### VII. Ne jamais briquer, toujours pouvoir revenir à l'appli Lidl (NON NÉGOCIABLE)

Ce principe a le même rang que le principe I : en cas de doute, on choisit l'option la plus sûre,
même si elle est plus lente ou moins pratique (« safe first »).

- Les fonctionnalités MUST NOT écrire dans les partitions du système (preloader, boot, recovery,
  system, vendor, cache) ni modifier le système Android, en dehors des étapes documentées du guide
  d'installation. L'appli s'installe et se met à jour comme une appli Android ordinaire.
- Toute modification du robot (composant de l'appli Lidl, écran d'accueil, réglage Android, module,
  appli, démarrage automatique) MUST être inscrite dans le registre des modifications, avec la
  valeur d'avant et l'action qui l'annule, **avant** d'être faite.
- Le robot MUST toujours pouvoir revenir à un fonctionnement avec la seule appli Lidl, depuis le
  robot et depuis l'ordinateur, même si Camybrith ne démarre plus. Le root et l'accès par le Wi-Fi
  MAY rester.
- Après des plantages répétés de Camybrith au démarrage, un mode de secours MUST faire ce retour
  à l'appli Lidl sans intervention.
- Les modules Magisk du projet MUST pouvoir être retirés sans laisser de trace qui empêche le
  démarrage ; ils MUST NOT toucher aux partitions.
- Un script ou un assistant qui rencontre un état inattendu (taille de fichier, version, message
  inconnu) MUST s'arrêter et demander, plutôt que de continuer.
- La sauvegarde complète de la mémoire (étape 2 du guide) MUST rester obligatoire avant toute
  écriture, et le projet MUST NOT proposer de raccourci qui la contourne.

Raison : un robot qui ne démarre plus est la pire issue possible pour l'utilisateur ; on doit
toujours pouvoir revenir à un robot qui cuisine avec l'appli Lidl.

## Contraintes du projet

- Matériel visé : Monsieur Cuisine Smart SKMS 1200 A1 (base firmware 3090). Le Connect (SKMC 1200)
  reste expérimental ; une fonction propre au Smart MUST être refusée proprement sur le Connect.
- Licence MIT. Le code, les APK, les recettes et les fichiers de Lidl MUST NOT être ajoutés au dépôt.
- Ne jamais commiter : `outils/robot.conf`, `modules/adb-wifi/adb_keys`, APK, sons ou recettes
  personnels, photos de l'appareil (numéros de série), secrets.
- Le relais d'accès distant est un composant séparé, écrit en Go, distribué comme un seul
  exécutable ; il ne contient aucune règle de cuisine propre et ne stocke aucune donnée du robot
  au-delà du nécessaire à son fonctionnement.
- Les scripts de `outils/` qui écrivent sur le robot MUST annoncer ce qu'ils font et rester
  compatibles Mac, Linux et Ubuntu sous Windows (WSL).

## Méthode de travail

- Chaque fonctionnalité suit Spec Kit : `/speckit-specify` → (`/speckit-clarify`) →
  `/speckit-plan` → `/speckit-tasks` → `/speckit-implement`. Le « Constitution Check » du plan
  MUST passer en revue les principes I à VII.
- Avant une publication : tests du cœur au vert, essai sur le vrai robot pour tout ce qui touche
  la machine (main sur l'interrupteur au premier essai moteur), entrée dans `CHANGELOG.md` en
  français et en anglais.
- Les APK sont fabriqués et publiés par GitHub Actions ; les mises à jour depuis le robot MUST
  n'accepter que des fichiers publiés par ce dépôt.

## Governance

- Cette constitution prime sur les autres habitudes du projet. En cas de conflit avec un plan ou
  une tâche, c'est le plan qui change, ou la constitution est amendée explicitement.
- Amendement : modifier ce fichier par une PR ou un commit dédié qui explique le changement, met à
  jour la version et la date, et vérifie que `CLAUDE.md`, `AGENTS.md` et les guides restent
  cohérents.
- Versions (SemVer) : MAJOR pour retirer ou redéfinir un principe ; MINOR pour ajouter un principe
  ou une section, ou élargir nettement une règle ; PATCH pour une clarification.
- Toute revue (PR, `/speckit-analyze`, `/code-review`) vérifie la conformité ; une exception MUST
  être justifiée par écrit dans le plan concerné.

**Version**: 2.0.0 | **Ratified**: 2026-11-27 | **Last Amended**: 2026-11-27
