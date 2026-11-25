# Toque sur le Monsieur Cuisine Smart : état du portage

Notes techniques du portage. Pour installer, voir [GUIDE.md](GUIDE.md).

## Robot de référence

| | |
|---|---|
| Modèle | SilverCrest SKMS 1200 A1, IAN 368069_2101, fabriqué en 05/2021 |
| Firmware de la base | 3090 |
| Android | 8.1.0, build V01.00.83_2022062820 |
| Appli d'origine | V2.20.2 |
| Noyau | Linux 4.4.95, compilé le 28/06/2022, correctif de sécurité 2021-03, build user |
| Lecture SP Flash Tool | OK le 21/11/2026 (boot lu à 0x1D80000, image Android valide) : agent de téléchargement accepté |
| Signature du boot | Verified Boot 1.0 avec la clé de test publique AOSP (android@android.com) : build test-keys |
| Root | **actif le 21/11/2026** ; ADB Wi‑Fi par module `modules/adb-wifi` (clé de l'ordinateur autorisée), `su` accordé à Shell dans Magisk (v31 : plus de fenêtre de demande, accès à accorder d'avance dans l'onglet Super‑utilisateur) |
| Boot patché | Magisk 31.1 vérifié (noyau identique, init d'origine sauvegardé, KEEPVERITY=true, PREINITDEVICE=cache, re-signé avec la même clé de test) |

## Mises à jour de Lidl

Les journaux du cache montrent une mise à jour système installée le 13/01/2025 par
`com.discovery.systemupdate` (paquet `LocalSdUpdate.zip`, signé, vérifié par le recovery) :
passage de SPM288_V01.00.78 (25/10/2021) à la build du 28/06/2022. Elle réécrit system, vendor
et **boot**. Une fois le robot rooté, une telle mise à jour effacerait le root : ne pas l'accepter.

## Ce que décrit le protocole actuel

`core/protocol/Mcs.kt` suit désormais l'appli **2.20.2.2** du robot de référence (APK récupéré par
ADB, analysé pour l'interopérabilité ; aucun code de Lidl n'est copié dans ce dépôt) :

| | 1.2.14.15 (SDK) | 2.20.2.2 (robot) |
|---|---|---|
| Enveloppe, somme, séquence | AA, bitmap 4F 42, saut de 0x66 | identique |
| Port | /dev/ttyMT1, 9600 | identique |
| Commande | 39 octets (lumière sur 2 octets) | **38 octets** (lumière sur 1 octet) |
| État reçu | 48 octets | identique, même décodage |

Vérification : 403 commandes produites par le constructeur de trames de la 2.20.2.2, exécuté isolément,
sont reproduites octet pour octet (`core/src/test/resources/mcs/app-2.20.2.2-commands.json`).

Comportement de l'appli 2.20 :
1. elle renvoie la commande complète toutes les 200 ms (90 ms dans un mode particulier) ;
2. au repos, la commande envoyée est 0802 avec la fonction 0 et tous les champs à zéro ;
3. chaque programme est une fonction de la base avec ses paramètres : manuel 1, vapeur 3, rôtir 4,
   pétrir 5, nettoyages 6 et 7, œufs 8, sous vide 9, mijotage 10, fermentation 11, purée 12,
   smoothie 13 ; œufs, sous vide, mijotage et fermentation passent une température constante ;
4. son registre connaît aussi les opcodes 0810, 0811, 0812, 0813, 0821 à 0826 et 0831, absents du SDK
   (sens à établir ; Toque ne les émet pas).

## Écoute passive du 21/11/2026

strace sur l'appli d'origine, rien d'émis par nous. 2402 commandes, 2401 réponses, toutes valides.

1. Commandes : 38 octets, comme prévu ; nos encodages reproduisent les 260 commandes distinctes.
2. Réponses : **53 octets**, en-tête `01 0F 00 00`, séquence de la commande renvoyée (accusé de
   réception, environ 100 ms après), puis les 39 champs connus et 5 octets à zéro. Version 3090.
3. Vitesse, fonction, niveau de température et compte à rebours sont l'écho de la commande. La base ne
   décompte pas : l'appli envoie l'arrêt elle‑même à la fin (10,3 s pour 10 s, 60,3 s pour 60 s).
4. Balance : grammes (990 lus pour 990 affichés), surcharge = état 4, état 2 pendant une cuisson.
5. Bits d'état : 0x80 couvercle ouvert, 0x40 bol absent.
6. Température réelle : 0 tant que l'eau est sous 37 °C, puis paliers de la table. Seconde écoute
   (70 °C, vitesse 1, 180 s, 500 ml) : 37 °C à 106 s, 40 à 124 s, 45 à 133 s, 50 à 148 s, 55 à 169 s ;
   55 °C affichés par l'appli à la fin, 55 lus. Le champ « renvoyée » reste à 0. Résolution : 5 °C.
7. Aucune trame 0801 ni autre opcode pendant ces usages.

8. Appariement des deux écoutes, réponse par réponse : 3 338 réponses, chacune avec la séquence d'une
   commande envoyée juste avant, et un écho (opcode, vitesse, fonction, température, compte à rebours)
   identique à cette commande dans 100 % des cas. Délai : 100 ms en général, 321 ms au pire.
9. Pendant une cuisson, l'appli d'origine envoie une commande toutes les 310 ms environ (200 ms au
   repos). Le compte à rebours envoyé reste constant (10, 60, 180 s). Le poids est figé pendant la
   cuisson. La température lue reste affichée après l'arrêt de la chauffe (55 °C).

## Pilotage par Toque (`core/.../smart/SmartController.kt`)

Toque choisit le Smart tout seul quand l'appli `com.tecpal.device.mc30` est installée (réglage
`robot` : `auto`, `connect` ou `smart`). Port `/dev/ttyMT1`, 9600 bauds, une commande toutes les
200 ms. Le service qui ouvre le port dans l'appli d'origine est `SerialPortMC30Service` (ouvert dans
`onBind`) : c'est lui que « Machine réelle › Appliquer » désactive, comme `HardwareMachineService`
sur le Connect.

