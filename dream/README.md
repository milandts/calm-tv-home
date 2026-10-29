# Bildschirmschoner (dream/)

Eigene App `app.calmtv.home.dream`: System-Bildschirmschoner (Android "Daydream") für TV Home. Naturfotos mit Kamerafahrt, Uhr, Datum und Wetter.
Nur diese App hat die Berechtigung `INTERNET`, und nur für Open-Meteo (Wetter und Stadtsuche).

## Voraussetzungen

Android SDK mit Build-Tools 33 und Plattform android-33. Fotos und Schrift kommen aus dem Launcher-Bau (`scripts/setup.sh` einmal ausführen).
Keine anonymen Klassen in Feld-Initialisierern (D8-Fehler in Build-Tools 33). Auch eine private innere View-Klasse (`SetupRow$SwitchView`) ließ D8 abstürzen; sie steht deshalb als eigene Datei `SetupSwitch`.

## Bauen und prüfen

```bash
scripts/check-build.sh dream     # javac + d8, ohne APK
scripts/build-dream.sh           # out/TVHomeDream.apk
```

## Einstellungen und Intent-Extras

| Name | Wo | Bedeutung |
|---|---|---|
| `accent` (int, ARGB) | `SetupActivity` | Akzentfarbe des Launchers für Fokusflächen und Schalter; fehlt es, Weiß (0xFFFFFFFF). Schrift auf der Fokusfläche dunkel (0xFF1C1C1E) bei Helligkeit über 140, sonst weiß |
| `night` (boolean) | `PreviewActivity` | zeigt den Nachtmodus (abgedunkelt, Mond im Wettersymbol) auch tagsüber, falls er eingeschaltet ist |

SharedPreferences `ambient` (nur Schalter und Auswahlen, ohne Eintrag gilt der Standard = Verhalten wie vorher):
Schalter (Standard an) `weather` (aus = keine Netzwerkzugriffe), `motion`, `night`, `show_place`, `show_range`.
Auswahlen (int): `photo_seconds` (10/15/24/40/60), `motion_level` (0 sanft, 1 normal, 2 kräftig), `unit` (0 °C, 1 °F), `time_format` (0 System, 1 24 h, 2 12 h),
`clock_size` (0 bis 2), `clock_style` (0 dünn, 1 kräftig), `date_format` (0 lang, 1 kurz), `corner` (-1 wechselt pro Foto, sonst 0 unten links, 1 oben rechts, 2 unten rechts, 3 oben links),
`night_from` (20 bis 1), `night_to` (4 bis 9), `night_brightness` (30/45/60 Prozent). "Alles zurücksetzen" löscht diese Datei, die Stadt (`weather`) bleibt.
Die gewählte Stadt liegt wie bisher in `weather` (Name und auf zwei Stellen gerundete Koordinaten, nur auf dem Stick).

## Verhalten

- Nachtmodus 23 bis 6 Uhr: Bildschirm auf ca. 45 % abgedunkelt (weicher Übergang über 20 s), Kamerafahrt und Fotowechsel halb so schnell.
- Zusatzangaben (Einstellung "Zusatzangaben", Standard Keine): Regenwahrscheinlichkeit des Tages und aktueller Wind in km/h, in der Zeile mit Ort und Spanne; kommen aus demselben Open-Meteo-Abruf.
- Wettersymbol: Mond statt Sonne ab 20 bis 6 Uhr bei "Klar", "Überwiegend klar", "Teils bewölkt".
- Offline: kein Fehlertext. Das zuletzt geholte Wetter bleibt bis zu 4 Stunden stehen, dann verschwindet die Anzeige; Wiederholung nach 30 s, 2 min, dann alle 5 min.

## Dateien

`AmbientView` (Bühne), `InfoPanel` (Uhr, Datum, Wetter), `WeatherIcon` (Symbole im Code gezeichnet), `CornerScrim`, `FlatView`, `Fonts`, `AmbientSettings`,
`Weather` (Open-Meteo), `SetupActivity` mit `SetupStyle`, `SetupRow`, `SetupSwitch`, `PreviewActivity`, `AmbientDream`.
