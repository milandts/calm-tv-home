#!/usr/bin/env bash
# Baut den TV-Home-Launcher zu einer signierten APK (ohne Gradle, nur Android-SDK-Werkzeuge).
#
# Aufruf: scripts/build.sh <Quellordner> <Ausgabeordner> [APK-Name]   (Standard-Name: TVHome.apk)
#
# Umgebungsvariablen (alle optional):
#   ANDROID_SDK         Pfad zum Android SDK            (Standard: wird gesucht, siehe scripts/env.sh)
#   BUILD_TOOLS         Version der Build-Tools         (Standard: 33.0.0)
#   PLATFORM            Android-Plattform für android.jar (Standard: android-33)
#   TVHOME_KEY_DIR      Ordner mit tvhome.keystore und keystore.pass
#                       (Standard: ~/.config/firestick-tvhome, außerhalb des Repos)
#
# Der Signaturschlüssel wird beim ersten Lauf in TVHOME_KEY_DIR angelegt und danach wiederverwendet.
# Nur mit demselben Schlüssel lässt sich eine neue APK über die installierte installieren.
set -euo pipefail

SRC=$(cd "${1:?Launcher-Ordner fehlt}" && pwd)
OUT=${2:?Ausgabeordner fehlt}
mkdir -p "$OUT"
OUT=$(cd "$OUT" && pwd)
APK_NAME=${3:-TVHome.apk}

# shellcheck source=env.sh
source "$(dirname "$0")/env.sh"
KEY_DIR=${TVHOME_KEY_DIR:-$HOME/.config/firestick-tvhome}
KEYSTORE="$KEY_DIR/tvhome.keystore"
KEYPASS_FILE="$KEY_DIR/keystore.pass"
WORK="$OUT/work"

for tool in aapt2 d8 zipalign apksigner; do
  [[ -x $BUILD_TOOLS_DIR/$tool ]] || { echo "Fehlt: $BUILD_TOOLS_DIR/$tool" >&2; exit 1; }
done
[[ -f $ANDROID_JAR ]] || { echo "Fehlt: $ANDROID_JAR" >&2; exit 1; }
command -v javac >/dev/null || { echo "javac nicht gefunden (JDK 17 oder neuer nötig)" >&2; exit 1; }

rm -rf "$WORK"; mkdir -p "$WORK/classes"

# 1. Ressourcen und Manifest
"$BUILD_TOOLS_DIR/aapt2" compile --dir "$SRC/res" -o "$WORK/res.zip"
# Versionsnummer: Minuten seit 2026-01-01 (UTC), damit jede neue Version höher ist
VERSION_NAME=$(tr -d '[:space:]' < "$(dirname "$0")/../VERSION")   # die sichtbare Version steht in der Datei VERSION, gepflegt von Hand
VERSION_CODE=$(( ( $(date -u +%s) - 1767225600 ) / 60 ))   # 1767225600 = 2026-01-01 00:00 UTC, so rechnet es auf macOS und Linux gleich
"$BUILD_TOOLS_DIR/aapt2" link -o "$WORK/unsigned.apk" -I "$ANDROID_JAR" \
  --manifest "$SRC/AndroidManifest.xml" --min-sdk-version 22 --target-sdk-version 28 \
  --version-code "$VERSION_CODE" --version-name "$VERSION_NAME" "$WORK/res.zip"

# 2. Java -> Klassen -> dex
find "$SRC/src" -name '*.java' > "$WORK/sources.txt"
javac --release 17 -nowarn -encoding UTF-8 -cp "$ANDROID_JAR" -d "$WORK/classes" @"$WORK/sources.txt"
find "$WORK/classes" -name '*.class' > "$WORK/classes.txt"
"$BUILD_TOOLS_DIR/d8" --release --min-api 22 --lib "$ANDROID_JAR" --output "$WORK" @"$WORK/classes.txt"

# 3. classes.dex und Assets in die APK (Fotos unkomprimiert)
(cd "$WORK" && zip -q unsigned.apk classes.dex)
(cd "$SRC" && zip -q -0 -r "$WORK/unsigned.apk" assets -i '*.jpg' '*.webp' \
  && zip -q -r "$WORK/unsigned.apk" assets -x '*.jpg' '*.webp')

# 4. Ausrichten und signieren
if [[ ! -f $KEYSTORE ]]; then
  mkdir -p "$KEY_DIR" && chmod 700 "$KEY_DIR"
  ( umask 077; openssl rand -hex 16 > "$KEYPASS_FILE" )
  keytool -genkeypair -keystore "$KEYSTORE" -alias tvhome -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass:file "$KEYPASS_FILE" -keypass:file "$KEYPASS_FILE" -dname "CN=TV Home, O=Local" >/dev/null
  chmod 600 "$KEYSTORE" "$KEYPASS_FILE"
  echo "Neuer Signaturschlüssel angelegt in $KEY_DIR (sichern!)"
fi
"$BUILD_TOOLS_DIR/zipalign" -f -p 4 "$WORK/unsigned.apk" "$WORK/aligned.apk"
"$BUILD_TOOLS_DIR/apksigner" sign --ks "$KEYSTORE" --ks-key-alias tvhome \
  --ks-pass "file:$KEYPASS_FILE" --out "$OUT/$APK_NAME" "$WORK/aligned.apk"
"$BUILD_TOOLS_DIR/apksigner" verify "$OUT/$APK_NAME"
echo "Gebaut: $OUT/$APK_NAME ($(du -k "$OUT/$APK_NAME" | cut -f1) KB, Version $VERSION_CODE)"
