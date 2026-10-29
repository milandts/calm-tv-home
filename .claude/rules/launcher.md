---
paths:
  - "launcher/**"
  - "docs/DESIGN.md"
---

# Bereich: Launcher

Pfadgebundene Regel: lädt, sobald eine Datei im Launcher gelesen oder bearbeitet wird. Bei Widerspruch zum Code gilt der Code. Bereichsübergreifende Gotchas stehen in `CLAUDE.md`.

## Invarianten
- Kein `INTERNET`. Wetter nur über den signaturgeschützten Provider des Bildschirmschoners (`HomeWeather`), mit Paket- und Signaturprüfung vor jeder Abfrage.
- Maße nur über `Ui.px`, `Metrics` und `HomeGeometry`; die Startseite liegt im Sicherheitsrand (`Opts.SAFE_MARGIN`), Vollbild-Bilder nehmen `Metrics.screenWidth`.
- Jede Einstellung ist eine Zeile in `Opts` (Schlüssel, Standard, Bereich) plus eine Zeile in `SettingsPages*`; die Sicherung nimmt Opts-Schlüssel automatisch auf.

## Gotchas
- `HomeBackdrop.coverImage` ist `static volatile`, weil `HomeKeyService` (anderer Thread, Bedienungshilfe) es liest, während Amazons Startseite abgedeckt wird.
- `HomeWeather` fragt den Provider im Hintergrund und höchstens alle 5 Minuten: Die Abfrage startet den Prozess des Bildschirmschoners (1 GB RAM). Nie auf dem Hauptthread, kein ContentObserver (wirft ab API 26 bei fehlendem Provider).
- `Tile` hebt Bild-Kacheln per `translationZ`, Text-Kacheln nicht (der Schatten einer halbdurchsichtigen Fläche scheint durch).
- `Glass` und `GlassText` zeichnen die unscharfe Foto-Kopie deckungsgleich zum Bildschirm (`getLocationInWindow`); neue Glas-Flächen müssen in `HomeScene.glass`, damit sie beim Bildwechsel das neue Foto bekommen.
- Vorschau-Seiten (`.preview()`) zeigen keine Links und Überschriften (siehe `scripts/check-rules.sh`); für zusätzliche Eingaben einen Knopf (`Rows.button`) nehmen, er erscheint neben dem Titel.
- Nach "Sicherung laden" ruft `restoreBackup` `Config.reloaded()`: Der Glasstil hält Werte im Speicher und liest sonst die alten.

## Entscheidungen
- Dock-Kacheln tragen den Paketnamen als Tag, Raster-Kacheln ihren Index (`HomeDock.isDockTile`, `AppGrid.focusedIndex`); der Fokuswechsel zwischen beiden stützt sich darauf. Siehe `docs/entscheidungen.md`.
