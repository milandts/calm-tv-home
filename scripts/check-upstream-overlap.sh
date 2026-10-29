#!/usr/bin/env bash
# Zählt, wie viele nicht triviale Zeilen des Launcher-Quelltexts noch wortgleich im Original stehen (Maß für "eigenständig").
#
# Aufruf: scripts/check-upstream-overlap.sh [Originalordner]   (Standard: work/upstream, ein lokaler Klon von vom00/firestick-tv;
#         ohne Klon bricht das Skript mit Hinweis ab, der Build braucht ihn nicht)
# Nicht trivial: nach dem Kürzen mindestens 8 Zeichen, keine reine Klammer-, import-, package- oder Kommentarzeile.
# Mit VERBOSE=1 stehen die Zeilen selbst mit Datei und Zeilennummer in der Ausgabe.
# Beendet mit Status 1, solange es Treffer gibt. Ausgabe: Treffer je Datei und Gesamtzahl.
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
UP=${1:-$ROOT/work/upstream/launcher}
[[ -d $UP ]] || { echo "Kein Original-Klon unter $UP (git clone https://github.com/vom00/firestick-tv work/upstream)." >&2; exit 2; }

VERBOSE=${VERBOSE:-0} python3 -I - "$UP" "$ROOT/launcher" <<'PY'
import os, re, sys
from pathlib import Path
up, ours = Path(sys.argv[1]), Path(sys.argv[2])
SKIP = re.compile(r"^(import |package |//|/\*|\*|\}|\{|@Override|\)|;)")
RUN = 3   # so viele nicht triviale Zeilen hintereinander müssen wortgleich sein, damit sie zählen (einzelne Redewendungen sind kein Übernehmen)
def norm(path):
    out = []
    for number, raw in enumerate(path.read_text(encoding="utf-8", errors="replace").splitlines(), 1):
        t = " ".join(raw.split())
        if len(t) >= 8 and not SKIP.match(t):
            out.append((t, number))
    return out
def files(root):
    return [f for f in sorted(root.rglob("*")) if f.is_file() and f.suffix in (".java", ".xml") and "assets" not in f.parts]
shingles = set()
for f in files(up):
    n = [t for t, _ in norm(f)]
    for i in range(len(n) - RUN + 1):
        shingles.add(tuple(n[i:i + RUN]))
total = 0
for f in files(ours):
    n = norm(f)
    texts = [t for t, _ in n]
    covered = set()
    for i in range(len(texts) - RUN + 1):
        if tuple(texts[i:i + RUN]) in shingles:
            covered.update(range(i, i + RUN))
    if covered:
        print("%4d  %s" % (len(covered), f.relative_to(ours)))
        total += len(covered)
        if os.environ.get("VERBOSE") == "1":
            for i in sorted(covered):
                print("        %s:%d  %s" % (f.name, n[i][1], n[i][0][:100]))
print("Gesamt: %d Zeilen in wortgleichen Blöcken von mindestens %d nicht trivialen Zeilen" % (total, RUN))
sys.exit(1 if total else 0)
PY
