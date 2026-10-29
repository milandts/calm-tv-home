"""Lädt die in wallpapers.json aufgeführten Fotos von Wikimedia Commons herunter.

Aufruf: python3 -I scripts/fetch-wallpapers.py <Ausgabeordner>

Jedes Bild wird in der Breite geladen, bei der nach dem Zuschnitt auf 16:9 mindestens 1920x1080
übrig bleiben. Die Dateien heißen wie in wallpapers.json ("10.jpg" ...). Abgerufen wird mit curl,
damit der Zertifikatsspeicher des Systems genutzt wird.
"""
import json
import subprocess
import sys
import time
import urllib.parse
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = Path(sys.argv[1])
OUT.mkdir(parents=True, exist_ok=True)
API = "https://commons.wikimedia.org/w/api.php"
USER_AGENT = "firestick-tvhome/1.0 (private project)"
RETRIES = 8   # Wikimedia setzt bei vielen Abrufen Verbindungen zurück; bei jedem Versuch länger warten


def get(url: str, timeout: int = 90) -> bytes:
    """Holt eine URL; Wikimedia setzt bei vielen Abrufen Verbindungen zurück, daher Wiederholungen."""
    for attempt in range(RETRIES):
        done = subprocess.run(["curl", "-sfL", "-A", USER_AGENT, "--max-time", str(timeout), url],
                              capture_output=True)
        if done.returncode == 0:
            return done.stdout
        time.sleep(5 * (attempt + 1))
    raise OSError(f"curl {done.returncode} für {url[:80]}")


def thumbnail_url(title: str, width: int) -> str:
    query = urllib.parse.urlencode({"action": "query", "titles": title, "prop": "imageinfo",
                                    "iiprop": "url", "iiurlwidth": width, "format": "json"})
    page = next(iter(json.loads(get(f"{API}?{query}", 60))["query"]["pages"].values()))
    return page["imageinfo"][0]["thumburl"]


for entry in json.loads((ROOT / "wallpapers.json").read_text(encoding="utf-8")):
    target = OUT / entry["file"]
    if target.exists() and target.stat().st_size > 50_000:
        print(entry["file"], "vorhanden")
        continue
    width = max(1920, round(1080 * entry["ratio"]) + 1)
    target.write_bytes(get(thumbnail_url(entry["title"], width)))
    print(entry["file"], entry["place"])
    time.sleep(3)
