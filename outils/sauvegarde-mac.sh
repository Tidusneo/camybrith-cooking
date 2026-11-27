#!/bin/bash
# Sauvegarde complète de la mémoire du Smart depuis un Mac, avec mtkclient, en LECTURE SEULE.
# Rien n'est jamais écrit dans le robot : le script ne lance que des commandes de lecture
# (printgpt, rl, rf), écrites en dur ci-dessous.
#
#   bash outils/sauvegarde-mac.sh preparer        # une fois : installe mtkclient (sans le robot)
#   bash outils/sauvegarde-mac.sh lire            # robot éteint, branché sur « Android USB »
#   bash outils/sauvegarde-mac.sh lire --sans-donnees   # sans la partition userdata (plus court)
#
# La sauvegarde va dans ~/Camybrith-sauvegarde/<date>/ , HORS du projet : elle contient le numéro
# de série et les réglages du robot (nvram, proinfo). Ne jamais la publier ni la committer.
set -eu

MTK_TAG=v2.1.4.1                          # version de mtkclient vérifiée pour ce script
MTK_SRC=${MTK_SRC:-$HOME/Projets/mtkclient}
MTK_VENV=${MTK_VENV:-$HOME/Projets/mtkclient-venv}
MTK_STUB=${MTK_STUB:-$HOME/Projets/mtkclient-stub}
DEST_RACINE=${DEST_RACINE:-$HOME/Camybrith-sauvegarde}
# Partitions sans lesquelles on ne pourra pas revenir en arrière : la sauvegarde doit les contenir.
ESSENTIELLES="boot recovery cache lk seccfg nvram proinfo system vendor"

erreur() { echo "ERREUR : $*" >&2; exit 1; }

preparer() {
  command -v brew >/dev/null || erreur "Homebrew manque (https://brew.sh)."
  brew list libusb >/dev/null 2>&1 || brew install libusb
  PY=$(command -v python3.12 || true)
  [ -n "$PY" ] || { brew install python@3.12; PY=$(brew --prefix python@3.12)/bin/python3.12; }
  if [ ! -d "$MTK_SRC/.git" ]; then
    git clone -q https://github.com/bkerler/mtkclient.git "$MTK_SRC"
  fi
  git -C "$MTK_SRC" fetch -q --tags
  git -C "$MTK_SRC" checkout -q "$MTK_TAG"
  [ -x "$MTK_VENV/bin/python" ] || "$PY" -m venv "$MTK_VENV"
  "$MTK_VENV/bin/pip" install -q --disable-pip-version-check "$MTK_SRC"
  # mtkclient importe toujours « fuse », qui ne sert qu'à monter la mémoire comme un disque.
  # Plutôt qu'installer macFUSE (extension système), un faux module suffit pour lire.
  mkdir -p "$MTK_STUB"
  cat > "$MTK_STUB/fuse.py" <<'EOF'
# Faux module « fuse » (Camybrith) : la commande « fs » de mtkclient n'est pas utilisée.
class Operations: pass
class LoggingMixIn: pass
class FuseOSError(OSError): pass
def FUSE(*a, **k):
    raise RuntimeError("Montage FUSE non disponible (macFUSE non installé)")
EOF
  mtk --help >/dev/null || erreur "mtkclient ne se lance pas."
  echo "mtkclient $MTK_TAG prêt. Le jour du branchement : bash outils/sauvegarde-mac.sh lire"
}

mtk() { (cd "$MTK_SRC" && PYTHONPATH="$MTK_STUB" "$MTK_VENV/bin/mtk" "$@"); }

lire() {
  local sans_donnees=0
  [ "${1:-}" = "--sans-donnees" ] && sans_donnees=1
  [ -x "$MTK_VENV/bin/mtk" ] || erreur "mtkclient n'est pas installé : bash outils/sauvegarde-mac.sh preparer"
  [ "$(git -C "$MTK_SRC" describe --tags 2>/dev/null)" = "$MTK_TAG" ] \
    || erreur "mtkclient n'est pas en $MTK_TAG : relance « preparer »."

  local dest
  dest="$DEST_RACINE/$(date +%Y-%m-%d_%H%M)"
  case "$dest" in *" "*) erreur "Le dossier de sauvegarde ne doit pas contenir d'espace : $dest";; esac
  local libre_go=$(( $(df -k "$HOME" | awk 'NR==2{print $4}') / 1024 / 1024 ))
  [ "$libre_go" -ge 12 ] || erreur "Il faut au moins 12 Go libres sur le Mac (il en reste $libre_go)."
  mkdir -p "$dest/partitions"

  cat <<TXT

Sauvegarde en LECTURE SEULE vers : $dest

  1. Le robot est ÉTEINT à l'interrupteur.
  2. Le câble est branché sur la prise « Android USB » (JAMAIS « Power PCB USB »),
     l'autre bout directement sur le Mac (pas de hub ni d'adaptateur).
  3. Appuie sur Entrée ici, PUIS allume le robot à l'interrupteur.

TXT
  read -r -p "Prêt ? Entrée pour lancer, Ctrl+C pour annuler. " _

  local skip=""
  [ "$sans_donnees" = 1 ] && skip=" --skip userdata"
  # Uniquement des lectures. Une seule connexion pour tout (le robot n'accepte qu'une session).
  local cmds="printgpt;rl $dest/partitions$skip;rf $dest/boot1.bin --parttype boot1;rf $dest/boot2.bin --parttype boot2"
  mtk multi "$cmds" 2>&1 | tee "$dest/journal.txt"

  echo
  echo "Vérification de la sauvegarde…"
  grep -iE "sbc|sla|daa|hw ?code|secure|target config" "$dest/journal.txt" | sort -u > "$dest/securite.txt" || true
  local manque=""
  for p in $ESSENTIELLES; do
    f=$(ls "$dest/partitions/$p".* "$dest/partitions/$p" 2>/dev/null | head -1 || true)
    if [ -z "$f" ] || [ ! -s "$f" ]; then manque="$manque $p"; fi
  done
  find "$dest" -type f -size 0 -print | sed 's/^/  vide : /'
  (cd "$dest" && find partitions boot1.bin boot2.bin -type f 2>/dev/null | sort | xargs shasum -a 256 > SHA256SUMS) || true
  du -sh "$dest" | awk '{print "Taille totale : "$1}'
  echo "Sécurité du démarrage (extrait du journal) :"
  sed 's/^/  /' "$dest/securite.txt"
  if [ -n "$manque" ]; then
    echo
    erreur "Sauvegarde INCOMPLÈTE, il manque :$manque. Ne passe pas à l'étape suivante. Journal : $dest/journal.txt"
  fi
  echo
  echo "Sauvegarde complète : $dest"
  echo "Copie ce dossier sur un autre support (disque externe, cloud privé) avant toute écriture."
  echo "Éteins le robot, débranche le câble : il redémarrera normalement sur l'appli Lidl."
}

case "${1:-}" in
  preparer) preparer ;;
  lire) shift; lire "$@" ;;
  *) sed -n '2,11p' "$0" | sed 's/^# \{0,1\}//'; exit 1 ;;
esac
