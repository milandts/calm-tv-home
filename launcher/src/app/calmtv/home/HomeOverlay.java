package app.calmtv.home;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Die Ebene über der Startseite für Suche und geöffnete Ordner: unscharfes Foto mit Abdunklung, Überschrift und ein Raster aus
 * App-Kacheln. Die Suche filtert Apps und Einstellungen beim Tippen und schlägt eine Websuche vor; ein Ordner zeigt seine Apps.
 * Es gibt höchstens eine Ebene; sie sperrt solange den Fokus für Dock, Raster und Leiste.
 */
final class HomeOverlay implements HomeBackdrop.BlurUser {
    private static final String SEARCH_URL = "https://www.bing.com/search?q=";
    private static final int COLUMNS = 5, TILE_WIDTH_DP = 148, SPACING_DP = 30, LEFT_DP = 64;
    private static final long KEYBOARD_DELAY_MS = 250;

    private final HomeActivity home;
    private final HomeScene scene;
    private final AppCatalog catalog;
    private final AppLayout layout;
    private FrameLayout layer;
    private ImageView background;
    private Glass searchBox;
    private boolean isSearch;
    private String folderName;
    private int returnIndex = -1;   // Rasterkachel, von der aus ein Ordner geöffnet wurde
    private List<SettingsSearch.Entry> settingsIndex;

    HomeOverlay(HomeActivity home, HomeScene scene, AppCatalog catalog, AppLayout layout) {
        this.home = home;
        this.scene = scene;
        this.catalog = catalog;
        this.layout = layout;
    }

    boolean isOpen() {
        return layer != null;
    }

    boolean isFolderOpen() {
        return layer != null && !isSearch;
    }

    String folderName() {
        return folderName;
    }

    @Override public void onBackdrop(Bitmap blurred) {
        if (background != null) background.setImageBitmap(blurred);
    }

    void openSearch() {
        isSearch = true;
        open();
    }

    /** Öffnet einen Ordner über dem App-Raster und merkt sich die Kachel, zu der man zurückkehrt. */
    void openFolder(String folder) {
        returnIndex = home.grid().focusedIndex();
        folderName = folder;
        isSearch = false;
        open();
    }

    /** Baut den offenen Ordner nach einer Änderung neu auf; ist er leer geworden, schließt er. */
    void refreshFolder() {
        if (!isFolderOpen()) return;
        if (home.installed(layout.folderApps(folderName)).isEmpty()) close(); else open();
    }

    // ---- Öffnen und Schließen --------------------------------------------------------------------

