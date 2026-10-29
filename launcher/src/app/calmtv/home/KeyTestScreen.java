package app.calmtv.home;

import android.content.Context;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Tasten-Test: zeigt den Namen jeder Fernbedienungstaste, die gedrückt wird (z. B. DPAD_CENTER, MENU, MEDIA_PLAY_PAUSE), und die
 * letzten Tasten darunter. Zurück zweimal hintereinander beendet; HOME beendet ebenfalls (das macht die Startseite).
 * Nichts wird gespeichert oder gesendet.
 */
final class KeyTestScreen implements Screen {
    private static final int HISTORY_SIZE = 5;
    private static final String KEY_PREFIX = "KEYCODE_";
    private static final float PANEL_WIDTH_DP = 560, PANEL_PADDING_DP = 28;

    private final FrameLayout root;
    private final TextView keyName;
    private final TextView keyDetail;
    private final TextView history;
    private final TextView hint;
    private final String[] recent = new String[HISTORY_SIZE];
    private int recentCount;
    private boolean hasBackPending;

    KeyTestScreen(HomeHost host) {
        Context context = host.context();

        root = new FrameLayout(context);
        root.setBackgroundColor(Ui.SCRIM);
        root.setFocusable(true);
        root.setFocusableInTouchMode(true);
        root.setOnKeyListener(this::onKey);

        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = Ui.px(context, PANEL_PADDING_DP);
        panel.setPadding(pad, pad, pad, pad);
        panel.setBackground(Ui.round(context, Ui.PANEL_MODAL, Ui.RADIUS_PANEL, Ui.HAIRLINE, Math.max(1, Ui.px(context, 1))));
        root.addView(panel, new FrameLayout.LayoutParams(Ui.px(context, PANEL_WIDTH_DP), -2, Gravity.CENTER));

        panel.addView(Ui.label(context, "Tasten-Test", Ui.sp(17), 600, Ui.TEXT_DIM), new LinearLayout.LayoutParams(-2, -2));
        keyName = Ui.label(context, "Drücke eine Taste", Ui.sp(34), 600, Ui.TEXT);
        keyName.setGravity(Gravity.CENTER);
        panel.addView(keyName, spaced(context, 22));
        keyDetail = Ui.label(context, " ", Ui.sp(Ui.SP_BODY), 500, Ui.TEXT_DIM);
        keyDetail.setGravity(Gravity.CENTER);
        panel.addView(keyDetail, spaced(context, 8));
        history = Ui.label(context, " ", Ui.sp(Ui.SP_BODY), 500, Ui.TEXT_FAINT);
        history.setGravity(Gravity.CENTER);
        history.setLineSpacing(Ui.px(context, 3), 1f);
        panel.addView(history, spaced(context, 22));
        hint = Ui.label(context, "Zurück zweimal drücken beendet", Ui.sp(Ui.SP_LABEL), 500, Ui.TEXT_DIM);
        hint.setGravity(Gravity.CENTER);
        panel.addView(hint, spaced(context, 22));
    }

    private static LinearLayout.LayoutParams spaced(Context context, float topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.topMargin = Ui.px(context, topDp);
        return lp;
    }

    /**
     * Zeigt jede Taste und verbraucht sie, damit der Fokus nirgends hinläuft. Zurück bleibt unverbraucht: die Startseite ruft danach
     * {@link #onBack} auf und zählt dort.
     */
    private boolean onKey(View view, int keyCode, KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) show(keyCode, event.getRepeatCount());
        return keyCode != KeyEvent.KEYCODE_BACK;
    }

    private void show(int keyCode, int repeatCount) {
        String name = nameOf(keyCode);
        keyName.setText(name);
        keyDetail.setText("Code " + keyCode + (repeatCount > 0 ? " · gehalten (" + repeatCount + ")" : ""));
        if (repeatCount == 0) {
            pushRecent(name);
            history.setText(historyText());
        }
        if (keyCode != KeyEvent.KEYCODE_BACK) {
            hasBackPending = false;
            hint.setText("Zurück zweimal drücken beendet");
        }
    }

    /** "DPAD_CENTER" statt "KEYCODE_DPAD_CENTER". */
    private static String nameOf(int keyCode) {
        String name = KeyEvent.keyCodeToString(keyCode);
        return name.startsWith(KEY_PREFIX) ? name.substring(KEY_PREFIX.length()) : name;
    }

    /** Neueste zuerst; ältere rutschen hinten heraus. */
    private void pushRecent(String name) {
        System.arraycopy(recent, 0, recent, 1, HISTORY_SIZE - 1);
        recent[0] = name;
        recentCount = Math.min(HISTORY_SIZE, recentCount + 1);
    }

    private String historyText() {
        StringBuilder text = new StringBuilder();
        for (int i = 1; i < recentCount; i++) {   // die neueste steht schon groß darüber
            if (text.length() > 0) text.append("   ");
            text.append(recent[i]);
        }
        return text.length() == 0 ? " " : text.toString();
    }

    // ---- Screen ----------------------------------------------------------------------------------

    @Override public View view() {
        return root;
    }

    @Override public void onShown() {
        root.requestFocus();
    }

    /** Angezeigt hat Zurück schon {@link #onKey}; hier wird nur gezählt: das erste verbraucht, das zweite in Folge beendet den Test. */
    @Override public boolean onBack() {
        if (hasBackPending) return false;
        hasBackPending = true;
        hint.setText("Noch einmal Zurück beendet den Test");
        return true;
    }

    /** Die Menütaste liefert die Startseite hierher (sie fängt sie vor den Ansichten ab). */
    @Override public boolean onMenu() {
        show(KeyEvent.KEYCODE_MENU, 0);
        return true;
    }

    @Override public void onClosed() {
        root.setOnKeyListener(null);
    }
}
