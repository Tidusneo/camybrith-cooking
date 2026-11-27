#!/bin/bash
# Emulator test bench (spec 007, quickstart §B): tests going back to the Lidl app WITHOUT the
# robot. An Android 8.1 emulator gets a FAKE Lidl app (bench/fake-lidl), Camybrith and the rescue
# module; each scenario prints OK or ÉCHEC (results are in French, like the other scripts).
#
#   bash outils/banc-essai.sh               # the default scenarios
#   bash outils/banc-essai.sh B2 B3         # only these
#   bash outils/banc-essai.sh B5 aleatoire  # power cuts at random times (20 runs)
#   bash outils/banc-essai.sh B11           # long mode: 100 restarts spaced by more than 2 min
#   bash outils/banc-essai.sh arreter       # switches the emulator off
#
# Requires JDK 17 and the Android SDK (README, "Fabriquer les APK soi-même"). Nothing is sent to
# the robot: every command targets the emulator emulator-5580.
#
# Limits (research.md D11): no real Magisk (the module is copied by hand, and post-fs-data.sh and
# service.sh are run by this script at every boot, with a stand-in for Magisk's "su -c"), the
# interface has no root (B1 goes through the module's command), no real motor service or serial port.
set -u
ROOT=$(cd "$(dirname "$0")/.." && pwd)
SDK=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}
[ -d "$SDK" ] || SDK=$HOME/Android/Sdk
ADB="$SDK/platform-tools/adb"
EMU="$SDK/emulator/emulator"
AVD=camybrith-banc
PORT=5580
SERIAL=emulator-$PORT
case "$(uname -m)" in arm64|aarch64) ABI=arm64-v8a ;; *) ABI=x86_64 ;; esac
IMAGE="system-images;android-27;default;$ABI"
if [ -z "${JAVA_HOME:-}" ] && [ -d /opt/homebrew/opt/openjdk@17 ]; then export JAVA_HOME=/opt/homebrew/opt/openjdk@17; fi
MOD=/data/adb/modules/camybrith_secours
CAM=/data/adb/camybrith
LIDL=com.tecpal.device.mc30
SVC=$LIDL/$LIDL.SerialPortMC30Service
BOOT=app.toque/app.toque.recipes.BootReceiver
HOME_ALIAS=app.toque/app.toque.ui.Accueil
LOGS="$ROOT/build/banc-essai"
mkdir -p "$LOGS"

a() { "$ADB" -s "$SERIAL" "$@"; }
sh_() { a shell "$@" | tr -d '\r'; }
cam() { sh_ "sh $MOD/camybrith $*"; }

# ------------------------------------------------------------------ emulator

