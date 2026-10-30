# TV Home (calm-tv-home)

An ad-free, tvOS-style home screen for the Amazon Fire TV Stick and for Android TV / Google TV, plus a matching screensaver with weather.
No account, no trackers, and the launcher itself has no internet permission.

> **Status:**
>
> | Device | State |
> |---|---|
> | Fire TV Stick HD/Lite (Fire OS 7, 1080p) | developed and used on a real device |
> | Android TV 14 and Google TV 14 | checked in the Android emulator only (start, HOME button, settings, screensaver preview) |
> | Other Fire TV models, Fire OS 8, Sony, Philips, Shield, Chromecast with Google TV, other screen sizes | not tested; reports welcome |
>
> Use at your own risk; system updates can change how the HOME button behaves.

## What it does

- **Home screen:** greeting, clock and date over a daily nature photo (30 photos, one per day by default), a glass dock with your favourite apps, and a full app grid below it.
- **Dock and grid:** reorder, rename, hide, put apps in folders, "recently used", a "more apps this way" hint at the dock edges.
- **Settings and Control Center:** clock style, glass look, dock position and size, safe margin for TVs with overscan, text size, photo rhythm, backup and restore.
- **Screensaver (separate app):** slow photo pan with clock, date and optional weather from [Open-Meteo](https://open-meteo.com/) for a city you pick yourself.
- **HOME button:** an accessibility service brings TV Home to the front as soon as the device's own home screen (Amazon's, or Google's on Android TV / Google TV) appears. You switch it on once in the TV's accessibility settings; TV Home shows where under Settings > Einrichtung.
- Language: German user interface.

## Privacy

The HOME-button service is an accessibility service: it receives window-change events (which app is in front) to notice the device's own home screen, and opens TV Home then. It does not read screen content, keys or text, keeps nothing and has no network access.


The launcher has **no `INTERNET` permission** (Settings > Privacy shows this live). Only the screensaver app can reach the network, and only for the weather: the city name you type while searching, then the rounded coordinates of the city you chose, to two Open-Meteo hosts over HTTPS. Nothing is collected or logged. The launcher shows the last weather the screensaver fetched through a read-only provider that only apps signed with the same key may query.

## Requirements

- To use it: a Fire TV Stick or an Android TV / Google TV device. Nothing else is needed for the basic setup (see [INSTALL.md](INSTALL.md)). For the Fire TV screensaver and the fastest HOME button you need a computer with ADB; **switch ADB debugging off again when you are done.**
- To build: JDK 17+, Android SDK with build-tools 33 and platform 33, `curl`, `python3`, `zip`, `openssl`, `bash`. macOS and Linux (Windows via WSL; only macOS is tested). The JDK also crops the photos, so nothing else is needed. No Gradle.
- Optional: `cwebp` (`brew install webp` or `apt install webp`). With it the photos are stored as WebP (quality 90, visually identical) and each APK is about 17 MB; without it they stay JPEG and the APKs are about 26 MB.

## Install

Just want to use it? See [INSTALL.md](INSTALL.md): the easy way needs only the TV remote (the free "Downloader" app fetches the APK, then you switch on one accessibility service). A computer with `adb` is only needed for the Fire TV screensaver and as an alternative.

## Build and install

```bash
scripts/setup.sh                                   # downloads the photos, builds out/TVHome.apk and out/TVHomeDream.apk
ANDROID_SERIAL=<ip>:5555 scripts/install.sh        # installs the launcher and binds the HOME-button service
ANDROID_SERIAL=<ip>:5555 scripts/install-dream.sh  # installs the screensaver and sets it as the system screensaver
```

The first build creates a signing key in `~/.config/firestick-tvhome`. Keep it: updates must be signed with the same key.
Choose the weather city on the TV under Settings > Screensaver > Weather. Without a city there is no weather and no network access at all.

## Develop

`scripts/check-all.sh` runs everything that needs no device (compile with javac and d8, layout rules, logic tests, doc references).
Device tests: `scripts/test-matrix.py` (edge cases for safe margin, text size, dock size) and `scripts/snapshot-ui.py` (compares the UI before and after a refactor).
Start with [CLAUDE.md](CLAUDE.md), [docs/architektur.md](docs/architektur.md) and [docs/DESIGN.md](docs/DESIGN.md) (German).

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) and [CHANGELOG.md](CHANGELOG.md). Security issues: [SECURITY.md](SECURITY.md).

## License

The source code is MIT-licensed, see [LICENSE](LICENSE). Fonts, photos and weather data have their own terms, see [THIRD_PARTY.md](THIRD_PARTY.md).
The photos are not part of the repository: `scripts/setup.sh` downloads them from Wikimedia Commons, and each photo keeps its own Creative Commons licence (listed in `wallpapers.json`).
Origin: this project started as a fork of [vom00/firestick-tv](https://github.com/vom00/firestick-tv), which has no license; all files taken from it have since been rewritten.
This is an independent project and is not affiliated with Amazon. "Fire TV" and all app names shown by the launcher belong to their owners.
