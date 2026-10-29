package app.calmtv.home;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.view.KeyEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Das Raster mit allen übrigen Apps ("Bibliothek"), das beim Druck auf Runter unter dem Dock erscheint. Es zeigt in Abschnitten
 * die zuletzt benutzten Apps, die Ordner und alle anderen Apps; was im Dock steht, in einem Ordner liegt oder ausgeblendet ist,
 * taucht im letzten Abschnitt nicht auf. Beim Öffnen gleitet das Dock nach oben, das Foto wird unscharf und abgedunkelt.
 */
final class AppGrid {
    private static final long SLIDE_MS = 360, TITLE_FADE_MS = 150;
    private static final int SLIDE_DISTANCE_DP = 70, HEAD_SHIFT_DP = 24;

    /** Wo gerade Kacheln eingefügt werden: die offene Zeile, ihre Spalte und wie viele Zeilen es schon gibt. */
    private static final class Cursor {
        LinearLayout row;
        int column, rows;
    }

    private final HomeActivity home;
    private final HomeScene scene;
    private final AppCatalog catalog;
    private final DockStore store;
    private final AppLayout layout;
    private final TileSizes sizes;
    private final List<Tile> tiles = new ArrayList<>();
    private boolean isOpen;

    AppGrid(HomeActivity home, HomeScene scene, AppCatalog catalog, DockStore store, AppLayout layout, TileSizes sizes) {
        this.home = home;
        this.scene = scene;
        this.catalog = catalog;
        this.store = store;
        this.layout = layout;
        this.sizes = sizes;
    }

    boolean isOpen() {
        return isOpen;
    }

    /** Der Fokus liegt auf einer Rasterkachel: ihr Index (die Kacheln tragen ihn als Tag), sonst -1. */
    int focusedIndex() {
        View focused = home.getCurrentFocus();
        return focused != null && focused.getTag() instanceof Integer ? (Integer) focused.getTag() : -1;
    }

    // ---- Öffnen und Schließen --------------------------------------------------------------------

    /** Schiebt das Dock nach oben, macht das Foto unscharf und zeigt alle übrigen Apps darunter. */
    void show(int column) {
        if (isOpen) {
            focus(column);
            return;
        }
        isOpen = true;
        rebuild();
        home.bar().setFocusable(false);
        float shift = 0f;   // ein Dock oben bleibt, wo es ist; unten schiebt es sich nach oben
        if (!HomeGeometry.dockTop()) {
            int[] at = new int[2];
            scene.dock.getLocationOnScreen(at);
            shift = scene.px(30) - (at[1] - scene.bottom.getTranslationY());
        }
        slide(scene.bottom, shift);
        scene.title.animate().alpha(0f).setDuration(Ui.ms(TITLE_FADE_MS)).start();
        for (View v : new View[] {scene.header.view(), scene.bar}) {
            v.animate().alpha(0f).translationY(HomeGeometry.stripSign() * scene.px(HEAD_SHIFT_DP)).setDuration(Ui.ms(SLIDE_MS))
                    .setInterpolator(HomeScene.EASE).start();
        }
        scene.libraryBg.animate().alpha(1f).setDuration(Ui.ms(SLIDE_MS)).start();
        scene.libraryDim.animate().alpha(1f).setDuration(Ui.ms(SLIDE_MS)).start();

        scene.grid.setPadding(0, scene.px(30) + scene.dock.getHeight() + scene.px(12), 0, scene.px(36));
        scene.grid.scrollTo(0, 0);
        scene.grid.setVisibility(View.VISIBLE);
        scene.grid.setAlpha(0f);
        scene.grid.setTranslationY(scene.px(SLIDE_DISTANCE_DP));
        scene.grid.animate().alpha(1f).translationY(0f).setDuration(Ui.ms(SLIDE_MS)).setInterpolator(HomeScene.EASE).start();
        focus(column);
    }

    void hide() {
        if (!isOpen) return;
        isOpen = false;
        slide(scene.bottom, 0f);
        for (View v : new View[] {scene.header.view(), scene.bar}) {
            v.animate().alpha(1f).translationY(0f).setDuration(Ui.ms(SLIDE_MS)).setInterpolator(HomeScene.EASE).start();
        }
        scene.libraryBg.animate().alpha(0f).setDuration(Ui.ms(SLIDE_MS)).start();
        scene.libraryDim.animate().alpha(0f).setDuration(Ui.ms(SLIDE_MS)).start();
        scene.grid.animate().alpha(0f).translationY(scene.px(SLIDE_DISTANCE_DP)).setDuration(Ui.ms(SLIDE_MS)).setInterpolator(HomeScene.EASE)
                .withEndAction(() -> {
                    if (!isOpen) scene.grid.setVisibility(View.GONE);
                }).start();
        home.bar().setFocusable(true);
        if (home.dock().lastFocus() != null) {
            home.dock().lastFocus().requestFocus();
            scene.title.animate().alpha(1f).setDuration(Ui.ms(SLIDE_MS)).start();
        } else {
            home.bar().focus();
        }
    }

