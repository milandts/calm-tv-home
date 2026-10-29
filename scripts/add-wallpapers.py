"""Fügt die zugeschnittenen Hintergrundbilder und ihre Bildnachweise in den Launcher ein.

Aufruf: python3 -I scripts/add-wallpapers.py <Launcher-Ordner> <Ordner mit zugeschnittenen Bildern>

Die Namen, Urheber und Lizenzen stehen in wallpapers.json. credits.txt des Launchers bekommt pro Bild
eine Zeile "Datei<TAB>Ort · Photo: Urheber · Lizenz".
"""
import json
import shutil
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
launcher, images = Path(sys.argv[1]), Path(sys.argv[2])
manifest = json.loads((ROOT / "wallpapers.json").read_text(encoding="utf-8"))
target = launcher / "assets" / "wallpapers"

credits_file = target / "credits.txt"
credits = credits_file.read_text(encoding="utf-8") if credits_file.exists() else ""
if not credits.endswith("\n"):
    credits += "\n"
for entry in manifest:
    stem = entry["file"].rsplit(".", 1)[0]
    found = sorted(images.glob(stem + ".*"))   # je nach Werkzeug .webp oder .jpg
    if not found:
        sys.exit(f"Kein zugeschnittenes Bild für {entry['file']} in {images}")
    name = found[0].name
    destination = target / name
    if destination.exists():
        sys.exit(f"{destination.name} existiert schon, Launcher-Ordner nicht frisch?")
    shutil.copyfile(found[0], destination)
    credits += f'{name}\t{entry["place"]} · Photo: {entry["photographer"]} · {entry["license"]}\n'
credits_file.write_text(credits, encoding="utf-8")
print(len(manifest), "Bilder und Nachweise hinzugefügt")
