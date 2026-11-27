#!/bin/bash
# Back to the Lidl app from the computer (spec 007, US2): the robot runs the Lidl app only again.
# Works even if the Camybrith interface is broken or uninstalled (everything is done by the rescue
# module). Camybrith stays installed, and so do root and Wi-Fi access. Messages are in French.
#
#   bash outils/origine.sh             → back to the Lidl app (asks for confirmation)
#   bash outils/origine.sh registre    → copies the registry and the reports into sauvegardes/
#   bash outils/origine.sh reprendre   → back to Camybrith (US4)
set -u
. "$(dirname "$0")/commun.sh"
connecter
exiger_root
require_rescue
# adb must not read this script's input (it would swallow the user's answers when piped).
cam() { $A shell "su -c 'sh $RESCUE $*'" </dev/null | tr -d '\r'; }
# Same, followed by a "#code=N" line with the command's exit code.
cam_code() { $A shell "su -c 'sh $RESCUE $*'; echo \"#code=\$?\"" </dev/null | tr -d '\r'; }
exit_code() { echo "$1" | sed -n 's/^#code=//p' | tail -1; }

case "${1:-}" in
  registre)
    D="$RACINE/sauvegardes/registre-$(date +%Y%m%d-%H%M%S)"
    mkdir -p "$D/bilans"
    $A shell "su -c 'cat /data/adb/camybrith/registre.json'" 2>/dev/null | tr -d '\r' > "$D/registre.json"
    for b in $($A shell "su -c 'ls /data/adb/camybrith/bilans'" 2>/dev/null | tr -d '\r'); do
      case "$b" in *.json) $A shell "su -c 'cat /data/adb/camybrith/bilans/$b'" | tr -d '\r' > "$D/bilans/$b" ;; esac
    done
    echo "Registre et bilans copiés dans $D (ce dossier n'est jamais envoyé sur GitHub)." ;;
  reprendre)
    echo "Camybrith Cooking va redevenir l'écran d'accueil et reprendre la machine : l'appli Lidl est"
    echo "fermée et recoupée du moteur. Camybrith s'ouvre ensuite et refait ses vérifications."
    echo "Aucune cuisson ne doit être en cours avec l'appli Lidl (regarde le robot)."
    read -r -p "Reprendre Camybrith ? (o/N) " answer
    case "$answer" in o|O|oui) ;; *) echo "Rien n'a été fait."; exit 0 ;; esac
    OUT=$(cam_code reprendre --ordinateur)
    CODE=$(exit_code "$OUT")
    echo "$OUT" | grep -v '^#code=' | sed 's/^/  /'
    case "$CODE" in
      0|'') echo; echo "Camybrith Cooking est de retour. Sur le robot, valide les vérifications de Machine réelle." ;;
      1) echo; echo "Refusé : rien n'a été fait."; exit 1 ;;
      *) echo; echo "La reprise n'a pas pu se faire jusqu'au bout : le robot est resté sur l'appli Lidl."; exit 2 ;;
    esac ;;
  '')
    echo "État du robot vu par le module de secours :"
    cam etat | sed 's/^/  /'
    echo
    echo "Le robot va redémarrer et fonctionner avec la seule appli Lidl. Les modifications listées"
    echo "ci-dessus (portée « lidl ») seront annulées. Camybrith Cooking reste installé (pour revenir),"
    echo "le root et l'accès Wi-Fi aussi."
    echo "Ne jamais accepter de mise à jour système Lidl tant que le robot est rooté."
    read -r -p "Revenir à l'appli Lidl ? (o/N) " answer
    case "$answer" in o|O|oui) ;; *) echo "Rien n'a été fait."; exit 0 ;; esac
    OUT=$(cam_code retour-lidl --ordinateur)
    CODE=$(exit_code "$OUT")
    if [ "$CODE" = 1 ] && echo "$OUT" | grep -q "état de cuisson inconnu"; then
      echo "$OUT" | grep -v '^#code=' | sed 's/^/  /'
      echo
      echo "Le module ne sait pas si une cuisson est en cours : l'interface s'est peut-être arrêtée"
      echo "pendant un programme. Regarde le robot. Arrêter maintenant coupe le moteur et la chauffe"
      echo "(la carte s'arrête d'elle-même quelques secondes après), sans rien démarrer."
      read -r -p "Aucune cuisson en cours, continuer quand même ? (o/N) " answer
      case "$answer" in o|O|oui) ;; *) echo "Rien n'a été fait."; exit 0 ;; esac
      OUT=$(cam_code retour-lidl --ordinateur --forcer)
      CODE=$(exit_code "$OUT")
    fi
    echo "$OUT" | grep -v '^#code=' | sed 's/^/  /'
    case "$CODE" in
      0|'') echo; echo "Le robot redémarre sur l'appli Lidl. Pour revoir ce bilan : bash outils/origine.sh registre" ;;
      1) echo; echo "Refusé : rien n'a été fait."; exit 1 ;;
      *) echo; echo "État inattendu : le module s'est arrêté sans rien faire de plus. Garde ce message."; exit 2 ;;
    esac ;;
  *) echo "Usage : bash $0 [registre|reprendre]"; exit 1 ;;
esac
