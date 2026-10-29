# Architektur

## Das Problem
Amazons Startseite auf dem Fire TV Stick zeigt Werbung und lässt sich nicht ersetzen. TV Home ist ein eigener, werbefreier Startbildschirm,
den die HOME-Taste öffnet, plus ein eigener Bildschirmschoner. Beides soll ohne Konto, ohne Tracker und ohne Internetzugriff im Launcher laufen.

## Codemap
    launcher/               Launcher-App (Paket app.calmtv.home)
    ├── AndroidManifest.xml HomeActivity, HOME-Dienst, Einschlaf-Alarm; keine Internetberechtigung
    ├── res/                Symbol, Banner, Theme, Dienstbeschreibung
    ├── assets/fonts/       Nunito (OFL); die Fotos lädt scripts/setup.sh nach assets/wallpapers (nicht versioniert)
    └── src/app/calmtv/home/
        ├── HomeActivity    Lebenszyklus, HOME-Taste, Verdrahtung; HomeHost für die Vollbild-Ansichten
        ├── Home*, App*, Dock*  Bausteine der Startseite (Tabelle in docs/DESIGN.md)
        ├── Glass*, Pill, Tile  Glas-Bausteine mit unscharfer Foto-Kopie
        ├── Settings*       Einstellungen: Modell, Seiten, Zeilen, Vorschau, Suche
        ├── Config, Opts    zentrale Einstellungen (jede mit Schlüssel, Standard, Bereich)
        └── Wallpaper*      Bildquelle, Aufbereitung, Wahl nach Rhythmus
    dream/                  Bildschirmschoner-App (Paket app.calmtv.home.dream), einzige App mit INTERNET
    scripts/                Bau, Installation, Prüfungen und Gerätetests
    tools/                  Messskripte (Speicher, HOME-Zeit) und der Foto-Zuschnitt (CropWallpapers.java)
    docs/                   DESIGN (Regeln), NOTES (Messwerte, Fehlversuche), Entscheidungen, Betriebslog
    wallpapers.json         Quellen und Lizenz je Foto (Wikimedia Commons)
    .claude/rules/          Pfadgebundene Bereichsregeln für die KI

## Invarianten
- Der Launcher hat keine Internetberechtigung. (Beleg: `launcher/AndroidManifest.xml`; Einstellungen > Datenschutz liest die Berechtigung zur Laufzeit, `SettingsPagesPrivacy.internetAccess`)
- Der Launcher liest das Wetter nur über `WeatherProvider` des Bildschirmschoners; der Zugriff verlangt die Signatur-Permission `app.calmtv.home.permission.READ_WEATHER`, und `HomeWeather.isTrustedProvider` prüft Paketname und Signatur. Der Provider gibt keinen Ort heraus. (Beleg: beide Manifeste, `WeatherProvider.query`)
- Aus dem Bildschirmschoner geht Netzverkehr nur durch `Weather.getJson`: HTTPS, zwei feste Hosts, keine Weiterleitungen. (Beleg: `Weather.getJson`, `usesCleartextTraffic="false"` im Manifest)
- Die Bildschirmgröße wird nur in `Ui` und `Metrics` gelesen; alles andere nutzt `usableWidth` und `usableHeight` (ziehen den Sicherheitsrand ab). (Beleg: `scripts/check-rules.sh` Regel 1)
- Seiten mit `.preview()` enthalten keine Links und Überschriften. (Beleg: `scripts/check-rules.sh` Regel 2)
- Der Quelltext enthält keine anonymen Klassen. (Beleg: `scripts/check-build.sh` übersetzt mit d8, das daran scheitert)
- Schlüssel, Kennwörter, Screenshots und Logs sind nicht versioniert. (Beleg: `.gitignore`)

## Grenzen
- Launcher und Bildschirmschoner sind zwei APKs mit demselben Schlüssel; nur so greift die Signatur-Permission. Der Bildschirmschoner kennt den Launcher nicht.
- Vollbild-Ansichten (`Screen`) sprechen nur über `HomeHost` mit der Startseite; Menüs, Eingaben und Meldungen liegen in einer eigenen Ebene darüber (`modalLayer`).
- HOME-Taste: Fire OS gibt HOME nur an Amazons Launcher; `HomeKeyService` (Bedienungshilfe) erkennt dessen Startseite und holt TV Home per Alarm nach vorn.

## Querschnittsthemen
- Einstellungen: Alle Werte sind ganze Zahlen mit Schlüssel in `Opts`; `Config` hält sie, eine Probeschicht erlaubt Vorschau mit "Änderungen behalten?". Die Sicherung (`Backup`) nimmt nur Schlüssel aus einer Liste auf.
- Fehler: Netz- und Dateifehler werden an den Rändern gefangen, geloggt und als kurze Meldung gezeigt; im Innern laufen Fehler durch.
- Leistung: 1 GB RAM und ein GPU-Kern. Das Glas zeichnet eine kleine, vorab unscharf gerechnete Foto-Kopie statt live zu weichzeichnen; große Bilder gibt der Launcher frei, solange eine andere App vorn ist.

## Schnelltest
Wo ändere ich die Startseite? Der passende Baustein in `docs/DESIGN.md`. Wo eine Einstellung? Zeile in `Opts`, Seite in `SettingsPages*`, Wirkung dort, wo sie gelesen wird.
Wie teste ich? `scripts/check-build.sh launcher`, dann am Stick `scripts/snapshot-ui.py` und `scripts/test-matrix.py --quick`. Was nie? Netz im Launcher, anonyme Klassen, blindes Tastensenden.