create_avd() {
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

running() { "$ADB" devices | grep -q "^${SERIAL}[[:space:]]*device"; }

# Stand-in for Magisk's "su -c" (the emulator's su does not understand it), in /sbin (in memory,
# redone at every boot): the outils/ scripts use it.
su_stand_in() {
  sh_ 'mount -o remount,rw / 2>/dev/null; printf "#!/system/bin/sh\n[ \"\$1\" = -c ] && { shift; exec /system/bin/sh -c \"\$*\"; }\nexec /system/xbin/su \"\$@\"\n" > /sbin/su; chmod 755 /sbin/su; mount -o remount,ro / 2>/dev/null' >/dev/null
}

# Magisk runs post-fs-data.sh early at every boot, then service.sh; here the bench does it:
# post-fs-data.sh as soon as adb answers (before the end of the boot), service.sh afterwards.
wait_for_boot() {
  a wait-for-device
  a root >/dev/null 2>&1
  sleep 1
  a wait-for-device
  sh_ "[ -f $MOD/post-fs-data.sh ] && sh $MOD/post-fs-data.sh" >/dev/null
  local i
  for i in $(seq 1 150); do
    [ "$(sh_ getprop sys.boot_completed)" = 1 ] && break
    sleep 2
  done
  su_stand_in
}

after_boot() {
  wait_for_boot
  sh_ "rm -f $CAM/demarrage.log; [ -f $MOD/service.sh ] && (nohup sh $MOD/service.sh >/dev/null 2>&1 &)" >/dev/null
  # As on the robot, let the module finish its boot work before looking.
  local i
  for i in $(seq 1 60); do
    sh_ "cat $CAM/demarrage.log 2>/dev/null" | grep -q "Démarrage traité" && break
    sleep 1
  done
}

start_emulator() {
  if running; then
    # Already on (previous bench run): restore root and the su stand-in.
    a root >/dev/null 2>&1; sleep 1; a wait-for-device; su_stand_in
    return
  fi
  create_avd
  echo "Démarrage de l'émulateur (une à deux minutes)…"
  nohup "$EMU" -avd "$AVD" -port "$PORT" -no-window -no-audio -no-boot-anim -no-snapshot \
    -gpu swiftshader_indirect >"$LOGS/emulateur.log" 2>&1 &
  sleep 5
  after_boot
}

power_cut() {
  # Hard stop, like a power cut (nothing is written cleanly).
  a emu kill >/dev/null 2>&1
  local i
  for i in $(seq 1 30); do running || break; sleep 1; done
  sleep 2
}

restart() {
  a shell svc power reboot >/dev/null 2>&1 || a reboot
  sleep 8
  after_boot
}

# ------------------------------------------------------------------ installation

install_all() {
  echo "Compilation (fausse appli Lidl, moteur de secours, Camybrith)…"
  (cd "$ROOT" && ./gradlew -q :bench:fake-lidl:assembleDebug :rescue-engine:toModule :pilote:assembleDebug :app:assembleDebug) \
    >"$LOGS/gradle.log" 2>&1 || { echo "La compilation a échoué : $LOGS/gradle.log"; exit 1; }
  a install -r "$ROOT/bench/fake-lidl/build/outputs/apk/debug/fake-lidl-debug.apk" >/dev/null
  a install -r "$ROOT/pilote/build/outputs/apk/debug/pilote-debug.apk" >/dev/null
  a install -r "$ROOT/app/build/outputs/apk/debug/app-debug.apk" >/dev/null
  # As on the robot: no home screen other than the Lidl app and Camybrith.
  sh_ "pm disable com.android.launcher3" >/dev/null 2>&1
  sh_ "mkdir -p $MOD $CAM && chmod 700 $CAM" >/dev/null
  local f
  for f in module.prop camybrith post-fs-data.sh service.sh camybrith.apk; do a push "$ROOT/modules/secours/$f" "$MOD/$f" >/dev/null; done
  sh_ "chmod 755 $MOD/camybrith $MOD/service.sh $MOD/post-fs-data.sh; touch $CAM/bench" >/dev/null
  cp "$ROOT/app/build/outputs/apk/debug/app-debug.apk" "$LOGS/app-normal.apk"
}

# Starting state of every scenario: robot "installed" then switched to Machine réelle, no journal.
initial_state() {
  sh_ "pm path app.toque" | grep -q package: || a install "$LOGS/app-normal.apk" >/dev/null
  sh_ "am force-stop app.toque; cd $CAM && rm -rf registre.json op.json mode.json bilans secours.json bench-stopped corrections-demarrage.json cuisson.json demarrages-courts" >/dev/null
  sh_ "pm enable $SVC; pm enable $BOOT; pm enable $HOME_ALIAS" >/dev/null
  # As on the robot: Camybrith is the chosen home screen.
  sh_ "cmd package set-home-activity $HOME_ALIAS" >/dev/null
  # What outils/installer.sh does (T018): record Camybrith's startup before installing.
  cam noter demarrage "$BOOT" absent enabled lidl installer.sh >/dev/null
  cam noter demarrage "$HOME_ALIAS" absent enabled lidl installer.sh >/dev/null
  switch_to_real_machine >/dev/null
  sh_ "printf '%s' '{\"occupe\":false,\"programme\":\"\",\"maj\":\"2026-01-01T00:00:00+00:00\"}' > $CAM/cuisson.json"
  sh_ "am start -a android.intent.action.MAIN -c android.intent.category.HOME" >/dev/null
  # Android writes component states to disk about ten seconds after a change, and Linux keeps
  # writes in memory for up to 30 s: wait, then force them to disk, so that a power cut does not
  # bring back the previous scenario's state.
  sleep 12
  sh_ sync >/dev/null
}

# What the interface does when switching to Machine réelle (LidlSync, T017): record, act, confirm.
switch_to_real_machine() {
  local id
  id=$(cam noter composant "$SVC" enabled disabled lidl interface | tail -1)
  case "$id" in ''|*[!0-9]*) echo "noter a échoué : $id"; return 1 ;; esac
  sh_ "pm disable $SVC" >/dev/null
  cam noter-fin "$id" ok >/dev/null
  echo "$id"
}

