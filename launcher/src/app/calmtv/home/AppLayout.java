package app.calmtv.home;

import android.content.SharedPreferences;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Wie der Nutzer seine Apps angeordnet hat: Ordner, ausgeblendete Apps, eigene Namen, nach vorn gezogene Apps und
 * die zuletzt benutzten. Alles liegt in den SharedPreferences "layout"; die Methoden liefern Kopien, geändert wird
 * nur über die Methoden hier. Nur Paketnamen und selbst vergebene Namen werden gespeichert.
 */
final class AppLayout {
    /** So viele zuletzt benutzte Apps werden gemerkt. */
    private static final int RECENT_KEPT = 12;
    private static final String KEY_FOLDERS = "folders", KEY_HIDDEN = "hidden", KEY_LABELS = "labels",
            KEY_PINNED = "pinned", KEY_RECENT = "recent", KEY_SHOW_RECENT = "showRecent";

    private final SharedPreferences prefs;

    AppLayout(SharedPreferences prefs) {
        this.prefs = prefs;
    }

    // ---- Ordner --------------------------------------------------------------------------------

    List<String> folderNames() {
        return new ArrayList<>(readFolders().keySet());
    }

    List<String> folderApps(String folder) {
        List<String> apps = readFolders().get(folder);
        return apps == null ? new ArrayList<String>() : new ArrayList<>(apps);
    }

    /** Name des Ordners, in dem die App liegt, sonst null. */
    String folderOf(String pkg) {
        for (Map.Entry<String, List<String>> e : readFolders().entrySet()) {
            if (e.getValue().contains(pkg)) return e.getKey();
        }
        return null;
    }

    /** Legt die App in den Ordner (der angelegt wird, falls es ihn noch nicht gibt); sie verlässt ihren alten Ordner. */
    void putInFolder(String pkg, String folder) {
        Map<String, List<String>> folders = readFolders();
        for (List<String> apps : folders.values()) apps.remove(pkg);
        List<String> target = folders.get(folder);
        if (target == null) {
            target = new ArrayList<>();
            folders.put(folder, target);
        }
        target.add(pkg);
        writeFolders(withoutEmpty(folders));
    }

    void removeFromFolder(String pkg) {
        Map<String, List<String>> folders = readFolders();
        for (List<String> apps : folders.values()) apps.remove(pkg);
        writeFolders(withoutEmpty(folders));
    }