Sans mesure du moteur ni de la chauffe, la sécurité repose sur ce que la base a montré :
1. liaison prête après 3 accusés d'arrêt consécutifs ; plus de réponse pendant 1,5 s : tout s'arrête ;
2. chaque réponse est comparée à la commande de même séquence : un écho différent arrête tout ;
3. des réponses à des séquences jamais envoyées (un autre programme parle à la base) arrêtent tout ;
4. le minuteur ne démarre qu'à l'écho du réglage, Toque envoie l'arrêt à la fin, la fin n'est
   confirmée que par l'accusé d'une trame d'arrêt ;
5. changement de sens : arrêt confirmé puis 2 s de ralentissement ;
6. couvercle ouvert ou bol retiré : pause et arrêt immédiats, reprise seulement à la demande ;
7. erreur de la base ou code de retour non nul : arrêt et alerte à acquitter ;
8. mêmes limites que sur le Connect (vitesse 3 en chauffe et en sens inverse, 99 min, contenu chaud) ;
9. seulement les opcodes 0801, 0802 et 0803 ; jamais 0805 ni les trames de mise à jour.

Accepté aujourd'hui : cuisson manuelle (avec ou sans chauffe, phases de repos comprises) et tare.
Refusé tant qu'on ne l'a pas observé sur le robot : vapeur, pétrin, rissolage, Turbo. Les réglages de
l'interface (table du Connect) sont traduits vers la table du Smart ; 40 °C n'est pas encore proposé.

Tests (`SmartControllerTest`, base simulée `SmartSimBoard` calquée sur les écoutes) : 22 cas, dont
la première trame de marche, identique octet pour octet à celle de l'appli d'origine (70 °C,
vitesse 1, 3 min).

## Premiers essais sur le robot (21/11/2026, version 0.6.0-smart2)

Toque en « Machine réelle », bol vide puis fond d'eau, main sur l'interrupteur :
1. vitesse 2, 10 s : le moteur tourne puis s'arrête seul ;
2. vitesse 2, 30 s, couvercle ouvert au milieu : arrêt immédiat, pause, reprise après fermeture ;
3. vitesse 1 à 37 °C, 30 s, 200 ml d'eau : rotation et chauffe.

Avant cela, en Démo, l'interface plantait au bout de 10 à 30 s (tr() renvoyait null) : fonction
recompilée de travers par le JIT d'Android 8.1 sur MT8167. Corrigé par `android:vmSafeMode="true"`.

