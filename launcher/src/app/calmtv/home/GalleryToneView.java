package app.calmtv.home;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

/** Helligkeit anpassen (-30 bis +30 Prozent): Regler mit Live-Vorschau am Bild. Auswahltaste speichert über {@link Wallpapers#setTone}. */
final class GalleryToneView extends GalleryOverlay {
    private static final int MIN = -30, MAX = 30, STEP = 5;
    private static final float IMAGE_W_DP = 560, IMAGE_H_DP = 315, IMAGE_TOP_DP = 84, LABEL_TOP_DP = 418, BAR_TOP_DP = 456;
    private static final float BAR_W_DP = 480, BAR_H_DP = 28;

    /** Der Regler: Spur, Füllung von der Mitte aus, Knopf. */
    private static final class Bar extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int value;

        Bar(Context c) {
            super(c);
        }

        void setValue(int v) {
            value = v;
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            float w = getWidth(), h = getHeight(), track = Ui.px(getContext(), 8), r = track / 2f;
            float pad = h / 2f, cy = h / 2f;
            float x0 = pad, x1 = w - pad;
            float mid = (x0 + x1) / 2f;
            float knob = x0 + (value - MIN) / (float) (MAX - MIN) * (x1 - x0);
            paint.setColor(Ui.ROW_PRESSED);
            canvas.drawRoundRect(x0, cy - r, x1, cy + r, r, r, paint);
            paint.setColor(Look.focusFill());
            canvas.drawRoundRect(Math.min(mid, knob), cy - r, Math.max(mid, knob), cy + r, r, r, paint);
            paint.setColor(Ui.TEXT_FAINT);
            canvas.drawRect(mid - 1, cy - r - 3, mid + 1, cy + r + 3, paint);
            paint.setColor(Look.focusFill());
            canvas.drawCircle(knob, cy, h / 2f - 2, paint);
        }
    }

    private final Wallpapers wallpapers;
    private final ImageView picture;
    private final TextView label;
    private final Bar bar;
    private int value;

    GalleryToneView(Context c, Wallpapers wallpapers, Wallpapers.Entry entry, Done done) {
        super(c, entry, "Helligkeit anpassen",
                "Links und rechts ändern die Helligkeit, die Auswahltaste speichert, Zurück verwirft.", done);
        this.wallpapers = wallpapers;
        value = GalleryPrefs.clamp(entry.tone, MIN, MAX);

        picture = new ImageView(c);
        picture.setScaleType(ImageView.ScaleType.CENTER_CROP);
        picture.setBackgroundColor(Ui.ROW);
        picture.setOutlineProvider(new RoundOutline(Ui.px(c, 12)));
        picture.setClipToOutline(true);
        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(Ui.px(c, IMAGE_W_DP), Ui.px(c, IMAGE_H_DP), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        pp.topMargin = Ui.px(c, IMAGE_TOP_DP);
        addView(picture, pp);

        label = Ui.label(c, "", Ui.sp(17), 600, Ui.TEXT);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        lp.topMargin = Ui.px(c, LABEL_TOP_DP);
        addView(label, lp);

        bar = new Bar(c);
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(Ui.px(c, BAR_W_DP), Ui.px(c, BAR_H_DP), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        bp.topMargin = Ui.px(c, BAR_TOP_DP);
        addView(bar, bp);
        refresh();
    }

    @Override protected void onImage(Bitmap image) {
        picture.setImageBitmap(image);
        refresh();
    }

    private void refresh() {
        label.setText("Helligkeit  " + (value > 0 ? "+" : "") + value + " %");
        bar.setValue(value);
        picture.setColorFilter(value == 0 ? null : new ColorMatrixColorFilter(Wallpapers.toneMatrix(value)));
    }

    @Override protected void onArrow(int keyCode, int repeat) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) value = Math.max(MIN, value - STEP);
        else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) value = Math.min(MAX, value + STEP);
        else return;
        refresh();
    }

    @Override protected void onSave() {
        wallpapers.setTone(entry.id, value);
    }

    @Override void release() {
        super.release();
        picture.setImageDrawable(null);
    }
}
