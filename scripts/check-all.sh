#!/usr/bin/env bash
# Alle Prüfungen ohne Gerät in einem Lauf: Übersetzung beider Apps mit Layout-Regeln, Logik-Tests und Doku-Verweise.
#
# Aufruf: scripts/check-all.sh        (Exit-Code 0 nur, wenn alles besteht; der erste Fehler bricht ab)
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
run() {
  echo "== $*"
  "$@" 2>&1 | grep -v "^Hinweis\|^Warning\|class file version" || [[ ${PIPESTATUS[0]} -eq 0 ]]
  [[ ${PIPESTATUS[0]} -eq 0 ]] || { echo "FEHLER in: $*" >&2; exit 1; }
}
run "$ROOT/scripts/check-build.sh" launcher
run "$ROOT/scripts/check-build.sh" dream
run "$ROOT/scripts/test-unit.sh"
run python3 "$ROOT/scripts/check-doc-refs.py" "$ROOT"
echo "ALLES OK"
