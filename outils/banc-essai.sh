#!/bin/bash
# Banc d'essai sur émulateur (spec 007, quickstart §B) : teste le retour à l'appli Lidl SANS le
# robot. Un émulateur Android 8.1 reçoit une FAUSSE appli Lidl (banc/fausse-lidl), Camybrith et
# le module de secours ; chaque scénario affiche OK ou ÉCHEC.
#
#   bash outils/banc-essai.sh              # tous les scénarios disponibles
#   bash outils/banc-essai.sh B2 B3        # seulement ceux-là
#   bash outils/banc-essai.sh B5 aleatoire # coupures à des instants tirés au hasard (20 essais)
#   bash outils/banc-essai.sh arreter      # éteint l'émulateur
#
# Prérequis : JDK 17 et SDK Android (README, « Fabriquer les APK soi-même »). Rien n'est envoyé au
# robot : toutes les commandes visent l'émulateur emulator-5580.
#
# Limites (research.md D11) : pas de vrai Magisk (le module est copié à la main et service.sh lancé
# par ce script à chaque démarrage), pas de su pour l'interface (B1 passe par la commande du module),
# pas de vrai service moteur ni de port série.
set -u
RACINE=$(cd "$(dirname "$0")/.." && pwd)
SDK=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}
[ -d "$SDK" ] || SDK=$HOME/Android/Sdk
ADB="$SDK/platform-tools/adb"
EMU="$SDK/emulator/emulator"
AVD=camybrith-banc
PORT=5580
SERIE=emulator-$PORT
case "$(uname -m)" in arm64|aarch64) ABI=arm64-v8a ;; *) ABI=x86_64 ;; esac
IMAGE="system-images;android-27;default;$ABI"
if [ -z "${JAVA_HOME:-}" ] && [ -d /opt/homebrew/opt/openjdk@17 ]; then export JAVA_HOME=/opt/homebrew/opt/openjdk@17; fi
MOD=/data/adb/modules/camybrith_secours
CAM=/data/adb/camybrith
LIDL=com.tecpal.device.mc30
SVC=$LIDL/$LIDL.SerialPortMC30Service
BOOT=app.toque/app.toque.recipes.BootReceiver
HOME_ALIAS=app.toque/app.toque.ui.Accueil
JOURNAL="$RACINE/build/banc-essai"
mkdir -p "$JOURNAL"

a() { "$ADB" -s "$SERIE" "$@"; }
sh_() { a shell "$@" | tr -d '\r'; }
cam() { sh_ "sh $MOD/camybrith $*"; }
dire() { echo "  $*"; }

# ------------------------------------------------------------------ émulateur

creer_avd() {
  [ -x "$EMU" ] || { echo "Émulateur absent : $SDK/emulator. Installe-le avec sdkmanager \"emulator\"."; exit 1; }
  local sm
  sm=$(ls "$SDK"/cmdline-tools/*/bin/sdkmanager 2>/dev/null | head -1)
  if [ ! -d "$SDK/system-images/android-27/default/$ABI" ]; then
    [ -n "$sm" ] || { echo "sdkmanager introuvable ($SDK/cmdline-tools)."; exit 1; }
    echo "Téléchargement de l'image Android 8.1 ($ABI)…"
    yes | "$sm" "$IMAGE" >/dev/null
  fi
  if ! "$EMU" -list-avds 2>/dev/null | grep -qx "$AVD"; then
    local am
    am=$(ls "$SDK"/cmdline-tools/*/bin/avdmanager | head -1)
    echo no | "$am" create avd -n "$AVD" -k "$IMAGE" -d pixel >/dev/null
  fi
}

en_marche() { "$ADB" devices | grep -q "^${SERIE}[[:space:]]*device"; }

attendre_demarrage() {
  a wait-for-device
  local i
  for i in $(seq 1 120); do
    [ "$(sh_ getprop sys.boot_completed)" = 1 ] && break
    sleep 2
  done
  a root >/dev/null 2>&1
  sleep 2
  a wait-for-device
  for i in $(seq 1 60); do [ "$(sh_ getprop sys.boot_completed)" = 1 ] && break; sleep 1; done
}

