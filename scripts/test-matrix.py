#!/usr/bin/env python3
"""Randfall-Matrix am echten Stick: Sicherheitsrand x Schriftgröße x Dock-Größe, jeweils Startseite, Einstellungen, Kontrollzentrum.

Aufruf: ANDROID_SERIAL=<IP>:5555 scripts/test-matrix.py [Ausgabeordner] [--quick]
  --quick prüft nur wenige Eckfälle statt aller Kombinationen; --cases=48:130:1,0:100:1 nur die genannten (Rand:Schrift:Dock).

Setzt die Werte über "Sicherung laden" (nur der Teil "config"), liest nach jedem Wechsel die Oberfläche per uiautomator und prüft:
  1. Alle Elemente liegen im nutzbaren Bereich (Bildschirm minus Sicherheitsrand); Vollbild-Hintergrund ist ausgenommen.
  2. Kein sichtbarer Text endet mit "…" (abgeschnitten).
  3. Das Dock-Glas ist mittig (links und rechts gleich viel Rand, +-3 px) und alle Dock-Kacheln liegen darin.
Speichert zu jedem Fall einen Screenshot. Am Anfang sichert es die Einstellungen des Nutzers (Sicherung speichern) und stellt sie am Ende wieder her; gelingt das Sichern nicht, gilt am Ende der Standard. Exit-Code 1, wenn ein Fall durchfällt.
Fasst keine fremde App an: gedrückt werden nur Richtungs-, Auswahl-, Zurück- und HOME-Taste, jeweils nach Prüfung des Fokus.
"""
import itertools
import json
import os
import re
import subprocess
import sys
import tempfile
import time

W, H, DENSITY = 1920, 1080, 2.0
TOL = 3
BACKUP_PATH = "/sdcard/Android/data/app.calmtv.home/files/tvhome-sicherung.json"
# Alle Werte, die ein Testfall ändern darf; "Standard" setzt jeden zurück (sonst bleiben Werte aus früheren Fällen hängen).
DEFAULT = {"safeMargin": 0, "textScale": 100, "dockSize": 1, "dockPos": 0, "dockGap": 16,
           "dim": 0, "panelOpacity": 100, "focusScale": 110, "focusRing": 3,
           "glassOpacity": 100, "glassDarken": 0, "glassSaturation": 160, "glassBrightness": 78,
           "glassRim": 100, "glassSheen": 100, "glassShadow": 100,
           "bgRhythm": 0, "bgOwn": 0, "bgOnlyOwn": 0, "bgFavoritesOnly": 0}
NODE = re.compile(r"<node ([^>]*)>")


def adb(*args, check=True):
    r = subprocess.run(["adb", *args], capture_output=True, text=True)
    if check and r.returncode != 0:
        raise RuntimeError("adb %s: %s" % (" ".join(args), r.stderr.strip()))
    return r.stdout


def key(name, pause=0.5):
    adb("shell", "input", "keyevent", name)
    time.sleep(pause)


def nodes():
    for attempt in range(4):   # uiautomator scheitert gelegentlich, solange die Oberfläche animiert
        try:
            adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
            break
        except RuntimeError:
            if attempt == 3:
                raise
            time.sleep(1.0)
    xml = adb("shell", "cat", "/sdcard/ui.xml")
    out = []
    for m in NODE.finditer(xml):
        attrs = dict(re.findall(r'([\w-]+)="([^"]*)"', m.group(1)))
        b = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", attrs.get("bounds", ""))
        if not b:
            continue
        attrs["box"] = tuple(int(v) for v in b.groups())
        out.append(attrs)
    return out


def label(n):
    return n.get("text") or n.get("content-desc") or ""


def foreground():
    return adb("shell", "dumpsys", "activity", "activities")


def focused():
    """Das fokussierte Element samt Beschriftung; bei Zeilen ohne eigenen Text zählt der erste Text darin."""
    ns = nodes()
    f = next((n for n in ns if n.get("focused") == "true"), None)
    if f and not label(f):
        x0, y0, x1, y1 = f["box"]
        inner = [n for n in ns if label(n) and n["box"][0] >= x0 and n["box"][1] >= y0 and n["box"][2] <= x1 and n["box"][3] <= y1]
        if inner:
            f = dict(f, text=label(inner[0]))
    return f


