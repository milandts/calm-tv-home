package app.calmtv.home;

import android.view.KeyEvent;
import android.view.View;
import android.widget.LinearLayout;

import java.util.List;

/**
 * Das Glas-Dock mit den Lieblings-Apps: baut die Kacheln aus {@link DockStore} und {@link AppCatalog}, regelt Fokus und Tasten
 * (Hoch und Runter wechseln zu Leiste und Raster, Links und Rechts bleiben im Dock, die Menütaste öffnet das Kachel-Menü)
 * und zeigt den Namen der fokussierten App über dem Dock. Die Kacheln tragen den Paketnamen als Tag; so erkennt man Dock-Kacheln.
 */
final class HomeDock {
    private static final long TITLE_FADE_MS = 150;

    private final HomeActivity home;
    private final HomeScene scene;
    private final AppCatalog catalog;
    private final DockStore store;
    private final TileSizes sizes;
    private View lastFocus;
    private boolean isEmptyAndLoaded;   // Apps sind geladen und das Dock ist leer: dann bleibt die Einstellungen-Taste als einziger Weg sichtbar

    HomeDock(HomeActivity home, HomeScene scene, AppCatalog catalog, DockStore store, TileSizes sizes) {
        this.home = home;
        this.scene = scene;
        this.catalog = catalog;
        this.store = store;
        this.sizes = sizes;
    }

    View lastFocus() {
        return lastFocus;
    }

    void forgetFocus() {
        lastFocus = null;
    }

    boolean isEmptyAndLoaded() {
        return isEmptyAndLoaded;
    }

    int count() {
        return scene.dockRow.getChildCount();
    }

    /** Die Kachel in dieser Spalte (begrenzt auf die letzte); null bei leerem Dock. */
    Tile tileAt(int column) {
        int count = count();
        return count == 0 ? null : (Tile) scene.dockRow.getChildAt(Math.min(Math.max(column, 0), count - 1));
    }

    /** Fokus auf die zuletzt fokussierte Kachel, sonst in die Leiste. */
    void focus() {
        if (lastFocus != null) lastFocus.requestFocus(); else home.bar().focus();
    }

    /** True, wenn dieser Fokus-Kandidat eine Dock-Kachel ist. */
    static boolean isDockTile(View view) {
        return view != null && view.getTag() instanceof String;
    }

    /** Baut das Dock neu auf; {@code focusId} (oder die zuletzt fokussierte App) bekommt danach den Fokus. */
    void rebuild(String focusId) {
        if (focusId == null && lastFocus != null) focusId = (String) lastFocus.getTag();
        scene.dockRow.removeAllViews();
        lastFocus = null;
        // Das Glas skaliert mit der Größenstufe: Innenabstand und Mindestbreite (sechs Kacheln) wachsen und schrumpfen mit.
        float scale = HomeGeometry.dockScale();
        int sidePad = scene.px(HomeGeometry.dockPadDp(home, HomeGeometry.dockTileWidth(home)));
        int verticalPad = scene.px(Math.round(14 * scale));
        scene.dock.setRadius(scene.px(26 * scale));
        scene.dockRow.setPadding(sidePad, verticalPad, sidePad, verticalPad);
        scene.dock.setMinimumWidth(6 * sizes.dockWidth + 5 * sizes.dockGap + 2 * sidePad);
        scene.dock.requestLayout();   // Breite neu messen, sonst bleibt das Glas nach einem Wechsel der Größenstufe so breit wie vorher
        scene.bottom.requestLayout();

        List<String> ids = store.visible();
        Tile toFocus = null;
        for (int i = 0; i < ids.size(); i++) {
            String id = ids.get(i);
            Tile tile = makeTile(id);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
            if (i > 0) params.leftMargin = sizes.dockGap;
            scene.dockRow.addView(tile, params);
            if (toFocus == null || id.equals(focusId)) toFocus = tile;
        }
        HomeGeometry geometry = home.geometry();
        geometry.setDockCount(ids.size());
        geometry.apply(isEmptyAndLoaded);   // Name und Leiste richten sich nach der Glasbreite
        scene.dockHint.setVisibility(ids.isEmpty() ? View.VISIBLE : View.GONE);
        scene.dockRow.setMinimumHeight(ids.isEmpty() ? scene.px(60) : 0);
        boolean nowEmptyAndLoaded = ids.isEmpty() && !catalog.isEmpty();
        if (nowEmptyAndLoaded != isEmptyAndLoaded) {
            isEmptyAndLoaded = nowEmptyAndLoaded;
            geometry.apply(isEmptyAndLoaded);
        }
        // Liegt der Fokus woanders (Raster, eine Leisten-Taste), bleibt er dort; nur ein verlorener Fokus kommt ins Dock.
        View focused = home.getCurrentFocus();
        boolean isElsewhere = focused != null && !isDockTile(focused);
        if (toFocus != null) {
            lastFocus = toFocus.card;
            if (!home.overlay().isOpen() && !isElsewhere) toFocus.card.requestFocus();
        } else if (!home.overlay().isOpen() && !home.grid().isOpen()) {
            home.bar().focus();
        }
    }

