package app.calmtv.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;

/**
 * Schmale Reglerleiste: Spur, gefüllter Teil in der Akzentfarbe und ein Knopf. Sie hat keine eigene Bedienung;
 * in Zeilen steuert die Zeile, in der Vorschau-Leiste ({@code standalone}) ist die Leiste selbst fokussierbar.
 */
final class SettingsSlider extends View {
    private static final int HEIGHT_DP = 20, TRACK_DP = 4, KNOB_DP = 7, KNOB_LIT_DP = 9;
    private static final int TRACK_ON_DARK = 0x33FFFFFF, TRACK_ON_LIGHT = 0x33000000;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final boolean standalone;
    private final float density;
    private int min, max = 1, value;
    private boolean rowFocused, selfFocused;

    SettingsSlider(Context c, boolean standalone) {
        super(c);
        this.standalone = standalone;
        density = Ui.density(c);
        if (standalone) {
            setFocusable(true);
            setClickable(true);
        }
    }

    void setRange(int min, int max, int value) {
        this.min = min;
        this.max = Math.max(min + 1, max);
        this.value = Math.max(min, Math.min(this.max, value));
        invalidate();
    }

    /** Die umgebende Zeile hat den Fokus (helle Fläche): Farben kehren sich um. */
    void setRowFocused(boolean focused) {
        rowFocused = focused;
        invalidate();
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthSpec), Math.round(HEIGHT_DP * density));
    }

    @Override protected void onFocusChanged(boolean gainFocus, int direction, Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
        if (standalone) {
            selfFocused = gainFocus;
            invalidate();
        }
    }

    @Override protected void onDraw(Canvas canvas) {
        float knobRadius = (rowFocused || selfFocused ? KNOB_LIT_DP : KNOB_DP) * density;
        float left = KNOB_LIT_DP * density, right = getWidth() - left, cy = getHeight() / 2f;
        float half = TRACK_DP * density / 2f;
        float share = (value - min) / (float) (max - min);
        float knobX = left + (right - left) * share;
        int fill = rowFocused ? Look.focusText() : Look.focusRing();

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(rowFocused ? TRACK_ON_LIGHT : TRACK_ON_DARK);
        rect.set(left, cy - half, right, cy + half);
        canvas.drawRoundRect(rect, half, half, paint);
        paint.setColor(fill);
        rect.set(left, cy - half, knobX, cy + half);
        canvas.drawRoundRect(rect, half, half, paint);
        canvas.drawCircle(knobX, cy, knobRadius, paint);
    }
}
