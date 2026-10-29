# Notizen: Messwerte und gescheiterte Versuche

Alle Zahlen stammen von einem Fire TV Stick HD / Lite (AFTSS, Fire OS 7.7.1.7, 1 GB RAM) und sind Einzelmessungen
mit starker Schwankung. Die Messskripte liegen in `tools/`. Der Unterschied beim Ruckeln (43 % gegen 41 % und 35 %) liegt im
Rauschen; für die HOME-Zeit gilt: etwa 0,4 bis 0,5 s schneller, aus je 3 bis 4 Läufen.

## Messwerte

| Messung | Ausgangsversion | Letzter Stand |
|---|---|---|
| Amazons Startseite nach HOME im Fokus (`measure-home.sh`) | 1897, 1927, 2007 ms (Mittel 1,94 s) | 1372, 1831, 1424, 1352 ms (Mittel 1,50 s); nur mit Alarm-Start ohne weitere Änderungen: Mittel 1,34 s |
| Ruckelnde Bilder bei fester Tastenfolge (`perf.sh`) | 43 % | 41 % und 35 % |
| App-Speicher (TOTAL PSS) | ca. 95 bis 100 MB | ca. 108 bis 110 MB (Ursache **ungeklärt**) |

## Warum nicht schneller als ca. 1,4 s

Android liefert das Ereignis "Fenster gewechselt" für Amazons Startseite erst ca. 0,8 s nach dem HOME-Druck, weil die Startseite
selbst so lange zum Aufbau braucht (`am_activity_launch_time` ca. 800 ms). Der Dienst kann erst danach reagieren.
Die verbleibende Zeit bis TV Home sichtbar ist, stammt aus dem Pausieren von Amazons Startseite und dem Wiederaufnehmen von TV Home.

## Probiert und verworfen

0. **Kennzahlen oben rechts** (WLAN-Band, Verbindungsrate und Signal, freier Speicher und RAM mit Warnfarbe, Laufzeit): gebaut und
   getestet, auf Wunsch des Nutzers wieder entfernt (Commit "refactor: Kennzahlen oben rechts entfernt"). Die Berechtigung
   `ACCESS_WIFI_STATE` entfiel damit ebenfalls. Der Code steht im Git-Verlauf davor.

1. **HOME-Druck früher erkennen über `ACTION_CLOSE_SYSTEM_DIALOGS` (reason=homekey)**: Fire OS sendet den Broadcast, aber mit der
   Berechtigung `com.amazon.permission.ENABLE_KEYGUARD_FLAGS` (`signature|privileged|amazon`). Der Empfänger einer normalen App wird
   übersprungen ("Skipped"). Ebenso `amazon.intent.action.HOME_PRESSED` (`signature|privileged`, gezielt an Amazon-Pakete).
2. **Kachel "Speicher freigeben"** (`killBackgroundProcesses` für alle Apps): `MemAvailable` stieg nicht (240 → 235 MB, 237 → 223 MB),
   Prime Video blieb bei 79 bis 85 MB. Android verwirft zwischengespeicherte Prozesse bei Bedarf selbst; sie vorab zu beenden bringt nichts.
3. **"Im Hintergrund beenden" für eine Dock-App**: Der Aufruf `killBackgroundProcesses` läuft, aber der Prozess (Netflix) bleibt
   bestehen. `adb shell am kill` beendet ihn dagegen. Fire OS lässt Fremd-Apps das offenbar nicht zu.
4. **Chip-Temperatur aus `/sys/class/thermal/thermal_zone0/temp`**: für Apps nicht lesbar.
5. **`canRetrieveWindowContent=false`** im Accessibility-Dienst: keine messbare Verbesserung, deshalb zurückgenommen.
6. **App-Info einer einzelnen App**: Amazons Einstellungen ignorieren das Paket und zeigen "Installierte Apps verwalten". Der Dock-Menüpunkt
   heißt deshalb "Apps verwalten".

## Korrekturen aus dem Code-Check

Ein unabhängiger Review fand diese Fehler; sie sind behoben und am Gerät geprüft (soweit angegeben):

