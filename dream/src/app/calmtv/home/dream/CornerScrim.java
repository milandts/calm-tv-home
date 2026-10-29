package app.calmtv.home.dream;

import android.graphics.Bitmap;

/**
 * Weicher, viertelkreisförmiger Schatten für die Ecke mit Uhr und Wetter (hält die Schrift auf hellen Fotos lesbar).
 * Als winziges Alpha-Bild erzeugt und vom ImageView hochskaliert: Verläufe mit Radial-Shadern zeichnet die GPU des Sticks
 * als einfarbige Fläche. Die Mitte liegt unten links; andere Ecken entstehen durch Spiegeln der View.
 */
final class CornerScrim {
    private static final int SIZE = 96;
    private static final float PEAK_ALPHA = 0.68f;

    private CornerScrim() {}

    static Bitmap create() {
        int[] pixels = new int[SIZE * SIZE];
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                float dx = x + 0.5f, dy = SIZE - y - 0.5f;   // Abstand zur Ecke unten links
                float t = Math.min(1f, (float) Math.sqrt(dx * dx + dy * dy) / SIZE);
                float fade = 1f - t * t * (3f - 2f * t);      // weiches Ausklingen (Smoothstep)
                pixels[y * SIZE + x] = Math.round(PEAK_ALPHA * fade * 255f) << 24;   // Schwarz mit Alpha
            }
        }
        return Bitmap.createBitmap(pixels, SIZE, SIZE, Bitmap.Config.ARGB_8888);
    }
}
