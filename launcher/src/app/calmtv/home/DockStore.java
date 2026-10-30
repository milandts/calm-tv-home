package app.calmtv.home;

import android.content.SharedPreferences;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Welche Apps im Dock stehen und in welcher Reihenfolge (Einstellung "dock" in den Voreinstellungen "home", Paketnamen mit Komma).
 * Einträge, die gerade nicht zu sehen sind (ausgeblendet oder vorübergehend nicht installiert), bleiben gespeichert und
 * kommen zurück, sobald sie wieder sichtbar sind.
 */
final class DockStore {
    private static final String KEY = "dock";
    /** Erstausstattung, bis der Nutzer selbst sortiert. */
    private static final List<String> FIRST_USE = Arrays.asList(
            "com.netflix.ninja", "com.amazon.firetv.youtube", "com.google.android.youtube.tv", "com.amazon.firebat",
            "com.disney.disneyplus", "com.spotify.tv.android", AppCatalog.SILK);

    private final SharedPreferences prefs;
    private final AppCatalog catalog;
    private final AppLayout layout;

    DockStore(SharedPreferences prefs, AppCatalog catalog, AppLayout layout) {
        this.prefs = prefs;
        this.catalog = catalog;
        this.layout = layout;
    }

    /** Alle gespeicherten Einträge in ihrer Reihenfolge (auch unsichtbare). */
    List<String> saved() {
        String text = prefs.getString(KEY, null);
        List<String> ids = new ArrayList<>();
        for (String id : text == null ? FIRST_USE : Arrays.asList(TextUtils.split(text, ","))) {
            if (!id.startsWith("@")) ids.add(id);   // frühere Versionen hielten "Alle Apps" und "Einstellungen" im Dock
        }
        return ids;
    }

    /** Die Einträge, die jetzt gezeigt werden: installiert und nicht ausgeblendet. */
    List<String> visible() {
        List<String> shown = new ArrayList<>();
        for (String id : saved()) if (catalog.has(id) && !layout.isHidden(id)) shown.add(id);
        return shown;
    }

    /** Speichert die sichtbare Reihenfolge; die unsichtbaren Einträge hängen hinten an. */
    void save(List<String> visibleIds) {
        List<String> hidden = saved();
        hidden.removeAll(visible());
        List<String> all = new ArrayList<>(visibleIds);
        all.addAll(hidden);
        prefs.edit().putString(KEY, TextUtils.join(",", all)).apply();
    }

    /** Zurück zur Erstausstattung. */
    void reset() {
        prefs.edit().remove(KEY).apply();
    }
}
