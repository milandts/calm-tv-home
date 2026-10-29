# Installing TV Home from a laptop

You need a laptop with `adb` (Android platform-tools) and the two APK files (`TVHome.apk`, `TVHomeDream.apk`) from the release page, or built yourself with `scripts/setup.sh`. Works the same on macOS, Linux and Windows, because these are plain `adb` commands.

> TV Home replaces what your HOME button opens. Amazon updates can change that behaviour. Everything below can be undone (see "Remove it again").

## 1. Prepare the Fire TV Stick

1. Settings > My Fire TV > About: select the device name seven times. Developer options appear.
2. Settings > My Fire TV > Developer options: switch **ADB debugging** on.
3. Note the IP address (Settings > My Fire TV > About > Network). The laptop and the stick must be on the same Wi-Fi.

## 2. Connect and install

```bash
adb connect <ip>:5555          # accept the prompt on the TV ("Always allow")
adb install -r TVHome.apk
adb install -r TVHomeDream.apk
```

## 3. Make the HOME button open TV Home

TV Home does this with an accessibility service. First look at what is already enabled so you do not overwrite it:

```bash
adb shell settings get secure enabled_accessibility_services
```

If it prints `null`, the list is empty:

```bash
adb shell settings put secure enabled_accessibility_services app.calmtv.home/app.calmtv.home.HomeKeyService
```

If it prints something, append ours after a colon, for example `existing/Service:app.calmtv.home/app.calmtv.home.HomeKeyService`. Then:

```bash
adb shell settings put secure accessibility_enabled 1
adb shell settings put global alarm_manager_constants min_futurity=0   # lets the HOME button start TV Home at once
adb shell am start -n app.calmtv.home/.HomeActivity
```

If HOME still opens Amazon's home screen after a reinstall, enable the service once with another value first (any other accessibility service id), wait two seconds, then set ours again. `scripts/install.sh` does exactly that for you if you have the repository.

## 4. Screensaver (optional)

```bash
adb shell settings put secure screensaver_components app.calmtv.home.dream/.AmbientDream
adb shell settings put secure screensaver_enabled 1
```

Pick the weather city on the TV under Settings > Screensaver > Weather. Without a city there is no weather and no network access at all.

## 5. Switch ADB debugging off again

Settings > My Fire TV > Developer options > ADB debugging: off. TV Home does not need it to run.

## Remove it again

```bash
adb shell settings put secure enabled_accessibility_services ""        # or your previous value
adb shell settings put secure screensaver_components com.amazon.ftv.screensaver/.app.services.ScreensaverService
adb shell settings delete global alarm_manager_constants
adb uninstall app.calmtv.home
adb uninstall app.calmtv.home.dream
```

## Updates

An update must be signed with the same key as the installed version. Installing a build you made yourself over a downloaded one fails with a signature error: uninstall the old one first (your TV Home settings are lost, use Settings > Backup before).
