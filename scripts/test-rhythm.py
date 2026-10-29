#!/usr/bin/env python3
"""Prüft am Stick, ob das Hintergrundbild nach der Einstellung "Bildwechsel" wechselt (Nie, Bei jedem Heimkommen, Täglich, Alle 6 Stunden).

Aufruf: ANDROID_SERIAL=<IP>:5555 scripts/test-rhythm.py
Erkennt einen Wechsel an einem Farbabdruck des Bildes (Mitte und rechts, ohne Uhr, Leiste und Dock). Die Startseite wird über die eigene
Vorschau des Bildschirmschoners verlassen und per HOME wieder betreten. Die Systemuhr lässt sich per ADB nicht stellen, darum wird für den
Tageswechsel nur die Zeitzone verschoben (cmd alarm set-timezone): vor 12 Uhr nach Pago Pago (11 Stunden zurück), sonst nach Kiritimati (13 Stunden vor),
so dass der Ortstag sicher wechselt. Die Zeitzone und die Einstellungen des Nutzers werden in jedem Fall wiederhergestellt.
Der echte 6-Stunden-Wechsel lässt sich so nicht auslösen (die Zeit läuft über die Gerätelaufzeit); geprüft wird nur, dass das Bild bei kurzer Rückkehr bleibt.
"""
import importlib.util
import os
import struct
import sys
import time
import zlib

spec = importlib.util.spec_from_file_location("tm", os.path.join(os.path.dirname(os.path.abspath(__file__)), "test-matrix.py"))
tm = importlib.util.module_from_spec(spec)
spec.loader.exec_module(tm)

POINTS = [(x, y) for x in range(700, 1900, 150) for y in range(300, 780, 120)]
CHANGED = 12   # mittlere Abweichung je Farbwert, ab der ein Bild als anders gilt
SCREENSHOT = "/tmp/tvhome-rhythm.png"


def load_png(path):
    """Liest ein PNG (RGB oder RGBA, 8 Bit) ohne Zusatzpakete; gibt Breite, Bytes je Punkt und die Zeilen zurück."""
    data = open(path, "rb").read()
    pos, idat = 8, b""
    while pos < len(data):
        length, kind = struct.unpack(">I4s", data[pos:pos + 8])
        chunk = data[pos + 8:pos + 8 + length]
        pos += 12 + length
        if kind == b"IHDR":
            width, height, _, color = struct.unpack(">IIBB", chunk[:10])
        elif kind == b"IDAT":
            idat += chunk
    bpp = {2: 3, 6: 4}[color]
    raw, stride, rows, previous, i = zlib.decompress(idat), width * bpp, [], bytearray(width * bpp), 0
    for _ in range(height):
        mode, line = raw[i], bytearray(raw[i + 1:i + 1 + width * bpp])
        i += 1 + stride
        for x in range(stride):
            a = line[x - bpp] if x >= bpp else 0
            b = previous[x]
            c = previous[x - bpp] if x >= bpp else 0
            if mode == 1:
                line[x] = (line[x] + a) & 255
            elif mode == 2:
                line[x] = (line[x] + b) & 255
            elif mode == 3:
                line[x] = (line[x] + ((a + b) >> 1)) & 255
            elif mode == 4:
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                line[x] = (line[x] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 255
        rows.append(line)
        previous = line
    return width, bpp, rows


def fingerprint():
    tm.shot(SCREENSHOT)
    _, bpp, rows = load_png(SCREENSHOT)
    return [tuple(rows[y][x * bpp:x * bpp + 3]) for x, y in POINTS]


def difference(a, b):
    return sum(sum(abs(p - q) for p, q in zip(u, v)) for u, v in zip(a, b)) / (len(a) * 3)


def set_zone(zone):
    tm.adb("shell", "cmd", "alarm", "set-timezone", zone)


def leave_and_return(wait):
    tm.adb("shell", "am", "start", "-n", "app.calmtv.home.dream/.PreviewActivity")
    time.sleep(wait)
    tm.key("HOME", 5.0)


def check(name, rhythm, steps, day_zone, home_zone, settle_day=False):
    tm.apply(dict(tm.DEFAULT, bgRhythm=rhythm))
    time.sleep(4)
    if settle_day:   # erst auf das Foto des heutigen Tages bringen (ein früher gewürfeltes Foto könnte zufällig das des Nachbartags sein)
        set_zone(day_zone)
        leave_and_return(5)
        set_zone(home_zone)
        leave_and_return(5)
    base = fingerprint()
    problems = 0
    print(name)
    for label, shifts_day, expect_change in steps:
        if shifts_day:
            set_zone(day_zone)
        leave_and_return(5)
        current = fingerprint()
        delta = difference(base, current)
        changed = delta > CHANGED
        ok = changed == expect_change
        problems += not ok
        print("  %s: Δ=%.1f %s (erwartet %s) %s" % (label, delta, "geändert" if changed else "gleich",
                                                     "geändert" if expect_change else "gleich", "OK" if ok else "FEHLER"))
        if shifts_day:
            set_zone(home_zone)
        base = current if changed else base
    sys.stdout.flush()
    return problems


def main():
    if not os.environ.get("ANDROID_SERIAL"):
        sys.exit("ANDROID_SERIAL=<IP>:5555 setzen")
    home_zone = tm.adb("shell", "getprop", "persist.sys.timezone").strip() or "Europe/Berlin"
    hour = int(tm.adb("shell", "date", "+%H").strip())
    day_zone = "Pacific/Kiritimati" if hour >= 12 else "Pacific/Pago_Pago"
    print("Zeitzone des Sticks: %s, Tageswechsel über %s" % (home_zone, day_zone))
    saved = tm.save_user_state()
    print("Einstellungen gesichert" if saved else "Hinweis: Einstellungen konnten nicht gesichert werden")
    problems = 0
    try:
        problems += check("Nie", 3, [("zurück nach 5 s", False, False), ("Tageswechsel, zurück", True, False)], day_zone, home_zone)
        problems += check("Bei jedem Heimkommen", 1, [("zurück 1", False, True), ("zurück 2", False, True)], day_zone, home_zone)
        problems += check("Täglich", 0, [("selber Tag", False, False), ("Tageswechsel", True, True)], day_zone, home_zone, settle_day=True)
        problems += check("Alle 6 Stunden", 2, [("zurück nach 5 s", False, False)], day_zone, home_zone)
    finally:
        set_zone(home_zone)
        restored = tm.restore_user_state(saved) if saved else tm.apply(tm.DEFAULT)
        print("Zeitzone %s, Einstellungen %s" % (home_zone, "wiederhergestellt" if restored else "NICHT wiederhergestellt, bitte am Stick prüfen"))
    print("%d Abweichungen" % problems)
    sys.exit(1 if problems else 0)


if __name__ == "__main__":
    main()
