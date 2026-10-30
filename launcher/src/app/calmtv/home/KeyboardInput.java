package app.calmtv.home;

import android.content.Context;
import android.view.KeyEvent;
import android.widget.EditText;

/**
 * Verbindet die eigene Tastatur ({@link GlassKeyboard}) mit einem Textfeld: Tasten schreiben ans Ende des Textes, "Löschen"
 * nimmt das letzte Zeichen. Das Feld selbst nimmt keinen Fokus und keine Systemtastatur mehr. Zeichen einer angeschlossenen
 * Tastatur gehen über {@link #typeFromHardwareKey} direkt ins Feld (die Fernbedienung hat keine).
 */
final class KeyboardInput implements GlassKeyboard.Listener {
    private final EditText field;
    private final GlassKeyboard keyboard;
    private final Runnable onDone;

    /** @param doneLabel Beschriftung der Taste zum Abschließen oder null; {@code onDone} läuft, wenn man sie drückt */
    KeyboardInput(Context c, EditText field, String doneLabel, float keyHeightDp, Runnable onDone) {
        this.field = field;
        this.onDone = onDone;
        field.setFocusable(false);
        field.setCursorVisible(false);
        keyboard = new GlassKeyboard(c, this, doneLabel, keyHeightDp);
        keyboard.sync(field.getText().toString());
    }

    GlassKeyboard keyboard() {
        return keyboard;
    }

    @Override public void onText(String text) {
        setText(field.getText().toString() + text);
    }

    @Override public void onDelete() {
        setText(KeyboardLayout.deleteLast(field.getText().toString()));
    }

    @Override public void onDone() {
        if (onDone != null) onDone.run();
    }

    private void setText(String text) {
        field.setText(text);
        field.setSelection(text.length());
        keyboard.sync(text);
    }

    /** Gibt true zurück, wenn die Taste ein Zeichen oder Rückschritt war und im Feld gelandet ist. */
    boolean typeFromHardwareKey(KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN || event.isCtrlPressed() || event.isAltPressed()) return false;
        if (event.getKeyCode() == KeyEvent.KEYCODE_DEL) {
            onDelete();
            return true;
        }
        int character = event.getUnicodeChar();
        if (character < ' ') return false;
        onText(new String(Character.toChars(character)));
        return true;
    }
}
