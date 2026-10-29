package app.calmtv.home;

import android.view.KeyEvent;
import android.view.View;

/**
 * Die drei Glas-Tasten oben (Apps, Suche, Einstellungen): Klick-Ziele und Fokus-Wege. Links und Rechts bleiben in der Leiste,
 * zum Dock hin geht es zur zuletzt fokussierten Dock-Kachel; die Menütaste öffnet das Kontrollzentrum.
 */
final class HomeBar {
    private final HomeActivity home;
    private final HomeScene scene;
    private Pill lastFocused;

    HomeBar(HomeActivity home, HomeScene scene) {
        this.home = home;
        this.scene = scene;
    }

    void install() {
        scene.appsPill.setOnClickListener(v -> home.grid().show(0));
        scene.searchPill.setOnClickListener(v -> home.overlay().openSearch());
        scene.settingsPill.setOnClickListener(v -> home.openSettings(null));
        for (final Pill pill : scene.pills()) {
            pill.setOnKeyListener((v, code, event) -> onKey(pill, code, event));
            final View.OnFocusChangeListener drawing = pill.getOnFocusChangeListener();   // das Zeichnen des Fokus behält die Taste selbst
            pill.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) lastFocused = pill;
                if (drawing != null) drawing.onFocusChange(v, hasFocus);
            });
        }
    }

    /** Fokus auf die zuletzt benutzte sichtbare Taste, sonst die erste; false, wenn die Leiste leer ist. */
    boolean focus() {
        HomeGeometry geometry = home.geometry();
        Pill target = geometry.isPillVisible(lastFocused) ? lastFocused : geometry.edgePill(true);
        if (target == null) return false;
        target.requestFocus();
        return true;
    }

    /** Macht die Tasten fokussierbar oder nicht (zum Beispiel, solange das Raster oder eine Vollbild-Ansicht offen ist). */
    void setFocusable(boolean focusable) {
        for (Pill pill : scene.pills()) pill.setFocusable(focusable);
    }

    private boolean onKey(Pill pill, int code, KeyEvent event) {
        if (code == KeyEvent.KEYCODE_MENU) {
            if (event.getAction() == KeyEvent.ACTION_UP) home.openControlCenter();
            return true;
        }
        HomeGeometry geometry = home.geometry();
        boolean isPress = event.getAction() == KeyEvent.ACTION_DOWN;
        if (code == KeyEvent.KEYCODE_DPAD_LEFT) return pill == geometry.edgePill(true);
        if (code == KeyEvent.KEYCODE_DPAD_RIGHT) return pill == geometry.edgePill(false);
        boolean isDockOnTop = HomeGeometry.dockTop();
        if (code == (isDockOnTop ? KeyEvent.KEYCODE_DPAD_UP : KeyEvent.KEYCODE_DPAD_DOWN)) {   // zum Dock hin
            View target = home.dock().lastFocus();
            if (target == null) return false;
            if (isPress) target.requestFocus();
            return true;
        }
        if (code == (isDockOnTop ? KeyEvent.KEYCODE_DPAD_DOWN : KeyEvent.KEYCODE_DPAD_UP)) {   // vom Dock weg
            if (isDockOnTop && isPress) home.grid().show(0);   // das Raster liegt unter der Leiste
            return true;
        }
        return false;
    }
}
