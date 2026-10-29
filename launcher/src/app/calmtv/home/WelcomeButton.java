package app.calmtv.home;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.TextView;

/**
 * Kapsel-Button der Einführung. Ruhe: leichte Fläche, Fokus: gefüllt in der Akzentfarbe (Schrift passt sich an).
 * Die Höhe legt der Aufrufer über die Layout-Parameter fest.
 */
final class WelcomeButton extends TextView {
    static final int PRIMARY = 0, SECONDARY = 1, GHOST = 2;

    private static final ArgbEvaluator ARGB = new ArgbEvaluator();
    private static final int PRIMARY_FILL = 0x38FFFFFF, GHOST_FILL = 0x00FFFFFF;
    private static final float FOCUS_SCALE = 1.05f;

    private final GradientDrawable background;
    private final int restFill;
    private final int restText;
    private ValueAnimator fade;
    private float progress;   // 0 = Ruhe, 1 = Fokus

    WelcomeButton(Context c, String label, int style) {
        super(c);
        restFill = style == PRIMARY ? PRIMARY_FILL : style == SECONDARY ? Ui.ROW : GHOST_FILL;
        restText = style == GHOST ? Ui.TEXT_DIM : Ui.TEXT;
        setText(label);
        setTypeface(Ui.font(c, 700));
        setTextSize(style == GHOST ? 14 : 16);
        setGravity(Gravity.CENTER);
        setSingleLine();
        setIncludeFontPadding(false);
        int padH = Ui.px(c, style == GHOST ? 18 : 28);
        setPadding(padH, 0, padH, 0);
        background = Ui.round(c, restFill, 100, 0, 0);
        setBackground(background);
        setFocusable(true);
        setClickable(true);
        applyProgress(0f);
    }

    @Override
    protected void onFocusChanged(boolean gainFocus, int direction, Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
        if (fade != null) fade.cancel();
        fade = ValueAnimator.ofFloat(progress, gainFocus ? 1f : 0f);
        fade.setDuration(Ui.FAST);
        fade.addUpdateListener(a -> applyProgress((float) a.getAnimatedValue()));
        fade.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (fade != null) fade.cancel();
        super.onDetachedFromWindow();
    }

    private void applyProgress(float p) {
        progress = p;
        background.setColor((int) ARGB.evaluate(p, restFill, Look.focusFill()));
        setTextColor((int) ARGB.evaluate(p, restText, Look.focusText()));
        float scale = 1f + (FOCUS_SCALE - 1f) * p;
        setScaleX(scale);
        setScaleY(scale);
    }
}
