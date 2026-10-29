package app.calmtv.home;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;

import java.io.IOException;
import java.io.InputStream;

/**
 * Macht aus einer Bilddatei das, was die Oberfläche zeigt: aufrecht dekodiert (höchstens doppelt so groß wie nötig), auf das
 * Seitenverhältnis des Schirms zugeschnitten (der Fokuspunkt möglichst in der Mitte), in der Helligkeit angepasst und als kleine,
 * stark unscharfe Kopie für Glas und Ansichten. Alles läuft auf Hintergrundthreads.
 */
final class WallpaperRender {
    private static final int BLUR_WIDTH = 192, BLUR_HEIGHT = 108, BLUR_RADIUS = 4, BLUR_ROUNDS = 3;

    private final WallpaperSource source;

    WallpaperRender(WallpaperSource source) {
        this.source = source;
    }

    /** Matrix für die Helligkeit in Prozent (-30 bis +30): alle Farbkanäle mit 1 + Prozent/100. */
    static ColorMatrix toneMatrix(int percent) {
        float factor = 1f + percent / 100f;
        ColorMatrix matrix = new ColorMatrix();
        matrix.setScale(factor, factor, factor, 1f);
        return matrix;
    }

    /** Dekodiert aufrecht, höchstens um den Faktor 2 größer als gewünscht (Zuschnitt und Skalierung folgen danach). */
    private Bitmap decode(String id, int width, int height) throws IOException {
        BitmapFactory.Options probe = new BitmapFactory.Options();
        probe.inJustDecodeBounds = true;
        try (InputStream in = source.open(id)) {
            BitmapFactory.decodeStream(in, null, probe);
        }
        if (probe.outWidth <= 0 || probe.outHeight <= 0) return null;
        int rotation = source.rotationOf(id);
        boolean isSideways = rotation == 90 || rotation == 270;
        int wantedWidth = isSideways ? height : width, wantedHeight = isSideways ? width : height;
        int sample = 1;
        while (probe.outWidth / (sample * 2) >= wantedWidth && probe.outHeight / (sample * 2) >= wantedHeight) sample *= 2;

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;   // volle Farbe: 16 Bit halbieren den Speicher, würden aber Himmel und Nebel in Stufen zeigen
        Bitmap decoded;
        try (InputStream in = source.open(id)) {
            decoded = BitmapFactory.decodeStream(in, null, options);
        }
        if (decoded == null || rotation == 0) return decoded;
        Matrix turn = new Matrix();
        turn.postRotate(rotation);
        return swap(decoded, Bitmap.createBitmap(decoded, 0, 0, decoded.getWidth(), decoded.getHeight(), turn, true));
    }

    /**
     * Schneidet das Bild auf das Seitenverhältnis width:height zu, den Fokuspunkt (Prozent) möglichst in der Mitte und ohne über den Rand
     * hinaus, skaliert auf genau width x height und wendet die Helligkeit an, in einem Schritt und ohne Zwischenkopien.
     */
    Bitmap render(String id, int width, int height, int focusX, int focusY, int tone) throws IOException {
        Bitmap picture = decode(id, width, height);
        if (picture == null) return null;
        float sourceWidth = picture.getWidth(), sourceHeight = picture.getHeight();
        float scale = Math.max(width / sourceWidth, height / sourceHeight);
        float cropWidth = Math.min(sourceWidth, width / scale), cropHeight = Math.min(sourceHeight, height / scale);
        float left = Math.max(0f, Math.min(sourceWidth - cropWidth, focusX / 100f * sourceWidth - cropWidth / 2f));
        float top = Math.max(0f, Math.min(sourceHeight - cropHeight, focusY / 100f * sourceHeight - cropHeight / 2f));
        Rect crop = new Rect(Math.round(left), Math.round(top),
                Math.min(picture.getWidth(), Math.round(left + cropWidth)), Math.min(picture.getHeight(), Math.round(top + cropHeight)));
        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        if (tone != 0) paint.setColorFilter(new ColorMatrixColorFilter(toneMatrix(tone)));
        Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        new Canvas(result).drawBitmap(picture, crop, new Rect(0, 0, width, height), paint);
        picture.recycle();
        return result;
    }

