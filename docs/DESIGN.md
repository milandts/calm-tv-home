# TV Home 2.0: Designdokument

Ziel: TV Home soll sich wie ein **eigenes Betriebssystem für den Fernseher** anfühlen: ruhig, hochwertig, durchgängig
gestaltet, ohne Brüche. Kein Dialog im Standard-Android-Look, keine Werbung, keine Überraschungen. Alles in Deutsch.

## Prinzipien

1. **Eine Gestaltungssprache.** "Liquid Glass" wie auf der Startseite: Hintergrundfoto scheint durch, abgerundete Flächen,
   weiche Schatten, Nunito als Schrift, Fokus = gefüllte Fläche in der Akzentfarbe (Weiß oder gewählte Farbe, Schrift passt sich an).
2. **Alles per Fernbedienung.** Pfeiltasten, Auswahl, Zurück, Menü. Nie ein Element, das nur mit Maus oder Touch geht. Fokus ist immer sichtbar
   und landet nach jedem Schließen wieder dort, wo man herkam.
3. **Zurück geht immer eine Ebene höher** und schließt zuletzt die Ansicht. Menü-Taste öffnet das Kontextmenü zu dem, was Fokus hat.
4. **Sehen, was man ändert.** Aussehen-Einstellungen zeigen die echte Startseite (Vorschau-Modus), nicht eine Liste.
5. **Leicht bleiben.** 1 GB RAM, ein GPU-Kern: keine `Glass`-Instanzen in Listen (nur Panels), keine großen Bitmaps zusätzlich,
   keine Animationen über 400 ms, nichts im Hintergrund, das nicht sein muss.
6. **Datenschutz ist ein Feature.** Der Launcher hat kein `INTERNET`. Es wird nichts gesammelt, nichts protokolliert, was die Nutzerin oder den Nutzer
   betrifft. Texte erklären offen, was wohin geht.
7. **Professioneller Ton.** Kurze, freundliche Sätze, Sie-Form vermeiden (das Gerät duzt), keine Ausrufezeichen, keine Emojis,
   Fachbegriffe nur, wenn sie hilfreich sind. Einheitliche Verben ("Öffnen", "Zurücksetzen", "Speichern").

## Ebenen im Fenster (HomeScene.root, von unten nach oben)

1. Hintergrundfoto (scharf oder unscharf), Abdunkeln
2. Startseite: Uhr, Leiste (Apps/Suche/Einstellungen), Dock, App-Raster
3. **Screen-Ebene** (`screenLayer`): genau eine Vollbild-Ansicht (`Screen`): Einstellungen, Kontrollzentrum, Willkommen, Ordner, Suche
4. **Modal-Ebene** (`modalLayer`): `GlassMenu`, `GlassPrompt`, `GlassToast`; liegt immer über allem und bekommt Zurück zuerst (`Ui.dismissTopModal`)

## Informationsarchitektur der Einstellungen

Linke Spalte: Kategorien mit Symbol. Rechts: Inhalt der gewählten Kategorie. Unterseiten öffnen sich rechts (Link-Zeilen), Zurück geht hoch.

| Kategorie | Inhalt |
|---|---|
| **Darstellung** | Link "Hintergrund" (Vorschau-Seite: Schärfe scharf/unscharf, Abdunkeln 0/20/35/50 %, "Nächstes Bild"); Link "Akzentfarbe" (Vorschau-Seite: sechs Farben); Info "Bildwechsel: täglich um Mitternacht" |
| **Apps & Dock** | Link "Ordner" (Liste, je Ordner Umbenennen/Auflösen); Link "Ausgeblendete Apps" (Liste, Antippen blendet ein); Toggle "Zuletzt benutzt anzeigen"; Button "Dock zurücksetzen" (mit Bestätigung) |
| **Bildschirmschoner** | Button "Jetzt starten"; Link/Button "Wetter einrichten"; Info "Startet nach X Minuten" (aus `screen_off_timeout`, nur lesen) + Hinweis, wo man es ändert; Info "Datenschutz: nur das Wetter geht ins Internet" |
| **Verbindungen & Geräte** | Netzwerk, Controller & Bluetooth (Amazon-Seiten), Speedtest (fast.com), Info "Filter (VPN)" aktiv/nicht |
| **System** | "Töne und Bildschirm", "Barrierefreiheit", "Konto" usw.: öffnet Amazons Übersicht ("Alle Einstellungen"); Anwendungen; Mein Fire TV |
| **Datenschutz** | Info-Seite: Filter-Status, "TV Home hat keine Internetberechtigung", Anzahl deaktivierter System-Apps, was der Bildschirmschoner sendet, was TV Home liest (App-Liste, Netzwerkstatus) |
| **Sicherung** | Speichern, Laden (mit Bestätigung), Hinweis zum Ablageort |
| **Info** | Version, Gerät, Fire-OS-Version, Speicher frei, Arbeitsspeicher, Laufzeit, "Einführung wiederholen", Bildnachweise, Lizenzen |

