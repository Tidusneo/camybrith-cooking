# Camybrith Cooking sur le Monsieur Cuisine Connect (expérimental)

🇬🇧 **[English version](CONNECT.en.md)**

Camybrith Cooking vient de **Toque**, l'appli de Wuki60 écrite pour le Monsieur Cuisine Connect
(SKMC 1200). Le code de commande du Connect est toujours là, donc Camybrith Cooking peut aussi
tourner sur un Connect, avec son interface, ses sons et son animation.

> **Attention : version expérimentale.** Camybrith Cooking n'a **pas encore été essayé sur un vrai
> Connect**. Toque, lui, y tourne tous les jours. Si tu veux juste une appli éprouvée sur ton
> Connect, prends [Toque](https://github.com/Wuki60/MrCuisineVibeConnect). Si tu veux essayer
> Camybrith Cooking, suis ce qui suit en restant prudent, et raconte-nous comment ça s'est passé
> dans les [Issues](https://github.com/scaule/camybrith-cooking/issues).

## 1. Rooter le Connect avec le guide de Toque

Le root du Connect est différent de celui du Smart. Suis le guide de Toque jusqu'au bout de son
étape 2, ADB par Wi-Fi compris :
[guide d'installation de Toque](https://github.com/Wuki60/MrCuisineVibeConnect/blob/main/docs/INSTALLATION.fr.md).

À la fin, tu dois avoir Magisk sur le robot, et la commande
`adb connect ADRESSE_DU_ROBOT:5555` doit répondre `connected`.

Si Toque est déjà installé sur ton Connect, tu as déjà tout ça : passe à la suite.

## 2. Préparer l'ordinateur

Fais les étapes **1.5** (Ubuntu sous Windows, ou Terminal sur Mac/Linux, et le projet) et **7.2**
(ADB) du [guide du Smart](GUIDE.md). Puis vérifie le root :

```
~/platform-tools/adb connect ADRESSE_DU_ROBOT:5555
~/platform-tools/adb shell su -c id
```

La réponse doit contenir `uid=0(root)`. Sinon, dans Magisk sur le robot, onglet
**Superutilisateur**, autorise « Shell ».

## 3. Installer Camybrith Cooking

```
cd ~/camybrith-cooking
bash outils/telecharger.sh
bash outils/installer.sh
```

* Camybrith Cooking **remplace Toque** : c'est la même appli (`app.toque`), tes favoris et réglages
  sont gardés. Le pilote moteur, lui, est une appli en plus.
* Sur Android 6 (celui du Connect), l'étape de compilation est sautée : Android compile déjà
  l'appli à l'installation.
* L'animation de démarrage s'installe pareil que sur le Smart : `bash outils/animation.sh installer`.

## 4. Premier essai, bol vide

L'appli démarre en mode **Démo**. Pour le vrai moteur, **une main sur l'interrupteur principal** :

1. **Réglages**, **Machine**, **Machine réelle**, puis **Continuer**. La ligne « Robot » doit
   indiquer **Monsieur Cuisine Connect**.
2. La pastille en haut à droite doit afficher « Prête », sans « Démo ».
3. Bol vide, couvercle fermé : cuisson manuelle, 10 s, vitesse 2. Le moteur tourne puis s'arrête
   seul.
4. Ouvre le couvercle pendant un essai : le moteur s'arrête et l'appli se met en pause.
5. 30 s à 37 °C, vitesse 1, avec un peu d'eau : la température monte.
6. Balance : pose un objet de poids connu.

**Au moindre comportement anormal** : coupe l'interrupteur principal, repasse en **Démo**, et
ouvre une Issue avec le journal (**Réglages**, **Outils**, **Journal**).

## Recettes, mises à jour, retour arrière

* **Recettes Lidl** : **Réglages**, **Recettes Lidl**, **Synchroniser**. Sur le Connect, tu peux
  aussi importer celles de l'appli d'origine.
* **Mises à jour** : `bash outils/telecharger.sh` puis `bash outils/installer.sh`. Ou, plus simple,
  **Réglages › Mises à jour** dans l'appli.
* **Revenir à Toque** : désinstalle Camybrith Cooking puis réinstalle Toque avec son propre script.
  ```
  ~/platform-tools/adb -s ADRESSE_DU_ROBOT:5555 uninstall app.toque
  ~/platform-tools/adb -s ADRESSE_DU_ROBOT:5555 uninstall app.toque.pilote
  ```
  Tes favoris et réglages sont alors perdus.