    /** Das ganze Bild klein (passt in maxWidth x maxHeight), ohne Zuschnitt und Helligkeit. */
    Bitmap overview(String id, int maxWidth, int maxHeight) throws IOException {
        Bitmap picture = decode(id, maxWidth, maxHeight);
        if (picture == null) return null;
        float scale = Math.min(1f, Math.min(maxWidth / (float) picture.getWidth(), maxHeight / (float) picture.getHeight()));
        if (scale >= 1f) return picture;
        int width = Math.max(1, Math.round(picture.getWidth() * scale)), height = Math.max(1, Math.round(picture.getHeight() * scale));
        return swap(picture, Bitmap.createScaledBitmap(picture, width, height, true));
    }

    /** Gibt ein Zwischenbild sofort frei, statt auf die Speicherbereinigung zu warten. */
    private static Bitmap swap(Bitmap old, Bitmap next) {
        if (next != old) old.recycle();
        return next;
    }

    // ---- Unscharfe Kopie -------------------------------------------------------------------------

    /** Eine kleine, stark unscharfe Kopie für das Glas und die Ansichten hinter Dock, Apps und Einstellungen. */
    static Bitmap blurred(Bitmap full) {
        Bitmap small = Bitmap.createScaledBitmap(full, BLUR_WIDTH, BLUR_HEIGHT, true);
        int[] pixels = new int[BLUR_WIDTH * BLUR_HEIGHT];
        small.getPixels(pixels, 0, BLUR_WIDTH, 0, 0, BLUR_WIDTH, BLUR_HEIGHT);
        small.recycle();
        int[] scratch = new int[pixels.length];
        for (int round = 0; round < BLUR_ROUNDS; round++) {
            average(pixels, scratch, true);
            average(scratch, pixels, false);
        }
        return Bitmap.createBitmap(pixels, BLUR_WIDTH, BLUR_HEIGHT, Bitmap.Config.ARGB_8888);
    }

    /** Ein Durchgang Mittelwert-Filter (gleitendes Fenster) entlang der Zeilen oder Spalten. */
    private static void average(int[] from, int[] to, boolean alongRows) {
        int length = alongRows ? BLUR_WIDTH : BLUR_HEIGHT, lines = alongRows ? BLUR_HEIGHT : BLUR_WIDTH, window = 2 * BLUR_RADIUS + 1;
        for (int line = 0; line < lines; line++) {
            int red = 0, green = 0, blue = 0;
            for (int i = -BLUR_RADIUS; i <= BLUR_RADIUS; i++) {
                int pixel = from[offset(line, clamp(i, length), alongRows)];
                red += (pixel >> 16) & 255;
                green += (pixel >> 8) & 255;
                blue += pixel & 255;
            }
            for (int i = 0; i < length; i++) {
                to[offset(line, i, alongRows)] = 0xFF000000 | (red / window) << 16 | (green / window) << 8 | (blue / window);
                int entering = from[offset(line, clamp(i + BLUR_RADIUS + 1, length), alongRows)];
                int leaving = from[offset(line, clamp(i - BLUR_RADIUS, length), alongRows)];
                red += ((entering >> 16) & 255) - ((leaving >> 16) & 255);
                green += ((entering >> 8) & 255) - ((leaving >> 8) & 255);
                blue += (entering & 255) - (leaving & 255);
            }
        }
    }

    private static int clamp(int index, int length) {
        return Math.max(0, Math.min(length - 1, index));
    }

    private static int offset(int line, int i, boolean alongRows) {
        return alongRows ? line * BLUR_WIDTH + i : i * BLUR_WIDTH + line;
    }
}
