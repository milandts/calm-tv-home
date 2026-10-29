#!/usr/bin/env bash
# Prüft die Layout-Regeln aus docs/DESIGN.md im Quelltext des Launchers (schnell, ohne Gerät).
#
# Aufruf: scripts/check-rules.sh [Quellordner]
# Regel 1: Die Bildschirmgröße wird nur über Ui.usableWidth/usableHeight gelesen (sie ziehen den Sicherheitsrand ab).
#          Direkte Zugriffe auf widthPixels/heightPixels sind nur in den Dateien aus ERLAUBT erlaubt.
# Regel 2: Eine Seite mit .preview() (kompakte Ansicht mit Live-Vorschau) zeigt nur Wahl-, Regler- und Schalterzeilen, Knöpfe und Hinweise.
#          SettingsModel.Header und Rows.link dort würden stillschweigend fehlen (so ging einmal "Name für den Gruß" verloren).
# Regel 3: Eine Zeile mit "// regel-ok: Grund" ist von der Prüfung ausgenommen (Grund ist Pflicht).
# Beendet mit Status 1, wenn eine Regel verletzt ist.
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
SRC=${1:-$ROOT/launcher/src/app/calmtv/home}
ERLAUBT='^(Ui|Metrics)\.java$'
bad=0

while IFS=: read -r file line text; do
  base=$(basename "$file")
  [[ $base =~ $ERLAUBT ]] && continue
  [[ $text == *"regel-ok:"* ]] && continue
  echo "Regel 1: $base:$line liest die Bildschirmgröße direkt (Ui.usableWidth/usableHeight nutzen): ${text## }"
  bad=1
done < <(grep -n -E 'widthPixels|heightPixels' "$SRC"/*.java | sed -E 's/^([^:]*):([0-9]+):/\1:\2:/')

# Regel 2: keine Überschrift und kein Link auf einer Seite mit .preview()
python3 -I - "$SRC" <<'PY' || bad=1
import re, sys
from pathlib import Path
bad = 0
for f in sorted(Path(sys.argv[1]).glob("SettingsPages*.java")):
    text = f.read_text(encoding="utf-8")
    for m in re.finditer(r'new SettingsModel\.Page\("([^"]+)"[^;]*\.preview\(\);(.*?)\n        return page;', text, re.S):
        for token in ("SettingsModel.Header(", "Rows.link("):
            if token in m.group(2):
                print("Regel 2: %s, Seite %s: %s ist in der Vorschau-Ansicht nicht darstellbar" % (f.name, m.group(1), token.rstrip("(")))
                bad = 1
sys.exit(bad)
PY

if [[ $bad -eq 0 ]]; then
  echo "OK: Layout-Regeln eingehalten"
else
  exit 1
fi
