#!/bin/bash
# Full backup of the Smart's memory from a Mac, with mtkclient, READ ONLY.
# Nothing is ever written to the robot: the script only runs read commands (printgpt, rl, rf),
# hard-coded below. Messages are in French (user-facing).
#
#   bash outils/sauvegarde-mac.sh preparer              # once: installs mtkclient (no robot needed)
#   bash outils/sauvegarde-mac.sh lire                  # robot off, plugged on "Android USB"
#   bash outils/sauvegarde-mac.sh lire --sans-donnees   # without the userdata partition (shorter)
#
# The backup goes to ~/Camybrith-sauvegarde/<date>/, OUTSIDE the project: it holds the robot's
# serial number and settings (nvram, proinfo). Never publish or commit it.
set -eu

MTK_TAG=v2.1.4.1                          # mtkclient version checked for this script
MTK_SRC=${MTK_SRC:-$HOME/Projets/mtkclient}
MTK_VENV=${MTK_VENV:-$HOME/Projets/mtkclient-venv}
MTK_STUB=${MTK_STUB:-$HOME/Projets/mtkclient-stub}
BACKUP_ROOT=${BACKUP_ROOT:-$HOME/Camybrith-sauvegarde}
# Partitions needed to ever go back: the backup must contain them.
ESSENTIAL="boot recovery cache lk seccfg nvram proinfo system vendor"

fail() { echo "ERREUR : $*" >&2; exit 1; }

usage() {
  cat <<'TXT'
Sauvegarde complète de la mémoire du robot depuis le Mac, en LECTURE SEULE (rien n'est écrit).

  bash outils/sauvegarde-mac.sh preparer              une fois : installe mtkclient (sans le robot)
  bash outils/sauvegarde-mac.sh lire                  robot éteint, branché sur « Android USB »
  bash outils/sauvegarde-mac.sh lire --sans-donnees   sans la partition userdata (plus court)

La sauvegarde va dans ~/Camybrith-sauvegarde/<date>/, hors du projet : elle contient le numéro de
série et les réglages du robot. Ne jamais la publier.
TXT
  exit 1
}

prepare() {
  command -v brew >/dev/null || fail "Homebrew manque (https://brew.sh)."
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
  # mtkclient always imports "fuse", which is only used to mount the memory as a disk. Rather than
  # installing macFUSE (a system extension), a fake module is enough for reading.
  mkdir -p "$MTK_STUB"
  cat > "$MTK_STUB/fuse.py" <<'EOF'
# Fake "fuse" module (Camybrith): mtkclient's "fs" command is not used.
class Operations: pass
class LoggingMixIn: pass
class FuseOSError(OSError): pass
def FUSE(*a, **k):
    raise RuntimeError("FUSE mount not available (macFUSE not installed)")
EOF
  mtk --help >/dev/null || fail "mtkclient ne se lance pas."
  echo "mtkclient $MTK_TAG prêt. Le jour du branchement : bash outils/sauvegarde-mac.sh lire"
}

mtk() { (cd "$MTK_SRC" && PYTHONPATH="$MTK_STUB" "$MTK_VENV/bin/mtk" "$@"); }

read_all() {
  local skip_userdata=0
  [ "${1:-}" = "--sans-donnees" ] && skip_userdata=1
  [ -x "$MTK_VENV/bin/mtk" ] || fail "mtkclient n'est pas installé : bash outils/sauvegarde-mac.sh preparer"
  [ "$(git -C "$MTK_SRC" describe --tags 2>/dev/null)" = "$MTK_TAG" ] \
    || fail "mtkclient n'est pas en $MTK_TAG : relance « preparer »."

  local dest
  dest="$BACKUP_ROOT/$(date +%Y-%m-%d_%H%M)"
  case "$dest" in *" "*) fail "Le dossier de sauvegarde ne doit pas contenir d'espace : $dest";; esac
  local free_gb=$(( $(df -k "$HOME" | awk 'NR==2{print $4}') / 1024 / 1024 ))
  [ "$free_gb" -ge 12 ] || fail "Il faut au moins 12 Go libres sur le Mac (il en reste $free_gb)."
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
  [ "$skip_userdata" = 1 ] && skip=" --skip userdata"
  # Reads only. A single connection for everything (the robot accepts only one session).
  local cmds="printgpt;rl $dest/partitions$skip;rf $dest/boot1.bin --parttype boot1;rf $dest/boot2.bin --parttype boot2"
  mtk multi "$cmds" 2>&1 | tee "$dest/journal.txt"

  echo
  echo "Vérification de la sauvegarde…"
  grep -iE "sbc|sla|daa|hw ?code|secure|target config" "$dest/journal.txt" | sort -u > "$dest/securite.txt" || true
  local missing="" p f
  for p in $ESSENTIAL; do
    f=$(ls "$dest/partitions/$p".* "$dest/partitions/$p" 2>/dev/null | head -1 || true)
    if [ -z "$f" ] || [ ! -s "$f" ]; then missing="$missing $p"; fi
  done
  find "$dest" -type f -size 0 -print | sed 's/^/  vide : /'
  (cd "$dest" && find partitions boot1.bin boot2.bin -type f 2>/dev/null | sort | xargs shasum -a 256 > SHA256SUMS) || true
  du -sh "$dest" | awk '{print "Taille totale : "$1}'
  echo "Sécurité du démarrage (extrait du journal) :"
  sed 's/^/  /' "$dest/securite.txt"
  if [ -n "$missing" ]; then
    echo
    fail "Sauvegarde INCOMPLÈTE, il manque :$missing. Ne passe pas à l'étape suivante. Journal : $dest/journal.txt"
  fi
  echo
  echo "Sauvegarde complète : $dest"
  echo "Copie ce dossier sur un autre support (disque externe, cloud privé) avant toute écriture."
  echo "Éteins le robot, débranche le câble : il redémarrera normalement sur l'appli Lidl."
}

case "${1:-}" in
  preparer) prepare ;;
  lire) shift; read_all "$@" ;;
  *) usage ;;
esac
