package app.calmtv.home;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Die Seite "Darstellung" mit Uhr, Glas, Hintergrund, Akzentfarbe, Bewegung sowie die Seite "Bedienungshilfen". */
final class SettingsPagesLook {
    private SettingsPagesLook() {}

    static SettingsModel.Page build(HomeHost host) {
        SettingsModel.Page page = new SettingsModel.Page("darstellung", "Darstellung")
                .subtitle("Uhr, Glas, Hintergrund und Farbe").icon(Icons.DARSTELLUNG);
        page.add(SettingsPagesRows.link("Uhr und Datum", "Format, Größe, Gruß und was angezeigt wird", Icons.LAUFZEIT,
                "uhr zeit 12 24 stunden datum gruss name anzeigen", clock(host)));
        page.add(SettingsPagesRows.link("Glas", "Transparenz, Tönung, Glanz und Schatten", Icons.UNSCHARF,
                "glas transparenz deckkraft saettigung helligkeit rand glanz schatten frost klar rauch matt", glass(host)));
        page.add(SettingsPagesRows.link("Hintergrund", "Schärfe, Abdunkeln, Bilder", Icons.BILD,
                "bild foto wallpaper unscharf scharf blur dunkel abdunkeln eigene bilder favoriten ordner", background(host)));
        page.add(SettingsPagesRows.link("Akzentfarbe", "Automatisch passend zum Bild, oder eine feste Farbe", Icons.FARBE,
                "farbe farben akzent fokus blau gruen orange rosa violett weiss", accent(host)));
        page.add(SettingsPagesRows.link("Bewegung und Fokus", "Tempo, Einblenden, Größe und Ring", Icons.TEMPO,
                "animation tempo bewegung fokus ring groesse einblenden langsam schnell", motion()));
        page.add(SettingsPagesRows.link("Layout", "Platz von Uhr, Leiste, Dock und Raster", Icons.DOCK,
                "layout position platz dock leiste uhr raster spalten sortierung kompakt kino", SettingsPagesLayout.build(host)));
        page.add(SettingsPagesRows.link("Kontrollzentrum", "Welche Kacheln erscheinen und welche vorn stehen", Icons.EINSTELLUNGEN,
                "kontrollzentrum kacheln schnellmenue menue reihenfolge ausblenden", controlCenter()));
        return page;
    }

    // ---- Uhr und Datum ---------------------------------------------------------------------------

    private static SettingsModel.Page clock(HomeHost host) {
        SettingsModel.Page page = new SettingsModel.Page("darstellung/uhr", "Uhr und Datum").icon(Icons.LAUFZEIT).preview();
        page.add(SettingsPagesRows.opt("Zeitformat", "", Icons.LAUFZEIT, "uhr zeit 12 24 stunden", Opts.CLOCK_FORMAT));
        page.add(SettingsPagesRows.opt("Größe der Uhr", "", Icons.LAUFZEIT, "uhr groesse schrift", Opts.CLOCK_SIZE));
        page.add(SettingsPagesRows.opt("Stil der Uhr", "", Icons.LAUFZEIT, "uhr stil weiss glas duenn", Opts.CLOCK_STYLE));
        page.add(SettingsPagesRows.opt("Datum", "", Icons.LAUFZEIT, "datum format kurz lang zahlen", Opts.DATE_FORMAT));
        page.add(SettingsPagesRows.opt("Gruß", "", Icons.STERN, "gruss begruessung name tageszeit", Opts.GREETING));
        // Knopf neben dem Titel (die Vorschau-Seite zeigt nur Wahlzeilen, Regler und Knöpfe, keine Links auf Unterseiten).
        page.add(SettingsPagesRows.button("Name …", "Name für den Gruß eingeben", Icons.UMBENENNEN, "name gruss begruessung eingeben",
                () -> GlassPrompt.show(host.modalLayer(), "Name für den Gruß", "Dein Name", host.config().getText(Opts.GREETING_NAME), text -> {
                    host.config().setText(Opts.GREETING_NAME, text);
                    if (!text.trim().isEmpty()) host.config().set(Opts.GREETING, 1);   // sonst bliebe der Name unsichtbar
                })));
        page.add(SettingsPagesRows.opt("Uhr anzeigen", "", Icons.LAUFZEIT, "uhr ausblenden", Opts.SHOW_CLOCK));
        page.add(SettingsPagesRows.opt("Datum anzeigen", "", Icons.LAUFZEIT, "datum ausblenden", Opts.SHOW_DATE));
        page.add(SettingsPagesRows.opt("Filter anzeigen", "", Icons.FILTER, "vpn filter ausblenden", Opts.SHOW_FILTER));
        page.add(SettingsPagesRows.opt("Wetter anzeigen", "Erscheint, sobald du im Bildschirmschoner eine Stadt gewählt hast",
                Icons.WETTER, "wetter temperatur startseite ausblenden", Opts.SHOW_WEATHER));
        page.add(SettingsPagesRows.opt("Titel im Dock anzeigen", "", Icons.DOCK, "dock titel name ausblenden",
                Opts.SHOW_DOCK_TITLE));
        return page;
    }

