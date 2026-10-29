#!/usr/bin/env bash
# Baut den Bildschirmschoner (dream/) zu out/TVHomeDream.apk.
#
# Aufruf: scripts/build-dream.sh        (vorher einmal scripts/setup.sh, das liefert die Fotos und die Schrift)
#
# Die Fotos und die Schrift kommen aus dem gebauten Launcher (launcher/assets) und werden nur in einen
# Arbeitsordner kopiert, nicht ins Repo.
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
LAUNCHER_ASSETS="$ROOT/launcher/assets"
STAGE="$ROOT/work/tree/dream"

[[ -d $LAUNCHER_ASSETS/wallpapers ]] || { echo "Erst scripts/setup.sh ausführen (Fotos fehlen)." >&2; exit 1; }

rm -rf "$STAGE"; mkdir -p "$STAGE/assets/photos" "$STAGE/assets/fonts"
cp -R "$ROOT/dream/." "$STAGE/"
cp "$LAUNCHER_ASSETS/wallpapers/credits.txt" "$STAGE/assets/photos/"
for photo in "$LAUNCHER_ASSETS"/wallpapers/*.jpg "$LAUNCHER_ASSETS"/wallpapers/*.webp; do
  [[ -f $photo ]] && cp "$photo" "$STAGE/assets/photos/"
done
[[ -n $(find "$STAGE/assets/photos" -name "*.jpg" -o -name "*.webp") ]] || { echo "Keine Fotos in $LAUNCHER_ASSETS/wallpapers (erst scripts/setup.sh)." >&2; exit 1; }
cp "$LAUNCHER_ASSETS/fonts/Nunito.ttf" "$LAUNCHER_ASSETS/fonts/OFL.txt" "$STAGE/assets/fonts/"
"$ROOT/scripts/build.sh" "$STAGE" "$ROOT/out" TVHomeDream.apk