    private void open() {
        if (layer != null) scene.root.removeView(layer);
        layer = new FrameLayout(home);
        layer.setClickable(true);
        layer.setClipChildren(false);   // fokussierte Kacheln am Rand wachsen über das Raster hinaus

        background = scene.photoView();
        Bitmap blurred = home.backdrop().blurred();
        if (blurred != null) background.setImageBitmap(blurred);
        home.backdrop().removeBlurUser(this);   // ein neu aufgebauter Ordner ersetzt die alte Ebene
        home.backdrop().addBlurUser(this);
        layer.addView(background, HomeScene.fill());
        View tint = new View(home);
        tint.setBackgroundColor(0x8C000000);
        layer.addView(tint, HomeScene.fill());

        TextView heading = Ui.label(home, isSearch ? "Suche" : folderName, 30, 300, Color.WHITE);
        FrameLayout.LayoutParams headingParams = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        headingParams.setMargins(scene.px(LEFT_DP), scene.px(34), 0, 0);
        layer.addView(heading, headingParams);

        LinearLayout rows = Views.linear(home, LinearLayout.VERTICAL);
        ScrollView scroll = Views.scroller(home);
        scroll.setPadding(0, scene.px(22), 0, scene.px(56));
        scroll.addView(rows);
        FrameLayout.LayoutParams scrollParams = new FrameLayout.LayoutParams(-2, -1, Gravity.START);
        scrollParams.leftMargin = scene.px(LEFT_DP);   // dieselbe linke Kante wie die Überschrift
        scrollParams.topMargin = scene.px(isSearch ? 150 : 96);

        View first;
        if (isSearch) {
            first = addSearchField(rows);
        } else {
            List<Tile> tiles = folderTiles();
            fill(rows, tiles);
            first = tiles.isEmpty() ? null : tiles.get(0).card;
        }
        layer.addView(scroll, scrollParams);

        scene.dock.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        scene.grid.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);   // das Raster hinter einem Ordner darf keinen Fokus bekommen
        home.bar().setFocusable(false);
        scene.title.animate().alpha(0f).setDuration(Ui.ms(100)).start();
        scene.root.addView(layer, HomeScene.fill());
        layer.setAlpha(0f);
        layer.animate().alpha(1f).setDuration(Ui.ms(160)).start();
        if (first != null) first.requestFocus();
    }

    void close() {
        if (layer == null) return;
        final FrameLayout closing = layer;
        boolean wasFolder = !isSearch;
        layer = null;
        background = null;
        home.backdrop().removeBlurUser(this);
        if (searchBox != null) {
            scene.glass.remove(searchBox);
            searchBox = null;
        }
        home.hideKeyboard(closing);
        closing.animate().alpha(0f).setDuration(Ui.ms(140)).withEndAction(() -> scene.root.removeView(closing)).start();
        scene.dock.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        scene.grid.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        home.bar().setFocusable(!home.grid().isOpen());   // bei offenem Raster bleiben die Tasten aus
        if (wasFolder) {
            if (returnIndex >= 0 && home.grid().isOpen()) home.grid().focus(returnIndex); else home.dock().focus();
        } else if (home.geometry().isPillVisible(scene.searchPill)) {
            scene.searchPill.requestFocus();
        } else {
            home.dock().focus();
        }
    }

    // ---- Kacheln ---------------------------------------------------------------------------------

    private List<Tile> folderTiles() {
        List<AppEntry> members = new ArrayList<>();
        for (String pkg : home.installed(layout.folderApps(folderName))) members.add(catalog.get(pkg));
        return appTiles(members);
    }

    private List<Tile> appTiles(Iterable<AppEntry> apps) {
        List<Tile> tiles = new ArrayList<>();
        int width = scene.px(TILE_WIDTH_DP), height = width * 9 / 16;
        for (final AppEntry app : apps) {
            Tile tile = Tile.art(home, width, height, catalog.art(app.pkg), home.nameOf(app), true, 1.1f);
            tile.card.setOnClickListener(v -> {
                home.launch(app);
                close();
            });
            tile.card.setOnLongClickListener(v -> {
                home.menus().app(app);
                return true;
            });
            tile.card.setOnKeyListener(AppMenus.menuKey(() -> home.menus().app(app)));
            tiles.add(tile);
        }
        return tiles;
    }

    /** Legt die Kacheln in Zeilen zu je fünf. */
    private void fill(LinearLayout rows, List<Tile> tiles) {
        rows.removeAllViews();
        LinearLayout row = null;
        for (int i = 0; i < tiles.size(); i++) {
            if (i % COLUMNS == 0) {
                row = Views.linear(home, LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-2, -2);
                rowParams.bottomMargin = scene.px(22);
                rows.addView(row, rowParams);
            }
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
            if (i % COLUMNS > 0) params.leftMargin = scene.px(SPACING_DP);
            row.addView(tiles.get(i), params);
        }
    }

    // ---- Suche -----------------------------------------------------------------------------------

    /** Das Glas-Suchfeld: Tippen filtert Apps und Einstellungen, die Suchtaste der Tastatur fragt das Web (Silk). */
    private EditText addSearchField(final LinearLayout rows) {
        Glass box = new Glass(home, scene.px(28));
        searchBox = box;
        scene.glass.add(box);
        Bitmap blurred = home.backdrop().blurred();
        if (blurred != null) box.setBlur(blurred, 0);

        LinearLayout line = new LinearLayout(home);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.setPadding(scene.px(20), 0, scene.px(20), 0);
        ImageView icon = new ImageView(home);
        icon.setImageDrawable(Icons.icon(Icons.SUCHE, 0xCCFFFFFF));
        line.addView(icon, new LinearLayout.LayoutParams(scene.px(18), scene.px(18)));

        final EditText field = new EditText(home);
        field.setHint("Apps durchsuchen oder im Web mit Silk suchen");
        field.setTypeface(Ui.font(home, 600));
        field.setTextSize(18);
        field.setSingleLine();
        field.setBackground(null);
        field.setHintTextColor(0x99FFFFFF);
        field.setTextColor(Color.WHITE);
        field.setImeOptions(EditorInfo.IME_ACTION_SEARCH | EditorInfo.IME_FLAG_NO_EXTRACT_UI | EditorInfo.IME_FLAG_NO_FULLSCREEN);
        LinearLayout.LayoutParams fieldParams = new LinearLayout.LayoutParams(0, -2, 1f);
        fieldParams.leftMargin = scene.px(12);
        line.addView(field, fieldParams);
        box.addView(line, new FrameLayout.LayoutParams(-1, -1));

        FrameLayout.LayoutParams boxParams = new FrameLayout.LayoutParams(scene.px(620), scene.px(56), Gravity.TOP | Gravity.START);
        boxParams.topMargin = scene.px(84);
        boxParams.leftMargin = scene.px(LEFT_DP);   // Titel, Feld und Ergebnisse teilen eine linke Kante
        layer.addView(box, boxParams);

        field.addTextChangedListener(new Typing(this, rows));
        field.setOnEditorActionListener((v, actionId, event) -> {
            boolean isEnter = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN;
            if (actionId != EditorInfo.IME_ACTION_SEARCH && !isEnter) return false;
            searchWeb(field.getText().toString().trim());
            return true;
        });
        home.post(KEYBOARD_DELAY_MS, () -> {
            InputMethodManager keyboard = (InputMethodManager) home.getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
            if (keyboard != null && field.hasFocus()) keyboard.showSoftInput(field, InputMethodManager.SHOW_IMPLICIT);
        });
        showResults(rows, "");
        return field;
    }

    /** Zeigt bei jeder Eingabe die passenden Ergebnisse (benannte Klasse: D8 dieser Build-Kette verträgt hier keine anonyme). */
    private static final class Typing implements TextWatcher {
        private final HomeOverlay overlay;
        private final LinearLayout rows;

        Typing(HomeOverlay overlay, LinearLayout rows) {
            this.overlay = overlay;
            this.rows = rows;
        }

        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

        @Override public void afterTextChanged(Editable s) {
            overlay.showResults(rows, s.toString().trim());
        }
    }

    private void showResults(LinearLayout rows, final String query) {
        List<Tile> tiles = new ArrayList<>();
        int width = scene.px(TILE_WIDTH_DP), height = width * 9 / 16;
        if (!query.isEmpty()) {
            Tile web = Tile.text(home, width, height, "Im Web suchen: „" + query + "“");
            web.card.setOnClickListener(v -> searchWeb(query));
            tiles.add(web);
            if (settingsIndex == null) settingsIndex = SettingsSearch.index(SettingsPages.build(home));
            for (final SettingsSearch.Entry hit : SettingsSearch.match(settingsIndex, query)) {
                Tile entry = Tile.text(home, width, height, hit.title + "\n" + hit.breadcrumb.split(" › ")[0]);
                entry.card.setOnClickListener(v -> {
                    close();
                    home.openSettings(hit.pageId);
                });
                tiles.add(entry);
            }
        }
        List<AppEntry> matches = new ArrayList<>();
        String needle = query.toLowerCase(Locale.getDefault());
        for (AppEntry app : catalog.all()) {
            if (needle.isEmpty() || home.nameOf(app).toLowerCase(Locale.getDefault()).contains(needle)) matches.add(app);
        }
        tiles.addAll(appTiles(matches));
        fill(rows, tiles);
    }

    /**
     * Öffnet Bing im Browser Silk (sonst in irgendeinem). Silk ignoriert Websuche-Intents und öffnet nur den letzten Tab,
     * und Google antwortet Silk gern mit einer Roboter-Prüfung.
     */
    private void searchWeb(String query) {
        if (query.isEmpty()) return;
        if (home.openUrl(SEARCH_URL + Uri.encode(query))) close();
    }
}
