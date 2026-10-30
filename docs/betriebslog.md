# Betriebslog

Fehler mit Symptom, Ursache, Lösung. Neue Einträge oben. Auch Fehler, die auf veraltete Doku zurückgehen.

## 2026-10-30: Tastatur bei der Namenseingabe auf dem Stick zu groß
- **Ursache**: Amazons Tastatur (`com.amazon.tv.ime`) füllt am Stick (AFTSS, 1080p, Dichte 320) ein Fenster von 1920x1080 und läuft unten über den Rand (Leerzeichen, Löschen abgeschnitten). Auch ein einfaches Textfeld der Bildschirmschoner-App zeigt sie so; die App kann sie nicht verkleinern, `IME_FLAG_NO_FULLSCREEN` und `NO_EXTRACT_UI` ändern nichts. Mit `SHOW_IMPLICIT` bleibt ihr Fenster am Stick unsichtbar (Suchfeld), darum ruft `GlassPrompt` mit `SHOW_FORCED`.
- **Lösung**: Auf Fire TV tippt man mit eigenen Tastaturen: `GlassPrompt` (Namen, Ordner) und die Suche der Startseite nutzen `GlassKeyboard` über `KeyboardInput` (Regeln in `KeyboardLayout`, geprüft in `UnitTests`); die Stadtsuche des Bildschirmschoners hat `SetupKeyboard` (eigene App, eigene Fassung derselben Regeln: bei einer Änderung beide anpassen). Android TV und Google TV behalten die Systemtastatur (Gboard ist dort normal groß, geprüft im Emulator bei Dichte 320, 240 und 720p).
- **Platz**: Das Panel ist auf 80 % der Höhe begrenzt (`GlassMenu.HEIGHT_SHARE`); bei Dichte 400 (432 dp) fielen "Abbrechen" und "Speichern" weg. Darum hat die Tastatur im Dialog keine Knopfzeile: "Speichern" (bzw. "Anlegen") ist eine Taste in der unteren Zeile, Zurück bricht ab, und die Tastenhöhe richtet sich nach dem Platz (`GlassKeyboard.keyHeightFor`, 24 bis 34 dp).
- **Fallstrick**: `ScrollView` schneidet mit `clipToPadding` nur ab, wenn der Innenabstand ungleich null ist; die Suche setzt darum 1 px unten, sonst ragen die Ergebnisse unter die Tastatur.
- **Wo prüfen**: `GlassPrompt`, `HomeOverlay.addSearchKeyboard`, `GlassKeyboard`, `KeyboardLayout`, `SetupKeyboard`
- **Doku betroffen?**: ja, `docs/NOTES.md`

## 2026-10-28: "Name für den Gruß" war in den Einstellungen nicht zu finden
- **Ursache**: Die Seite "Uhr und Datum" ist eine Vorschau-Seite, die nur Wahl-, Regler- und Schalterzeilen, Knöpfe und Hinweise zeigt; der Link auf die Unterseite und eine Überschrift fielen still weg. Kein Test verglich Deklariertes mit Sichtbarem.
- **Lösung**: Knopf "Name …" neben dem Titel (`SettingsPagesLook.clock`); `scripts/check-rules.sh` Regel 2 verbietet Links und Überschriften auf Vorschau-Seiten.
- **Wo prüfen**: `scripts/check-rules.sh`, `SettingsPreview` (Log "preview page can't show row")
- **Doku betroffen?**: ja, Regel in `docs/architektur.md` und `.claude/rules/launcher.md`

## 2026-10-28: Gerätetests überschrieben Einstellungen des Nutzers
- **Ursache**: Die Matrix und der Bildwechsel-Test stellten am Ende alle Testschlüssel auf Standard, auch Werte, die der Nutzer selbst gewählt hatte.
- **Lösung**: `test-matrix.py` sichert vorher über "Sicherung speichern" und stellt wieder her (`save_user_state`, `restore_user_state`). Wird ein Lauf hart abgebrochen, geht die Kopie verloren: dann die Werte von Hand zurückstellen.
- **Wo prüfen**: `scripts/test-matrix.py`
- **Doku betroffen?**: ja, `CLAUDE.md` (Gotchas)

## 2026-10-28: HOME blieb bei Amazons Startseite, Tests scheiterten ohne erkennbaren Grund
- **Ursache**: `uiautomator dump` scheitert gelegentlich und kann den HOME-Dienst (Bedienungshilfe) aus dem Tritt bringen; außerdem kam die erste Richtungstaste nach HOME manchmal zu früh an.
- **Lösung**: `scripts/rebind.sh`; Tests wiederholen den Dump und prüfen den Fokus vor dem Weiterdrücken (`nodes`, `to_bar`, `snapshot-ui.home`).
- **Wo prüfen**: `scripts/test-matrix.py`, `scripts/snapshot-ui.py`
- **Doku betroffen?**: ja, `CLAUDE.md` und `.claude/rules/scripts.md`

## 2026-10-27: Weiße, unlesbare Leistenkapseln nach "Sicherung laden"
- **Ursache**: `GlassStyle` registrierte seine Überwachung nur an der ersten `Config` und las nach dem Neuaufbau der Activity die alten Werte; zusätzlich war eine Helligkeit über 90 % nicht mehr lesbar.
- **Lösung**: `GlassStyle.registeredWith` merkt sich die Config, `Config.reloaded()` nach der Sicherung, Helligkeit höchstens 90 %.
- **Wo prüfen**: `GlassStyle`, `HomeActivity.restoreBackup`
- **Doku betroffen?**: ja, `.claude/rules/launcher.md`