    // ---- Kontrollzentrum -------------------------------------------------------------------------

    /** Welche Kacheln das Kontrollzentrum zeigt und welche vorn stehen. */
    static SettingsModel.Page controlCenter() {
        SettingsModel.Page page = new SettingsModel.Page("darstellung/kontrollzentrum", "Kontrollzentrum").icon(Icons.EINSTELLUNGEN);
        page.add(new SettingsModel.Note("Wähle je Kachel: Normal, Zuerst (vorn im Raster) oder Aus. Einstellungen bleibt immer da."));
        page.add(SettingsPagesRows.opt("Bildschirmschoner", "", Icons.SCHIRMSCHONER, "kontrollzentrum kachel schoner", Opts.CC_SAVER));
        page.add(SettingsPagesRows.opt("Nächstes Bild", "", Icons.BILD, "kontrollzentrum kachel bild hintergrund", Opts.CC_NEXT));
        page.add(SettingsPagesRows.opt("Schärfe", "", Icons.UNSCHARF, "kontrollzentrum kachel schaerfe unscharf", Opts.CC_SHARP));
        page.add(SettingsPagesRows.opt("Abdunkeln", "", Icons.ABDUNKELN, "kontrollzentrum kachel dunkel", Opts.CC_DIM));
        page.add(SettingsPagesRows.opt("Einstellungen", "", Icons.SYSTEM, "kontrollzentrum kachel einstellungen", Opts.CC_SETTINGS));
        page.add(SettingsPagesRows.opt("Fire TV", "", Icons.TV, "kontrollzentrum kachel fire tv system", Opts.CC_SYSTEM));
        return page;
    }

    // ---- Glas ------------------------------------------------------------------------------------

    private static final String[] PRESET_NAMES = {"Standard", "Klar", "Frost", "Rauch", "Matt", "Eigene"};
    /** Reihenfolge der Werte: Deckkraft, Tönung, Sättigung, Helligkeit, Rand, Glanz, Schatten. */
    private static final int[][] PRESETS = {
            {100, 0, 160, 78, 100, 100, 100},
            {40, 0, 140, 90, 100, 100, 60},
            {100, 10, 120, 85, 80, 100, 100},
            {100, 35, 100, 60, 60, 60, 100},
            {100, 20, 60, 70, 40, 20, 40}};
    private static final Opt[] GLASS_OPTS = {Opts.GLASS_OPACITY, Opts.GLASS_DARKEN, Opts.GLASS_SATURATION,
            Opts.GLASS_BRIGHTNESS, Opts.GLASS_RIM, Opts.GLASS_SHEEN, Opts.GLASS_SHADOW};

