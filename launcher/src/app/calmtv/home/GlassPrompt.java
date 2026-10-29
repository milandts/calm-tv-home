package app.calmtv.home;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ResultReceiver;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Eingabe- und Bestätigungsdialog im Glas-Look. Scrim, Panel, Animation und Tastenfang teilt er mit {@link GlassMenu}.
 * Beide Formen sind {@link Ui.Modal}: Zurück schließt ohne Aufruf, der Fokus kehrt dorthin zurück, wo man herkam.
 */
final class GlassPrompt extends FrameLayout implements Ui.Modal {
    /** Nimmt den eingegebenen Text entgegen (bereits getrimmt). */
    interface TextHandler {
        void accept(String text);
    }

    private static final float INPUT_WIDTH_DP = 520, CONFIRM_WIDTH_DP = 480, PAD_H_DP = 28, PAD_TOP_DP = 26, PAD_BOTTOM_DP = 24;
    private static final float BUTTON_HEIGHT_DP = 44, BUTTON_GAP_DP = 12, INPUT_TOP_SHARE = 0.14f;
    private static final long KEYBOARD_FIRST_MS = 250, KEYBOARD_RETRY_MS = 250, KEYBOARD_LAST_MS = 700, PROBE_TIMEOUT_MS = 400;
    private static final String DEFAULT_OK_LABEL = "Speichern";
    private static final float FIELD_HEIGHT_DP = 48, FIELD_PAD_H_DP = 20;

    private ViewGroup layer;
    private View previousFocus, scrim;
    private ViewGroup panel;
    private EditText field;
    private Chip cancelButton, okButton;   // Fokusziele neben dem Feld (bei confirm: Abbrechen und Bestätigung)
    private boolean closing, backProbePending;

    private GlassPrompt(Context c) {
        super(c);
    }

    /** Fragt nach einem Text (Bildschirmtastatur kommt mit der Auswahltaste). Abbrechen und Zurück rufen nichts auf. */
    static void show(ViewGroup layer, String title, String hint, String initial, TextHandler onOk) {
        show(layer, title, hint, initial, DEFAULT_OK_LABEL, onOk);
    }

    /** Wie {@link #show(ViewGroup, String, String, String, TextHandler)}, mit eigener Beschriftung des Bestätigungs-Buttons (z. B. "Anlegen"). */
    static void show(ViewGroup layer, String title, String hint, String initial, String confirmLabel, TextHandler onOk) {
        if (layer == null) return;
        new GlassPrompt(layer.getContext()).openInput(layer, title, hint, initial, confirmLabel, onOk);
    }

    /**
     * Fragt nach Bestätigung. {@code destructive} färbt die Bestätigung rot. Der Fokus beginnt bei destruktiven Aktionen auf
     * "Abbrechen", sonst auf der Bestätigung.
     */
    static void confirm(ViewGroup layer, String title, String message, String confirmLabel, boolean destructive, Runnable onConfirm) {
        if (layer == null) return;
        new GlassPrompt(layer.getContext()).openConfirm(layer, title, message, confirmLabel, destructive, onConfirm);
    }

    // ---- Aufbau -----------------------------------------------------------------------------------

    private GlassMenu.CappedPanel begin(ViewGroup layerView) {
        layer = layerView;
        previousFocus = GlassMenu.currentFocus(layerView);
        setClickable(true);   // verschluckt Berührungen (kein Durchgriff auf die Ansicht darunter)
        scrim = GlassMenu.newScrim(getContext());
        addView(scrim, new LayoutParams(-1, -1));
        GlassMenu.CappedPanel box = GlassMenu.newPanel(getContext());
        box.setPadding(Ui.px(getContext(), PAD_H_DP), Ui.px(getContext(), PAD_TOP_DP),
                Ui.px(getContext(), PAD_H_DP), Ui.px(getContext(), PAD_BOTTOM_DP));
        panel = box;
        return box;
    }

