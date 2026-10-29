package app.calmtv.home;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Suche in den Einstellungen: {@link #index} flacht die Seiten zu Einträgen ab, {@link #match} filtert sie.
 * Dynamische Seiten (Listen) werden nicht ausgeführt, nur die Seite selbst steht im Index.
 */
final class SettingsSearch {
    /** So viele Treffer liefert {@link #match} höchstens. */
    static final int MAX_RESULTS = 8;
    private static final String SEPARATOR = " › ";
    private static final String ROOT_BREADCRUMB = "Einstellungen";

    /** Ein Suchtreffer: Titel, Pfad zur Seite, Kennung der Seite, auf der die Zeile liegt, und Suchbegriffe. */
    static final class Entry {
        final String title;
        final String breadcrumb;
        final String pageId;
        final String keywords;

        Entry(String title, String breadcrumb, String pageId, String keywords) {
            this.title = title;
            this.breadcrumb = breadcrumb;
            this.pageId = pageId;
            this.keywords = keywords;
        }
    }

    private SettingsSearch() {}

    // ---- Index -----------------------------------------------------------------------------------

    /** Alle Seiten und ihre bedienbaren Zeilen (Link, Schalter, Auswahl, Button, Info) als Einträge. */
    static List<Entry> index(List<SettingsModel.Page> categories) {
        List<Entry> entries = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        for (SettingsModel.Page category : categories) {
            addPage(entries, visited, category, ROOT_BREADCRUMB, category.title, category.subtitle);
        }
        return entries;
    }

    /**
     * Legt den Eintrag der Seite an und geht ihre Zeilen durch. {@code parentPath} steht im Eintrag der Seite,
     * {@code ownPath} bei ihren Zeilen. Eine Seite, die über mehrere Wege erreichbar ist, kommt nur einmal vor.
     */
    private static void addPage(List<Entry> entries, Set<String> visited, SettingsModel.Page page, String parentPath,
                                String ownPath, String keywords) {
        if (!visited.add(page.id)) return;
        entries.add(new Entry(page.title, parentPath, page.id, keywords));
        if (page.dynamicRows != null) return;   // Listen werden nicht ausgeführt
        for (SettingsModel.Row row : page.rows) {
            String rowKeywords = row.keywords + " " + row.subtitle;
            if (row instanceof SettingsModel.Link) {
                SettingsModel.Page target = ((SettingsModel.Link) row).target;
                // Der Link ersetzt seinen Eintrag durch den der Zielseite, damit nichts doppelt erscheint.
                addPage(entries, visited, target, ownPath, ownPath + SEPARATOR + target.title, rowKeywords);
            } else if (isSearchable(row)) {
                entries.add(new Entry(row.title, ownPath, page.id, rowKeywords));
            }
        }
    }

    private static boolean isSearchable(SettingsModel.Row row) {
        return row instanceof SettingsModel.Toggle || row instanceof SettingsModel.OptRow || row instanceof SettingsModel.Choice
                || row instanceof SettingsModel.Button || row instanceof SettingsModel.Info;
    }

    // ---- Suche -----------------------------------------------------------------------------------

    /**
     * Teilstring-Suche ohne Rücksicht auf Groß-/Kleinschreibung und Umlaute (ä = ae, ö = oe, ü = ue, ß = ss). Bei mehreren
     * Wörtern muss jedes irgendwo vorkommen. Treffer im Titel stehen vor Treffern in Stichwörtern und Pfad.
     */
    static List<Entry> match(List<Entry> index, String query) {
        List<Entry> titleStart = new ArrayList<>();
        List<Entry> titleHit = new ArrayList<>();
        List<Entry> otherHit = new ArrayList<>();
        String normalizedQuery = normalize(query == null ? "" : query).trim();
        if (normalizedQuery.isEmpty()) return new ArrayList<>();
        String[] words = normalizedQuery.split("\\s+");
        for (Entry entry : index) {
            String title = normalize(entry.title);
            String everything = title + " " + normalize(entry.keywords) + " " + normalize(entry.breadcrumb);
            if (!containsAll(everything, words)) continue;
            if (!containsAll(title, words)) otherHit.add(entry);
            else if (title.startsWith(words[0])) titleStart.add(entry);
            else titleHit.add(entry);
        }
        List<Entry> result = new ArrayList<>(titleStart);
        result.addAll(titleHit);
        result.addAll(otherHit);
        return new ArrayList<>(result.subList(0, Math.min(result.size(), MAX_RESULTS)));
    }

    private static boolean containsAll(String text, String[] words) {
        for (String word : words) if (!text.contains(word)) return false;
        return true;
    }

    /** Klein geschrieben, Umlaute und ß aufgelöst. */
    private static String normalize(String text) {
        return text.toLowerCase(Locale.ROOT)
                .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss");
    }
}
