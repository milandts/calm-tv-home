#!/usr/bin/env bash
# Installiert den Bildschirmschoner "TV Home Bildschirmschoner" und macht ihn zum System-Bildschirmschoner.
#
# Aufruf:   ANDROID_SERIAL=<IP>:5555 scripts/install-dream.sh [Pfad zur APK]
# Beispiel: ANDROID_SERIAL=<IP-des-Sticks>:5555 scripts/install-dream.sh out/TVHomeDream.apk
#
# Geändert wird nur die Systemeinstellung "screensaver_components" (welcher Bildschirmschoner läuft). Zurück zu Amazons:
#   adb shell settings put secure screensaver_components com.amazon.ftv.screensaver/.app.services.ScreensaverService
# Die Startzeit steuert Amazons Einstellung "Bildschirmschoner" (Standard: 5 Minuten, intern screen_off_timeout).
set -euo pipefail

APK=${1:-$(cd "$(dirname "$0")/.." && pwd)/out/TVHomeDream.apk}
: "${ANDROID_SERIAL:?ANDROID_SERIAL=<IP>:5555 setzen}"

adb install -r "$APK"
adb shell settings put secure screensaver_components app.calmtv.home.dream/.AmbientDream
adb shell settings put secure screensaver_enabled 1
echo "Bildschirmschoner aktiv: $(adb shell settings get secure screensaver_components | tr -d '\r')"
echo "Wetter: in TV Home unter Einstellungen > Wetter einrichten die Stadt wählen."