    private void end(GlassMenu.CappedPanel box, float widthDp, int gravity, int topMargin, View focusTarget) {
        LayoutParams lp = new LayoutParams(Math.round(Ui.px(getContext(), widthDp) * Math.max(1f, Ui.sp(1f))), -2, gravity);   // bei großer Schrift breiter
        lp.topMargin = topMargin;
        addView(box, lp);
        layer.addView(this, new LayoutParams(-1, -1));
        focusTarget.requestFocus();
        GlassMenu.animateIn(scrim, panel);
    }

    private TextView title(String text) {
        TextView t = Ui.label(getContext(), text, Ui.sp(22), 600, Ui.TEXT);
        t.setShadowLayer(0, 0, 0, 0);
        t.setMaxLines(2);
        t.setEllipsize(TextUtils.TruncateAt.END);
        return t;
    }

    private Chip button(String label, boolean danger, Runnable onClick) {
        Chip chip = new Chip(getContext(), label, BUTTON_HEIGHT_DP);
        chip.setDanger(danger);
        chip.setFocusableInTouchMode(true);
        chip.setOnClickListener(v -> onClick.run());
        return chip;
    }

    /** Zwei gleich breite Buttons nebeneinander: links "Abbrechen", rechts die Bestätigung. */
    private LinearLayout buttonRow(Chip cancel, Chip confirm, int topMargin) {
        Context c = getContext();
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(0, -2, 1f);
        cancelLp.rightMargin = Ui.px(c, BUTTON_GAP_DP);
        row.addView(cancel, cancelLp);
        row.addView(confirm, new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
        rowLp.topMargin = topMargin;
        row.setLayoutParams(rowLp);
        return row;
    }

    private void openInput(ViewGroup layerView, String titleText, String hint, String initial, String confirmLabel,
                           TextHandler onOk) {
        GlassMenu.CappedPanel box = begin(layerView);
        Context c = getContext();
        box.addView(title(titleText), new LinearLayout.LayoutParams(-1, -2));

        field = new EditText(c);
        // Glaspille: Fläche wie eine Zeile, feine Kante, im Fokus ein Rand in der Akzentfarbe
        int hairlinePx = Math.max(1, Ui.px(c, 1)), focusPx = Math.max(2, Ui.px(c, 2));
        GradientDrawable pill = Ui.round(c, Ui.ROW, Ui.RADIUS_CHIP, Ui.HAIRLINE, hairlinePx);
        field.setBackground(pill);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        field.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI | EditorInfo.IME_FLAG_NO_FULLSCREEN);
        field.setTextSize(Ui.sp(18));
        field.setTypeface(Ui.font(c, 500));
        field.setIncludeFontPadding(false);
        field.setGravity(Gravity.CENTER_VERTICAL);
        field.setTextColor(Color.WHITE);
        field.setHintTextColor(Ui.TEXT_FAINT);
        field.setHint(hint);
        field.setText(initial == null ? "" : initial);
        field.setSelection(field.getText().length());
        field.setHeight(Ui.px(c, FIELD_HEIGHT_DP));
        field.setPadding(Ui.px(c, FIELD_PAD_H_DP), 0, Ui.px(c, FIELD_PAD_H_DP), 0);
        LinearLayout.LayoutParams fieldLp = new LinearLayout.LayoutParams(-1, -2);
        fieldLp.topMargin = Ui.px(c, 18);
        box.addView(field, fieldLp);

        field.setOnFocusChangeListener((v, focused) -> {
            if (focused) pill.setStroke(focusPx, Look.focusRing()); else pill.setStroke(hairlinePx, Ui.HAIRLINE);
            if (!focused) hideKeyboard();   // die erzwungene Tastatur würde sonst über den Buttons stehen bleiben
        });
        pill.setStroke(focusPx, Look.focusRing());   // das Feld hat von Anfang an den Fokus
        field.setOnClickListener(v -> showKeyboardWithRetries(0));

        field.setOnEditorActionListener((v, actionId, e) -> {
            boolean enter = e != null && e.getKeyCode() == KeyEvent.KEYCODE_ENTER && e.getAction() == KeyEvent.ACTION_DOWN;
            if (actionId != EditorInfo.IME_ACTION_DONE && !enter) return false;
            submit(onOk);
            return true;
        });
        cancelButton = button("Abbrechen", false, this::cancel);
        okButton = button(confirmLabel, false, () -> submit(onOk));
        box.addView(buttonRow(cancelButton, okButton, Ui.px(c, 28)));

        // Weit oben, damit die Bildschirmtastatur das Feld nicht verdeckt
        int layerHeight = layerView.getHeight() > 0 ? layerView.getHeight() : Ui.usableHeight(c);
        end(box, INPUT_WIDTH_DP, Gravity.TOP | Gravity.CENTER_HORIZONTAL, Math.round(layerHeight * INPUT_TOP_SHARE), field);
        showKeyboardWithRetries(KEYBOARD_FIRST_MS);
    }

