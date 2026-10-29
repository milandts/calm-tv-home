package app.calmtv.home;

import android.animation.ValueAnimator;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.TextView;

import java.util.ArrayList;

/**
 * Animierter Fokuswechsel für eine Zeile: Fläche, Schrift und Symbole gehen gemeinsam von der Ruhefarbe
 * zu {@link Look#focusFill()} / {@link Look#focusText()} über. Eine Instanz gehört zu genau einer Ansicht.
 */
final class SettingsFocus implements View.OnFocusChangeListener {
    /** Wird aufgerufen, wenn die Ansicht den Fokus bekommt. */
    interface Gained {
        void onGained(View view);
    }

    private static final int DESTRUCTIVE_ON_LIGHT = 0xFFD70015;
    private static final int DIM_ALPHA = 0xB3000000;

    /** Ein Text oder Symbol, dessen Farbe mitwechselt. */
    private static final class Tint {
        final TextView text;
        final Icons.Glyph glyph;
        final int rest;
        final boolean dim;
        final boolean danger;

        Tint(TextView text, Icons.Glyph glyph, int rest, boolean dim, boolean danger) {
            this.text = text;
            this.glyph = glyph;
            this.rest = rest;
            this.dim = dim;
            this.danger = danger;
        }
    }

    private final ArrayList<Tint> tints = new ArrayList<>();
    private final GradientDrawable background;
    private int restFill;   // 0 = durchsichtig (in der Farbe der Fokusfläche)
    private GlassSwitch toggle;
    private SettingsSlider slider;
    private Gained gained;
    private ValueAnimator animator;
    private float progress;

    SettingsFocus(GradientDrawable background, int restFill) {
        this.background = background;
        this.restFill = restFill;
        apply(0f);
    }

    void addText(TextView text, int restColor, boolean dim, boolean danger) {
        tints.add(new Tint(text, null, restColor, dim, danger));
    }

    void addGlyph(Icons.Glyph glyph, int restColor, boolean dim, boolean danger) {
        tints.add(new Tint(null, glyph, restColor, dim, danger));
    }

    void setSwitch(GlassSwitch toggle) {
        this.toggle = toggle;
    }

    void setSlider(SettingsSlider slider) {
        this.slider = slider;
    }

    void setGained(Gained gained) {
        this.gained = gained;
    }

    /** Ändert die Ruhefläche (z. B. gewählte Kategorie in der Seitenleiste). */
    void setRestFill(int restFill) {
        this.restFill = restFill;
        if (animator == null || !animator.isRunning()) apply(progress);
    }

    @Override public void onFocusChange(View v, boolean hasFocus) {
        if (animator != null) animator.cancel();
        animator = ValueAnimator.ofFloat(progress, hasFocus ? 1f : 0f);
        animator.setDuration(Ui.ms(Ui.FAST));
        animator.setInterpolator(Ui.EASE);
        animator.addUpdateListener(a -> apply((float) a.getAnimatedValue()));
        animator.start();
        if (toggle != null) toggle.setRowFocused(hasFocus);
        if (slider != null) slider.setRowFocused(hasFocus);
        if (hasFocus && gained != null) gained.onGained(v);
    }

    private void apply(float p) {
        progress = p;
        int focusFill = Look.focusFill();
        int rest = restFill == 0 ? focusFill & 0x00FFFFFF : restFill;
        background.setColor(blend(rest, focusFill, p));
        int focusText = Look.focusText();
        for (int i = 0; i < tints.size(); i++) {
            Tint t = tints.get(i);
            int target = focusText;
            if (t.danger && focusText == Ui.TEXT_ON_LIGHT) target = DESTRUCTIVE_ON_LIGHT;
            else if (t.dim) target = (focusText & 0x00FFFFFF) | DIM_ALPHA;
            int color = blend(t.rest, target, p);
            if (t.text != null) t.text.setTextColor(color);
            else t.glyph.setColor(color);
        }
    }

    private static int blend(int from, int to, float p) {
        int a = channel(from >>> 24, to >>> 24, p);
        int r = channel((from >> 16) & 0xFF, (to >> 16) & 0xFF, p);
        int g = channel((from >> 8) & 0xFF, (to >> 8) & 0xFF, p);
        int b = channel(from & 0xFF, to & 0xFF, p);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int channel(int from, int to, float p) {
        return Math.round(from + (to - from) * p);
    }
}
