package app.calmtv.home;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.View;

/**
 * Farbkreis mit Namen darunter (Akzentfarbe in der Vorschau-Leiste). Gewählt = Häkchen und heller Außenring,
 * fokussiert = Ring in der Akzentfarbe und leichte Vergrößerung. Alles wird direkt gezeichnet, ohne Unteransichten.
 */
final class SettingsSwatch extends View {
    private static final int WIDTH_DP = 92, HEIGHT_DP = 88, RADIUS_DP = 22, CENTER_Y_DP = 32, RING_GAP_DP = 5;
    private static final float FOCUS_SCALE = 0.1f;
    private static final int EDGE_COLOR = 0x33000000, CHOSEN_RING = 0xCCFFFFFF, LIGHT_CHECK = 0xFF1C1C1E;
    private static final int LIGHT_ABOVE = 140;

    private final int color;
    private final String name;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    private boolean chosen;
    private float focusProgress;
    private ValueAnimator animator;

    SettingsSwatch(Context c, String name, int color) {
        super(c);
        this.name = name;
        this.color = color;
        density = Ui.density(c);
        setFocusable(true);
        setClickable(true);
        setContentDescription(name);
    }

    void setChosen(boolean chosen) {
        if (this.chosen == chosen) return;
        this.chosen = chosen;
        invalidate();
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(Math.round(WIDTH_DP * density), Math.round(HEIGHT_DP * density));
    }

    @Override protected void onFocusChanged(boolean gainFocus, int direction, Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
        if (animator != null) animator.cancel();
        animator = ValueAnimator.ofFloat(focusProgress, gainFocus ? 1f : 0f);
        animator.setDuration(Ui.ms(Ui.FAST));
        animator.setInterpolator(Ui.EASE);
        animator.addUpdateListener(a -> {
            focusProgress = (float) a.getAnimatedValue();
            setScaleX(1f + FOCUS_SCALE * focusProgress);
            setScaleY(1f + FOCUS_SCALE * focusProgress);
            invalidate();
        });
        animator.start();
    }

    @Override protected void onDraw(Canvas canvas) {
        float cx = getWidth() / 2f, cy = CENTER_Y_DP * density, r = RADIUS_DP * density;

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        canvas.drawCircle(cx, cy, r, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1f, density));
        paint.setColor(EDGE_COLOR);   // feine dunkle Kante, damit Weiß auf hellem Grund erkennbar bleibt
        canvas.drawCircle(cx, cy, r - 0.5f * density, paint);

        float ringRadius = r + RING_GAP_DP * density;
        if (chosen) {
            paint.setStrokeWidth(2f * density);
            paint.setColor(withAlpha(CHOSEN_RING, 1f - focusProgress));
            canvas.drawCircle(cx, cy, ringRadius, paint);
            drawCheck(canvas, cx, cy, r);
        }
        if (focusProgress > 0f) {
            paint.setStrokeWidth(3f * density);
            paint.setColor(withAlpha(Look.focusRing(), focusProgress));
            canvas.drawCircle(cx, cy, ringRadius, paint);
        }

        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(Ui.font(getContext(), chosen ? 700 : 600));
        paint.setTextSize(Ui.sp(Ui.SP_LABEL) * getResources().getDisplayMetrics().scaledDensity);
        paint.setColor(chosen || focusProgress > 0.5f ? Ui.TEXT : Ui.TEXT_DIM);
        canvas.drawText(name, cx, cy + r + 26f * density, paint);
    }

    private void drawCheck(Canvas canvas, float cx, float cy, float r) {
        int r8 = (color >> 16) & 0xFF, g8 = (color >> 8) & 0xFF, b8 = color & 0xFF;
        boolean light = (299 * r8 + 587 * g8 + 114 * b8) / 1000 > LIGHT_ABOVE;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(2.5f * density);
        paint.setColor(light ? LIGHT_CHECK : 0xFFFFFFFF);
        canvas.drawLine(cx - 0.32f * r, cy + 0.02f * r, cx - 0.08f * r, cy + 0.28f * r, paint);
        canvas.drawLine(cx - 0.08f * r, cy + 0.28f * r, cx + 0.36f * r, cy - 0.24f * r, paint);
        paint.setStrokeCap(Paint.Cap.BUTT);
    }

    private static int withAlpha(int color, float factor) {
        int alpha = Math.round(((color >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, factor)));
        return (alpha << 24) | (color & 0x00FFFFFF);
    }
}
