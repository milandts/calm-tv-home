#!/usr/bin/env bash
# Bindet den HOME-Dienst von TV Home neu, ohne die APK zu installieren.
#
# Aufruf: ANDROID_SERIAL=<IP>:5555 scripts/rebind.sh
#
# Nötig, wenn HOME bei Amazons Startseite bleibt. Das passiert, nachdem TV Home per "Stoppen erzwingen" beendet
# oder neu installiert wurde: Android nimmt den Accessibility-Dienst dann aus den aktiven Diensten.
set -euo pipefail

: "${ANDROID_SERIAL:?ANDROID_SERIAL=<IP>:5555 setzen}"
SERVICE="app.calmtv.home/app.calmtv.home.HomeKeyService"
OTHER_SERVICE="tv.dumbtv.home/tv.dumbtv.home.Guard"   # nur als "anderer Wert", damit das System neu bindet

echo "Aktive Dienste vorher: '$(adb shell settings get secure enabled_accessibility_services | tr -d '\r')'"
adb shell settings put secure enabled_accessibility_services "$OTHER_SERVICE"
sleep 2
adb shell settings put secure enabled_accessibility_services "$SERVICE"
adb shell settings put secure accessibility_enabled 1
adb shell am start -n app.calmtv.home/.HomeActivity
echo "Aktive Dienste nachher: '$(adb shell settings get secure enabled_accessibility_services | tr -d '\r')'"
