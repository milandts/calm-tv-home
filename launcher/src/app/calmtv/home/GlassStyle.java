package app.calmtv.home;

import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;

/**
 * Die Glas-Einstellungen aus {@link Opts}, einmal gelesen und zwischengespeichert, damit {@link Glass} und
 * {@link GlassText} sie bei jedem Zeichnen ohne Allokation abfragen können. Bei jeder Änderung steigt {@link #version()};
 * Ansichten vergleichen sie beim Zeichnen und bauen ihre Farbfilter und Verläufe nur dann neu.
 * Die Standardwerte ergeben genau das Aussehen vor der Einstellbarkeit.
 */
final class GlassStyle {
    private static final Watcher WATCHER = new Watcher();
    private static Config registeredWith;   // die Konfiguration, bei der der Beobachter hängt (nach recreate() ist es eine neue)
    private static int version = 1;
    private static int opacity = 100, darken, saturation = 160, brightness = 78, rim = 100, sheen = 100, shadow = 100;
    private static ColorMatrixColorFilter photoFilter, textFilter;

    private GlassStyle() {}

    /** Meldet sich einmal bei {@link Config} an (sobald es sie gibt) und liest die Werte. Billig, darf oft aufgerufen werden. */
    static void ensure() {
        Config config = Config.i();
        if (config == null || config == registeredWith) return;
        registeredWith = config;
        reload();
        config.addListener(WATCHER);
    }

    private static final class Watcher implements Config.Listener {
        @Override public void onChanged(String key) {
            reload();
        }
    }

    private static void reload() {
        int o = Config.val(Opts.GLASS_OPACITY), d = Config.val(Opts.GLASS_DARKEN), s = Config.val(Opts.GLASS_SATURATION);
        int b = Config.val(Opts.GLASS_BRIGHTNESS), r = Config.val(Opts.GLASS_RIM), h = Config.val(Opts.GLASS_SHEEN);
        int sh = Config.val(Opts.GLASS_SHADOW);
        boolean filterChanged = photoFilter == null || s != saturation || b != brightness;
        if (!filterChanged && o == opacity && d == darken && r == rim && h == sheen && sh == shadow) return;
        opacity = o;
        darken = d;
        saturation = s;
        brightness = b;
        rim = r;
        sheen = h;
        shadow = sh;
        if (filterChanged) {
            photoFilter = buildPhotoFilter();
            textFilter = buildTextFilter();
        }
        version++;
    }

    /** Foto im Glas: kräftigere Farben, leicht abgedunkelt. */
    private static ColorMatrixColorFilter buildPhotoFilter() {
        ColorMatrix vivid = new ColorMatrix();
        vivid.setSaturation(saturation / 100f);
        ColorMatrix dim = new ColorMatrix();
        float scale = brightness / 100f;
        dim.setScale(scale, scale, scale, 1f);
        vivid.postConcat(dim);
        return new ColorMatrixColorFilter(vivid);
    }

    /** Foto in den Buchstaben von {@link GlassText}: gesättigt und weit Richtung Weiß verblasst. */
    private static ColorMatrixColorFilter buildTextFilter() {
        ColorMatrix frost = new ColorMatrix();
        frost.setSaturation(saturation / 100f);
        final float gain = 0.4f, lift = 158f;   // jeder Farbkanal: 0,4 mal Wert plus 158, also weit Richtung Weiß
        frost.postConcat(new ColorMatrix(new float[] {
                gain, 0, 0, 0, lift,
                0, gain, 0, 0, lift,
                0, 0, gain, 0, lift,
                0, 0, 0, 1, 0}));
        return new ColorMatrixColorFilter(frost);
    }

    static int version() { return version; }

    /** Alpha-Anteil der Foto-Schicht, 0 bis 1. */
    static float opacity() { return opacity / 100f; }

    /** Alpha (0 bis 255) der zusätzlichen schwarzen Tönung. */
    static int darkenAlpha() { return Math.round(255 * darken / 100f); }

    static ColorMatrixColorFilter photoFilter() {
        if (photoFilter == null) photoFilter = buildPhotoFilter();
        return photoFilter;
    }

    static ColorMatrixColorFilter textFilter() {
        if (textFilter == null) textFilter = buildTextFilter();
        return textFilter;
    }

    /** Faktoren 0 bis 1 für Rand, Glanz und Schatten. */
    static float rim() { return rim / 100f; }

    static float sheen() { return sheen / 100f; }

    static float shadow() { return shadow / 100f; }
}
