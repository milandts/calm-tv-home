package app.calmtv.home;

import java.util.Arrays;

/**
 * Die Seite "Layout" unter Darstellung: Platz von Uhr, Leiste, Dock und App-Raster. Jede Unterseite ist eine
 * Vorschau-Seite, man sieht die Startseite also live. Die Werte liest {@link HomeGeometry} und {@link HomeActivity}.
 */
final class SettingsPagesLayout {
    private static final String[] PRESET_NAMES = {"Standard", "Kompakt", "Kino", "Groß", "Eigene"};
    /** Werte der Voreinstellungen in der Reihenfolge von {@link #PRESET_OPTS}. */
    private static final int[][] PRESETS = {
            {100, 0, 0, 0, 0, 0, 0, 1, 1, 1, 0, 1, 16, 12, 0},    // Standard
            {70, 0, 0, 0, 0, 0, 0, 1, 1, 1, 0, 0, 12, 12, 0},     // Kompakt: kleinere Uhr, niedrigeres Dock
            {60, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 12, 12, 0},     // Kino: kleine Uhr oben rechts, keine Leiste, niedriges Dock
            {130, 0, 0, 0, 0, 0, 0, 1, 1, 1, 0, 2, 20, 12, 0}};   // Groß: größere Kacheln und Uhr
    private static final Opt[] PRESET_OPTS = {Opts.CLOCK_SIZE, Opts.HEAD_CORNER, Opts.HEAD_DX, Opts.HEAD_DY, Opts.BAR_POS,
            Opts.BAR_DX, Opts.BAR_DY, Opts.SHOW_BAR_APPS, Opts.SHOW_BAR_SEARCH, Opts.SHOW_BAR_SETTINGS, Opts.DOCK_POS,
            Opts.DOCK_SIZE, Opts.DOCK_GAP, Opts.TILE_RADIUS, Opts.DOCK_LABEL};

    private SettingsPagesLayout() {}

    static SettingsModel.Page build(HomeHost host) {
        SettingsModel.Page page = new SettingsModel.Page("darstellung/layout", "Layout")
                .subtitle("Platz von Uhr, Leiste, Dock und Raster").icon(Icons.DOCK);
        page.add(SettingsPagesRows.link("Voreinstellung", "Standard, Kompakt, Kino oder Groß", Icons.STERN,
                "layout preset kompakt kino gross standard", presets(host)));
        page.add(SettingsPagesRows.link("Uhr-Position", "Ecke und Versatz von Gruß, Uhr und Datum", Icons.LAUFZEIT,
                "uhr position ecke versatz links rechts mitte oben verschieben", clock()));
        page.add(SettingsPagesRows.link("Leiste", "Apps, Suche und Einstellungen: Platz und Sichtbarkeit", Icons.APPS,
                "leiste buttons pillen apps suche einstellungen position ausblenden unten", bar()));
        page.add(SettingsPagesRows.link("Dock", "Position, Größe, Abstand, Rundung und Namen", Icons.DOCK,
                "dock oben unten groesse kacheln abstand radius rundung beschriftung name", dock()));
        page.add(SettingsPagesRows.link("Raster", "Spalten, Sortierung und zuletzt benutzte Apps", Icons.ORDNER,
                "raster spalten sortierung sortieren zuletzt benutzt installiert", grid()));
        return page;
    }

    // ---- Voreinstellungen ------------------------------------------------------------------------

    private static SettingsModel.Page presets(final HomeHost host) {
        final Config config = host.config();
        SettingsModel.Page page = new SettingsModel.Page("darstellung/layout/voreinstellung", "Voreinstellung")
                .icon(Icons.STERN).preview();
        page.add(SettingsPagesRows.choice("Layout", "", Icons.STERN, "layout preset kompakt kino gross standard",
                Arrays.asList(PRESET_NAMES), () -> currentPreset(config), index -> applyPreset(config, index)));
        page.add(new SettingsModel.Note("Eine Voreinstellung setzt Uhr, Leiste und Dock. Spalten und Sortierung bleiben. "
                + "Eigene erscheint, sobald du etwas einzeln änderst."));
        return page;
    }

    /** Nummer der Voreinstellung, die zu den Werten passt, sonst "Eigene". */
    private static int currentPreset(Config config) {
        for (int p = 0; p < PRESETS.length; p++) {
            boolean same = true;
            for (int i = 0; i < PRESET_OPTS.length; i++) if (config.get(PRESET_OPTS[i]) != PRESETS[p][i]) same = false;
            if (same) return p;
        }
        return PRESET_NAMES.length - 1;
    }

