package app.calmtv.home;

import android.content.SharedPreferences;

/**
 * Darstellungs-Einstellungen: Akzentfarbe, Abdunkeln des Hintergrunds und ob er unscharf gezeigt wird.
 * Die Farbe lesen Kacheln, Buttons und Glas beim Fokussieren über die statischen Methoden.
 */
final class Look {
    static final String[] ACCENT_NAMES = {"Automatisch", "Weiß", "Blau", "Grün", "Orange", "Rosa", "Violett"};
    /** Nummer der Option "Automatisch" (die erste und Standard): die Farbe folgt dem Hintergrundbild, siehe {@link #refreshAuto}. */
    static final int AUTO = 0;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int[] ACCENT_COLORS = {0xFF0A84FF, WHITE, 0xFF0A84FF, 0xFF30D158, 0xFFFF9F0A, 0xFFFF375F, 0xFFBF5AF2};
    private static final String KEY_AUTO_COLOR = "autoColor", KEY_ACCENT_VERSION = "accentV";
    /** Unterhalb dieser mittleren Farbigkeit des Bildes wirkt ein Foto farblos; dann bleibt die Akzentfarbe Weiß. */
    private static final float MIN_COLORFULNESS = 0.012f;
    static final int[] DIM_PERCENT = {0, 20, 35, 50};
    private static final String KEY_ACCENT = "accent", KEY_DIM = "dim", KEY_BLURRED = "blurred";
    /** Ab dieser Helligkeit der Akzentfarbe ist dunkle Schrift darauf besser lesbar. */
    private static final int DARK_TEXT_ABOVE = 140;

    private static volatile int accent = WHITE;
    private static volatile int autoColor = ACCENT_COLORS[AUTO];

    private final SharedPreferences prefs;

    Look(SharedPreferences prefs) {
        this.prefs = prefs;
        migrateAccent();
        autoColor = prefs.getInt(KEY_AUTO_COLOR, ACCENT_COLORS[AUTO]);   // zuletzt berechnete Farbe, bis das Bild geladen ist
        accent = colorOf(accentIndex());
        migrateDim();
    }

    private static int colorOf(int index) {
        return index == AUTO ? autoColor : ACCENT_COLORS[index];
    }

    /**
     * Frühere Versionen hatten Weiß an erster Stelle; "Automatisch" steht jetzt davor. Gespeicherte Nummern einmal verschieben
     * (ohne gespeicherte Farbe gilt der neue Standard "Automatisch").
     */
    private void migrateAccent() {
        if (prefs.contains(KEY_ACCENT_VERSION)) return;
        SharedPreferences.Editor edit = prefs.edit().putInt(KEY_ACCENT_VERSION, 2);
        if (prefs.contains(KEY_ACCENT)) {
            int old = prefs.getInt(KEY_ACCENT, 0);
            edit.putInt(KEY_ACCENT, old == 6 ? AUTO : old + 1);   // alt: 0 Weiß ... 5 Violett, 6 Automatisch (nie ausgeliefert)
        }
        edit.apply();
    }

    int accentIndex() {
        int i = prefs.getInt(KEY_ACCENT, AUTO);
        return i >= 0 && i < ACCENT_COLORS.length ? i : 0;
    }

    /** Setzt die Akzentfarbe nach Nummer (siehe {@link #ACCENT_NAMES}, 0 = Automatisch). */
    void setAccentIndex(int index) {
        int i = index >= 0 && index < ACCENT_COLORS.length ? index : AUTO;
        prefs.edit().putInt(KEY_ACCENT, i).apply();
        accent = colorOf(i);
    }

    /** Die Akzentfarbe als ARGB, z. B. für Farbfelder in Einstellungen ("Automatisch" zeigt die aktuell berechnete Farbe). */
    static int accentColor(int index) {
        return colorOf(index >= 0 && index < ACCENT_COLORS.length ? index : AUTO);
    }

    boolean isAuto() {
        return accentIndex() == AUTO;
    }

