package app.calmtv.home;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.ColorMatrix;
import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Die Bildersammlung der Startseite: mitgelieferte Naturfotos und, bei eingeschalteter Einstellung, eigene Bilder. Die Klasse fasst
 * {@link WallpaperSource} (woher), {@link WallpaperRender} (wie aufbereitet) und {@link WallpaperPicker} (welches jetzt) zusammen und
 * trägt die Markierungen pro Bild: ausgeblendet, Favorit, Bildausschnitt (Fokuspunkt) und Helligkeit (siehe {@link GalleryPrefs}).
 */
final class Wallpapers {
    /** Ein fertig aufbereitetes Bild: das große, die unscharfe Kopie, der Nachweis und die Kennung. */
    static final class Frame {
        final String id;
        final String credit;
        final Bitmap blur;
        final Bitmap full;

        Frame(String id, String credit, Bitmap blur, Bitmap full) {
            this.id = id;
            this.credit = credit;
            this.blur = blur;
            this.full = full;
        }
    }

    /** Ein Bild der Sammlung für die Galerie. */
    static final class Entry {
        final String id;
        final String title;     // Ort aus credits.txt, sonst Dateiname
        final String credit;    // ganze Nachweiszeile oder ""
        final boolean own;
        final boolean hidden;
        final boolean favorite;
        final int focusX, focusY;   // Prozent, Standard 50/50
        final int tone;             // Helligkeit -30 bis +30 Prozent

        Entry(String id, String title, String credit, boolean own, boolean hidden, boolean favorite, int focusX, int focusY, int tone) {
            this.id = id;
            this.title = title;
            this.credit = credit;
            this.own = own;
            this.hidden = hidden;
            this.favorite = favorite;
            this.focusX = focusX;
            this.focusY = focusY;
            this.tone = tone;
        }
    }

    private static final String TAG = HomeKeyService.TAG;

    private final WallpaperSource source;
    private final WallpaperRender renderer;
    private final WallpaperPicker picker;

    Wallpapers(Context context) {
        Context app = context.getApplicationContext();
        source = new WallpaperSource(app);
        renderer = new WallpaperRender(source);
        picker = new WallpaperPicker(app);
    }

    /** Tage seit 1970 in der Zeitzone des Fernsehers; das Bild wechselt um Mitternacht. */
    static long dayNumber() {
        return WallpaperPicker.dayNumber();
    }

    static ColorMatrix toneMatrix(int percent) {
        return WallpaperRender.toneMatrix(percent);
    }

    // ---- Sammlung --------------------------------------------------------------------------------

    /** Alle Bilder, nach Kennung sortiert, mit Markierungen. Eigene nur bei eingeschalteter Einstellung. */
    List<Entry> list() {
        Set<String> hidden = GalleryPrefs.readList(Opts.BG_HIDDEN);
        Set<String> favorites = GalleryPrefs.readList(Opts.BG_FAVORITES);
        Map<String, String> focus = GalleryPrefs.readMap(GalleryPrefs.FOCUS);
        Map<String, String> tone = GalleryPrefs.readMap(GalleryPrefs.TONE);
        List<Entry> entries = new ArrayList<>();
        for (Map.Entry<String, Boolean> found : source.ids().entrySet()) {
            String id = found.getKey();
            String credit = source.creditFor(id);
            int[] point = GalleryPrefs.parseFocus(focus.get(id));
            entries.add(new Entry(id, titleOf(id, credit), credit, found.getValue(), hidden.contains(id), favorites.contains(id),
                    point[0], point[1], GalleryPrefs.parseTone(tone.get(id))));
        }
        return entries;
    }

    private static String titleOf(String id, String credit) {
        int dot = credit.indexOf(" · ");
        return dot > 0 ? credit.substring(0, dot) : id.substring(id.indexOf(':') + 1);
    }

    List<String> credits() {
        return source.allCredits();
    }

    void setHidden(String id, boolean hidden) {
        GalleryPrefs.setInList(Opts.BG_HIDDEN, id, hidden);
    }

    void setFavorite(String id, boolean favorite) {
        GalleryPrefs.setInList(Opts.BG_FAVORITES, id, favorite);
    }

    /** Bildausschnitt: Fokuspunkt in Prozent des Bildes (50/50 ist die Mitte). */
    void setFocus(String id, int x, int y) {
        int focusX = GalleryPrefs.clamp(x, 0, 100), focusY = GalleryPrefs.clamp(y, 0, 100);
        GalleryPrefs.putInMap(GalleryPrefs.FOCUS, id, focusX == 50 && focusY == 50 ? null : focusX + "," + focusY);
    }

    /** Helligkeit in Prozent (-30 bis +30). */
    void setTone(String id, int percent) {
        int tone = GalleryPrefs.clamp(percent, -30, 30);
        GalleryPrefs.putInMap(GalleryPrefs.TONE, id, tone == 0 ? null : Integer.toString(tone));
    }

    // ---- Auswahl ---------------------------------------------------------------------------------

    /** Dieses Bild beim nächsten {@link #next} nehmen (und danach beibehalten, bis zum nächsten Wechsel). */
    void pin(String id) {
        picker.pin(id);
    }

    /** Kennung des zuletzt geladenen oder gespeicherten Bildes; null, wenn keins. */
    String currentId() {
        return picker.currentId();
    }

    /** Das nächste Bild, auf width x height zugeschnitten. Auf einem Hintergrundthread aufrufen. */
    Frame next(final int width, final int height) {
        return picker.next(list(), id -> load(id, width, height));
    }

    /** Das Bild für jetzt nach dem Wechselrhythmus. Auf einem Hintergrundthread aufrufen. */
    Frame today(final int width, final int height) {
        return picker.today(list(), id -> load(id, width, height));
    }

    // ---- Laden -----------------------------------------------------------------------------------

    private Frame load(String id, int width, int height) {
        Bitmap full = thumbnail(id, width, height);
        return full == null ? null : new Frame(id, source.creditFor(id), WallpaperRender.blurred(full), full);
    }

    /** Ein Bild genau width x height mit Ausschnitt und Helligkeit; null, wenn es sich nicht lesen lässt. Nur auf Hintergrundthreads. */
    Bitmap thumbnail(String id, int width, int height) {
        try {
            int[] point = GalleryPrefs.parseFocus(GalleryPrefs.readMap(GalleryPrefs.FOCUS).get(id));
            int tone = GalleryPrefs.parseTone(GalleryPrefs.readMap(GalleryPrefs.TONE).get(id));
            return renderer.render(id, width, height, point[0], point[1], tone);
        } catch (IOException | RuntimeException | OutOfMemoryError e) {
            Log.w(TAG, "can't load wallpaper " + id, e);
            return null;
        }
    }

    /** Das ganze Bild klein (passt in maxWidth x maxHeight), ohne Zuschnitt und Helligkeit. Nur auf Hintergrundthreads. */
    Bitmap overview(String id, int maxWidth, int maxHeight) {
        try {
            return renderer.overview(id, maxWidth, maxHeight);
        } catch (IOException | RuntimeException | OutOfMemoryError e) {
            Log.w(TAG, "can't load preview " + id, e);
            return null;
        }
    }
}
