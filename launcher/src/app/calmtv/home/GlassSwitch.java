package app.calmtv.home;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/**
 * Schalter im Glas-Look: 52 x 30 dp, Track mit Knopf. An = Akzentfarbe mit Knopf in der Schriftfarbe der Akzentfläche
 * (bei Weiß also ein dunkler Knopf), aus = durchscheinender Track mit hellem Knopf. Auf einer fokussierten (hellen)
 * Zeile kehren sich die Farben um ({@link #setRowFocused}), damit der Schalter dort lesbar bleibt.
 * Der Schalter ist selbst nicht fokussierbar; Fokus und Auswahltaste gehören der Zeile.
 */
final class GlassSwitch extends View {
    private static final float WIDTH_DP = 52, HEIGHT_DP = 30, KNOB_INSET_DP = 3;
    private static final int TRACK_OFF = 0x59FFFFFF, KNOB_OFF = 0xF2FFFFFF;
    private static final int KNOB_SHADOW = 0x33000000;
    private static final ArgbEvaluator ARGB = new ArgbEvaluator();

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF track = new RectF();
    private final int widthPx, heightPx, insetPx, shadowOffsetPx;
    private boolean checked, rowFocused;
    private float onProgress, focusProgress;   // 0 = aus / Zeile ruhig, 1 = an / Zeile fokussiert
    private ValueAnimator onAnim, focusAnim;

    GlassSwitch(Context c) {
        super(c);
        widthPx = Ui.px(c, WIDTH_DP);
        heightPx = Ui.px(c, HEIGHT_DP);
        insetPx = Ui.px(c, KNOB_INSET_DP);
        shadowOffsetPx = Math.max(1, Ui.px(c, 1));
    }

    boolean isChecked() {
        return checked;
    }

    /** Setzt den Zustand ohne Animation. */
    void setChecked(boolean on) {
        checked = on;
        if (onAnim != null) onAnim.cancel();
        onProgress = on ? 1f : 0f;
        invalidate();
    }

    /** Schaltet um (mit Animation) und gibt den neuen Zustand zurück. */
    boolean toggle() {
        checked = !checked;
        onAnim = run(onAnim, onProgress, checked ? 1f : 0f, true);
        return checked;
    }

    /** Die Zeile mit dem Schalter hat (oder verliert) den Fokus: Farben passen sich der hellen Fokusfläche an. */
    void setRowFocused(boolean focused) {
        if (rowFocused == focused) return;
        rowFocused = focused;
        focusAnim = run(focusAnim, focusProgress, focused ? 1f : 0f, false);
    }

    private ValueAnimator run(ValueAnimator running, float from, float to, boolean isOn) {
        if (running != null) running.cancel();
        ValueAnimator anim = ValueAnimator.ofFloat(from, to);
        anim.setDuration(Ui.ms(Ui.FAST));
        anim.setInterpolator(Ui.EASE);
        anim.addUpdateListener(a -> {
            float value = (float) a.getAnimatedValue();
            if (isOn) onProgress = value; else focusProgress = value;
            invalidate();
        });
        anim.start();
        return anim;
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(widthPx, heightPx);
    }

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (onAnim != null) onAnim.cancel();
        if (focusAnim != null) focusAnim.cancel();
    }

    @Override protected void onDraw(Canvas canvas) {
        int fill = Look.focusFill(), ink = Look.focusText();
        // Ruhige Zeile: Track an = Akzent, aus = Glas; Knopf an = Schriftfarbe der Akzentfläche, aus = hell.
        int calmTrack = (int) ARGB.evaluate(onProgress, TRACK_OFF, fill);
        int calmKnob = (int) ARGB.evaluate(onProgress, KNOB_OFF, ink);
        // Fokussierte Zeile (Akzentfläche): umgekehrt, Track an = Schriftfarbe, aus = abgedunkelt; Knopf = Fläche.
        int focusedTrack = (int) ARGB.evaluate(onProgress, (ink & 0x00FFFFFF) | 0x40000000, ink);
        int focusedKnob = fill;
        int trackColor = (int) ARGB.evaluate(focusProgress, calmTrack, focusedTrack);
        int knobColor = (int) ARGB.evaluate(focusProgress, calmKnob, focusedKnob);

        int w = getWidth(), h = getHeight();
        float radius = h / 2f;
        track.set(0, 0, w, h);
        paint.setColor(trackColor);
        canvas.drawRoundRect(track, radius, radius, paint);

        float knobRadius = radius - insetPx;
        float knobX = knobRadius + insetPx + onProgress * (w - 2 * (knobRadius + insetPx));
        paint.setColor(KNOB_SHADOW);
        canvas.drawCircle(knobX, radius + shadowOffsetPx, knobRadius, paint);
        paint.setColor(knobColor);
        canvas.drawCircle(knobX, radius, knobRadius, paint);
    }
}