# ------------------------------------------------------------------ checks

# True if package/class is listed under "disabledComponents:" (same parsing as core).
is_disabled() {
  sh_ "dumpsys package ${1%%/*}" | awk -v c="${1#*/}" '
    { t=$0; sub(/^ +/, "", t); ind=length($0)-length(t) }
    n>=0 && (t=="" || ind<=n) { n=-1 }
    n>=0 { if (t==c) f=1; next }
    t=="disabledComponents:" { n=ind }
    BEGIN { n=-1 } END { exit f?0:1 }'
}
home_screen() { sh_ "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME" | tail -1; }
foreground() { sh_ "dumpsys activity activities" | grep -m1 mResumedActivity | sed 's/.* u0 \([^ ]*\).*/\1/'; }
wait_lidl_in_front() {
  local i
  for i in $(seq 1 "${1:-60}"); do case "$(foreground)" in $LIDL/*) return 0 ;; esac; sleep 1; done
  return 1
}
in_lidl_mode() { sh_ "cat $CAM/mode.json 2>/dev/null" | grep -q '"lidl"'; }

FAILURES=0
verdict() { # name, list of errors (French, shown)
  if [ -z "$2" ]; then echo "OK      $1"; else echo "ÉCHEC   $1 :$2"; FAILURES=$((FAILURES + 1)); fi
}

lidl_mode_checks() {
  local e=""
  is_disabled "$SVC" && e="$e service moteur Lidl toujours désactivé;"
  is_disabled "$BOOT" || e="$e BootReceiver actif;"
  is_disabled "$HOME_ALIAS" || e="$e alias d'accueil actif;"
  case "$(home_screen)" in $LIDL/*) ;; *) e="$e accueil = $(home_screen);" ;; esac
  sh_ "[ -f $CAM/op.json ] && echo journal" | grep -q journal && e="$e journal pas effacé;"
  in_lidl_mode || e="$e mode.json pas en lidl;"
  wait_lidl_in_front 60 || e="$e appli au premier plan = $(foreground);"
  echo "$e"
}

# Checks when the interface is no longer installed (B7).
lidl_checks_without_interface() {
  local e=""
  is_disabled "$SVC" && e="$e service moteur Lidl toujours désactivé;"
  case "$(home_screen)" in $LIDL/*) ;; *) e="$e accueil = $(home_screen);" ;; esac
  in_lidl_mode || e="$e mode.json pas en lidl;"
  wait_lidl_in_front 60 || e="$e appli au premier plan = $(foreground);"
  echo "$e"
}

# ------------------------------------------------------------------ scenarios

B1() {
  initial_state
  sh_ "pm enable $SVC" >/dev/null
  sh_ "cd $CAM && rm -f registre.json" >/dev/null
  local id e=""
  id=$(switch_to_real_machine) || e="$e $id;"
  is_disabled "$SVC" || e="$e service pas désactivé;"
  sh_ "cat $CAM/registre.json" | grep -q "\"cible\": \"$SVC\"" || e="$e pas inscrit au registre;"
  sh_ "cat $CAM/registre.json" | grep -q '"confirme": true' || e="$e inscription pas confirmée;"
  # An off-format target is refused and nothing is recorded.
  cam noter composant "x; reboot" enabled disabled lidl interface >/dev/null
  sh_ "cat $CAM/registre.json" | grep -q reboot && e="$e cible invalide acceptée;"
  verdict "B1 passage en Machine réelle inscrit au registre avant d'agir" "$e"
}

B2() {
  initial_state
  local t0 t1 e
  t0=$(date +%s)
  cam retour-lidl >"$LOGS/B2.txt" 2>&1
  sleep 8
  after_boot
  e=$(lidl_mode_checks)
  t1=$(date +%s)
  [ $((t1 - t0)) -le 180 ] || e="$e $((t1 - t0)) s (plus de 3 min);"
  grep -q "Résultat : réussi" "$LOGS/B2.txt" || e="$e bilan pas réussi (voir $LOGS/B2.txt);"
  local p
  for p in app.toque app.toque.pilote; do [ -n "$(sh_ "pidof $p")" ] && e="$e $p tourne;"; done
  verdict "B2 retour à l'appli Lidl en $((t1 - t0)) s" "$e"
}

B3() {
  # Follows B2: the robot is in Lidl mode.
  in_lidl_mode || { cam retour-lidl >/dev/null 2>&1; sleep 8; after_boot; }
  local i e=""
  for i in 1 2 3; do
    restart
    wait_lidl_in_front 90 || e="$e démarrage $i : $(foreground);"
  done
  sleep 5
  e="$e$(lidl_mode_checks)"
  verdict "B3 trois redémarrages, toujours l'appli Lidl" "$e"
}

B5() {
  local step e all=""
  for step in debut arret annulation verification termine; do
    initial_state
    sh_ "cd $CAM && rm -f bench-stopped" >/dev/null
    # The engine stops dead at this step ("bench" marker file present: emulator only).
    (a shell "CAMYBRITH_BENCH_STOP=$step sh $MOD/camybrith retour-lidl" >/dev/null 2>&1 &)
    local i
    for i in $(seq 1 60); do sh_ "cat $CAM/bench-stopped 2>/dev/null" | grep -q "$step" && break; sleep 1; done
    power_cut
    start_emulator
    sleep 5
    e=$(lidl_mode_checks)
    [ -z "$e" ] || all="$all [$step]$e"
    echo "        coupure à l'étape « $step » : ${e:-ok}"
  done
  verdict "B5 coupure à chaque étape du retour" "$all"
}

B5_random() {
  local n=${N_RANDOM:-20} k ok=0 e
  for k in $(seq 1 "$n"); do
    initial_state
    sh_ "rm -f $CAM/demarrages-courts" >/dev/null
    local delay
    delay=$(awk -v s="$RANDOM" 'BEGIN{srand(s); printf "%.1f", rand()*4}')
    (a shell "sh $MOD/camybrith retour-lidl --sans-redemarrage" >/dev/null 2>&1 &)
    sleep "$delay"
    power_cut
    start_emulator
    sleep 5
    e=$(lidl_mode_checks)
    # Cut before the journal even started: the robot is still on Camybrith, which is fine; but it
    # must never be in an in-between state.
    if [ -z "$e" ]; then ok=$((ok + 1)); echo "        essai $k (coupure à ${delay} s) : retour terminé"
    elif ! is_disabled "$HOME_ALIAS" && is_disabled "$SVC" && ! sh_ "ls $CAM/mode.json" >/dev/null 2>&1; then
      ok=$((ok + 1)); echo "        essai $k (coupure à ${delay} s) : coupé avant le début, robot resté sur Camybrith"
    else echo "        essai $k (coupure à ${delay} s) : ÉTAT INTERMÉDIAIRE :$e"
    fi
  done
  if [ "$ok" = "$n" ]; then verdict "B5 aleatoire $ok/$n" ""; else verdict "B5 aleatoire $ok/$n" " états intermédiaires"; fi
}

B6() {
  initial_state
  echo "        fabrication d'une version qui plante au démarrage…"
  (cd "$ROOT" && ./gradlew -q -Pbench.crash=true :app:assembleDebug) >"$LOGS/gradle-crash.log" 2>&1 \
    || { verdict "B6 mode de secours" " compilation de la version qui plante impossible"; return; }
  cp "$ROOT/app/build/outputs/apk/debug/app-debug.apk" "$LOGS/app-crash.apk"
  (cd "$ROOT" && ./gradlew -q :app:assembleDebug) >>"$LOGS/gradle-crash.log" 2>&1
  a install -r "$LOGS/app-crash.apk" >/dev/null
  # Reinstalling makes Android forget the chosen home app: as on the robot, Camybrith is it.
  sh_ "cmd package set-home-activity $HOME_ALIAS" >/dev/null
  sleep 12
  restart
  local i e=""
  for i in $(seq 1 75); do in_lidl_mode && break; sleep 2; done
  e=$(lidl_mode_checks)
  sh_ "cat $CAM/secours.json 2>/dev/null" | grep -q '"plantages"' || e="$e secours.json absent;"
  sh_ "cat $CAM/mode.json 2>/dev/null" | grep -q '"secours"' || e="$e origine pas « secours »;"
  sh_ "cat $CAM/demarrage.log" > "$LOGS/B6.txt"
  a install -r "$LOGS/app-normal.apk" >/dev/null
  verdict "B6 trois plantages au démarrage, retour automatique" "$e"
}

B7() {
  initial_state
  sh_ "pm uninstall app.toque" >/dev/null
  printf 'o\no\n' | ROBOT_SERIAL=$SERIAL bash "$ROOT/outils/origine.sh" >"$LOGS/B7.txt" 2>&1
  sleep 8
  after_boot
  local e
  e=$(lidl_checks_without_interface)
  grep -q "Résultat : réussi" "$LOGS/B7.txt" || e="$e bilan pas réussi (voir $LOGS/B7.txt);"
  a install "$LOGS/app-normal.apk" >/dev/null
  verdict "B7 retour depuis l'ordinateur, interface désinstallée" "$e"
}

# Back to Camybrith (US4): return, restart on the Lidl app, then resume; data untouched.
B4() {
  initial_state
  local before after e=""
  before=$(sh_ "cat /data/data/app.toque/databases/* 2>/dev/null | md5sum")
  cam retour-lidl >/dev/null 2>&1
  sleep 8
  after_boot
  in_lidl_mode || e="$e retour pas fait;"
  cam reprendre >"$LOGS/B4.txt" 2>&1 || e="$e reprise refusée (voir $LOGS/B4.txt);"
  sleep 5
  in_lidl_mode && e="$e mode.json toujours en lidl;"
  is_disabled "$SVC" || e="$e service moteur Lidl pas recoupé;"
  is_disabled "$BOOT" && e="$e BootReceiver désactivé;"
  is_disabled "$HOME_ALIAS" && e="$e alias d'accueil désactivé;"
  case "$(foreground)" in app.toque/*) ;; *) e="$e appli au premier plan = $(foreground);" ;; esac
  after=$(sh_ "cat /data/data/app.toque/databases/* 2>/dev/null | md5sum")
  [ "$before" = "$after" ] || e="$e données de l'interface modifiées;"
  # And it survives a restart: Camybrith again.
  restart
  sleep 10
  case "$(foreground)" in app.toque/*) ;; *) e="$e après redémarrage : $(foreground);" ;; esac
  verdict "B4 retour puis reprise de Camybrith, données intactes" "$e"
}

bubble_running() { sh_ "dumpsys activity services app.toque/.ui.ReturnBubble" | grep -q "ServiceRecord"; }

# The "Camybrith" bubble in Lidl mode (US4): shown at boot, back as soon as Camybrith leaves.
B12() {
  initial_state
  cam retour-lidl >/dev/null 2>&1
  sleep 8
  after_boot
  local i e=""
  for i in $(seq 1 20); do bubble_running && break; sleep 1; done
  bubble_running || e="$e pas de bulle au démarrage;"
  sh_ "dumpsys window windows" | grep -q "app.toque" || e="$e fenêtre de la bulle absente;"
  # Open Camybrith (what a tap on the bubble does), then leave it with HOME.
  sh_ "am start -n app.toque/.ui.MainActivity" >/dev/null
  sleep 6
  sh_ "input keyevent KEYCODE_HOME" >/dev/null
  sleep 4
  bubble_running || e="$e bulle pas revenue en quittant Camybrith;"
  wait_lidl_in_front 20 || e="$e appli au premier plan = $(foreground);"
  verdict "B12 bulle « Camybrith » en mode Lidl" "$e"
}

# Backup (US6, T058): created in less than 30 s, copied to the computer, without any secret.
B8() {
  initial_state
  local secret="banc-secret-$RANDOM" t0 t1 e="" name
  # A secret in the settings, as an MQTT password would be (written while the app is stopped).
  sh_ "am force-stop app.toque" >/dev/null
  sh_ "f=/data/data/app.toque/shared_prefs/toque.xml; [ -f \$f ] || { mkdir -p \${f%/*}; printf '<?xml version=\"1.0\" encoding=\"utf-8\" standalone=\"yes\" ?>\n<map>\n</map>\n' > \$f; }; sed -i 's#</map>#    <string name=\"mqttPassword\">$secret</string>\n    <string name=\"theme\">sombre</string>\n</map>#' \$f; chown \$(stat -c %u:%g \${f%/*}) \$f" >/dev/null
  t0=$(date +%s)
  ROBOT_SERIAL=$SERIAL bash "$ROOT/outils/sauvegarde.sh" creer </dev/null >"$LOGS/B8.txt" 2>&1
  t1=$(date +%s)
  grep -q "Sauvegarde créée" "$LOGS/B8.txt" || e="$e pas de sauvegarde (voir $LOGS/B8.txt);"
  [ $((t1 - t0)) -le 30 ] || e="$e $((t1 - t0)) s (plus de 30 s);"
  rm -rf "$ROOT/sauvegardes"
  ROBOT_SERIAL=$SERIAL bash "$ROOT/outils/sauvegarde.sh" recuperer </dev/null >>"$LOGS/B8.txt" 2>&1
  name=$(ls "$ROOT"/sauvegardes/camybrith-*.camybrith 2>/dev/null | head -1)
  if [ -z "$name" ]; then e="$e pas récupérée sur l'ordinateur;"
  else
    unzip -l "$name" | grep -q manifest.json || e="$e manifeste absent;"
    unzip -p "$name" | grep -q "$secret" && e="$e LE SECRET EST DANS LA SAUVEGARDE;"
    unzip -p "$name" settings.json | grep -q sombre || e="$e réglages absents;"
  fi
  verdict "B8 sauvegarde en $((t1 - t0)) s, récupérée, sans secret" "$e"
}

# Full removal (US5): no Camybrith app or module left, the fake Lidl app works, root stays.
B9() {
  initial_state
  # What outils/installer.sh records (T018) for the apps.
  cam noter appli app.toque absent installee retrait installer.sh >/dev/null
  cam noter appli app.toque.pilote absent installee retrait installer.sh >/dev/null
  printf 'n\nn\no\n' | ROBOT_SERIAL=$SERIAL bash "$ROOT/outils/retirer.sh" >"$LOGS/B9.txt" 2>&1
  local e=""
  for p in app.toque app.toque.pilote; do sh_ "pm path $p" | grep -q package: && e="$e $p toujours installé;"; done
  sh_ "[ -f $MOD/remove ] && echo ok" | grep -q ok || e="$e module de secours pas marqué à retirer;"
  sh_ "[ -d $CAM ] && echo reste" | grep -q reste && e="$e $CAM toujours là;"
  is_disabled "$SVC" && e="$e service moteur Lidl toujours désactivé;"
  case "$(home_screen)" in $LIDL/*) ;; *) e="$e accueil = $(home_screen);" ;; esac
  [ "$(sh_ 'su -c "id -u"')" = 0 ] || e="$e root perdu;"
  ls "$ROOT"/sauvegardes/registre-* >/dev/null 2>&1 || e="$e registre pas copié sur l'ordinateur;"
  # Put the bench back (Magisk would have removed the module at the next boot).
  sh_ "rm -f $MOD/remove; mkdir -p $CAM && chmod 700 $CAM && touch $CAM/bench" >/dev/null
  a install "$LOGS/app-normal.apk" >/dev/null
  a install "$ROOT/pilote/build/outputs/apk/debug/pilote-debug.apk" >/dev/null
  verdict "B9 retrait complet de Camybrith" "$e"
}

# A short boot: switch on, wait for the end of the boot, cut before 2 minutes.
short_boot() { power_cut; start_emulator; sleep 10; }

B10() {
  local n=${N_GESTURE:-10} k ok=0 e
  for k in $(seq 1 "$n"); do
    initial_state
    sh_ "echo 0 > $CAM/demarrages-courts"
    short_boot; short_boot
    # After two short boots, still Camybrith.
    e=""
    in_lidl_mode && e=" retour déjà fait au 2e démarrage;"
    short_boot
    sleep 20
    e="$e$(lidl_mode_checks)"
    sh_ "cat $CAM/mode.json 2>/dev/null" | grep -q '"geste"' || e="$e origine pas « geste »;"
    if [ -z "$e" ]; then ok=$((ok + 1)); echo "        essai $k : appli Lidl au 3e démarrage"
    else echo "        essai $k :$e"; fi
  done
  if [ "$ok" = "$n" ]; then verdict "B10 geste de l'interrupteur $ok/$n" ""; else verdict "B10 geste de l'interrupteur $ok/$n" " échecs"; fi
}

B11() {
  local n=${N_LONG:-100} k e=""
  initial_state
  for k in $(seq 1 "$n"); do
    restart
    sleep 130   # on for more than 2 minutes: the counter goes back to 0
    in_lidl_mode && { e=" déclenché au redémarrage $k;"; break; }
    [ $((k % 10)) = 0 ] && echo "        $k redémarrages espacés, aucun déclenchement"
  done
  verdict "B11 redémarrages espacés ($n), jamais de déclenchement" "$e"
}

B13() {
  local v e all=""
  for v in "abc" "99999999999999999999" "" "-5"; do
    initial_state
    sh_ "printf '%s' '$v' > $CAM/demarrages-courts"
    restart
    sleep 20
    e=""
    in_lidl_mode && e="$e retour déclenché;"
    [ "$(foreground | cut -d/ -f1)" = app.toque ] || e="$e Camybrith pas au premier plan ($(foreground));"
    case "$(sh_ "cat $CAM/demarrages-courts")" in 1|0) ;; *) e="$e compteur = $(sh_ "cat $CAM/demarrages-courts");" ;; esac
    [ -z "$e" ] || all="$all [« $v »]$e"
  done
  verdict "B13 compteur du geste illisible : démarrage normal" "$all"
}

# ------------------------------------------------------------------ main

case "${1:-}" in
  arreter) running && a emu kill >/dev/null; echo "Émulateur arrêté."; exit 0 ;;
esac
SCENARIOS=${*:-"B1 B2 B3 B4 B5 B6 B7 B8 B10 B12 B13 B9"}
start_emulator
install_all
echo "Scénarios : $SCENARIOS"
# shellcheck disable=SC2086
set -- $SCENARIOS
while [ $# -gt 0 ]; do
  case "$1" in
    B1|B2|B3|B4|B6|B7|B8|B9|B10|B11|B12|B13) "$1" ;;
    B5) if [ "${2:-}" = aleatoire ]; then B5_random; shift; else B5; fi ;;
    *) echo "Scénario inconnu ou pas encore écrit : $1" ;;
  esac
  shift
done
echo
if [ "$FAILURES" = 0 ]; then echo "Tout est OK."; else echo "$FAILURES scénario(s) en ÉCHEC. Journaux : $LOGS"; fi
exit "$FAILURES"
