package app.calmtv.home;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.os.SystemClock;

import java.util.ArrayList;
import java.util.List;

/** Die Seite "Info" mit Gerätedaten, Einführung, Bildnachweisen und Lizenzen. */
final class SettingsPagesInfo {
    private SettingsPagesInfo() {}

    static SettingsModel.Page build(final HomeHost host) {
        SettingsModel.Page page = new SettingsModel.Page("info", "Info")
                .subtitle("Version, Gerät, Bildnachweise und Lizenzen").icon(Icons.INFO);
        page.add(SettingsPagesRows.info("Version", buildSubtitle(host.context()), Icons.INFO, "tv home build",
                () -> versionNumber(host.context())));
        page.add(SettingsPagesRows.info("Gerät", "", Icons.TV, "modell hersteller fire tv stick aftss",
                () -> SettingsPagesFormat.deviceName(Build.MANUFACTURER, Build.MODEL)));
        page.add(SettingsPagesRows.info("Fire OS", "Android " + Build.VERSION.RELEASE + " · Build " + shortBuildId(),
                Icons.SYSTEM, "android betriebssystem sicherheitspatch sicherheitsstand",
                () -> SettingsPagesFormat.securityPatch(Build.VERSION.SECURITY_PATCH)));
        page.add(SettingsPagesRows.info("Freier Speicher", "", Icons.SPEICHER, "speicherplatz platz festplatte",
                SettingsPagesInfo::freeStorage));
        page.add(SettingsPagesRows.info("Arbeitsspeicher", "", Icons.SPEICHER, "ram speicher frei",
                () -> memory(host.context())));
        page.add(SettingsPagesRows.info("Laufzeit", "Seit dem letzten Start des Geräts", Icons.LAUFZEIT,
                "uptime betriebszeit neustart", () -> SettingsPagesFormat.uptime(SystemClock.elapsedRealtime())));
        page.add(SettingsPagesRows.button("Einführung wiederholen", "Zeigt die Willkommensseiten erneut", Icons.STERN,
                "willkommen tutorial anleitung hilfe erklaerung", host::showWelcome));
        page.add(SettingsPagesRows.link("Bildnachweise", "Wer die Hintergrundfotos gemacht hat", Icons.BILD,
                "foto fotos urheber wikimedia lizenz autor", credits(host)));
        page.add(SettingsPagesRows.link("Lizenzen", "Schrift, Fotos und Wetterdaten", Icons.INFO,
                "open source nunito open-meteo wikimedia", licenses()));
        page.add(new SettingsModel.Header("Für Fortgeschrittene"));
        page.add(SettingsPagesRows.link("Technische Details", "Ablageort der Sicherung", Icons.SPEICHER,
                "adb pull pfad sicherung datei entwickler", technicalDetails()));
        return page;
    }

    /** Der Versionsname aus der Datei VERSION, z. B. "1.0.0". */
    private static String versionNumber(Context context) {
        try {
            String name = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
            return name == null || name.isEmpty() ? SettingsPagesRows.UNKNOWN : name;
        } catch (PackageManager.NameNotFoundException e) {
            return SettingsPagesRows.UNKNOWN;
        }
    }

    /** "Build 433330" aus dem Versionscode (Minuten seit Jahresbeginn 2026, jede Fassung hat einen höheren). */
    private static String buildSubtitle(Context context) {
        try {
            return "Build " + context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            return "";
        }
    }

    /** Die Build-Kennung, auf 24 Zeichen gekürzt (sie kann bei Amazon lang sein). */
    private static String shortBuildId() {
        String id = Build.ID == null ? "" : Build.ID;
        return id.length() > 24 ? id.substring(0, 24) : id;
    }

    private static String freeStorage() {
        StatFs stat = new StatFs(Environment.getDataDirectory().getPath());
        return SettingsPagesFormat.gigabytes(stat.getAvailableBytes()) + " von "
                + SettingsPagesFormat.gigabytes(stat.getTotalBytes());
    }

    private static String memory(Context context) {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
        manager.getMemoryInfo(info);
        return SettingsPagesFormat.megabytes(info.availMem) + " frei von " + SettingsPagesFormat.megabytes(info.totalMem);
    }

    // ---- Bildnachweise und Lizenzen --------------------------------------------------------------

    private static SettingsModel.Page credits(final HomeHost host) {
        SettingsModel.Page page = new SettingsModel.Page("info/bildnachweise", "Bildnachweise").icon(Icons.BILD);
        page.dynamicRows = () -> creditRows(host);
        return page;
    }

    private static List<SettingsModel.Row> creditRows(HomeHost host) {
        List<SettingsModel.Row> rows = new ArrayList<>();
        String current = host.currentWallpaperCredit();
        if (current != null && !current.isEmpty()) {
            rows.add(new SettingsModel.Note("Aktuell zu sehen: " + current.replace(" · Photo: ", " · Foto: ")));
        }
        List<String> all = host.wallpaperCredits();
        if (all != null && !all.isEmpty()) {
            rows.add(new SettingsModel.Header("Naturfotos von Wikimedia Commons"));
            for (String credit : all) rows.add(new SettingsModel.Note(credit.replace(" · Photo: ", " · Foto: ")));
        }
        if (rows.isEmpty()) rows.add(new SettingsModel.Note("Keine Bildnachweise vorhanden."));
        return rows;
    }

    private static SettingsModel.Page licenses() {
        SettingsModel.Page page = new SettingsModel.Page("info/lizenzen", "Lizenzen").icon(Icons.INFO);
        page.add(new SettingsModel.Note("Nunito: SIL Open Font License 1.1"));
        page.add(new SettingsModel.Note("Naturfotos: Wikimedia Commons, Lizenz je Bild unter Bildnachweise"));
        page.add(new SettingsModel.Note("Wetterdaten: Open-Meteo.com (CC BY 4.0)"));
        page.add(new SettingsModel.Note("TV Home basiert auf vom00/firestick-tv"));
        return page;
    }

    private static SettingsModel.Page technicalDetails() {
        SettingsModel.Page page = new SettingsModel.Page("info/technik", "Technische Details").icon(Icons.SPEICHER);
        page.add(new SettingsModel.Note("Ablageort der Sicherung: /sdcard/Android/data/app.calmtv.home/files/" + Backup.FILE_NAME));
        page.add(new SettingsModel.Note("Zum Kopieren auf den Computer: adb pull /sdcard/Android/data/app.calmtv.home/files/"
                + Backup.FILE_NAME));
        return page;
    }
}
