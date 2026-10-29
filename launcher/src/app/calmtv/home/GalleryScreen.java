package app.calmtv.home;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Bilderverwaltung: Raster aller Hintergrundbilder mit Filter (Alle, Favoriten, Eigene, Ausgeblendet). Die Auswahltaste
 * oder Menütaste öffnet das Menü zu einem Bild (als Hintergrund setzen, Favorit, ausblenden, Ausschnitt, Helligkeit, Details).
 * Vorschaubilder kommen klein und asynchron ({@link GalleryThumbs}); beim Schließen wird alles freigegeben.
 */
final class GalleryScreen implements Screen {
    private static final int COLUMNS = 4, LEFT_DP = 64, GAP_DP = 20, ROW_GAP_DP = 8;
    private static final String[] FILTERS = {"Alle", "Favoriten", "Eigene", "Ausgeblendet"};

    private final HomeHost host;
    private final Context ctx;
    private final Wallpapers wallpapers;
    private final GalleryRoot root;
    private final FrameLayout content;
    private final Chip[] chips = new Chip[FILTERS.length];
    private final TextView hint;
    private final GalleryScroll scroll;
    private final LinearLayout grid;
    private final GalleryThumbs thumbs;
    private final ImageView backdrop;
    private final List<GalleryTile> tiles = new ArrayList<>();
    private GalleryOverlay overlay;
    private String overlayReturnId;
    private int filter;
    private boolean closed;

    GalleryScreen(HomeHost host) {
        this.host = host;
        this.ctx = host.context();
        this.wallpapers = host.wallpapers();
        root = new GalleryRoot(ctx);
        root.setClickable(true);

        // Hintergrund wie in den Einstellungen: unscharfes Foto mit Abdunkelung
        backdrop = new ImageView(ctx);
        backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
        Bitmap blurred = host.blurredWallpaper();
        if (blurred != null) {
            backdrop.setImageBitmap(blurred);
            backdrop.setColorFilter(new PorterDuffColorFilter(Ui.SCRIM, PorterDuff.Mode.SRC_OVER));
        } else {
            backdrop.setBackgroundColor(Color.BLACK);
        }
        root.addView(backdrop, new FrameLayout.LayoutParams(-1, -1));

        content = new FrameLayout(ctx);
        content.setClipChildren(false);
        root.addView(content, new FrameLayout.LayoutParams(-1, -1));

        TextView title = Ui.label(ctx, "Bilder", 30, 300, Ui.TEXT);
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        tlp.setMargins(Ui.px(ctx, LEFT_DP), Ui.px(ctx, 34), 0, 0);
        content.addView(title, tlp);

        LinearLayout column = new LinearLayout(ctx);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setClipChildren(false);   // die Liste ragt um den Innenabstand nach links über die Spalte (Platz für den Fokusrahmen)
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(-1, -1);
        clp.setMargins(Ui.px(ctx, LEFT_DP), Ui.px(ctx, 84), Ui.px(ctx, LEFT_DP), 0);
        content.addView(column, clp);

        LinearLayout chipRow = new LinearLayout(ctx);
        chipRow.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < FILTERS.length; i++) {
            Chip chip = new Chip(ctx, FILTERS[i]);
            chip.setChosen(i == 0);
            final int index = i;
            chip.setOnClickListener(v -> setFilter(index));
            chips[i] = chip;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            if (i > 0) lp.leftMargin = Ui.px(ctx, 10);
            chipRow.addView(chip, lp);
        }
        column.addView(chipRow, new LinearLayout.LayoutParams(-2, -2));

        hint = Ui.label(ctx, "", Ui.sp(Ui.SP_BODY), 500, Ui.TEXT_DIM);
        hint.setVisibility(View.GONE);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(-1, -2);
        hlp.topMargin = Ui.px(ctx, 12);
        column.addView(hint, hlp);

        scroll = new GalleryScroll(ctx);
        scroll.setPadding(Ui.px(ctx, 14), Ui.px(ctx, 22), Ui.px(ctx, 14), Ui.px(ctx, 40));
        scroll.setClipToPadding(false);   // der Fokusrahmen der Randkacheln liegt im Innenabstand
        scroll.setOnScrolled(this::loadVisibleThumbs);
        grid = new LinearLayout(ctx);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setClipChildren(false);
        scroll.addView(grid, new FrameLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, 0, 1f);
        slp.leftMargin = -Ui.px(ctx, 14);   // der Innenabstand lässt Platz für Ring und Vergrößerung, die Kacheln bleiben bei x = 64 dp
        slp.topMargin = Ui.px(ctx, 4);
        column.addView(scroll, slp);

