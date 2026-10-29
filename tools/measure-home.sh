#!/usr/bin/env bash
# Misst, wie lange Amazons Startseite nach HOME im Fokus ist, bevor TV Home übernimmt.
# Aufruf: measure-home.sh [Anzahl Läufe]
set -u
RUNS=${1:-3}
for n in $(seq 1 "$RUNS"); do
  adb shell am start -a android.intent.action.VIEW -d about:blank -p com.amazon.cloud9 >/dev/null 2>&1; sleep 6
  adb logcat -b events -c
  adb shell input keyevent KEYCODE_HOME; sleep 4
  adb logcat -b events -d -v epoch 2>/dev/null | tr -d '\r' | grep -E 'am_set_resumed_activity' > /tmp/home-events.$$ || true
  python3 -I - <<PY
import re
amazon = tvhome = None
for line in open("/tmp/home-events.$$"):
    t = float(line.split()[0])
    if "com.amazon.tv.launcher" in line and amazon is None: amazon = t
    if "app.calmtv.home" in line and tvhome is None: tvhome = t
if amazon is None: print("Lauf $n: Amazons Startseite nie im Fokus (direkter Wechsel)", "TV Home:", tvhome is not None)
elif tvhome is None: print("Lauf $n: TV Home hat nicht übernommen")
else: print("Lauf $n: Amazons Startseite %.0f ms im Fokus" % ((tvhome - amazon) * 1000))
PY
  rm -f /tmp/home-events.$$
  adb shell am force-stop com.amazon.cloud9; sleep 2
done