- **Kachel-Übergang im Einstellungsmenü:** Der Schatten der angehobenen, halb durchsichtigen Text-Kachel schien als dunkles Rechteck
  (um den Eckradius eingerückt) durch. Text-Kacheln haben jetzt keine Erhebung; Füll-, Schrift- und Glanzfarbe laufen in einem Animator.
  Geprüft mit Zeitlupe (`animator_duration_scale`) und echten Bildschirmfotos; ein Video allein war nicht eindeutig.
- **App-Liste nach Installation/Deinstallation:** Die Empfänger liefen nur zwischen `onStart` und `onStop`; Änderungen im Hintergrund
  (Appstore, "Apps verwalten") fehlten nach HOME. Jetzt wird beim Start geprüft, gebündelt (500 ms) und nur bei echter Änderung neu gebaut,
  ohne den Fokus von den Buttons ins Dock zu ziehen. Geprüft: App deaktivieren, HOME, App ist weg.
- **Geteilte Bilder zwischen Dock, Übersicht und Suche:** Jede Kachel bekommt eine eigene Kopie (Kacheln von Apps ohne Banner kamen sonst klein heraus).
- **Pills (Apps/Suche/Einstellungen):** Schrift- und Symbolfarbe laufen mit der Füllung (gleiches Muster wie bei den Kacheln).
- **Hintergrundbild, das erst nach dem Verlassen fertig geladen wird,** wird verworfen (sonst blieben ca. 8 MB belegt).
- Kleinigkeiten: Spalte beim Sprung vom Dock in die Übersicht bei mehr als 6 Apps, Vorlesen der Uhrzeit (`contentDescription`), "…" bei zu langem Text.

Bewusst nicht umgesetzt: Kacheln der Suche für sehr viele Apps (Leistung, nur bei vielen Apps spürbar), vollständige EXIF-Auswertung
bei eigenen Fotos (es gibt keine), und die als "unsicher" gemeldeten Randfälle im HOME-Dienst (nicht belegt).

## Einstellungs-Kacheln und gesperrte Seiten

Amazons Einstellungsseiten "Anzeige & Ton", "Präferenzen"/"Datenschutz", "Bedienungshilfen", "Konto & Profile" und "Standby & Spiegelung"
verlangen die Berechtigung `com.amazon.tv.permission.LAUNCHER_SETTINGS` (`signature|privileged|amazon`) bzw. `INSTALL_PACKAGES`.
Eine normale App bekommt `Permission Denial`, TV Home zeigte nur den Hinweis "Diese Einstellung ist nicht verfügbar". Offen sind
nur Netzwerk, Anwendungen, Controller & Bluetooth und Mein Fire TV. Der allgemeine Aufruf `android.settings.SETTINGS` landet in einem
Dummy, der Amazons Startseite öffnet (die der HOME-Dienst dann übernimmt).

Lösung: Die Kategorie `android.intent.category.LEANBACK_SETTINGS` (Aktion MAIN) öffnet `com.amazon.tv.launcher/.ui.MainSettingsActivity`,
Amazons Einstellungsübersicht ohne Berechtigung (dumbtv nimmt denselben Weg). Von dort startet Amazons Launcher alle Seiten selbst.
TV Home zeigt dafür die Kachel "Alle Einstellungen" und blendet Direktkacheln aus, deren Activity eine nicht erteilte Berechtigung
verlangt (`ActivityInfo.permission`). Am Gerät geprüft: Übersicht, "Töne und Bildschirm" und zweimal Zurück zu TV Home.

## Bildschirmschoner

- Fire OS nutzt den normalen Android-Mechanismus: Amazons Standard ist `com.amazon.ftv.screensaver/.app.services.ScreensaverService`
  (`settings get secure screensaver_components`). Ein eigener `DreamService` lässt sich per `settings put secure screensaver_components`
  setzen (Shell darf das) und startet nach `screen_off_timeout` (Standard 300000 ms). Am Gerät geprüft: Start nach 20 s Leerlauf,
  Taste beendet ihn, danach TV Home im Vordergrund.
- Messung (Stick HD): ca. 78 MB PSS, Bildzeiten fast nur 17 bis 23 ms (ca. 45 bis 55 Bilder/s), 99. Perzentil 38 ms. Der Anteil "ruckelnder"
  Bilder in `gfxinfo` liegt bei ca. 90 %, weil schon 17 ms über dem 16,7-ms-Raster zählen; bei langsamer Kamerafahrt fällt das nicht auf.
