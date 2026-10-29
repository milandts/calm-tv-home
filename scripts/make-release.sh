#!/usr/bin/env bash
# Stellt die Dateien für ein GitHub-Release zusammen: beide APKs, Prüfsummen, Bildnachweise, Lizenzen und die Installationsanleitung.
#
# Aufruf: scripts/make-release.sh [Ausgabeordner]      (vorher einmal scripts/setup.sh, das baut out/TVHome.apk und out/TVHomeDream.apk)
# Ergebnis: <Ausgabeordner>/tv-home-<Version>/ (Standard: release/). Hochgeladen wird nichts; das Release legst du selbst an.
# Die APKs enthalten die Fotos: Bildnachweise und Lizenzen gehören deshalb in dasselbe Release (PHOTO_CREDITS.txt, THIRD_PARTY.md).
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
# shellcheck source=env.sh
source "$ROOT/scripts/env.sh"
for apk in TVHome.apk TVHomeDream.apk; do
  [[ -f $ROOT/out/$apk ]] || { echo "Fehlt: out/$apk (erst scripts/setup.sh)." >&2; exit 1; }
done

VERSION=$("$BUILD_TOOLS_DIR/aapt2" dump badging "$ROOT/out/TVHome.apk" | sed -n "s/.*versionName='\([^']*\)'.*/\1/p" | head -1)
[[ -n $VERSION ]] || { echo "Versionsnummer nicht lesbar." >&2; exit 1; }
TARGET="${1:-$ROOT/release}/tv-home-$VERSION"
rm -rf "$TARGET"
mkdir -p "$TARGET"

cp "$ROOT/out/TVHome.apk" "$ROOT/out/TVHomeDream.apk" "$TARGET/"
cp "$ROOT/LICENSE" "$ROOT/THIRD_PARTY.md" "$TARGET/"
cp "$ROOT/docs/public/INSTALL.md" "$TARGET/INSTALL.md"
unzip -p "$ROOT/out/TVHome.apk" assets/wallpapers/credits.txt > "$TARGET/PHOTO_CREDITS.txt"
(
  cd "$TARGET"
  if command -v sha256sum >/dev/null; then sha256sum TVHome.apk TVHomeDream.apk > SHA256SUMS; else shasum -a 256 TVHome.apk TVHomeDream.apk > SHA256SUMS; fi
)
echo "Fertig: $TARGET"
ls -l "$TARGET" | awk 'NR>1 {printf "  %8.1f MB  %s\n", $5/1048576, $9}'
echo "Signiert mit dem Schlüssel aus ~/.config/firestick-tvhome (gleicher Schlüssel für Updates nötig). Version $VERSION."
