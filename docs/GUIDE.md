# Installer Camybrith Cooking sur un Monsieur Cuisine Smart, pas à pas

🇬🇧 **[English version](GUIDE.en.md)**

Ce guide part de zéro. Il est écrit pour quelqu'un qui n'a jamais rooté d'appareil Android : lis
chaque étape en entier avant de la commencer, et ne saute rien. Compte deux à trois heures en tout,
dont une bonne partie d'attente.

Les étapes 1 à 6 (le **root**) ne se font qu'une fois. Ensuite, tout se fait par le Wi-Fi.

> **Envie d'être accompagné ?** Un assistant Claude prêt à l'emploi peut te guider étape par
> étape : voir « Se faire guider par Claude » dans le [README](../README.md).

## Sommaire

0. [Avant de commencer](#0-avant-de-commencer)
1. [Préparer le PC Windows](#1-préparer-le-pc-windows)
2. [Brancher le robot et tout sauvegarder](#2-brancher-le-robot-et-tout-sauvegarder)
3. [Préparer l'image « cache »](#3-préparer-limage-cache)
4. [Démarrer TWRP et copier les fichiers](#4-démarrer-twrp-et-copier-les-fichiers)
5. [Remettre le démarrage d'origine et installer les applis](#5-remettre-le-démarrage-dorigine-et-installer-les-applis)
6. [Rooter avec Magisk](#6-rooter-avec-magisk)
7. [Commander le robot par le Wi-Fi (ADB)](#7-commander-le-robot-par-le-wi-fi-adb)
8. [Installer Camybrith Cooking](#8-installer-camybrith-cooking)
9. [Premier essai avec le vrai moteur](#9-premier-essai-avec-le-vrai-moteur)
10. [Au quotidien](#10-au-quotidien)
11. [Mettre à jour](#11-mettre-à-jour)
12. [Revenir en arrière](#12-revenir-en-arrière)
13. [Problèmes fréquents](#13-problèmes-fréquents)


## 0. Avant de commencer

### Les règles d'or

Ces erreurs ont déjà coûté des robots à d'autres personnes. Relis-les avant chaque étape.

1. **Le bon port USB.** Sous la trappe de maintenance, il y a deux prises USB. Branche-toi
   **uniquement** sur celle marquée **« Android USB »**. L'autre (« Power PCB USB ») est reliée à
   la carte de puissance du moteur et de la chauffe : elle peut griller ton PC.
2. **Dans SP Flash Tool, toujours « Download Only ».** Jamais « Firmware Upgrade », jamais
   « Format All + Download ». Si une fenêtre parle de **« PMT changed »** ou propose de formater,
   clique sur **Annuler** et arrête-toi.
3. **Dans TWRP, ne jamais faire « Swipe to Allow Modifications ».** Choisis toujours **« Keep Read
   Only »**. Modifier la partition système empêche le robot de redémarrer.
4. **Dans le menu usine du robot, ne lance aucun test et n'accepte aucune fenêtre.** Surtout pas
   « Activer l'appareil » (ça casse la liaison avec le compte Lidl), ni le test d'endurance (il fait
   tourner le moteur), ni la calibration de la balance (elle demande des poids de 1 et 3 kg).
5. **Ne plus jamais accepter de mise à jour système de Lidl** une fois le robot rooté : elle
   effacerait le root et pourrait bloquer le robot. (Les mises à jour de l'appli Monsieur Cuisine
   ne posent pas de problème, c'est celle du système qui est dangereuse.)
6. **Ne jamais effacer la partition « cache »** (dans TWRP ou ailleurs) une fois Magisk installé :
   il s'en sert.
7. **Garde ta sauvegarde** (étape 2) en deux exemplaires, sur deux supports différents. C'est elle
   qui permet de réparer le robot en cas de pépin.

### Le matériel

* Un **Monsieur Cuisine Smart SKMS 1200 A1**. Le guide a été testé sur un exemplaire de 2021 avec
  Android 8.1 et la version système **V01.00.83** (juin 2022).
* Un **PC sous Windows 10 ou 11**, avec environ **6 Go d'espace libre**. SP Flash Tool, l'outil qui
  lit et écrit la mémoire du robot, n'existe que sous Windows (et Linux).
* Un câble **USB-A mâle vers USB-A mâle**, en **USB 2.0** de préférence (quelques euros). Attention :
  beaucoup de câbles « de charge » n'ont pas les fils de données et ne marcheront pas.
* Un **petit tournevis Torx** pour la trappe de maintenance (vérifie la taille des vis de ton robot).
* Le **Wi-Fi** de la maison, auquel le robot est déjà connecté.

### Le robot est-il rootable ?

Sur certains Smart, le démarrage sécurisé est activé et SP Flash Tool est refusé dès la connexion,
avec l'erreur `STATUS_BROM_CMD_SEND_DA_FAIL`. Personne n'a réussi à rooter ces exemplaires. Bonne
nouvelle : le tout premier test (étape 2) **ne fait que lire** la mémoire. S'il échoue avec cette
erreur, tu t'arrêtes là, sans rien avoir abîmé.

### Quelques mots de vocabulaire

* **Root** : avoir tous les droits sur le système Android du robot. Camybrith Cooking en a besoin
  pour parler directement à la carte du moteur.
* **Partition** : un morceau de la mémoire du robot (le démarrage « boot », le système « system »,
  le cache « cache »…).
* **TWRP** : un petit système de secours, démarré une seule fois pour copier des fichiers.
* **Magisk** : l'outil qui donne le root, sans toucher à la partition système.
* **ADB** : l'outil de Google qui permet de commander un appareil Android depuis un ordinateur.
* **Terminal** : la fenêtre où l'on tape des commandes. Pour copier une commande de ce guide,
  sélectionne-la, copie-la, colle-la dans le terminal, puis appuie sur Entrée.


## 1. Préparer le PC Windows

### 1.1 SP Flash Tool

1. Télécharge **SP Flash Tool v5 pour Windows** sur [spflashtool.com](https://spflashtool.com/).
2. Décompresse-le dans un dossier simple, par exemple `C:\SPFT`.
3. Crée un dossier pour tout le projet, par exemple `C:\MCS`, avec un sous-dossier
   `C:\MCS\sauvegarde`.

### 1.2 Le pilote USB MediaTek

Sans lui, Windows voit le robot une seconde sous le nom « MT65xx Preloader » avec un triangle
jaune, puis le perd.

1. Va sur le [catalogue Microsoft Update](https://www.catalog.update.microsoft.com/) et cherche
   **MediaTek PreLoader USB VCOM**. Télécharge le fichier `.cab` pour Windows 10/11 64 bits.
2. Extrais le `.cab` (avec [7-Zip](https://www.7-zip.org/) par exemple) en gardant les dossiers.
   Tu dois trouver un fichier `cdc-acm.inf`, dans le dossier `x64`.
3. Fais un clic droit sur le menu Démarrer, **Terminal (administrateur)**, va dans ce dossier
   (`cd` suivi du chemin) puis tape :
   ```
   pnputil /add-driver cdc-acm.inf /install
   ```
4. Si Windows répond « Accès refusé », c'est que le terminal n'est pas en administrateur.
5. Si Windows dit plus tard qu'il ne peut pas charger `usb2ser.sys`, c'est une protection de
   Windows 11 : **Sécurité Windows > Sécurité de l'appareil > Isolation du noyau**, désactive
   **Intégrité de la mémoire**, puis redémarre le PC. Tu pourras la réactiver à la fin.

### 1.3 Le fichier « scatter » et TWRP

Ils ont été partagés par ihrapsa dans la discussion de la communauté. Sur
[ce commentaire](https://github.com/EliasKotlyar/Monsieur-Cuisine-Connect-Hack/issues/38#issuecomment-1605329093),
télécharge les deux fichiers joints et mets-les dans `C:\MCS` :

* `MT8167_Android_scatter_MC3_jonah1024.txt` (le plan de la mémoire du Smart) ;
* `twrp_MC3_no_adb.zip` (décompresse-le : tu obtiens une image `.img` de TWRP).

### 1.4 Les trois applis à installer sur le robot

Télécharge-les dans un dossier `C:\MCS\apk` et **renomme-les exactement** comme indiqué :

| Appli | Où la trouver | Version testée | Nom à donner |
|---|---|---|---|
| Magisk | [GitHub officiel de Magisk](https://github.com/topjohnwu/Magisk/releases) (`Magisk-v31.1.apk`) | 31.1 | `magisk.apk` |
| Navigation Bar (`nu.nav.bar`) | Play Store, ou un site miroir d'APK réputé (APKMirror, APKCombo) | 3.3.0 | `navbar.apk` |
| HTTP File Server (`slowscript.httpfileserver`) | Play Store, ou un site miroir d'APK réputé | 1.7.1 | `httpserver.apk` |

Pourquoi ces applis : le robot n'a pas de boutons Android, **Navigation Bar** ajoute une barre
avec Retour, Accueil et Applis récentes. **HTTP File Server** permet d'échanger des fichiers entre
le robot et le PC avec un simple navigateur web, car l'ADB par USB ne fonctionne pas sur le Smart.

Prends Magisk **uniquement** sur son GitHub officiel : de fausses versions circulent ailleurs.

### 1.5 Ubuntu dans Windows (WSL) et ce projet

Deux étapes (la 3 et la 7) et tous les scripts du projet tournent sous Linux. Windows sait faire
tourner Ubuntu dans une fenêtre, sans rien changer au PC.

1. Dans le Terminal (administrateur), tape `wsl --install -d Ubuntu`, puis redémarre le PC.
2. Ouvre **Ubuntu** depuis le menu Démarrer, choisis un nom d'utilisateur et un mot de passe
   (rien ne s'affiche quand tu tapes le mot de passe, c'est normal).
3. Dans la fenêtre Ubuntu, installe les outils et récupère le projet :
   ```
   sudo apt update && sudo apt install -y git e2fsprogs zip unzip curl python3-pil
   git clone https://github.com/scaule/camybrith-cooking ~/camybrith-cooking
   cd ~/camybrith-cooking
   ```

Dans Ubuntu, ton disque Windows est sous `/mnt/c` : `C:\MCS` s'appelle `/mnt/c/MCS`.

> **Sur Mac ou Linux**, tu peux faire toutes les étapes à partir de la 7 depuis ton ordinateur
> habituel : ouvre le Terminal, installe git et récupère le projet de la même façon. Les étapes 1 à 6
> demandent quand même un PC Windows pour SP Flash Tool.


## 2. Brancher le robot et tout sauvegarder

Cette étape ne fait que **lire** la mémoire du robot. Elle n'écrit rien.

### 2.1 Brancher

1. Éteins le robot à l'interrupteur.
2. Ouvre la trappe de maintenance (vis Torx). Repère la prise **« Android USB »** (règle d'or n° 1).
3. Branche le câble entre cette prise et un port USB **à l'arrière** du PC (directement sur la
   carte mère, sans hub ni rallonge).

### 2.2 Le principe de SP Flash Tool

Quand on allume le robot, sa puce passe quelques secondes dans un mode spécial avant de démarrer
Android. SP Flash Tool en profite pour parler directement à la mémoire. Il faut donc **toujours
cliquer d'abord dans SP Flash Tool, puis allumer le robot**.

1. Lance `C:\SPFT\flash_tool.exe` avec un clic droit, **Exécuter en tant qu'administrateur**.
2. Onglet **Download**, à côté de « Scatter-loading File », clique sur **choose** et ouvre
   `C:\MCS\MT8167_Android_scatter_MC3_jonah1024.txt`. Ne touche à rien d'autre dans cet onglet.

### 2.3 Le test de lecture (16 Mo)

1. Onglet **Readback**, clique sur **Add** : une ligne apparaît. **Double-clique** sur cette ligne.
2. Une fenêtre demande où enregistrer : choisis `C:\MCS\sauvegarde` et tape le nom `boot.img`.
   (Si tu oublies de donner un nom, le fichier s'appellera `ROM_0` : renomme-le ensuite.)
3. La fenêtre suivante demande quelle zone lire. Remplis exactement :
   * Type : **Hex**
   * Region : **EMMC_USER**
   * Start Address : **0x1D80000**
   * Length : **0x1000000**
4. Robot éteint, câble branché : clique sur **Read Back**, puis **allume le robot**.
5. Une barre de progression démarre, puis une fenêtre verte « Readback OK » s'affiche.
   Éteins le robot.

Vérifie : `boot.img` doit faire **exactement 16 777 216 octets** (clic droit, Propriétés).

* **Erreur `STATUS_BROM_CMD_SEND_DA_FAIL` tout de suite** : ton robot est verrouillé, arrête-toi
  ici. Rien n'est abîmé.
* **Rien ne se passe** : c'est presque toujours le pilote (étape 1.2) ou le câble. Ouvre le
  Gestionnaire de périphériques et rallume le robot : « MediaTek PreLoader USB VCOM » doit
  apparaître quelques secondes dans **Ports (COM et LPT)**.

### 2.4 La sauvegarde complète

Dans l'onglet **Readback**, retire la ligne du test (clic droit, Remove), puis ajoute ces trois
lignes de la même façon (Type **Hex**, Region **EMMC_USER**) :

| Nom du fichier | Start Address | Length | Taille attendue (octets) |
|---|---|---|---|
| `emmc_debut.bin` | 0x0 | 0xB4C00000 | 3 032 481 792 |
| `cache.img` | 0xA2000000 | 0x12C00000 | 314 572 800 |
| `system.img` | 0x22000000 | 0x80000000 | 2 147 483 648 |

`emmc_debut.bin` est la copie d'un bloc de tout le début de la mémoire, jusqu'au cache compris :
table des partitions, réglages propres à ton robot, système. C'est ce qui permettrait de le
réparer. Les deux autres fichiers servent pendant la procédure.

Robot éteint : **Read Back**, puis allume le robot. Compte une dizaine de minutes, ne touche à rien
avant la fenêtre verte. Vérifie ensuite les trois tailles, puis **copie tout le dossier
`C:\MCS\sauvegarde` sur un autre support** (clé USB, disque externe, autre ordinateur).


## 3. Préparer l'image « cache »

Le robot n'a ni ADB par USB ni lecteur de carte : pour lui faire parvenir les applis, on les glisse
dans une copie de sa partition cache, qu'on réécrira ensuite. Ta sauvegarde n'est pas modifiée.

Dans la fenêtre **Ubuntu** :

```
cd ~/camybrith-cooking
bash outils/preparer_cache.sh /mnt/c/MCS/sauvegarde /mnt/c/MCS/apk
```

Le script vérifie les tailles, écrit `boot.img` et les trois applis dans une copie nommée
`cache_avec_applis.img`, relit chaque fichier pour s'assurer qu'il est identique, puis contrôle le
système de fichiers. Il doit se terminer par « Image prête ». En cas d'« ÉCHEC », n'utilise pas
l'image.


## 4. Démarrer TWRP et copier les fichiers

C'est **la première étape qui écrit sur le robot**. Ne la lance qu'avec une sauvegarde vérifiée.

### 4.1 Écrire TWRP et le cache

1. Dans SP Flash Tool, onglet **Download**, scatter chargé :
   1. **décoche toutes les lignes**, puis coche seulement **boot** et **cache** ;
   2. double-clique sur l'emplacement de **boot** et choisis l'image **TWRP** (le `.img` sorti de
      `twrp_MC3_no_adb.zip`) ;
   3. double-clique sur l'emplacement de **cache** et choisis
      `C:\MCS\sauvegarde\cache_avec_applis.img` ;
   4. dans le menu déroulant au-dessus de la liste, choisis **Download Only** (règle d'or n° 2).
2. Robot éteint : clique sur **Download** (la flèche verte), puis allume le robot. Attends la
   fenêtre verte.
3. Éteins puis rallume le robot. Le logo s'affiche une seconde, puis l'écran reste **noir environ
   une minute** : c'est normal. TWRP démarre ensuite.

### 4.2 Viser dans TWRP

Sur le Smart, l'affichage de TWRP est **tourné d'un quart de tour**, mais le tactile, lui, ne
l'est pas : il réagit comme si l'interface était affichée **droite et étirée sur tout l'écran**.

Le plus simple : prends l'écran en photo avec ton téléphone, fais pivoter la photo jusqu'à lire
TWRP à l'endroit, et repère où se trouve le bouton voulu, en proportion (par exemple « aux trois
quarts vers la droite et tout en bas »). Touche ensuite l'écran du robot **au même endroit en
proportion**, en le regardant normalement. Avance doucement : un appui de travers ne fait rien de
grave tant que tu ne fais aucun « swipe » non prévu.

### 4.3 Copier les fichiers

1. Au démarrage, TWRP demande s'il peut modifier le système : choisis **Keep Read Only**
   (règle d'or n° 3).
2. Va dans **Advanced**, puis **File Manager**, puis ouvre le dossier **cache**.
3. Pour chacun des quatre fichiers (`boot.img`, `magisk.apk`, `navbar.apk`, `httpserver.apk`) :
   appuie sur le fichier, **Copy File**, choisis le dossier **sdcard**, **Select Folder**, puis fais
   le **Swipe to Confirm** (celui-là est sans danger). TWRP affiche « Successful ». Reviens avec
   **Back** dans le dossier cache pour le fichier suivant.


## 5. Remettre le démarrage d'origine et installer les applis

### 5.1 Remettre le démarrage d'origine

TWRP occupe pour l'instant la place du démarrage normal. On remet l'original.

1. Dans SP Flash Tool, onglet **Download** : **décoche cache**, il ne reste que **boot** de coché.
   Double-clique sur son emplacement et choisis ton **`boot.img` d'origine** (celui de la
   sauvegarde, surtout pas l'image TWRP). Mode **Download Only**.
2. Clique sur **Download** : SP Flash Tool attend le robot.
3. Dans TWRP, touche l'icône **maison** (au milieu, tout en bas de l'écran vu normalement), puis
   **Reboot**, puis **System**. Si TWRP propose d'installer son appli : **Do Not Install**, sans swipe.
4. Au redémarrage, SP Flash Tool attrape le robot et réécrit le démarrage. Attends la fenêtre
   verte, puis éteins et rallume : l'appli Monsieur Cuisine démarre comme avant.

Si SP Flash Tool rate le passage et que le robot revient dans TWRP : attends une minute, éteins à
l'interrupteur, reclique sur **Download**, puis rallume.

Tu peux maintenant débrancher le câble USB : il ne servira plus qu'à l'étape 6.

### 5.2 Ouvrir les réglages Android par le menu usine

1. Dans l'appli Monsieur Cuisine : menu (en haut à droite), **Réglages**, **À propos**, puis
   **À propos de cet appareil**. Note les **4 derniers caractères du numéro de série** de la
   tablette, puis **Terminé**.
2. Fais un **appui long (2 secondes)** sur le titre « À propos de Monsieur Cuisine ».
3. Le mot de passe est ces 4 caractères suivis de `_Mc30`. Par exemple, pour un numéro finissant
   par `AB12`, c'est `AB12_Mc30`. Une fois déverrouillé, le menu usine reste accessible sans mot
   de passe pendant 15 jours.
4. Va sur l'écran **Further test** et fais un **appui long (2 secondes)** sur le numéro de version,
   en bas à droite : les réglages Android s'ouvrent.

Rappel (règle d'or n° 4) : dans le menu usine, ne lance rien et n'accepte rien.

### 5.3 Installer les trois applis

1. Dans les réglages Android : **Stockage**, puis **Fichiers** (ou « Explorer »). À la racine, tu
   retrouves tes quatre fichiers.
2. Installe **`navbar.apk` en premier**. Android demande d'autoriser l'installation d'applis
   inconnues pour le gestionnaire de fichiers : accepte.
3. Ouvre **Navigation Bar** et donne-lui ce qu'elle demande (affichage par-dessus les autres
   applis, accessibilité). Une barre apparaît avec Retour, Accueil et le **carré des applis
   récentes**. Si tu restes coincé avant, éteins et rallume le robot, et reprends au 5.2.
4. Installe **`magisk.apk`** puis **`httpserver.apk`**.

Astuce pour ouvrir une appli (le robot n'a pas d'écran d'applis) : retouche son fichier `.apk`
dans le gestionnaire, accepte de la réinstaller, puis appuie sur **Ouvrir**. Une fois qu'elle a
été ouverte, elle apparaît aussi dans le **carré** de la barre.


## 6. Rooter avec Magisk

### 6.1 Faire patcher le démarrage par Magisk

1. Ouvre **Magisk**. S'il propose une mise à jour de l'appli, refuse.
2. Sur la ligne Magisk, appuie sur **Installer**.
3. Laisse **Conserver AVB 2.0/dm-verity** activé (vert).
4. Appuie sur **Patch firmware / image** (la ligne avec la petite flèche).
5. Le sélecteur de fichiers cache la mémoire interne : touche **⋮** en haut à droite,
   **Afficher la mémoire interne**, puis choisis l'appareil dans la colonne de gauche et prends
   **`boot.img`**.
6. Attends la fin du journal. Tu dois y lire notamment « Stock boot image detected » (le démarrage
   de départ est bien l'original) et « Pre-init storage partition: cache ». Le fichier créé
   s'appelle `magisk_patched-31100_xxxxx.img`, dans le dossier **Download**.

### 6.2 Récupérer le fichier sur le PC

1. Ouvre **HTTP File Server** et **démarre le serveur**. Il affiche une adresse du type
   `http://192.168.1.42:8080`. Note l'adresse IP du robot (ici `192.168.1.42`) : tu en auras besoin
   plus tard.
2. Sur le PC, ouvre cette adresse dans le navigateur, va dans **Download** et télécharge le
   fichier `magisk_patched…img` dans `C:\MCS`.
3. Vérifie sa taille : il doit faire **au plus 16 777 216 octets** (en général un peu moins de
   16 Mo). Pour un contrôle de plus, dans Ubuntu :
   ```
   head -c 8 /mnt/c/MCS/magisk_patched*.img; echo
   ```
   La réponse doit être `ANDROID!`.

### 6.3 Écrire le démarrage patché

1. Rebranche le câble USB (prise « Android USB »), robot éteint.
2. SP Flash Tool, onglet **Download** : **seul boot coché**, avec le fichier
   `magisk_patched…img`. Mode **Download Only**.
3. **Download**, puis allume le robot, et attends la fenêtre verte.
4. Éteins et rallume. Le robot peut redémarrer une ou deux fois tout seul avant d'afficher
   l'appli Monsieur Cuisine : laisse-le faire.
5. Ouvre Magisk (carré de la barre) : la ligne Magisk doit indiquer **Installé : 31.1**. S'il
   annonce qu'une configuration supplémentaire est nécessaire, accepte (il redémarre une fois).

**Le robot boucle au démarrage depuis plus de cinq minutes ?** Pas de panique : réécris ton
`boot.img` d'origine de la même façon (étape 6.3 avec l'original), il redémarrera comme avant.

Le robot est rooté. L'appli Monsieur Cuisine fonctionne exactement comme avant.


## 7. Commander le robot par le Wi-Fi (ADB)

À partir d'ici, plus besoin de câble ni de SP Flash Tool. Tout se fait depuis la fenêtre
**Ubuntu** (ou le Terminal du Mac ou de Linux), dans le dossier du projet.

### 7.1 Activer le débogage sur le robot

1. Réglages Android (par le menu usine, étape 5.2) : **Système**, **À propos de la tablette**.
2. Touche **7 fois** la ligne **Numéro de build** : les options pour les développeurs se débloquent.
3. **Système**, **Options pour les développeurs** : active **Débogage USB**.

### 7.2 Installer ADB sur l'ordinateur

Les outils officiels de Google, sans rien installer dans le système :

```
cd ~
curl -LO https://dl.google.com/android/repository/platform-tools-latest-linux.zip
unzip -q platform-tools-latest-linux.zip
~/platform-tools/adb version
```

Sur Mac, remplace `linux` par `darwin` dans l'adresse. La dernière commande doit afficher
« Android Debug Bridge ».

### 7.3 Fabriquer et installer le module « ADB Wi-Fi »

Ce module Magisk met l'ADB à l'écoute du Wi-Fi à chaque démarrage. Il enregistre aussi la clé de
**ton** ordinateur comme autorisée : sur le Smart, la fenêtre « Autoriser le débogage ? » n'arrive
pas à s'afficher par-dessus l'appli Monsieur Cuisine.

```
cd ~/camybrith-cooking
~/platform-tools/adb start-server
bash outils/module_adb.sh
cp modules/adb-wifi-ma-cle.zip /mnt/c/MCS/
```

(La dernière ligne copie le module dans `C:\MCS` pour le retrouver depuis Windows. Sur Mac, il est
directement dans le dossier `modules` du projet.)

1. Sur le robot, démarre le serveur de **HTTP File Server**. Sur le PC, ouvre son adresse dans le
   navigateur et **envoie** `adb-wifi-ma-cle.zip` avec le bouton d'envoi de la page.
2. Dans **Magisk** : onglet **Modules**, **Installer depuis le stockage**, choisis le ZIP, puis
   **Redémarrer**.
3. Quand l'appli Monsieur Cuisine est revenue, attends **une trentaine de secondes**.

### 7.4 Autoriser le root pour l'ordinateur

Depuis la version 31, Magisk n'affiche plus de fenêtre de demande : on accorde le root d'avance.

1. Ouvre Magisk, onglet **Superutilisateur** (icône « # »).
2. Appuie sur le **+**, choisis **Shell** (parfois `com.android.shell`) et active-le, durée
   **Toujours** si on te le demande.
3. Laisse **Restrict root capabilities** désactivé.

### 7.5 Vérifier

Remplace `192.168.1.42` par l'adresse de ton robot :

```
~/platform-tools/adb connect 192.168.1.42:5555
~/platform-tools/adb shell su -c id
```

La première doit répondre `connected to…`, la seconde `uid=0(root)…`. Si c'est le cas, la partie
délicate est terminée.

> Conseil : dans les réglages de ta box, réserve une adresse IP fixe au robot (« bail DHCP
> statique »). Sinon, son adresse peut changer et il faudra la redonner aux scripts.


## 8. Installer Camybrith Cooking

Toujours dans le dossier du projet :

```
cd ~/camybrith-cooking
git pull
bash outils/telecharger.sh
bash outils/installer.sh
```

* `telecharger.sh` récupère la dernière version publiée : le **pilote moteur**, l'interface
  **rapide** et **prudente** (dans `apk/`), et l'**animation de démarrage** (dans `modules/`).
* `installer.sh` te demande la première fois l'adresse IP du robot (elle est gardée dans
  `outils/robot.conf`), installe le pilote, lui accorde le root, installe l'interface, lui accorde
  le root, la fait **compiler par Android** (une à deux minutes, n'ouvre pas l'appli pendant ce
  temps), puis l'ouvre.

Ensuite, l'animation de démarrage (facultatif mais joli) :

```
bash outils/animation.sh installer
~/platform-tools/adb -s 192.168.1.42:5555 reboot
```

Au redémarrage, tu vois l'animation Camybrith Cooking, puis l'appli s'ouvre toute seule.
L'appli Monsieur Cuisine d'origine reste installée, intacte.

Si l'appli fait des choses étranges (écran figé, plantage au bout de quelques secondes) : installe
la version prudente, plus lente mais à l'abri du problème de compilation de ce robot :
`bash outils/installer.sh prudente`.


## 9. Premier essai avec le vrai moteur

À l'installation, Camybrith Cooking démarre en mode **Démo** : le moteur est simulé, rien ne tourne.
On passe au vrai moteur prudemment, **une main sur l'interrupteur du robot**.

1. Dans l'appli : **Réglages**, rubrique **Machine**, choisis **Machine réelle**, puis
   **Continuer**. L'appli prend le contrôle du moteur à la place de l'appli d'origine. Vérifie que
   la ligne « Robot » indique bien le **Smart**.
2. **Bol vide, couvercle fermé** : cuisson manuelle, vitesse 2, 10 secondes. Le moteur tourne puis
   s'arrête seul.
3. **Bol vide** : vitesse 2, 30 secondes, et **ouvre le couvercle au milieu**. Le moteur s'arrête
   tout de suite et l'appli se met en pause. Referme : elle propose de **reprendre**.
4. **200 ml d'eau** : vitesse 1, 37 °C, 30 secondes. Le moteur tourne et l'eau tiédit.
5. **La balance** : pose un objet, le poids s'affiche.

Si l'un de ces essais ne se passe pas comme décrit, coupe le robot à l'interrupteur, repasse en
**Démo**, et ouvre un ticket (onglet **Issues** du projet) en décrivant ce que tu as vu.


## 10. Au quotidien

* **Les recettes Lidl** : **Réglages**, **Recettes Lidl**, **Synchroniser**. Elles se mettent
  ensuite à jour chaque nuit (réglable).
* **La barre de navigation** en bas de l'écran peut être cachée :
  `bash outils/barre.sh cacher` (et `bash outils/barre.sh remettre`). Sans elle, un **appui long
  sur l'heure** dans Camybrith Cooking ouvre Retour, Accueil et Applis récentes.
* **Revenir à l'appli d'origine** : **Réglages**, **Machine**, « Revenir à l'appli d'origine ».
  Camybrith Cooking rend le moteur et passe en Démo.
* **Tes propres recettes** écrites pour un Thermomix : le format du fichier et la façon de les
  envoyer sont expliqués dans [RECETTES.md](RECETTES.md). Les réglages convertis pour le Smart
  sont signalés « à vérifier » : relis-les avant de lancer.
* **Personnaliser les sons et le logo** : voir
  [personnalisation/LISEZMOI.md](../personnalisation/LISEZMOI.md).


## 11. Mettre à jour

**Depuis le robot (le plus simple)** : quand une nouvelle version sort, l'appli le signale à
l'ouverture. Va dans **Réglages › Mises à jour**, puis **Installer** : elle télécharge le pilote et
l'interface, les installe et redémarre (environ une minute, jamais pendant une cuisson).

**Depuis l'ordinateur** (si le robot n'a pas Internet, ou en dépannage) :

```
cd ~/camybrith-cooking
git pull
bash outils/telecharger.sh
bash outils/installer.sh
```

Tes favoris, réglages et recettes sont conservés.


## 12. Revenir en arrière

Du plus léger au plus complet :

1. **Juste utiliser l'appli d'origine** : Réglages, Machine, « Revenir à l'appli d'origine ».
2. **Retirer l'animation** : `bash outils/animation.sh retirer`, puis redémarre.
3. **Désinstaller Camybrith Cooking** (après le point 1) :
   ```
   ~/platform-tools/adb -s 192.168.1.42:5555 uninstall app.toque
   ~/platform-tools/adb -s 192.168.1.42:5555 uninstall app.toque.pilote
   ```
4. **Retirer le root** : réécris ton `boot.img` d'origine avec SP Flash Tool (étape 6.3 avec
   l'original). Magisk et ses modules deviennent inactifs.
5. **En cas de gros pépin**, ta sauvegarde de l'étape 2 contient tout ce qu'il faut pour remettre
   le robot dans son état d'origine. Ne tente rien au hasard : demande de l'aide dans les Issues
   du projet, avec une description précise de ce qui s'est passé.


## 13. Problèmes fréquents

**Windows voit « MT65xx Preloader » avec un triangle jaune.** Le pilote n'est pas installé :
étape 1.2.

**`pnputil` répond « Accès refusé ».** Le terminal n'est pas en administrateur.

**SP Flash Tool ne réagit pas quand j'allume le robot.** Clique sur Read Back ou Download
**avant** d'allumer. Change de port USB (à l'arrière du PC). Vérifie que le câble transporte bien
les données : branche un téléphone avec, il doit apparaître dans l'explorateur.

**SP Flash Tool parle de « PMT changed » ou veut formater.** Annule. Vérifie que tu es en
**Download Only** et que seules les bonnes lignes sont cochées.

**Le sélecteur de fichiers de Magisk est vide.** Touche ⋮ puis « Afficher la mémoire interne »
(étape 6.1). Sinon, déplace `boot.img` dans le dossier Download avec le gestionnaire de fichiers.

**`adb connect` répond « failed to authenticate » ou « unauthorized ».** Le module avec ta clé
n'est pas installé, ou le robot n'a pas fini de démarrer : vérifie dans Magisk, onglet Modules,
que « ADB Wi-Fi » est là et actif, redémarre, attends 30 secondes, puis
`~/platform-tools/adb disconnect` et recommence.

**`su -c id` répond « Permission denied ».** Shell n'a pas le root : étape 7.4.

**Un autre ordinateur doit commander le robot.** Sur cet ordinateur, fais l'étape 7.2, puis
`bash outils/module_adb.sh` et installe ce nouveau module dans Magisk : il ajoute sa clé sans
retirer les autres.

**« Le robot ne répond pas » dans les scripts.** Vérifie qu'il est allumé et sur le même réseau,
puis son adresse : si elle a changé, supprime `outils/robot.conf` et relance.

**L'appli dit « accès root refusé » en passant en Machine réelle.** Dans Magisk, onglet
Superutilisateur, active « Camybrith Cooking » et « Camybrith Cooking : pilote moteur ».

**L'appli plante ou se fige au bout de quelques secondes.** Relance `bash outils/installer.sh`
(il recompile), ou passe à la version prudente : `bash outils/installer.sh prudente`.

**Revenir de la prudente à la rapide** : la prudente porte un numéro plus élevé. Désinstalle
l'interface (`adb uninstall app.toque`, tes réglages seront perdus) puis `bash outils/installer.sh`.


## Sources et remerciements

* Méthode de root du Smart : [guide de depau](https://github.com/EliasKotlyar/Monsieur-Cuisine-Connect-Hack/issues/38#issuecomment-1928542640),
  fichiers scatter et TWRP d'[ihrapsa](https://github.com/EliasKotlyar/Monsieur-Cuisine-Connect-Hack/issues/38#issuecomment-1605329093),
  et toute la discussion [n° 38](https://github.com/EliasKotlyar/Monsieur-Cuisine-Connect-Hack/issues/38).
* [Magisk](https://github.com/topjohnwu/Magisk) par topjohnwu.
* [Toque](https://github.com/Wuki60/MrCuisineVibeConnect) par Wuki60, dont Camybrith Cooking est
  l'adaptation au Smart.
