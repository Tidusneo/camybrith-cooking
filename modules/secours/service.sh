#!/system/bin/sh
# At boot (Magisk, without blocking the boot), once Android is ready:
#  1. power switch gesture, resuming an interrupted operation, storage reserve, and check after a
#     return to the Lidl app (camybrith demarrage);
#  2. rescue mode: 3 interface crashes within 120 s → Lidl app (camybrith surveiller);
#  3. robot left on for 2 minutes: the gesture counter goes back to 0.
# Log: /data/adb/camybrith/demarrage.log
MODDIR=${0%/*}
D=/data/adb/camybrith
until [ "$(getprop sys.boot_completed)" = "1" ]; do sleep 2; done
T0=$(date +%s)
mkdir -p "$D"
chmod 700 "$D"
{
  sh "$MODDIR/camybrith" demarrage
  sh "$MODDIR/camybrith" surveiller "$T0"
} > "$D/demarrage.log" 2>&1
while [ $(( $(date +%s) - T0 )) -lt 120 ]; do sleep 5; done
echo 0 > "$D/demarrages-courts"
sync
