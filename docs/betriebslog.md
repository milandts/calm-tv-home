# Betriebslog

Fehler mit Symptom, Ursache, Lösung. Neue Einträge oben. Auch Fehler, die auf veraltete Doku zurückgehen.

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
