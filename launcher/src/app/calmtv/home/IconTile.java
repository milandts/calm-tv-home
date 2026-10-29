package app.calmtv.home;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/**
 * Die Kachel für Apps ohne TV-Banner: ihr Symbol mittig auf einem senkrechten Verlauf in der Durchschnittsfarbe des Symbols.
 * Jede Kachel bekommt über {@link #getConstantState} ihre eigene Kopie, denn ein ImageView setzt die Maße seines Bildes, und Kacheln
 * verschiedener Größe (Dock, Raster, Suche) dürfen sich keines teilen.
 */
final class IconTile extends Drawable {
    private static final int SAMPLE = 12, FALLBACK = 0xFF3A3F47;
    private static final float ICON_SHARE = 0.62f, LIGHTEN = 0.12f, DARKEN = 0.45f;

    private final Drawable icon;
    private final int average;
    private final Paint backdrop = new Paint();

    static Drawable of(Drawable icon) {
        return new IconTile(icon, averageColor(icon));
    }

    private IconTile(Drawable icon, int average) {
        this.icon = icon;
        this.average = average;
    }

    /** Eine Kopie mit eigenem Symbol; die Durchschnittsfarbe wird nicht noch einmal berechnet. */
    @Override public ConstantState getConstantState() {
        ConstantState iconState = icon.getConstantState();
        return iconState == null ? null : new Copy(iconState, average);
    }

    /** Benannte Klasse statt einer anonymen: der D8 dieser Build-Kette stürzt bei anonymen Klassen ab. */
    private static final class Copy extends ConstantState {
        private final ConstantState iconState;
        private final int average;

        Copy(ConstantState iconState, int average) {
            this.iconState = iconState;
            this.average = average;
        }

        @Override public Drawable newDrawable() {
            return new IconTile(iconState.newDrawable().mutate(), average);
        }

        @Override public int getChangingConfigurations() {
            return 0;
        }
    }

    @Override protected void onBoundsChange(Rect bounds) {
        backdrop.setShader(new LinearGradient(0, bounds.top, 0, bounds.bottom,
                mix(average, Color.WHITE, LIGHTEN), mix(average, Color.BLACK, DARKEN), Shader.TileMode.CLAMP));
        int size = Math.round(bounds.height() * ICON_SHARE);
        int left = bounds.centerX() - size / 2, top = bounds.centerY() - size / 2;
        icon.setBounds(left, top, left + size, top + size);
    }

    @Override public void draw(Canvas canvas) {
        canvas.drawRect(getBounds(), backdrop);
        icon.draw(canvas);
    }

    @Override public void setAlpha(int alpha) {}

    @Override public void setColorFilter(ColorFilter filter) {}

    @Override public int getOpacity() {
        return PixelFormat.OPAQUE;
    }

    /** Mittelwert der deckenden Bildpunkte eines kleinen Abzugs; ein leeres Symbol ergibt ein neutrales Grau. */
    private static int averageColor(Drawable drawable) {
        Bitmap sample = Bitmap.createBitmap(SAMPLE, SAMPLE, Bitmap.Config.ARGB_8888);
        drawable.setBounds(0, 0, SAMPLE, SAMPLE);
        drawable.draw(new Canvas(sample));
        long red = 0, green = 0, blue = 0, count = 0;
        for (int y = 0; y < SAMPLE; y++) {
            for (int x = 0; x < SAMPLE; x++) {
                int pixel = sample.getPixel(x, y);
                if (Color.alpha(pixel) < 128) continue;
                red += Color.red(pixel);
                green += Color.green(pixel);
                blue += Color.blue(pixel);
                count++;
            }
        }
        sample.recycle();
        return count == 0 ? FALLBACK : Color.rgb((int) (red / count), (int) (green / count), (int) (blue / count));
    }

    /** Mischt zwei Farben; {@code share} = 0 ergibt {@code from}, 1 ergibt {@code to}. */
    private static int mix(int from, int to, float share) {
        return Color.rgb(
                Math.round(Color.red(from) + (Color.red(to) - Color.red(from)) * share),
                Math.round(Color.green(from) + (Color.green(to) - Color.green(from)) * share),
                Math.round(Color.blue(from) + (Color.blue(to) - Color.blue(from)) * share));
    }
}
