#!/usr/bin/env bash
# Schneller Übersetzungstest ohne APK: javac + d8 (d8 fängt Fehler, die javac nicht sieht, z. B. anonyme Klassen in Feld-Initialisierern).
#
# Aufruf: scripts/check-build.sh launcher | dream [Quellordner]
#   Der optionale Quellordner ersetzt den Standard (launcher/src bzw. dream/src), z. B. für eine Kopie.
# Ergebnis: Ausgabe der Fehler, Exit-Code 0 nur bei Erfolg. Schreibt nur in ein temporäres Verzeichnis.
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
# shellcheck source=env.sh
source "$ROOT/scripts/env.sh"

case ${1:-} in
  launcher) SRC=${2:-$ROOT/launcher/src} ;;
  dream)    SRC=${2:-$ROOT/dream/src} ;;
  *) echo "Aufruf: $0 launcher|dream" >&2; exit 2 ;;
esac

TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
find "$SRC" -name '*.java' > "$TMP/sources.txt"
mkdir -p "$TMP/classes"
# R.java fehlt hier; Ressourcen-IDs kommen nur im Voll-Build vor. Der Launcher nutzt R bisher nicht in Quelltext-Dateien.
javac --release 17 -nowarn -Xlint:none -encoding UTF-8 -cp "$ANDROID_JAR" -d "$TMP/classes" @"$TMP/sources.txt"
find "$TMP/classes" -name '*.class' > "$TMP/classes.txt"
"$BUILD_TOOLS_DIR/d8" --release --min-api 22 --lib "$ANDROID_JAR" --output "$TMP" @"$TMP/classes.txt"
[[ $1 == launcher ]] && "$ROOT/scripts/check-rules.sh" "$SRC/app/calmtv/home"
echo "OK: $1 übersetzt ($(wc -l < "$TMP/sources.txt" | tr -d ' ') Dateien)"