    private static SettingsModel.Page glass(final HomeHost host) {
        final Config config = host.config();
        SettingsModel.Page page = new SettingsModel.Page("darstellung/glas", "Glas").icon(Icons.UNSCHARF).preview();
        page.add(SettingsPagesRows.choice("Voreinstellung", "", Icons.UNSCHARF, "glas preset frost klar rauch matt standard",
                Arrays.asList(PRESET_NAMES),
                () -> currentPreset(config),
                index -> applyPreset(config, index)));
        page.add(SettingsPagesRows.opt("Glas-Deckkraft", "Wie stark das Glas das Foto zeigt", Icons.UNSCHARF,
                "transparenz glas deckkraft", Opts.GLASS_OPACITY));
        page.add(SettingsPagesRows.opt("Tönung", "Dunkle Schicht unter dem Glas", Icons.ABDUNKELN,
                "dunkel abdunkeln lesbarkeit", Opts.GLASS_DARKEN));
        page.add(SettingsPagesRows.opt("Sättigung", "Farbkraft des Fotos im Glas", Icons.FARBE,
                "farbe saettigung", Opts.GLASS_SATURATION));
        page.add(SettingsPagesRows.opt("Helligkeit", "Helligkeit des Fotos im Glas", Icons.SONNE,
                "hell dunkel helligkeit", Opts.GLASS_BRIGHTNESS));
        page.add(SettingsPagesRows.opt("Rand", "", Icons.UNSCHARF, "rand kante rim", Opts.GLASS_RIM));
        page.add(SettingsPagesRows.opt("Glanz", "", Icons.UNSCHARF, "glanz sheen", Opts.GLASS_SHEEN));
        page.add(SettingsPagesRows.opt("Schatten", "", Icons.UNSCHARF, "schatten", Opts.GLASS_SHADOW));
        page.add(SettingsPagesRows.opt("Deckkraft der Menüs", "Einstellungen, Kontrollzentrum und Menüs", Icons.UNSCHARF,
                "panel menue transparenz einstellungen", Opts.PANEL_OPACITY));
        page.add(SettingsPagesRows.button("Glas zurücksetzen", "Alle Glas-Werte auf Standard", Icons.PAPIERKORB,
                "glas standard reset zuruecksetzen", () -> {
                    for (Opt o : GLASS_OPTS) config.reset(o);
                    config.reset(Opts.PANEL_OPACITY);
                }));
        return page;
    }

    /** Nummer der Voreinstellung, die zu den Werten passt, sonst "Eigene". */
    private static int currentPreset(Config config) {
        for (int p = 0; p < PRESETS.length; p++) {
            boolean same = true;
            for (int i = 0; i < GLASS_OPTS.length; i++) if (config.get(GLASS_OPTS[i]) != PRESETS[p][i]) same = false;
            if (same) return p;
        }
        return PRESET_NAMES.length - 1;
    }

    private static void applyPreset(Config config, int index) {
        if (index < 0 || index >= PRESETS.length) return;   // "Eigene" ändert nichts
        for (int i = 0; i < GLASS_OPTS.length; i++) config.trial(GLASS_OPTS[i], PRESETS[index][i]);
    }

    // ---- Hintergrund -----------------------------------------------------------------------------

    private static SettingsModel.Page background(final HomeHost host) {
        final Look look = host.look();
        SettingsModel.Page page = new SettingsModel.Page("darstellung/hintergrund", "Hintergrund").icon(Icons.BILD).preview();
        page.add(SettingsPagesRows.choice("Schärfe", "", Icons.UNSCHARF, "unscharf scharf blur weichzeichnen",
                Arrays.asList("Scharf", "Unscharf"),
                () -> look.isBlurred() ? 1 : 0,
                index -> {
                    look.setBlurred(index == 1);
                    host.applyLook();
                }));
        page.add(SettingsPagesRows.opt("Abdunkeln", "", Icons.ABDUNKELN, "dunkel heller dunkler helligkeit", Opts.DIM)
                .after(host::applyLook));
        page.add(SettingsPagesRows.button("Nächstes Bild", "", Icons.NACH_VORN, "bild wechseln naechstes foto wallpaper",
                host::nextWallpaper));
        page.add(SettingsPagesRows.button("Bilder verwalten …", "Eigene Bilder, Favoriten und ausgeblendete Bilder",
                Icons.BILD, "bilder galerie favoriten eigene ordner ausblenden", host::openGallery));
        page.add(SettingsPagesRows.opt("Bildwechsel", "", Icons.SONNE, "rhythmus wechsel taeglich mitternacht nie",
                Opts.BG_RHYTHM));
        page.add(SettingsPagesRows.opt("Eigene Bilder einbeziehen", "Bilder aus dem Ordner auf dem Stick, nie aus dem Netz",
                Icons.ORDNER, "eigene bilder ordner fotos", Opts.BG_OWN));
        page.add(SettingsPagesRows.opt("Nur eigene Bilder", "", Icons.ORDNER, "eigene bilder nur", Opts.BG_ONLY_OWN));
        page.add(SettingsPagesRows.opt("Nur Favoriten", "", Icons.STERN, "favoriten nur lieblings", Opts.BG_FAVORITES_ONLY));
        return page;
    }

