# Entscheidungen

## 2026-10-25: Der Launcher bekommt kein Internet, das Wetter holt ein eigener Bildschirmschoner
- **Status**: angenommen
- **Kontext**: Datenschutz ist Hauptzweck; trotzdem soll es Wetter geben.
- **Entscheidung**: Zwei APKs. Nur `dream/` hat `INTERNET` und holt das Wetter von Open-Meteo für die Stadt, die der Nutzer selbst wählt.
- **Folgen**: Der Launcher ist belegbar offline (Einstellungen > Datenschutz liest es zur Laufzeit). Wetter im Launcher braucht einen Übergabeweg (siehe unten).
- **Verworfen**: Wetter im Launcher mit eigener Berechtigung: widerspricht dem Zweck.

## 2026-10-28: Wetterstand über einen signaturgeschützten Provider an den Launcher
- **Status**: angenommen
- **Kontext**: Der Nutzer wollte das Wetter auch auf der Startseite, ohne den Launcher ans Netz zu hängen.
- **Entscheidung**: `WeatherProvider` im Bildschirmschoner (Temperatur, Text, Zeit; kein Ort), geschützt durch `READ_WEATHER` (Signatur). Der Launcher prüft Paket und Signatur selbst, weil die Permission nur den Provider schützt, nicht vor einem fremden Provider mit derselben Adresse.
- **Folgen**: Kein Netzverkehr durch HOME-Drücke. Der Stand ist so aktuell wie der letzte Lauf des Bildschirmschoners; ab 3 Stunden blendet die Zeile sich aus.
- **Verworfen**: Aufrufen einer Auffrischung durch den Launcher (würde Netzverkehr aus dem Launcher auslösen); ContentObserver (wirft ab API 26 bei fehlendem Provider).
- **Neu bewerten, wenn**: Der Nutzer aktuelleres Wetter will: dann der optionale Zwei-Stunden-Plan in `WeatherRefresh` (seit 2026-10-28 wählbar, Standard aus).

## 2026-10-28: Quelltext liegt direkt im Repo, kein Patch gegen das Original mehr
- **Status**: angenommen
- **Kontext**: Das Original (vom00/firestick-tv) hat keine Lizenz; ein öffentliches Gegenstück darf es nicht weitergeben, auch nicht als Patch (der enthält Original-Zeilen als Kontext).
- **Entscheidung**: Alle übernommenen Dateien sind neu geschrieben (Messung: `scripts/check-upstream-overlap.sh`); `launcher/` ist die Quelle, `setup.sh` braucht das Original nicht mehr.
- **Folgen**: Öffentlich möglich, aber nicht rechtlich zugesichert, weil die Neufassung mit Kenntnis des Originals entstand. Eine Lizenzanfrage an den Autor bleibt ratsam.
- **Neu bewerten, wenn**: Der Autor eine Lizenz erteilt.

## 2026-10-28: HomeActivity in Bausteine zerlegt
- **Status**: angenommen
- **Kontext**: 1892 Zeilen in einer Datei bei einer Regel von höchstens 600; Teile waren Originalcode.
- **Entscheidung**: Ansichtenbaum (`HomeScene`) getrennt vom Verhalten (`HomeDock`, `AppGrid`, `HomeBar`, `HomeOverlay`, `HomeScreens`, `HomeBackdrop`, `AppMenus`, `AppCatalog`); die Activity verdrahtet nur.
- **Folgen**: Jeder Baustein unter 500 Zeilen (HomeActivity 484). Die Bausteine kennen sich über `HomeActivity`-Zugriffe (`home.dock()` usw.), nicht über Schnittstellen: bewusst einfach gehalten.
- **Verworfen**: Ein gemeinsamer Zustandsbehälter für alle: hätte die Kopplung nur versteckt.

## 2026-10-28: Eigene Paket-ID `app.calmtv.home` statt `local.tvhome`
- **Status**: angenommen (Wunsch des Nutzers für das öffentliche Projekt)
- **Kontext**: `local.tvhome` war ein Platzhalter; ein öffentliches Projekt braucht eine eindeutige ID.
- **Entscheidung**: Launcher `app.calmtv.home`, Bildschirmschoner `app.calmtv.home.dream`, Provider-Adresse `app.calmtv.home.dream.weather`, Permission `app.calmtv.home.permission.READ_WEATHER`. Quellordner `app/calmtv/home`.
- **Folgen**: Für Android ist es eine andere App: Einstellungen und die Wetter-Stadt der alten Apps gehen nicht mit. Der Umzug am Stick lief über "Sicherung speichern" und "Sicherung laden" (Dock, Layout, Einstellungen); die Stadt und die Bildschirmschoner-Optionen wählt der Nutzer neu. Die ID kommt nicht mehr aus einer Domain des Nutzers, sondern ist ein reiner Name.
- **Neu bewerten, wenn**: Das Projekt eine eigene Domain bekommt.

## 2026-10-28: Fotos als WebP (Qualität 90), JPEG als Rückfall
- **Status**: angenommen
- **Kontext**: Beide APKs trugen je 30 Fotos mit zusammen 25 MB; auf einem Stick mit wenig Speicher viel.
- **Entscheidung**: `setup.sh` erzeugt WebP aus verlustfreien PNGs (ein einziger verlustbehafteter Schritt), wenn `cwebp` vorhanden ist. Gemessen: etwa 45 dB Abstand zum Original, im Ausschnitt in Originalgröße nicht zu unterscheiden; jede APK 17 statt 26 MB.
- **Folgen**: `cwebp` ist eine optionale Abhängigkeit; ohne sie bleibt alles beim JPEG. Eine Qualität von 80 (etwa 39 dB) wurde verworfen, weil Himmelsverläufe zu Stufen neigen.
- **Verworfen**: Fotos nur einmal im Launcher halten und dem Bildschirmschoner über einen Provider geben (spart Speicher auf dem Gerät, koppelt aber den Schoner an den Launcher); weniger Fotos.

## 2026-10-28: Kein Profil-, Szenen- und PIN-System
- **Status**: angenommen (Wunsch des Nutzers)
- **Entscheidung**: Ein Layout für alle; ein Kindermodus ist nur Komfort, keine Sperre. Keine zweite Sprache.
