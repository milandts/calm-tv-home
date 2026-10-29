package app.calmtv.home;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.GradientDrawable;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Die Einstellungen als Vollbild-Ansicht: links die Kategorien (der Fokus wählt sofort), rechts das Panel mit der
 * Seite. Unterseiten liegen auf einem Stapel; Vorschau-Seiten blenden die eigene Oberfläche aus und zeigen nur eine
 * kompakte Leiste über der echten Startseite. Gezeichnet wird nur, was {@link SettingsModel} beschreibt.
 */
final class SettingsScreen implements Screen {
    private static final String TAG = HomeKeyService.TAG;

    private static final int ANIM_NONE = 0, ANIM_FORWARD = 1, ANIM_BACKWARD = 2, ANIM_CATEGORY = 3;
    /** Fokuswünsche nach dem Anzeigen einer Seite; Werte ab 0 sind eine Zeilennummer. */
    private static final int FOCUS_KEEP = -1, FOCUS_FIRST = -2;

    /** Breite der Seitenleiste: wächst mit der Schriftgröße (höchstens auf 340 dp), damit "Verbindungen & Geräte" nicht mit "…" endet. */
    private float sidebarDp() {
        return Math.min(340f, SIDEBAR_DP * Math.max(1f, Ui.sp(1f)));
    }

    private static final int LEFT_DP = 64, SIDEBAR_DP = 260, GAP_DP = 28, TOP_DP = 110, BOTTOM_DP = 40, SLIDE_DP = 40;
    private static final int PREVIEW_WIDTH_DP = 780;
    /** Platz für die Vorschau-Leiste zwischen Uhr und Dock: y 172 bis 400 dp, höchstens 45 % der Höhe. */
    private static final int PREVIEW_TOP_DP = 172, PREVIEW_SLOT_DP = 228;
    private static final float PREVIEW_MAX_HEIGHT = 0.45f;

    /** Eine geöffnete Seite und die Zeile der Elternseite, von der man kam. */
    private static final class Level {
        final SettingsModel.Page page;
        final int returnRow;

        Level(SettingsModel.Page page, int returnRow) {
            this.page = page;
            this.returnRow = returnRow;
        }
    }

    private final HomeHost host;
    private final Context ctx;
    private final List<SettingsModel.Page> categories;
    private final ArrayList<Level> stack = new ArrayList<>();
    private final ArrayList<SettingsRowView> focusables = new ArrayList<>();
    private final FrameLayout root;
    private final FrameLayout backdrop;
    private final ImageView wallpaper;
    private final TextView titleView;
    private final SettingsSidebar sidebar;
    private final FrameLayout panel;
    private final GradientDrawable panelBackground;
    private final SettingsPanelSizer panelSizer;
    private final SettingsRowView.Actions rowActions;
    private final SettingsPreview.Actions previewActions;
    private final Runnable rebuildTask;

    private int category;
    private SettingsContent content;
    private SettingsModel.Page contentPage;
    private SettingsPreview preview;
    private boolean previewActive, shown, closed, pendingContentFocus;
    int lastRowIndex;
    final SettingsOptions options;

