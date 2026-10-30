# Installing TV Home

TV Home replaces what your HOME button opens. It is a normal app plus one accessibility switch; nothing is rooted or hacked, and everything can be undone (see "Remove it again").

| Device | State |
|---|---|
| Fire TV Stick HD/Lite (Fire OS 7) | Tested on a real device |
| Android TV 14 and Google TV 14 | Tested in Android 14 emulator images only (no real device, and not the install through Downloader) |
| Other Fire TV models, Fire OS 8, Sony, Philips, Nvidia Shield, Chromecast with Google TV and so on | Not tested, feedback welcome |

The interface is in German.

## The easy way: on the TV, no computer

You need the TV remote and about ten minutes.

**1. Get the "Downloader" app.** Fire TV: search for *Downloader* in the Appstore. Android TV / Google TV: search in the Play Store. It is a free, orange app that downloads a file from a web address.

**2. Allow Downloader to install apps.**
- Fire TV: Settings > My Fire TV > Developer options > *Install unknown apps* > switch **Downloader** on. If "Developer options" is missing: Settings > My Fire TV > About, select the device name seven times.
- Android TV / Google TV: the TV asks the first time Downloader wants to install something. Choose *Settings* and allow it.

**3. Download TV Home.** Open Downloader, select the address field and type exactly:

```
github.com/milandts/calm-tv-home/releases/latest/download/TVHome.apk
```

Select *Go*, wait for the download, then select **Install**. If the TV warns that the app is unknown or from outside the store, choose *Install anyway* (the app is open source; you can compare the checksum in `SHA256SUMS` on the release page). When it says *Done*, select **Delete** to free the space, then open TV Home.

**4. Turn on the HOME button.** In TV Home: Settings > **Einrichtung** > **Dienst einschalten**. The TV's accessibility page opens. Scroll down to **Services**, select **Home-Taste für TV Home**, switch *Enable* on and confirm the question. Go back and press HOME on the remote: TV Home appears. The line "HOME-Taste" on the Einrichtung page now says *Aktiv*.

> Not yet verified on real hardware: (1) on a Fire TV Stick, whether *Home-Taste für TV Home* shows up in its accessibility page (the list was only checked on Android TV); (2) on newer Android versions (13 and later) the system may grey out the switch for an app that was installed from a file instead of a store ("restricted settings"). If either happens, use "With a computer" below. The check could not be done in the emulator.
>
> What the service does: it only watches which window comes to the front, to notice the device's own home screen, and then opens TV Home. It reads no text, sends nothing anywhere and has no network access. Switching it off in the same list gives you the device's own home screen back at once.

That is all that is needed. Two optional extras:

- **Screensaver with weather** (a second file). Repeat step 3 with `TVHomeDream.apk` instead of `TVHome.apk`. On Android TV / Google TV the device may let you choose it in its own settings (usually under Device Preferences > Screen saver); whether it is offered depends on the device, and on Google TV it is often not, so plan on the computer path below. On Fire TV, Amazon hides that choice, so the screensaver always needs the computer path. Then pick your city in Settings > Bildschirmschoner > Wetter einrichten (without a city there is no weather and no network access).
- **Faster HOME on Fire TV** needs the computer path as well (one setting). Without it HOME still works, a little slower.

**If something does not work**

- *HOME still opens the device's own home screen.* Switch the TV Home service off and on again in the accessibility page, once after every update. Fire TV also needs this after "Force stop".
- *On Google TV the old home screen flashes for a moment.* That is how it works: TV Home is brought to the front as soon as the device's home screen appears. It cannot be avoided without removing Google's home screen.
- *Something else broke.* Settings > System > Diagnose shows what TV Home sees.

## With a computer (advanced)

You need `adb` (Android platform-tools) and the APK files from the release page, or built yourself with `scripts/setup.sh`. The commands are the same on macOS, Linux and Windows.

### 1. Prepare the device

Fire TV: Settings > My Fire TV > About: select the device name seven times; then Developer options > **ADB debugging** on. Note the IP address (About > Network). Android TV / Google TV: the same idea; Developer options appear after selecting *Build* seven times under Settings > Device Preferences > About. The computer and the TV must be on the same Wi-Fi.

### 2. Connect and install

```bash
adb connect <ip>:5555          # accept the prompt on the TV ("Always allow")
adb install -r TVHome.apk
adb install -r TVHomeDream.apk # optional
```

### 3. Make the HOME button open TV Home

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
adb shell settings put global alarm_manager_constants min_futurity=0   # Fire TV only: lets the HOME button start TV Home at once
adb shell am start -n app.calmtv.home/.HomeActivity
```

If HOME still opens the device's home screen after a reinstall, enable the service with another value first (any other accessibility service id), wait two seconds, then set ours again. `scripts/install.sh` does exactly that for you if you have the repository.

### 4. Screensaver (optional, needed on Fire TV)

```bash
adb shell settings put secure screensaver_components app.calmtv.home.dream/.AmbientDream
adb shell settings put secure screensaver_enabled 1
```

Pick the weather city on the TV under Settings > Bildschirmschoner > Wetter einrichten.

### 5. Switch ADB debugging off again

TV Home does not need it to run.

## Remove it again

On the TV: switch the TV Home service off in the accessibility page, then uninstall TV Home (and the screensaver) in the apps list. With a computer:

```bash
adb shell settings put secure enabled_accessibility_services ""        # or your previous value
adb shell settings put secure screensaver_components com.amazon.ftv.screensaver/.app.services.ScreensaverService   # Fire TV; on Android TV choose another screensaver in the system settings
adb shell settings delete global alarm_manager_constants
adb uninstall app.calmtv.home
adb uninstall app.calmtv.home.dream
```

## Updates

An update must be signed with the same key as the installed version. Installing a build you made yourself over a downloaded one fails with a signature error: uninstall the old one first (your TV Home settings are lost, use Settings > Sicherung before). After an update, switch the service off and on again in the accessibility page if HOME no longer opens TV Home.
