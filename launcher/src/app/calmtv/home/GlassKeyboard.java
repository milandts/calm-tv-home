package app.calmtv.home;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * Eigene Bildschirmtastatur im Glas-Look für die Fernbedienung: Ziffern, QWERTZ mit Umlauten, Umschalten, Bindestrich,
 * Leerzeichen und Löschen. Sie hält keinen Text; der Besitzer bekommt die Eingaben über {@link Listener} und meldet den
 * neuen Text mit {@link #sync} zurück (damit die Tasten groß oder klein erscheinen). Die Fokusführung übernimmt der Besitzer
 * über die Fokussuche des Systems (alle Tasten sind {@link Chip}s).
 */
final class GlassKeyboard extends LinearLayout {
    /** Nimmt die Eingaben entgegen. */
    interface Listener {
        void onText(String text);

        void onDelete();

        /** Die Taste mit der Beschriftung {@code doneLabel} (nur wenn es sie gibt). */
        void onDone();
    }

    static final float MAX_KEY_HEIGHT_DP = 34, MIN_KEY_HEIGHT_DP = 24;
    private static final float GAP_DP = 4;

    private final List<Chip> letterKeys = new ArrayList<>();
    private final List<String> letterBases = new ArrayList<>();   // kleine Grundform je Buchstabentaste
    private final Listener listener;
    private final float keyHeightDp;
    private final String doneLabel;   // null: keine Taste zum Abschließen
    private String text = "";
    private boolean flipped;   // Umschalttaste gedrückt: der nächste Buchstabe wird umgekehrt geschrieben
    private Boolean shownUpper;   // null, bis die Tasten zum ersten Mal beschriftet wurden
    private View firstKey;

    /** Höhe aller Tasten einer Tastatur mit {@code rows} Zeilen, die in {@code roomDp} passen müssen (begrenzt auf lesbare Werte). */
    static float keyHeightFor(float roomDp, int rows) {
        float perRow = (roomDp - (rows - 1) * GAP_DP) / rows;
        return Math.max(MIN_KEY_HEIGHT_DP, Math.min(MAX_KEY_HEIGHT_DP, perRow));
    }

    /** @param doneLabel Beschriftung der Taste, die den Dialog abschließt (z. B. "Speichern"); null lässt sie weg */
    GlassKeyboard(Context c, Listener listener, String doneLabel, float keyHeightDp) {
        super(c);
        this.listener = listener;
        this.doneLabel = doneLabel;
        this.keyHeightDp = keyHeightDp;
        setOrientation(VERTICAL);
        for (String row : KeyboardLayout.ROWS) addRow(buildCharacterRow(row));
        addRow(buildActionRow());
        refresh();
    }

    /** Die Taste, auf der der Fokus beginnt: erster Buchstabe der oberen Buchstabenzeile. */
    View firstKey() {
        return firstKey;
    }

    /** Teilt der Tastatur den aktuellen Text mit (für Groß und Klein). */
    void sync(String current) {
        text = current;
        refresh();
    }

    // ---- Aufbau -----------------------------------------------------------------------------------

    private void addRow(LinearLayout row) {
        LayoutParams lp = new LayoutParams(-1, -2);
        if (getChildCount() > 0) lp.topMargin = Ui.px(getContext(), GAP_DP);
        addView(row, lp);
    }

    private LinearLayout newRow() {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(HORIZONTAL);
        return row;
    }

    private void addKey(LinearLayout row, Chip key, float weight) {
        LayoutParams lp = new LayoutParams(0, -2, weight);
        if (row.getChildCount() > 0) lp.leftMargin = Ui.px(getContext(), GAP_DP);
        row.addView(key, lp);
    }

    private Chip newKey(String label) {
        Chip key = new Chip(getContext(), label, keyHeightDp);
        key.setFocusableInTouchMode(true);
        return key;
    }

    private LinearLayout buildCharacterRow(String characters) {
        LinearLayout row = newRow();
        boolean isDigitRow = Character.isDigit(characters.charAt(0));
        for (int i = 0; i < characters.length(); i++) {
            final String character = String.valueOf(characters.charAt(i));
            Chip key = newKey(character);
            key.setOnClickListener(v -> typeCharacter(character));
            if (!isDigitRow) {
                letterKeys.add(key);
                letterBases.add(character);
            }
            if (firstKey == null && !isDigitRow) firstKey = key;
            addKey(row, key, 1f);
        }
        return row;
    }

    private LinearLayout buildActionRow() {
        LinearLayout row = newRow();
        Chip shift = newKey("Aa");
        shift.setOnClickListener(v -> {
            flipped = !flipped;
            refresh();
        });
        Chip dash = newKey(KeyboardLayout.DASH);
        dash.setOnClickListener(v -> typeText(KeyboardLayout.DASH));
        Chip space = newKey("Leerzeichen");
        space.setOnClickListener(v -> typeText(KeyboardLayout.SPACE));
        Chip delete = newKey("Löschen");
        delete.setOnClickListener(v -> listener.onDelete());
        addKey(row, shift, 2f);
        addKey(row, dash, 1f);
        if (doneLabel == null) {
            addKey(row, space, 4f);
            addKey(row, delete, 3f);
            return row;
        }
        Chip done = newKey(doneLabel);
        done.setOnClickListener(v -> listener.onDone());
        addKey(row, space, 3f);
        addKey(row, delete, 2f);
        addKey(row, done, 2f);
        return row;
    }

    // ---- Eingabe ----------------------------------------------------------------------------------

    private void typeCharacter(String character) {
        boolean isUpper = KeyboardLayout.isUpper(text, flipped);
        flipped = false;
        listener.onText(KeyboardLayout.shown(character, isUpper));
    }

    private void typeText(String typed) {
        flipped = false;
        listener.onText(typed);
    }

    /** Beschriftet die Buchstabentasten neu, wenn sich Groß oder Klein ändert. */
    private void refresh() {
        boolean isUpper = KeyboardLayout.isUpper(text, flipped);
        if (shownUpper != null && shownUpper == isUpper) return;
        shownUpper = isUpper;
        for (int i = 0; i < letterKeys.size(); i++) letterKeys.get(i).setLabel(KeyboardLayout.shown(letterBases.get(i), isUpper));
    }
}