    private void slide(View view, float translationY) {
        view.animate().translationY(translationY).setDuration(Ui.ms(SLIDE_MS)).setInterpolator(HomeScene.EASE).start();
    }

    /** Fokus auf die Kachel in dieser Spalte der obersten Zeile (begrenzt auf die vorhandenen). */
    void focus(int column) {
        if (!tiles.isEmpty()) tiles.get(Math.max(0, Math.min(column, tiles.size() - 1))).card.requestFocus();
    }

    // ---- Aufbau ----------------------------------------------------------------------------------

    /** Baut das Raster neu auf und hält den Fokus auf derselben Stelle. */
    void rebuild() {
        int focusIndex = focusedIndex();
        scene.gridRows.removeAllViews();
        tiles.clear();

        List<String> docked = store.visible();
        Set<String> foldered = new HashSet<>();
        List<String> folders = new ArrayList<>();
        for (String folder : layout.folderNames()) {
            List<String> members = home.installed(layout.folderApps(folder));
            if (members.isEmpty()) continue;
            folders.add(folder);
            foldered.addAll(members);
        }
        List<AppEntry> recent = new ArrayList<>();
        if (layout.showRecent()) {
            for (String pkg : layout.recent()) {
                AppEntry app = catalog.get(pkg);
                if (app != null && !docked.contains(pkg) && !layout.isHidden(pkg) && recent.size() < HomeGeometry.recentLimit()) recent.add(app);
            }
        }
        List<AppEntry> others = new ArrayList<>();
        for (AppEntry app : catalog.all()) {
            if (!docked.contains(app.pkg) && !layout.isHidden(app.pkg) && !foldered.contains(app.pkg)) others.add(app);
        }
        Collections.sort(others, comparator());

        Cursor cursor = new Cursor();
        boolean hasHeaders = (recent.isEmpty() ? 0 : 1) + (folders.isEmpty() ? 0 : 1) + (others.isEmpty() ? 0 : 1) > 1;
        if (!recent.isEmpty()) {
            startSection(cursor, hasHeaders ? "Zuletzt benutzt" : null);
            for (AppEntry app : recent) addTile(cursor, appTile(app), AppMenus.menuKey(() -> home.menus().app(app)));
        }
        if (!folders.isEmpty()) {
            startSection(cursor, hasHeaders ? "Ordner" : null);
            for (String folder : folders) {
                addTile(cursor, folderTile(folder, home.installed(layout.folderApps(folder))), AppMenus.menuKey(() -> home.menus().folder(folder)));
            }
        }
        if (!others.isEmpty()) {
            startSection(cursor, hasHeaders ? "Alle Apps" : null);
            for (AppEntry app : others) addTile(cursor, appTile(app), AppMenus.menuKey(() -> home.menus().app(app)));
        }
        TextView hint = HomeText.plain(home, 12, "sans-serif", 0x99FFFFFF);
        hint.setText(tiles.isEmpty()
                ? "Keine weiteren Apps."
                : "Menütaste auf einer App: Dock, Ordner, Umbenennen, Ausblenden. Menütaste auf einer Dock-App: verschieben oder entfernen.");
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(-2, -2);
        hintParams.topMargin = scene.px(14);
        scene.gridRows.addView(hint, hintParams);
        if (focusIndex >= 0 && isOpen) {
            if (tiles.isEmpty()) home.dock().focus(); else focus(Math.min(focusIndex, tiles.size() - 1));
        }
    }

    /** Reihenfolge der übrigen Apps: nach vorn gezogene zuerst, dann je nach Einstellung zuletzt benutzte oder neu installierte, sonst nach Namen. */
    private java.util.Comparator<AppEntry> comparator() {
        final List<String> pinned = layout.pinned();
        final List<String> used = layout.recent();
        final int sort = Config.val(Opts.GRID_SORT);
        return (x, y) -> {
            int pinX = pinned.indexOf(x.pkg), pinY = pinned.indexOf(y.pkg);
            if (pinX >= 0 && pinY >= 0) return pinX - pinY;
            if (pinX >= 0) return -1;
            if (pinY >= 0) return 1;
            if (sort == 1) {
                int useX = used.indexOf(x.pkg), useY = used.indexOf(y.pkg);
                if (useX >= 0 && useY >= 0) return useX - useY;
                if (useX >= 0) return -1;
                if (useY >= 0) return 1;
            } else if (sort == 2) {
                int newer = Long.compare(catalog.installTime(y.pkg), catalog.installTime(x.pkg));
                if (newer != 0) return newer;
            }
            return home.nameOf(x).compareToIgnoreCase(home.nameOf(y));
        };
    }

