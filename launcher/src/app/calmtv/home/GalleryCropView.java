package app.calmtv.home;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.KeyEvent;

/**
 * Ausschnitt wählen: das ganze Bild klein, darüber ein Rahmen im Seitenverhältnis des Bildschirms. Pfeiltasten verschieben den
 * Fokuspunkt in 4-Prozent-Schritten; der Rahmen bleibt im Bild. Auswahltaste speichert über {@link Wallpapers#setFocus}.
 */
final class GalleryCropView extends GalleryOverlay {
    private static final int STEP = 4;
    private static final float TOP_DP = 92, BOTTOM_DP = 76, SIDE_DP = 64, CROSS_DP = 8;
    private static final int OUTSIDE = 0xB3000000;

    private final Wallpapers wallpapers;
    private final float screenAspect;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect imageRect = new Rect();
    private final RectF window = new RectF();
    private Bitmap image;
    private int focusX, focusY;

    GalleryCropView(Context c, Wallpapers wallpapers, Wallpapers.Entry entry, float screenAspect, Done done) {
        super(c, entry, "Ausschnitt wählen",
                "Der helle Rahmen zeigt, was auf dem Fernseher zu sehen ist. Pfeiltasten verschieben ihn, die Auswahltaste speichert, Zurück verwirft.",
                done);
        this.wallpapers = wallpapers;
        this.screenAspect = screenAspect;
        this.focusX = entry.focusX;
        this.focusY = entry.focusY;
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(Ui.px(c, 3));
        setWillNotDraw(false);
    }

    @Override protected void onImage(Bitmap bitmap) {
        image = bitmap;
        clampFocus();
        invalidate();
    }

    /** Anteil des Bildes (0..1), den der Rahmen in Breite und Höhe abdeckt. */
    private float fracW() {
        float imageAspect = image.getWidth() / (float) image.getHeight();
        return imageAspect > screenAspect ? screenAspect / imageAspect : 1f;
    }

    private float fracH() {
        float imageAspect = image.getWidth() / (float) image.getHeight();
        return imageAspect > screenAspect ? 1f : imageAspect / screenAspect;
    }

    private void clampFocus() {
        int loX = Math.round(50 * fracW()), loY = Math.round(50 * fracH());
        focusX = GalleryPrefs.clamp(focusX, loX, 100 - loX);
        focusY = GalleryPrefs.clamp(focusY, loY, 100 - loY);
    }

    @Override protected void onArrow(int keyCode, int repeat) {
        if (image == null) return;
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) focusX -= STEP;
        else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) focusX += STEP;
        else if (keyCode == KeyEvent.KEYCODE_DPAD_UP) focusY -= STEP;
        else focusY += STEP;
        clampFocus();
        invalidate();
    }

    @Override protected void onSave() {
        if (image != null) wallpapers.setFocus(entry.id, focusX, focusY);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float left = Ui.px(getContext(), SIDE_DP), top = Ui.px(getContext(), TOP_DP);
        float areaW = getWidth() - 2 * left, areaH = getHeight() - top - Ui.px(getContext(), BOTTOM_DP);
        if (image == null) {
            paint.setColor(Ui.ROW);
            canvas.drawRoundRect(left, top, left + areaW, top + areaH, Ui.px(getContext(), 12), Ui.px(getContext(), 12), paint);
            return;
        }
        float scale = Math.min(areaW / image.getWidth(), areaH / image.getHeight());
        int w = Math.round(image.getWidth() * scale), h = Math.round(image.getHeight() * scale);
        int x = Math.round(left + (areaW - w) / 2f), y = Math.round(top + (areaH - h) / 2f);
        imageRect.set(x, y, x + w, y + h);
        paint.setColor(0xFFFFFFFF);
        canvas.drawBitmap(image, null, imageRect, paint);

        float ww = w * fracW(), wh = h * fracH();
        float wx = x + Math.max(0f, Math.min(w - ww, focusX / 100f * w - ww / 2f));
        float wy = y + Math.max(0f, Math.min(h - wh, focusY / 100f * h - wh / 2f));
        window.set(wx, wy, wx + ww, wy + wh);
        paint.setColor(OUTSIDE);
        canvas.drawRect(imageRect.left, imageRect.top, imageRect.right, window.top, paint);
        canvas.drawRect(imageRect.left, window.bottom, imageRect.right, imageRect.bottom, paint);
        canvas.drawRect(imageRect.left, window.top, window.left, window.bottom, paint);
        canvas.drawRect(window.right, window.top, imageRect.right, window.bottom, paint);
        ring.setColor(Look.focusRing());
        canvas.drawRect(window, ring);

        float cx = window.centerX(), cy = window.centerY(), arm = Ui.px(getContext(), CROSS_DP);
        ring.setColor(0xCCFFFFFF);
        canvas.drawLine(cx - arm, cy, cx + arm, cy, ring);
        canvas.drawLine(cx, cy - arm, cx, cy + arm, ring);
    }
}
