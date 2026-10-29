package app.calmtv.home.dream;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** Der Schalter, gezeichnet im Code. Auf der Fokusfläche tauschen Schiene und Knopf ihre Farben, damit er lesbar bleibt. */
final class SetupSwitch extends View implements ValueAnimator.AnimatorUpdateListener {
    private static final long MOVE_MS = 140;

    private final SetupStyle style;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF track = new RectF();
    private boolean isChecked, isRowFocused;
    private float position;   // 0 = aus (links), 1 = an (rechts)
    private ValueAnimator mover;

    SetupSwitch(Context context, SetupStyle style, boolean checked) {
        super(context);
        this.style = style;
        isChecked = checked;
        position = checked ? 1f : 0f;
    }

    boolean isChecked() {
        return isChecked;
    }

    void setChecked(boolean checked, boolean isAnimated) {
        isChecked = checked;
        if (mover != null) mover.cancel();
        if (!isAnimated) {
            position = checked ? 1f : 0f;
            invalidate();
            return;
        }
        mover = ValueAnimator.ofFloat(position, checked ? 1f : 0f);
        mover.setDuration(MOVE_MS);
        mover.addUpdateListener(this);
        mover.start();
    }

    @Override
    public void onAnimationUpdate(ValueAnimator animation) {
        position = (float) animation.getAnimatedValue();
        invalidate();
    }

    void setRowFocused(boolean focused) {
        isRowFocused = focused;
        invalidate();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (mover != null) mover.cancel();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth(), h = getHeight();
        int trackColor, knobColor;
        if (isRowFocused) {
            trackColor = isChecked ? style.onAccent : SetupRow.withAlpha(style.onAccent, 0x40);
            knobColor = isChecked ? style.accent : SetupRow.withAlpha(style.onAccent, 0xE6);
        } else {
            trackColor = isChecked ? style.accent : 0x33FFFFFF;
            knobColor = isChecked ? style.onAccent : 0xE6FFFFFF;
        }
        paint.setColor(trackColor);
        track.set(0, 0, w, h);
        canvas.drawRoundRect(track, h / 2f, h / 2f, paint);
        float radius = h / 2f - h * 0.13f;
        paint.setColor(knobColor);
        canvas.drawCircle(h / 2f + position * (w - h), h / 2f, radius, paint);
    }
}
