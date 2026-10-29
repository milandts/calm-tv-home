package app.calmtv.home;

import java.util.ArrayList;
import java.util.List;

/**
 * Beschreibt die Einstellungen als Daten: Seiten mit Zeilen. {@code SettingsPages} baut die Seiten, {@code SettingsScreen}
 * zeichnet sie. So lassen sich Inhalte ändern, ohne die Ansicht anzufassen.
 */
final class SettingsModel {
    private SettingsModel() {}

    /** Liefert einen Wert (zum Zeitpunkt des Zeichnens neu gelesen, damit Zeilen aktuell bleiben). */
    interface Value<T> {
        T get();
    }

    /** Nimmt einen neuen Wert entgegen. */
    interface Setter<T> {
        void set(T value);
    }

    /** Eine Zeile einer Seite. Titel ist Pflicht, Untertitel und Symbol sind optional. */
    abstract static class Row {
        final String title;
        String subtitle = "";
        int icon = Icons.NONE;
        /** Zusätzliche Suchbegriffe (klein geschrieben), damit die Einstellungs-Suche die Zeile findet. */
        String keywords = "";

        Row(String title) {
            this.title = title;
        }

        Row subtitle(String s) {
            subtitle = s;
            return this;
        }

        Row icon(int i) {
            icon = i;
            return this;
        }

        Row keywords(String k) {
            keywords = k;
            return this;
        }
    }

    /** Überschrift eines Abschnitts (nicht fokussierbar). */
    static final class Header extends Row {
        Header(String title) {
            super(title);
        }
    }

    /** Öffnet eine Unterseite (rechts, mit Zurück-Pfeil). */
    static final class Link extends Row {
        final Page target;

        Link(String title, Page target) {
            super(title);
            this.target = target;
        }
    }

    /** Schalter an/aus. */
    static final class Toggle extends Row {
        final Value<Boolean> state;
        final Setter<Boolean> set;

        Toggle(String title, Value<Boolean> state, Setter<Boolean> set) {
            super(title);
            this.state = state;
            this.set = set;
        }
    }

    /**
     * Auswahl aus wenigen Werten. In normalen Seiten schaltet die Auswahltaste zum nächsten Wert und rechts steht der Wert;
     * in Vorschau-Seiten werden alle Werte als Chips nebeneinander gezeigt und links/rechts wählt direkt.
     */
    static final class Choice extends Row {
        final List<String> options;
        final Value<Integer> selected;
        final Setter<Integer> select;
        /** Optional ein Farbwert (ARGB) je Option, gezeichnet als Farbpunkt vor dem Namen (null = keine). */
        List<Integer> swatches;

        Choice(String title, List<String> options, Value<Integer> selected, Setter<Integer> select) {
            super(title);
            this.options = options;
            this.selected = selected;
            this.select = select;
        }

        Choice swatches(List<Integer> colors) {
            swatches = colors;
            return this;
        }
    }

    /** Eine Aktion; {@code destructive} färbt sie rot (z. B. Zurücksetzen). */
    static final class Button extends Row {
        final Runnable action;
        boolean destructive;
        /** Wenn nicht leer: vor dem Ausführen nachfragen (Text der Bestätigung). */
        String confirm = "";
        /** Kurze Beschriftung des Bestätigen-Knopfs (leer: der Zeilentitel). */
        String confirmLabel = "";

        Button(String title, Runnable action) {
            super(title);
            this.action = action;
        }

        Button confirmLabel(String label) {
            confirmLabel = label;
            return this;
        }

        Button destructive(String confirmText) {
            destructive = true;
            confirm = confirmText;
            return this;
        }
    }

    /** Nur Anzeige: links Titel, rechts ein Wert (zum Zeitpunkt des Zeichnens gelesen). */
    static final class Info extends Row {
        final Value<String> value;

        Info(String title, Value<String> value) {
            super(title);
            this.value = value;
        }
    }

    /** Zeile mit einem längeren Text (Erklärungen, Lizenzen); nicht fokussierbar. */
    static final class Note extends Row {
        Note(String text) {
            super(text);
        }
    }

    /**
     * Zeile für eine Einstellung aus {@link Opts}: je nach {@link Opt#kind} Schalter, Auswahl oder Regler. Gelesen und geschrieben wird
     * über {@link Config}; die Zeile zeigt den Wert, ein Punkt markiert Abweichungen vom Standard, die Menütaste setzt zurück.
     * {@code afterChange} läuft nach jeder Änderung (z. B. um andere Zeilen oder die Vorschau zu aktualisieren), darf null sein.
     * Riskante Einstellungen ({@link Opt#risky}) werden mit der Rückfrage "Behalten?" übernommen.
     */
    static final class OptRow extends Row {
        final Opt opt;
        Runnable afterChange;

        OptRow(String title, Opt opt) {
            super(title);
            this.opt = opt;
        }

        OptRow after(Runnable r) {
            afterChange = r;
            return this;
        }
    }

    /** Eine Seite: Titel, Zeilen und ob sie als Vorschau-Seite gezeigt wird. */
    static final class Page {
        final String id;
        final String title;
        String subtitle = "";
        int icon = Icons.NONE;
        /** Vorschau-Seite: die Startseite bleibt sichtbar, die Zeilen erscheinen als kompakte Leiste. */
        boolean preview;
        /** Wird jedes Mal ausgeführt, wenn die Seite geöffnet wird (z. B. um Listen neu zu füllen). */
        Runnable onOpen;
        /** Liefert die Zeilen frisch (Listen, die sich ändern). Hat Vorrang vor {@link #rows}. */
        Value<List<Row>> dynamicRows;
        final List<Row> rows = new ArrayList<>();

        Page(String id, String title) {
            this.id = id;
            this.title = title;
        }

        Page subtitle(String s) {
            subtitle = s;
            return this;
        }

        Page icon(int i) {
            icon = i;
            return this;
        }

        Page preview() {
            preview = true;
            return this;
        }

        Page add(Row row) {
            rows.add(row);
            return this;
        }

        /** Die aktuellen Zeilen (dynamisch, falls gesetzt). */
        List<Row> currentRows() {
            return dynamicRows != null ? dynamicRows.get() : rows;
        }
    }
}
