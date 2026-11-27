#!/bin/bash
# Fabrique le module Magisk de secours (spec 007) : modules/camybrith_secours.zip.
# Le moteur (core/backup, en Kotlin) est compilé par Gradle puis rangé dans le module.
#
#   bash outils/fabriquer_secours.sh [dossier_de_sortie]
set -eu
RACINE=$(cd "$(dirname "$0")/.." && pwd)
OUT=${1:-$RACINE/modules}
command -v zip >/dev/null || { echo "La commande zip manque (sous Linux : sudo apt install zip)."; exit 1; }
if [ -z "${JAVA_HOME:-}" ] && [ -d /opt/homebrew/opt/openjdk@17 ]; then export JAVA_HOME=/opt/homebrew/opt/openjdk@17; fi
(cd "$RACINE" && ./gradlew -q :moteur-secours:versModule)
M="$RACINE/modules/secours"
[ -s "$M/camybrith.apk" ] || { echo "Le moteur n'a pas été fabriqué ($M/camybrith.apk)."; exit 1; }
mkdir -p "$OUT"
Z="$(cd "$OUT" && pwd)/camybrith_secours.zip"
rm -f "$Z"
(cd "$M" && zip -q -r -X "$Z" module.prop customize.sh camybrith service.sh camybrith.apk META-INF)
echo "Module de secours prêt : $Z"
