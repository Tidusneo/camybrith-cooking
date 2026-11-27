#!/bin/bash
# Barre de navigation du robot (boutons Retour, Accueil, Applis récentes en bas de l'écran).
# Sur le Smart, ce n'est pas celle d'Android mais l'appli « Navigation Bar » (nu.nav.bar),
# installée pendant le root et branchée comme service d'accessibilité.
#
#   bash outils/barre.sh cacher     → la barre disparaît tout de suite
#   bash outils/barre.sh remettre   → elle revient
#
# Sans la barre : dans Camybrith Cooking, un appui long sur l'heure ouvre Retour, Accueil et Applis récentes.
set -u
. "$(dirname "$0")/commun.sh"
connecter
exiger_root
exiger_secours
SVC=nu.nav.bar/nu.nav.bar.service.NavigationBarService
cur=$($A shell settings get secure enabled_accessibility_services | tr -d '\r')
# Inscrit au registre avant de toucher aux réglages (spec 007) : seul le retrait complet les remet.
avant=$cur; { [ -z "$avant" ] || [ "$avant" = null ]; } && avant=absent
acc=$($A shell settings get secure accessibility_enabled | tr -d '\r'); { [ -z "$acc" ] || [ "$acc" = null ]; } && acc=absent
case "${1:-}" in cacher|remettre)
  I1=$(noter reglage secure/enabled_accessibility_services "$avant" "$1" retrait) || exit 1
  I2=$(noter reglage secure/accessibility_enabled "$acc" "$1" retrait) || exit 1 ;;
esac
case "${1:-}" in
  cacher)
    new=$(echo "$cur" | tr ':' '\n' | grep -v -x "$SVC" | grep -v '^null$' | paste -sd: -)
    if [ -z "$new" ]; then
      $A shell settings delete secure enabled_accessibility_services >/dev/null
      $A shell settings put secure accessibility_enabled 0
    else
      $A shell settings put secure enabled_accessibility_services "$new"
    fi
    noter_fin "$I1" ok; noter_fin "$I2" ok
    echo "Barre cachée." ;;
  remettre)
    case ":$cur:" in *":$SVC:"*) ;; *)
      if [ -z "$cur" ] || [ "$cur" = null ]; then new=$SVC; else new="$cur:$SVC"; fi
      $A shell settings put secure enabled_accessibility_services "$new"
      $A shell settings put secure accessibility_enabled 1 ;;
    esac
    noter_fin "$I1" ok; noter_fin "$I2" ok
    echo "Barre remise." ;;
  *) echo "Usage : bash $0 cacher|remettre"; exit 1 ;;
esac