    private Tile makeTile(final String id) {
        final AppEntry app = catalog.get(id);
        final int labelMode = HomeGeometry.labelMode();
        final Tile tile = Tile.art(home, sizes.dockWidth, sizes.dockHeight, catalog.art(id), home.nameOf(app), labelMode != 0, 1.1f);
        tile.setCornerDp(Math.round(Config.val(Opts.TILE_RADIUS) * HomeGeometry.dockScale()));
        if (labelMode == 2) tile.pinLabel(false);
        tile.card.setTag(id);
        tile.card.setOnClickListener(v -> home.launch(app));
        tile.card.setOnLongClickListener(v -> {
            home.menus().dock(id);
            return true;
        });
        final View.OnKeyListener menuKey = AppMenus.menuKey(() -> home.menus().dock(id));
        tile.card.setOnKeyListener((v, code, event) -> onKey(tile, menuKey, v, code, event));
        tile.onFocus((v, hasFocus) -> onTileFocus(tile, app, labelMode, v, hasFocus));
        return tile;
    }

    private boolean onKey(Tile tile, View.OnKeyListener menuKey, View v, int code, KeyEvent event) {
        boolean isPress = event.getAction() == KeyEvent.ACTION_DOWN;
        AppGrid grid = home.grid();
        if (code == KeyEvent.KEYCODE_DPAD_DOWN) {
            if (isPress) {
                int column = Math.min(scene.dockRow.indexOfChild(tile), sizes.columns - 1);   // ein Dock kann länger sein als das Raster breit
                if (!grid.isOpen() && HomeGeometry.dockTop() && home.geometry().barShown()) home.bar().focus();   // Dock oben mit Leiste darunter: erst die Leiste
                else if (grid.isOpen()) grid.focus(column);
                else grid.show(column);
            }
            return true;
        }
        if (code == KeyEvent.KEYCODE_DPAD_UP) {
            if (isPress) {
                if (grid.isOpen()) grid.hide();
                else if (!HomeGeometry.dockTop()) home.bar().focus();   // Dock oben: darüber ist nichts
            }
            return true;
        }
        boolean isSideways = code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT;
        if (isSideways) {
            int direction = code == KeyEvent.KEYCODE_DPAD_LEFT ? View.FOCUS_LEFT : View.FOCUS_RIGHT;
            return !isDockTile(v.focusSearch(direction));   // an beiden Enden im Dock bleiben
        }
        return menuKey.onKey(v, code, event);
    }

    private void onTileFocus(Tile tile, AppEntry app, int labelMode, View view, boolean hasFocus) {
        if (hasFocus) {
            lastFocus = view;
            scene.title.setText(home.nameOf(app));
            if (!home.grid().isOpen()) scene.title.animate().alpha(1f).setDuration(Ui.ms(TITLE_FADE_MS)).start();
            if (labelMode == 2) tile.pinLabel(true);
            return;
        }
        if (labelMode == 2) tile.pinLabel(false);
        // Den Namen nur ausblenden, wenn der Fokus das Dock wirklich verlassen hat.
        home.post(() -> {
            if (!isDockTile(home.getCurrentFocus())) scene.title.animate().alpha(0f).setDuration(Ui.ms(TITLE_FADE_MS)).start();
        });
    }
}
