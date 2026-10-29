package app.calmtv.home;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;

/**
 * Sichert und lädt die persönliche Anordnung (Dock, Ordner, Namen, Aussehen) als JSON-Datei im App-Ordner auf dem
 * Stick. Der Ordner braucht keine Berechtigung; per {@code adb pull} lässt sich die Datei kopieren.
 */
final class Backup {
    static final String FILE_NAME = "tvhome-sicherung.json";
    private static final String[] PREF_FILES = {"home", "layout", "config"};
    /** Textwerte der Einstellungen (Name, Bilderlisten), die in der Sicherung stehen dürfen. */
    private static final String[] CONFIG_TEXT = {Opts.GREETING_NAME, Opts.BG_HIDDEN, Opts.BG_FAVORITES, "bgFocus", "bgTone"};
    /** Erlaubte Einträge je Datei und ihr Typ; alles andere (auch der Nutzungsverlauf "recent") wird weder gesichert noch geladen. */
    private static final String[][] ALLOWED = {
            {"home", "dock", "s"},
            {"layout", "folders", "s"}, {"layout", "hidden", "s"}, {"layout", "labels", "s"}, {"layout", "pinned", "s"},
            {"layout", "accent", "i"}, {"layout", "accentV", "i"}, {"layout", "dim", "i"}, {"layout", "blurred", "b"}, {"layout", "showRecent", "b"}};
    private static final long MAX_BYTES = 256 * 1024;

    private Backup() {}

    static File file(Context c) {
        File dir = c.getExternalFilesDir(null);
        return dir == null ? null : new File(dir, FILE_NAME);
    }

    /** Schreibt die Sicherung; gibt die Datei zurück oder null, wenn der Speicher nicht verfügbar ist. */
    static File save(Context c) throws IOException, JSONException {
        File target = file(c);
        if (target == null) return null;
        JSONObject all = new JSONObject();
        for (String name : PREF_FILES) {
            JSONObject one = new JSONObject();
            for (Map.Entry<String, ?> e : c.getSharedPreferences(name, Context.MODE_PRIVATE).getAll().entrySet()) {
                if (typeOf(name, e.getKey()) != null) one.put(e.getKey(), e.getValue());
            }
            all.put(name, one);
        }
        Files.write(target.toPath(), all.toString(2).getBytes(StandardCharsets.UTF_8));
        return target;
    }

    private static String typeOf(String file, String key) {
        if ("config".equals(file)) {
            if (Opts.byKey(key) != null) return "i";
            for (String t : CONFIG_TEXT) if (t.equals(key)) return "s";
            return null;
        }
        for (String[] allowed : ALLOWED) if (allowed[0].equals(file) && allowed[1].equals(key)) return allowed[2];
        return null;
    }

    /** Liest die Sicherung zurück; false, wenn es keine gibt. Unbekannte Werte werden übersprungen. */
    static boolean restore(Context c) throws IOException, JSONException {
        File source = file(c);
        if (source == null || !source.isFile()) return false;
        if (source.length() > MAX_BYTES) throw new IOException("Sicherung zu groß");
        JSONObject all = new JSONObject(new String(Files.readAllBytes(source.toPath()), StandardCharsets.UTF_8));
        for (String name : PREF_FILES) {
            JSONObject one = all.optJSONObject(name);
            if (one == null) continue;
            SharedPreferences.Editor edit = c.getSharedPreferences(name, Context.MODE_PRIVATE).edit();
            if ("config".equals(name)) {
                for (java.util.Iterator<String> it = one.keys(); it.hasNext(); ) {
                    String key = it.next();
                    String type = typeOf(name, key);
                    Object value = one.get(key);
                    if ("i".equals(type) && value instanceof Integer) edit.putInt(key, Opts.byKey(key).clamp((Integer) value));
                    else if ("s".equals(type) && value instanceof String) edit.putString(key, (String) value);
                }
                edit.apply();
                continue;
            }
            for (String[] allowed : ALLOWED) {
                if (!allowed[0].equals(name) || !one.has(allowed[1])) continue;
                Object value = one.get(allowed[1]);
                // Nur den erwarteten Typ übernehmen: ein falscher Typ würde beim Lesen im Launcher abstürzen.
                if ("s".equals(allowed[2]) && value instanceof String) edit.putString(allowed[1], (String) value);
                else if ("i".equals(allowed[2]) && value instanceof Integer) edit.putInt(allowed[1], (Integer) value);
                else if ("b".equals(allowed[2]) && value instanceof Boolean) edit.putBoolean(allowed[1], (Boolean) value);
            }
            // Eine ältere Sicherung kennt "accentV" nicht: dann gilt die alte Reihenfolge der Akzentfarben und Look verschiebt sie beim Start.
            if ("layout".equals(name) && !one.has("accentV")) edit.remove("accentV");
            edit.apply();
        }
        return true;
    }
}
