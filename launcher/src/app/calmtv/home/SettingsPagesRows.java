package app.calmtv.home;

import android.content.Intent;

import java.util.ArrayList;
import java.util.List;

/**
 * Kleine Bausteine für {@link SettingsPages}: Zeilen mit Untertitel, Symbol und Suchbegriffen in einem Aufruf,
 * Info-Zeilen, die nie abstürzen, und Texte, die mehrere Seiten brauchen.
 */
final class SettingsPagesRows {
    /** Anzeige, wenn ein Wert sich nicht ermitteln lässt. */
    static final String UNKNOWN = "unbekannt";

    private SettingsPagesRows() {}

    static SettingsModel.Button button(String title, String subtitle, int icon, String keywords, Runnable action) {
        SettingsModel.Button row = new SettingsModel.Button(title, action);
        row.subtitle = subtitle;
        row.icon = icon;
        row.keywords = keywords;
        return row;
    }

    static SettingsModel.Link link(String title, String subtitle, int icon, String keywords, SettingsModel.Page target) {
        SettingsModel.Link row = new SettingsModel.Link(title, target);
        row.subtitle = subtitle;
        row.icon = icon;
        row.keywords = keywords;
        return row;
    }

    static SettingsModel.Toggle toggle(String title, String subtitle, int icon, String keywords,
                                       SettingsModel.Value<Boolean> state, SettingsModel.Setter<Boolean> set) {
        SettingsModel.Toggle row = new SettingsModel.Toggle(title, state, set);
        row.subtitle = subtitle;
        row.icon = icon;
        row.keywords = keywords;
        return row;
    }

    static SettingsModel.Choice choice(String title, String subtitle, int icon, String keywords, List<String> options,
                                       SettingsModel.Value<Integer> selected, SettingsModel.Setter<Integer> select) {
        SettingsModel.Choice row = new SettingsModel.Choice(title, options, selected, select);
        row.subtitle = subtitle;
        row.icon = icon;
        row.keywords = keywords;
        return row;
    }

    /** Zeile für eine Einstellung aus {@link Opts}; Regler, Schalter und Auswahl zeichnet das Gerüst. */
    static SettingsModel.OptRow opt(String title, String subtitle, int icon, String keywords, Opt opt) {
        SettingsModel.OptRow row = new SettingsModel.OptRow(title, opt);
        row.subtitle = subtitle;
        row.icon = icon;
        row.keywords = keywords;
        return row;
    }

    /** Info-Zeile; der Wert wird bei jedem Zeichnen frisch gelesen, ein Fehler zeigt "unbekannt". */
    static SettingsModel.Info info(String title, String subtitle, int icon, String keywords,
                                   final SettingsModel.Value<String> value) {
        SettingsModel.Value<String> guarded = () -> {
            try {
                String text = value.get();
                return text == null || text.isEmpty() ? UNKNOWN : text;
            } catch (RuntimeException e) {
                return UNKNOWN;
            }
        };
        SettingsModel.Info row = new SettingsModel.Info(title, guarded);
        row.subtitle = subtitle;
        row.icon = icon;
        row.keywords = keywords;
        return row;
    }

    /** Eine Liste mit nur einem Hinweis (für leere dynamische Seiten). */
    static List<SettingsModel.Row> onlyNote(String text) {
        List<SettingsModel.Row> rows = new ArrayList<>();
        rows.add(new SettingsModel.Note(text));
        return rows;
    }

    /** Ein Intent auf eine Amazon-Einstellungsseite (Aktion), als eigene Aufgabe gestartet. */
    static Intent action(String action) {
        return new Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    /** Startet den Intent über den Host (der bei Fehlern selbst meldet). */
    static Runnable starter(final HomeHost host, final Intent intent) {
        return () -> host.start(intent);
    }

    /** Status des DNS-Filters (VPN) als Text. */
    static String filterState(HomeHost host) {
        return host.isFilterActive() ? "Aktiv" : "Nicht aktiv";
    }

    /** "1 App" oder "n Apps". */
    static String appCount(int count) {
        return count == 1 ? "1 App" : count + " Apps";
    }
}
