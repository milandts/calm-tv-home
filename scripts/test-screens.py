#!/usr/bin/env python3
"""Prüft die Startseite, die Einstellungen und das Kontrollzentrum bei anderen Bildschirmgrößen, indem der Stick sie nur vortäuscht (wm size, wm density).

Aufruf: ANDROID_SERIAL=<IP>:5555 scripts/test-screens.py [Ausgabeordner]
Profile: 1280x720 bei 213 dpi (ein 720p-Fernseher) und 3840x2160 bei 640 dpi (4K). Je Profil laufen dieselben Prüfungen wie in
scripts/test-matrix.py (nichts im Sicherheitsrand, kein abgeschnittener Text, Dock mittig und Kacheln darin) für Randfälle von Rand und Schriftgröße.
Größe und Dichte werden in jedem Fall zurückgesetzt (auch bei Fehlern und Abbruch mit Strg+C); die Einstellungen des Nutzers werden vorher gesichert
und danach wiederhergestellt. Bleibt der Stick nach einem harten Abbruch verstellt: adb shell wm size reset; adb shell wm density reset
"""
import importlib.util
import os
import sys
import tempfile
import time

spec = importlib.util.spec_from_file_location("tm", os.path.join(os.path.dirname(os.path.abspath(__file__)), "test-matrix.py"))
tm = importlib.util.module_from_spec(spec)
spec.loader.exec_module(tm)

PROFILES = [("720p", 1280, 720, 213), ("4k", 3840, 2160, 640)]
CASES = [(0, 100, 1), (48, 130, 1), (24, 90, 2)]   # Rand, Schrift, Dock


def set_display(width, height, dpi):
    tm.adb("shell", "wm", "size", "%dx%d" % (width, height))
    tm.adb("shell", "wm", "density", str(dpi))
    tm.W, tm.H, tm.DENSITY = width, height, dpi / 160.0
    time.sleep(6)   # die Activity baut sich bei geänderter Dichte neu auf


def reset_display():
    """Setzt Größe und Dichte zurück und prüft nach; reißt die ADB-Verbindung gerade ab, wird neu verbunden und es gibt mehrere Versuche."""
    tm.W, tm.H, tm.DENSITY = 1920, 1080, 2.0
    for attempt in range(8):
        tm.adb("shell", "wm", "size", "reset", check=False)
        tm.adb("shell", "wm", "density", "reset", check=False)
        time.sleep(4)
        if "Override" not in tm.adb("shell", "wm", "size", check=False) + tm.adb("shell", "wm", "density", check=False):
            time.sleep(4)
            return True
        tm.adb("connect", os.environ["ANDROID_SERIAL"], check=False)
        time.sleep(3 * (attempt + 1))
    print("WARNUNG: Größe ließ sich nicht zurücksetzen. Von Hand: adb shell wm size reset; adb shell wm density reset")
    return False


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else tempfile.mkdtemp(prefix="screens-")
    os.makedirs(out, exist_ok=True)
    if not os.environ.get("ANDROID_SERIAL"):
        sys.exit("ANDROID_SERIAL=<IP>:5555 setzen")
    saved = tm.save_user_state()
    print("Einstellungen gesichert" if saved else "Hinweis: Einstellungen konnten nicht gesichert werden")
    failed = []
    try:
        for name, width, height, dpi in PROFILES:
            set_display(width, height, dpi)
            for margin, scale, dock in CASES:
                case = "%s_r%d_s%d_d%d" % (name, margin, scale, dock)
                values = dict(tm.DEFAULT, safeMargin=margin, textScale=scale, dockSize=dock)
                problems = tm.run_case(values, out, case)
                print("%-22s %s" % (case, "OK" if not problems else "FEHLER"))
                for line in problems:
                    print("   ", line)
                failed += problems
    finally:
        reset_ok = reset_display()
        restored = tm.restore_user_state(saved) if saved else tm.apply(tm.DEFAULT)
        print("Größe %s, Einstellungen %s" % ("zurückgesetzt" if reset_ok else "NICHT zurückgesetzt",
                                           "wiederhergestellt" if restored else "NICHT wiederhergestellt, bitte am Stick prüfen"))
    print("Bilder:", out)
    print("%d Fälle, %d Probleme" % (len(PROFILES) * len(CASES), len(failed)))
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