    /** Benennt einen Ordner um; gibt false zurück, wenn der neue Name leer oder schon vergeben ist. */
    boolean renameFolder(String oldName, String newName) {
        String name = newName.trim();
        Map<String, List<String>> folders = readFolders();
        if (name.isEmpty() || !folders.containsKey(oldName) || (!name.equals(oldName) && folders.containsKey(name))) return false;
        Map<String, List<String>> renamed = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> e : folders.entrySet()) {
            renamed.put(e.getKey().equals(oldName) ? name : e.getKey(), e.getValue());
        }
        writeFolders(renamed);
        return true;
    }

    /** Löst einen Ordner auf; seine Apps erscheinen wieder in der normalen Liste. */
    void dissolveFolder(String folder) {
        Map<String, List<String>> folders = readFolders();
        folders.remove(folder);
        writeFolders(folders);
    }

    // ---- ausgeblendet ----------------------------------------------------------------------------

    boolean isHidden(String pkg) {
        return readSet(KEY_HIDDEN).contains(pkg);
    }

    List<String> hidden() {
        return new ArrayList<>(readSet(KEY_HIDDEN));
    }

    void hide(String pkg) {
        Set<String> hidden = readSet(KEY_HIDDEN);
        hidden.add(pkg);
        writeList(KEY_HIDDEN, hidden);
    }

    void unhide(String pkg) {
        Set<String> hidden = readSet(KEY_HIDDEN);
        hidden.remove(pkg);
        writeList(KEY_HIDDEN, hidden);
    }

    // ---- Namen -----------------------------------------------------------------------------------

    /** Der selbst vergebene Name, sonst der Originalname. */
    String label(String pkg, String original) {
        String own = readLabels().get(pkg);
        return own == null || own.isEmpty() ? original : own;
    }

    boolean isRenamed(String pkg) {
        return readLabels().containsKey(pkg);
    }

    /** Vergibt einen Namen; ein leerer Name stellt den Originalnamen wieder her. */
    void rename(String pkg, String name) {
        Map<String, String> labels = readLabels();
        String trimmed = name.trim();
        if (trimmed.isEmpty()) labels.remove(pkg); else labels.put(pkg, trimmed);
        prefs.edit().putString(KEY_LABELS, new JSONObject(labels).toString()).apply();
    }

    // ---- nach vorn ---------------------------------------------------------------------------------

    List<String> pinned() {
        return new ArrayList<>(readSet(KEY_PINNED));
    }

    boolean isPinned(String pkg) {
        return readSet(KEY_PINNED).contains(pkg);
    }

    /** Zieht die App nach vorn (zuletzt gewählte ganz vorn) oder nimmt sie wieder zurück. */
    void togglePinned(String pkg) {
        List<String> pinned = new ArrayList<>(readSet(KEY_PINNED));
        if (!pinned.remove(pkg)) pinned.add(0, pkg);
        writeList(KEY_PINNED, pinned);
    }

    // ---- zuletzt benutzt --------------------------------------------------------------------------

    /** Zuletzt gestartete Apps, die jüngste zuerst. */
    List<String> recent() {
        return new ArrayList<>(readSet(KEY_RECENT));
    }

    void touch(String pkg) {
        List<String> recent = new ArrayList<>(readSet(KEY_RECENT));
        recent.remove(pkg);
        recent.add(0, pkg);
        writeList(KEY_RECENT, recent.subList(0, Math.min(recent.size(), RECENT_KEPT)));
    }

    // ---- Anzeige ---------------------------------------------------------------------------------

    /** Ob "Zuletzt benutzt" über dem App-Raster erscheint (Standard: ja). */
    boolean showRecent() {
        return prefs.getBoolean(KEY_SHOW_RECENT, true);
    }

    void setShowRecent(boolean show) {
        prefs.edit().putBoolean(KEY_SHOW_RECENT, show).apply();
    }

    /** Vergisst die zuletzt benutzten Apps. */
    void clearRecent() {
        prefs.edit().remove(KEY_RECENT).apply();
    }

    // ---- Speicherung ----------------------------------------------------------------------------------

    private Set<String> readSet(String key) {
        String saved = prefs.getString(key, "");
        Set<String> out = new LinkedHashSet<>();
        if (!TextUtils.isEmpty(saved)) out.addAll(Arrays.asList(TextUtils.split(saved, ",")));
        return out;
    }

    private void writeList(String key, java.util.Collection<String> values) {
        prefs.edit().putString(key, TextUtils.join(",", values)).apply();
    }

    private Map<String, String> readLabels() {
        Map<String, String> out = new LinkedHashMap<>();
        try {
            JSONObject json = new JSONObject(prefs.getString(KEY_LABELS, "{}"));
            for (java.util.Iterator<String> it = json.keys(); it.hasNext(); ) {
                String key = it.next();
                out.put(key, json.getString(key));
            }
        } catch (JSONException e) {
            // Beschädigter Eintrag: ohne eigene Namen weitermachen.
        }
        return out;
    }

    private Map<String, List<String>> readFolders() {
        Map<String, List<String>> out = new LinkedHashMap<>();
        try {
            JSONObject json = new JSONObject(prefs.getString(KEY_FOLDERS, "{}"));
            for (java.util.Iterator<String> it = json.keys(); it.hasNext(); ) {
                String name = it.next();
                JSONArray array = json.getJSONArray(name);
                List<String> apps = new ArrayList<>();
                for (int i = 0; i < array.length(); i++) apps.add(array.getString(i));
                out.put(name, apps);
            }
        } catch (JSONException e) {
            // Beschädigter Eintrag: ohne Ordner weitermachen.
        }
        return out;
    }

    private void writeFolders(Map<String, List<String>> folders) {
        JSONObject json = new JSONObject();
        try {
            for (Map.Entry<String, List<String>> e : folders.entrySet()) json.put(e.getKey(), new JSONArray(e.getValue()));
        } catch (JSONException e) {
            return;   // Namen sind beliebiger Text, put wirft nur bei null-Namen
        }
        prefs.edit().putString(KEY_FOLDERS, json.toString()).apply();
    }

    private static Map<String, List<String>> withoutEmpty(Map<String, List<String>> folders) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> e : folders.entrySet()) if (!e.getValue().isEmpty()) out.put(e.getKey(), e.getValue());
        return out;
    }
}
