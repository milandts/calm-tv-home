package app.calmtv.home.dream;

import android.content.Context;
import android.content.pm.PackageManager;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Eigene Bildschirmtastatur für die Stadtsuche, nur auf Fire TV: Amazons Tastatur läuft dort über den Bildschirmrand und
 * die App kann sie nicht verkleinern. Gleiche Tasten und Schreibregeln wie {@code KeyboardLayout} und {@code GlassKeyboard}
 * im Launcher (getrennte App, darum eine eigene Fassung im Stil der Einstellungen des Bildschirmschoners). Sie hält keinen Text:
 * Eingaben gehen über {@link Listener} an den Besitzer, der den neuen Text mit {@link #sync} meldet.
 */
final class SetupKeyboard extends LinearLayout {
    /** Nimmt die Eingaben entgegen. */
    interface Listener {
        void onText(String text);

        void onDelete();

        void onSearch();
    }

    private static final String[] ROWS = {"1234567890", "qwertzuiop", "asdfghjklß", "yxcvbnmäöü"};
    private static final String FEATURE_FIRE_TV = "amazon.hardware.fire_tv", AMAZON_LAUNCHER = "com.amazon.tv.launcher";
    private static final float KEY_HEIGHT_DP = 38, GAP_DP = 4, KEY_SP = 17, KEY_RADIUS_DP = 12;

    private final SetupStyle style;
    private final Listener listener;
    private final List<TextView> letterKeys = new ArrayList<>();
    private final List<String> letterBases = new ArrayList<>();
    private String text = "";
    private boolean flipped;   // Umschalttaste gedrückt: der nächste Buchstabe wird umgekehrt geschrieben
    private Boolean shownUpper;
    private View firstKey;

    SetupKeyboard(Context context, SetupStyle style, Listener listener) {
        super(context);
        this.style = style;
        this.listener = listener;
        setOrientation(VERTICAL);
        for (String row : ROWS) addRow(buildCharacterRow(context, row));
        addRow(buildActionRow(context));
        refresh();
    }

    /** Fire TV: das Systemmerkmal oder Amazons Startseite (ältere Geräte melden das Merkmal nicht immer). */
    static boolean isNeeded(Context context) {
        PackageManager packages = context.getPackageManager();
        if (packages.hasSystemFeature(FEATURE_FIRE_TV)) return true;
        try {
            packages.getPackageInfo(AMAZON_LAUNCHER, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

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
        if (getChildCount() > 0) lp.topMargin = style.px(GAP_DP);
        addView(row, lp);
    }

    private LinearLayout newRow(Context context) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(HORIZONTAL);
        return row;
    }

    private void addKey(LinearLayout row, TextView key, float weight) {
        LayoutParams lp = new LayoutParams(0, style.px(KEY_HEIGHT_DP), weight);
        if (row.getChildCount() > 0) lp.leftMargin = style.px(GAP_DP);
        row.addView(key, lp);
    }

    /** Eine Taste: ruhige Fläche, im Fokus die Akzentfarbe wie bei {@link SetupRow}. */
    private TextView newKey(Context context, String label, View.OnClickListener onClick) {
        final TextView key = style.text(context, label, KEY_SP, style.semibold, SetupStyle.TEXT);
        key.setGravity(Gravity.CENTER);
        key.setSingleLine();
        key.setFocusable(true);
        key.setClickable(true);
        final android.graphics.drawable.Drawable idle = style.rounded(SetupStyle.ROW_FILL, KEY_RADIUS_DP);
        final android.graphics.drawable.Drawable focused = style.rounded(style.accent, KEY_RADIUS_DP);
        key.setBackground(idle);
        key.setOnFocusChangeListener((v, hasFocus) -> {
            key.setBackground(hasFocus ? focused : idle);
            key.setTextColor(hasFocus ? style.onAccent : SetupStyle.TEXT);
        });
        key.setOnClickListener(onClick);
        return key;
    }

    private LinearLayout buildCharacterRow(Context context, String characters) {
        LinearLayout row = newRow(context);
        boolean isDigitRow = Character.isDigit(characters.charAt(0));
        for (int i = 0; i < characters.length(); i++) {
            final String character = String.valueOf(characters.charAt(i));
            TextView key = newKey(context, character, v -> typeCharacter(character));
            if (!isDigitRow) {
                letterKeys.add(key);
                letterBases.add(character);
                if (firstKey == null) firstKey = key;
            }
            addKey(row, key, 1f);
        }
        return row;
    }

    private LinearLayout buildActionRow(Context context) {
        LinearLayout row = newRow(context);
        addKey(row, newKey(context, "Aa", v -> {
            flipped = !flipped;
            refresh();
        }), 2f);
        addKey(row, newKey(context, "-", v -> typeText("-")), 1f);
        addKey(row, newKey(context, "Leerzeichen", v -> typeText(" ")), 3f);
        addKey(row, newKey(context, "Löschen", v -> listener.onDelete()), 2.5f);
        addKey(row, newKey(context, "Suchen", v -> listener.onSearch()), 2f);
        return row;
    }

    // ---- Eingabe ----------------------------------------------------------------------------------

    private boolean isUpper() {
        return startsWord(text) != flipped;
    }

    /** Am Anfang, nach Leerzeichen und nach Bindestrich beginnt ein Wort: dort schreibt die Tastatur groß. */
    private static boolean startsWord(String current) {
        if (current.isEmpty()) return true;
        char last = current.charAt(current.length() - 1);
        return Character.isWhitespace(last) || last == '-';
    }

    /** "ß" bleibt klein (groß wäre "SS"). */
    private static String shown(String key, boolean isUpper) {
        return isUpper && !"ß".equals(key) ? key.toUpperCase(Locale.GERMAN) : key;
    }

    private void typeCharacter(String character) {
        boolean upper = isUpper();
        flipped = false;
        listener.onText(shown(character, upper));
    }

    private void typeText(String typed) {
        flipped = false;
        listener.onText(typed);
    }

    private void refresh() {
        boolean upper = isUpper();
        if (shownUpper != null && shownUpper == upper) return;
        shownUpper = upper;
        for (int i = 0; i < letterKeys.size(); i++) letterKeys.get(i).setText(shown(letterBases.get(i), upper));
    }
}
