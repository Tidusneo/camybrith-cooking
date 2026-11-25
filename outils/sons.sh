#!/bin/bash
# Sons personnels. Tout fichier .wav, .ogg ou .mp3 du dossier personnalisation/sons nommé
# demarrage, debut, fin, recette, alerte, pause, pesee, trop ou appui remplace le son de l'appli du même nom.
# compte (facultatif) est joué quand il reste sa durée dans un programme, pour finir pile avec lui.
#
#   bash outils/sons.sh envoyer   → envoie tes sons au robot
#   bash outils/sons.sh retirer   → enlève tous les sons personnels (retour aux sons fournis)
#
# Ensuite, dans l'appli : Réglages, « Recharger les sons » (ou rouvre l'appli).
set -u
. "$(dirname "$0")/commun.sh"
S="$RACINE/personnalisation/sons"
R=/sdcard/Android/data/app.toque/files/sons
connecter
case "${1:-}" in
  retirer)
    $A shell "rm -f $R/*" && echo "Sons personnels retirés. Dans l'appli : Réglages, Recharger les sons." ;;
  envoyer)
    $A shell "mkdir -p $R"
    n=0
    for k in demarrage debut fin recette alerte pause pesee trop appui compte; do
      for e in wav ogg mp3; do
        f="$S/$k.$e"
        [ -s "$f" ] || continue
        # Un seul fichier par son sur le robot (l'appli prend d'abord .wav, puis .ogg, puis .mp3).
        $A shell "rm -f $R/$k.wav $R/$k.ogg $R/$k.mp3"
        $A push "$f" "$R/$k.$e" >/dev/null && echo "  $k.$e" && n=$((n + 1))
        break
      done
    done
    if [ "$n" = 0 ]; then echo "Aucun son trouvé dans personnalisation/sons (voir personnalisation/LISEZMOI.md)."
    else echo "$n son(s) envoyé(s). Dans l'appli : Réglages, Recharger les sons (ou rouvre l'appli)."; fi ;;
  *) echo "Usage : bash $0 envoyer|retirer"; exit 1 ;;
esac
