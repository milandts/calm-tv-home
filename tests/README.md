# Tests

Two kinds of checks, both without Gradle.

**Logic tests (no device):** `scripts/test-unit.sh` compiles the launcher with `javac` and runs `UnitTests` on the JVM (530 checks at the time of writing). They cover pure logic only, no Android calls:

- on-screen keyboard rules (layout, shift, capitalisation)
- device detection (Fire TV or other Android TV) and the takeover guard that pauses the HOME-button service after repeated takeovers
- every setting in `Opts`: complete, with a default inside its range
- corrupt stored gallery values must never crash
- photo rhythm (when the picture changes, which one is next)
- settings search (hits, umlauts, order)

`scripts/check-all.sh` runs these together with the compile checks of both apps, the layout rules (`scripts/check-rules.sh`) and the documentation reference check. The same command runs on GitHub for every push and pull request.

**Device tests (need a stick or emulator with ADB):** `scripts/test-matrix.py`, `test-settings.py`, `test-dock.py`, `test-rhythm.py`, `test-screens.py` and `snapshot-ui.py`. They read the screen through `uiautomator` and press keys, so they must not run on a device that someone is using. Each one saves the user's settings first and restores them afterwards. Which device was checked how is listed in [docs/kompatibilitaet.md](../docs/kompatibilitaet.md).

Not covered by any automatic test: the screensaver start through idle time, the install through the "Downloader" app, and anything on real Android TV or Google TV hardware.
