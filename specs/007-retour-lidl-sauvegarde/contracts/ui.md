# Contract: écrans de l'interface

Textes en français (clés i18n), traduits en anglais et espagnol. Aucun de ces écrans ne propose
d'action qui démarre le moteur ou la chauffe.

## Réglages › « Revenir à l'appli Lidl » (remplace « Revenir à l'appli d'origine »)

1. Refus pendant une cuisson (« Arrêtez d'abord le programme en cours »).
2. Si le module de secours est absent : explication et commande à lancer depuis l'ordinateur
   (`bash outils/secours.sh installer`) ; le retour reste possible en mode dégradé (comportement
   actuel de `restoreVendor`), signalé comme tel.
3. Écran de confirmation : ce qui sera annulé (liste lisible des modifications de portée `lidl`),
   ce qui restera (Camybrith installé, root, accès Wi-Fi), et le rappel : « Ne jamais accepter de
   mise à jour système Lidl tant que le robot est rooté ».
4. « Revenir » → `camybrith retour-lidl` → le robot redémarre sur l'appli Lidl.

## Bulle « Camybrith » en mode Lidl

Affichée par-dessus l'appli Lidl à chaque démarrage en mode Lidl. Petite, dans un coin ;
déplaçable ; un appui long la réduit à un onglet sur le bord (et inversement) ; **pas de
fermeture**. Un appui ouvre Camybrith sur le bandeau ci-dessous.

## Ouverture de Camybrith après un retour (par la bulle)

Bandeau : « Le robot fonctionne avec l'appli Lidl depuis le <date> » (ou « … suite à des plantages de Camybrith Cooking
au démarrage » si `secours`, « … suite au geste de l'interrupteur » si `geste`), lien vers le bilan, boutons **Reprendre Camybrith** (`camybrith
reprendre`, puis vérifications de Machine réelle) et **Rester sur l'appli Lidl**.

## Réglages › « Revenir à l'appli Lidl » : aide

Sous le bouton, une ligne d'aide : « Écran figé ? Éteignez et rallumez le robot 3 fois de suite
(moins de 2 minutes à chaque fois) : il repartira sur l'appli Lidl. »

## Réglages › « Sauvegardes »

| Élément | Comportement |
|---|---|
| Espace libre | « Espace libre : 1,2 Go (15 %) » ; en orange sous 10 % avec « La mémoire du robot est presque pleine : supprimez d'anciennes sauvegardes ou des photos de recettes. » |
| **Sauvegarder maintenant** | vérifie l'espace (taille estimée + 50 Mo), sinon refus et proposition de supprimer les plus anciennes ; crée une sauvegarde ; pendant une cuisson, propose « à la fin de la cuisson » ; affiche le résumé |
| Liste | date, taille, résumé ; **Restaurer**, **Supprimer** |
| **Restaurer** | validation → résumé (ce qui sera remplacé, secrets à ressaisir, autre robot) → confirmation → sauvegarde automatique de l'état actuel → restauration (transaction) → redémarrage de l'appli ; refusé pendant une cuisson |

## Réglages › « Modifications du robot » (lecture seule)

Liste du registre : ce qui a été changé, quand, par quoi, état. Sert à comprendre le bilan.