- Bewusst keine Videos: wären viel größer als die Fotos, und es gibt keine sauber lizenzierten Clips im Repo. Kamerafahrt (Zoom bis 12 %,
  Verschiebung höchstens 5 %) lässt nie einen Rand sichtbar werden.
- Das Wetter (Open-Meteo) ist nur in der Bildschirmschoner-APK; der Launcher bleibt ohne `INTERNET`.
- Wetterzeile auf der Startseite (`HomeWeather`): liest den Stand über den Provider `app.calmtv.home.dream.weather` (`WeatherProvider` in der
  Dream-APK, nur Temperatur, Text, Zeit). Schutz: Signatur-Permission `app.calmtv.home.permission.READ_WEATHER` (in beiden Manifesten definiert,
  gleicher Schlüssel) und im Launcher Prüfung von Paketname und `checkSignatures`, sonst könnte eine fremde App unter derselben Adresse
  Text einschleusen. Die Abfrage startet den Dream-Prozess, darum nur im Hintergrund-Thread und höchstens alle 5 Minuten; es gibt keinen
  Auffrischen-Aufruf (der Launcher löst nie Netzverkehr aus). Der Stand ändert sich nur, wenn der Bildschirmschoner lief, außer der Nutzer
  wählt im Bildschirmschoner unter Wetter "Auffrischen: Alle 2 Stunden" (Standard: nur beim Bildschirmschoner). Dann holt `WeatherRefresh`
  (JobScheduler, nicht persistent) alle 2 Stunden das Wetter; ihn legt `sync` an und löscht ihn wieder, der Job prüft vor jedem Abruf dieselben
  Bedingungen (Auswahl, Wetter an, Stadt gewählt). Nach einem Neustart zieht `WeatherProvider.query` den Plan nach (TV Home fragt beim Heimkommen).
  Einziges Tor nach draußen ist `Weather.getJson`: nur HTTPS, nur die beiden Open-Meteo-Hosts, keine Weiterleitungen; im Manifest kein Klartext.
- Falle: Ein `LinearLayout` kürzt Kinder mit `match_parent` (Standard beim Hinzufügen ohne LayoutParams) auf die Breite der breitesten
  `wrap_content`-Zeile; die Uhr wurde zu "16:". Alle Kinder mit expliziten LayoutParams (-2, -2) hinzufügen.
- Z-Reihenfolge: Die zwei Foto-Ebenen liegen mit `translationZ` übereinander, Schatten und Angaben brauchen ein größeres Z, sonst werden sie überdeckt.

## Ordner, Ausblenden, Umbenennen

- Alles liegt in den SharedPreferences "layout" (`AppLayout`, `Look`) und, für das Dock, in "home". Gespeichert werden nur Paketnamen und
  selbst vergebene Namen. Ausgeblendete Apps bleiben in der Suche auffindbar.
- "Zuletzt benutzt" zählt nur Starts über TV Home (kein UsageStats-Zugriff, keine Sonderrechte) und lässt Dock-Apps weg.
- Das Raster hinter einem geöffneten Ordner wird per `FOCUS_BLOCK_DESCENDANTS` gesperrt, sonst läuft der Fokus durch das Overlay hindurch.

## Oberfläche 2.0: Erkenntnisse aus dem Gerätetest

- **HOME bei offenem Menü** führte zu einem ANR: `Ui.dismissTopModal` lieferte true, solange das Modal in der Ausblend-Animation stand, und eine
  Schleife `while (dismissTopModal(...))` im Host lief endlos. Jetzt entfernen sich Modals sofort aus der Ebene (Animation läuft an einer Hülle), und
  `onNewIntent` räumt die Modal-Ebene mit `removeAllViews()`.
- **Seiten ohne fokussierbare Zeilen** (Bildnachweise, Datenschutz-Texte) waren nicht bedienbar. Die Seite fängt jetzt selbst den Fokus und Hoch/Runter scrollen
  um ca. 40 % der Listenhöhe; ein Balken am Rand und weiche Ränder zeigen weiteren Inhalt an.
- **Panels über der Startseite** halten sich an die freie Zone zwischen Button-Leiste und Dock (y = 74 bis 398 dp bei 540 dp Höhe).
- **Bildschirmtastatur:** Am Stick wird teils eine Hardware-Tastatur gemeldet (`KeyboardType: 2`); die Tastatur erscheint dann nicht von selbst. `GlassPrompt` fordert
  sie mehrfach und bei Auswahltaste an und führt den Fokus per eigener Richtungslogik zwischen Feld und Buttons.
