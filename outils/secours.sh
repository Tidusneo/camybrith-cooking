#!/bin/bash
# Module de secours Camybrith (spec 007) : registre des modifications et retour à l'appli Lidl.
#
#   bash outils/secours.sh installer   → installe le module, inscrit ce qui est déjà modifié
#   bash outils/secours.sh etat        → état du robot vu par le module (rien n'est modifié)
#   bash outils/secours.sh retirer     → retire le module au prochain démarrage
#
# Le module se trouve dans modules/camybrith_secours.zip (page Releases, via outils/telecharger.sh,
# ou fabriqué avec bash outils/fabriquer_secours.sh). Il ne modifie pas /system.
set -u
. "$(dirname "$0")/commun.sh"
connecter
exiger_root
ID=camybrith_secours
case "${1:-}" in
  installer)
    ZIP=${2:-$(ls -t "$RACINE"/modules/camybrith_secours*.zip 2>/dev/null | head -1)}
    [ -n "$ZIP" ] && [ -s "$ZIP" ] || { echo "Il manque modules/camybrith_secours.zip : bash outils/telecharger.sh (ou bash outils/fabriquer_secours.sh)."; exit 1; }
    echo "Ce script va :"
    echo "  1. installer le module Magisk « Secours Camybrith » (aucune modification de /system) ;"
    echo "  2. relever ce que Camybrith a déjà modifié sur le robot et l'inscrire au registre"
    echo "     (/data/adb/camybrith), pour pouvoir toujours revenir à l'appli Lidl."
    read -r -p "Continuer ? (o/N) " rep
    case "$rep" in o|O|oui) ;; *) echo "Rien n'a été fait."; exit 0 ;; esac
    $A push "$ZIP" /data/local/tmp/camybrith_secours.zip >/dev/null
    $A shell "su -c 'magisk --install-module /data/local/tmp/camybrith_secours.zip'" 2>&1 | tr -d '\r' | tail -2 | sed 's/^/  /'
    $A shell "rm -f /data/local/tmp/camybrith_secours.zip"
    C=$(chemin_secours)
    [ -n "$C" ] || { echo "Magisk n'a pas installé le module. Installe le ZIP à la main : Magisk > Modules > Installer depuis le stockage."; exit 1; }
    $A shell "su -c 'sh $C inscrire'" | tr -d '\r' | sed 's/^/  /'
    echo
    echo "Module installé. Il sera actif au prochain démarrage du robot."
    read -r -p "Redémarrer le robot maintenant ? (o/N) " rep
    case "$rep" in o|O|oui) $A shell "su -c 'svc power reboot'" ;; esac ;;
  etat)
    C=$(chemin_secours)
    [ -n "$C" ] || { echo "Le module de secours n'est pas installé : bash outils/secours.sh installer"; exit 1; }
    $A shell "su -c 'sh $C etat'" | tr -d '\r' ;;
  retirer)
    echo "Sans le module, le registre n'est plus tenu et le retour à l'appli Lidl se fait en mode dégradé."
    read -r -p "Retirer le module au prochain démarrage ? (o/N) " rep
    case "$rep" in o|O|oui) $A shell "su -c 'touch /data/adb/modules/$ID/remove'" && echo "Module retiré au prochain démarrage." ;; *) echo "Rien n'a été fait." ;; esac ;;
  *) echo "Usage : bash $0 installer|etat|retirer"; exit 1 ;;
esac