        int thumbW = Ui.px(ctx, GalleryTile.WIDTH_DP);
        thumbs = new GalleryThumbs(wallpapers, thumbW, Math.round(thumbW * 9f / 16f));
        rebuild(null, 0);
    }

    // ---- Screen ----------------------------------------------------------------------------------

    @Override public View view() {
        return root;
    }

    @Override public void onShown() {
        focusTile(tiles.isEmpty() ? null : tiles.get(0).entry().id, 0);
    }

    @Override public boolean onBack() {
        if (overlay == null) return false;
        closeOverlay(false);
        return true;
    }

    @Override public boolean onMenu() {
        if (overlay != null) return true;
        View focused = root.findFocus();
        if (focused instanceof GalleryTile) openMenu((GalleryTile) focused);
        return true;
    }

    @Override public void onClosed() {
        closed = true;
        if (overlay != null) {
            overlay.release();
            root.removeView(overlay);
            overlay = null;
        }
        thumbs.shutdown();
        backdrop.setImageDrawable(null);
        grid.removeAllViews();
        tiles.clear();
    }

    // ---- Raster ----------------------------------------------------------------------------------

    private void setFilter(int index) {
        if (index == filter) return;
        filter = index;
        for (int i = 0; i < chips.length; i++) chips[i].setChosen(i == filter);
        rebuild(null, 0);
        chips[filter].requestFocus();
    }

    private boolean matchesFilter(Wallpapers.Entry e) {
        switch (filter) {
            case 1: return e.favorite;
            case 2: return e.own;
            case 3: return e.hidden;
            default: return true;
        }
    }

    /** Baut das Raster neu; die Kacheln sind leicht (Zeichnen, keine Glass-Instanzen). */
    private void rebuild(String focusId, int fallbackIndex) {
        List<Wallpapers.Entry> all = wallpapers.list();
        boolean ownOn = Config.val(Opts.BG_OWN) != 0;
        boolean anyOwn = false;
        for (Wallpapers.Entry e : all) anyOwn |= e.own;
        updateHint(ownOn, anyOwn);

        grid.removeAllViews();
        tiles.clear();
        LinearLayout row = null;
        for (Wallpapers.Entry e : all) {
            if (!matchesFilter(e)) continue;
            if (tiles.size() % COLUMNS == 0) {
                row = Views.linear(ctx, LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-2, -2);
                rlp.bottomMargin = Ui.px(ctx, ROW_GAP_DP);
                grid.addView(row, rlp);
            }
            GalleryTile tile = new GalleryTile(ctx, e);
            tile.setOnClickListener(v -> openMenu((GalleryTile) v));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            if (tiles.size() % COLUMNS != 0) lp.leftMargin = Ui.px(ctx, GAP_DP);
            row.addView(tile, lp);
            tiles.add(tile);
            Bitmap cached = thumbs.cached(GalleryThumbs.key(e));
            if (cached != null) tile.setThumb(GalleryThumbs.key(e), cached);
        }
        if (tiles.isEmpty()) {
            TextView empty = Ui.label(ctx, emptyText(ownOn), Ui.sp(Ui.SP_BODY), 500, Ui.TEXT_DIM);
            empty.setPadding(0, Ui.px(ctx, 24), 0, 0);
            grid.addView(empty, new LinearLayout.LayoutParams(-1, -2));
        }
        if (focusId != null) focusTile(focusId, fallbackIndex);
        scroll.post(this::loadVisibleThumbs);
    }

    private String emptyText(boolean ownOn) {
        switch (filter) {
            case 1: return "Noch keine Favoriten. Öffne bei einem Bild das Menü und wähle Zum Favoriten machen.";
            case 2: return ownOn ? "Keine eigenen Bilder gefunden." : "Eigene Bilder sind aus.";
            case 3: return "Du hast kein Bild ausgeblendet.";
            default: return "Keine Bilder gefunden.";
        }
    }

    private void updateHint(boolean ownOn, boolean anyOwn) {
        if (!ownOn) {
            hint.setText("Eigene Bilder sind aus. Du kannst sie in den Einstellungen unter Hintergrund einschalten.");
            hint.setVisibility(View.VISIBLE);
        } else if (!anyOwn) {
            hint.setText("Lege JPG-Dateien in den Ordner 'wallpapers' im App-Ordner von TV Home (Android/data/"
                    + ctx.getPackageName() + "/files); mit 'adb push' oder einem Dateimanager.");
            hint.setVisibility(View.VISIBLE);
        } else {
            hint.setVisibility(View.GONE);
        }
    }

    /** Fokus auf die Kachel mit dieser Kennung, sonst auf die Kachel an der Stelle (begrenzt), sonst auf den Filter. */
    private void focusTile(String id, int fallbackIndex) {
        GalleryTile target = null;
        for (GalleryTile t : tiles) if (id != null && t.entry().id.equals(id)) target = t;
        if (target == null && !tiles.isEmpty()) target = tiles.get(Math.max(0, Math.min(tiles.size() - 1, fallbackIndex)));
        if (target != null) target.requestFocus(); else chips[filter].requestFocus();
    }

    private void loadVisibleThumbs() {
        if (closed) return;
        for (GalleryTile t : tiles) {
            if (!t.hasThumb() && t.isOnScreen()) thumbs.request(t.entry(), this::onThumb);
        }
    }

    private void onThumb(String key, Bitmap bitmap) {
        for (GalleryTile t : tiles) t.setThumb(key, bitmap);
    }

    // ---- Menü ------------------------------------------------------------------------------------

    private void openMenu(GalleryTile tile) {
        final Wallpapers.Entry e = tile.entry();
        final int index = tiles.indexOf(tile);
        List<GlassMenu.Item> items = new ArrayList<>();
        items.add(new GlassMenu.Item("Jetzt als Hintergrund", Icons.BILD, () -> setAsWallpaper(e)));
        items.add(new GlassMenu.Item(e.favorite ? "Aus Favoriten nehmen" : "Zum Favoriten machen", Icons.STERN, () -> {
            wallpapers.setFavorite(e.id, !e.favorite);
            rebuild(e.id, index);
        }));
        items.add(new GlassMenu.Item(e.hidden ? "Wieder einblenden" : "Ausblenden", Icons.AUSGEBLENDET, () -> {
            wallpapers.setHidden(e.id, !e.hidden);
            rebuild(e.id, index);
        }));
        items.add(new GlassMenu.Item("Ausschnitt wählen…", Icons.TV, () -> openCrop(e)));
        items.add(new GlassMenu.Item("Helligkeit anpassen…", Icons.SONNE, () -> openTone(e)));
        items.add(new GlassMenu.Item("Details", Icons.INFO, () -> showDetails(e)));
        GlassMenu.show(host.modalLayer(), e.title, items, null);
    }

    private void setAsWallpaper(Wallpapers.Entry e) {
        wallpapers.pin(e.id);
        host.nextWallpaper();
        host.toast("Hintergrund gesetzt");
    }

    /** Ändert sich das Bild, das gerade Hintergrund ist, wird es neu geladen. */
    private void applyIfCurrent(String id) {
        if (!id.equals(wallpapers.currentId())) return;
        wallpapers.pin(id);
        host.nextWallpaper();
    }

    private void showDetails(Wallpapers.Entry e) {
        List<GlassMenu.Item> items = new ArrayList<>();
        Runnable close = () -> { };
        if (e.own) {
            items.add(detail("Datei", e.title, close));
            items.add(detail("Herkunft", "Eigenes Bild aus deinem Ordner", close));
        } else {
            String[] parts = e.credit.split(" · ");
            String author = "", license = "";
            for (String p : parts) {
                if (p.startsWith("Photo: ")) author = p.substring(7);
            }
            if (parts.length >= 3) license = parts[parts.length - 1];
            items.add(detail("Ort", e.title, close));
            items.add(detail("Urheber", author.isEmpty() ? "Nicht angegeben" : author, close));
            items.add(detail("Lizenz", license.isEmpty() ? "Nicht angegeben" : license, close));
        }
        GlassMenu.show(host.modalLayer(), "Details", items, null);
    }

    private static GlassMenu.Item detail(String label, String value, Runnable close) {
        return new GlassMenu.Item(label, value, Icons.NONE, false, close);
    }

    // ---- Unteransichten --------------------------------------------------------------------------

    private void openCrop(Wallpapers.Entry e) {
        showOverlay(new GalleryCropView(ctx, wallpapers, e, Metrics.screenAspect(ctx), this::onOverlayDone), e);
    }

    private void openTone(Wallpapers.Entry e) {
        showOverlay(new GalleryToneView(ctx, wallpapers, e, this::onOverlayDone), e);
    }

    private void showOverlay(GalleryOverlay view, Wallpapers.Entry e) {
        overlayReturnId = e.id;
        overlay = view;
        content.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        root.addView(view, new FrameLayout.LayoutParams(-1, -1));
        view.loadImage(wallpapers, thumbs);
        view.requestFocus();
    }

    private void onOverlayDone(boolean saved) {
        closeOverlay(saved);
    }

    private void closeOverlay(boolean saved) {
        GalleryOverlay view = overlay;
        if (view == null) return;
        overlay = null;
        view.release();
        root.removeView(view);
        content.setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);
        String id = overlayReturnId;
        int index = 0;
        for (int i = 0; i < tiles.size(); i++) if (tiles.get(i).entry().id.equals(id)) index = i;
        if (saved) {
            applyIfCurrent(id);
            rebuild(id, index);   // neue Vorschau mit dem gespeicherten Ausschnitt oder der Helligkeit
        } else {
            focusTile(id, index);
        }
    }
}