- **Messung:** Seitenleiste 2 % ruckelnd, Inhaltswechsel 25 %, App-Raster 33 %; Speicher nach 20 Öffnen/Schließen-Zyklen der Einstellungen ein Plateau bei ca. 121 MB PSS (kein lineares Leck).
- **D8 aus Build-Tools 33** stürzt auch bei nicht statischen inneren Klassen ab (z. B. `SetupRow$SwitchView`): Hilfsklassen als eigene Dateien oder statische Klassen.

## Stolpersteine

- **`settings delete global animator_duration_scale` setzt die Animationsgeschwindigkeit nicht zurück.** Nach Zeitlupen-Tests mit Faktor 20
  blieben alle Animationen auf dem Stick langsam, obwohl `settings get` "null" zeigte (bemerkt am Nutzer: "viel zu langsam"; im Video
  dauerte ein Übergang statt ca. 0,12 s ein Vielfaches). Richtig: `settings put global animator_duration_scale 1.0`.
- **"Stoppen erzwingen" entfernt den HOME-Dienst aus den aktiven Diensten** (`enabled_accessibility_services` ist danach leer);
  `scripts/rebind.sh` stellt ihn wieder her.

- `cancelHomeNow` (Alarm abbrechen) wirkt praktisch nicht: Der Alarm feuert sofort. Verhindert wird ein späteres Zurückziehen
  durch das Entfernen der Handler-Callbacks.
- Ob Android einen abgestürzten HOME-Dienst von selbst wieder bindet, wurde nicht geprüft; ohne Dienst bleibt HOME bei Amazons Startseite.
- Das Tagesbild hängt an der Uhrzeit des Sticks. Ist sie direkt nach dem Start noch falsch, springt das Bild nach der Zeit-Synchronisierung.

- **Nach jeder Neuinstallation** muss der Accessibility-Dienst neu gebunden werden (`scripts/install.sh` macht das).
- **D8 aus Build-Tools 33** stürzt bei anonymen Klassen in Feld-Initialisierern ab (`NullPointerException`, `EnclosingMethod` ohne
  Methode). Benannte geschachtelte Klassen verwenden.
- Der Wechsel des Tagesbildes um Mitternacht wurde nicht live beobachtet, nur die Logik und der Zustand innerhalb eines Tages.
- Die Anzeige "Filter (VPN) aktiv" prüft nur, ob irgendein VPN-Netzwerk läuft (der DNS-Filter von dumbtv ist eines).

## Eigenständigkeit (Stand der Ablösung vom Original)
- Der Quelltext liegt seit 2026-10-28 direkt im Repo (`launcher/`), nicht mehr als Patch gegen vom00/firestick-tv (hat keine Lizenz). Der alte Stand steht im Git-Verlauf.
- Maß: `scripts/check-upstream-overlap.sh` (braucht einen lokalen Klon in `work/upstream`) zählt Zeilen, die in Blöcken von mindestens 3 nicht trivialen Zeilen wortgleich mit dem Original sind. Ausgangswert 1070, Stand 2026-10-28 nach dem Umbau: 0. Die Startseite ist in Bausteine zerlegt (siehe DESIGN.md), Glass, GlassText, Pill, Tile, HomeKeyService und Wallpapers sind neu geschrieben, Icon, Banner, Manifest und Ressourcen neu gestaltet.
- Grenze der Messung: Sie findet nur wortgleiche Blöcke. Die Neufassung ist mit Kenntnis des Originals entstanden und setzt dessen Verhalten fort; für eine Veröffentlichung bleibt die Anfrage an den Autor (Lizenz) die sicherere Absicherung.
- Die Fotos 01 bis 09 haben jetzt eigene Quellen auf Wikimedia Commons (`wallpapers.json`), zugeordnet per Farbprofil-Vergleich mit den bisherigen Bildern; der Zuschnitt kann leicht vom früheren abweichen.

