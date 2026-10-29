#!/usr/bin/env bash
# Führt die Prüfungen der reinen Logik aus (tests/), ohne Gerät: übersetzt den Launcher mit javac und startet UnitTests auf der JVM.
#
# Aufruf: scripts/test-unit.sh
# Die Klassen des Android-SDK (android.jar) liegen nur im Klassenpfad; die Prüfungen rufen keine Android-Methoden auf.
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
# shellcheck source=env.sh
source "$ROOT/scripts/env.sh"
[[ -f $ANDROID_JAR ]] || { echo "Fehlt: $ANDROID_JAR" >&2; exit 1; }

TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
find "$ROOT/launcher/src" "$ROOT/tests" -name '*.java' > "$TMP/sources.txt"
javac --release 17 -nowarn -Xlint:none -encoding UTF-8 -cp "$ANDROID_JAR" -d "$TMP/classes" @"$TMP/sources.txt"
java -cp "$TMP/classes:$ANDROID_JAR" app.calmtv.home.UnitTests