def focus_until(target, step, limit=14):
    """Drückt step, bis das fokussierte Element target enthält; False, wenn es nach limit Tasten nicht erreicht ist."""
    for _ in range(limit):
        f = focused()
        if f and target in label(f):
            return True
        key(step, 0.35)
    f = focused()
    return bool(f and target in label(f))


def write_backup(values):
    with tempfile.NamedTemporaryFile("w", suffix=".json", delete=False, encoding="utf-8") as t:
        json.dump({"config": values}, t)
    adb("push", t.name, BACKUP_PATH)
    os.unlink(t.name)


def home():
    key("KEYCODE_WAKEUP", 0.5)
    key("HOME", 3.0)


def open_settings():
    """Öffnet die Einstellungen über die Leiste; sie liegt je nach Dock-Position oben oder unten, also erst nach oben, dann nach unten suchen."""
    home()
    for direction in ("DPAD_UP", "DPAD_DOWN"):
        key(direction, 0.8)
        if focus_until("Einstellungen", "DPAD_RIGHT", 4):
            key("DPAD_CENTER", 1.8)
            return True
    return False


def open_backup_page():
    """Öffnet Einstellungen > Sicherung. False, wenn ein Schritt nicht erreichbar war."""
    if not open_settings():
        return False
    for _ in range(12):
        key("DPAD_UP", 0.15)
    if not focus_until("Sicherung", "DPAD_DOWN", 14):
        return False
    key("DPAD_RIGHT", 0.8)
    return True


def load_backup_via_ui():
    """Lädt die Sicherungsdatei über Sicherung laden > Laden."""
    if not open_backup_page() or not focus_until("Sicherung laden", "DPAD_DOWN", 4):
        return False
    key("DPAD_CENTER", 1.0)
    if not focus_until("Laden", "DPAD_RIGHT", 3):
        return False
    key("DPAD_CENTER", 1.5)
    key("HOME", 3.0)
    return True


BAR_LABELS = ("Apps", "Suche", "Einstellungen")


def to_bar():
    """Von der Startseite in die Leiste: Hoch drücken, bis der Fokus auf einer ihrer Tasten liegt (höchstens dreimal; direkt nach HOME kommt die erste Taste manchmal zu früh)."""
    for _ in range(3):
        key("DPAD_UP", 1.0)
        f = focused()
        if f and label(f) in BAR_LABELS:
            return True
    return False


def apply(values):
    """Lädt die Werte (nur der Teil "config") über die Sicherungsseite. Gibt False zurück, wenn ein Schritt nicht erreichbar war."""
    write_backup(values)
    return load_backup_via_ui()


def save_user_state():
    """Sichert die aktuellen Einstellungen des Nutzers über Sicherung speichern und gibt den Text der Datei zurück (None, wenn das nicht klappt)."""
    if not open_backup_page() or not focus_until("Sicherung speichern", "DPAD_DOWN", 4):
        return None
    key("DPAD_CENTER", 1.5)
    text = adb("shell", "cat", BACKUP_PATH, check=False)
    key("HOME", 3.0)
    return text if text.strip().startswith("{") else None


def restore_user_state(text):
    """Stellt die mit save_user_state gesicherten Einstellungen wieder her."""
    with tempfile.NamedTemporaryFile("w", suffix=".json", delete=False, encoding="utf-8") as t:
        t.write(text)
    adb("push", t.name, BACKUP_PATH)
    os.unlink(t.name)
    return load_backup_via_ui()


def check_nodes(ns, margin_dp, tag):
    m = int(margin_dp * DENSITY)
    problems = []
    for n in ns:
        x0, y0, x1, y1 = n["box"]
        if x1 - x0 >= W - 4 and y1 - y0 >= H - 4:
            continue   # Vollbild-Ebene
        if n.get("package") != "app.calmtv.home":
            continue
        visible = label(n) or n.get("focusable") == "true"
        if not visible:
            continue
        if x0 < m - TOL or y0 < m - TOL or x1 > W - m + TOL or y1 > H - m + TOL:
            problems.append("%s: '%s' liegt im Rand: %s (Rand %d px)" % (tag, label(n)[:30], n["box"], m))
        if label(n).endswith("…") or label(n).endswith("..."):
            problems.append("%s: Text abgeschnitten: '%s'" % (tag, label(n)))
    return problems


