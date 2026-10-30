# Changelog

All notable changes to TV Home. The visible version is in the file `VERSION`; every build also gets a higher internal build number (minutes since 2026-01-01).
Versions 0.1.0 to 1.3.0 were numbered retroactively from the development history (dates are the real commit dates).

## Unreleased

- Fire TV: own on-screen keyboard for typing a name, a folder name or an app name, for the search, and for the weather city search of the screensaver. Amazon's keyboard on the Fire TV Stick covers the whole screen and runs over the bottom edge (space and delete are cut off), and an app cannot make it smaller; with the search field it did not show at all. Android TV and Google TV keep the system keyboard.
- The keyboard has digits, QWERTZ with umlauts, shift, hyphen, space and delete; the first letter of each word is capitalized. In the name dialog, "Speichern" is a key and Back cancels, so everything fits at larger display scales as well (checked at density 320 and 400 on the Fire TV Stick). A connected keyboard still types directly.
- Checked on a Fire TV Stick (AFTSS) and in Android TV / Google TV emulators; the screensaver start on Google TV through idle time could not be checked (needs root).

## 1.5.0 - 2026-10-29

- Android TV and Google TV: the HOME button now also works there. The accessibility service brings TV Home to the front when the device's home screen appears (only that window, not its pop-ups), and pauses for a minute if that happens three times within ten seconds. Checked in Android 14 emulator images (Android TV, Google TV) only, not on real hardware.
- New settings page "Einrichtung": shows whether the HOME button and the screensaver are set up and opens the right system page, so the basic setup may need no computer. Not yet tried on real hardware, and newer Android versions may block the install-from-file route (see INSTALL.md).
- Install guide rewritten: the easy way on the TV with the "Downloader" app comes first, the `adb` way second.
- On other devices than Fire TV the "Kein Filter" hint is hidden unless a VPN is active; Google's YouTube app is in the first dock; Play Store and Chrome can appear in the app grid.

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
