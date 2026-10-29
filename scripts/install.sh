#!/usr/bin/env bash
# Installiert TV Home auf einem Fire TV Stick und bindet den HOME-Dienst neu.
#
# Aufruf:   ANDROID_SERIAL=<IP>:5555 scripts/install.sh [Pfad zur APK]
# Beispiel: ANDROID_SERIAL=<IP-des-Sticks>:5555 scripts/install.sh out/TVHome.apk
#
# ADB-Debugging muss am Stick eingeschaltet und per "adb connect <IP>:5555" verbunden sein.
# Nach jeder Neuinstallation muss der Accessibility-Dienst einmal neu gebunden werden, sonst bleibt
# die HOME-Taste bei Amazons Startseite. Das erledigt dieses Skript.
set -euo pipefail

APK=${1:-$(cd "$(dirname "$0")/.." && pwd)/out/TVHome.apk}
: "${ANDROID_SERIAL:?ANDROID_SERIAL=<IP>:5555 setzen}"
SERVICE="app.calmtv.home/app.calmtv.home.HomeKeyService"
OTHER_SERVICE="tv.dumbtv.home/tv.dumbtv.home.Guard"   # nur als "anderer Wert", damit das System neu bindet

adb install -r "$APK"
adb shell settings put secure enabled_accessibility_services "$OTHER_SERVICE"
sleep 2
adb shell settings put secure enabled_accessibility_services "$SERVICE"
adb shell settings put secure accessibility_enabled 1
# Die schnelle HOME-Taste startet TV Home über einen Alarm; der feuert nur sofort, wenn min_futurity=0 gilt.
CURRENT=$(adb shell settings get global alarm_manager_constants | tr -d '\r')
if [[ -z $CURRENT || $CURRENT == null ]]; then
  adb shell settings put global alarm_manager_constants min_futurity=0
elif [[ $CURRENT != *min_futurity=0* ]]; then
  echo "Hinweis: alarm_manager_constants ist '$CURRENT'. Ohne min_futurity=0 greift nur der langsamere Rückfall-Weg." >&2
fi
adb shell am start -n app.calmtv.home/.HomeActivity
