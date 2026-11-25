#!/bin/bash
# Télécharge la dernière version publiée de Camybrith Cooking (page « Releases » du projet) :
# les APK vont dans apk/, l'animation de démarrage dans modules/.
#
#   bash outils/telecharger.sh        → version publique
#   bash outils/telecharger.sh beta   → dernière bêta de test (branche beta), dans apk/beta/
#
# La bêta est publiée en préversion : l'appli et la version publique de ce script ne la voient pas.
set -eu
RACINE=$(cd "$(dirname "$0")/.." && pwd)
DEPOT=${DEPOT:-Tidusneo/camybrith-cooking}
CANAL=${1:-publique}
command -v curl >/dev/null || { echo "La commande curl manque (sous Ubuntu : sudo apt install curl)."; exit 1; }
case "$CANAL" in
  publique|stable)
    URL="https://api.github.com/repos/$DEPOT/releases/latest"
    DAPK="$RACINE/apk"; SUITE="bash outils/installer.sh" ;;
  beta|bêta)
    URL="https://api.github.com/repos/$DEPOT/releases/tags/latest-beta"
    DAPK="$RACINE/apk/beta"; SUITE="bash outils/installer.sh beta" ;;
  *) echo "Usage : bash $0 [beta]"; exit 1 ;;
esac
mkdir -p "$DAPK" "$RACINE/modules"
if ! JSON=$(curl -fsSL "$URL" 2>/dev/null); then
  [ "$DAPK" = "$RACINE/apk" ] && echo "Impossible de lire la page des versions de $DEPOT." || echo "Aucune bêta publiée pour l'instant sur $DEPOT (ou page des versions injoignable)."
  exit 1
fi
TITRE=$(printf '%s' "$JSON" | grep -o '"name": *"[^"]*"' | head -1 | sed 's/.*"\([^"]*\)"$/\1/')
if [ "$DAPK" = "$RACINE/apk" ]; then
  TAG=$(printf '%s' "$JSON" | grep -o '"tag_name": *"[^"]*"' | head -1 | sed 's/.*"\([^"]*\)"$/\1/')
  echo "Dernière version publiée : $TAG"
else
  # La bêta est reconstruite à chaque push sous les mêmes noms de fichiers : on repart à zéro.
  rm -f "$DAPK"/*.apk "$DAPK"/*.part
  echo "Bêta : $TITRE"
fi
n=0
for url in $(printf '%s' "$JSON" | grep -o '"browser_download_url": *"[^"]*"' | sed 's/.*"\(https[^"]*\)"$/\1/'); do
  f=$(basename "$url")
  case "$f" in
    *.apk) dest="$DAPK/$f" ;;
    *.zip) [ "$DAPK" = "$RACINE/apk" ] || continue; dest="$RACINE/modules/$f" ;;
    *) continue ;;
  esac
  if [ -s "$dest" ]; then echo "  déjà là : $f"; continue; fi
  curl -fsSL -o "$dest.part" "$url" && mv "$dest.part" "$dest" && echo "  téléchargé : $f" && n=$((n + 1))
done
[ "$DAPK" = "$RACINE/apk" ] || printf '%s\n' "$TITRE" > "$DAPK/VERSION.txt"
echo "$n fichier(s) téléchargé(s). Étape suivante : $SUITE"
