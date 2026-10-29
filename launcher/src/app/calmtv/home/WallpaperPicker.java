package app.calmtv.home;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TimeZone;

/**
 * Entscheidet, welches Hintergrundbild jetzt dran ist. Zur Wahl stehen die Bilder, die nicht ausgeblendet sind (mit Einschränkung auf
 * eigene Bilder oder Favoriten, solange es welche gibt). Der Wechselrhythmus ({@link Opts#BG_RHYTHM}) legt fest: täglich das Bild
 * "Tagesnummer modulo Anzahl", bei jedem Heimkommen ein zufälliges, alle sechs Stunden das Bild des Zeitfensters, oder nie ein anderes.
 * Ein von Hand gewähltes Bild gilt bis zum nächsten Wechsel.
 */
final class WallpaperPicker {
    /** Lädt ein Bild; null, wenn es sich nicht lesen lässt. */
    interface Loader {
        Wallpapers.Frame load(String id);
    }

    private static final String PREFS = "wallpaper", KEY_DAY = "day", KEY_ID = "id", KEY_TIME = "time", KEY_PINNED = "pinned";
    private static final long SIX_HOURS_MS = 6L * 60 * 60 * 1000, DAY_MS = 86_400_000L;

    private final Context context;
    private final Set<String> shown = new HashSet<>();   // in dieser Runde schon gezeigt (für "Nächstes Bild")
    private final Random random = new Random();
    private volatile String last;
    private volatile String requested;

    WallpaperPicker(Context context) {
        this.context = context;
    }

