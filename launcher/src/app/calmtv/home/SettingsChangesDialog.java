package app.calmtv.home;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/**
 * "Änderungen behalten?" beim Verlassen eines Bereichs: Liste der probeweise geänderten Einstellungen und drei Buttons.
 * Zurück gilt wie "Weiter bearbeiten". Genau einer der drei Rückrufe läuft, einmal.
 */
final class SettingsChangesDialog extends FrameLayout implements Ui.Modal {
    private static final int WIDTH_DP = 560, BUTTON_DP = 44;

    private final ViewGroup layer;
    private final Runnable onKeep, onDiscard, onStay;
    private final Chip[] buttons = new Chip[3];
    private boolean finished;
    /** Das Element, das vor dem Dialog den Fokus hatte; bei "Weiter bearbeiten" bekommt es ihn zurück. */
    private View previousFocus;

    private SettingsChangesDialog(ViewGroup layer, List<String> lines, Runnable onKeep, Runnable onDiscard, Runnable onStay) {
        super(layer.getContext());
        Context c = getContext();
        this.layer = layer;
        this.onKeep = onKeep;
        this.onDiscard = onDiscard;
        this.onStay = onStay;
        setClickable(true);
        setBackgroundColor(0x66000000);

        LinearLayout panel = new LinearLayout(c);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(Ui.px(c, 28), Ui.px(c, 26), Ui.px(c, 28), Ui.px(c, 24));
        GradientDrawable shape = Ui.round(c, Ui.PANEL_MODAL, Ui.RADIUS_PANEL, Ui.HAIRLINE, Math.max(1, Ui.px(c, 1)));
        panel.setBackground(shape);
        panel.addView(SettingsRowView.text(c, "Änderungen behalten?", 20, 600, Ui.TEXT), new LinearLayout.LayoutParams(-1, -2));
        for (String line : lines) {
            TextView text = SettingsRowView.text(c, line, Ui.SP_BODY, 500, Ui.TEXT_DIM);
            text.setSingleLine();
            text.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = Ui.px(c, 8);
            panel.addView(text, lp);
        }

        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        String[] labels = {"Behalten", "Verwerfen", "Weiter bearbeiten"};
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            buttons[i] = new Chip(c, labels[i], BUTTON_DP);
            buttons[i].setOnClickListener(v -> finish(index));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
            if (i > 0) lp.setMarginStart(Ui.px(c, 10));
            row.addView(buttons[i], lp);
        }
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2);
        rlp.topMargin = Ui.px(c, 22);
        panel.addView(row, rlp);
        addView(panel, new FrameLayout.LayoutParams(Math.round(Ui.px(c, WIDTH_DP) * Math.max(1f, Ui.sp(1f))), -2, Gravity.CENTER));
    }

    /** Zeigt den Dialog; der Fokus beginnt auf "Behalten". Gibt die Ansicht zurück (zum Entfernen beim Schließen). */
    static View show(ViewGroup layer, List<String> lines, Runnable onKeep, Runnable onDiscard, Runnable onStay) {
        SettingsChangesDialog dialog = new SettingsChangesDialog(layer, lines, onKeep, onDiscard, onStay);
        layer.addView(dialog, new FrameLayout.LayoutParams(-1, -1));
        dialog.setAlpha(0f);
        dialog.animate().alpha(1f).setDuration(Ui.ms(Ui.FAST)).start();
        dialog.previousFocus = layer.getRootView().findFocus();
        dialog.buttons[0].requestFocus();
        return dialog;
    }

    @Override public void dismiss() {
        finish(2);
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        int key = event.getKeyCode();
        boolean left = key == KeyEvent.KEYCODE_DPAD_LEFT, right = key == KeyEvent.KEYCODE_DPAD_RIGHT;
        if (left || right || key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_DOWN) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && (left || right)) {
                int at = 0;
                for (int i = 0; i < buttons.length; i++) if (buttons[i].hasFocus()) at = i;
                buttons[Math.max(0, Math.min(buttons.length - 1, at + (left ? -1 : 1)))].requestFocus();
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private void finish(int choice) {
        if (finished) return;
        finished = true;
        final View self = this;
        setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
        animate().alpha(0f).setDuration(Ui.ms(Ui.FAST)).withEndAction(() -> layer.removeView(self)).start();
        (choice == 0 ? onKeep : choice == 1 ? onDiscard : onStay).run();
        // Beim Weiterbearbeiten bleibt die Seite stehen: ihr Element bekommt den Fokus zurück (sonst landet er im Wurzelelement).
        if (choice == 2 && previousFocus != null && previousFocus.isAttachedToWindow()) previousFocus.requestFocus();
    }
}