## Programmes automatiques (écoutes du 21/11/2026 et code de l'appli 2.20)

Dans l'appli d'origine, chaque programme a son générateur (`s5.*`) qui fait varier les commandes
seconde par seconde ; la base exécute ce qu'on lui envoie. « ct » est l'octet de température exacte
(octets 3 et 4 de la charge), utilisé avec le niveau 1 par les programmes à température précise.
Vitesse = vitesse affichée ×2. Compte à rebours = durée restante, changé à chaque palier par
certains programmes. La base répond fonction 1 à toutes les fonctions sauf 0 et 198.

| Programme (Smart) | Fonction | Ce que l'appli envoie (réglage écouté) | Toque |
|---|---|---|---|
| Cuisson personnalisée | 1 | vitesse, niveau de température, sens | oui |
| Saisir | 4 | 130 °C, 5 s vitesse 1 / 30 s arrêt, sens inverse | oui (Rissoler) |
| Turbo | 198 | trame de 35 octets, octet 10 = 10, bouton 6, 5 s | oui (3 s par appui) |
| Cuisson vapeur | 3 | 120 °C, vitesse 1, sens normal, constant | oui (Vapeur) |
| Malaxer, pâte ferme | 5 | 45 s vitesse 4 sens normal, puis 15 s arrêt (1 min) | via Pétrir |
| Malaxer, pâte molle | 5 | 15 s vitesse 4 sens inverse, puis 45 s sens normal (1 min) | Pétrir (même départ) |
| Malaxer, pâte liquide | 5 | vitesse 4 sens normal tout du long (1 min) | via Pétrir |
| Mixer | 12 | vitesse 8 (30 s) | non |
| Smoothie | 13 | vitesse 10 (1 min) | non |
| Chauffer l'eau | 3 | niveau 14 + ct 100, vitesse 1 sens inverse | non |
| Mijoter | 10 | niveau 1 + ct 90, 30 s vitesse 1 / 30 s arrêt, sens inverse | non |
| Sous-vide | 9 | niveau 1 + ct 60, vitesse 1 sens inverse, compte à rebours 3600 pendant la montée en température puis la durée réglée | non |
| Fermenter | 11 | niveau 1 + ct 40, vitesse 1 sens inverse | non |
| Cuire du riz | 1 | niveau 14 + ct 100, vitesse 1 sens inverse | non |
| Cuire des œufs | 8 | niveau 1 + ct 130, vitesse 1 sens normal, 8 min | non |
| Prélaver (rapide) | 6 | 37 °C : 30 s vitesse 10, 30 s vitesse 4 sens inverse, 30 s vitesse 10 | non |
| Trancheuse-râpe | 1 | vitesse 1 (accessoire) | non |

Pause dans l'appli : trames d'arrêt (fonction 0) et quelques 0801 ; reprise avec le temps restant
comme compte à rebours. Le sens renvoyé par la base reste celui de la dernière marche.

**Chien de garde de la base** : appli d'origine fermée de force pendant une marche à vitesse 1, le
moteur s'arrête seul environ 4 s après la dernière commande.

## Inconnues à lever par l'écoute

Si la base applique elle‑même le compte à rebours, sens des opcodes 0810 à 0831 et des 5 derniers
octets. Établi depuis : la base coupe moteur et chauffe couvercle ouvert et le dit dans sa réponse ;
le sens renvoyé reste celui de la dernière marche ; les programmes sont décrits ci‑dessus.

## Camybrith Cooking : nom, sons, logo et animations

