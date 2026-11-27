#!/bin/bash
# Animation de démarrage Camybrith Cooking (module Magisk, /system n'est pas modifié).
#
#   bash outils/animation.sh installer   → installe modules/camybrith_animation.zip (ou celle que tu as fabriquée)
#   bash outils/animation.sh retirer     → remet l'animation Lidl au prochain démarrage
#
# Pour mettre ta propre animation : voir personnalisation/LISEZMOI.md.
set -u
. "$(dirname "$0")/commun.sh"
connecter
exiger_root
ID=camybrith_boot
case "${1:-}" in
  installer)
    ZIP=${2:-$(ls -t "$RACINE"/modules/camybrith_animation*.zip 2>/dev/null | head -1)}
    [ -n "$ZIP" ] && [ -s "$ZIP" ] || { echo "Il manque modules/camybrith_animation.zip (page Releases, ou fabrique-la : personnalisation/LISEZMOI.md)."; exit 1; }
    exiger_secours
    I=$(noter module "$ID" absent installe retrait) || exit 1
    $A push "$ZIP" /data/local/tmp/camybrith_animation.zip >/dev/null
    $A shell "su -c 'magisk --install-module /data/local/tmp/camybrith_animation.zip'" 2>&1 | tr -d '\r' | tail -2 | sed 's/^/  /'
    $A shell "rm -f /data/local/tmp/camybrith_animation.zip"
    if $A shell "su -c 'ls /data/adb/modules_update/$ID /data/adb/modules/$ID'" >/dev/null 2>&1; then
      noter_fin "$I" ok
      echo "Animation installée : elle s'affichera au prochain démarrage du robot."
    else
      noter_fin "$I" echec
      echo "Magisk n'a pas installé le module. Installe le ZIP à la main : Magisk > Modules > Installer depuis le stockage."
      exit 1
    fi ;;
  retirer)
    $A shell "su -c 'touch /data/adb/modules/$ID/remove'" && echo "Animation retirée au prochain démarrage." ;;
  *) echo "Usage : bash $0 installer|retirer"; exit 1 ;;
esac
