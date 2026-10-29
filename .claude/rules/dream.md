---
paths:
  - "dream/**"
---

# Bereich: Bildschirmschoner

Pfadgebundene Regel: lädt, sobald eine Datei im Bildschirmschoner gelesen oder bearbeitet wird. Bei Widerspruch zum Code gilt der Code.

## Invarianten
- Einzige App mit `INTERNET`. Jeder Abruf geht durch `Weather.getJson` (HTTPS, zwei Hosts, keine Weiterleitungen); Netzcode anderswo ist ein Fehler.
- Es gibt kein Wetter und keinen Netzverkehr, solange der Nutzer keine Stadt gewählt hat (`Weather.isConfigured`). Die Stadt wählt nur der Nutzer am Gerät und steht nie im Repo.
- `WeatherProvider` gibt Temperatur, Text und Zeit heraus, nie Ort oder Koordinaten, und holt nie selbst etwas aus dem Netz.

## Gotchas
- Der optionale Hintergrundabruf (`WeatherRefresh`, Auswahl "Alle 2 Stunden") ist ein nicht persistenter JobScheduler-Plan; nach einem Neustart legt ihn `WeatherProvider.query` neu an, sobald TV Home fragt. `isWanted` prüft Auswahl, Wetter-Schalter und Stadt vor jedem Abruf.
- Die Fotos kommen beim Bau aus `launcher/assets` (`scripts/build-dream.sh`); die Schrift ebenso.
- Der Bildschirmschoner wird über `scripts/install-dream.sh` als System-Schoner gesetzt (`screensaver_components`).
