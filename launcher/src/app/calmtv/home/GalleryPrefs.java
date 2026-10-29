package app.calmtv.home;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Speicher der Bilderverwaltung in den Config-Texten: Listen (ausgeblendet, Favoriten; eine Kennung je Zeile) und
 * Zuordnungen "id=wert;id=wert" (Ausschnitt, Helligkeit). Kennungen werden maskiert, damit Dateinamen mit
 * Trennzeichen nichts durcheinanderbringen. Ohne Config (vor dem Start) liest alles leer und schreiben tut nichts.
 */
final class GalleryPrefs {
    static final String FOCUS = "bgFocus", TONE = "bgTone";

    private GalleryPrefs() {}

    static Set<String> readList(String key) {
        Set<String> out = new LinkedHashSet<>();
        Config c = Config.i();
        if (c == null) return out;
        for (String line : c.getText(key).split("\n")) {
            String id = line.trim();
            if (!id.isEmpty()) out.add(id);
        }
        return out;
    }

    static void setInList(String key, String id, boolean on) {
        Config c = Config.i();
        if (c == null) return;
        Set<String> ids = readList(key);
        if (on ? !ids.add(id) : !ids.remove(id)) return;
        c.setText(key, String.join("\n", ids));
    }

    static Map<String, String> readMap(String key) {
        Map<String, String> out = new LinkedHashMap<>();
        Config c = Config.i();
        if (c == null) return out;
        for (String pair : c.getText(key).split(";")) {
            int eq = pair.lastIndexOf('=');
            if (eq > 0) out.put(unescape(pair.substring(0, eq)), pair.substring(eq + 1));
        }
        return out;
    }

    /** Setzt den Wert zu einer Kennung; null entfernt den Eintrag (Standard wird nicht gespeichert). */
    static void putInMap(String key, String id, String value) {
        Config c = Config.i();
        if (c == null) return;
        Map<String, String> map = readMap(key);
        if (value == null) map.remove(id); else map.put(id, value);
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : map.entrySet()) {
            if (sb.length() > 0) sb.append(';');
            sb.append(escape(e.getKey())).append('=').append(e.getValue());
        }
        c.setText(key, sb.toString());
    }

    /** Ausschnitt "x,y" in Prozent; Standard 50/50. */
    static int[] parseFocus(String value) {
        int[] out = {50, 50};
        if (value == null) return out;
        String[] p = value.split(",");
        try {
            if (p.length == 2) {
                out[0] = clamp(Integer.parseInt(p[0].trim()), 0, 100);
                out[1] = clamp(Integer.parseInt(p[1].trim()), 0, 100);
            }
        } catch (NumberFormatException e) {
            out[0] = 50;
            out[1] = 50;
        }
        return out;
    }

    static int parseTone(String value) {
        if (value == null) return 0;
        try {
            return clamp(Integer.parseInt(value.trim()), -30, 30);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static String escape(String id) {
        return id.replace("%", "%25").replace(";", "%3B").replace("=", "%3D");
    }

    private static String unescape(String s) {
        return s.replace("%3D", "=").replace("%3B", ";").replace("%25", "%");
    }
}