    /** Beginnt einen Abschnitt: neue Zeile, auf Wunsch mit Überschrift. */
    private void startSection(Cursor cursor, String header) {
        cursor.row = null;
        if (header == null) return;
        TextView label = HomeText.caps(home, 10);
        label.setText(header);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
        params.topMargin = scene.px(cursor.rows == 0 ? 6 : 16);
        params.leftMargin = scene.px(4);
        scene.gridRows.addView(label, params);
    }

    private void addTile(Cursor cursor, Tile tile, final View.OnKeyListener menuKey) {
        if (cursor.row == null || cursor.column == sizes.columns) {
            cursor.row = new LinearLayout(home);
            cursor.row.setOrientation(LinearLayout.HORIZONTAL);
            cursor.row.setClipChildren(false);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-2, -2);
            rowParams.topMargin = scene.px(cursor.rows == 0 ? 10 : 4);
            scene.gridRows.addView(cursor.row, rowParams);
            cursor.column = 0;
            cursor.rows++;
        }
        final int index = tiles.size();
        final boolean isTopRow = cursor.rows == 1;
        tile.card.setTag(index);
        tile.card.setOnKeyListener((v, code, event) -> onKey(index, isTopRow, menuKey, v, code, event));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
        if (cursor.column > 0) params.leftMargin = sizes.gridGap;
        cursor.row.addView(tile, params);
        cursor.column++;
        tiles.add(tile);
    }

    private boolean onKey(int index, boolean isTopRow, View.OnKeyListener menuKey, View v, int code, KeyEvent event) {
        if (code == KeyEvent.KEYCODE_DPAD_UP && isTopRow) {   // oberste Zeile: hoch ins Dock, zur Kachel darüber
            if (event.getAction() == KeyEvent.ACTION_DOWN && home.dock().count() > 0) home.dock().tileAt(index).card.requestFocus();
            return true;
        }
        int direction = code == KeyEvent.KEYCODE_DPAD_DOWN ? View.FOCUS_DOWN
                : code == KeyEvent.KEYCODE_DPAD_UP ? View.FOCUS_UP
                : code == KeyEvent.KEYCODE_DPAD_LEFT ? View.FOCUS_LEFT
                : code == KeyEvent.KEYCODE_DPAD_RIGHT ? View.FOCUS_RIGHT : 0;
        if (direction != 0) {
            View next = v.focusSearch(direction);
            return next == null || !(next.getTag() instanceof Integer);   // im Raster bleiben
        }
        return menuKey.onKey(v, code, event);
    }

    // ---- Kacheln ---------------------------------------------------------------------------------

    /** Kachel einer App: Klick startet, langes Drücken öffnet das Menü (die Menütaste setzt {@link #addTile}). */
    private Tile appTile(final AppEntry app) {
        Tile tile = Tile.art(home, sizes.gridWidth, sizes.gridHeight, catalog.art(app.pkg), home.nameOf(app), true, 1.1f);
        tile.setCornerDp(Config.val(Opts.TILE_RADIUS));
        tile.card.setOnClickListener(v -> home.launch(app));
        tile.card.setOnLongClickListener(v -> {
            home.menus().app(app);
            return true;
        });
        return tile;
    }

    private Tile folderTile(final String folder, List<String> members) {
        Drawable cover = new BitmapDrawable(home.getResources(), folderCover(members));
        Tile tile = Tile.art(home, sizes.gridWidth, sizes.gridHeight, cover, folder, true, 1.1f);
        tile.setCornerDp(Config.val(Opts.TILE_RADIUS));
        tile.card.setOnClickListener(v -> home.overlay().openFolder(folder));
        tile.card.setOnLongClickListener(v -> {
            home.menus().folder(folder);
            return true;
        });
        return tile;
    }

    /** Das Ordner-Symbol: bis zu vier Apps als kleine Kacheln auf dunklem Grund. */
    private Bitmap folderCover(List<String> members) {
        int width = sizes.gridWidth, height = sizes.gridHeight;
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(0xFF2C2F36);
        int gap = height / 10, cellWidth = (width - 3 * gap) / 2, cellHeight = (height - 3 * gap) / 2;
        Path clip = new Path();
        for (int i = 0; i < Math.min(4, members.size()); i++) {
            Drawable source = catalog.art(members.get(i));
            if (source == null) continue;
            Drawable.ConstantState state = source.getConstantState();
            Drawable cell = state == null ? source : state.newDrawable().mutate();
            int left = gap + (i % 2) * (cellWidth + gap), top = gap + (i / 2) * (cellHeight + gap);
            cell.setBounds(left, top, left + cellWidth, top + cellHeight);
            canvas.save();
            clip.reset();
            clip.addRoundRect(new RectF(left, top, left + cellWidth, top + cellHeight), scene.px(5), scene.px(5), Path.Direction.CW);
            canvas.clipPath(clip);
            cell.draw(canvas);
            canvas.restore();
        }
        return bitmap;
    }
}
