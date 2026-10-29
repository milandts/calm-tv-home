package app.calmtv.home;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Debug;
import android.os.Process;
import android.provider.Settings;

import java.util.List;
import java.util.Locale;

/**
 * Die Seite "Diagnose" (unter System): nur lesen, nichts ändern, nichts senden. Zeigt, was den Stick langsam oder unsicher
 * machen kann (Speicher, Animationen systemweit verändert, ADB-Debugging an), ob Bildschirmschoner und HOME-Dienst eingerichtet
 * sind, und öffnet den Tasten-Test. Alle Werte stammen aus Systemeinstellungen, die jede App lesen darf.
 */
final class SettingsPagesDiagnose {
    private static final String DREAM_PACKAGE = "app.calmtv.home.dream";
    private static final long KILOBYTES_PER_MEGABYTE = 1024;
    /** Die drei Animations-Faktoren des Systems (Entwickleroptionen); 1 ist der Standard. */
    private static final String[] ANIMATION_SCALES = {
            Settings.Global.ANIMATOR_DURATION_SCALE, Settings.Global.TRANSITION_ANIMATION_SCALE, Settings.Global.WINDOW_ANIMATION_SCALE};

    private SettingsPagesDiagnose() {}

    static SettingsModel.Page build(final HomeHost host) {
        SettingsModel.Page page = new SettingsModel.Page("system/diagnose", "Diagnose").icon(Icons.INFO);
        page.add(SettingsPagesRows.info("App-Speicher", "Arbeitsspeicher von TV Home (PSS)", Icons.SPEICHER,
                "ram pss speicher verbrauch", () -> appMemory(host.context())));
        page.add(SettingsPagesRows.info("Android", "Build " + shortBuildId(), Icons.SYSTEM,
                "fire os version android api build", SettingsPagesDiagnose::androidVersion));
        page.add(SettingsPagesRows.info("Animationen im System", "Entwickleroptionen von Amazon", Icons.TEMPO,
                "animation tempo skalierung langsam zeitlupe aus", () -> animations(host.context())));
        page.add(SettingsPagesRows.info("ADB-Debugging", "Erlaubt Steuerung des Sticks über das Netzwerk", Icons.SCHILD,
                "adb debugging usb entwickler sicherheit warnung", () -> adbState(host.context())));
        page.add(SettingsPagesRows.info("Bildschirmschoner", "", Icons.SCHIRMSCHONER,
                "dream screensaver schoner installiert aktiv", () -> screensaverState(host.context())));
        page.add(SettingsPagesRows.info("HOME-Dienst", "Leitet die HOME-Taste auf TV Home um", Icons.DOCK,
                "home taste dienst bedienungshilfen accessibility", () -> homeServiceState(host.context())));
        page.add(SettingsPagesRows.info("Hintergrundbilder", "", Icons.BILD,
                "bilder fotos ausgeblendet galerie anzahl", () -> wallpaperCounts(host)));
        page.add(new SettingsModel.Header("Werkzeuge"));
        page.add(SettingsPagesRows.button("Tasten-Test", "Zeigt, was die Fernbedienung beim Drücken sendet", Icons.TV,
                "tasten test fernbedienung keycode taste", () -> host.openScreen(new KeyTestScreen(host))));
        page.add(new SettingsModel.Note("Diese Seite liest nur Systemwerte. Es werden keine Daten gespeichert oder gesendet."));
        return page;
    }

    /** Die Build-Kennung, auf 24 Zeichen gekürzt (sie kann bei Amazon lang sein). */
    private static String shortBuildId() {
        String id = Build.ID == null ? "" : Build.ID;
        return id.length() > 24 ? id.substring(0, 24) : id;
    }

    /** Gesamt-PSS dieses Prozesses in Kilobyte, derselbe Wert wie "TOTAL PSS" in dumpsys meminfo (mit Grafikspeicher, anders als Debug.getPss). */
    private static String appMemory(Context context) {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        Debug.MemoryInfo[] info = manager.getProcessMemoryInfo(new int[] {Process.myPid()});
        return info[0].getTotalPss() / KILOBYTES_PER_MEGABYTE + " MB";
    }

    private static String androidVersion() {
        return "Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")";
    }

    /** "Normal", "Aus" (alle Faktoren 0) oder der abweichende Wert; abweichend heißt: andere Apps wirken träge oder abgehackt. */
    private static String animations(Context context) {
        float smallest = 1f, largest = 1f;
        for (String name : ANIMATION_SCALES) {
            float scale = Settings.Global.getFloat(context.getContentResolver(), name, 1f);
            smallest = Math.min(smallest, scale);
            largest = Math.max(largest, scale);
        }
        if (smallest == 1f && largest == 1f) return "Normal";
        if (largest == 0f) return "Aus (0), bitte auf 1 stellen";
        String shown = String.format(Locale.GERMANY, "%.1f", largest > 1f ? largest : smallest);
        return "Verändert (" + shown + "), Standard ist 1";
    }

    /** ADB_ENABLED ist ohne Berechtigung lesbar. */
    private static String adbState(Context context) {
        int enabled = Settings.Global.getInt(context.getContentResolver(), Settings.Global.ADB_ENABLED, 0);
        return enabled != 0 ? "An, bitte ausschalten" : "Aus";
    }

    /** Installiert und als Schoner gesetzt (secure screensaver_components nennt das Paket)? */
    private static String screensaverState(Context context) {
        try {
            context.getPackageManager().getPackageInfo(DREAM_PACKAGE, 0);
        } catch (PackageManager.NameNotFoundException e) {
            return "Nicht installiert";
        }
        String components = Settings.Secure.getString(context.getContentResolver(), "screensaver_components");
        boolean isSelected = components != null && components.contains(DREAM_PACKAGE);
        return isSelected ? "Installiert und eingestellt" : "Installiert, aber nicht eingestellt";
    }

    /** Der Accessibility-Dienst steht in enabled_accessibility_services (Komponentenname, durch Doppelpunkt getrennt). */
    private static String homeServiceState(Context context) {
        String enabled = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        ComponentName ours = new ComponentName(context, HomeKeyService.class);
        if (enabled != null) {
            for (String entry : enabled.split(":")) {
                if (ours.equals(ComponentName.unflattenFromString(entry))) return "Aktiv";
            }
        }
        return "Nicht aktiv, HOME öffnet Amazons Startseite";
    }

    /** "30 Bilder, 2 ausgeblendet" (alle Bilder der Sammlung, auch eigene, wenn sie eingebunden sind). */
    private static String wallpaperCounts(HomeHost host) {
        List<Wallpapers.Entry> all = host.wallpapers().list();
        int hidden = 0;
        for (Wallpapers.Entry entry : all) if (entry.hidden) hidden++;
        return all.size() + (all.size() == 1 ? " Bild, " : " Bilder, ") + hidden + " ausgeblendet";
    }
}
