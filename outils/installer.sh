#!/bin/bash
# Installe (ou met à jour) Camybrith Cooking sur le robot : le pilote moteur puis l'interface.
#
#   bash outils/installer.sh              → version rapide (conseillée)
#   bash outils/installer.sh prudente     → version prudente, à utiliser si la rapide fait des bêtises
#
# Les APK sont pris dans le dossier apk/ à côté de ce dossier outils (téléchargés depuis la page
# « Releases » du projet) : Camybrith-pilote-*.apk et Camybrith-*-rapide.apk ou *-prudente.apk.
# À lancer robot à l'arrêt, sans cuisson en cours.
set -u
. "$(dirname "$0")/commun.sh"
MODE=${1:-rapide}
case "$MODE" in rapide|prudente) ;; *) echo "Usage : bash $0 [rapide|prudente]"; exit 1 ;; esac

D="$RACINE/apk"
PILOTE=$(ls -t "$D"/Camybrith-pilote-*.apk 2>/dev/null | head -1)
APP=$(ls -t "$D"/Camybrith-*-"$MODE".apk 2>/dev/null | head -1)
[ -n "$PILOTE" ] || { echo "Il manque Camybrith-pilote-….apk dans le dossier apk/ : lance d'abord bash outils/telecharger.sh."; exit 1; }
[ -n "$APP" ] || { echo "Il manque Camybrith-…-$MODE.apk dans le dossier apk/ : lance d'abord bash outils/telecharger.sh."; exit 1; }

connecter
exiger_root
require_rescue

# Spec 007: recorded in the registry BEFORE installing: the apps (removed only by a full removal)
# and Camybrith's startup (undone by going back to the Lidl app).
ID_PILOT=$(note appli app.toque.pilote absent installee retrait) || exit 1
ID_APP=$(note appli app.toque absent installee retrait) || exit 1
ID_BOOT=$(note demarrage app.toque/app.toque.recipes.BootReceiver absent enabled lidl) || exit 1
ID_HOME=$(note demarrage app.toque/app.toque.ui.Accueil absent enabled lidl) || exit 1

# Magisk 31 n'affiche plus de fenêtre de demande : le root s'accorde d'avance, ici pour le pilote
# (port série de la carte moteur) et pour l'interface (couper l'appli Lidl du moteur, commandes Android).
accorder_root() {
  local pkg=$1 nom=$2 uid
  uid=$($A shell dumpsys package "$pkg" | tr -d '\r' | grep -o 'userId=[0-9]*' | head -1 | cut -d= -f2)
  [ -n "$uid" ] || return 1
  $A shell "su -c \"magisk --sqlite 'REPLACE INTO policies (uid,policy,until,logging,notification) VALUES($uid,2,0,1,0)'\"" >/dev/null 2>&1
  if $A shell "su -c \"magisk --sqlite 'SELECT policy FROM policies WHERE uid=$uid'\"" 2>/dev/null | tr -d '\r' | grep -q "policy=2"; then
    echo "     Root accordé à « $nom »."
  else
    echo "     Pas pu accorder le root d'ici. Dans Magisk, onglet Superutilisateur : active « $nom »."
  fi
}

echo "1/5  Pilote moteur : $(basename "$PILOTE")…"
$A install -r "$PILOTE" | tail -1
UID_P=$($A shell dumpsys package app.toque.pilote | tr -d '\r' | grep -o 'userId=[0-9]*' | head -1 | cut -d= -f2)
if [ -z "$UID_P" ]; then
  for i in $ID_PILOT $ID_APP $ID_BOOT $ID_HOME; do note_done "$i" echec; done
  echo "Le pilote ne s'est pas installé : rien d'autre n'a été fait."; exit 1
fi
note_done "$ID_PILOT" ok
accorder_root app.toque.pilote "Camybrith Cooking : pilote moteur"

echo "2/5  Interface : $(basename "$APP")…"
OUT=$($A install -r "$APP" 2>&1 | tail -1)
echo "     $OUT"
case "$OUT" in
  *VERSION_DOWNGRADE*)
    echo "     La version installée est plus récente (par exemple la prudente). Pour revenir à la rapide :"
    echo "     $ADB -s $IP:5555 uninstall app.toque   puis relance ce script (favoris et réglages seront perdus)."
    for i in $ID_APP $ID_BOOT $ID_HOME; do note_done "$i" echec; done
    exit 1 ;;
  *Success*) for i in $ID_APP $ID_BOOT $ID_HOME; do note_done "$i" ok; done ;;
  *) for i in $ID_APP $ID_BOOT $ID_HOME; do note_done "$i" echec; done
     echo "     L'installation a échoué."; exit 1 ;;
esac

echo "3/5  Accès root de l'interface…"
accorder_root app.toque "Camybrith Cooking"

$A shell am force-stop app.toque
SDK=$($A shell getprop ro.build.version.sdk | tr -d '\r')
if [ "$MODE" = rapide ] && [ "${SDK:-0}" -lt 24 ] 2>/dev/null; then
  # Android 6 (Monsieur Cuisine Connect) compile déjà toute l'appli à l'installation.
  echo "4/5  Android ${SDK} : l'appli est déjà compilée à l'installation."
elif [ "$MODE" = rapide ]; then
  # Sur ce robot, la compilation « au fil de l'eau » d'Android (JIT) fait planter l'interface :
  # on la fait compiler entièrement avant la première ouverture.
  echo "4/5  Compilation de l'interface par Android (une à deux minutes, n'ouvre pas l'appli avant)…"
  $A shell cmd package compile -m speed -f app.toque | tr -d '\r' | tail -1 | sed 's/^/     /'
  $A shell am force-stop app.toque
else
  echo "4/5  Version prudente : pas de compilation."
fi

echo "5/5  Ouverture…"
$A shell am start -n app.toque/.ui.MainActivity >/dev/null
echo "     Interface : $($A shell dumpsys package app.toque | tr -d '\r' | grep -m1 -o 'versionName=.*')"
echo "     Pilote    : $($A shell dumpsys package app.toque.pilote | tr -d '\r' | grep -m1 -o 'versionName=.*')"
echo "C'est fini. Fais maintenant le premier essai décrit dans le guide (étape 9)."
