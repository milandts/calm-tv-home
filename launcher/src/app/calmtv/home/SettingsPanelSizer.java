package app.calmtv.home;

import android.animation.ValueAnimator;
import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

/**
 * Passt die Höhe des Inhalts-Panels dem Inhalt der Seite an (mindestens 60 % der verfügbaren Höhe), damit kurze
 * Seiten unten nicht leer wirken. Die Höhe ändert sich beim Seitenwechsel sanft, nie sprunghaft.
 */
final class SettingsPanelSizer {
    private static final float MIN_FRACTION = 0.6f;

    private final View panel;
    private final Context context;
    private final int reservedPx;   // Platz über und unter dem Panel
    private ValueAnimator animator;
    private boolean sizedOnce;

    SettingsPanelSizer(View panel, Context context, int reservedPx) {
        this.panel = panel;
        this.context = context;
        this.reservedPx = reservedPx;
    }

    /** Verfügbare Höhe; ändert sich mit dem Sicherheitsrand, deshalb jedes Mal neu gelesen. */
    int availablePx() {
        return Ui.usableHeight(context) - reservedPx;
    }

    /** Kürzt das Panel, falls der Sicherheitsrand wuchs, während die Einstellungen offen sind (sonst ragt es unten heraus). */
    void clamp() {
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) panel.getLayoutParams();
        if (params.height > availablePx()) {
            params.height = availablePx();
            panel.setLayoutParams(params);
        }
    }

    /** Misst den Inhalt nach seinem ersten Layout und stellt das Panel darauf ein. */
    void adapt(SettingsContent content) {
        content.addOnLayoutChangeListener(new FirstLayout(this, content));
    }

    void animateTo(int target) {
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) panel.getLayoutParams();
        int from = params.height > 0 ? params.height : availablePx();
        if (animator != null) animator.cancel();
        if (!sizedOnce || from == target) {
            sizedOnce = true;
            params.height = target;
            panel.setLayoutParams(params);
            return;
        }
        animator = ValueAnimator.ofInt(from, target);
        animator.setDuration(Ui.ms(Ui.MEDIUM));
        animator.setInterpolator(Ui.EASE);
        animator.addUpdateListener(a -> {
            params.height = (int) a.getAnimatedValue();
            panel.setLayoutParams(params);
        });
        animator.start();
    }

    /** Wartet auf das erste Layout des Inhalts, liest dann seine natürliche Höhe und entfernt sich wieder. */
    private static final class FirstLayout implements View.OnLayoutChangeListener {
        private final SettingsPanelSizer sizer;
        private final SettingsContent content;

        FirstLayout(SettingsPanelSizer sizer, SettingsContent content) {
            this.sizer = sizer;
            this.content = content;
        }

        @Override public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
            if (b - t <= 0 || content.rows.getHeight() <= 0) return;
            content.removeOnLayoutChangeListener(this);
            int target = Math.max((int) (sizer.availablePx() * MIN_FRACTION), Math.min(sizer.availablePx(), content.naturalHeight()));
            sizer.panel.post(() -> sizer.animateTo(target));
        }
    }
}
