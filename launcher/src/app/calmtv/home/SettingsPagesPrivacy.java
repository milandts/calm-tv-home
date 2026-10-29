package app.calmtv.home;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import java.util.List;

/** Die Seite "Datenschutz": reine Information darüber, was TV Home liest und was nicht. */
final class SettingsPagesPrivacy {
    private SettingsPagesPrivacy() {}

    static SettingsModel.Page build(final HomeHost host) {
        SettingsModel.Page page = new SettingsModel.Page("datenschutz", "Datenschutz")
                .subtitle("Was TV Home liest und was nie").icon(Icons.DATENSCHUTZ);
        page.add(SettingsPagesRows.info("Filter", "", Icons.FILTER, "vpn dns werbung tracker dumbtv",
                () -> SettingsPagesRows.filterState(host)));
        page.add(SettingsPagesRows.info("Internetzugriff von TV Home", "", Icons.SCHILD,
                "internet netzwerk berechtigung offline", () -> internetAccess(host.context())));
        page.add(SettingsPagesRows.info("Deaktivierte System-Apps", "", Icons.SCHILD,
                "debloat amazon apps abgeschaltet entfernt", () -> disabledSystemApps(host.context())));
        page.add(new SettingsModel.Header("Was TV Home liest"));
        page.add(new SettingsModel.Note("TV Home liest die Liste der installierten Apps und ob der Filter (VPN) läuft. "
                + "Es sendet nichts und zeichnet nichts auf."));
        page.add(new SettingsModel.Header("Bildschirmschoner"));
        page.add(new SettingsModel.Note("Der Bildschirmschoner sendet den Stadtnamen, wenn du eine Stadt suchst, danach nur "
                + "gerundete Koordinaten. Open-Meteo sieht dabei auch die IP-Adresse deines Anschlusses."));
        return page;
    }

    /** "Keiner", solange TV Home die Berechtigung INTERNET nicht hat (gelesen, nicht angenommen). */
    private static String internetAccess(Context context) {
        int result = context.getPackageManager().checkPermission("android.permission.INTERNET", context.getPackageName());
        return result == PackageManager.PERMISSION_GRANTED ? "Erlaubt" : "Keiner";
    }

    /** Zählt System-Apps, die abgeschaltet sind (enabled == false); entfernte Pakete zählen nicht mit. */
    private static String disabledSystemApps(Context context) {
        List<ApplicationInfo> all = context.getPackageManager()
                .getInstalledApplications(PackageManager.MATCH_DISABLED_COMPONENTS);
        int count = 0;
        for (ApplicationInfo app : all) {
            if (!app.enabled && (app.flags & ApplicationInfo.FLAG_SYSTEM) != 0) count++;
        }
        return String.valueOf(count);
    }
}
