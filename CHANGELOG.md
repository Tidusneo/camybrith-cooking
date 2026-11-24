# Nouveautés · Changelog

🇫🇷 Français, puis 🇬🇧 English.

## 1.3.0

> **Tu as une version plus ancienne ?** Fais une dernière mise à jour depuis l'ordinateur
> (`git pull`, `bash outils/telecharger.sh`, `bash outils/installer.sh`). Ensuite, tout se fera
> depuis le robot.

* **Couleurs sur la molette** : l'anneau montre l'état du robot. Rouge pour un problème, orange en
  pause, vert quand c'est à toi (cuisson terminée, ou recette guidée qui attend que tu pèses ou que
  tu lances l'étape), cyan pendant la cuisson. Pendant la veille de l'écran, il reste normal, passe
  en veilleuse ou s'éteint. *Réglages › Affichage et sons › Molette.* Code repris de Toque 0.9.0
  (Wuki60) : le Smart a les mêmes LED que le Connect.
* **Mises à jour depuis l'appli** : *Réglages › Mises à jour* télécharge le pilote moteur et
  l'interface (même variante, rapide ou prudente), les installe avec le root, recompile la version
  rapide et relance l'appli. Jamais pendant une cuisson ; en cas d'échec, l'ancienne version reste.
* **Nouvelle version signalée** à l'ouverture de l'appli (au plus toutes les 6 h), sans jamais
  l'installer sans toi.

🇬🇧
> **On an older version?** Do one last update from the computer (`git pull`,
> `bash outils/telecharger.sh`, `bash outils/installer.sh`). From then on, everything happens on
> the robot.

* **Colours on the knob**: the ring shows the robot's state. Red for a problem, orange when paused,
  green when it's your turn (cooking finished, or a guided recipe waiting for you to weigh or start
  the step), cyan while cooking. During screen sleep it stays normal, dims to a night light or turns
  off. *Settings › Display and sounds › Knob.* Code taken from Toque 0.9.0 (Wuki60): the Smart has
  the same LEDs as the Connect.
* **Updates from the app**: *Settings › Updates* downloads the motor pilot and the interface (same
  variant, fast or safe), installs them with root, recompiles the fast version and restarts the
  app. Never while cooking; if anything fails, the previous version stays.
* **New versions announced** when the app opens (at most every 6 hours), never installed without you.

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
