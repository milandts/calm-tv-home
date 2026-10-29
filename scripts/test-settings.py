#!/usr/bin/env python3
"""Prüft am Stick, dass jede im Quelltext deklarierte Zeile einer Einstellungsseite auch zu sehen ist.

Aufruf: ANDROID_SERIAL=<IP>:5555 scripts/test-settings.py [Seiten-Kennung ...]     (ohne Angabe: alle Seiten mit festen Zeilen)

Liest aus launcher/src/**/SettingsPages*.java je Seite (Kennung, Titel) die Zeilen, die dort mit Rows.opt, choice, toggle, button, link
oder info angelegt werden, öffnet die Seite über die Seitenleiste und die Link-Zeilen und rollt sie mit Runter durch. Fehlt der Titel
einer Zeile in allem, was dabei sichtbar war, ist das ein Fehler: genau die Fehlerklasse, bei der eine Zeile im Code stand, aber nicht erschien.
Zeilen hinter einer Bedingung (davor steht eine if-Zeile) werden übersprungen, Listen aus dynamicRows ebenfalls.
Gedrückt werden nur Richtungs-, Zurück- und HOME-Taste und Auswahl auf den Titeln von Seiten (Links), nie auf Knöpfen.
"""
import html
import importlib.util
import os
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
spec = importlib.util.spec_from_file_location("tm", ROOT / "scripts" / "test-matrix.py")
tm = importlib.util.module_from_spec(spec)
spec.loader.exec_module(tm)

PAGE = re.compile(r'new SettingsModel\.Page\("([^"]+)",\s*"([^"]+)"\)')
ROW = re.compile(r'Rows\.(opt|choice|toggle|button|link|info)\(\s*"([^"]+)"')
CATEGORY_TITLES = {}   # Kennung der obersten Ebene -> Titel (aus den Seiten selbst)


def parse():
    """Seiten mit Titel und den festen, unbedingten Zeilen."""
    pages = {}
    for source in sorted((ROOT / "launcher" / "src").rglob("SettingsPages*.java")):
        text = source.read_text(encoding="utf-8")
        for match in PAGE.finditer(text):
            end = text.find("return page;", match.end())
            body = text[match.end():end if end > 0 else len(text)]
            rows = []
            lines = body.split("\n")
            for number, line in enumerate(lines):
                found = ROW.search(line)
                previous = lines[number - 1].strip() if number else ""
                if found and not previous.startswith("if (") and "dynamicRows" not in body:
                    rows.append((found.group(1), found.group(2)))
            pages[match.group(1)] = {"title": match.group(2), "rows": rows, "dynamic": "dynamicRows" in body}
    return pages


# Zeilen, die absichtlich keinen eigenen Titel zeigen (Farbfelder tragen ihre Namen selbst)
NOT_SHOWN = {("darstellung/akzent", "Farbe")}


def visible_labels():
    return {html.unescape(tm.label(n)) for n in tm.nodes() if tm.label(n) and n.get("package") == "app.calmtv.home"}


def scroll_collect():
    """Alle Beschriftungen, die beim Durchrollen der offenen Seite sichtbar werden."""
    seen = set(visible_labels())
    for direction in ("DPAD_UP", "DPAD_DOWN"):   # erst nach oben (die Seite öffnet nicht immer ganz oben), dann nach unten
        last, repeats = None, 0
        for _ in range(40):
            tm.key(direction, 0.6)
            f = tm.focused()
            shown = visible_labels()
            # Bei Reglern bleibt der Fokus an derselben Stelle, während der Inhalt darunter rollt: darum zählt auch, was sichtbar ist
            marker = (html.unescape(tm.label(f)) if f else "", tuple(f["box"]) if f else (), frozenset(shown))
            seen |= shown
            repeats = repeats + 1 if marker == last else 0
            if repeats >= 2:
                break
            last = marker
    return seen


def focus_title(title, limit=14):
    """Fokus auf das Element mit diesem Titel (die Auslese schreibt "&" als "&amp;")."""
    for _ in range(limit):
        f = tm.focused()
        if f and html.unescape(tm.label(f)) == html.unescape(title):
            return True
        tm.key("DPAD_DOWN", 0.35)
    f = tm.focused()
    return bool(f and html.unescape(tm.label(f)) == html.unescape(title))


def open_page(page_id, pages):
    """Öffnet die Seite über die Seitenleiste und die Link-Zeilen; False, wenn ein Schritt nicht erreichbar war."""
    parts = page_id.split("/")
    chain = ["/".join(parts[:i + 1]) for i in range(len(parts))]
    if not tm.open_settings():
        return False
    for _ in range(12):
        tm.key("DPAD_UP", 0.15)
    if not focus_title(pages[chain[0]]["title"]):
        return False
    for step in chain[1:]:
        tm.key("DPAD_RIGHT", 0.8)
        for _ in range(12):
            tm.key("DPAD_UP", 0.1)
        if not focus_title(pages[step]["title"]):
            return False
        tm.key("DPAD_CENTER", 1.4)
    if len(chain) == 1:
        tm.key("DPAD_RIGHT", 0.8)
    return True


def main():
    if not os.environ.get("ANDROID_SERIAL"):
        sys.exit("ANDROID_SERIAL=<IP>:5555 setzen")
    pages = parse()
    wanted = sys.argv[1:] or [pid for pid, p in pages.items() if p["rows"] and not p["dynamic"] and all(
        "/".join(pid.split("/")[:i + 1]) in pages for i in range(len(pid.split("/"))))]
    problems = 0
    for page_id in wanted:
        page = pages[page_id]
        if not open_page(page_id, pages):
            print("%-34s NAVIGATION FEHLER" % page_id)
            problems += 1
            continue
        seen = scroll_collect()
        missing = [title for kind, title in page["rows"]
                   if (page_id, title) not in NOT_SHOWN and title not in seen and not any(title in s for s in seen)]
        print("%-34s %s (%d Zeilen)%s" % (page_id, "OK" if not missing else "FEHLT", len(page["rows"]),
                                         "" if not missing else ": " + ", ".join(missing)))
        problems += bool(missing)
    tm.home()
    print("%d Seiten, %d mit Problemen" % (len(wanted), problems))
    sys.exit(1 if problems else 0)


if __name__ == "__main__":
    main()