# Magisk lance service.sh à chaque démarrage ; ici, c'est le banc qui le fait (en arrière-plan).
apres_demarrage() {
  attendre_demarrage
  sh_ "[ -f $MOD/service.sh ] && (nohup sh $MOD/service.sh >/dev/null 2>&1 &)" >/dev/null
}

demarrer() {
  if en_marche; then return; fi
  creer_avd
  echo "Démarrage de l'émulateur (une à deux minutes)…"
  nohup "$EMU" -avd "$AVD" -port "$PORT" -no-window -no-audio -no-boot-anim -no-snapshot \
    -gpu swiftshader_indirect >"$JOURNAL/emulateur.log" 2>&1 &
  sleep 5
  apres_demarrage
}

couper() {
  # Arrêt brutal, comme une coupure de courant (rien n'est écrit proprement).
  a emu kill >/dev/null 2>&1
  local i
  for i in $(seq 1 30); do en_marche || break; sleep 1; done
  sleep 2
}

redemarrer() {
  a shell svc power reboot >/dev/null 2>&1 || a reboot
  sleep 8
  apres_demarrage
}

# ------------------------------------------------------------------ installation

installer() {
  echo "Compilation (fausse appli Lidl, moteur de secours, Camybrith)…"
  (cd "$RACINE" && ./gradlew -q :banc:fausse-lidl:assembleDebug :moteur-secours:versModule :pilote:assembleDebug :app:assembleDebug) \
    >"$JOURNAL/gradle.log" 2>&1 || { echo "La compilation a échoué : $JOURNAL/gradle.log"; exit 1; }
  a install -r "$RACINE/banc/fausse-lidl/build/outputs/apk/debug/fausse-lidl-debug.apk" >/dev/null
  a install -r "$RACINE/pilote/build/outputs/apk/debug/pilote-debug.apk" >/dev/null
  a install -r "$RACINE/app/build/outputs/apk/debug/app-debug.apk" >/dev/null
  # Comme sur le robot : pas d'autre écran d'accueil que l'appli Lidl et Camybrith.
  sh_ "pm disable com.android.launcher3" >/dev/null 2>&1
  sh_ "mkdir -p $MOD $CAM && chmod 700 $CAM" >/dev/null
  local f
  for f in module.prop camybrith service.sh camybrith.apk; do a push "$RACINE/modules/secours/$f" "$MOD/$f" >/dev/null; done
  sh_ "chmod 755 $MOD/camybrith $MOD/service.sh; touch $CAM/banc" >/dev/null
}

# État de départ de chaque scénario : robot « installé » puis passé en Machine réelle, sans journal.
etat_initial() {
  sh_ "am force-stop app.toque; cd $CAM && rm -rf registre.json op.json mode.json bilans secours.json banc-arrete corrections-demarrage.json cuisson.json" >/dev/null
  sh_ "pm enable $SVC; pm enable $BOOT; pm enable $HOME_ALIAS" >/dev/null
  # Ce que fait outils/installer.sh (T018) : inscrire le démarrage de Camybrith avant d'installer.
  cam noter demarrage "$BOOT" absent enabled lidl installer.sh >/dev/null
  cam noter demarrage "$HOME_ALIAS" absent enabled lidl installer.sh >/dev/null
  passage_machine_reelle >/dev/null
  sh_ "printf '%s' '{\"occupe\":false,\"programme\":\"\",\"maj\":\"2026-01-01T00:00:00+00:00\"}' > $CAM/cuisson.json"
  sh_ "am start -a android.intent.action.MAIN -c android.intent.category.HOME" >/dev/null
  sleep 2
}

