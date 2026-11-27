#!/bin/bash
# Builds the rescue Magisk module (spec 007): modules/camybrith_secours.zip.
# The engine (core/backup, in Kotlin) is compiled by Gradle and then put into the module.
#
#   bash outils/fabriquer_secours.sh [output_folder]
set -eu
ROOT=$(cd "$(dirname "$0")/.." && pwd)
OUT=${1:-$ROOT/modules}
command -v zip >/dev/null || { echo "La commande zip manque (sous Linux : sudo apt install zip)."; exit 1; }
if [ -z "${JAVA_HOME:-}" ] && [ -d /opt/homebrew/opt/openjdk@17 ]; then export JAVA_HOME=/opt/homebrew/opt/openjdk@17; fi
(cd "$ROOT" && ./gradlew -q :rescue-engine:toModule)
M="$ROOT/modules/secours"
[ -s "$M/camybrith.apk" ] || { echo "Le moteur n'a pas été fabriqué ($M/camybrith.apk)."; exit 1; }
mkdir -p "$OUT"
Z="$(cd "$OUT" && pwd)/camybrith_secours.zip"
rm -f "$Z"
(cd "$M" && zip -q -r -X "$Z" module.prop customize.sh camybrith post-fs-data.sh service.sh camybrith.apk META-INF)
echo "Module de secours prêt : $Z"
