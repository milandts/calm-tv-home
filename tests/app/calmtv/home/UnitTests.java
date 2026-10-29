package app.calmtv.home;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Prüfungen der reinen Logik ohne Gerät: Einstellungen ({@link Opts}), Auslesen gespeicherter Werte ({@link GalleryPrefs}) und die
 * Einstellungssuche. Lauf: scripts/test-unit.sh. Jede Prüfung muss scheitern können: bricht ein Wert, zählt das Ergebnis als Fehler.
 */
public final class UnitTests {
    private static final List<String> FAILURES = new ArrayList<>();
    private static int checks;

    private UnitTests() {}

    public static void main(String[] args) {
        settingsDefinitions();
        galleryValues();
        settingsSearch();
        wallpaperRhythm();
        System.out.println(checks + " Prüfungen, " + FAILURES.size() + " Fehler");
        for (String failure : FAILURES) System.out.println("  FEHLER: " + failure);
        System.exit(FAILURES.isEmpty() ? 0 : 1);
    }

    private static void check(boolean ok, String what) {
        checks++;
        if (!ok) FAILURES.add(what);
    }

    private static void equal(Object expected, Object actual, String what) {
        check(expected == null ? actual == null : expected.equals(actual), what + ": erwartet " + expected + ", war " + actual);
    }

    // ---- Opts: jede Einstellung ist vollständig und in sich stimmig ---------------------------------

    private static void settingsDefinitions() {
        Set<String> keys = new HashSet<>();
        for (Opt opt : Opts.all()) {
            String name = opt.key;
            check(!name.isEmpty() && keys.add(name), "Schlüssel eindeutig und nicht leer: " + name);
            check(opt.def >= opt.min && opt.def <= opt.max, name + ": Standard liegt im Bereich");
            check(opt.step > 0, name + ": Schrittweite positiv");
            if (opt.kind == Opt.RANGE) check((opt.max - opt.min) % opt.step == 0, name + ": Bereich ist ein Vielfaches der Schrittweite");
            if (opt.kind == Opt.CHOICE) equal(opt.max + 1, opt.labels.length, name + ": eine Beschriftung je Option");
            check(!Opts.title(name).equals(name), name + ": hat einen Anzeigenamen in Opts.TITLES");
            check(!opt.describe(opt.def).isEmpty(), name + ": Standardwert lässt sich beschreiben");
            equal(opt.min, opt.clamp(opt.min - 1000), name + ": clamp nach unten");
            equal(opt.max, opt.clamp(opt.max + 1000), name + ": clamp nach oben");
            check(Opts.byKey(name) == opt, name + ": über den Schlüssel auffindbar");
        }
        check(!keys.isEmpty(), "die Liste der Einstellungen ist nicht leer");
        equal(null, Opts.byKey("gibtEsNicht"), "unbekannter Schlüssel");
    }

    // ---- GalleryPrefs: kaputte gespeicherte Werte dürfen nie abstürzen ---------------------------------

    private static void galleryValues() {
        equal(50, GalleryPrefs.parseFocus(null)[0], "Ausschnitt ohne Wert: Mitte (x)");
        equal(20, GalleryPrefs.parseFocus("20,80")[0], "Ausschnitt x");
        equal(80, GalleryPrefs.parseFocus("20,80")[1], "Ausschnitt y");
        equal(100, GalleryPrefs.parseFocus("500,-5")[0], "Ausschnitt x wird auf 100 begrenzt");
        equal(0, GalleryPrefs.parseFocus("500,-5")[1], "Ausschnitt y wird auf 0 begrenzt");
        equal(50, GalleryPrefs.parseFocus("abc,def")[0], "Ausschnitt mit Text fällt auf die Mitte zurück");
        equal(50, GalleryPrefs.parseFocus("1,2,3")[1], "Ausschnitt mit drei Zahlen fällt auf die Mitte zurück");
        equal(0, GalleryPrefs.parseTone(null), "Helligkeit ohne Wert");
        equal(12, GalleryPrefs.parseTone(" 12 "), "Helligkeit mit Leerzeichen");
        equal(30, GalleryPrefs.parseTone("99"), "Helligkeit nach oben begrenzt");
        equal(-30, GalleryPrefs.parseTone("-99"), "Helligkeit nach unten begrenzt");
        equal(0, GalleryPrefs.parseTone("hell"), "Helligkeit mit Text");
    }

    // ---- WallpaperPicker: wann wechselt das Bild, welches kommt dran ----------------------------------

