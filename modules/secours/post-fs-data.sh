#!/system/bin/sh
# Power switch gesture (spec 007, research.md D13): counts boots. Three short boots in a row
# (switched off before 2 minutes) bring the robot back to the Lidl app (see service.sh).
# The robot's boot waits for this script: it ONLY reads, adds 1, writes and syncs.
# No waiting, no call to pm or am, every error ignored. Storage full: the 2 MB reserve is deleted
# and the write retried once (research.md D15), so the gesture still works.
D=/data/adb/camybrith
F=$D/demarrages-courts
n=$(cat "$F" 2>/dev/null)
case "$n" in
  ''|*[!0-9]*) n=0 ;;
esac
[ "${#n}" -gt 4 ] && n=0
mkdir -p "$D" 2>/dev/null
if ! echo $((n + 1)) > "$F" 2>/dev/null; then
  rm -f "$D/reserve" 2>/dev/null
  echo $((n + 1)) > "$F" 2>/dev/null
fi
sync
exit 0
