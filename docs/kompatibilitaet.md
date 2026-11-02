# Geräte und Kompatibilität

Die einzige Stelle für die Geräte-Matrix. README und INSTALL verlinken hierher, statt die Tabelle zu wiederholen. Wer ein Gerät prüft, trägt es hier ein (Gerät, Android-Version, was geprüft wurde, was nicht).

## Stand der Prüfung

| Gerät | Android | Geprüft | Wie |
|---|---|---|---|
| Fire TV Stick HD / Lite (AFTSS, Fire OS 7.7.1.7) | 9 | HOME-Taste, Einstellungen, Bildschirmschoner, Wetter, Bildschirmtastatur | echtes Gerät, 1080p |
| Dasselbe Gerät mit vorgetäuschter Größe 1280x720 und 3840x2160 | 9 | Startseite, Einstellungen und Kontrollzentrum (nichts im Sicherheitsrand, kein abgeschnittener Text) | `scripts/test-screens.py` (`wm size`, `wm density`) |
| Android TV (Emulator `atv34`) | 14 | Start, HOME-Taste über den Bedienungshilfen-Dienst, Einstellungen, Bildschirmschoner vorschauen | Emulator, kein echtes Gerät |
| Google TV (Emulator `gtv34`) | 14 | wie Android TV; Bildschirmschoner als `DreamService` angemeldet, `screensaver_components` wird angenommen | Emulator, kein echtes Gerät |
| Andere Fire-TV-Modelle (4K Max, Cube, Omni), Fire OS 8 | 9 bis 11 | nicht geprüft | |
| Sony, Philips, TCL, Hisense, Nvidia Shield, Chromecast mit Google TV | 9 bis 14 | nicht geprüft | |

Nicht prüfbar im Emulator: der Start des Bildschirmschoners durch Leerlauf (`cmd dreams` verlangt Root), ob Googles eigene Auswahl den Schoner anbietet, und die Installation über die App "Downloader".

## Was gerätespezifisch ist

- **Erkennung:** `Platform.isFireTv` (Systemmerkmal oder Amazons Launcher vorhanden). Nur dort gelten Amazons Einstellungs-Aktionen, der Schnellmenü-Weg, die eigene Bildschirmtastatur und der Hinweis "Kein Filter". Auf allen anderen Geräten nimmt TV Home die Systemtastatur und blendet den Hinweis nur bei aktivem VPN ein.
- **HOME-Taste:** Auf dem Fire TV Stick bekommt der Dienst die Taste selbst. Auf Android TV und Google TV nicht; dort erkennt der Dienst, dass die Startseite des Geräts nach vorn kommt (die Auflösung der HOME-Kategorie, also herstellerunabhängig), und holt TV Home davor. Googles Startseite blitzt dabei kurz auf.
- **Apps:** Der Katalog (`AppCatalog`) liest `LEANBACK_LAUNCHER` und `LAUNCHER`. Fire-TV-eigene Apps stehen in einer Weißliste, Play Store und Chrome erscheinen auf Android TV im Raster.
- **Bildschirmschoner:** Fire OS ersetzt Amazons Bildschirmschoner über `screensaver_components` (nur per `adb`). Android TV und Google TV bieten die Auswahl je nach Gerät in den eigenen Einstellungen an, Google TV oft nicht.

## Bekannte Grenzen und Risiken

- **`targetSdk` ist 28** (`scripts/build.sh`), `minSdk` 22. Android 14 installiert das ohne Warnung (Mindestwert dort 23). Spätere Android-Versionen heben diesen Mindestwert an; ein höherer `targetSdk` ändert aber das Verhalten bei Hintergrunddiensten, Paketsicht und Bedienungshilfen und lässt sich ohne echte Geräte nicht absichern. Darum bewusst nicht angehoben. Mit 28 gilt auch keine Paketsichtbarkeits-Einschränkung (Android 11), die den App-Katalog sonst leer ließe.
- **Android 13 und neuer:** Das System kann den Schalter für den HOME-Dienst bei Apps sperren, die aus einer Datei statt aus einem Store installiert wurden ("Eingeschränkte Einstellungen"). Üblicher Weg laut Android: Einstellungen > Apps > TV Home > Menü (drei Punkte) > "Eingeschränkte Einstellungen zulassen". Auf einem Fernseher nicht geprüft; ein Weg über `adb install` ist ausgenommen.
- **Nur Querformat** (`screenOrientation="landscape"`), keine Touch-Bedienung vorausgesetzt (`touchscreen` und `leanback` sind nicht verpflichtend).
- **Größen:** Maße kommen zentral aus `Metrics` und `Ui` und skalieren mit der Dichte; geprüft sind 720p, 1080p und 4K auf dem Fire TV Stick. Ein Fernseher mit starkem Overscan braucht den "Sicherheitsrand" unter Einstellungen > Bedienungshilfen.
- **Fernbedienungen:** Getestet ist die Fire-TV-Fernbedienung. Tasten anderer Fernbedienungen zeigt Einstellungen > System > Diagnose > Tasten-Test.

## Ein Gerät melden

Im Issue-Formular "Bug" Hersteller und Modell, Android- oder Fire-OS-Version und die Zeile "HOME-Taste" von Einstellungen > Einrichtung angeben. Keine IP-Adressen und keine Kontonamen.
