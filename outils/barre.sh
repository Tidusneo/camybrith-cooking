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
require_rescue
SVC=nu.nav.bar/nu.nav.bar.service.NavigationBarService
cur=$($A shell settings get secure enabled_accessibility_services | tr -d '\r')
# Spec 007: recorded in the registry before touching the settings; only a full removal restores them.
before=$cur; { [ -z "$before" ] || [ "$before" = null ]; } && before=absent
acc=$($A shell settings get secure accessibility_enabled | tr -d '\r'); { [ -z "$acc" ] || [ "$acc" = null ]; } && acc=absent
case "${1:-}" in cacher|remettre)
  ID1=$(note reglage secure/enabled_accessibility_services "$before" "$1" retrait) || exit 1
  ID2=$(note reglage secure/accessibility_enabled "$acc" "$1" retrait) || exit 1 ;;
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
    note_done "$ID1" ok; note_done "$ID2" ok
    echo "Barre cachée." ;;
  remettre)
    case ":$cur:" in *":$SVC:"*) ;; *)
      if [ -z "$cur" ] || [ "$cur" = null ]; then new=$SVC; else new="$cur:$SVC"; fi
      $A shell settings put secure enabled_accessibility_services "$new"
      $A shell settings put secure accessibility_enabled 1 ;;
    esac
    note_done "$ID1" ok; note_done "$ID2" ok
    echo "Barre remise." ;;
  *) echo "Usage : bash $0 cacher|remettre"; exit 1 ;;
esac
