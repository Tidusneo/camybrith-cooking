# Quickstart: valider le retour à l'appli Lidl et la sauvegarde

**Feature**: 007-retour-lidl-sauvegarde

Références : [contracts/cli.md](contracts/cli.md), [contracts/ui.md](contracts/ui.md),
[data-model.md](data-model.md).

## A. Sans robot ni émulateur

Prérequis : JDK 17.

```text
./gradlew :core:test
```

Attendu : tests du registre (ordre d'annulation, fusion, cibles invalides refusées), du journal,
de la règle de secours et des sauvegardes (secrets, validation, conversions) au vert.

## B. Banc d'essai sur émulateur (sans toucher au robot)

Prérequis : JDK 17, SDK Android (émulateur et image Android 8.1 ou la plus proche).

```text
bash outils/banc-essai.sh
```

Le script crée l'émulateur, installe la fausse appli Lidl, Camybrith en mode Démo, le module de
secours et la doublure `magisk`, puis déroule ces scénarios et affiche OK/ÉCHEC pour chacun :

| # | Scénario | Attendu |
|---|---|---|
| B1 | Passage en Machine réelle simulé | service « moteur » de la fausse appli Lidl désactivé ET inscrit au registre avant |
| B2 | `retour-lidl` (chronométré) | moins de 3 min ; service réactivé, écran d'accueil résolu vers la fausse appli Lidl, `BootReceiver` et `.ui.Accueil` désactivés, processus Camybrith arrêtés, bilan sans échec, redémarrage sur la fausse appli Lidl |
| B3 | Redémarrer 3 fois | toujours la fausse appli Lidl au premier plan |
| B4 | `reprendre` | Camybrith redevient l'accueil, favoris intacts |
| B5 | Coupure pendant `retour-lidl` : à chaque étape, puis 20 fois au hasard (`B5 aleatoire`) | au redémarrage, retour terminé, journal effacé, 20/20 |
| B6 | Version de test qui plante au démarrage | après 2 plantages, retour automatique, `secours.json` présent |
| B7 | Interface désinstallée à la main | `outils/origine.sh` fonctionne quand même |
| B8 | Sauvegarde, récupération, restauration | données revenues ; aucune valeur secrète dans le fichier |
| B10 | Geste : 3 démarrages courts de l'émulateur (arrêt avant 2 min) | 3ᵉ démarrage sur la fausse appli Lidl, bilan « geste » ; 10/10 |
| B11 | Usage normal : redémarrages espacés de plus de 2 min | jamais de déclenchement ; 100/100 (mode long) |
| B13 | Compteur du geste corrompu ou illisible | démarrage normal, compteur remis à 0, aucun retour déclenché |
| B14 | Mémoire de l'émulateur remplie à 100 % | retour à l'appli Lidl, geste et démarrage fonctionnent ; bilan marqué « mémoire pleine » ; sauvegarde refusée proprement ; alerte d'espace affichée |
| B12 | Bulle en mode Lidl | bulle affichée, déplaçable, réductible, non fermable ; un appui ouvre le bandeau ; « Reprendre » en 3 gestes au plus |
| B9 | `retrait` | plus d'appli ni de module du projet ; fausse appli Lidl fonctionnelle |

## C. Sur le vrai robot (validation finale)

Prérequis : robot rooté avec Camybrith (guide, étapes 1 à 8), **sauvegarde complète de l'étape 2
disponible**, robot au repos, bol vide, main près de l'interrupteur.

1. `bash outils/secours.sh installer` puis `bash outils/secours.sh etat` : registre initialisé
   avec les modifications déduites (service moteur Lidl, applis, socle).
2. Réglages › **Revenir à l'appli Lidl** : confirmation avec la liste et le rappel ; le robot
   redémarre sur l'appli Lidl (chronométrer : moins de 3 min). Vérifier dans le bilan que l'écran
   d'accueil pointe vers l'appli Lidl (point non vérifiable sur l'émulateur).
3. Avec l'appli Lidl : une pesée, puis une cuisson courte (2 min, 37 °C, vitesse 1). Attendu : tout
   fonctionne (SC-002).
4. Redémarrer deux fois : toujours l'appli Lidl.
5. Ouvrir Camybrith par la barre de navigation : bandeau, **Reprendre Camybrith** ; vérifications de
   Machine réelle ; une pesée avec Camybrith.
6. Depuis le Mac : `bash outils/origine.sh` puis `bash outils/origine.sh reprendre`.
6 bis. Geste : allumer, attendre le logo, éteindre à l'interrupteur ; trois fois ; rallumer.
   Attendu : appli Lidl et bulle « Camybrith ». Toucher la bulle › Reprendre Camybrith.
7. Sauvegarde : **Sauvegarder maintenant**, `bash outils/sauvegarde.sh recuperer`, restauration.