    SettingsScreen(HomeHost host, List<SettingsModel.Page> categories) {
        this.host = host;
        this.ctx = host.context();
        this.categories = new ArrayList<>(categories);
        this.rowActions = new SettingsActions.Rows(this);
        this.previewActions = new SettingsActions.Preview(this);
        this.rebuildTask = this::rebuildAfterChange;
        this.options = new SettingsOptions(host, this::rebuildAfterChange);

        root = new SettingsRoot(ctx, this);
        root.setClickable(true);

        // Hintergrund: unscharfes Foto (oder Schwarz) mit Abdunkelung
        // Die Abdunkelung ist ein Farbfilter auf dem Bild statt einer eigenen Fläche (eine Vollbild-Überzeichnung weniger).
        backdrop = new FrameLayout(ctx);
        wallpaper = new ImageView(ctx);
        wallpaper.setScaleType(ImageView.ScaleType.CENTER_CROP);
        Bitmap blurred = host.blurredWallpaper();
        if (blurred != null) {
            wallpaper.setImageBitmap(blurred);
            wallpaper.setColorFilter(new PorterDuffColorFilter(Ui.SCRIM, PorterDuff.Mode.SRC_OVER));
        } else {
            backdrop.setBackgroundColor(Color.BLACK);
        }
        backdrop.addView(wallpaper, new FrameLayout.LayoutParams(-1, -1));
        root.addView(backdrop, new FrameLayout.LayoutParams(-1, -1));

        titleView = Ui.label(ctx, "Einstellungen", Ui.sp(30), 300, Ui.TEXT);
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        tlp.setMargins(Ui.px(ctx, LEFT_DP), Ui.px(ctx, 34), 0, 0);
        root.addView(titleView, tlp);

        sidebar = new SettingsSidebar(ctx, this.categories, new SettingsActions.Sidebar(this));
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(Ui.px(ctx, sidebarDp()), -1);
        slp.setMargins(Ui.px(ctx, LEFT_DP), Ui.px(ctx, TOP_DP), 0, Ui.px(ctx, BOTTOM_DP));
        root.addView(sidebar, slp);

        panel = new FrameLayout(ctx);
        panelBackground = Ui.round(ctx, Ui.panel(Ui.PANEL), Ui.RADIUS_PANEL, Ui.HAIRLINE, hairlinePx());
        panel.setBackground(panelBackground);
        panel.setClipToOutline(true);   // Inhalt und Ränder folgen der Rundung des Panels
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(-1, -1);
        plp.setMargins(Ui.px(ctx, LEFT_DP + sidebarDp() + GAP_DP), Ui.px(ctx, TOP_DP), Ui.px(ctx, LEFT_DP), Ui.px(ctx, BOTTOM_DP));
        root.addView(panel, plp);
        panelSizer = new SettingsPanelSizer(panel, ctx, Ui.px(ctx, TOP_DP + BOTTOM_DP));
        root.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (b - t != ob - ot) panelSizer.clamp();   // Sicherheitsrand geändert: Panel passt sich an
        });

        if (!this.categories.isEmpty()) {
            SettingsModel.Page first = this.categories.get(0);
            stack.add(new Level(first, -1));
            sidebar.setSelected(0);
            if (first.onOpen != null) first.onOpen.run();
            show(ANIM_NONE, FOCUS_KEEP);
        }
    }

    // ---- Screen ----------------------------------------------------------------------------------

    @Override public View view() {
        return root;
    }

    @Override public void onShown() {
        shown = true;
        if (previewActive && preview != null) {
            preview.focusInitial();
        } else if (pendingContentFocus && !focusables.isEmpty()) {
            focusRowNear(FOCUS_FIRST);
        } else {
            sidebar.focusIndex(category);
        }
        pendingContentFocus = false;
        // Sanftes Einlaufen der beiden Spalten
        sidebar.setTranslationX(-Ui.px(ctx, 16));
        sidebar.animate().translationX(0).setDuration(Ui.ms(Ui.MEDIUM)).setInterpolator(Ui.EASE).start();
        panel.setTranslationX(Ui.px(ctx, 24));
        panel.animate().translationX(0).setDuration(Ui.ms(Ui.MEDIUM)).setInterpolator(Ui.EASE).start();
    }

    @Override public boolean onBack() {
        if (stack.size() > 1) {
            options.leave(this::popPage, null);   // fragt nur nach, wenn es Probeänderungen gibt
            return true;
        }
        if (previewActive) return false;
        if (sidebar.indexOf(root.findFocus()) < 0 && !categories.isEmpty()) {
            options.leave(() -> sidebar.focusIndex(category), null);
            return true;
        }
        return false;
    }

    /** Menütaste auf einer Einstellung (Zeile oder Vorschau-Leiste): zurück auf den Standard. */
    @Override public boolean onMenu() {
        View focused = root.findFocus();
        if (previewActive && preview != null) return preview.resetFocused(focused);
        if (!(focused instanceof SettingsRowView) || ((SettingsRowView) focused).optRow() == null) return false;
        options.reset(((SettingsRowView) focused).optRow());
        return true;
    }

    /** Liest die Seite nach kurzer Pause neu (gleiche Aufrufe innerhalb der Pause zählen einmal). */
    void scheduleRebuild(long delayMs) {
        root.removeCallbacks(rebuildTask);
        root.postDelayed(rebuildTask, delayMs);
    }

    @Override public void onClosed() {
        options.commitSilently();   // HOME: Probeänderungen werden ohne Dialog gespeichert
        closed = true;
        root.removeCallbacks(rebuildTask);
        if (previewActive) {
            previewActive = false;
            host.setPreviewMode(false);
        }
        wallpaper.setImageDrawable(null);
        panel.removeAllViews();
        focusables.clear();
        content = null;
        preview = null;
    }

    /** Tiefenlink (Einstellungs-Suche): öffnet die Seite mit dieser Kennung samt Kategorie und Unterseiten. */
    void openPath(String pageId) {
        if (pageId == null || closed) return;
        options.commitSilently();
        ArrayList<SettingsModel.Page> trail = SettingsPaths.find(categories, pageId);
        if (trail == null) return;
        category = categories.indexOf(trail.get(0));
        sidebar.setSelected(category);
        stack.clear();
        SettingsModel.Page parent = null;
        for (SettingsModel.Page page : trail) {
            stack.add(new Level(page, parent == null ? -1 : SettingsPaths.rowIndexOfLink(parent, page)));
            if (page.onOpen != null) page.onOpen.run();
            parent = page;
        }
        pendingContentFocus = !shown;
        show(ANIM_NONE, shown ? FOCUS_FIRST : FOCUS_KEEP);
    }

    private int hairlinePx() {
        return Math.max(1, Ui.px(ctx, 1));
    }

    // ---- Seitenstapel ----------------------------------------------------------------------------

    private SettingsModel.Page topPage() {
        return stack.get(stack.size() - 1).page;
    }

    /** Zeigt die oberste Seite des Stapels (Zeilen oder Vorschau). */
    private void show(int anim, int focusRow) {
        SettingsModel.Page top = topPage();
        options.previewPage = top.preview;
        if (top.preview) {
            SettingsModel.Page base = nearestPlainPage();
            if (base != null && (content == null || contentPage != base)) setContent(base, ANIM_NONE);
            openPreview(top);
            return;
        }
        boolean leavingPreview = previewActive;
        if (leavingPreview) closePreview();
        if (content == null || contentPage != top) {
            setContent(top, leavingPreview ? ANIM_NONE : anim);
        } else {
            fillRows(top);
        }
        applyFocus(focusRow);
    }

    private SettingsModel.Page nearestPlainPage() {
        for (int i = stack.size() - 1; i >= 0; i--) if (!stack.get(i).page.preview) return stack.get(i).page;
        return null;
    }

    void selectCategory(final int index) {
        if (index == category) return;   // Fokus kehrt auf die Kategorie zurück, die offene Unterseite bleibt stehen
        options.leave(() -> openCategory(index), () -> sidebar.focusIndex(category));
    }

    private void openCategory(int index) {
        category = index;
        stack.clear();
        SettingsModel.Page page = categories.get(index);
        stack.add(new Level(page, -1));
        sidebar.setSelected(index);
        if (page.onOpen != null) page.onOpen.run();
        show(ANIM_CATEGORY, FOCUS_KEEP);
    }

    void pushPage(final SettingsModel.Page target, final int fromRow) {
        options.leave(() -> openSubpage(target, fromRow), null);
    }

    private void openSubpage(SettingsModel.Page target, int fromRow) {
        if (target.onOpen != null) target.onOpen.run();
        stack.add(new Level(target, fromRow));
        show(ANIM_FORWARD, FOCUS_FIRST);
    }

    private void popPage() {
        Level closing = stack.remove(stack.size() - 1);
        show(ANIM_BACKWARD, closing.returnRow);
    }

    /** Baut den Inhalt der Seite neu auf und blendet ihn ein; der alte Inhalt verschwindet. */
    private void setContent(SettingsModel.Page page, int anim) {
        SettingsContent next = new SettingsContent(ctx, page, crumbFor(page));
        panelSizer.adapt(next);
        panelBackground.setStroke(hairlinePx(), Ui.HAIRLINE);
        next.scroll.setOnFocusChangeListener((v, has) -> panelBackground.setStroke(hairlinePx(),
                has ? (Look.focusRing() & 0x00FFFFFF) | 0x99000000 : Ui.HAIRLINE));   // Hinweis: die Liste selbst hat den Fokus
        contentPage = page;
        SettingsContent old = content;
        content = next;
        fillRows(page);
        panel.addView(next, new FrameLayout.LayoutParams(-1, -1));

        float slide = Ui.px(ctx, SLIDE_DP);
        if (anim == ANIM_FORWARD || anim == ANIM_BACKWARD) {
            next.setAlpha(0f);
            next.setTranslationX(anim == ANIM_FORWARD ? slide : -slide * 0.6f);
            next.animate().alpha(1f).translationX(0).setDuration(Ui.ms(Ui.MEDIUM)).setInterpolator(Ui.EASE).start();
        } else if (anim == ANIM_CATEGORY) {
            next.setAlpha(0f);
            next.animate().alpha(1f).setDuration(Ui.ms(Ui.FAST)).start();
        }
        if (old != null) {
            old.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
            panel.removeView(old);   // nur eine Fläche animiert: spart Überzeichnung auf dem schwachen Stick
        }
    }

    /** Pfad der Eltern ("Darstellung › Hintergrund") oder null bei einer Kategorie-Seite. */
    private String crumbFor(SettingsModel.Page page) {
        int depth = -1;
        for (int i = stack.size() - 1; i >= 0; i--) {
            if (stack.get(i).page == page) {
                depth = i;
                break;
            }
        }
        if (depth <= 0) return null;
        StringBuilder trail = new StringBuilder();
        for (int i = 0; i < depth; i++) {
            if (i > 0) trail.append("  ›  ");
            trail.append(stack.get(i).page.title);
        }
        return trail.toString();
    }

    // ---- Zeilen ----------------------------------------------------------------------------------

    /** Füllt die Zeilenliste frisch (dynamicRows werden neu gelesen); Scrollposition und Seitenkopf bleiben. */
    private void fillRows(SettingsModel.Page page) {
        if (content == null) return;
        ViewGroup box = content.rows;
        box.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);   // kein automatischer Fokus beim Einfügen
        box.removeAllViews();
        focusables.clear();
        List<SettingsModel.Row> rows = page.currentRows();
        if (rows == null || rows.isEmpty()) {
            box.addView(SettingsRowView.emptyHint(ctx, "Keine Einträge."), new ViewGroup.LayoutParams(-1, -2));
        } else {
            for (int i = 0; i < rows.size(); i++) {
                boolean separated = rows.get(i) instanceof SettingsModel.Info && i + 1 < rows.size()
                        && rows.get(i + 1) instanceof SettingsModel.Info;
                View view = SettingsRowView.create(ctx, rows.get(i), i, rowActions, separated);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
                lp.bottomMargin = Ui.px(ctx, SettingsRowView.GAP_DP);
                box.addView(view, lp);
                if (view instanceof SettingsRowView) focusables.add((SettingsRowView) view);
            }
        }
        box.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        content.scroll.setFocusable(focusables.isEmpty());
    }

    /** Nach einer Änderung durch eine Zeile: Seite neu lesen und den Fokus auf der Zeile lassen. */
    void rebuildAfterChange() {
        if (closed) return;
        if (previewActive) {
            refreshPreview();
            return;
        }
        if (content == null) return;
        View focused = root.findFocus();
        boolean restore = focused instanceof SettingsRowView || (focused == null && !windowHasFocus());
        fillRows(contentPage);
        if (restore) focusRowNear(lastRowIndex);
        root.post(this::ensureFocus);
    }

    private void refreshPreview() {
        if (preview == null) return;
        if (topPage().dynamicRows == null) {
            preview.refresh();
            return;
        }
        int code = preview.focusCode();
        openPreview(topPage());
        preview.restoreFocus(code);
    }

    /** Falls durch einen Dialog oder Neuaufbau der Fokus verloren ging, geht er zurück in die Seite. */
    private void ensureFocus() {
        if (closed || windowHasFocus()) return;
        if (previewActive && preview != null) preview.focusInitial();
        else if (!focusables.isEmpty()) focusRowNear(lastRowIndex);
        else sidebar.focusIndex(category);
    }

    private boolean windowHasFocus() {
        return root.getRootView().findFocus() != null;
    }

    private void applyFocus(int focusRow) {
        if (focusRow == FOCUS_KEEP) return;
        if (focusables.isEmpty()) {
            if (content != null && content.scroll.isFocusable()) content.scroll.requestFocus();
            else sidebar.focusIndex(category);
            return;
        }
        focusRowNear(focusRow);
    }

    /** Fokus auf die Zeile mit dieser Nummer, sonst die nächste fokussierbare danach, sonst die letzte. */
    private void focusRowNear(int rowIndex) {
        if (focusables.isEmpty()) return;
        SettingsRowView target = focusables.get(0);
        if (rowIndex >= 0) {
            target = focusables.get(focusables.size() - 1);
            for (SettingsRowView row : focusables) {
                if (row.index() >= rowIndex) {
                    target = row;
                    break;
                }
            }
        }
        target.requestFocus();
    }

    void focusContentFirst() {
        if (previewActive && preview != null) preview.focusInitial();
        else if (!focusables.isEmpty()) {
            focusables.get(0).requestFocus();
            content.reveal(focusables.get(0), true, focusables.size() == 1, true);
        } else if (content != null && content.scroll.isFocusable()) {
            content.scroll.requestFocus();   // Seite nur mit Text: die Liste selbst hat den Fokus
        }
    }

    // ---- Tasten ----------------------------------------------------------------------------------

    /** Alle Pfeiltasten laufen hier durch; nichts davon darf die Ansicht verlassen. */
    void onArrow(int key, int repeat) {
        View focused = root.findFocus();
        if (previewActive && preview != null) {
            preview.onArrow(key, focused, repeat);
            return;
        }
        if (focused == null) {
            ensureFocus();
            return;
        }
        int sideIndex = sidebar.indexOf(focused);
        if (sideIndex >= 0) {
            if (key == KeyEvent.KEYCODE_DPAD_UP) sidebar.focusIndex(sideIndex - 1);
            else if (key == KeyEvent.KEYCODE_DPAD_DOWN) sidebar.focusIndex(sideIndex + 1);
            else if (key == KeyEvent.KEYCODE_DPAD_RIGHT) focusContentFirst();
            return;
        }
        if (content != null && focused == content.scroll) {
            if (key == KeyEvent.KEYCODE_DPAD_LEFT) sidebar.focusIndex(category);
            else if (key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_DOWN) {
                content.pageScroll(key == KeyEvent.KEYCODE_DPAD_UP ? -1 : 1, repeat > 0);
            }
            return;
        }
        if (!(focused instanceof SettingsRowView)) return;
        SettingsRowView row = (SettingsRowView) focused;
        if (key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_DOWN) {
            moveInContent(row, key == KeyEvent.KEYCODE_DPAD_UP ? -1 : 1, repeat > 0);
        } else if (key == KeyEvent.KEYCODE_DPAD_LEFT) {
            if (!row.stepHorizontal(-1, repeat)) sidebar.focusIndex(category);
        } else {
            row.stepHorizontal(1, repeat);
        }
    }

    private void moveInContent(SettingsRowView from, int direction, boolean fast) {
        int next = focusables.indexOf(from) + direction;
        if (next < 0 || next >= focusables.size()) {
            // Keine weitere Zeile: Text darunter oder darüber (Notizen, Infos) trotzdem erreichbar scrollen
            content.pageScroll(direction, fast);
            return;
        }
        SettingsRowView target = focusables.get(next);
        target.requestFocus();
        content.reveal(target, next == 0, next == focusables.size() - 1, fast);
    }

    // ---- Vorschau --------------------------------------------------------------------------------

    private void openPreview(SettingsModel.Page page) {
        if (preview != null) root.removeView(preview);
        DisplayMetrics metrics = ctx.getResources().getDisplayMetrics();
        int width = Math.min(Ui.px(ctx, PREVIEW_WIDTH_DP), Ui.usableWidth(ctx) - Ui.px(ctx, 2 * LEFT_DP));
        preview = new SettingsPreview(ctx, page, previewActions);
        int slot = Math.min(Math.round(Ui.usableHeight(ctx) * PREVIEW_MAX_HEIGHT), Ui.px(ctx, PREVIEW_SLOT_DP));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(width, slot, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        lp.topMargin = Ui.px(ctx, PREVIEW_TOP_DP);
        root.addView(preview, lp);
        if (!previewActive) {
            preview.setAlpha(0f);
            preview.setTranslationY(Ui.px(ctx, 12));
            preview.animate().alpha(1f).translationY(0).setDuration(Ui.ms(Ui.MEDIUM)).setInterpolator(Ui.EASE).start();
            previewActive = true;
            host.setPreviewMode(true);
            fade(backdrop, false);
            fade(titleView, false);
            fade(sidebar, false);
            fade(panel, false);
        }
        preview.focusInitial();
    }

    private void closePreview() {
        previewActive = false;
        host.setPreviewMode(false);
        final SettingsPreview leaving = preview;
        preview = null;
        if (leaving != null) {
            leaving.setFocusable(false);
            leaving.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
            leaving.animate().alpha(0f).setDuration(Ui.ms(Ui.FAST)).withEndAction(() -> root.removeView(leaving)).start();
        }
        fade(backdrop, true);
        fade(titleView, true);
        fade(sidebar, true);
        fade(panel, true);
    }

    /** Blendet eine Ansicht sanft ein oder aus; ausgeblendet ist sie unsichtbar und nimmt keinen Fokus an. */
    private void fade(final View view, final boolean visible) {
        view.animate().cancel();
        if (visible) {
            view.setVisibility(View.VISIBLE);
            view.animate().alpha(1f).setDuration(Ui.ms(Ui.MEDIUM)).setInterpolator(Ui.EASE).start();
        } else {
            view.animate().alpha(0f).setDuration(Ui.ms(Ui.MEDIUM)).setInterpolator(Ui.EASE)
                    .withEndAction(() -> view.setVisibility(View.INVISIBLE)).start();
        }
    }

    // ---- Aktionen der Zeilen ---------------------------------------------------------------------

    void requestButton(final SettingsModel.Button button) {
        if (!button.confirm.isEmpty()) {
            GlassPrompt.confirm(host.modalLayer(), button.title, button.confirm, button.confirmLabel.isEmpty() ? button.title : button.confirmLabel, button.destructive,
                    () -> performButton(button));
        } else {
            performButton(button);
        }
    }

    private void performButton(SettingsModel.Button button) {
        try {
            button.action.run();
        } catch (RuntimeException e) {
            Log.w(TAG, "Einstellung fehlgeschlagen: " + e.getMessage());
            host.toast("Das hat nicht geklappt.");
        }
        rebuildAfterChange();
    }
}
