---
paths:
  - "scripts/**"
  - "tools/**"
  - "wallpapers.json"
---

# Bereich: Skripte

Pfadgebundene Regel: lädt, sobald ein Skript gelesen oder bearbeitet wird. Bei Widerspruch zum Code gilt der Code.

## Gotchas
- Gerätetests stützen sich auf `uiautomator dump`, das gelegentlich scheitert und den HOME-Dienst stören kann; `test-matrix.nodes` wiederholt, nach Unregelmäßigkeiten `scripts/rebind.sh`.
- Direkt nach HOME kommt die erste Richtungstaste manchmal zu früh an: Wege in die Leiste immer mit Fokusprüfung und Wiederholung (`to_bar`).
- Jedes Testskript, das Einstellungen ändert, sichert sie vorher (`save_user_state`) und stellt sie wieder her; nie nur auf "Standard" zurücksetzen.
- Der Bildwechsel-Test verschiebt per `cmd alarm set-timezone` die Zeit (die Systemuhr lässt sich nicht stellen) und muss `Europe/Berlin` danach zurücksetzen, auch bei Abbruch.
- Auf Python 3.8 laufen alle Skripte (macOS-Standard); keine neueren Sprachmittel.
- Fotos: `setup.sh` schneidet sie mit `tools/CropWallpapers.java` (nur JDK nötig) auf 16:9 zu und macht daraus WebP (Qualität 90, wenn `cwebp` da ist), sonst JPEG 0,82. Launcher (`WallpaperSource`) und Bildschirmschoner (`AmbientView`, `BlurredBackdrop`) nehmen beide Endungen. Die Dateinamen in `credits.txt` müssen zu den Dateien in `assets/` passen (`add-wallpapers.py` schreibt sie mit der tatsächlichen Endung).
- Fotos kommen von Wikimedia Commons und werden nie eingecheckt; Urheber und Lizenz je Foto stehen in `wallpapers.json` und werden in `credits.txt` der APK geschrieben.