## Messung der Flüssigkeit (2026-10-29, nach dem Umbau der Startseite)
- Dock links und rechts durchlaufen (10 Tasten): neu 1 bis 9 % ruckelnde Bilder (95. Perzentil 8 bis 18 ms), alter Stand 1 bis 19 % (11 bis 23 ms).
- Raster öffnen und schließen: neu 61 % (40 bis 61 ms), alter Stand 79 % (61 bis 200 ms). Beides ruckelt spürbar; die Übergangsanimation des Rasters ist der teuerste Teil und könnte noch leichter werden (Ebenen ohne Schatten während der Bewegung, kürzere Dauer).
- Einstellungen im Leerlauf: 0 % ruckelnde Bilder, 95. Perzentil 13 ms.
- **Raster-Animation, zweite Runde (2026-10-29, `gfxinfo` nach 10 Zyklen Runter-Taste und Zurück, Abstand 1,5 s, `scripts/build.sh` plus `install.sh` vor jeder Messung):** vorher 93 % ruckelnde Bilder, Median 27 ms, 95. Perzentil 44 ms. Nachher 53 bis 68 % (meist 54 bis 58 %), Median 17 ms, 95. Perzentil 32 bis 34 ms. Ursache: Das Raster (`ScrollView`) wurde beim Ein- und Ausblenden über seine Deckkraft bei jedem Bild in eine Zwischenebene gezeichnet (alle Kacheln mit Schatten und abgerundetem Beschnitt, dann die Ebene mit Deckkraft aufs Bild). `GridScroll` meldet `hasOverlappingRendering() == false`; die Deckkraft wirkt dann einzeln auf jede Zeichnung. Optik gleich bis auf den Mittelteil der Blende, in dem der Kartenhintergrund bei Bildkacheln kurz durch das halbtransparente Bild scheint. Gleiche Dauer. `snapshot-ui.py`: 20 Ansichten, 0 Abweichungen.
- Gescheitert oder ohne Wirkung (alle gemessen): Hardware-Ebene (`withLayer()`) auf allen animierten Ansichten (schlechter, 98 %), nur auf dem Raster (kein Gewinn, auch mit auf den sichtbaren Teil verkleinerter Ebene über Außenabstand statt Innenabstand), Abdunklung des Rasterhintergrunds ins Bitmap gebacken statt eigener Vollbild-Fläche (kein Gewinn), `Bitmap.setHasAlpha(false)` für Foto und Unschärfe-Kopie (kein Gewinn), Dock ohne Animation (kein Gewinn). Die Vollbild-Blende des unscharfen Fotos (Wand plus `libraryBg`, zwei Vollbild-Durchgänge) kostet auf diesem Stick ca. 8 ms je Bild und ist der Rest des Ruckelns; ohne sie läge der Median bei 17 ms. Sie ist Teil der Optik und blieb. Offen: `rebuild()` läuft beim Öffnen synchron im ersten Bild (Layout ca. 27 ms, Gesamtzeit 80 ms); Vorbauen im Leerlauf mit Änderungsmarke wäre der nächste Schritt (Grid-Neuaufbau-Stellen: `AppMenus`, `HomeActivity`).
- Messfallen: `scripts/check-build.sh` baut keine APK, vor `install.sh` immer `scripts/build.sh launcher out`; sonst misst man den alten Stand. Messablauf für das Raster: HOME, `gfxinfo reset`, zehnmal `DPAD_DOWN` und `BACK` (Fokus vorher mit `dumpsys window | grep mCurrentFocus` prüfen).
- Messbefehle: `tools/perf.sh` (Gesamtfolge) und `adb shell dumpsys gfxinfo app.calmtv.home`; vorher HOME drücken, 3 Sekunden warten, sonst zeigt gfxinfo 0 Bilder.

## Umgesetzt (frühere Ideen des Nutzers)
- "Mehr"-Zeichen auch auf den Vorschau-Seiten der Einstellungen (2026-10-28). Die Fenstergröße passte sich schon vorher dem Inhalt an (`SettingsPanelSizer`, Vorschau-Panel umschließt seinen Inhalt).
- Kontrollzentrum anpassbar: Einstellungen > Darstellung > Kontrollzentrum, je Kachel Normal, Zuerst oder Aus (`Opts.CC_*`, `ControlCenter.chooseShownTiles`); Einstellungen bleibt immer da.

## Vorgemerkt (Ideen des Nutzers, noch nicht umgesetzt)
- (derzeit nichts)
