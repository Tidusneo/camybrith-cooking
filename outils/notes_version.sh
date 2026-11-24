#!/bin/bash
# Écrit les notes d'une version (page « Releases ») à partir de CHANGELOG.md.
#   bash outils/notes_version.sh 1.3.0 > notes.md
# Sans section pour cette version dans CHANGELOG.md : rien n'est écrit et le script répond 1.
set -eu
V=$1
RACINE=$(cd "$(dirname "$0")/.." && pwd)
SECTION=$(awk -v v="## $V" '$0==v{on=1;next} /^## /{if(on)exit} on' "$RACINE/CHANGELOG.md")
[ -n "$(echo "$SECTION" | tr -d '[:space:]')" ] || exit 1
cat <<NOTES
Mode d'emploi : [docs/GUIDE.md](https://github.com/Tidusneo24150/camybrith-cooking/blob/main/docs/GUIDE.md) · [English](https://github.com/Tidusneo24150/camybrith-cooking/blob/main/docs/GUIDE.en.md)

## Nouveautés · What's new
$SECTION

## Fichiers · Files

* \`Camybrith-pilote-*.apk\` + \`Camybrith-$V-rapide.apk\` : l'appli (pilote moteur + interface) · the app (motor pilot + interface)
* \`Camybrith-$V-prudente.apk\` : interface de secours, plus lente · fallback interface, slower
* \`camybrith_animation.zip\` : animation de démarrage (module Magisk) · boot animation (Magisk module)
* \`camybrith-assistant-claude.zip\` : assistant Claude qui guide l'installation · Claude assistant guiding the install
NOTES
