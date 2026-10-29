package app.calmtv.home;

import android.view.View;
import android.view.ViewGroup;

/**
 * Verwaltet die Vollbild-Ansichten über der Startseite (Einstellungen, Kontrollzentrum, Willkommen, Bilderverwaltung):
 * höchstens eine zugleich, mit Ein- und Ausblenden. Solange eine offen ist, sperrt die Startseite ihren Fokus;
 * beim Schließen kehrt er dorthin zurück, wo die erste Ansicht geöffnet wurde.
 */
final class HomeScreens {
    private static final float ENTER_SCALE = 0.97f;

    private final HomeActivity home;
    private final HomeScene scene;
    private Screen current;
    private View returnFocus;

    HomeScreens(HomeActivity home, HomeScene scene) {
        this.home = home;
        this.scene = scene;
    }

    boolean isOpen() {
        return current != null;
    }

    Screen current() {
        return current;
    }

    /** Zeigt die Ansicht und ersetzt eine offene; der Rückkehr-Fokus bleibt vom ersten Öffnen. */
    void open(Screen screen) {
        if (current != null) {
            remove(current, false);
        } else {
            returnFocus = home.getCurrentFocus();
            blockHome(true);
        }
        current = screen;
        View view = screen.view();
        scene.screenLayer.addView(view, HomeScene.fill());
        view.setAlpha(0f);
        view.setScaleX(ENTER_SCALE);
        view.setScaleY(ENTER_SCALE);
        view.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(Ui.MEDIUM).setInterpolator(Ui.EASE).start();
        screen.onShown();
    }

    void close() {
        if (current == null) return;
        Screen closing = current;
        current = null;
        remove(closing, true);
        blockHome(false);
        View target = returnFocus;
        returnFocus = null;
        if (target != null && target.isAttachedToWindow() && target.isFocusable()) target.requestFocus(); else home.dock().focus();
    }

    private void remove(Screen screen, boolean animate) {
        final View view = screen.view();
        screen.onClosed();
        if (view instanceof ViewGroup) ((ViewGroup) view).setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        if (animate) {
            view.animate().alpha(0f).setDuration(Ui.FAST).withEndAction(() -> scene.screenLayer.removeView(view)).start();
        } else {
            view.animate().cancel();
            scene.screenLayer.removeView(view);
        }
    }

    /** Sperrt oder gibt den Fokus der Startseite frei (Dock, Raster und Leisten-Tasten). */
    private void blockHome(boolean isBlocked) {
        int mode = isBlocked ? ViewGroup.FOCUS_BLOCK_DESCENDANTS : ViewGroup.FOCUS_AFTER_DESCENDANTS;
        scene.dock.setDescendantFocusability(mode);
        scene.grid.setDescendantFocusability(mode);
        home.bar().setFocusable(!isBlocked && !home.grid().isOpen());
    }

    /** Ob eines der Menüs, Eingabefelder oder Meldungen über allem offen ist. */
    boolean hasModal() {
        for (int i = 0; i < scene.modalLayer.getChildCount(); i++) {
            if (scene.modalLayer.getChildAt(i) instanceof Ui.Modal) return true;
        }
        return false;
    }
}
