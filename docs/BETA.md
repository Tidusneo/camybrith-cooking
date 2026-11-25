# Tester une version avant de la publier (branche `beta`)

Deux branches, deux publics :

* **`main`** : la version publique. Quand `versionName` change dans `app/build.gradle.kts`, l'action
  « Publier une version » publie la version, et tout le monde la reçoit (mise à jour depuis
  l'appli, `bash outils/telecharger.sh`).
* **`beta`** : la version de test. Chaque push sur `beta` construit les APK et les publie en
  **préversion** sous le nom `latest-beta`, remplacée à chaque push. Elle n'est jamais marquée
  « dernière version » : l'appli et `telecharger.sh` sans option ne la voient pas.

## Le cycle

1. **Travailler sur `beta`**
   ```
   cd ~/camybrith-cooking
   git fetch && git checkout beta && git pull
   ```
   Fais tes changements, puis `git push`.

2. **Attendre la construction** : onglet **Actions** du dépôt, « Publier une version », une dizaine
   de minutes. La préversion apparaît ensuite dans **Releases** avec le numéro du commit.

3. **L'installer sur ton robot**, au choix :
   * **depuis le robot** : Réglages › Mises à jour, active **Recevoir les bêtas**, puis
     « Rechercher une mise à jour ». Le robot propose chaque nouvelle bêta comme une mise à jour
     (« 1.4.2 bêta (6eb3b1d) »), la télécharge, l'installe et redémarre ;
   * **depuis l'ordinateur** :
     ```
     bash outils/telecharger.sh beta
     bash outils/installer.sh beta
     ```
     `installer.sh beta prudente` installe l'interface prudente de la bêta. Les fichiers de la bêta
     vont dans `apk/beta/`, ceux de la version publique restent dans `apk/`.

   Une bêta installée affiche son commit dans le numéro de version (Réglages › Mises à jour :
   « 1.4.2-rapide-beta-6eb3b1d »). Elle garde le `versionCode` de la version dont elle part, ce qui
   lui permet de s'installer par-dessus. Quand une version publique plus récente sort, le robot la
   propose, bêtas activées ou non.

4. **Tester**, recommencer 1 à 3 autant que nécessaire.

5. **Publier pour tout le monde**, quand la bêta est validée :
   * dans `app/build.gradle.kts`, augmente `versionCode` (les deux valeurs) et `versionName` ;
   * ajoute la section de cette version dans `CHANGELOG.md` (elle sert de notes de version) ;
   * fusionne dans `main` :
     ```
     git checkout main && git pull && git merge beta && git push
     git checkout beta
     ```
   L'action publie alors la version publique, comme avant.

## Revenir à la version publique sur ton robot

```
bash outils/telecharger.sh
bash outils/installer.sh
```

Attention aux numéros : Android refuse d'installer une version dont le `versionCode` est **plus
petit** que celui déjà installé. Si tu as augmenté `versionCode` sur `beta` sans publier ensuite,
le retour à la version publique plus ancienne échoue, et il faudrait désinstaller l'appli (favoris
et réglages perdus). Le plus simple : n'augmente `versionCode` qu'au moment de publier (étape 5).
Une bêta et une version publique de même `versionCode` s'installent l'une par-dessus l'autre.
