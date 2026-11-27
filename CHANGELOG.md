# Nouveautés · Changelog

🇫🇷 Français, puis 🇬🇧 English.

## 1.5.0

* **On peut toujours revenir à l'appli Lidl** (module de secours, à installer une fois avec
  `bash outils/secours.sh installer`) :
  * depuis le robot (Réglages › Outils › Revenir à l'appli Lidl), depuis l'ordinateur
    (`bash outils/origine.sh`), ou sans écran : éteindre et rallumer 3 fois de suite ;
  * tout seul si Camybrith plante dès le démarrage ;
  * une coupure de courant pendant le retour ne laisse jamais le robot à moitié revenu ;
  * puis revenir à Camybrith par la bulle « Camybrith » affichée sur l'appli Lidl.
* **Sauvegardes** : Réglages › Sauvegardes (favoris, recettes perso et importées, réglages ;
  jamais les mots de passe ni les recettes Lidl), copie sur l'ordinateur avec
  `bash outils/sauvegarde.sh`, restauration vérifiée.
* **Retrait complet** de Camybrith : `bash outils/retirer.sh` (le root et l'accès Wi-Fi restent).
* **Modifications du robot** : la liste de tout ce que Camybrith a changé (Réglages › Outils).

🇬🇧
* **You can always go back to the Lidl app** (rescue module, installed once with
  `bash outils/secours.sh installer`):
  * from the robot (Settings › Tools › Back to the Lidl app), from the computer
    (`bash outils/origine.sh`), or without a screen: switch off and on 3 times in a row;
  * by itself if Camybrith crashes right at startup;
  * a power cut during the return never leaves the robot half way;
  * then back to Camybrith with the "Camybrith" bubble shown over the Lidl app.
* **Backups**: Settings › Backups (favorites, own and imported recipes, settings; never passwords
  or Lidl recipes), copied to the computer with `bash outils/sauvegarde.sh`, checked restore.
* **Full removal** of Camybrith: `bash outils/retirer.sh` (root and Wi-Fi access stay).
* **Changes to the robot**: the list of everything Camybrith changed (Settings › Tools).

## 1.4.3

* **Mon propre dépôt** : les mises à jour depuis le robot, les guides et les scripts pointent vers
  [github.com/scaule/camybrith-cooking](https://github.com/scaule/camybrith-cooking). Le robot
  refuse les fichiers publiés ailleurs.
* **Import de recettes Cookomix** : dans Recettes, bouton « Importer », puis colle le lien d'une
  recette cookomix.com. Les réglages sont convertis pour le Smart, et les étapes adaptées sont
  marquées « à vérifier ». La recette arrive dans « Mes recettes », avec sa photo.

🇬🇧
* **Own repository**: updates from the robot, the guides and the scripts now point to
  [github.com/scaule/camybrith-cooking](https://github.com/scaule/camybrith-cooking). The robot
  refuses files published elsewhere.
* **Cookomix recipe import**: in Recipes, tap "Import" and paste a cookomix.com recipe link.
  Settings are converted for the Smart, and adapted steps are marked "to check". The recipe lands
  in "My recipes", with its photo.

## 1.4.2

* **Nouvelle adresse du projet** : [github.com/Tidusneo/camybrith-cooking](https://github.com/Tidusneo/camybrith-cooking).
  Les mises à jour depuis le robot, les guides et les scripts pointent vers elle.
* **Mises à jour plus sûres** : le robot ne télécharge plus que des fichiers publiés par ce dépôt.
* Après l'installation, le robot redémarre tout seul (nouveauté de la 1.4.1, dont c'est le premier
  essai en vrai).

> Si tu as cloné le projet sur ton ordinateur, mets son adresse à jour une fois :
> `git remote set-url origin https://github.com/Tidusneo/camybrith-cooking`

🇬🇧
* **New project address**: [github.com/Tidusneo/camybrith-cooking](https://github.com/Tidusneo/camybrith-cooking).
  Updates from the robot, the guides and the scripts now point to it.
* **Safer updates**: the robot only downloads files published by this repository.
* After installing, the robot restarts on its own (new in 1.4.1, first real test here).

> If you cloned the project on your computer, update its address once:
> `git remote set-url origin https://github.com/Tidusneo/camybrith-cooking`

## 1.4.1

* **Mise à jour depuis le robot plus fiable** : après l'installation, le robot redémarre tout seul
  au lieu de relancer seulement l'appli. Avant, la liaison avec la base pouvait rester coupée
  (« Base absente ») jusqu'à un redémarrage à la main.

> Cette correction joue à partir de la mise à jour **suivante**. Si « Base absente » s'affiche
> après avoir installé la 1.4.1, éteins et rallume le robot une fois.

🇬🇧
* **More reliable updates from the robot**: after installing, the robot now restarts on its own
  instead of only relaunching the app. Before, the link with the base could stay down ("No base")
  until a manual restart.

> This fix applies from the **next** update on. If "No base" shows after installing 1.4.1, switch
> the robot off and on once.

## 1.4.0

### ⭐ Home Assistant

**Le robot envoie son état à Home Assistant par MQTT**, et ses capteurs apparaissent tout seuls
grâce à la découverte MQTT : état (au repos, en cours, en pause, alerte), programme, température,
vitesse, poids sur la balance, temps restant, couvercle, alerte. De quoi recevoir une notification
quand la cuisson est terminée, par exemple.

* *Réglages › Home Assistant* : adresse du serveur, port, utilisateur, mot de passe.
* **Lecture seule** : rien venant du réseau ne peut démarrer le moteur ou la chauffe.
* Désactivé par défaut. Mode d'emploi : [docs/HOME-ASSISTANT.md](docs/HOME-ASSISTANT.md).

> Mise à jour directement depuis le robot, *Réglages › Mises à jour*, si tu es déjà en 1.3.0 ou
> plus. Sinon, une dernière fois depuis l'ordinateur (`git pull`, `bash outils/telecharger.sh`,
> `bash outils/installer.sh`).

🇬🇧

### ⭐ Home Assistant

**The robot sends its state to Home Assistant over MQTT**, and its sensors show up on their own
through MQTT discovery: status (idle, running, paused, fault), program, temperature, speed, weight
on the scale, time remaining, lid, alert. Handy for a notification when cooking is done, for
example.

* *Settings › Home Assistant*: server address, port, user, password.
* **Read only**: nothing coming from the network can start the motor or the heater.
* Off by default. How‑to: [docs/HOME-ASSISTANT.en.md](docs/HOME-ASSISTANT.en.md).

> Update straight from the robot, *Settings › Updates*, if you're already on 1.3.0 or later.
> Otherwise, one last time from the computer (`git pull`, `bash outils/telecharger.sh`,
> `bash outils/installer.sh`).

## 1.3.1

* **Veille de l'écran corrigée** : le choix du délai s'affiche bien quand on appuie dessus, et
  l'écran s'éteint vraiment. Il restait allumé à cause des petits changements d'état de la liaison
  avec la base et des quelques grammes de variation de la balance au repos. Seul un vrai ajout ou
  retrait (30 g) le rallume maintenant.
* **Journal** : chaque mise en veille et chaque réveil y est noté avec sa raison, pour comprendre
  facilement si l'écran ne s'éteint pas.
* **Résumé des nouveautés** dans *Réglages › Mises à jour*, dans la langue de l'appli.
* Tous les sélecteurs des réglages montrent le nouveau choix dès l'appui.

> **Tu es sur une version d'avant la 1.3.0 ?** Fais cette mise à jour une dernière fois depuis
> l'ordinateur (`git pull`, `bash outils/telecharger.sh`, `bash outils/installer.sh`). C'est
> indispensable pour profiter ensuite des mises à jour directement depuis le robot, par son Wi‑Fi,
> sans ordinateur. Déjà en 1.3.0 ? Elle t'attend dans *Réglages › Mises à jour*.

🇬🇧
* **Screen sleep fixed**: the delay selector now shows your choice when you tap it, and the screen
  really turns off. It stayed on because of small link state changes with the base and the few
  grams the scale drifts at rest. Only a real addition or removal (30 g) wakes it now.
* **Log**: every sleep and wake is written to the log with its reason, to make it easy to see why
  the screen stays on.
* **What's new summary** in *Settings › Updates*, in the app's language.
* All settings selectors show the new choice as soon as you tap.

> **On a version older than 1.3.0?** Do this update one last time from the computer (`git pull`,
> `bash outils/telecharger.sh`, `bash outils/installer.sh`). It's required to get all future
> updates straight from the robot, over its Wi‑Fi, with no computer. Already on 1.3.0? It's waiting
> for you in *Settings › Updates*.

## 1.3.0

### ⭐ Plus besoin de l'ordinateur pour les mises à jour

**Le robot se met à jour tout seul, depuis son Wi‑Fi, ordinateur éteint.** Plus de câble, plus
d'ADB, plus de commandes à taper.

* **Il vérifie tout seul** : à l'ouverture de l'appli (au plus toutes les 6 h), il regarde s'il y a
  une nouvelle version et te le signale.
* **Un appui pour installer** dans *Réglages › Mises à jour*, avec le résumé des nouveautés. L'appli
  télécharge la bonne variante (rapide ou prudente), met à jour le pilote moteur s'il a changé,
  installe le tout avec le root, recompile et se relance.
* **Sans risque** : jamais pendant une cuisson, jamais sans ton accord, et en cas d'échec l'ancienne
  version reste en place.

> **Tu as une version plus ancienne ?** Il faut une toute dernière mise à jour depuis l'ordinateur
> pour recevoir cette nouveauté (`git pull`, `bash outils/telecharger.sh`,
> `bash outils/installer.sh`). Ensuite, tout se fait depuis le robot.

### Aussi dans cette version

* **Couleurs sur la molette** : l'anneau montre l'état du robot. Rouge pour un problème, orange en
  pause, vert quand c'est à toi (cuisson terminée, ou recette guidée qui attend que tu pèses ou que
  tu lances l'étape), cyan pendant la cuisson. Pendant la veille de l'écran, il reste normal, passe
  en veilleuse ou s'éteint. *Réglages › Affichage et sons › Molette.* Code repris de Toque 0.9.0
  (Wuki60) : le Smart a les mêmes LED que le Connect.

🇬🇧

### ⭐ No more computer needed for updates

**The robot now updates itself over its Wi‑Fi, with the computer switched off.** No cable, no ADB,
no commands to type.

* **It checks on its own**: when the app opens (at most every 6 hours), it looks for a new version
  and lets you know.
* **One tap to install** in *Settings › Updates*, with a summary of what's new. The app downloads
  the right variant (fast or safe), updates the motor pilot if it changed, installs everything with
  root, recompiles and restarts.
* **Safe**: never while cooking, never without your OK, and if anything fails the previous version
  stays.

> **On an older version?** One very last update from the computer is needed to get this
> (`git pull`, `bash outils/telecharger.sh`, `bash outils/installer.sh`). From then on, everything
> happens on the robot.

### Also in this version

* **Colours on the knob**: the ring shows the robot's state. Red for a problem, orange when paused,
  green when it's your turn (cooking finished, or a guided recipe waiting for you to weigh or start
  the step), cyan while cooking. During screen sleep it stays normal, dims to a night light or turns
  off. *Settings › Display and sounds › Knob.* Code taken from Toque 0.9.0 (Wuki60): the Smart has
  the same LEDs as the Connect.

## 1.2.0

* **Mise en veille de l'écran** : sans cuisson en cours, l'écran passe au noir après 2, 5, 10 ou
  30 minutes (ou jamais). Un appui sur l'écran ou un tour de molette le rallume, sans déclencher de
  bouton. Une cuisson, une pause, une alerte, le couvercle ou une pesée le rallument.
  *Réglages › Affichage et sons.* Avant, l'écran restait toujours allumé.

🇬🇧
* **Screen sleep**: when nothing is cooking, the screen goes dark after 2, 5, 10 or 30 minutes (or
  never). A touch or a turn of the knob wakes it, without pressing any button. Cooking, a pause, an
  alert, the lid or weighing wake it up. *Settings › Display and sounds.* Before, the screen never
  turned off.

## 1.1.0

* **L'appli en espagnol**, en entier. Avec l'appli en espagnol, les recettes Lidl sont téléchargées
  en espagnol. Présentation du projet en espagnol (`README.es.md`).
* **Monsieur Cuisine Connect, en bêta** : Camybrith Cooking peut aussi tourner sur le Connect, avec
  son guide (`docs/CONNECT.md`). Pas encore essayé sur un vrai Connect.
* **Assistant Claude prêt à l'emploi** (`camybrith-assistant-claude.zip`) : il guide l'installation
  étape par étape, vérifie les tailles et les photos d'écran, et parle français, anglais ou espagnol.
* **Documentation en anglais**, captures d'écran, animation de démarrage et animation de la pesée
  dans la présentation.
* Installateur : pas de compilation à l'avance sous Android 6 (Connect).

🇬🇧
* **The app in Spanish**, fully. With the app in Spanish, Lidl recipes are downloaded in Spanish.
  Project overview in Spanish (`README.es.md`).
* **Monsieur Cuisine Connect, beta**: Camybrith Cooking can also run on the Connect, with its guide
  (`docs/CONNECT.en.md`). Not tried on a real Connect yet.
* **Ready-to-use Claude assistant** (`camybrith-assistant-claude.zip`): it guides the install step
  by step, checks file sizes and screen photos, and speaks French, English or Spanish.
* **English documentation**, screenshots, boot animation and weighing animation in the overview.
* Installer: no ahead-of-time compile on Android 6 (Connect).

## 1.0.0

Première version publique : Toque (Wuki60) adapté au Monsieur Cuisine Smart.

* Protocole du Smart relevé sur un vrai robot : trames de 38 et 53 octets, programmes, table des
  températures, chien de garde de la base.
* **Pilote moteur dans une appli séparée** : si l'interface plante, la cuisson continue.
* **Couvercle ouvert = pause**, reprise d'un appui.
* Interface **rapide** (compilée à l'avance) ou **prudente**.
* Recettes Monsieur Cuisine du Smart, recherche tolérante aux fautes, cuisson guidée avec pesée à
  la molette, cuisson manuelle, programmes, balance.
* Import de tes propres recettes écrites pour un autre robot.
* Sons et animation de démarrage personnalisables.
* Guide de root pas à pas pour débutants, et scripts d'installation par le Wi-Fi.

🇬🇧
First public version: Toque (Wuki60) ported to the Monsieur Cuisine Smart.

* Smart protocol captured on a real robot: 38 and 53 byte frames, programs, temperature table,
  base watchdog.
* **Motor pilot in a separate app**: if the interface crashes, cooking keeps going.
* **Lid open = pause**, resumed with one tap.
* **Fast** (ahead-of-time compiled) or **safe** interface.
* Monsieur Cuisine recipes for the Smart, typo-tolerant search, guided cooking with knob weighing,
  manual cooking, programs, scale.
* Import your own recipes written for another robot.
* Customizable sounds and boot animation.
* Step by step rooting guide for beginners, and install scripts over Wi-Fi.
