#!/usr/bin/env bash
# Baut TV Home komplett neu: Bilder laden, APKs bauen.
#
# Aufruf: scripts/setup.sh
# Ergebnis: out/TVHome.apk (Launcher) und out/TVHomeDream.apk (Bildschirmschoner)
#
# Voraussetzungen: curl, python3, zip, openssl, JDK 17+ und das Android SDK
# mit Build-Tools 33 (siehe README und scripts/build.sh für die Umgebungsvariablen).
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
WORK="$ROOT/work"
OUT="$ROOT/out"
mkdir -p "$WORK"

echo "== 1/4 Hintergrundbilder laden (Wikimedia Commons)"
python3 -I "$ROOT/scripts/fetch-wallpapers.py" "$WORK/wallpapers-raw"

echo "== 2/4 Bilder zuschneiden und einfügen"
rm -rf "$WORK/wallpapers-cropped"
if command -v cwebp >/dev/null; then   # WebP (Qualität 90, optisch nicht vom JPEG zu unterscheiden) spart gut ein Drittel der APK-Größe
  java "$ROOT/tools/CropWallpapers.java" "$WORK/wallpapers-raw" "$WORK/wallpapers-cropped" --png
  for png in "$WORK"/wallpapers-cropped/*.png; do cwebp -quiet -q 90 -m 6 "$png" -o "${png%.png}.webp" && rm "$png"; done
else
  echo "Hinweis: cwebp fehlt (macOS: brew install webp, Linux: apt install webp); die Fotos bleiben JPEG und die APKs etwa ein Drittel größer."
  java "$ROOT/tools/CropWallpapers.java" "$WORK/wallpapers-raw" "$WORK/wallpapers-cropped"
fi
rm -rf "$ROOT/launcher/assets/wallpapers"; mkdir -p "$ROOT/launcher/assets/wallpapers"
python3 -I "$ROOT/scripts/add-wallpapers.py" "$ROOT/launcher" "$WORK/wallpapers-cropped"

echo "== 3/4 Launcher bauen"
"$ROOT/scripts/build.sh" "$ROOT/launcher" "$OUT"

echo "== 4/4 Bildschirmschoner bauen"
"$ROOT/scripts/build-dream.sh"
