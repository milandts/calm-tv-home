# TV Home

Werbefreier, tvOS-artiger Startbildschirm (Launcher) für den Amazon Fire TV Stick plus ein eigener Bildschirmschoner mit Wetter.
Reines Android ohne Gradle (aapt2, javac 17, d8), Java für beide Apps, Bash und Python für Bau und Tests.
Getestet nur auf Fire TV Stick HD/Lite (AFTSS, Fire OS 7, Android 9, 1080p).

**Bei Fehlern oder Auffälligkeiten zuerst `docs/betriebslog.md` lesen** und neue Fehler dort eintragen, sobald sie gefunden oder gelöst sind.

**Code gewinnt**: Widerspricht eine Aussage hier, in `.claude/rules/` oder in `docs/` dem Code, gilt der Code. Abweichung melden und die Doku im selben Zug
korrigieren. Hier steht nur, was der Code nicht zeigt: Regeln, Gotchas, Entscheidungen samt Grund. `scripts/check-doc-refs.py` prüft Dateiverweise und die `paths`-Muster der Regeln.

**Wo steht was**: Bereichsdetails in `.claude/rules/` (laden bei passenden Dateien, nach `/compact` nicht automatisch neu, darum stehen die bereichsübergreifenden
Gotchas hier). Architektur und Invarianten: `docs/architektur.md`. Gestaltungsregeln: `docs/DESIGN.md`. Messwerte und gescheiterte Versuche: `docs/NOTES.md`.
Entscheidungen: `docs/entscheidungen.md`. Verzeichnis: `docs/README.md`.

## Befehle
- `scripts/check-all.sh`: alle Prüfungen ohne Gerät (Übersetzung beider Apps mit javac und d8, Layout-Regeln, Logik-Tests `scripts/test-unit.sh`, Doku-Verweise); immer vor einem Commit
- `scripts/check-build.sh launcher|dream`: nur die schnelle Übersetzung plus Layout-Regeln (`scripts/check-rules.sh`)
- `scripts/setup.sh`: komplett neu bauen (Fotos laden, beide APKs nach `out/`); `scripts/make-release.sh` packt daraus die Dateien für ein GitHub-Release (APKs, Prüfsummen, Bildnachweise, Anleitung)
- `scripts/build.sh launcher out` und `scripts/build-dream.sh`: nur bauen
- `ANDROID_SERIAL=<IP>:5555 scripts/install.sh` und `scripts/install-dream.sh`: auf den Stick installieren; `scripts/rebind.sh` bindet den HOME-Dienst neu
- Gerätetests (alle brauchen `ANDROID_SERIAL`, sichern und stellen die Einstellungen des Nutzers wieder her): `scripts/test-matrix.py` (Randfälle), `scripts/test-settings.py` (jede deklarierte Einstellungszeile ist sichtbar), `scripts/test-dock.py` (Dock mit 10 Apps), `scripts/test-rhythm.py` (Bildwechsel, verschiebt nur die Zeitzone), `scripts/test-screens.py` (720p und 4K, vom Stick nur vorgetäuscht), `scripts/snapshot-ui.py speichern|vergleichen <Datei>` (Oberfläche vor und nach einem Umbau)
- `scripts/check-upstream-overlap.sh` (braucht einen lokalen Klon des Originals in `work/upstream`): Herkunft prüfen

## Gotchas
- **D8 (Build-Tools 33) stürzt bei anonymen Klassen ab**, auch in Methoden (`TextWatcher`, `ConstantState`): Lambdas oder benannte statische Klassen. Keine `java.util.function`-Typen (minSdk 22).
- **Nach jeder Installation den HOME-Dienst neu binden** (`install.sh` macht es), sonst bleibt HOME bei Amazons Startseite. Auch ein `uiautomator dump` kann den Dienst aus dem Tritt bringen: dann `scripts/rebind.sh`.
- **Am Stick nie blind Tasten senden**: BACK und HOME verlassen die Ansicht, ein Druck auf Auswahl kann eine fremde App starten. Vorher Fokus prüfen (`uiautomator`), nur eigene Oberflächen anfassen. Fenster fremder Apps (zum Beispiel Spotify) gehören dem Nutzer.
- **Tests dürfen Einstellungen des Nutzers nicht überschreiben**: `test-matrix.py` sichert sie am Anfang über "Sicherung speichern" und stellt sie am Ende wieder her. Eigene Testskripte genauso behandeln.
- Nach Tests **ADB-Debugging am Stick ausschalten** lassen. Die Systemuhr lässt sich per ADB nicht stellen (nur die Zeitzone: `cmd alarm set-timezone`).
- **Einstellungs-Seiten mit `.preview()`** zeigen nur Wahl-, Regler-, Schalterzeilen, Knöpfe und Hinweise. Links und Überschriften fehlen dort still; `check-rules.sh` Regel 2 verbietet sie.
- **Kein `INTERNET` im Launcher.** Netz nur im Bildschirmschoner (`dream/`), und dort nur über `Weather.getJson` zu zwei Open-Meteo-Hosts.
- Den Signaturschlüssel (`~/.config/firestick-tvhome`) nie ins Repo und nie verlieren: ohne ihn lässt sich TV Home nicht über die installierte Version installieren.
- Die Wetter-Stadt wählt der Nutzer selbst am Gerät. Nie eine Stadt raten, setzen oder ins Repo schreiben.

## Konventionen
- Version: die sichtbare Nummer steht in `VERSION` (von Hand, bei einer Veröffentlichung erhöhen) und in `CHANGELOG.md`; die interne Build-Nummer zählt `scripts/build.sh` selbst hoch.
- Oberflächentexte und Kommentare auf Deutsch, duzend, ohne Ausrufezeichen und Emojis; Dateien unter 600 Zeilen; Maße zentral in `Metrics` und `Ui`.
- Commits: `<Art>: <Beschreibung>` (feat, fix, refactor, docs, test, chore, perf); der Git-Autor ist bewusst neutral (`tvhome <noreply@invalid>`).
- Das Repo ist privat; ein öffentliches Gegenstück entsteht erst nach der Prüfung in `docs/NOTES.md` ("Eigenständigkeit").

## Sicherheit
- Zugangsdaten, Schlüssel und personenbezogene Daten nie in Dateien, Doku oder Kontext. Screenshots und Logs bleiben aus dem Repo (`.gitignore`).
