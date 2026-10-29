package app.calmtv.home;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Alle Einstellungen von TV Home mit Standardwert (= Aussehen vor der Einstellbarkeit), Bereich und Beschriftungen.
 * Neue Einstellung = eine Zeile hier, dann in {@code SettingsPages} eine Zeile und im Aussehen die Stelle, die sie liest.
 * Gelesen wird über {@link Config#val} (Views) oder {@link Config#get}.
 */
final class Opts {
    private static final List<Opt> ALL = new ArrayList<>();

    private static Opt reg(Opt o) {
        ALL.add(o);
        return o;
    }

    // ---- Uhr, Datum, Gruß -------------------------------------------------------------------------
    /** 0 = wie das System, 1 = 24 Stunden, 2 = 12 Stunden. */
    static final Opt CLOCK_FORMAT = reg(Opt.choice("clockFormat", 0, "Wie das System", "24 Stunden", "12 Stunden"));
    /** Größe der Uhr relativ zu 80 sp. */
    static final Opt CLOCK_SIZE = reg(Opt.range("clockSize", 100, 60, 140, 10, " %"));
    /** 0 = Glas (wie bisher), 1 = Weiß, kräftig, 2 = Weiß, dünn. */
    static final Opt CLOCK_STYLE = reg(Opt.choice("clockStyle", 0, "Glas", "Weiß kräftig", "Weiß dünn"));
    /** 0 = Sonntag, 25. Oktober; 1 = So., 25. Okt.; 2 = 25.10.2026. */
    static final Opt DATE_FORMAT = reg(Opt.choice("dateFormat", 0, "Lang", "Kurz", "Zahlen"));
    /** 0 = Gruß nach Tageszeit, 1 = mit Name (Text {@link #GREETING_NAME}), 2 = kein Gruß. */
    static final Opt GREETING = reg(Opt.choice("greeting", 0, "Nach Tageszeit", "Mit Name", "Aus"));
    /** Schlüssel des Namens für den Gruß (Text, siehe {@link Config#getText}). */
    static final String GREETING_NAME = "greetingName";
    static final Opt SHOW_CLOCK = reg(Opt.bool("showClock", true));
    static final Opt SHOW_DATE = reg(Opt.bool("showDate", true));
    static final Opt SHOW_FILTER = reg(Opt.bool("showFilter", true));
    /** Wetterzeile unter dem Datum; sie erscheint nur, wenn im Bildschirmschoner eine Stadt gewählt ist. */
    static final Opt SHOW_WEATHER = reg(Opt.bool("showWeather", true));
    static final Opt SHOW_DOCK_TITLE = reg(Opt.bool("showDockTitle", true));

    // ---- Layout und Platzierung (siehe HomeGeometry) ----------------------------------------------
    /** Ecke der Uhr-Gruppe (Gruß, Uhr, Datum, Filter). Steht das Dock oben, rückt die Gruppe an den unteren Rand. */
    static final Opt HEAD_CORNER = reg(Opt.choice("headCorner", 0, "Oben links", "Oben rechts", "Mitte oben"));
    /** Versatz der Uhr-Gruppe in dp (plus = nach rechts bzw. nach unten). */
    static final Opt HEAD_DX = reg(Opt.range("headDx", 0, -40, 40, 4, " dp"));
    static final Opt HEAD_DY = reg(Opt.range("headDy", 0, -40, 40, 4, " dp"));
    /** Platz der Button-Leiste (Apps, Suche, Einstellungen). Bei gleicher Ecke wie die Uhr rückt sie unter die Uhr. */
    static final Opt BAR_POS = reg(Opt.choice("barPos", 0, "Oben rechts", "Oben links", "Über dem Dock").risky());
    static final Opt BAR_DX = reg(Opt.range("barDx", 0, -40, 40, 4, " dp"));
    static final Opt BAR_DY = reg(Opt.range("barDy", 0, -40, 40, 4, " dp"));
    static final Opt SHOW_BAR_APPS = reg(Opt.bool("showBarApps", true));
    static final Opt SHOW_BAR_SEARCH = reg(Opt.bool("showBarSearch", true));
    /** Aus: Einstellungen bleiben über die Menütaste auf dem Dock und mit zweimal HOME erreichbar. */
    static final Opt SHOW_BAR_SETTINGS = reg(Opt.bool("showBarSettings", true).risky());
    /** Dock unten oder oben. */
    static final Opt DOCK_POS = reg(Opt.choice("dockPos", 0, "Unten", "Oben").risky());
    /** Größe der Dock-Kacheln: Klein 90 %, Normal 100 %, Groß 110 %. */
    static final Opt DOCK_SIZE = reg(Opt.choice("dockSize", 1, "Klein", "Normal", "Groß"));
    /** Abstand der Dock-Kacheln (heute 16 dp). */
    static final Opt DOCK_GAP = reg(Opt.range("dockGap", 16, 8, 32, 2, " dp"));
    /** Eckenradius der Kacheln in Dock und Raster (heute 12 dp). */
    static final Opt TILE_RADIUS = reg(Opt.range("tileRadius", 12, 6, 20, 1, " dp"));
    /** Name unter den Dock-Kacheln: 0 = nie (der Titel über dem Dock bleibt), 1 = nur bei Fokus, 2 = immer. */
    static final Opt DOCK_LABEL = reg(Opt.choice("dockLabel", 0, "Aus", "Nur bei Fokus", "Immer"));
    /** Spalten des App-Rasters: Index 0 bis 3 = 4 bis 7 Spalten (heute 6). */
    static final Opt GRID_COLUMNS = reg(Opt.choice("gridColumns", 2, "4", "5", "6", "7"));
    /** Sortierung des Rasters; "Nach vorn" gezogene Apps stehen immer zuerst. */
    static final Opt GRID_SORT = reg(Opt.choice("gridSort", 0, "A bis Z", "Zuletzt benutzt", "Kürzlich installiert"));
    /** Anzahl der Apps in der Reihe "Zuletzt benutzt": Index 0 bis 3 = 0, 3, 6, 12 (heute 6). */
    static final Opt RECENT_COUNT = reg(Opt.choice("recentCount", 2, "Aus", "3", "6", "12"));

    // ---- Glas ------------------------------------------------------------------------------------
    /** Deckkraft der Glasflächen (Foto-Schicht): 100 = wie bisher, 0 = nur Rand und Glanz. */
    static final Opt GLASS_OPACITY = reg(Opt.range("glassOpacity", 100, 0, 100, 10, " %"));
    /** Zusätzliche dunkle Tönung unter dem Glas (Lesbarkeit). */
    static final Opt GLASS_DARKEN = reg(Opt.range("glassDarken", 0, 0, 60, 5, " %"));
    /** Farbsättigung des Fotos im Glas (heute 160 %). */
    static final Opt GLASS_SATURATION = reg(Opt.range("glassSaturation", 160, 50, 220, 10, " %"));
    /** Helligkeit des Fotos im Glas (heute 78 %). */
    static final Opt GLASS_BRIGHTNESS = reg(Opt.range("glassBrightness", 78, 50, 90, 2, " %"));
    static final Opt GLASS_RIM = reg(Opt.range("glassRim", 100, 0, 100, 10, " %"));
    static final Opt GLASS_SHEEN = reg(Opt.range("glassSheen", 100, 0, 100, 10, " %"));
    static final Opt GLASS_SHADOW = reg(Opt.range("glassShadow", 100, 0, 100, 10, " %"));
    /** Deckkraft der großen Panels (Einstellungen, Kontrollzentrum, Menüs) relativ zum Standard. */
    static final Opt PANEL_OPACITY = reg(Opt.range("panelOpacity", 100, 50, 100, 5, " %"));

    // ---- Farbe ------------------------------------------------------------------------------------
    /** Abdunkeln des Hintergrunds in Prozent (löst die vier Stufen ab). */
    static final Opt DIM = reg(Opt.range("dim", 0, 0, 70, 5, " %"));

    // ---- Bewegung und Fokus ----------------------------------------------------------------------
    /** Tempo aller Animationen: 0 = Normal, 1 = Langsam, 2 = Schnell, 3 = Aus. */
    static final Opt MOTION = reg(Opt.choice("motion", 0, "Normal", "Langsam", "Schnell", "Aus"));
    /** Einblend-Animation beim Heimkommen. */
    static final Opt ENTRANCE = reg(Opt.bool("entrance", true));
    /** Vergrößerung fokussierter Bild-Kacheln (heute 110 %). */
    static final Opt FOCUS_SCALE = reg(Opt.range("focusScale", 110, 100, 125, 5, " %"));
    /** Breite des Fokus-Rings an Bild-Kacheln (heute 3 dp). */
    static final Opt FOCUS_RING = reg(Opt.range("focusRing", 3, 1, 6, 1, " dp"));

    // ---- Schrift und Layout ----------------------------------------------------------------------
    /** Schriftgröße aller Texte der neuen Oberfläche (Einstellungen, Menüs, Kontrollzentrum). */
    static final Opt TEXT_SCALE = reg(Opt.range("textScale", 100, 90, 130, 5, " %").risky());
    /** Sicherheitsrand um die ganze Startseite, gegen Beschneiden durch Fernseher (Overscan). */
    static final Opt SAFE_MARGIN = reg(Opt.range("safeMargin", 0, 0, 48, 4, " dp").risky());

    // ---- Hintergrundbilder -----------------------------------------------------------------------
    /** 0 = täglich um Mitternacht (wie bisher), 1 = bei jedem Heimkommen, 2 = alle 6 Stunden, 3 = nie (Bild bleibt). */
    static final Opt BG_RHYTHM = reg(Opt.choice("bgRhythm", 0, "Täglich", "Bei jedem Heimkommen", "Alle 6 Stunden", "Nie"));
    /** Bilder aus dem eigenen Ordner einbeziehen (Standard: nein, nur die mitgelieferten Naturfotos). */
    static final Opt BG_OWN = reg(Opt.bool("bgOwn", false));
    /** Nur die eigenen Bilder verwenden (nur wirksam, wenn {@link #BG_OWN} an ist und der Ordner Bilder enthält). */
    static final Opt BG_ONLY_OWN = reg(Opt.bool("bgOnlyOwn", false));
    /** Schlüssel der Listen (Text, Dateinamen mit Komma getrennt): ausgeblendete und bevorzugte Bilder. */
    static final String BG_HIDDEN = "bgHidden", BG_FAVORITES = "bgFavorites";
    /** Nur die Favoriten verwenden, solange es welche gibt. */
    static final Opt BG_FAVORITES_ONLY = reg(Opt.bool("bgFavoritesOnly", false));

    // ---- Kontrollzentrum ----------------------------------------------------------------------------
    /** Kacheln des Kontrollzentrums: 0 = normal, 1 = zuerst (vorn im Raster), 2 = aus. "Einstellungen" lässt sich nicht ausblenden (sonst wäre der Weg zu dieser Seite weg). */
    static final Opt CC_SAVER = reg(Opt.choice("ccSaver", 0, "Normal", "Zuerst", "Aus"));
    static final Opt CC_NEXT = reg(Opt.choice("ccNext", 0, "Normal", "Zuerst", "Aus"));
    static final Opt CC_SHARP = reg(Opt.choice("ccSharp", 0, "Normal", "Zuerst", "Aus"));
    static final Opt CC_DIM = reg(Opt.choice("ccDim", 0, "Normal", "Zuerst", "Aus"));
    static final Opt CC_SETTINGS = reg(Opt.choice("ccSettings", 0, "Normal", "Zuerst"));
    static final Opt CC_SYSTEM = reg(Opt.choice("ccSystem", 0, "Normal", "Zuerst", "Aus"));

    // ---- Einschlaf-Timer (die Laufzeit selbst steht in {@link SleepTimer}, sie gilt nur einmal) ----------
    /** Was nach Ablauf des Einschlaf-Timers passiert: 0 = Bildschirmschoner starten, 1 = Bildschirm ausschalten (Ruhezustand). */
    static final Opt SLEEP_ACTION = reg(Opt.choice("sleepAction", 0, "Bildschirmschoner", "Ruhezustand"));

    private Opts() {}

    static List<Opt> all() {
        return Collections.unmodifiableList(ALL);
    }

    /** Verständliche Namen für Meldungen und Dialoge (Schlüssel → Anzeigename); unbekannte Schlüssel kommen unverändert zurück. */
    private static final String[] TITLES = {
            "clockFormat", "Zeitformat", "clockSize", "Größe der Uhr", "clockStyle", "Stil der Uhr", "dateFormat", "Datum",
            "greeting", "Gruß", "showClock", "Uhr anzeigen", "showDate", "Datum anzeigen", "showFilter", "Filter-Anzeige", "showWeather", "Wetter",
            "showDockTitle", "Titel über dem Dock", "headCorner", "Ecke der Uhr", "headDx", "Uhr: Versatz seitlich",
            "headDy", "Uhr: Versatz hoch/runter", "barPos", "Position der Leiste", "barDx", "Leiste: Versatz seitlich",
            "barDy", "Leiste: Versatz hoch/runter", "showBarApps", "Button Apps", "showBarSearch", "Button Suche",
            "showBarSettings", "Button Einstellungen", "dockPos", "Position des Docks", "dockSize", "Größe des Docks",
            "dockGap", "Abstand der Kacheln", "tileRadius", "Rundung der Kacheln", "dockLabel", "Namen unter den Kacheln",
            "gridColumns", "Spalten im Raster", "gridSort", "Sortierung im Raster", "recentCount", "Zuletzt benutzt",
            "glassOpacity", "Glas-Deckkraft", "glassDarken", "Tönung", "glassSaturation", "Sättigung",
            "glassBrightness", "Helligkeit", "glassRim", "Rand", "glassSheen", "Glanz", "glassShadow", "Schatten",
            "panelOpacity", "Deckkraft der Menüs", "dim", "Abdunkeln", "motion", "Tempo der Animationen",
            "entrance", "Einblenden beim Start", "focusScale", "Größe beim Fokus", "focusRing", "Breite des Fokus-Rings",
            "textScale", "Schriftgröße", "safeMargin", "Sicherheitsrand", "bgRhythm", "Bildwechsel", "bgOwn", "Eigene Bilder",
            "bgOnlyOwn", "Nur eigene Bilder", "bgFavoritesOnly", "Nur Favoriten", "sleepAction", "Nach dem Timer",
            "ccSaver", "Kachel Bildschirmschoner", "ccNext", "Kachel Nächstes Bild", "ccSharp", "Kachel Schärfe", "ccDim", "Kachel Abdunkeln",
            "ccSettings", "Kachel Einstellungen", "ccSystem", "Kachel Fire TV"};

    static String title(String key) {
        for (int i = 0; i + 1 < TITLES.length; i += 2) if (TITLES[i].equals(key)) return TITLES[i + 1];
        return key;
    }

    static Opt byKey(String key) {
        for (Opt o : ALL) if (o.key.equals(key)) return o;
        return null;
    }
}
