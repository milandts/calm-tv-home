package app.calmtv.home.dream;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Ein zufälliges Foto aus den Assets, stark verkleinert dekodiert und weichgezeichnet (nur wenige KB), als Hintergrund der Einstellungen. */
final class BlurredBackdrop {
    private static final String PHOTO_DIR = "photos";
    private static final int SAMPLE_SIZE = 16, BLUR_RADIUS = 4, BLUR_PASSES = 2;

    private BlurredBackdrop() {}

    /** Blockiert kurz (Dekodieren); im Hintergrund-Thread aufrufen. Gibt null zurück, wenn kein Foto lesbar ist. */
    static Bitmap load(Context context) {
        try {
            String[] files = context.getAssets().list(PHOTO_DIR);
            List<String> photos = new ArrayList<>();
            if (files != null) for (String f : files) if (f.endsWith(".jpg") || f.endsWith(".webp")) photos.add(f);
            if (photos.isEmpty()) return null;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = SAMPLE_SIZE;
            try (InputStream in = context.getAssets().open(PHOTO_DIR + "/" + photos.get(new Random().nextInt(photos.size())))) {
                Bitmap small = BitmapFactory.decodeStream(in, null, options);
                return small == null ? null : blur(small);
            }
        } catch (IOException | RuntimeException | OutOfMemoryError e) {
            return null;   // Hintergrund bleibt der dunkle Verlauf
        }
    }

    private static Bitmap blur(Bitmap source) {
        int w = source.getWidth(), h = source.getHeight();
        int[] pixels = new int[w * h];
        source.getPixels(pixels, 0, w, 0, 0, w, h);
        int[] scratch = new int[w * h];
        for (int pass = 0; pass < BLUR_PASSES; pass++) {
            boxBlur(pixels, scratch, w, h, true);
            boxBlur(scratch, pixels, w, h, false);
        }
        return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888);
    }

    /** Mittelt je Pixel die Nachbarn in einer Richtung (Rand wird wiederholt). */
    private static void boxBlur(int[] in, int[] out, int w, int h, boolean isHorizontal) {
        int lines = isHorizontal ? h : w, length = isHorizontal ? w : h;
        int stride = isHorizontal ? 1 : w, lineStep = isHorizontal ? w : 1;
        int window = 2 * BLUR_RADIUS + 1;
        for (int line = 0; line < lines; line++) {
            int base = line * lineStep;
            for (int i = 0; i < length; i++) {
                int r = 0, g = 0, b = 0;
                for (int k = -BLUR_RADIUS; k <= BLUR_RADIUS; k++) {
                    int p = in[base + Math.max(0, Math.min(length - 1, i + k)) * stride];
                    r += (p >> 16) & 0xFF;
                    g += (p >> 8) & 0xFF;
                    b += p & 0xFF;
                }
                out[base + i * stride] = 0xFF000000 | (r / window) << 16 | (g / window) << 8 | (b / window);
            }
        }
    }
}