    private static void applyPreset(Config config, int index) {
        if (index < 0 || index >= PRESETS.length) return;   // "Eigene" ändert nichts
        for (int i = 0; i < PRESET_OPTS.length; i++) config.trial(PRESET_OPTS[i], PRESETS[index][i]);
    }

    // ---- Unterseiten -----------------------------------------------------------------------------

    private static SettingsModel.Page clock() {
        SettingsModel.Page page = new SettingsModel.Page("darstellung/layout/uhr", "Uhr-Position").icon(Icons.LAUFZEIT).preview();
        page.add(SettingsPagesRows.opt("Ecke", "", Icons.LAUFZEIT, "uhr ecke links rechts mitte oben", Opts.HEAD_CORNER));
        page.add(SettingsPagesRows.opt("Versatz waagerecht", "", Icons.LAUFZEIT, "uhr versatz horizontal verschieben", Opts.HEAD_DX));
        page.add(SettingsPagesRows.opt("Versatz senkrecht", "", Icons.LAUFZEIT, "uhr versatz vertikal verschieben", Opts.HEAD_DY));
        return page;
    }

    private static SettingsModel.Page bar() {
        SettingsModel.Page page = new SettingsModel.Page("darstellung/layout/leiste", "Leiste").icon(Icons.APPS).preview();
        page.add(SettingsPagesRows.opt("Platz", "", Icons.APPS, "leiste position oben unten links rechts dock", Opts.BAR_POS));
        page.add(SettingsPagesRows.opt("Versatz waagerecht", "", Icons.APPS, "leiste versatz horizontal", Opts.BAR_DX));
        page.add(SettingsPagesRows.opt("Versatz senkrecht", "", Icons.APPS, "leiste versatz vertikal", Opts.BAR_DY));
        page.add(SettingsPagesRows.opt("Apps zeigen", "", Icons.APPS, "leiste apps ausblenden", Opts.SHOW_BAR_APPS));
        page.add(SettingsPagesRows.opt("Suche zeigen", "", Icons.SUCHE, "leiste suche ausblenden", Opts.SHOW_BAR_SEARCH));
        page.add(SettingsPagesRows.opt("Einstellungen zeigen", "", Icons.EINSTELLUNGEN, "leiste einstellungen ausblenden",
                Opts.SHOW_BAR_SETTINGS));
        page.add(new SettingsModel.Note("Steht die Leiste bei der Uhr, rutscht sie darunter. Ist Einstellungen aus, "
                + "öffnest du sie mit der Menütaste auf einer Dock-App oder mit zweimal HOME."));
        return page;
    }

    private static SettingsModel.Page dock() {
        SettingsModel.Page page = new SettingsModel.Page("darstellung/layout/dock", "Dock").icon(Icons.DOCK).preview();
        page.add(SettingsPagesRows.opt("Position", "", Icons.DOCK, "dock oben unten position", Opts.DOCK_POS));
        page.add(SettingsPagesRows.opt("Größe", "", Icons.DOCK, "dock groesse hoehe kacheln klein gross", Opts.DOCK_SIZE));
        page.add(SettingsPagesRows.opt("Abstand der Kacheln", "", Icons.DOCK, "dock abstand luecke kacheln", Opts.DOCK_GAP));
        page.add(SettingsPagesRows.opt("Ecken der Kacheln", "", Icons.DOCK, "kacheln ecken radius rundung", Opts.TILE_RADIUS));
        page.add(SettingsPagesRows.opt("Name unter der Kachel", "", Icons.UMBENENNEN, "dock beschriftung name kachel fokus",
                Opts.DOCK_LABEL));
        page.add(new SettingsModel.Note("Steht das Dock oben, rücken Uhr und Leiste an den unteren Rand."));
        return page;
    }

    private static SettingsModel.Page grid() {
        SettingsModel.Page page = new SettingsModel.Page("darstellung/layout/raster", "Raster").icon(Icons.ORDNER).preview();
        page.add(SettingsPagesRows.opt("Spalten", "", Icons.ORDNER, "raster spalten anzahl", Opts.GRID_COLUMNS));
        page.add(SettingsPagesRows.opt("Sortierung", "", Icons.ORDNER, "raster sortierung sortieren alphabet installiert",
                Opts.GRID_SORT));
        page.add(SettingsPagesRows.opt("Zuletzt benutzt", "Anzahl der Apps in der Reihe", Icons.LAUFZEIT,
                "zuletzt benutzt reihe anzahl verlauf", Opts.RECENT_COUNT));
        page.add(new SettingsModel.Note("Das Raster siehst du auf der Startseite mit Runter auf dem Dock. "
                + "Apps mit Nach vorn stehen immer zuerst."));
        return page;
    }
}
