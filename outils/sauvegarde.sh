#!/bin/bash
# Manual backups of Camybrith data (spec 007, US6): favorites, own and imported recipes, settings
# without secrets, pilot settings. Never Lidl recipes, never photos. Messages are in French.
#
#   bash outils/sauvegarde.sh creer            → creates a backup on the robot
#   bash outils/sauvegarde.sh lister           → lists the backups on the robot
#   bash outils/sauvegarde.sh recuperer [nom]  → copies one (the latest by default) into sauvegardes/
#   bash outils/sauvegarde.sh envoyer FICHIER  → puts a backup from the computer onto the robot
#   bash outils/sauvegarde.sh restaurer NOM    → explains how to restore it (done on the robot)
set -u
. "$(dirname "$0")/commun.sh"
connecter
exiger_root
require_rescue
DIR=/data/adb/camybrith/sauvegardes
list() { $A shell "su -c 'ls $DIR 2>/dev/null'" </dev/null | tr -d '\r' | grep -E '^camybrith-[0-9]{8}-[0-9]{6}\.camybrith$' | sort -r; }
case "${1:-}" in
  creer)
    $A shell "su -c 'sh $RESCUE sauvegarder'" </dev/null | tr -d '\r' | sed 's/^/  /' ;;
  lister)
    N=$(list)
    if [ -z "$N" ]; then echo "Aucune sauvegarde sur le robot (bash outils/sauvegarde.sh creer)."; else echo "$N"; fi ;;
  recuperer)
    NAME=${2:-$(list | head -1)}
    case "$NAME" in camybrith-[0-9]*.camybrith) ;; *) echo "Aucune sauvegarde à récupérer (bash outils/sauvegarde.sh creer)."; exit 1 ;; esac
    mkdir -p "$RACINE/sauvegardes"
    # exec-out keeps the zip intact (no line-ending conversion).
    $A exec-out "su -c 'cat $DIR/$NAME'" > "$RACINE/sauvegardes/$NAME" </dev/null
    if unzip -tq "$RACINE/sauvegardes/$NAME" >/dev/null 2>&1; then
      echo "Sauvegarde copiée : sauvegardes/$NAME (jamais envoyée sur GitHub)."
    else
      echo "La copie est abîmée : recommence."; rm -f "$RACINE/sauvegardes/$NAME"; exit 1
    fi ;;
  envoyer)
    F=${2:-}
    NAME=$(basename "${F:-x}")
    case "$NAME" in camybrith-[0-9]*.camybrith) ;; *) echo "Donne le chemin d'un fichier camybrith-AAAAMMJJ-HHMMSS.camybrith."; exit 1 ;; esac
    [ -s "$F" ] && unzip -tq "$F" >/dev/null 2>&1 || { echo "Fichier absent ou abîmé : $F"; exit 1; }
    $A push "$F" "/data/local/tmp/$NAME" >/dev/null </dev/null
    $A shell "su -c 'mkdir -p $DIR && chmod 700 $DIR && mv /data/local/tmp/$NAME $DIR/$NAME && chmod 600 $DIR/$NAME'" </dev/null
    echo "Sauvegarde envoyée sur le robot : $NAME. Pour la restaurer : Réglages › Sauvegardes › Restaurer." ;;
  restaurer)
    NAME=${2:-}
    list | grep -qx "$NAME" || { echo "Sauvegarde inconnue sur le robot : « $NAME » (bash outils/sauvegarde.sh lister)."; exit 1; }
    echo "La restauration se fait sur le robot, pour voir ce qui sera remplacé avant de confirmer :"
    echo "  Réglages › Sauvegardes › Restaurer : ${NAME#camybrith-}"
    echo "L'état actuel est sauvegardé automatiquement avant, et la restauration est refusée pendant une cuisson." ;;
  *) echo "Usage : bash $0 creer|lister|recuperer [nom]|envoyer FICHIER|restaurer NOM"; exit 1 ;;
esac
