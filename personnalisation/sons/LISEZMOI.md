# Tes sons

Dépose ici tes sons, puis lance `bash outils/sons.sh envoyer`.

Chaque fichier doit porter exactement l'un de ces noms, en `.wav`, `.ogg` ou `.mp3` :

| Nom | Quand il est joué |
|---|---|
| `demarrage` | à l'ouverture de l'appli, une seule fois par allumage du robot |
| `debut` | au lancement d'un programme |
| `fin` | quand un programme est terminé |
| `recette` | quand une recette est terminée |
| `alerte` | en cas de problème à acquitter |
| `pause` | quand le robot se met en pause tout seul (couvercle ouvert, bol retiré) |
| `pesee` | quand le poids demandé est atteint sur la balance |
| `trop` | quand on a dépassé le poids demandé |
| `appui` | à chaque appui sur un bouton (garde-le très court) |
| `compte` | facultatif, aucun son fourni : compte à rebours de fin de programme. Il démarre quand il reste sa durée (un son de 9 s part à 9 s de la fin) pour se terminer pile avec le programme. Rien pour le turbo ni pour un programme plus court que le son |

Exemple : `fin.mp3` remplace le son de fin. Les noms absents gardent le son fourni avec l'appli.

Conseils : des fichiers courts (moins de 10 secondes, sauf `demarrage` et `compte`) et pas trop forts. Pour
revenir aux sons d'origine : `bash outils/sons.sh retirer`.

Les fichiers de ce dossier restent sur ton ordinateur : ils ne sont jamais envoyés sur GitHub.