def check_dock(ns, tag):
    glass = [n for n in ns if n.get("focusable") == "true" and not label(n) and n["box"][2] - n["box"][0] > 500]
    if not glass:
        return []
    g = max(glass, key=lambda n: n["box"][2] - n["box"][0])["box"]
    tiles = [n for n in ns if n.get("focusable") == "true" and label(n) and g[1] <= n["box"][1] and n["box"][3] <= g[3] + 2 * TOL]
    if not tiles:
        return []
    problems = []
    if abs(g[0] - (W - g[2])) > 2 * TOL + 2:
        problems.append("%s: Dock-Glas nicht mittig (links %d, rechts %d)" % (tag, g[0], W - g[2]))
    for t in tiles:
        if t["box"][0] < g[0] - TOL or t["box"][2] > g[2] + TOL:
            problems.append("%s: Kachel '%s' ragt aus dem Glas" % (tag, label(t)))
    inside = [t for t in tiles if t["box"][0] >= g[0] - TOL and t["box"][2] <= g[2] + TOL]
    if inside and len(inside) == len(tiles):   # nichts verdeckt: links und rechts gleich viel Glas um die Kacheln (sonst ist das Glas zu breit)
        left = min(t["box"][0] for t in inside) - g[0]
        right = g[2] - max(t["box"][2] for t in inside)
        if abs(left - right) > 4 * TOL:
            problems.append("%s: Kacheln nicht mittig im Glas (links %d, rechts %d)" % (tag, left, right))
    return problems


def shot(path):
    with open(path, "wb") as f:
        f.write(subprocess.run(["adb", "exec-out", "screencap", "-p"], capture_output=True).stdout)


def run_case(values, out, name):
    problems = []
    if not apply(values):
        return ["%s: Werte ließen sich nicht laden (Navigation)" % name]
    ns = nodes()
    shot(os.path.join(out, name + "_home.png"))
    margin = values["safeMargin"]
    problems += check_nodes(ns, margin, name + " Startseite")
    problems += check_dock(ns, name + " Startseite")
    if open_settings():
        ns = nodes()
        shot(os.path.join(out, name + "_einst.png"))
        problems += check_nodes(ns, margin, name + " Einstellungen")
        key("BACK", 1.0)
    else:
        problems.append("%s: Einstellungen nicht erreichbar" % name)
    home()
    to_bar()   # Fokus auf die Leiste, dann öffnet die Menütaste das Kontrollzentrum
    key("MENU", 1.8)
    ns = nodes()
    if any("Kontrollzentrum" in label(n) for n in ns):
        shot(os.path.join(out, name + "_kz.png"))
        problems += check_nodes(ns, margin, name + " Kontrollzentrum")
        key("BACK", 1.0)
    else:
        problems.append("%s: Kontrollzentrum nicht erreichbar" % name)
    home()
    return problems


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    quick = "--quick" in sys.argv
    out = args[0] if args else tempfile.mkdtemp(prefix="matrix-")
    os.makedirs(out, exist_ok=True)
    if not os.environ.get("ANDROID_SERIAL"):
        sys.exit("ANDROID_SERIAL=<IP>:5555 setzen")
    chosen = [a for a in sys.argv[1:] if a.startswith("--cases=")]
    if chosen:   # --cases=48:130:1,0:100:1 prüft nur diese Fälle (Rand:Schrift:Dock)
        cases = [tuple(int(v) for v in part.split(":")) for part in chosen[0].split("=", 1)[1].split(",")]
    elif quick:
        cases = [(0, 100, 1), (24, 100, 2), (48, 130, 1), (48, 90, 0), (0, 130, 2)]
    else:
        cases = list(itertools.product((0, 24, 48), (90, 100, 130), (0, 1, 2)))
    failed = []
    saved = save_user_state()   # die Einstellungen des Nutzers, damit der Test sie nicht überschreibt
    print("Einstellungen gesichert" if saved else "Hinweis: Einstellungen konnten nicht gesichert werden, am Ende gilt der Standard")
    for margin, scale, dock in cases:
        name = "r%d_s%d_d%d" % (margin, scale, dock)
        values = dict(DEFAULT, safeMargin=margin, textScale=scale, dockSize=dock)
        p = run_case(values, out, name)
        print("%-14s %s" % (name, "OK" if not p else "FEHLER"))
        for line in p:
            print("   ", line)
        failed += p
    if not (restore_user_state(saved) if saved else apply(DEFAULT)):
        failed.append("Die Einstellungen ließen sich nicht wiederherstellen, bitte am Stick prüfen")
    print("Screenshots:", out)
    print("%d Fälle, %d Probleme" % (len(cases), len(failed)))
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
