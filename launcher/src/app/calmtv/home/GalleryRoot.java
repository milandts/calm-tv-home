package app.calmtv.home;

import android.content.Context;
import android.view.FocusFinder;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;

/** Wurzel der Galerie: Richtungstasten verlassen die Ansicht nie, die Startseite darunter bekommt sie nicht. */
final class GalleryRoot extends FrameLayout {
    GalleryRoot(Context c) {
        super(c);
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        int direction = directionOf(event.getKeyCode());
        if (direction == 0) return super.dispatchKeyEvent(event);
        if (super.dispatchKeyEvent(event)) return true;   // ein Unterbild (Ausschnitt, Helligkeit) hat sie selbst gebraucht
        if (event.getAction() != KeyEvent.ACTION_DOWN) return true;
        View focused = findFocus();
        View next = focused == null ? null : FocusFinder.getInstance().findNextFocus(this, focused, direction);
        if (next != null) next.requestFocus(direction);
        return true;
    }

    private static int directionOf(int key) {
        switch (key) {
            case KeyEvent.KEYCODE_DPAD_UP: return View.FOCUS_UP;
            case KeyEvent.KEYCODE_DPAD_DOWN: return View.FOCUS_DOWN;
            case KeyEvent.KEYCODE_DPAD_LEFT: return View.FOCUS_LEFT;
            case KeyEvent.KEYCODE_DPAD_RIGHT: return View.FOCUS_RIGHT;
            default: return 0;
        }
    }
}