## Gestaltungs-Tokens (siehe `Ui.java`)

- Schrift Nunito (variabel): Titel 30 sp / 300, Seitenüberschrift 24 sp / 400, Zeilentitel 17 sp / 600, Untertitel 13 sp / 500 (gedämpft),
  Abschnittskopf 11 sp / 700 in Großbuchstaben mit Laufweite.
- Radien: Zeile 14 dp, Panel 24 dp, Chip 18 dp, Menü 20 dp.
- Zeile: mindestens 52 dp hoch, 16 dp Innenabstand, Symbol 22 dp links.
- Farben: Text 0xF2FFFFFF, gedämpft 0xB3FFFFFF, schwach 0x80FFFFFF, Zeile 0x1FFFFFFF, Panel dunkel 0x52000000 mit feiner Kante 0x26FFFFFF.
  Fokus: `Look.focusFill()` / `Look.focusText()` / `Look.focusRing()`.
- Bewegung: schnell 140 ms, mittel 220 ms, langsam 360 ms, `Ui.EASE` (Decelerate 1.8). Seitenwechsel: Einblenden von rechts (40 dp) + Alpha.
  Ansicht öffnen: Alpha + Skalierung 0.97 → 1. Menüs: Alpha + Skalierung 0.94 → 1.

## Zuständigkeiten (jede Datei hat genau einen Besitzer)

| Datei(en) | Zuständig |
|---|---|
| `Ui.java`, `Screen.java`, `HomeHost.java`, `SettingsModel.java` | Architekt (Vorgabe, nur nach Absprache ändern) |
| `GlassMenu`, `GlassPrompt`, `GlassToast`, `Switch`/`Chip`-Widgets (`GlassSwitch`, `Chip`), `Icons.java` | UI-Kit |
| `SettingsScreen.java` (+ Hilfsklassen mit Präfix `Settings`, z. B. `SettingsRowView`) | Einstellungs-Gerüst |
| `SettingsPages.java` (+ `SettingsSearch`) | Einstellungs-Seiten |
| `ControlCenter.java`, `Welcome.java` | Shell-Funktionen |
| `dream/**` | Bildschirmschoner |
| Startseite (Tabelle unten), `Tile.java`, `Glass.java`, `GlassText.java`, `Pill.java`, `Look.java`, `AppLayout.java`, `Backup.java` | Integrator (Architekt) |

### Die Startseite in Bausteinen

`HomeActivity` hält nur Lebenszyklus, HOME-Taste, Einstellungen und Verdrahtung (`HomeHost`). Das Verhalten liegt in kleinen Klassen, jede unter 350 Zeilen:

| Baustein | Aufgabe |
|---|---|
| `HomeScene` | Ansichtenbaum (nur Gerüst, kein Verhalten) |
| `HomeHeader`, `HomeWeather` | Gruß, Uhr, Datum, Wetterzeile, Filter-Anzeige |
| `HomeBar` | die drei Glas-Tasten oben, Fokuswege |
| `HomeDock`, `DockStore`, `DockScroll`, `DockEdgeHint` | Dock: Aufbau, Tasten, Speicher, Rolle, "Mehr"-Pfeile |
| `AppGrid`, `AppMenus` | App-Raster und die Menüs zu Apps und Ordnern |
| `HomeOverlay` | Suche und geöffneter Ordner |
| `HomeScreens` | Vollbild-Ansichten (Einstellungen, Kontrollzentrum, Willkommen) |
| `HomeBackdrop`, `Wallpapers` (`WallpaperSource`, `WallpaperRender`, `WallpaperPicker`) | Hintergrundbild: Quelle, Aufbereitung, Wahl nach Rhythmus |
| `AppCatalog`, `AppEntry`, `TileSizes` | Apps des Systems und Kachelmaße |
| `HomeKeyService`, `HomeLauncher`, `AmazonCover` | HOME-Taste auf Fire OS (Dienst, Start per Alarm, Abdeckung) |