    private SharedPreferences prefs() {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Tage seit dem 1. Januar 1970 in der Zeitzone des Fernsehers, damit das Bild um Mitternacht wechselt. */
    static long dayNumber() {
        long now = System.currentTimeMillis();
        return dayNumber(now, TimeZone.getDefault().getOffset(now));
    }

    /** Dasselbe für einen gegebenen Zeitpunkt und Zeitzonen-Versatz (in Millisekunden). */
    static long dayNumber(long now, int offsetMs) {
        return (now + offsetMs) / DAY_MS;
    }

    /** Dieses Bild beim nächsten {@link #next} nehmen; danach bleibt es bis zum nächsten Wechsel. */
    void pin(String id) {
        requested = id;
    }

    /** Kennung des zuletzt geladenen oder gespeicherten Bildes; null, wenn keins. */
    String currentId() {
        String id = last;
        return id != null ? id : prefs().getString(KEY_ID, null);
    }

    // ---- Vorauswahl ------------------------------------------------------------------------------

    /** Bilder zur Wahl: nicht ausgeblendet, auf Wunsch nur eigene oder nur Favoriten. Sind alle ausgeblendet, kommt trotzdem eins. */
    private static List<Wallpapers.Entry> pool(List<Wallpapers.Entry> all) {
        List<Wallpapers.Entry> open = new ArrayList<>();
        for (Wallpapers.Entry entry : all) if (!entry.hidden) open.add(entry);
        if (open.isEmpty()) return new ArrayList<>(all);
        if (Config.val(Opts.BG_OWN) != 0 && Config.val(Opts.BG_ONLY_OWN) != 0) open = onlyIfAny(open, true);
        if (Config.val(Opts.BG_FAVORITES_ONLY) != 0) open = onlyIfAny(open, false);
        return open;
    }

    private static List<Wallpapers.Entry> onlyIfAny(List<Wallpapers.Entry> candidates, boolean wantOwn) {
        List<Wallpapers.Entry> subset = new ArrayList<>();
        for (Wallpapers.Entry entry : candidates) if (wantOwn ? entry.own : entry.favorite) subset.add(entry);
        return subset.isEmpty() ? candidates : subset;
    }

    private static int indexOf(List<Wallpapers.Entry> entries, String id) {
        if (id == null) return -1;
        for (int i = 0; i < entries.size(); i++) if (entries.get(i).id.equals(id)) return i;
        return -1;
    }

    // ---- Wahl ------------------------------------------------------------------------------------

    /** Das nächste Bild: das vorgemerkte, sonst ein zufälliges, das in dieser Runde noch nicht dran war. Hintergrundthread. */
    Wallpapers.Frame next(List<Wallpapers.Entry> all, Loader loader) {
        if (all.isEmpty()) return null;
        String wanted = requested;
        requested = null;
        boolean isPinned = indexOf(all, wanted) >= 0;
        String id;
        if (isPinned) {
            id = wanted;
        } else {
            // Nur aus den noch nicht gezeigten wählen, damit neu hinzugekommene Bilder sofort mitspielen.
            List<String> pool = new ArrayList<>();
            for (Wallpapers.Entry entry : pool(all)) pool.add(entry.id);
            List<String> unseen = new ArrayList<>(pool);
            unseen.removeAll(shown);
            if (unseen.isEmpty()) {   // Runde zu Ende: von vorn, nur nicht dasselbe Bild zweimal hintereinander
                shown.clear();
                unseen.addAll(pool);
                if (unseen.size() > 1) unseen.remove(last);
            }
            id = unseen.get(random.nextInt(unseen.size()));
        }
        Wallpapers.Frame frame = loader.load(id);
        if (frame == null) return null;
        shown.add(id);
        last = id;
        remember(id, isPinned);
        return frame;
    }

    /** Das Bild für jetzt nach dem Wechselrhythmus. Lässt sich eins nicht lesen, kommt das nächste der Liste. Hintergrundthread. */
    Wallpapers.Frame today(List<Wallpapers.Entry> all, Loader loader) {
        if (all.isEmpty()) return null;
        List<Wallpapers.Entry> pool = pool(all);
        SharedPreferences prefs = prefs();
        String stored = prefs.getString(KEY_ID, null);
        long now = System.currentTimeMillis();
        boolean keeps = keepsStored(prefs, stored, all, pool, now);
        int start = keeps ? Math.max(0, indexOf(pool, stored)) : chosenIndex(pool, stored, now);
        // Das gespeicherte Bild zuerst, dann die Liste ab dem gewählten Platz; gemerkt wird nur ein Bild, das wirklich geladen wurde.
        for (int attempt = -1; attempt < pool.size(); attempt++) {
            String id = attempt < 0 ? (keeps ? stored : null) : pool.get((start + attempt) % pool.size()).id;
            if (id == null) continue;
            Wallpapers.Frame frame = loader.load(id);
            if (frame == null) continue;
            if (!keeps || !id.equals(stored)) remember(id, false);
            last = id;
            return frame;
        }
        return null;
    }

    /** Gilt das gespeicherte Bild noch? Je nach Rhythmus: am selben Tag, innerhalb von sechs Stunden, immer oder nie. */
    private boolean keepsStored(SharedPreferences prefs, String stored, List<Wallpapers.Entry> all, List<Wallpapers.Entry> pool, long now) {
        if (indexOf(all, stored) < 0) return false;
        if (!prefs.getBoolean(KEY_PINNED, false) && indexOf(pool, stored) < 0) return false;
        return keeps(Config.val(Opts.BG_RHYTHM), prefs.getLong(KEY_DAY, -1), dayNumber(), prefs.getLong(KEY_TIME, 0), now);
    }

    /**
     * Bleibt das gespeicherte Bild? Täglich (0): am selben Tag. Bei jedem Heimkommen (1): nie. Alle sechs Stunden (2): solange es höchstens
     * sechs Stunden her ist (eine Uhr, die zurückgestellt wurde, zählt als abgelaufen). Nie wechseln (3): immer.
     */
    static boolean keeps(int rhythm, long storedDay, long today, long storedTimeMs, long nowMs) {
        switch (rhythm) {
            case 1: return false;
            case 2: {
                long age = nowMs - storedTimeMs;
                return age >= 0 && age < SIX_HOURS_MS;
            }
            case 3: return true;
            default: return storedDay == today;
        }
    }

    private int chosenIndex(List<Wallpapers.Entry> pool, String stored, long now) {
        int size = pool.size();
        int pick = random.nextInt(size);
        boolean isStoredPick = pool.get(pick).id.equals(stored);
        return indexFor(Config.val(Opts.BG_RHYTHM), size, dayNumber(), now, TimeZone.getDefault().getOffset(now), pick, isStoredPick);
    }

    /**
     * Welcher Platz der Liste ist jetzt dran? Täglich: Tagesnummer modulo Anzahl. Bei jedem Heimkommen: der zufällige Platz {@code pick}, und
     * ist das gerade das gespeicherte Bild (bei mehr als einem Bild), der nächste. Alle sechs Stunden: Nummer des Zeitfensters modulo Anzahl.
     */
    static int indexFor(int rhythm, int size, long today, long nowMs, int offsetMs, int pick, boolean isStoredPick) {
        switch (rhythm) {
            case 1: return size > 1 && isStoredPick ? (pick + 1) % size : pick;
            case 2: return (int) (((nowMs + offsetMs) / SIX_HOURS_MS) % size);
            default: return (int) (today % size);
        }
    }

    private void remember(String id, boolean isPinned) {
        prefs().edit().putLong(KEY_DAY, dayNumber()).putLong(KEY_TIME, System.currentTimeMillis())
                .putString(KEY_ID, id).putBoolean(KEY_PINNED, isPinned).apply();
    }
}