    /**
     * Berechnet die Farbe zum Hintergrundbild: die Farbe, die im Bild am meisten Gewicht hat (Sättigung mal Helligkeit,
     * nach Farbton in 24 Gruppen gezählt), kräftig und hell genug für Fokusflächen. Farblose Fotos ergeben Weiß.
     * Wirkt sofort, wenn "Automatisch" gewählt ist. {@code small} ist die kleine unscharfe Kopie des Bildes.
     */
    void refreshAuto(android.graphics.Bitmap small) {
        if (small == null || small.isRecycled()) return;
        int w = small.getWidth(), h = small.getHeight();
        int[] pixels = new int[w * h];
        small.getPixels(pixels, 0, w, 0, 0, w, h);
        final int groups = 24;
        float[] weight = new float[groups], satSum = new float[groups];
        float[] hsv = new float[3];
        float total = 0;
        for (int pixel : pixels) {
            android.graphics.Color.colorToHSV(pixel, hsv);
            float w2 = hsv[1] * hsv[2] * hsv[1];   // blasse und dunkle Pixel zählen kaum
            int g = Math.min(groups - 1, (int) (hsv[0] / 360f * groups));
            weight[g] += w2;
            satSum[g] += hsv[1] * w2;
            total += w2;
        }
        int color = WHITE;
        if (total / pixels.length >= MIN_COLORFULNESS) {
            int best = 0;
            for (int g = 1; g < groups; g++) if (weight[g] > weight[best]) best = g;
            // Farbton: Mitte der Gruppe; Sättigung: gewichteter Mittelwert, nach oben begrenzt auf Werte, die auf Weiß/Schwarz lesbar bleiben.
            float hue = (best + 0.5f) * 360f / groups;
            float sat = weight[best] > 0 ? satSum[best] / weight[best] : 0.7f;
            hsv[0] = hue;
            hsv[1] = Math.max(0.55f, Math.min(0.85f, sat * 1.15f));
            hsv[2] = 0.96f;
            color = android.graphics.Color.HSVToColor(hsv);
        }
        autoColor = color;
        prefs.edit().putInt(KEY_AUTO_COLOR, color).apply();
        if (isAuto()) accent = color;
    }

    /** Wechselt zur nächsten Akzentfarbe und gibt deren Namen zurück. */
    String nextAccent() {
        int next = (accentIndex() + 1) % ACCENT_COLORS.length;
        setAccentIndex(next);
        return ACCENT_NAMES[next];
    }

    /** Einmalig: die alte Stufe (0..3) wird zum Prozentwert in {@link Opts#DIM}, sofern dort noch nichts steht. */
    private void migrateDim() {
        Config config = Config.i();
        if (config == null || !prefs.contains(KEY_DIM)) return;
        int level = prefs.getInt(KEY_DIM, 0);
        if (config.isDefault(Opts.DIM) && level > 0 && level < DIM_PERCENT.length) config.set(Opts.DIM, DIM_PERCENT[level]);
        prefs.edit().remove(KEY_DIM).apply();
    }

    /** Nächstliegende Stufe zum eingestellten Prozentwert. */
    int dimLevel() {
        int percent = Config.val(Opts.DIM), best = 0;
        for (int i = 1; i < DIM_PERCENT.length; i++) {
            if (Math.abs(DIM_PERCENT[i] - percent) < Math.abs(DIM_PERCENT[best] - percent)) best = i;
        }
        return best;
    }

    /** Setzt die Abdunkelungsstufe nach Nummer (siehe {@link #DIM_PERCENT}); gespeichert wird in {@link Opts#DIM}. */
    void setDimLevel(int level) {
        Config config = Config.i();
        if (config != null) config.set(Opts.DIM, DIM_PERCENT[level >= 0 && level < DIM_PERCENT.length ? level : 0]);
    }

    /** Wechselt zur nächsten Abdunkelungsstufe und gibt die Prozent zurück. */
    int nextDim() {
        int next = (dimLevel() + 1) % DIM_PERCENT.length;
        setDimLevel(next);
        return DIM_PERCENT[next];
    }

    /** Alpha (0 bis 255) der schwarzen Fläche über dem Hintergrundbild. */
    int dimAlpha() {
        if (prefs.contains(KEY_DIM)) migrateDim();
        return Math.round(255 * Config.val(Opts.DIM) / 100f);
    }

    boolean isBlurred() {
        return prefs.getBoolean(KEY_BLURRED, false);
    }

    void setBlurred(boolean blurred) {
        prefs.edit().putBoolean(KEY_BLURRED, blurred).apply();
    }

    boolean toggleBlurred() {
        boolean now = !isBlurred();
        prefs.edit().putBoolean(KEY_BLURRED, now).apply();
        return now;
    }

    // ---- Farben für fokussierte Elemente --------------------------------------------------------

    /** Farbe des Fokusrings an Bild-Kacheln. */
    static int focusRing() {
        return accent == WHITE ? 0xE6FFFFFF : accent;
    }

    /** Füllung eines fokussierten Buttons oder einer Text-Kachel (Weiß, wenn keine Farbe gewählt ist). */
    static int focusFill() {
        return accent == WHITE ? 0xF5FFFFFF : (accent & 0x00FFFFFF) | 0xF5000000;
    }

    /** Schriftfarbe auf der Füllung. */
    static int focusText() {
        int r = (accent >> 16) & 0xFF, g = (accent >> 8) & 0xFF, b = accent & 0xFF;
        boolean light = (299 * r + 587 * g + 114 * b) / 1000 > DARK_TEXT_ABOVE;
        return light ? 0xFF1C1C1E : 0xFFFFFFFF;
    }
}
