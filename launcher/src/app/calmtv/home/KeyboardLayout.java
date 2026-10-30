package app.calmtv.home;

import java.util.Locale;

/**
 * Tasten und Textregeln der eigenen Bildschirmtastatur ({@link GlassKeyboard}), ohne Android-Aufrufe und damit ohne Gerät
 * prüfbar. Die Tastatur gibt es, weil Amazons Tastatur am Stick über den Bildschirmrand läuft und die App sie nicht
 * verkleinern kann.
 */
final class KeyboardLayout {
    /** Zeilen der Zeichentasten (QWERTZ), je 10 Tasten. */
    static final String[] ROWS = {"1234567890", "qwertzuiop", "asdfghjklß", "yxcvbnmäöü"};
    static final String SPACE = " ", DASH = "-";

    private KeyboardLayout() {}

    /** Am Anfang, nach Leerzeichen und nach Bindestrich beginnt ein Wort: dort schreibt die Tastatur groß. */
    static boolean startsWord(String text) {
        if (text.isEmpty()) return true;
        char last = text.charAt(text.length() - 1);
        return Character.isWhitespace(last) || last == '-';
    }

    /** Groß, wenn ein Wort beginnt; die Umschalttaste kehrt das für den nächsten Buchstaben um. */
    static boolean isUpper(String text, boolean flipped) {
        return startsWord(text) != flipped;
    }

    /** Das Zeichen der Taste in der gewünschten Schreibweise; "ß" bleibt klein (groß wäre "SS"). */
    static String shown(String key, boolean upper) {
        return upper && !"ß".equals(key) ? key.toUpperCase(Locale.GERMAN) : key;
    }

    /** Text ohne das letzte Zeichen; leer bleibt leer. */
    static String deleteLast(String text) {
        if (text.isEmpty()) return text;
        return text.substring(0, text.offsetByCodePoints(text.length(), -1));
    }
}
