#!/usr/bin/env python3
"""Schnappschuss der Oberfläche am Stick (Texte und Positionen), zum Vergleich vor und nach einem Umbau.

Aufruf: ANDROID_SERIAL=<IP>:5555 scripts/snapshot-ui.py speichern <Datei.json>
        ANDROID_SERIAL=<IP>:5555 scripts/snapshot-ui.py vergleichen <Datei.json>
Besucht Startseite, Apps, Suche, Kontrollzentrum, die Kategorien der Einstellungen und die Seiten unter Darstellung. Gedrückt werden nur
Richtungs-, Auswahl-, Zurück-, Menü- und HOME-Taste, jeweils nach Prüfung des Fokus, und nur auf eigenen Oberflächen (nichts startet eine fremde App).
Uhrzeit, Datum und Wetterzeile werden nicht verglichen (ändern sich von selbst). Exit-Code 1 bei Abweichung.
"""
import importlib.util
import json
import os
import re
import sys

spec = importlib.util.spec_from_file_location("tm", os.path.join(os.path.dirname(os.path.abspath(__file__)), "test-matrix.py"))
tm = importlib.util.module_from_spec(spec)
spec.loader.exec_module(tm)

VOLATILE = re.compile(r"^\d{1,2}:\d{2}( ?[AP]M)?$|^(MONTAG|DIENSTAG|MITTWOCH|DONNERSTAG|FREITAG|SAMSTAG|SONNTAG)|°|^GUTEN |^● ", re.I)
DARSTELLUNG_PAGES = ["Uhr und Datum", "Layout", "Glas", "Hintergrund", "Akzentfarbe", "Bewegung und Fokus"]


def dump():
    """Sichtbare Elemente als sortierte Liste (Text, Grenzen, fokussierbar)."""
    out = []
    for n in tm.nodes():
        text = tm.label(n)
        if n.get("package") != "app.calmtv.home" or not (text or n.get("focusable") == "true"):
            continue
        if VOLATILE.search(text):
            continue
        out.append([text, list(n["box"]), n.get("focusable") == "true"])
    return sorted(out, key=lambda e: (e[1][1], e[1][0], e[0]))


BAR = ("Apps", "Suche", "Einstellungen")


def home():
    """HOME drücken und sicherstellen, dass TV Home vorn ist (sonst startet es der Wächter, damit ein Ausrutscher nicht alle Folgeschritte verfälscht)."""
    tm.home()
    if "app.calmtv.home/.HomeActivity" not in tm.adb("shell", "dumpsys", "activity", "activities").split("mResumedActivity", 1)[-1][:200]:
        tm.adb("shell", "am", "start", "-n", "app.calmtv.home/.HomeActivity")
        tm.time.sleep(2.5)


def to_bar():
    """Von der Startseite in die Leiste: Hoch drücken, bis der Fokus auf einer der drei Tasten liegt (höchstens dreimal)."""
    home()
    for _ in range(3):
        tm.key("DPAD_UP", 1.0)
        f = tm.focused()
        if f and tm.label(f) in BAR:
            return True
    return False


def reach(label):
    """Fokus auf die Leisten-Taste mit diesem Namen: erst nach rechts, dann nach links suchen (der Fokus startet auf der zuletzt benutzten)."""
    return tm.focus_until(label, "DPAD_RIGHT", 3) or tm.focus_until(label, "DPAD_LEFT", 3)


def settings_root():
    if not tm.open_settings():
        return False
    for _ in range(12):
        tm.key("DPAD_UP", 0.15)
    return True


def collect():
    snap = {}
    home()
    snap["home"] = dump()
    tm.key("DPAD_RIGHT", 0.6)
    snap["home_dock_rechts"] = dump()
    snap["home_leiste"] = dump() if to_bar() else "NAV FEHLER"
    if reach("Apps"):
        tm.key("DPAD_CENTER", 2.0)   # Apps (eigene Oberfläche)
        snap["apps"] = dump()
        tm.key("BACK", 1.2)
    else:
        snap["apps"] = "NAV FEHLER"
    if to_bar() and reach("Suche"):
        tm.key("DPAD_CENTER", 2.0)
        snap["suche"] = dump()
        tm.key("BACK", 1.2)
    else:
        snap["suche"] = "NAV FEHLER"
    to_bar()
    tm.key("MENU", 1.8)
    snap["kontrollzentrum"] = dump() if any("Kontrollzentrum" in tm.label(n) for n in tm.nodes()) else "NAV FEHLER"
    tm.key("BACK", 1.2)
    if settings_root():
        for i in range(8):   # Kategorien der Seitenleiste nacheinander
            snap["einstellungen_%d" % i] = dump()
            tm.key("DPAD_DOWN", 0.8)
        for title in DARSTELLUNG_PAGES:
            if not settings_root() or not tm.focus_until("Darstellung", "DPAD_DOWN", 10):
                snap["seite_" + title] = "NAV FEHLER"
                continue
            tm.key("DPAD_RIGHT", 0.8)
            if tm.focus_until(title, "DPAD_DOWN", 14):
                tm.key("DPAD_CENTER", 1.5)
                snap["seite_" + title] = dump()
            else:
                snap["seite_" + title] = "NAV FEHLER"
    home()
    return snap


TOLERANCE_PX = 12   # Fokus-Animationen vergrößern eine Taste um bis zu etwa 9 Pixel je Seite


def same(a, b):
    return a[0] == b[0] and a[2] == b[2] and all(abs(x - y) <= TOLERANCE_PX for x, y in zip(a[1], b[1]))


def main():
    if len(sys.argv) != 3 or sys.argv[1] not in ("speichern", "vergleichen") or not os.environ.get("ANDROID_SERIAL"):
        sys.exit(__doc__)
    snap = collect()
    if sys.argv[1] == "speichern":
        with open(sys.argv[2], "w", encoding="utf-8") as f:
            json.dump(snap, f, ensure_ascii=False, indent=1)
        print("%d Ansichten gespeichert" % len(snap), [k for k, v in snap.items() if v == "NAV FEHLER"] or "")
        return
    with open(sys.argv[2], encoding="utf-8") as f:
        old = json.load(f)
    diffs = 0
    for key in sorted(set(old) | set(snap)):
        a, b = old.get(key), snap.get(key)
        if not isinstance(a, list) or not isinstance(b, list):
            if a != b:
                diffs += 1
                print("ABWEICHUNG %s: %s -> %s" % (key, a if isinstance(a, str) else "Ansicht", b if isinstance(b, str) else "Ansicht"))
            continue
        only_old = [e for e in a if not any(same(e, f) for f in b)]
        only_new = [f for f in b if not any(same(e, f) for e in a)]
        if only_old or only_new:
            diffs += 1
            print("ABWEICHUNG %s: nur vorher %s | nur jetzt %s" % (key, only_old[:4], only_new[:4]))
    print("%d Ansichten verglichen, %d Abweichungen" % (len(snap), diffs))
    sys.exit(1 if diffs else 0)


if __name__ == "__main__":
    main()
