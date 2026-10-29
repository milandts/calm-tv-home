package app.calmtv.home;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * Vorschaubilder der Galerie: ein eigener Thread dekodiert klein (siehe {@link Wallpapers#thumbnail}), ein Cache nach Bytes
 * hält höchstens etwa zwei Dutzend im Speicher. Alles außer dem Dekodieren läuft im Hauptthread.
 */
final class GalleryThumbs {
    interface Sink {
        void onThumb(String key, Bitmap bitmap);
    }

    private static final int CACHE_BYTES = 7 * 1024 * 1024;

    private final Wallpapers wallpapers;
    private final int width, height;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    // Zugriffsreihenfolge = LRU. android.util.LruCache lässt sich mit d8 aus Build-Tools 33 nicht als Oberklasse übersetzen.
    private final LinkedHashMap<String, Bitmap> cache = new LinkedHashMap<>(32, 0.75f, true);
    private int cacheBytes;
    private final Set<String> pending = new HashSet<>();
    private boolean closed;

    GalleryThumbs(Wallpapers wallpapers, int width, int height) {
        this.wallpapers = wallpapers;
        this.width = width;
        this.height = height;
    }

    /** Schlüssel eines Vorschaubildes; ändert sich mit Ausschnitt und Helligkeit. */
    static String key(Wallpapers.Entry e) {
        return e.id + "|" + e.focusX + "," + e.focusY + "|" + e.tone;
    }

    Bitmap cached(String key) {
        return cache.get(key);
    }

    private void put(String key, Bitmap b) {
        Bitmap old = cache.put(key, b);
        if (old != null) cacheBytes -= old.getByteCount();
        cacheBytes += b.getByteCount();
        Iterator<Map.Entry<String, Bitmap>> it = cache.entrySet().iterator();
        while (cacheBytes > CACHE_BYTES && cache.size() > 1 && it.hasNext()) {
            Map.Entry<String, Bitmap> eldest = it.next();
            if (eldest.getValue() == b) break;
            cacheBytes -= eldest.getValue().getByteCount();
            it.remove();
        }
    }

    /** Lädt das Vorschaubild, falls es nicht im Cache ist; meldet es im Hauptthread an die Senke. */
    void request(Wallpapers.Entry entry, Sink sink) {
        String key = key(entry);
        if (closed || cache.containsKey(key) || !pending.add(key)) return;
        String id = entry.id;
        try {
            executor.execute(() -> {
                Bitmap b = wallpapers.thumbnail(id, width, height);
                main.post(() -> {
                    pending.remove(key);
                    if (closed || b == null) return;
                    put(key, b);
                    sink.onThumb(key, b);
                });
            });
        } catch (RejectedExecutionException e) {
            pending.remove(key);
        }
    }

    /** Führt Arbeit auf demselben Thread aus (z. B. das Dekodieren der Übersicht für Ausschnitt und Helligkeit). */
    void submit(Runnable work) {
        if (closed) return;
        try {
            executor.execute(work);
        } catch (RejectedExecutionException e) {
            // schon beendet
        }
    }

    void post(Runnable work) {
        main.post(work);
    }

    /** Beendet den Thread und gibt den Cache frei. */
    void shutdown() {
        closed = true;
        executor.shutdownNow();
        main.removeCallbacksAndMessages(null);
        cache.clear();
        cacheBytes = 0;
        pending.clear();
    }
}
