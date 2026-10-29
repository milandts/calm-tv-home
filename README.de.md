# TV Home (Deutsch)

Angepasste Version von **TV Home**, einer werbefreien, tvOS-ähnlichen Startseite für den Amazon Fire TV Stick,
dazu ein eigener **Bildschirmschoner** mit Wetter. Dieses Repo enthält den Quelltext des Launchers (`launcher/`), des
Bildschirmschoners (`dream/`) und Skripte, die daraus APKs bauen. Herkunft und Lizenzlage stehen unten unter "Lizenzen und Bildnachweise".

Getestet auf einem Fire TV Stick HD / Lite (Modell AFTSS, Fire OS 7.7.1.7, Android 9), zusammen mit dem
Debloat-Werkzeug [dumbtv](https://github.com/humblemuzzu/dumbtv).

## Was geändert wurde

| Bereich | Änderung |
|---|---|
| Sprache | Alle sichtbaren Texte auf Deutsch |
| Hintergrund | 30 Naturfotos von Wikimedia Commons, **ein Bild pro Tag** statt Wechsel alle 45 s; "Nächstes Hintergrundbild" gilt bis Mitternacht |
| HOME-Taste | Start per Alarm-`PendingIntent` (ohne die 5-Sekunden-Sperre von Android): Amazons Startseite ist ca. 0,4 bis 0,5 s kürzer im Fokus (Einzelmessungen, siehe docs/NOTES.md) |
| Anzeigen | Größere Uhr, Fokus-Ring, Einblend-Animation, Schatten für bessere Lesbarkeit |
| Filter-Anzeige | "Filter (VPN) aktiv" unter dem Datum (prüft nur, ob irgendein VPN läuft) |
| Einstellungen | Eigene Einstellungs-Ansicht mit Seitenleiste (Darstellung, Apps & Dock, Bildschirmschoner, Verbindungen & Geräte, System, Datenschutz, Sicherung, Info) und Unterseiten; "System > Alle Einstellungen" öffnet Amazons Übersicht (erreicht auch Anzeige & Ton, Barrierefreiheit, Konto) |
| Live-Vorschau | Hintergrund (scharf/unscharf, Abdunkeln) und Akzentfarbe werden direkt auf der echten Startseite eingestellt: Foto, Uhr und Dock bleiben sichtbar |
| Kontrollzentrum | Menütaste auf den Buttons oben rechts: Filter- und Verbindungsstatus, Bildschirmschoner starten, nächstes Bild, Schärfe, Abdunkeln, Einstellungen |
| Einführung | Fünf kurze Schritte beim ersten Start (Farbe, Hintergrund, Bedienung); wiederholbar unter Einstellungen > Info |
| Oberfläche | Eigene Glas-Menüs, Eingabefelder und Meldungen statt der Android-Dialoge, Symbole im Code gezeichnet, Suche findet auch Einstellungen |
| Apps ordnen | Menütaste oder langes Drücken auf einer App: Dock, **Ordner** (anlegen, umbenennen, auflösen), **Nach vorn ziehen**, **Umbenennen**, **Ausblenden** (wieder einblenden unter Einstellungen > Ausgeblendete Apps), Apps verwalten |
| Dock-Hinweis | Hat das Dock mehr Apps als ins Glas passen, zeigt ein dünnes "‹" oder "›" neben dem Glasrand, auf welcher Seite noch Apps liegen (nur dort, wo es weitergeht; bei "Bewegung: Aus" ohne Einblenden) |
| Zuletzt benutzt | Abschnitt über dem App-Raster mit den zuletzt über TV Home gestarteten Apps (ohne Dock-Apps) |
| Konfigurierbar | Zentrale Einstellungen (`Opts`/`Config`): Uhr (Format, Größe, Stil, Datum, Gruß mit Name, Blöcke ein/aus), Glas (Deckkraft, Tönung, Sättigung, Helligkeit, Rand, Glanz, Schatten, Voreinstellungen), Abdunkeln stufenlos, Tempo der Animationen, Fokus-Stärke, Schriftgröße, Sicherheitsrand, Bildwechsel-Rhythmus; riskante Werte mit Rückfrage "Behalten?"; Menütaste setzt eine Einstellung zurück |
| Layout | Position von Uhr und Button-Leiste (Ecke, Versatz), Dock oben oder unten, Größe, Abstand, Rundung und Namen der Kacheln, Spalten und Sortierung im Raster, Voreinstellungen (Standard, Kompakt, Kino, Groß); Menütaste im Dock öffnet Einstellungen und Kontrollzentrum, falls die Leiste ausgeblendet ist |
| Übernehmen | Änderungen in einem Bereich gelten zuerst probeweise; beim Verlassen fragt ein Dialog "Änderungen behalten?" mit Liste, bei riskanten Werten folgt "Passt das so?" mit Rückzähler; HOME speichert ohne Dialog |
| Bilder | Galerie: Bilder auswählen, ausblenden, Favoriten, Ausschnitt und Helligkeit je Bild; optional ein eigener Bilderordner auf dem Stick (standardmäßig aus, kein Netz) |
| Aussehen | Akzentfarbe (Standard "Automatisch": passend zum Tagesbild, oder feste Farbe; Fokus-Ring und Fokus-Füllung), Abdunkeln des Hintergrunds (0/20/35/50 %), Hintergrund scharf oder unscharf |
| Diagnose | Einstellungen > System > Diagnose (nur lesen, nichts wird gesendet): App-Speicher (PSS), Android-Version, ob Animationen im System verändert sind, ob ADB-Debugging an ist (Warnung), Bildschirmschoner installiert und eingestellt, HOME-Dienst aktiv, Anzahl Hintergrundbilder; dort der **Tasten-Test** (zeigt den Namen jeder Fernbedienungstaste, Zurück zweimal beendet) |
| Einschlaf-Timer | Einstellungen > Bildschirmschoner: aus/15/30/60 Minuten, gilt einmal (Alarm, keine neue Berechtigung); danach startet der Bildschirmschoner oder der Bildschirm geht aus (Ruhezustand über die Bedienungshilfen-Aktion des HOME-Dienstes) |
| Sicherung | "Sicherung speichern/laden" legt Dock, Ordner, Namen und Aussehen als JSON in den App-Ordner (siehe unten) |
| Bildschirmschoner | Eigene App mit Kamerafahrt über die 30 Fotos, Uhr, Datum und Wetter (siehe unten) |

Der Launcher hat weiterhin **keine Internetberechtigung**. Er braucht nur `ACCESS_NETWORK_STATE` (läuft der DNS-Filter?).
WLAN-Name, IP- und MAC-Adresse liest und zeigt er nicht. Das Wetter holt allein der Bildschirmschoner (eigene APK, siehe unten). Auf der Startseite zeigt der Launcher unter dem Datum nur dessen
letzten Stand (z. B. "12° · Teils bewölkt", ab 3 Stunden Alter ausgeblendet; abschaltbar unter Darstellung > Uhr und Datum). Der Stand kommt über einen
lesenden Provider, den nur Apps mit demselben Signaturschlüssel abfragen dürfen; Ort und Koordinaten gibt er nicht heraus.

**Der HOME-Dienst** ist ein Accessibility-Dienst (Fire OS lässt Apps die HOME-Taste nicht anders übernehmen). Er ist mit
`canRetrieveWindowContent` und Tastenfilter deklariert, könnte also Bildschirminhalte lesen. Genutzt wird das nur, um im
Rückfall-Weg die Kachel "TV Home" in Amazons Startseite zu finden. Wer das nicht möchte, kann den Dienst abschalten (siehe unten);
dann bleibt HOME bei Amazons Startseite.

## Voraussetzungen

- macOS oder Linux mit `bash` (Windows: über WSL; nicht getestet). Getestet ist nur macOS
- `curl`, `python3`, `zip`, `openssl`; optional `cwebp` (macOS `brew install webp`, Linux `apt install webp`): damit werden die Fotos WebP (Qualität 90) und jede APK etwa 17 statt 26 MB groß
- JDK 17 oder neuer (`javac`, `keytool`; das JDK schneidet auch die Fotos zu, `tools/CropWallpapers.java`)
- Android SDK mit **Build-Tools 33.0.0** und **Plattform android-33** (wird in `ANDROID_SDK`, `ANDROID_HOME` und den üblichen Ordnern gesucht, siehe `scripts/env.sh`)
- `adb` (zum Installieren), Fire TV Stick im selben WLAN mit eingeschaltetem **ADB-Debugging**
- Hinweis: Build-Tools 33 haben einen D8-Fehler bei anonymen Klassen in Feld-Initialisierern. Im Code deshalb nur benannte
  Klassen verwenden.

## Bauen

```bash
scripts/setup.sh          # Bilder laden, baut out/TVHome.apk und out/TVHomeDream.apk
scripts/build-dream.sh    # nur den Bildschirmschoner neu bauen (setup.sh muss einmal gelaufen sein)
```

Umgebungsvariablen für `scripts/build.sh` (alle optional):

| Variable | Bedeutung | Standard |
|---|---|---|
| `ANDROID_SDK` | Pfad zum Android SDK | wird gesucht (`scripts/env.sh`) |
| `BUILD_TOOLS` | Version der Build-Tools | `33.0.0` |
| `PLATFORM` | Plattform für `android.jar` | `android-33` |
| `TVHOME_KEY_DIR` | Ordner mit `tvhome.keystore` und `keystore.pass` | `~/.config/firestick-tvhome` |

Der **Signaturschlüssel** wird beim ersten Bauen in `TVHOME_KEY_DIR` angelegt, **außerhalb des Repos**. Sichere ihn:
Nur mit demselben Schlüssel lässt sich eine neue APK über die installierte installieren. Schlüssel und Kennwort
gehören nie in Git (`.gitignore` schließt sie aus).

## Installieren

```bash
adb connect <IP-des-Sticks>:5555
ANDROID_SERIAL=<IP-des-Sticks>:5555 scripts/install.sh            # nimmt out/TVHome.apk
ANDROID_SERIAL=<IP-des-Sticks>:5555 scripts/install.sh pfad/zur.apk
```

`install.sh` installiert die APK, **bindet den HOME-Dienst neu** (sonst bleibt der Stick nach einer Neuinstallation bei Amazons
Startseite) und setzt bei Bedarf `alarm_manager_constants min_futurity=0`. Danach ADB-Debugging am Stick wieder ausschalten.

**Achtung, zwei Systemeinstellungen werden geändert:**
- `enabled_accessibility_services` wird **komplett überschrieben**. Andere Accessibility-Dienste auf dem Stick sind danach aus.
- `alarm_manager_constants` (global) bekommt `min_futurity=0`, falls dort noch nichts steht. Das gilt für das ganze System.

## Bildschirmschoner

`dream/` ist eine eigene kleine App (Paket `app.calmtv.home.dream`) und der **System-Bildschirmschoner** des Sticks:
Naturfotos mit langsamem Zoom und Schwenk, weicher Überblendung, Uhr, Datum und Wetter, die bei jedem Foto in eine andere
Ecke wandern. Jede Taste beendet ihn.

```bash
ANDROID_SERIAL=<IP-des-Sticks>:5555 scripts/install-dream.sh      # installiert und aktiviert (nimmt out/TVHomeDream.apk)
```

- **Aktivieren** setzt nur `screensaver_components` (welcher Bildschirmschoner läuft). Zurück zu Amazons:
  `adb shell settings put secure screensaver_components com.amazon.ftv.screensaver/.app.services.ScreensaverService`
- **Startzeit:** Amazons Einstellung "Bildschirmschoner" (Standard 5 Minuten).
- **Wetter:** In TV Home unter Einstellungen > "Wetter einrichten" die Stadt eintippen und aus den Treffern wählen.
  "Bildschirmschoner jetzt" zeigt ihn sofort. Ohne gewählte Stadt gibt es kein Wetter und keine Netzwerkzugriffe.
  Standard: Das Wetter wird nur geholt, wenn der Bildschirmschoner läuft. Im Bildschirmschoner unter Wetter > "Auffrischen" lässt sich "Alle 2 Stunden"
  wählen; dann holt ihn ein Zeitplan im Hintergrund (nur mit gewählter Stadt und eingeschaltetem Wetter), damit die Wetterzeile auf der Startseite aktuell bleibt.
  Abgerufen wird nur per HTTPS und nur von den beiden Open-Meteo-Adressen, ohne Weiterleitungen.
- **Datenschutz:** Nur diese App hat die Berechtigung `INTERNET`, und nur für [Open-Meteo](https://open-meteo.com/)
  (kostenlos, ohne Schlüssel und Konto). Beim Suchen geht der eingetippte Stadtname an Open-Meteo, danach nur die auf zwei Nachkommastellen
  gerundeten Koordinaten der gewählten Stadt, jeweils mit der IP-Adresse. Die Stadt steht nur auf dem Stick, nie im Repo.
- Die Fotos und die Schrift kopiert `scripts/build-dream.sh` aus dem Launcher-Bau; sie liegen nicht im Repo.

## Sicherung der Anordnung

"Sicherung speichern" schreibt `tvhome-sicherung.json` nach `/sdcard/Android/data/app.calmtv.home/files/` (keine Berechtigung nötig).
Sie enthält Paketnamen des Docks, Ordner, ausgeblendete und umbenannte Apps und das Aussehen. Kopieren geht mit
`adb pull /sdcard/Android/data/app.calmtv.home/files/tvhome-sicherung.json`. Die Datei wird beim Entfernen oder "Daten löschen" der App mitgelöscht.

## Wenn HOME bei Amazons Startseite bleibt

Nach "Stoppen erzwingen" (Einstellungen → Apps) oder nach einer Neuinstallation nimmt Android den HOME-Dienst aus den aktiven
Diensten. Dann bindet ihn dieses Skript neu:

```bash
ANDROID_SERIAL=<IP-des-Sticks>:5555 scripts/rebind.sh
```

## Messen

```bash
ANDROID_SERIAL=<IP>:5555 tools/measure-home.sh 5   # wie lange Amazons Startseite nach HOME im Fokus ist
ANDROID_SERIAL=<IP>:5555 tools/perf.sh              # Anteil ruckelnder Bilder bei fester Tastenfolge
```

`measure-home.sh` öffnet dafür kurz den Silk-Browser. Beide Skripte senden Tasten an den Stick, der Fernseher reagiert also.

**Zeitlupe für Animationstests:** `adb shell settings put global animator_duration_scale 8`. Danach unbedingt mit
`adb shell settings put global animator_duration_scale 1.0` zurücksetzen, **nicht** mit `settings delete`: Das Fenstersystem behält
sonst den alten Wert im Speicher, und alle Animationen auf dem Stick bleiben langsam, obwohl `settings get` "null" zeigt.

## Zurück zum Ausgangszustand

- HOME-Dienst zurück zu dumbtv home: `adb shell settings put secure enabled_accessibility_services "tv.dumbtv.home/tv.dumbtv.home.Guard"`
- Alarm-Einstellung zurücksetzen: `adb shell settings delete global alarm_manager_constants` (dumbtv setzt sie für seinen eigenen Launcher ebenfalls)
- Bildschirmschoner zurück zu Amazons (siehe oben) und entfernen: `adb uninstall app.calmtv.home.dream`
- TV Home entfernen: `adb uninstall app.calmtv.home`
- Alles von dumbtv rückgängig machen: `scripts/restore.sh` aus dem dumbtv-Repo (nur mit dessen Snapshot vom ersten Lauf)

## Aufbau der Oberfläche

Die Gestaltungsregeln, die Informationsarchitektur der Einstellungen und die Zuständigkeiten je Datei stehen in [docs/DESIGN.md](docs/DESIGN.md). Kurz: `HomeActivity` ist der Host (`HomeHost`), Vollbild-Ansichten (`Screen`: Einstellungen, Kontrollzentrum, Einführung) liegen in einer eigenen Ebene, Menüs, Eingaben und Meldungen (`GlassMenu`, `GlassPrompt`, `GlassToast`) in einer Ebene darüber. `scripts/check-build.sh launcher|dream` prüft schnell die Übersetzung (javac und d8) und beim Launcher die Layout-Regeln (`scripts/check-rules.sh`). `scripts/test-matrix.py` prüft am Stick Sicherheitsrand, Schriftgröße und Dock-Größe (Startseite, Einstellungen, Kontrollzentrum) per Oberflächen-Auslese.

## Struktur

```
launcher/                 Launcher (Quelltext, Ressourcen, Manifest, Schrift; die Fotos lädt setup.sh)
dream/                    Bildschirmschoner (eigener Quelltext: Dienst, Vorschau, Stadtwahl, Wetter)
wallpapers.json           Quellen der 30 Fotos (Commons-Seite, Urheber, Lizenz)
scripts/                  setup, build, build-dream, install, install-dream, rebind, Bilder laden/zuschneiden/einfügen
tools/                    Messskripte
docs/NOTES.md             Messwerte und gescheiterte Versuche
docs/DESIGN.md            Gestaltungsregeln, Informationsarchitektur, Zuständigkeiten
```

## Lizenzen und Bildnachweise

- **Herkunft:** Der Launcher ging ursprünglich aus [vom00/firestick-tv](https://github.com/vom00/firestick-tv) hervor, das keine Lizenz hat.
  Alle damals übernommenen Dateien sind neu geschrieben (Messung: `scripts/check-upstream-overlap.sh`, 0 wortgleiche Blöcke). Ganz
  ausgeschlossen ist eine Ähnlichkeit damit nicht, weil die Neufassung die Idee und das Verhalten des Originals fortführt.
- Schrift Nunito: SIL Open Font License 1.1 (liegt im Original und wird in den Bildschirmschoner kopiert).
- Der Quelltext in `dream/` ist eigener Code ohne Lizenzdatei; die Wetterdaten stammen von Open-Meteo (CC BY 4.0, Nennung dort).
- Die 30 Fotos stammen von Wikimedia Commons (CC BY-SA 3.0/4.0, CC BY 4.0). Urheber und Lizenz je Bild stehen in
  `wallpapers.json` und werden in TV Home unter Einstellungen → Bildnachweise angezeigt. Die Bilder wurden auf 16:9 zugeschnitten
  und auf 1920×1080 skaliert; sie gelten **je Bild unter der Lizenz aus `wallpapers.json`**.
- Die Skripte in diesem Repo haben keine eigene Lizenzdatei.
