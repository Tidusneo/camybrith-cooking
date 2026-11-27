#!/bin/bash
# Full removal of Camybrith (spec 007, US5): back to the Lidl app, then removal of the Camybrith
# apps, the boot animation and the rescue module. Root (Magisk) and Wi-Fi access stay. Navigation
# Bar and HTTP File Server are removed only if the user says so. Messages are in French.
#
#   bash outils/retirer.sh
set -u
. "$(dirname "$0")/commun.sh"
connecter
exiger_root
require_rescue
cam() { $A shell "su -c 'sh $RESCUE $*'" </dev/null | tr -d '\r'; }
ask() { local answer; read -r -p "$1 (o/N) " answer; case "$answer" in o|O|oui) return 0 ;; *) return 1 ;; esac; }

echo "Ce script retire Camybrith Cooking du robot :"
echo "  - retour à l'appli Lidl (elle retrouve le moteur et l'écran d'accueil) ;"
echo "  - désinstallation de l'interface et du pilote Camybrith, de l'animation de démarrage ;"
echo "  - retrait du module de secours et de ses données (/data/adb/camybrith)."
echo "Restent : le root (Magisk) et l'accès Wi-Fi (ADB), pour pouvoir réinstaller plus tard."
echo "Les favoris et recettes perso de Camybrith seront perdus."
echo
echo "État actuel :"
cam etat | sed 's/^/  /'
echo
# Keep a trace on the computer first (the registry disappears with the module).
bash "$(dirname "$0")/origine.sh" registre </dev/null | sed 's/^/  /'
FLAGS=""
ask "Retirer aussi Navigation Bar (la barre Retour / Accueil en bas de l'écran) ?" && FLAGS="$FLAGS --avec-barre"
ask "Retirer aussi HTTP File Server (sert à envoyer des fichiers au robot) ?" && FLAGS="$FLAGS --avec-serveur"
ask "Tout retirer maintenant ?" || { echo "Rien n'a été fait."; exit 0; }
OUT=$($A shell "su -c 'sh $RESCUE retrait$FLAGS'; echo \"#code=\$?\"" </dev/null | tr -d '\r')
CODE=$(echo "$OUT" | sed -n 's/^#code=//p' | tail -1)
echo "$OUT" | grep -v '^#code=' | sed 's/^/  /'
case "$CODE" in
  0) echo; echo "C'est fait. Redémarre le robot pour finir (le module de secours part au démarrage)." ;;
  1) echo; echo "Refusé : rien n'a été fait."; exit 1 ;;
  *) echo; echo "Le retrait n'est pas allé au bout : le robot est sur l'appli Lidl, le module est gardé. Garde ce message."; exit 2 ;;
esac
