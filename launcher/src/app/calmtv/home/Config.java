package app.calmtv.home;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Zentraler Speicher aller Einstellungen aus {@link Opts}. Werte werden beim Lesen auf den erlaubten Bereich geklemmt,
 * ein falscher gespeicherter Wert kann die Startseite also nie zum Absturz bringen. Darüber liegt eine Probeschicht:
 * Werte, die nur zur Vorschau gesetzt sind und nie gespeichert werden ({@link #trial}, {@link #commit}, {@link #revert}).
 * Views lesen über {@link #i()}; Änderungen melden sich bei den Listenern (Hauptthread).
 */
final class Config {
    /** Wird nach jeder Änderung (auch einer Probe) mit dem Schlüssel aufgerufen; null = alles kann sich geändert haben. */
    interface Listener {
        void onChanged(String key);
    }

    private static volatile Config instance;

    private final SharedPreferences prefs;
    private final Map<String, Integer> trial = new HashMap<>();
    private final Map<String, Integer> trialOld = new HashMap<>();   // Wert vor der ersten Probeänderung
    private final List<Listener> listeners = new ArrayList<>();

    Config(Context c) {
        prefs = c.getApplicationContext().getSharedPreferences("config", Context.MODE_PRIVATE);
        instance = this;
    }

    /** Die aktive Konfiguration (nach dem Start der Startseite immer gesetzt). */
    static Config i() {
        return instance;
    }

    /** Kurzform für Views: Wert einer Einstellung; ohne Konfiguration der Standardwert. */
    static int val(Opt o) {
        Config c = instance;
        return c == null ? o.def : c.get(o);
    }

    int get(Opt o) {
        Integer t = trial.get(o.key);
        if (t != null) return o.clamp(t);
        Object stored = prefs.getAll().get(o.key);
        return stored instanceof Integer ? o.clamp((Integer) stored) : o.def;
    }

    boolean on(Opt o) {
        return get(o) != 0;
    }

    void set(Opt o, int value) {
        trial.remove(o.key);
        trialOld.remove(o.key);
        int v = o.clamp(value);
        if (v == o.def) prefs.edit().remove(o.key).apply(); else prefs.edit().putInt(o.key, v).apply();
        fire(o.key);
    }

    /** Setzt auf den Standard zurück. */
    void reset(Opt o) {
        set(o, o.def);
    }

    boolean isDefault(Opt o) {
        return get(o) == o.def;
    }

    // ---- Text (z. B. Name für den Gruß) ---------------------------------------------------------

    String getText(String key) {
        return prefs.getString(key, "");
    }

    void setText(String key, String text) {
        String t = text == null ? "" : text.trim();
        if (t.length() > 4000) t = t.substring(0, 4000);
        if (t.isEmpty()) prefs.edit().remove(key).apply(); else prefs.edit().putString(key, t).apply();
        fire(key);
    }

    // ---- Probeschicht -----------------------------------------------------------------------------

    /** Zeigt einen Wert nur probeweise (wird nicht gespeichert); {@link #commit} speichert, {@link #revert} verwirft. */
    void trial(Opt o, int value) {
        if (!trial.containsKey(o.key)) trialOld.put(o.key, get(o));
        trial.put(o.key, o.clamp(value));
        fire(o.key);
    }

    /** Die Probeänderungen, die wirklich etwas ändern: Schlüssel -> {alter, neuer Wert}. */
    Map<String, int[]> trialChanges() {
        Map<String, int[]> out = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, Integer> e : trial.entrySet()) {
            Integer old = trialOld.get(e.getKey());
            if (old != null && !old.equals(e.getValue())) out.put(e.getKey(), new int[] {old, e.getValue()});
        }
        return out;
    }

    boolean hasTrial() {
        return !trial.isEmpty();
    }

    /** Speichert alle Probewerte. */
    void commit() {
        Map<String, Integer> copy = new HashMap<>(trial);
        trial.clear();
        trialOld.clear();
        SharedPreferences.Editor edit = prefs.edit();
        for (Map.Entry<String, Integer> e : copy.entrySet()) {
            Opt o = Opts.byKey(e.getKey());
            if (o == null) continue;
            if (e.getValue() == o.def) edit.remove(o.key); else edit.putInt(o.key, o.clamp(e.getValue()));
        }
        edit.apply();
        fire(null);
    }

    /** Verwirft alle Probewerte. */
    void revert() {
        if (trial.isEmpty()) return;
        trial.clear();
        trialOld.clear();
        fire(null);
    }

    // ---- Sicherung und Zurücksetzen --------------------------------------------------------------

    /** Alle gespeicherten (vom Standard abweichenden) Werte, für die Sicherung. */
    Map<String, Integer> changed() {
        Map<String, Integer> out = new HashMap<>();
        for (Opt o : Opts.all()) if (!isDefault(o)) out.put(o.key, get(o));
        return out;
    }

    /** Setzt alle Einstellungen aus {@link Opts} auf den Standard (Text-Einstellungen bleiben). */
    void resetAll() {
        trial.clear();
        trialOld.clear();
        SharedPreferences.Editor edit = prefs.edit();
        for (Opt o : Opts.all()) edit.remove(o.key);
        edit.apply();
        fire(null);
    }

    void addListener(Listener l) {
        if (!listeners.contains(l)) listeners.add(l);
    }

    void removeListener(Listener l) {
        listeners.remove(l);
    }

    /** Nach "Sicherung laden": die Werte stehen direkt in der Datei, also Probewerte verwerfen und alle Zuhörer neu lesen lassen (z. B. den Glasstil). */
    void reloaded() {
        trial.clear();
        trialOld.clear();
        fire("*");
    }

    private void fire(String key) {
        for (Listener l : new ArrayList<>(listeners)) l.onChanged(key);
    }
}
