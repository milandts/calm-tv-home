# Architecture (short English overview)

The detailed documents are in German ([architektur.md](architektur.md), [DESIGN.md](DESIGN.md)); this page is the map for someone who does not read German.

## What it is

Two Android apps built without Gradle (`aapt2`, `javac` 17, `d8`), signed with the same key:

| App | Package | Role |
|---|---|---|
| Launcher | `app.calmtv.home` | The home screen: clock, daily photo, dock, app grid, settings, Control Center. Declares **no** `INTERNET` permission. |
| Screensaver | `app.calmtv.home.dream` | A `DreamService` with a slow photo pan, clock and optional weather. The only app with `INTERNET`, used only for two Open-Meteo hosts. |

The launcher shows the last weather through a read-only content provider of the screensaver. Access needs a signature permission, so only apps signed with the same key can read it, and the provider never returns a location.

## Taking over the HOME button

Fire OS and Android TV do not let a normal app become the home screen. TV Home uses an accessibility service (`HomeKeyService`) instead:

- It watches which window comes to the front. When the device's own home screen appears, it covers it and starts TV Home. The device home screen is found by resolving the HOME category, not by package name, so it is not tied to one vendor.
- On Fire TV it starts TV Home through an alarm, which avoids Android's five-second start restriction. As a fallback it taps TV Home's own tile in Amazon's home screen, which needs the permission to read window content.
- A guard pauses the takeover for a minute if it fires three times within ten seconds, so the service can never trap the user.
- The service declares window-content access (used only for that fallback tile) and a key filter (used only for the HOME key; all other keys are passed on). It keeps and sends nothing and has no network access. Switching it off in the system's accessibility list restores the device's own home screen at once.

## Layout of the launcher code

`HomeActivity` is the host. Full-screen views (settings, Control Center, introduction) are `Screen`s in their own layer, menus, prompts and toasts sit in a layer above. The home screen itself is split into small parts (`HomeHeader`, `HomeBar`, `HomeDock`, `AppGrid`, `HomeOverlay`, `HomeBackdrop`). All settings are integers with a key in `Opts`, held by `Config`, with a trial layer that allows preview and the "keep changes?" dialog. Screen size is read only in `Ui` and `Metrics`.

## Rules that are checked

- No `INTERNET` in the launcher (Settings > Privacy reads it at run time).
- No anonymous classes (the build tools' `d8` crashes on them): `scripts/check-build.sh` compiles with `d8`.
- Layout rules in `scripts/check-rules.sh`; documentation references in `scripts/check-doc-refs.py`.
- Keys, passwords, screenshots and logs are never committed.

## Where to start

`scripts/check-all.sh` runs every check that needs no device. See [tests/README.md](../tests/README.md) for what is covered and [kompatibilitaet.md](kompatibilitaet.md) for which devices were tested.
