#!/usr/bin/env bash
# Stellt die Dateien für ein GitHub-Release zusammen: beide APKs, Prüfsummen, Bildnachweise, Lizenzen und die Installationsanleitung.
#
# Aufruf: scripts/make-release.sh [--draft <Besitzer/Repo>] [Ausgabeordner]      (vorher einmal scripts/setup.sh, das baut out/TVHome.apk und out/TVHomeDream.apk)
# Ergebnis: <Ausgabeordner>/tv-home-<Version>/ (Standard: release/). Ohne --draft wird nichts hochgeladen.
# Mit --draft legt das Skript auf GitHub ein ENTWURF-Release v<Version> an (Texte aus CHANGELOG.md, die vier Dateien als Anhang). Vorher prüft es,
# dass die APK mit demselben Schlüssel signiert ist wie die des neuesten Releases; sonst brechen Updates auf installierten Geräten. Der Entwurf
# ist für niemanden sichtbar: veröffentlichen musst du ihn selbst (gh release edit v<Version> --draft=false), nachdem der Quelltext gepusht ist.
# Die APKs enthalten die Fotos: Bildnachweise und Lizenzen gehören deshalb in dasselbe Release (PHOTO_CREDITS.txt, THIRD_PARTY.md).
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
DRAFT_REPO=""
if [[ ${1:-} == --draft ]]; then
  DRAFT_REPO=${2:?Besitzer/Repo fehlt}
  shift 2
fi
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

[[ -n $DRAFT_REPO ]] || exit 0

# Unterschreibt derselbe Schlüssel wie beim neuesten Release? (SHA-256 des Zertifikats)
cert_digest() { "$BUILD_TOOLS_DIR/apksigner" verify --print-certs "$1" | sed -n 's/.*certificate SHA-256 digest: //p' | head -1; }
NEW_DIGEST=$(cert_digest "$TARGET/TVHome.apk")
[[ -n $NEW_DIGEST ]] || { echo "Zertifikat der neuen APK nicht lesbar." >&2; exit 1; }
CHECK=$(mktemp -d)
trap 'rm -rf "$CHECK"' EXIT
if gh release download --repo "$DRAFT_REPO" --pattern TVHome.apk --dir "$CHECK" 2>/dev/null; then
  OLD_DIGEST=$(cert_digest "$CHECK/TVHome.apk")
  [[ $OLD_DIGEST == "$NEW_DIGEST" ]] || { echo "ABBRUCH: anderer Signaturschlüssel als beim neuesten Release (alt $OLD_DIGEST, neu $NEW_DIGEST)." >&2; exit 1; }
  echo "Signatur gleich wie beim neuesten Release ($NEW_DIGEST)."
else
  echo "Hinweis: kein früheres Release gefunden, Schlüssel nicht verglichen." >&2
fi

# Texte: der Abschnitt dieser Version aus dem Änderungsprotokoll (Überschrift "## <Version> - <Datum>")
NOTES="$CHECK/notes.md"
awk -v v="$VERSION" '/^## /{ if (on) exit; on = ($2 == v) } on && !/^## /' "$ROOT/CHANGELOG.md" > "$NOTES"
[[ -s $NOTES ]] || { echo "ABBRUCH: in CHANGELOG.md steht kein Abschnitt \"## $VERSION\"." >&2; exit 1; }
gh release create "v$VERSION" --repo "$DRAFT_REPO" --draft --title "TV Home $VERSION" --notes-file "$NOTES" \
  "$TARGET/TVHome.apk" "$TARGET/TVHomeDream.apk" "$TARGET/SHA256SUMS" "$TARGET/PHOTO_CREDITS.txt" "$TARGET/THIRD_PARTY.md" "$TARGET/LICENSE" "$TARGET/INSTALL.md"
echo "Entwurf v$VERSION angelegt (unsichtbar). Veröffentlichen: gh release edit v$VERSION --repo $DRAFT_REPO --draft=false"
