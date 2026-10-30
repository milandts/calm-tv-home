package app.calmtv.home;

import android.content.Context;
import android.content.Intent;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Baut die Seiten der Einstellungen als Daten ({@link SettingsModel}); gezeichnet werden sie von {@code SettingsScreen}.
 * Alle Werte stehen in Value-Lambdas und werden erst beim Zeichnen gelesen. Apps, Datenschutz und Info liegen in
 * den Hilfsklassen {@code SettingsPagesApps}, {@code SettingsPagesPrivacy} und {@code SettingsPagesInfo}.
 */
final class SettingsPages {
    private static final String DREAM_PACKAGE = "app.calmtv.home.dream";
    private static final String ACTION_CONTROLLERS = "com.amazon.device.settings.action.CONTROLLERS";
    private static final String ACTION_APPLICATIONS = "com.amazon.device.settings.action.APPLICATIONS";
    private static final String ACTION_DEVICE = "com.amazon.device.settings.action.DEVICE";
    private static final String SPEEDTEST_URL = "https://fast.com";

    private SettingsPages() {}

    /** Die obersten Seiten (Kategorien) in der Reihenfolge der linken Spalte. */
    static List<SettingsModel.Page> build(HomeHost host) {
        List<SettingsModel.Page> categories = new ArrayList<>();
        categories.add(SettingsPagesSetup.build(host));
        categories.add(SettingsPagesLook.build(host));
        categories.add(SettingsPagesApps.build(host));
        categories.add(screensaver(host));
        categories.add(connections(host));
        categories.add(system(host));
        categories.add(SettingsPagesLook.accessibility());
        categories.add(SettingsPagesPrivacy.build(host));
        categories.add(backup(host));
        categories.add(SettingsPagesInfo.build(host));
        return categories;
    }

    // ---- Bildschirmschoner -----------------------------------------------------------------------

    private static SettingsModel.Page screensaver(final HomeHost host) {
        SettingsModel.Page page = new SettingsModel.Page("bildschirmschoner", "Bildschirmschoner")
                .subtitle("Starten, Wetter und Startzeit").icon(Icons.SCHIRMSCHONER);
        page.add(SettingsPagesRows.info("Startet nach", "", Icons.LAUFZEIT, "zeit minuten leerlauf timeout standby",
                () -> screenOffTimeout(host.context())));
        page.add(SettingsPagesRows.button("Startzeit ändern", "Öffnet die Systemeinstellungen", Icons.EINSTELLUNGEN,
                "zeit minuten leerlauf timeout", host::openAmazonOverview));
        page.add(new SettingsModel.Note("Die Startzeit stellst du in den Systemeinstellungen ein (bei Fire TV unter Töne und Bildschirm)."));

        final Intent preview = dreamIntent("PreviewActivity");
        if (host.canStart(preview)) {
            page.add(SettingsPagesRows.button("Jetzt starten", "Zeigt den Bildschirmschoner sofort", Icons.SCHIRMSCHONER,
                    "vorschau test screensaver bildschirmschoner", SettingsPagesRows.starter(host, preview)));
            page.add(SettingsPagesRows.button("Wetter einrichten", "Wähle deine Stadt für die Wetteranzeige", Icons.WETTER,
                    "wetter stadt ort temperatur open-meteo", () -> {
                        Intent setup = dreamIntent("SetupActivity")
                                .putExtra("accent", Look.accentColor(host.look().accentIndex()));
                        host.start(setup);
                    }));
        } else {
            page.add(new SettingsModel.Note("Der Bildschirmschoner ist nicht installiert."));
        }
        page.add(new SettingsModel.Header("Einschlaf-Timer"));
        page.add(SettingsPagesRows.choice("Dauer", "Gilt einmal und endet beim Neustart des Geräts", Icons.MOND,
                "schlafen timer einschlafen ausschalten ruhezustand abschalten minuten",
                Arrays.asList(SleepTimer.LABELS), () -> SleepTimer.selectedIndex(host.context()),
                index -> SleepTimer.set(host.context(), index)));
        page.add(SettingsPagesRows.info("Restzeit", "", Icons.LAUFZEIT, "timer einschlafen rest",
                () -> SleepTimer.describeRemaining(host.context())));
        page.add(SettingsPagesRows.opt("Nach dem Timer", "Ruhezustand schaltet nur den Bildschirm aus", Icons.MOND,
                "schlafen timer ruhezustand bildschirmschoner standby", Opts.SLEEP_ACTION));
        page.add(new SettingsModel.Header("Datenschutz"));
        page.add(new SettingsModel.Note("Nur der Bildschirmschoner nutzt das Internet, und nur für das Wetter (Open-Meteo). "
                + "TV Home selbst hat keine Internetberechtigung und liest nur den letzten Wetterstand des Bildschirmschoners (Temperatur und Text, ohne Ort)."));
        return page;
    }

