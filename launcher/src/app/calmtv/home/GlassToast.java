package app.calmtv.home;

import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * Kurze Meldung als Glas-Pille, unten mittig über dem Dock. Nicht fokussierbar und kein {@link Ui.Modal}: Zurück und
 * Fokus bleiben unberührt. Eine neue Meldung ersetzt die alte sofort (keine Warteschlange). Die Modal-Ebene
 * ({@code HomeHost.modalLayer()}) muss ein FrameLayout sein, damit die Pille unten mittig sitzt.
 */
final class GlassToast {
    private static final long HOLD_MS = 2600;
    private static final float BOTTOM_MARGIN_DP = 124, RISE_DP = 6, MAX_WIDTH_SHARE = 0.7f;
    private static final float PADDING_H_DP = 22, PADDING_V_DP = 11;

    private GlassToast() {}

    /** Die Pille selbst; eigene Klasse, damit eine alte Meldung in der Ebene wiedergefunden und entfernt werden kann. */
    private static final class ToastView extends TextView {
        ToastView(Context c) {
            super(c);
        }
    }

    /** Zeigt die Meldung etwa 2,6 Sekunden; eine neue ersetzt die alte. */
    static void show(ViewGroup layer, String message) {
        if (layer == null || message == null || message.isEmpty()) return;
        Context c = layer.getContext();
        removeOld(layer);

        ToastView toast = new ToastView(c);
        toast.setText(message);
        toast.setTextSize(Ui.sp(15));
        toast.setTypeface(Ui.font(c, 600));
        toast.setTextColor(Ui.TEXT);
        toast.setIncludeFontPadding(false);
        toast.setSingleLine();
        toast.setEllipsize(TextUtils.TruncateAt.END);
        toast.setGravity(Gravity.CENTER);
        toast.setFocusable(false);
        toast.setClickable(false);
        toast.setPadding(Ui.px(c, PADDING_H_DP), Ui.px(c, PADDING_V_DP), Ui.px(c, PADDING_H_DP), Ui.px(c, PADDING_V_DP));
        toast.setBackground(Ui.round(c, Ui.panel(Ui.PANEL_MODAL), 100, Ui.HAIRLINE, Math.max(1, Ui.px(c, 1))));
        int layerWidth = layer.getWidth() > 0 ? layer.getWidth() : Ui.usableWidth(c);
        toast.setMaxWidth(Math.round(layerWidth * MAX_WIDTH_SHARE));

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        lp.bottomMargin = Ui.px(c, BOTTOM_MARGIN_DP);
        toast.setAlpha(0f);
        toast.setTranslationY(Ui.px(c, RISE_DP));
        layer.addView(toast, lp);

        toast.animate().alpha(1f).translationY(0f).setDuration(Ui.ms(Ui.FAST)).setInterpolator(Ui.EASE).start();
        toast.postDelayed(new FadeOut(layer, toast), Ui.ms(Ui.FAST) + HOLD_MS);
    }

    /** Blendet die Pille nach der Standzeit aus und nimmt sie aus der Ebene (nichts, wenn sie inzwischen ersetzt wurde). */
    private static final class FadeOut implements Runnable {
        private final ViewGroup layer;
        private final View toast;

        FadeOut(ViewGroup layer, View toast) {
            this.layer = layer;
            this.toast = toast;
        }

        @Override public void run() {
            if (toast.getParent() != layer) return;
            toast.animate().alpha(0f).setDuration(Ui.ms(Ui.MEDIUM)).withEndAction(new Remove(layer, toast)).start();
        }
    }

    private static final class Remove implements Runnable {
        private final ViewGroup layer;
        private final View toast;

        Remove(ViewGroup layer, View toast) {
            this.layer = layer;
            this.toast = toast;
        }

        @Override public void run() {
            if (toast.getParent() == layer) layer.removeView(toast);
        }
    }

    private static void removeOld(ViewGroup layer) {
        for (int i = layer.getChildCount() - 1; i >= 0; i--) {
            View child = layer.getChildAt(i);
            if (child instanceof ToastView) {
                layer.removeViewAt(i);
                child.animate().cancel();
            }
        }
    }
}