* **Nom** : l'appli s'affiche « Camybrith Cooking » (le paquet reste `app.toque`, les mises à jour
  s'installent par-dessus). Icône : la cocotte et sa vapeur.
* **Ouverture de l'appli** : `SplashView`, même dessin et même chronologie que l'animation du robot,
  2,4 s, un appui la passe. Pas d'animation après un changement de langue.
* **Sons** (`Sounds.kt`, fichiers dans `assets/sons`, fabriqués par `personnalisation/marque/sons.py`, tous
  synthétisés) : `demarrage` (ouverture), `debut` (lancement), `fin` (programme terminé), `recette`
  (recette terminée), `alerte` (défaut), `pause` (pause imposée : couvercle, bol…), `appui` (touches), `pesee` (poids atteint), `trop` (poids dépassé), et `compte` (facultatif, sans son fourni : compte à rebours calé pour finir pile avec le programme).
  Chacun se remplace par un fichier du même nom en .wav, .ogg ou .mp3 dans
  `Android/data/app.toque/files/sons/`, puis « Recharger les sons » dans les réglages
  (`outils/sons.sh` envoie ceux du dossier `personnalisation/sons`). Les sons
  personnels sont lus par MediaPlayer, sans limite de durée (SoundPool coupe au delà d'environ 6 s
  sur Android 8). Ils restent sur le robot et n'entrent ni dans le dépôt ni dans l'APK. Le volume
  réglé dans l'appli est celui des médias du robot.
* **Animation de démarrage du robot** : module Magisk `camybrith_boot` (fichier
  `camybrith_animation.zip` des versions publiées), qui superpose `/system/media/bootanimation.zip` sans toucher à
  la partition système. Retrait : `outils/animation.sh retirer`, ou supprimer le module
  dans Magisk, puis redémarrer.
* **Tout premier logo** (affiché par le chargeur de démarrage, avant Android) : partition `logo`
  (`mmcblk0p11`, 8 Mio). Il ne se voit pratiquement pas sur le Smart (constaté le 21/11) : le
  projet n'y touche pas.
* **Son de démarrage** : joué par l'appli une seule fois par démarrage du robot (repéré par
  `Settings.Global.BOOT_COUNT`, sinon `/proc/sys/kernel/random/boot_id`), sauf si l'animation de
  démarrage contient elle-même un son (`audio.wav` dans une partie).
* **Constaté au premier essai (21/11)** : le programme d'animation du robot ne joue pas de son,
  c'est donc l'appli qui joue le son d'ouverture ; le logo Lidl de la partition `logo` ne se voit
  pas à l'allumage (premier écran visible : l'animation Camybrith), d'où l'inutilité de toucher à
  cette partition. L'animation d'ouverture attend que le son parte (1,5 s au plus).
* **À l'arrêt** : le Smart n'a pas d'animation d'arrêt (`shutanimation.zip` absent), l'écran
  s'éteint simplement.

## Recettes d'autres robots converties pour le Smart

`core/.../recipes/Cookidoo.kt` lit un fichier `data.json` de recettes personnelles écrites pour un
autre robot (liste au format schema.org Recipe, réglages du type `10 s/vitesse 5` dans le texte)
et les adapte au Smart. Aucune recette n'est fournie avec le projet (tests sur des exemples écrits
exprès).

* Dépôt : `data.json` et éventuellement un dossier `images/` (photo `r<numéro>.jpg` par recette)
  dans `Android/data/app.toque/files/import/`, importés à l'ouverture de l'appli ou par Réglages,
  « Importer les recettes ». Identifiants `tm-…` : la synchro Lidl ne les efface pas. Elles
  apparaissent dans un onglet à part.
* Conversion d'un réglage : voir le commentaire de `Cookidoo`. En résumé : mêmes vitesses
  (demi-vitesses arrondies, mijotage → 1 en sens inverse), vitesse 3 au plus en chauffe ou en sens
  inverse, 4 au plus au fouet, 10 min au plus au-delà de 6, température à la graduation du Smart
  juste en dessous, Varoma → Vapeur, Pétrin → Malaxer (2 min par fois, 2 fois au plus), mode
  mixage → Mixer, Turbo → impulsions, fourchette de temps → la plus courte. Réglage modifié :
  étape « à vérifier ». Réglage sans durée : à faire à la main.

## Interface compilée, pilote interprété (22/11/2026)

Le pilote moteur est une appli à part (`pilote/`, app.toque.pilote), toujours interprétée
(vmSafeMode). L'interface (app.toque) est compilée : la version rapide (0.8.1-rapide) a planté
deux fois dans la minute suivant l'installation, quand elle tournait encore avec la compilation au
fil de l'eau (JIT) : tr() renvoyait null, la sentinelle `CompilerWatch` l'a vu. Une fois compilée
à l'avance (`cmd package compile -m speed -f app.toque`, état `compilation_filter=speed`), plus
d'écart. Hypothèse retenue : seul le JIT de ce robot est fautif. `outils/installer.sh` compile donc
l'appli avant de l'ouvrir. Repli : version prudente, tout interprété (`-Pprudent=true`).
