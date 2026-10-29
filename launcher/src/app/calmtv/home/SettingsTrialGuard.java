package app.calmtv.home;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 10-Sekunden-Test für riskante Einstellungen: "Passt das so?" mit Rückzähler. Fokus beginnt auf "Passt so";
 * Zurück-Taste und Zeitablauf setzen zurück, "Behalten" übernimmt. Genau eine der beiden Rückrufe läuft, einmal.
 */
final class SettingsTrialGuard extends FrameLayout implements Ui.Modal {
    private static final int SECONDS = 10, WIDTH_DP = 440, BUTTON_DP = 44;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable onKeep, onRevert;
    private final TextView countdown;
    private final Chip revertButton, keepButton;
    private final Runnable tick;
    private final ViewGroup layer;
    private int remaining = SECONDS;
    private boolean finished;

    private SettingsTrialGuard(ViewGroup layer, Runnable onKeep, Runnable onRevert) {
        super(layer.getContext());
        Context c = getContext();
        this.layer = layer;
        this.onKeep = onKeep;
        this.onRevert = onRevert;
        setClickable(true);
        setBackgroundColor(0x66000000);

        LinearLayout panel = new LinearLayout(c);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(Ui.px(c, 28), Ui.px(c, 26), Ui.px(c, 28), Ui.px(c, 24));
        GradientDrawable shape = Ui.round(c, Ui.PANEL_MODAL, Ui.RADIUS_PANEL, Ui.HAIRLINE, Math.max(1, Ui.px(c, 1)));
        panel.setBackground(shape);
        panel.addView(SettingsRowView.text(c, "Änderung behalten?", 20, 600, Ui.TEXT), new LinearLayout.LayoutParams(-1, -2));
        countdown = SettingsRowView.text(c, "", Ui.SP_BODY, 500, Ui.TEXT_DIM);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, -2);
        clp.topMargin = Ui.px(c, 8);
        panel.addView(countdown, clp);

        LinearLayout buttons = new LinearLayout(c);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        revertButton = new Chip(c, "Zurücksetzen", BUTTON_DP);
        keepButton = new Chip(c, "Passt so", BUTTON_DP);
        revertButton.setOnClickListener(v -> finish(false));
        keepButton.setOnClickListener(v -> finish(true));
        buttons.addView(revertButton, new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout.LayoutParams klp = new LinearLayout.LayoutParams(0, -2, 1f);
        klp.setMarginStart(Ui.px(c, 12));
        buttons.addView(keepButton, klp);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, -2);
        blp.topMargin = Ui.px(c, 22);
        panel.addView(buttons, blp);
        addView(panel, new FrameLayout.LayoutParams(Math.round(Ui.px(c, WIDTH_DP) * Math.max(1f, Ui.sp(1f))), -2, Gravity.CENTER));

        tick = this::onTick;
        showCount();
    }

    /** Zeigt den Test. {@code onKeep} läuft bei "Behalten", {@code onRevert} bei allem anderen (genau einer, einmal). */
    static View show(ViewGroup layer, Runnable onKeep, Runnable onRevert) {
        if (layer == null) {
            onRevert.run();
            return null;
        }
        SettingsTrialGuard guard = new SettingsTrialGuard(layer, onKeep, onRevert);
        layer.addView(guard, new FrameLayout.LayoutParams(-1, -1));
        guard.setAlpha(0f);
        guard.animate().alpha(1f).setDuration(Ui.ms(Ui.FAST)).start();
        guard.keepButton.requestFocus();   // die Auswahltaste lässt die Änderung stehen; Zurück und Zeitablauf stellen den alten Wert her
        guard.handler.postDelayed(guard.tick, 1000);
        return guard;
    }

    @Override public void dismiss() {
        finish(false);
    }

    /** Pfeiltasten bleiben im Dialog: links/rechts wechseln den Button, hoch/runter tun nichts. */
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        int key = event.getKeyCode();
        boolean left = key == KeyEvent.KEYCODE_DPAD_LEFT, right = key == KeyEvent.KEYCODE_DPAD_RIGHT;
        if (left || right || key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_DOWN) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (left) revertButton.requestFocus();
                else if (right) keepButton.requestFocus();
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private void onTick() {
        if (finished) return;
        remaining--;
        if (remaining <= 0) {
            finish(false);
            return;
        }
        showCount();
        handler.postDelayed(tick, 1000);
    }

    private void showCount() {
        countdown.setText("Wird in " + remaining + " s zurückgesetzt");
    }

    private void finish(boolean keep) {
        if (finished) return;
        finished = true;
        handler.removeCallbacks(tick);
        final View self = this;
        animate().alpha(0f).setDuration(Ui.ms(Ui.FAST)).withEndAction(() -> layer.removeView(self)).start();
        setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
        (keep ? onKeep : onRevert).run();
    }
}
