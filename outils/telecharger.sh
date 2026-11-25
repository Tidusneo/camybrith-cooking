#!/bin/bash
# Télécharge la dernière version publiée de Camybrith Cooking (page « Releases » du projet) :
# les APK vont dans apk/, l'animation de démarrage dans modules/.
#
#   bash outils/telecharger.sh
set -eu
RACINE=$(cd "$(dirname "$0")/.." && pwd)
DEPOT=${DEPOT:-Tidusneo/camybrith-cooking}
command -v curl >/dev/null || { echo "La commande curl manque (sous Ubuntu : sudo apt install curl)."; exit 1; }
mkdir -p "$RACINE/apk" "$RACINE/modules"
JSON=$(curl -fsSL "https://api.github.com/repos/$DEPOT/releases/latest") || { echo "Impossible de lire la page des versions de $DEPOT."; exit 1; }
TAG=$(printf '%s' "$JSON" | grep -o '"tag_name": *"[^"]*"' | head -1 | sed 's/.*"\([^"]*\)"$/\1/')
echo "Dernière version publiée : $TAG"
n=0
for url in $(printf '%s' "$JSON" | grep -o '"browser_download_url": *"[^"]*"' | sed 's/.*"\(https[^"]*\)"$/\1/'); do
  f=$(basename "$url")
  case "$f" in
    *.apk) dest="$RACINE/apk/$f" ;;
    *.zip) dest="$RACINE/modules/$f" ;;
    *) continue ;;
  esac
  if [ -s "$dest" ]; then echo "  déjà là : $f"; continue; fi
  curl -fsSL -o "$dest.part" "$url" && mv "$dest.part" "$dest" && echo "  téléchargé : $f" && n=$((n + 1))
done
echo "$n fichier(s) téléchargé(s). Étape suivante : bash outils/installer.sh"