    private static SettingsModel.Page accent(HomeHost host) {
        final Look look = host.look();
        SettingsModel.Page page = new SettingsModel.Page("darstellung/akzent", "Akzentfarbe").icon(Icons.FARBE).preview();
        SettingsModel.Choice colors = SettingsPagesRows.choice("Farbe", "", Icons.FARBE, "akzent farben fokus",
                Arrays.asList(Look.ACCENT_NAMES),
                look::accentIndex,
                index -> {
                    look.setAccentIndex(index);
                    host.applyLook();
                });
        List<Integer> swatches = new ArrayList<>();
        for (int i = 0; i < Look.ACCENT_NAMES.length; i++) swatches.add(Look.accentColor(i));
        colors.swatches(swatches);
        page.add(colors);
        return page;
    }

    // ---- Bewegung und Fokus ----------------------------------------------------------------------

    private static SettingsModel.Page motion() {
        SettingsModel.Page page = new SettingsModel.Page("darstellung/bewegung", "Bewegung und Fokus").icon(Icons.TEMPO).preview();
        page.add(SettingsPagesRows.opt("Tempo der Animationen", "", Icons.TEMPO, "animation tempo langsam schnell aus bewegung",
                Opts.MOTION));
        page.add(SettingsPagesRows.opt("Einblenden beim Start", "Animation, wenn du zur Startseite kommst", Icons.TEMPO,
                "einblenden animation start", Opts.ENTRANCE));
        page.add(SettingsPagesRows.opt("Größe beim Fokus", "Wie stark die Auswahl wächst", Icons.TEMPO,
                "fokus vergroessern kachel groesse", Opts.FOCUS_SCALE));
        page.add(SettingsPagesRows.opt("Breite des Fokus-Rings", "", Icons.TEMPO, "fokus ring rand breite", Opts.FOCUS_RING));
        return page;
    }

    // ---- Bedienungshilfen ------------------------------------------------------------------------

    static SettingsModel.Page accessibility() {
        SettingsModel.Page page = new SettingsModel.Page("bedienung", "Bedienungshilfen")
                .subtitle("Schriftgröße, Rand und Bewegung").icon(Icons.STERN);
        page.add(SettingsPagesRows.opt("Schriftgröße", "Gilt für die Einstellungen, Menüs und das Kontrollzentrum",
                Icons.DARSTELLUNG, "schrift groesse text lesen gross klein", Opts.TEXT_SCALE));
        page.add(SettingsPagesRows.opt("Sicherheitsrand", "Abstand zum Bildschirmrand, falls der Fernseher etwas abschneidet",
                Icons.TV, "rand overscan abgeschnitten rahmen", Opts.SAFE_MARGIN));
        page.add(SettingsPagesRows.opt("Tempo der Animationen", "Bei Aus bewegt sich nichts", Icons.TEMPO,
                "animation tempo bewegung aus", Opts.MOTION));
        page.add(SettingsPagesRows.opt("Abdunkeln", "Mehr Kontrast zwischen Text und Foto", Icons.ABDUNKELN,
                "dunkel kontrast hintergrund", Opts.DIM));
        page.add(new SettingsModel.Note("Fire TV hat selbst keine Einstellung für die Schriftgröße. "
                + "Diese Werte gelten nur für TV Home."));
        return page;
    }
}
