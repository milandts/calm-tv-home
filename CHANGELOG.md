# Changelog

All notable changes to TV Home. The visible version is in the file `VERSION`; every build also gets a higher internal build number (minutes since 2026-01-01).
Versions 0.1.0 to 1.3.0 were numbered retroactively from the development history (dates are the real commit dates).

## 1.4.0 - 2026-10-29

- Smoother open and close animation of the app grid on the Fire TV Stick (janky frames in the measured sequence 93 % -> about 55 %, median frame time 27 ms -> 17 ms); look and duration unchanged.
- Fixed version number and this changelog; the version is shown in Settings > Info.
- Automatic checks on GitHub for every push, contribution and security notes, issue templates.
- More logic tests (photo rhythm rules, 503 checks in total) and a rhythm test for the device.

## 1.3.0 - 2026-10-28

- Own package ids (`app.calmtv.home`, `app.calmtv.home.dream`); photos as WebP, about the same sharpness at a fraction of the size.
- Rewrite of the home screen into small building blocks (header, bar, dock, grid, overlays, backdrop); source now lives directly in `launcher/`.
- Control Center tiles can be shown, hidden and put first; hint arrow on preview pages.
- Build on macOS and Linux, photo cropping in plain Java, install guide and release script.
- Export script for a public repository with a scan for personal data.

## 1.2.0 - 2026-10-28

- Weather line on the home screen, read from the screensaver through a read-only provider.
- Optional weather refresh every two hours; requests only to Open-Meteo over HTTPS.
- Greeting name is a button in Clock and date.

## 1.1.0 - 2026-10-27

- "More" hint as a pill with fade-out, dock edge hint for apps on the other side, larger sidebar text.
- Layout rules check in the build (`check-rules.sh`), central metrics, edge-case test on the device.
- Idle without stutter (hint arrow animates in 16 steps); dock margin for large focus; glass style reloads after a restore.

## 1.0.0 - 2026-10-26

- Dock scales as a whole and stays centred, safe margin respected in Settings, intro and gallery, double-HOME, dialog focus, dialog width grows with text size.

## 0.5.0 - 2026-10-25

- Settings view with live preview, probation period with "keep" dialog for layout changes, accent colour from the wallpaper, picture gallery, diagnostics, key test, sleep timer.
- Screensaver with weather extras.

## 0.3.0 - 2026-10-25

- Folders, hide, rename, "recently used", appearance settings, backup and restore.
- Own screensaver app with slow photo pan, clock and weather.
- Settings reachable via Amazon's overview; locked direct tiles hidden.

## 0.1.0 - 2026-10-25

- First version: ad-free, tvOS-style home screen for the Amazon Fire TV Stick with greeting, clock, date, daily nature photo, glass dock, full app grid and search; HOME button takeover through an accessibility service.