# Ce que fait l'interface au passage en Machine réelle (LidlSync, T017) : noter, agir, confirmer.
passage_machine_reelle() {
  local id
  id=$(cam noter composant "$SVC" enabled disabled lidl interface | tail -1)
  case "$id" in ''|*[!0-9]*) echo "noter a échoué : $id"; return 1 ;; esac
  sh_ "pm disable $SVC" >/dev/null
  cam noter-fin "$id" ok >/dev/null
  echo "$id"
}

# ------------------------------------------------------------------ contrôles

# Vrai si le composant paquet/classe est listé sous « disabledComponents: » (même lecture que core).
desactive() {
  sh_ "dumpsys package ${1%%/*}" | awk -v c="${1#*/}" '
    { t=$0; sub(/^ +/, "", t); ind=length($0)-length(t) }
    n>=0 && (t=="" || ind<=n) { n=-1 }
    n>=0 { if (t==c) f=1; next }
    t=="disabledComponents:" { n=ind }
    BEGIN { n=-1 } END { exit f?0:1 }'
}
accueil() { sh_ "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME" | tail -1; }
devant() { sh_ "dumpsys activity activities" | grep -m1 mResumedActivity | sed 's/.* u0 \([^ ]*\).*/\1/'; }
attendre_lidl_devant() {
  local i
  for i in $(seq 1 ${1:-60}); do case "$(devant)" in $LIDL/*) return 0 ;; esac; sleep 1; done
  return 1
}

ECHECS=0
verdict() { # nom, liste d'erreurs
  if [ -z "$2" ]; then echo "OK      $1"; else echo "ÉCHEC   $1 :$2"; ECHECS=$((ECHECS + 1)); fi
}

controles_mode_lidl() {
  local e=""
  desactive "$SVC" && e="$e service moteur Lidl toujours désactivé;"
  desactive "$BOOT" || e="$e BootReceiver actif;"
  desactive "$HOME_ALIAS" || e="$e alias d'accueil actif;"
  case "$(accueil)" in $LIDL/*) ;; *) e="$e accueil = $(accueil);" ;; esac
  sh_ "[ -f $CAM/op.json ] && echo journal" | grep -q journal && e="$e journal pas effacé;"
  sh_ "cat $CAM/mode.json 2>/dev/null" | grep -q '"lidl"' || e="$e mode.json pas en lidl;"
  attendre_lidl_devant 60 || e="$e appli au premier plan = $(devant);"
  echo "$e"
}

# ------------------------------------------------------------------ scénarios

B1() {
  etat_initial
  sh_ "pm enable $SVC" >/dev/null
  sh_ "cd $CAM && rm -f registre.json" >/dev/null
  local id e=""
  id=$(passage_machine_reelle) || e="$e $id;"
  desactive "$SVC" || e="$e service pas désactivé;"
  sh_ "cat $CAM/registre.json" | grep -q "\"cible\": \"$SVC\"" || e="$e pas inscrit au registre;"
  sh_ "cat $CAM/registre.json" | grep -q '"confirme": true' || e="$e inscription pas confirmée;"
  # Une cible hors format est refusée et rien n'est inscrit.
  cam noter composant "x; reboot" enabled disabled lidl interface >/dev/null
  sh_ "cat $CAM/registre.json" | grep -q reboot && e="$e cible invalide acceptée;"
  verdict "B1 passage en Machine réelle inscrit au registre avant d'agir" "$e"
}

B2() {
  etat_initial
  local t0 t1 e
  t0=$(date +%s)
  cam retour-lidl >"$JOURNAL/B2.txt" 2>&1
  sleep 8
  apres_demarrage
  e=$(controles_mode_lidl)
  t1=$(date +%s)
  [ $((t1 - t0)) -le 180 ] || e="$e $((t1 - t0)) s (plus de 3 min);"
  grep -q "Résultat : réussi" "$JOURNAL/B2.txt" || e="$e bilan pas réussi (voir $JOURNAL/B2.txt);"
  for p in app.toque app.toque.pilote; do [ -n "$(sh_ "pidof $p")" ] && e="$e $p tourne;"; done
  verdict "B2 retour à l'appli Lidl en $((t1 - t0)) s" "$e"
}

B3() {
  # Suite de B2 : le robot est en mode Lidl.
  sh_ "cat $CAM/mode.json 2>/dev/null" | grep -q '"lidl"' || { cam retour-lidl >/dev/null 2>&1; sleep 8; apres_demarrage; }
  local i e=""
  for i in 1 2 3; do
    redemarrer
    attendre_lidl_devant 90 || e="$e démarrage $i : $(devant);"
  done
  sleep 5
  e="$e$(controles_mode_lidl)"
  verdict "B3 trois redémarrages, toujours l'appli Lidl" "$e"
}

B5() {
  local etapes="debut arret annulation verification termine" etape e tout=""
  if [ "${1:-}" = aleatoire ]; then B5_aleatoire; return; fi
  for etape in $etapes; do
    etat_initial
    sh_ "cd $CAM && rm -f banc-arrete" >/dev/null
    # Le moteur s'arrête net à cette étape (fichier « banc » présent : émulateur seulement).
    (a shell "CAMYBRITH_BANC_ARRET=$etape sh $MOD/camybrith retour-lidl" >/dev/null 2>&1 &)
    local i
    for i in $(seq 1 60); do sh_ "cat $CAM/banc-arrete 2>/dev/null" | grep -q "$etape" && break; sleep 1; done
    couper
    demarrer
    sleep 5
    e=$(controles_mode_lidl)
    [ -z "$e" ] || tout="$tout [$etape]$e"
    echo "        coupure à l'étape « $etape » : ${e:-ok}"
  done
  verdict "B5 coupure à chaque étape du retour" "$tout"
}

B5_aleatoire() {
  local n=${N_ALEATOIRE:-20} k ok=0 e
  for k in $(seq 1 "$n"); do
    etat_initial
    local delai
    delai=$(awk -v s="$RANDOM" 'BEGIN{srand(s); printf "%.1f", rand()*4}')
    (a shell "sh $MOD/camybrith retour-lidl --sans-redemarrage" >/dev/null 2>&1 &)
    sleep "$delai"
    couper
    demarrer
    sleep 5
    e=$(controles_mode_lidl)
    # Coupure avant même le début du journal : le robot est encore en Camybrith, c'est normal ;
    # mais il ne doit jamais être dans un état intermédiaire.
    if [ -z "$e" ]; then ok=$((ok + 1)); echo "        essai $k (coupure à ${delai} s) : retour terminé"
    elif ! desactive "$HOME_ALIAS" && desactive "$SVC" && ! sh_ "ls $CAM/mode.json" >/dev/null 2>&1; then
      ok=$((ok + 1)); echo "        essai $k (coupure à ${delai} s) : coupé avant le début, robot resté sur Camybrith"
    else echo "        essai $k (coupure à ${delai} s) : ÉTAT INTERMÉDIAIRE :$e"
    fi
  done
  [ "$ok" = "$n" ] && verdict "B5 aleatoire $ok/$n" "" || verdict "B5 aleatoire $ok/$n" " états intermédiaires"
}

# ------------------------------------------------------------------ programme

case "${1:-}" in
  arreter) en_marche && a emu kill >/dev/null; echo "Émulateur arrêté."; exit 0 ;;
esac
SCEN=${*:-"B1 B2 B3 B5"}
demarrer
installer
echo "Scénarios : $SCEN"
set -- $SCEN
while [ $# -gt 0 ]; do
  case "$1" in
    B1|B2|B3) "$1" ;;
    B5) if [ "${2:-}" = aleatoire ]; then B5 aleatoire; shift; else B5; fi ;;
    *) echo "Scénario inconnu ou pas encore écrit : $1" ;;
  esac
  shift
done
echo
[ "$ECHECS" = 0 ] && echo "Tout est OK." || echo "$ECHECS scénario(s) en ÉCHEC. Journaux : $JOURNAL"
exit "$ECHECS"
