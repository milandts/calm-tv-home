#!/usr/bin/env python3
"""Prüft am Stick das Dock mit mehr Apps, als nebeneinander passen (Rollen, Mehr-Pfeile an den Rändern, Sicherheitsrand).

Aufruf: ANDROID_SERIAL=<IP>:5555 scripts/test-dock.py [Ausgabeordner] [--apps=paket1,paket2,...]
Setzt das Dock auf die genannten Apps (es müssen installierte Apps sein; Standard ist eine Liste, wie sie auf dem Entwicklungs-Stick liegt),
prüft je Fall, dass nichts im Sicherheitsrand liegt und der Fokus am Ende auf der letzten App steht, und speichert Bilder vom Anfang
und vom Ende des Docks (links und rechts jeweils der Mehr-Pfeil). Die Einstellungen des Nutzers werden vorher gesichert und danach wiederhergestellt.
Gedrückt werden nur Richtungstasten und HOME.
"""
import importlib.util
import json
import os
import sys
import tempfile

spec = importlib.util.spec_from_file_location("tm", os.path.join(os.path.dirname(os.path.abspath(__file__)), "test-matrix.py"))
tm = importlib.util.module_from_spec(spec)
spec.loader.exec_module(tm)

DEFAULT_APPS = ("com.netflix.ninja,com.amazon.firetv.youtube,com.amazon.firebat,com.spotify.tv.android,com.formulaone.production,com.dazn,"
                "com.apple.atve.amazon.appletv,com.disney.disneyplus,com.crunchyroll.crunchyroid,com.zdf.android.mediathek")
CASES = [("klein_unten_s130", dict(dockSize=0, dockPos=0, textScale=130, safeMargin=48)),
         ("normal_unten", dict(dockSize=1, dockPos=0, textScale=100, safeMargin=0)),
         ("gross_oben_s130", dict(dockSize=2, dockPos=1, textScale=130, safeMargin=48))]


def load(dock, config):
    with tempfile.NamedTemporaryFile("w", suffix=".json", delete=False, encoding="utf-8") as t:
        json.dump({"home": {"dock": dock}, "config": config}, t)
    tm.adb("push", t.name, tm.BACKUP_PATH)
    os.unlink(t.name)
    return tm.load_backup_via_ui()


def main():
    out = next((a for a in sys.argv[1:] if not a.startswith("--")), tempfile.mkdtemp(prefix="dock-"))
    apps = next((a.split("=", 1)[1] for a in sys.argv[1:] if a.startswith("--apps=")), DEFAULT_APPS)
    os.makedirs(out, exist_ok=True)
    if not os.environ.get("ANDROID_SERIAL"):
        sys.exit("ANDROID_SERIAL=<IP>:5555 setzen")
    count = len(apps.split(","))
    saved = tm.save_user_state()
    print("Einstellungen gesichert" if saved else "Hinweis: Einstellungen konnten nicht gesichert werden")
    problems = []
    for name, over in CASES:
        config = dict(tm.DEFAULT, **over)
        if not load(apps, config):
            problems.append("%s: Dock ließ sich nicht laden" % name)
            continue
        tm.shot(os.path.join(out, name + "_anfang.png"))
        for _ in range(count - 1):
            tm.key("DPAD_RIGHT", 0.45)
        tm.shot(os.path.join(out, name + "_ende.png"))
        found = tm.check_nodes(tm.nodes(), config["safeMargin"], name)
        focused = tm.focused()
        print("%-18s Fokus am Ende: %s  Probleme: %s" % (name, focused and tm.label(focused), found or "keine"))
        problems += found
    restored = tm.restore_user_state(saved) if saved else False
    print("Einstellungen wiederhergestellt" if restored else "Einstellungen NICHT wiederhergestellt, bitte am Stick prüfen")
    print("Bilder:", out)
    sys.exit(1 if problems or not restored else 0)


if __name__ == "__main__":
    main()
