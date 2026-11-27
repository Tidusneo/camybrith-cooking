#!/bin/bash
# Camybrith rescue module (spec 007): registry of changes and return to the Lidl app.
#
#   bash outils/secours.sh installer   → installs the module, records what is already modified
#   bash outils/secours.sh etat        → robot status as seen by the module (nothing is changed)
#   bash outils/secours.sh retirer     → removes the module at the next boot
#
# The module is modules/camybrith_secours.zip (Releases page, via outils/telecharger.sh, or built
# with bash outils/fabriquer_secours.sh). It does not modify /system. Messages are in French.
set -u
. "$(dirname "$0")/commun.sh"
connecter
exiger_root
ID=camybrith_secours
usage() { echo "Usage : bash $0 installer|etat|retirer"; exit 1; }
case "${1:-}" in
  installer)
    ZIP=${2:-$(ls -t "$RACINE"/modules/camybrith_secours*.zip 2>/dev/null | head -1)}
    [ -n "$ZIP" ] && [ -s "$ZIP" ] || { echo "Il manque modules/camybrith_secours.zip : bash outils/telecharger.sh (ou bash outils/fabriquer_secours.sh)."; exit 1; }
    echo "Ce script va :"
    echo "  1. installer le module Magisk « Secours Camybrith » (aucune modification de /system) ;"
    echo "  2. relever ce que Camybrith a déjà modifié sur le robot et l'inscrire au registre"
    echo "     (/data/adb/camybrith), pour pouvoir toujours revenir à l'appli Lidl."
    read -r -p "Continuer ? (o/N) " answer
    case "$answer" in o|O|oui) ;; *) echo "Rien n'a été fait."; exit 0 ;; esac
    $A push "$ZIP" /data/local/tmp/camybrith_secours.zip >/dev/null
    $A shell "su -c 'magisk --install-module /data/local/tmp/camybrith_secours.zip'" 2>&1 | tr -d '\r' | tail -2 | sed 's/^/  /'
    $A shell "rm -f /data/local/tmp/camybrith_secours.zip"
    CMD=$(rescue_path)
    [ -n "$CMD" ] || { echo "Magisk n'a pas installé le module. Installe le ZIP à la main : Magisk > Modules > Installer depuis le stockage."; exit 1; }
    $A shell "su -c 'sh $CMD inscrire'" | tr -d '\r' | sed 's/^/  /'
    echo
    echo "Module installé. Il sera actif au prochain démarrage du robot."
    read -r -p "Redémarrer le robot maintenant ? (o/N) " answer
    case "$answer" in o|O|oui) $A shell "su -c 'svc power reboot'" ;; esac ;;
  etat)
    CMD=$(rescue_path)
    [ -n "$CMD" ] || { echo "Le module de secours n'est pas installé : bash outils/secours.sh installer"; exit 1; }
    $A shell "su -c 'sh $CMD etat'" | tr -d '\r' ;;
  retirer)
    echo "Sans le module, le registre n'est plus tenu et le retour à l'appli Lidl se fait en mode dégradé."
    read -r -p "Retirer le module au prochain démarrage ? (o/N) " answer
    case "$answer" in o|O|oui) $A shell "su -c 'touch /data/adb/modules/$ID/remove'" && echo "Module retiré au prochain démarrage." ;; *) echo "Rien n'a été fait." ;; esac ;;
  *) usage ;;
esac
