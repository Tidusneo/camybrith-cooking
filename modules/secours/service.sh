#!/system/bin/sh
# Au démarrage (Magisk, sans bloquer le démarrage) : une fois Android prêt, le module termine une
# opération interrompue (retour à l'appli Lidl), recrée sa réserve d'espace et vérifie, après un
# retour, que l'appli Lidl est bien au premier plan. Journal : /data/adb/camybrith/demarrage.log
MODDIR=${0%/*}
until [ "$(getprop sys.boot_completed)" = "1" ]; do sleep 2; done
mkdir -p /data/adb/camybrith
chmod 700 /data/adb/camybrith
sh "$MODDIR/camybrith" demarrage > /data/adb/camybrith/demarrage.log 2>&1
