package app.calmtv.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** Fortschritts-Punkte-Band der Einführung: der aktuelle Schritt ist ein länglicher Punkt. */
final class WelcomeDots extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final int count;
    private final float dot, gap, activeWidth;
    private int current;

    WelcomeDots(Context c, int count) {
        super(c);
        this.count = count;
        dot = Ui.px(c, 6);
        gap = Ui.px(c, 8);
        activeWidth = Ui.px(c, 20);
    }

    void setStep(int step) {
        current = step;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(Math.round((count - 1) * (dot + gap) + activeWidth), Math.round(dot));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float x = 0;
        for (int i = 0; i < count; i++) {
            float width = i == current ? activeWidth : dot;
            paint.setColor(i == current ? Ui.TEXT : Ui.TEXT_FAINT);
            rect.set(x, 0, x + width, dot);
            canvas.drawRoundRect(rect, dot / 2f, dot / 2f, paint);
            x += width + gap;
        }
    }
}