    private void openConfirm(ViewGroup layerView, String titleText, String message, String confirmLabel,
                             boolean destructive, Runnable onConfirm) {
        GlassMenu.CappedPanel box = begin(layerView);
        Context c = getContext();
        box.addView(title(titleText), new LinearLayout.LayoutParams(-1, -2));

        TextView body = Ui.label(c, message, Ui.sp(Ui.SP_BODY + 2), 500, Ui.TEXT_DIM);
        body.setShadowLayer(0, 0, 0, 0);
        body.setLineSpacing(0, 1.2f);
        body.setMaxLines(8);
        body.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams bodyLp = new LinearLayout.LayoutParams(-1, -2);
        bodyLp.topMargin = Ui.px(c, 12);
        box.addView(body, bodyLp);

        cancelButton = button("Abbrechen", false, this::cancel);
        okButton = button(confirmLabel, destructive, () -> close(onConfirm));
        box.addView(buttonRow(cancelButton, okButton, Ui.px(c, 28)));

        end(box, CONFIRM_WIDTH_DP, Gravity.CENTER, 0, destructive ? cancelButton : okButton);
    }

    // ---- Verhalten --------------------------------------------------------------------------------

    /**
     * Zeigt die Tastatur mehrfach nacheinander (am Stick meldet das System teils eine Hardware-Tastatur, dann wird ein
     * einzelner sanfter Aufruf ignoriert). Mehrfaches Zeigen schadet nicht.
     */
    private void showKeyboardWithRetries(long firstDelayMs) {
        long[] delays = {firstDelayMs, firstDelayMs + KEYBOARD_RETRY_MS, firstDelayMs + KEYBOARD_LAST_MS};
        for (long delay : delays) {
            if (delay == 0) showKeyboardNow(); else field.postDelayed(this::showKeyboardNow, delay);
        }
    }

    @SuppressWarnings("deprecation")
    private void showKeyboardNow() {
        if (closing || field == null || !field.isAttachedToWindow() || !field.hasFocus()) return;
        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(field, InputMethodManager.SHOW_FORCED);
    }

