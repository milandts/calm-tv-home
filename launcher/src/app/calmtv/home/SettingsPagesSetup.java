package app.calmtv.home;

import android.content.Intent;
import android.provider.Settings;

/**
 * Die Seite "Einrichtung": zeigt, ob die HOME-Taste und der Bildschirmschoner schon auf TV Home zeigen, und bietet je einen
 * Knopf, der zur passenden Systemseite führt. Alle Zustände werden beim Zeichnen frisch gelesen; die Knöpfe ändern nichts
 * heimlich, sondern öffnen die Seiten des Systems, auf denen der Nutzer selbst zustimmt.
 */
final class SettingsPagesSetup {
    private static final String KEYWORDS_HOME = "home taste startseite einrichtung bedienungshilfe dienst barrierefreiheit";
    private static final String KEYWORDS_SAVER = "bildschirmschoner schoner screensaver daydream einrichtung";

    private SettingsPagesSetup() {}

    static SettingsModel.Page build(HomeHost host) {
        SettingsModel.Page page = new SettingsModel.Page("einrichtung", "Einrichtung")
                .subtitle("HOME-Taste und Bildschirmschoner").icon(Icons.HAKEN);
        boolean isFireTv = Platform.isFireTv(host.context());
        page.add(SettingsPagesRows.info("HOME-Taste", "Öffnet TV Home statt der Startseite des Geräts", Icons.TV, KEYWORDS_HOME,
                () -> HomeKeyService.isRunning() ? "Aktiv" : "Nicht aktiv, HOME öffnet die Startseite des Geräts"));
        Intent accessibility = SettingsPagesRows.action(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        if (host.canStart(accessibility)) {
            page.add(SettingsPagesRows.button("Dienst einschalten", "Öffnet die Bedienungshilfen: dort \"Home-Taste für TV Home\" einschalten",
                    Icons.EINSTELLUNGEN, KEYWORDS_HOME, SettingsPagesRows.starter(host, accessibility)));
        }
        page.add(new SettingsModel.Note("Steht die Zeile oben nach einem Update auf \"Nicht aktiv\", obwohl der Dienst in den "
                + "Bedienungshilfen eingeschaltet ist, hilft es, ihn dort aus- und wieder einzuschalten."));

        page.add(new SettingsModel.Header("Bildschirmschoner"));
        page.add(SettingsPagesRows.info("Bildschirmschoner", "", Icons.SCHIRMSCHONER, KEYWORDS_SAVER,
                () -> SettingsPagesDiagnose.screensaverState(host.context())));
        Intent dreamSettings = SettingsPagesRows.action(Settings.ACTION_DREAM_SETTINGS);
        if (!isFireTv && host.canStart(dreamSettings)) {
            page.add(SettingsPagesRows.button("Bildschirmschoner wählen", "Öffnet die Auswahl des Systems", Icons.SCHIRMSCHONER,
                    KEYWORDS_SAVER, SettingsPagesRows.starter(host, dreamSettings)));
        } else if (!isFireTv) {
            page.add(new SettingsModel.Note("Die Auswahl findest du in den Einstellungen des Geräts, meist unter Geräteeinstellungen > "
                    + "Bildschirmschoner. Ob TV Home Bildschirmschoner dort angeboten wird, hängt vom Gerät ab."));
        } else {
            page.add(new SettingsModel.Note("Amazon blendet die Auswahl des Bildschirmschoners aus. TV Home als Bildschirmschoner "
                    + "einzustellen geht nur einmal über einen Computer (siehe INSTALL.md)."));
        }
        return page;
    }
}