    private static Intent dreamIntent(String activity) {
        return new Intent().setClassName(DREAM_PACKAGE, DREAM_PACKAGE + "." + activity)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    /** Die Zeit bis zum Bildschirmschoner aus den Systemeinstellungen (nur gelesen). */
    private static String screenOffTimeout(Context context) {
        try {
            int millis = Settings.System.getInt(context.getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT);
            return SettingsPagesFormat.timeout(millis);
        } catch (Settings.SettingNotFoundException | RuntimeException e) {
            return SettingsPagesRows.UNKNOWN;
        }
    }

    // ---- Verbindungen & Geräte -------------------------------------------------------------------

    private static SettingsModel.Page connections(HomeHost host) {
        SettingsModel.Page page = new SettingsModel.Page("verbindungen", "Verbindungen & Geräte")
                .subtitle("Netzwerk, Controller und Filter").icon(Icons.VERBINDUNG);
        Intent network = SettingsPagesRows.action("android.settings.WIFI_SETTINGS");
        if (host.canStart(network)) {
            page.add(SettingsPagesRows.button("Netzwerk", "WLAN auswählen und verbinden", Icons.WLAN,
                    "wlan wifi internet verbindung passwort", SettingsPagesRows.starter(host, network)));
        }
        Intent controllers = SettingsPagesRows.action(ACTION_CONTROLLERS);
        if (host.canStart(controllers)) {
            page.add(SettingsPagesRows.button("Controller & Bluetooth", "Fernbedienung, Gamepad und Kopfhörer koppeln",
                    Icons.BLUETOOTH, "bluetooth fernbedienung gamepad kopfhoerer koppeln geraete",
                    SettingsPagesRows.starter(host, controllers)));
        }
        page.add(SettingsPagesRows.button("Speedtest", "Öffnet fast.com im Browser", Icons.TEMPO,
                "geschwindigkeit internet tempo fast", () -> host.openUrl(SPEEDTEST_URL)));
        page.add(SettingsPagesRows.info("Filter", "Blockiert Werbung und Tracker im Netzwerk", Icons.FILTER,
                "vpn dns dumbtv werbung tracker", () -> SettingsPagesRows.filterState(host)));
        return page;
    }

    // ---- System ----------------------------------------------------------------------------------

    private static SettingsModel.Page system(HomeHost host) {
        SettingsModel.Page page = new SettingsModel.Page("system", "System")
                .subtitle("Geräteeinstellungen und Diagnose").icon(Icons.SYSTEM);
        page.add(SettingsPagesRows.button("Alle Einstellungen",
                "Töne und Bildschirm, Barrierefreiheit, Konto, Präferenzen und mehr", Icons.EINSTELLUNGEN,
                "ton bildschirm anzeige barrierefreiheit konto praeferenzen sprache amazon",
                host::openAmazonOverview));
        Intent applications = SettingsPagesRows.action(ACTION_APPLICATIONS);
        if (host.canStart(applications)) {
            page.add(SettingsPagesRows.button("Apps verwalten", "Apps entfernen, stoppen, Speicher leeren", Icons.APPS,
                    "anwendungen apps deinstallieren deaktivieren speicher stoppen", SettingsPagesRows.starter(host, applications)));
        }
        page.add(SettingsPagesRows.link("Diagnose", "Speicher, Version, Warnungen und Tasten-Test", Icons.INFO,
                "diagnose speicher pss adb animation tasten test fernbedienung home dienst bildschirmschoner",
                SettingsPagesDiagnose.build(host)));
        Intent device = SettingsPagesRows.action(ACTION_DEVICE);
        if (host.canStart(device)) {
            page.add(SettingsPagesRows.button("Mein Fire TV", "Gerät, Neustart und Werkseinstellungen", Icons.SYSTEM,
                    "geraet neustart zuruecksetzen werkseinstellung update", SettingsPagesRows.starter(host, device)));
        }
        return page;
    }

    // ---- Sicherung -------------------------------------------------------------------------------

    private static SettingsModel.Page backup(HomeHost host) {
        SettingsModel.Page page = new SettingsModel.Page("sicherung", "Sicherung")
                .subtitle("Dock, Ordner und Aussehen sichern").icon(Icons.SICHERUNG);
        page.add(SettingsPagesRows.button("Sicherung speichern", "Speichert Dock, Ordner, Namen und Aussehen", Icons.SICHERUNG,
                "backup sichern speichern exportieren", () -> host.toast(host.saveBackup())));
        SettingsModel.Button restore = SettingsPagesRows.button("Sicherung laden", "Stellt den gespeicherten Stand wieder her",
                Icons.HERUNTERLADEN, "backup wiederherstellen importieren laden", () -> host.toast(host.restoreBackup()));
        restore.confirmLabel("Laden");
        restore.destructive("Dock, Ordner, Namen und Aussehen werden durch die gespeicherte Sicherung ersetzt.");
        page.add(restore);
        SettingsModel.Button resetLook = SettingsPagesRows.button("Alle Darstellungs-Einstellungen zurücksetzen",
                "Uhr, Glas, Hintergrund, Farbe, Bewegung und Schrift", Icons.PAPIERKORB,
                "standard reset zuruecksetzen darstellung aussehen", () -> resetAppearance(host));
        resetLook.confirmLabel("Zurücksetzen");
        resetLook.destructive("Alle Darstellungs-Einstellungen gehen auf den Standard zurück. Dock und Ordner bleiben.");
        page.add(resetLook);
        page.add(new SettingsModel.Note("Die Sicherung liegt auf dem Stick im Ordner von TV Home. "
                + "Sie enthält nur Paketnamen und deine Einstellungen."));
        return page;
    }

    private static void resetAppearance(HomeHost host) {
        host.config().resetAll();
        host.look().setAccentIndex(Look.AUTO);
        host.look().setBlurred(false);
        host.applyLook();
        host.toast("Darstellung zurückgesetzt");
    }
}