Niemand außer dem Besitzer ändert eine Datei. Brauchen Sie eine Änderung an fremdem Code, beschreiben Sie sie im Abschlussbericht.

## Regeln für den Quelltext

- Java 17-Syntax, Build ohne Gradle (`scripts/check-build.sh launcher|dream` prüft Übersetzung und d8).
- **Keine anonymen Klassen** (D8-Absturz in Build-Tools 33, auch in Methoden, etwa bei `TextWatcher` oder `ConstantState`): Lambdas oder benannte statische geschachtelte Klassen.
- Keine `java.util.function`-Typen; eigene kleine Schnittstellen (`SettingsModel.Value`, `SettingsModel.Setter`).
- Keine neuen Berechtigungen im Launcher (Ausnahme: die Signatur-Permission `READ_WEATHER`, nur für den Wetterstand des eigenen Bildschirmschoners). Kein Zugriff auf Netzwerk. Keine Protokolle mit persönlichen Daten.
- Kommentare auf Deutsch, knapp, nur wo das Warum nicht offensichtlich ist. Sprechende Namen. Dateien unter 600 Zeilen.
- Fokus: jedes interaktive Element `setFocusable(true)`, `setClickable(true)`; Fokus-Reihenfolge per D-Pad logisch; kein Fokus-Verlust nach Änderungen.
- Texte: Deutsch, einheitlicher Ton (siehe Prinzip 7).
- Nach `Back` muss der Fokus dort landen, wo die Ansicht geöffnet wurde.
- Speicher: keine Bitmaps cachen, die größer als ein Symbol sind; Views beim Schließen freigeben.

## Maße und Abstände (zentrale Regeln)

- **Bildschirmgröße nur über `Metrics`** (`usableWidth/usableHeight` ziehen den Sicherheitsrand ab; `Ui.usable*` leitet dorthin weiter). Rohwerte (`widthPixels`/`heightPixels`) sind nur in `Ui` und `Metrics` erlaubt; Ausnahmen kennzeichnet `// regel-ok: Grund`. `scripts/check-rules.sh` prüft das und läuft bei jedem `scripts/check-build.sh launcher`.
- **Abstandsleiter** in dp: `Metrics.SPACE_XS..SPACE_XXL` = 4, 8, 12, 16, 24, 32. Neue Abstände nehmen eine dieser Stufen.
- **Skalierung:** was mit der Größenstufe wächst (Dock: Kachel, Lücke, Innenabstand, Glasradius), nimmt denselben Faktor (`HomeGeometry.dockScale()`); Glas und Inhalt skalieren immer zusammen und bleiben mittig.
- **Schrift:** Zeilen und Dialoge wachsen mit `Ui.sp(1f)`, damit nichts mit "…" abgeschnitten wird.
- **Eigene Breiten** (Dialoge, Leisten) richten sich nach dem nutzbaren Bereich, nie nach dem Rohbildschirm.

## Randfall-Test am Gerät

`ANDROID_SERIAL=<IP>:5555 scripts/test-matrix.py [Ausgabeordner] [--quick]` setzt Sicherheitsrand (0/24/48 dp), Schriftgröße (90/100/130 %) und Dock-Größe (Klein/Normal/Groß) über "Sicherung laden", liest die Oberfläche aus und prüft (Startseite, Einstellungen, Kontrollzentrum): nichts im Sicherheitsrand, keine abgeschnittenen Texte ("…"), Dock-Glas mittig mit allen Kacheln darin. Dazu je Fall ein Screenshot. `--quick` prüft fünf Eckfälle (etwa 10 Minuten), die volle Matrix 27 Fälle (etwa eine Stunde). Danach steht der Stick wieder auf Standard.

## Abnahme (wann ist ein Teil fertig?)

1. Übersetzt mit `scripts/check-build.sh` (das enthält die Regelprüfung), ohne Warnungen aus eigenem Code. Bei Layout-Änderungen zusätzlich `scripts/test-matrix.py --quick`.
2. Alle Zustände bedacht: leer, sehr lang (Titel mit 40 Zeichen), viele Einträge (50+), Fokus am Rand.
3. Aussehen im Gesamtbild stimmig (siehe Prinzipien), nichts im Android-Standardlook.
4. Abschlussbericht: Dateien, öffentliche Schnittstelle, offene Punkte, was getestet wurde und wie.