    private void hideKeyboard() {
        if (field == null) return;
        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null && field.getWindowToken() != null) imm.hideSoftInputFromWindow(field.getWindowToken(), 0);
    }

    private void submit(TextHandler onOk) {
        if (closing) return;
        String text = field.getText().toString().trim();
        close(() -> onOk.accept(text));
    }

    /** Abbrechen-Button: schließt immer sofort. */
    private void cancel() {
        close(null);
    }

    /**
     * Zurück. Ist die Tastatur sichtbar, schließt die Taste nur sie (der Dialog bleibt, der Fokus bleibt im Dialog);
     * erst die nächste schließt den Dialog. Ob die Tastatur sichtbar war, sagt das System über einen ResultReceiver.
     */
    @Override public void dismiss() {
        if (closing || backProbePending) return;
        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (field == null || imm == null || field.getWindowToken() == null) {
            close(null);
            return;
        }
        backProbePending = true;
        boolean accepted = imm.hideSoftInputFromWindow(field.getWindowToken(), 0, new HideProbe(this));
        if (!accepted) {
            backProbePending = false;
            close(null);
            return;
        }
        postDelayed(this::onProbeTimeout, PROBE_TIMEOUT_MS);
    }

    private void onProbeTimeout() {
        if (!backProbePending) return;   // Antwort kam rechtzeitig
        backProbePending = false;
        close(null);
    }

    private void onProbeResult(boolean keyboardWasVisible) {
        if (!backProbePending) return;
        backProbePending = false;
        if (closing) return;
        if (keyboardWasVisible) {
            focusInsideDialog();
            postDelayed(this::focusInsideDialog, 120);   // das System verschiebt den Fokus teils erst beim Ausblenden
        } else {
            close(null);
        }
    }

    /** Antwort des Systems auf "Tastatur ausblenden": RESULT_HIDDEN heißt, sie war sichtbar. */
    private static final class HideProbe extends ResultReceiver {
        private final GlassPrompt prompt;

        HideProbe(GlassPrompt prompt) {
            super(new Handler(Looper.getMainLooper()));
            this.prompt = prompt;
        }

        @Override protected void onReceiveResult(int resultCode, Bundle resultData) {
            prompt.onProbeResult(resultCode == InputMethodManager.RESULT_HIDDEN);
        }
    }

    /** Sorgt dafür, dass ein Element des Dialogs den Fokus hat (nie das Panel oder die Ansicht darunter). */
    private void focusInsideDialog() {
        if (closing || !isAttachedToWindow()) return;
        View focused = findFocus();
        if (focused != null && (focused == field || focused == cancelButton || focused == okButton)) return;
        View target = field != null ? field : (okButton != null ? okButton : cancelButton);
        if (target != null) target.requestFocus();
    }

    @Override public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        if (hasWindowFocus) post(this::focusInsideDialog);   // z. B. nachdem die Tastatur verschwunden ist
    }

    private void close(Runnable afterwards) {
        if (closing) return;
        closing = true;
        hideKeyboard();
        GlassMenu.retire(layer, this, scrim, panel, previousFocus);
        if (afterwards != null) afterwards.run();
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        if (code == KeyEvent.KEYCODE_BACK) return super.dispatchKeyEvent(event);   // Zurück kommt über Ui.dismissTopModal
        if (code == KeyEvent.KEYCODE_MENU || closing) return true;
        if (navigate(event)) return true;
        return super.dispatchKeyEvent(event) || GlassMenu.trapDirections(this, event);
    }

    /**
     * Eigene Fokusführung statt der Suche des Systems (der EditText schluckt sonst Runter und Rechts, und ohne Tastatur
     * bliebe der Fokus im Feld hängen): Runter und Rechts am Textende springen vom Feld zu "OK", Hoch geht von den
     * Buttons zurück ins Feld, links/rechts wechselt zwischen den Buttons. Auswahltaste im Feld zeigt die Tastatur.
     * Gibt true zurück, wenn die Taste verbraucht ist; Richtungstasten, die hier nicht passen, fängt später der Tastenfang.
     */
    private boolean navigate(KeyEvent event) {
        int code = event.getKeyCode();
        boolean isDown = event.getAction() == KeyEvent.ACTION_DOWN;
        View focused = findFocus();
        boolean inField = field != null && focused == field;
        boolean known = inField || focused == cancelButton || focused == okButton;

        if (inField && (code == KeyEvent.KEYCODE_DPAD_CENTER)) {
            if (event.getAction() == KeyEvent.ACTION_UP) showKeyboardWithRetries(0);
            return true;
        }
        if (GlassMenu.directionOf(code) == 0 || !isDown) return false;
        if (!known) {   // Fokus liegt nirgends im Dialog: zurückholen und die Taste verbrauchen
            focusInsideDialog();
            return true;
        }
        View target = null;
        if (inField) {
            boolean cursorAtEnd = field.getSelectionEnd() >= field.getText().length() && field.getSelectionStart() == field.getSelectionEnd();
            if (code == KeyEvent.KEYCODE_DPAD_DOWN || (code == KeyEvent.KEYCODE_DPAD_RIGHT && cursorAtEnd)) target = okButton;
            else if (code == KeyEvent.KEYCODE_DPAD_UP) return true;
            else return false;   // links und rechts bewegen den Cursor im Text
        } else if (code == KeyEvent.KEYCODE_DPAD_LEFT) {
            target = cancelButton;
        } else if (code == KeyEvent.KEYCODE_DPAD_RIGHT) {
            target = okButton;
        } else if (code == KeyEvent.KEYCODE_DPAD_UP && field != null) {
            target = field;
        }
        if (target != null && target != focused) target.requestFocus();
        return true;
    }
}
