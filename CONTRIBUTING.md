# Contributing

Thanks for looking. TV Home is a small project and is only tested on one device (Fire TV Stick HD/Lite "AFTSS", Fire OS 7). Reports from other devices are very welcome.

## Reporting a problem

Open an issue and say which Fire TV model and Fire OS version you use, what you did, what you expected and what happened. A photo of the screen helps. Do not post your IP address or account names.

## Changing code

1. Read [CLAUDE.md](CLAUDE.md) (commands and gotchas) and [docs/architektur.md](docs/architektur.md). The documents are in German, the code comments too; English questions in issues are fine.
2. Build: JDK 17 and the Android SDK with build-tools 33 and platform 33, no Gradle. `scripts/check-all.sh` runs everything that needs no device. Please run it before you open a pull request; the same check runs on GitHub.
3. The build tools have a bug with **anonymous classes**: use lambdas or named static classes.
4. The launcher must keep **no internet permission**. Network access exists only in the screensaver app and only to the two Open-Meteo hosts in `Weather.getJson`.
5. If your change touches the UI, run the device tests you can (`scripts/test-matrix.py --quick`, `scripts/test-settings.py`) and say which ones you ran. They need a stick with ADB debugging; they back up and restore your settings.
6. Commit messages: `<type>: <description>` with type `feat`, `fix`, `refactor`, `docs`, `test`, `chore` or `perf`.

## Licence

By contributing you agree that your contribution is released under the MIT licence of this project. Do not add code or images you did not write or do not have the right to share.
