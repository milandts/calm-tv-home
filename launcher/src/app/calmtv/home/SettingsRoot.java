package app.calmtv.home;

import android.content.Context;
import android.view.KeyEvent;
import android.widget.FrameLayout;

/** Wurzel der Einstellungen: verbraucht alle Pfeiltasten, damit der Fokus nie in die Startseite darunter läuft. */
final class SettingsRoot extends FrameLayout {
    private final SettingsScreen owner;

    SettingsRoot(Context c, SettingsScreen owner) {
        super(c);
        this.owner = owner;
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        int key = event.getKeyCode();
        boolean isArrow = key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_DOWN
                || key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT;
        if (!isArrow) return super.dispatchKeyEvent(event);
        if (event.getAction() == KeyEvent.ACTION_DOWN) owner.onArrow(key, event.getRepeatCount());
        return true;
    }
}