    private static void wallpaperRhythm() {
        long hour = 60 * 60 * 1000L, day = 24 * hour;
        // Täglich (0): gleicher Tag bleibt, neuer Tag wechselt
        check(WallpaperPicker.keeps(0, 100, 100, 0, 0), "täglich: gleicher Tag bleibt");
        check(!WallpaperPicker.keeps(0, 100, 101, 0, 0), "täglich: neuer Tag wechselt");
        // Bei jedem Heimkommen (1): nie behalten
        check(!WallpaperPicker.keeps(1, 100, 100, 0, 0), "Heimkommen: wechselt immer");
        // Alle 6 Stunden (2)
        check(WallpaperPicker.keeps(2, 0, 0, 0, 5 * hour + 59 * 60_000L), "6 Stunden: nach 5:59 h bleibt es");
        check(!WallpaperPicker.keeps(2, 0, 0, 0, 6 * hour), "6 Stunden: nach 6:00 h wechselt es");
        check(!WallpaperPicker.keeps(2, 0, 0, 10 * hour, 9 * hour), "6 Stunden: zurückgestellte Uhr zählt als abgelaufen");
        // Nie (3)
        check(WallpaperPicker.keeps(3, 1, 999, 0, 99 * day), "nie: bleibt immer");
        // Welches Bild
        equal(2, WallpaperPicker.indexFor(0, 5, 7, 0, 0, 0, false), "täglich: Tag 7 bei 5 Bildern ist Platz 2");
        equal(3, WallpaperPicker.indexFor(0, 5, 8, 0, 0, 0, false), "täglich: der nächste Tag ist der nächste Platz");
        equal(0, WallpaperPicker.indexFor(0, 5, 10, 0, 0, 0, false), "täglich: nach dem letzten Platz wieder der erste");
        equal(4, WallpaperPicker.indexFor(1, 5, 0, 0, 0, 4, false), "Heimkommen: der gewürfelte Platz");
        equal(0, WallpaperPicker.indexFor(1, 5, 0, 0, 0, 4, true), "Heimkommen: gleiches Bild wie gespeichert rückt weiter, am Ende nach vorn");
        equal(0, WallpaperPicker.indexFor(1, 1, 0, 0, 0, 0, true), "Heimkommen: bei nur einem Bild bleibt es dabei");
        equal((int) ((8 * hour / (6 * hour)) % 5), WallpaperPicker.indexFor(2, 5, 0, 8 * hour, 0, 0, false), "6 Stunden: Zeitfenster 1 bei 5 Bildern");
        check(WallpaperPicker.indexFor(2, 5, 0, 6 * hour, 0, 0, false) != WallpaperPicker.indexFor(2, 5, 0, 5 * hour, 0, 0, false),
                "6 Stunden: beim Wechsel des Zeitfensters ändert sich der Platz");
        // Tagesnummer folgt der Zeitzone: um 23 Uhr UTC ist es in Berlin (+1 h) schon der nächste Tag
        equal(WallpaperPicker.dayNumber(day, 0) + 1, WallpaperPicker.dayNumber(day + 23 * hour, 2 * 60 * 60 * 1000), "Tagesnummer: Zeitzone verschiebt den Tageswechsel");
    }

    // ---- SettingsSearch: Treffer, Umlaute, Reihenfolge -----------------------------------------------

    private static void settingsSearch() {
        SettingsModel.Page clock = new SettingsModel.Page("darstellung/uhr", "Uhr und Datum");
        SettingsModel.Button name = new SettingsModel.Button("Name …", () -> { });
        name.keywords = "name gruss begruessung";
        clock.rows.add(name);
        SettingsModel.Page look = new SettingsModel.Page("darstellung", "Darstellung");
        look.rows.add(new SettingsModel.Link("Uhr und Datum", clock));
        SettingsModel.Page apps = new SettingsModel.Page("apps", "Apps & Dock");
        SettingsModel.Button hidden = new SettingsModel.Button("Ausgeblendete Apps", () -> { });
        apps.rows.add(hidden);
        List<SettingsSearch.Entry> index = SettingsSearch.index(java.util.Arrays.asList(look, apps));

        check(!SettingsSearch.match(index, "Gruß").isEmpty(), "Suche findet über das Stichwort mit ß/ss");
        equal("darstellung/uhr", SettingsSearch.match(index, "gruss").get(0).pageId, "Treffer verweist auf die Seite der Zeile");
        check(!SettingsSearch.match(index, "AUSGEBLENDETE").isEmpty(), "Suche ignoriert Groß- und Kleinschreibung");
        check(!SettingsSearch.match(index, "ausgeblendete apps").isEmpty(), "mehrere Wörter müssen alle vorkommen");
        check(SettingsSearch.match(index, "ausgeblendete zebra").isEmpty(), "ein fehlendes Wort ergibt keinen Treffer");
        check(SettingsSearch.match(index, "   ").isEmpty(), "leere Suche ergibt keine Treffer");
        check(SettingsSearch.match(index, null).isEmpty(), "Suche ohne Text ergibt keine Treffer");
        Set<String> pages = new HashSet<>();
        for (SettingsSearch.Entry entry : index) pages.add(entry.pageId);
        equal(3, pages.size(), "jede Seite kommt im Index genau einmal als Seite vor");
    }
}
