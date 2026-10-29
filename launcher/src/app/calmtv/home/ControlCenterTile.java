package app.calmtv.home;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Schnellkachel des Kontrollzentrums: Symbol, Beschriftung und Zustand. Ruhe = {@link Ui#ROW}, Fokus = gefüllte
 * Fläche in der Akzentfarbe. Kein Blur, keine Ebenen: nur eine abgerundete Fläche, deren Farbe überblendet wird.
 */
final class ControlCenterTile extends LinearLayout {
    private static final ArgbEvaluator ARGB = new ArgbEvaluator();
    private static final float DISABLED_ALPHA = 0.4f;
    private static final int DIM_ALPHA_MASK = 0xB3000000;

    private final GradientDrawable background;
    private final Icons.Glyph glyph;
    private final TextView title;
    private final TextView state;
    private final String label;
    private ValueAnimator fade;
    private float progress;   // 0 = Ruhe, 1 = Fokus

    ControlCenterTile(Context c, int iconId, String label) {
        super(c);
        this.label = label;
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setContentDescription(label);
        setClickable(true);
        setFocusable(true);
        background = Ui.round(c, Ui.ROW, Ui.RADIUS_ROW, 0, 0);
        setBackground(background);
        setPadding(Ui.px(c, 12), Ui.px(c, 8), Ui.px(c, 8), Ui.px(c, 8));

        // Symbol oben, darunter Beschriftung und Zustand (einzeilig, damit lange Wörter nicht mitten im Wort brechen).
        glyph = Icons.icon(iconId, Ui.TEXT);
        ImageView iconView = new ImageView(c);
        iconView.setImageDrawable(glyph);
        addView(iconView, new LayoutParams(Ui.px(c, 20), Ui.px(c, 20)));

        title = plainText(c, label, Ui.SP_BODY, 700);
        title.setMaxLines(1);
        LayoutParams titleParams = new LayoutParams(-1, -2);
        titleParams.topMargin = Ui.px(c, 6);
        addView(title, titleParams);
        state = plainText(c, "", Ui.SP_LABEL, 500);
        state.setMaxLines(1);
        LayoutParams stateParams = new LayoutParams(-1, -2);
        stateParams.topMargin = Ui.px(c, 2);
        addView(state, stateParams);
        applyProgress(0f);
    }

    /** Kleiner Text ohne Schatten (der Schatten stört auf der hellen Fokusfläche). */
    private static TextView plainText(Context c, String text, float sp, int weight) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextSize(sp);
        t.setTypeface(Ui.font(c, weight));
        t.setIncludeFontPadding(false);
        t.setEllipsize(TextUtils.TruncateAt.END);
        return t;
    }

    /** Setzt die Zustandszeile unter der Beschriftung. */
    void setState(String text) {
        state.setText(text);
        setContentDescription(label + ", " + text);
    }

    /** Nicht verfügbare Kacheln sind ausgegraut und nehmen keinen Fokus an. */
    void setAvailable(boolean available) {
        setFocusable(available);
        setClickable(available);
        setAlpha(available ? 1f : DISABLED_ALPHA);
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
        int focusText = Look.focusText();
        int dimFocus = (focusText & 0x00FFFFFF) | DIM_ALPHA_MASK;
        background.setColor((int) ARGB.evaluate(p, Ui.ROW, Look.focusFill()));
        int titleColor = (int) ARGB.evaluate(p, Ui.TEXT, focusText);
        title.setTextColor(titleColor);
        state.setTextColor((int) ARGB.evaluate(p, Ui.TEXT_DIM, dimFocus));
        glyph.setColor(titleColor);
    }
}
