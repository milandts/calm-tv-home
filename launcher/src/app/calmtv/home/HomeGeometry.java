package app.calmtv.home;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

/**
 * Platzierung der Blöcke der Startseite nach den Einstellungen aus {@link Opts}: Uhr-Gruppe, Button-Leiste und Dock samt
 * Titel. Standardwerte erzeugen genau die alten Maße. Steht das Dock oben, rücken Uhr-Gruppe und Leiste an den unteren Rand.
 * Haben Uhr-Gruppe und Leiste dieselbe Seite (oder die Uhr steht in der Mitte), hängt sich die Leiste unter die Uhr, damit
 * sich nichts überdeckt. Außerdem die Kachelmaße für Dock und Raster, die nur von der Einstellung und der Bildschirmbreite
 * abhängen. Die Fokus-Wege zwischen den Blöcken stehen in {@link HomeActivity}, sie fragen hier nach Dockposition und Leiste.
 */
final class HomeGeometry {
    static final int LEFT = 0, RIGHT = 1, CENTER = 2;                       // Opts.HEAD_CORNER
    static final int BAR_TOP_RIGHT = 0, BAR_TOP_LEFT = 1, BAR_AT_DOCK = 2; // Opts.BAR_POS
    private static final int IN_ROOT = 0, IN_HEAD = 1, IN_DOCK = 2;
    private static final int[] RECENT_COUNTS = {0, 3, 6, 12};
    private static final int[] DOCK_PERCENT = {80, 90, 100};   // 100 % = sechs Kacheln füllen das Dock bis zum Rand; mehr würde herausragen
    private static final int HEAD_X = 56, HEAD_Y = 30, BAR_X = 44, BAR_Y = 32, DOCK_X = 44, DOCK_Y = 30;
    private static final int TITLE_X = 12, TITLE_GAP = 10, TILE_GAP_DEFAULT = 16, PAD_ROW = 12;

    private final float dp;
    private final FrameLayout root;
    private final LinearLayout head, bar, bottom;
    private int dockCount = 6;
    private final View clock, title, dock;
    private final Pill[] pills;
    private int barParent = IN_ROOT;   // wie buildUi die Leiste anlegt
    private int bottomKey = -1;        // -1: noch nie gebaut, damit die Dock-Spalte beim ersten Anwenden ihre Layoutparameter (Glasbreite!) bekommt; Reihenfolge in der Dock-Spalte: Bit 0 = Dock oben, Bit 1 = Leiste in der Spalte

    HomeGeometry(Context c, FrameLayout root, LinearLayout head, LinearLayout bar, LinearLayout bottom, View clock,
                 View title, View dock, Pill[] pills) {
        this.dp = c.getResources().getDisplayMetrics().density;
        this.root = root;
        this.head = head;
        this.bar = bar;
        this.bottom = bottom;
        this.clock = clock;
        this.title = title;
        this.dock = dock;
        this.pills = pills;
    }

    // ---- Abfragen (auch für die Fokus-Wege) ----------------------------------------------------------

    static boolean dockTop() {
        return Config.val(Opts.DOCK_POS) == 1;
    }

    /** +1, wenn Uhr-Gruppe und Leiste unten sitzen (Dock oben), sonst -1: Richtung, in die sie beim Einblenden gleiten. */
    static int stripSign() {
        return dockTop() ? 1 : -1;
    }

    static int columns() {
        return 4 + Config.val(Opts.GRID_COLUMNS);
    }

    static int recentLimit() {
        return RECENT_COUNTS[Config.val(Opts.RECENT_COUNT)];
    }

    /** 0 = keine Namen unter den Dock-Kacheln, 1 = nur bei Fokus, 2 = immer. */
    static int labelMode() {
        return Config.val(Opts.DOCK_LABEL);
    }

    /** Alle Werte, die Kachelmaße oder Aufbau von Dock und Raster ändern; ändert sich der Text, wird neu gebaut. */
    static String styleSignature() {
        return Config.val(Opts.DOCK_SIZE) + "/" + Config.val(Opts.DOCK_GAP) + "/" + Config.val(Opts.TILE_RADIUS) + "/"
                + Config.val(Opts.DOCK_LABEL) + "/" + Config.val(Opts.GRID_COLUMNS) + "/" + Config.val(Opts.GRID_SORT) + "/"
                + Config.val(Opts.RECENT_COUNT) + "/" + Config.val(Opts.SAFE_MARGIN);
    }

    /** Breite einer Kachel bei dieser Spaltenzahl (Lücke 16 dp, Rand 44 dp plus 12 dp Innenabstand, Sicherheitsrand). */
    static int tileWidth(Context c, int columns) {
        int gap = Ui.px(c, TILE_GAP_DEFAULT);
        int inner = Metrics.rowInnerWidth(c, DOCK_X, PAD_ROW);
        return (inner - gap * (columns - 1)) / columns;
    }

    /** Faktor der Größenstufe des Docks (0,8 / 0,9 / 1) für Kacheln, Innenabstand und Glasbreite. */
    static float dockScale() {
        return DOCK_PERCENT[Config.val(Opts.DOCK_SIZE)] / 100f;
    }

    /** Anzahl der Dock-Kacheln; bestimmt mit, wie breit das Glas wird (mindestens sechs Kacheln breit). */
    void setDockCount(int count) {
        dockCount = count;
    }

    /** Abstand zwischen Spaltenrand und Glas auf jeder Seite: das Glas ist mittig und höchstens so breit wie die Spalte. */
    private int dockInset() {
        float density = bottom.getContext().getResources().getDisplayMetrics().density;
        float scale = dockScale();
        int tile = dockTileWidth(bottom.getContext());
        int gap = Math.round(Math.round(Config.val(Opts.DOCK_GAP) * density) * scale);
        int pad = Ui.px(bottom.getContext(), dockPadDp(bottom.getContext(), tile));
        int available = Ui.usableWidth(bottom.getContext()) - 2 * Math.round(DOCK_X * density);
        int min = 6 * tile + 5 * gap + 2 * pad;
        int content = dockCount * tile + Math.max(0, dockCount - 1) * gap + 2 * pad;
        return Math.max(0, (available - Math.min(available, Math.max(min, content))) / 2);
    }

    /**
     * Seitlicher Innenabstand des Docks in dp: mindestens 12 dp mal Größenstufe, mindestens so viel, dass eine fokussierte Kachel
     * samt Fokusring (Einstellungen "Fokus-Größe" und "Rahmenstärke") nicht von der Scroll-Ansicht des Docks abgeschnitten wird.
     */
    static int dockPadDp(Context c, int tilePx) {
        float density = Ui.density(c);
        float growPx = (Config.val(Opts.FOCUS_SCALE) / 100f - 1f) / 2f * tilePx + Config.val(Opts.FOCUS_RING) * density;
        return Math.max(Math.round(PAD_ROW * dockScale()), (int) Math.ceil(growPx / density) + 1);
    }

    /** Breite einer Dock-Kachel: so, dass sechs Kacheln mit dem eingestellten Abstand genau ins Dock passen, mal Größenstufe. */
    static int dockTileWidth(Context c) {
        int gap = Ui.px(c, Config.val(Opts.DOCK_GAP));
        int percent = DOCK_PERCENT[Config.val(Opts.DOCK_SIZE)];
        int tile = (Metrics.rowInnerWidth(c, DOCK_X, PAD_ROW) - 5 * gap) / 6 * percent / 100;
        int pad = dockPadDp(c, tile);
        if (pad > PAD_ROW) tile = (Metrics.rowInnerWidth(c, DOCK_X, pad) - 5 * gap) / 6 * percent / 100;   // großer Fokus braucht mehr Rand
        return tile;
    }

    /** Die sichtbare Leiste (mindestens ein Button an). */
    boolean barShown() {
        return bar.getVisibility() == View.VISIBLE;
    }

    /** Erster oder letzter sichtbarer Button der Leiste, null ohne sichtbare Buttons. */
    Pill edgePill(boolean first) {
        Pill found = null;
        for (Pill p : pills) {
            if (p.getVisibility() != View.VISIBLE) continue;
            if (first) return p;
            found = p;
        }
        return found;
    }

    boolean isPillVisible(Pill p) {
        return p != null && p.getVisibility() == View.VISIBLE;
    }

    // ---- Anwenden -------------------------------------------------------------------------------------

    /** Setzt Positionen und Sichtbarkeit; billig, darf bei jeder Änderung laufen. {@code forceSettings}: leeres Dock, kein Weg zu den Einstellungen. */
    void apply(boolean forceSettings) {
        boolean top = dockTop();
        int corner = Config.val(Opts.HEAD_CORNER);
        int barPos = Config.val(Opts.BAR_POS);
        boolean sameSide = (corner == LEFT && barPos == BAR_TOP_LEFT) || (corner == RIGHT && barPos == BAR_TOP_RIGHT);
        int parent = barPos == BAR_AT_DOCK ? IN_DOCK : (corner == CENTER || sameSide) ? IN_HEAD : IN_ROOT;

        applyPillVisibility(forceSettings);
        placeHead(top, corner);
        if (parent != barParent) {
            ViewGroup old = (ViewGroup) bar.getParent();
            if (old != null) old.removeView(bar);
            barParent = parent;
            if (parent == IN_DOCK) bottomKey = -1;   // Spalte neu aufbauen, die Leiste kommt hinein
        }
        placeBottom(top, parent == IN_DOCK);
        if (parent == IN_HEAD) place(head, headBarParams(corner));
        else if (parent == IN_ROOT) place(root, rootBarParams(top, barPos == BAR_TOP_LEFT));
    }

    private void applyPillVisibility(boolean forceSettings) {
        boolean apps = Config.val(Opts.SHOW_BAR_APPS) != 0, search = Config.val(Opts.SHOW_BAR_SEARCH) != 0;
        boolean settings = Config.val(Opts.SHOW_BAR_SETTINGS) != 0 || forceSettings;
        pills[0].setVisibility(apps ? View.VISIBLE : View.GONE);
        pills[1].setVisibility(search ? View.VISIBLE : View.GONE);
        pills[2].setVisibility(settings ? View.VISIBLE : View.GONE);
        bar.setVisibility(apps || search || settings ? View.VISIBLE : View.GONE);
    }

    private void placeHead(boolean top, int corner) {
        int dx = Config.val(Opts.HEAD_DX), dy = Config.val(Opts.HEAD_DY);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, (top ? Gravity.BOTTOM : Gravity.TOP) | horizontal(corner));
        sideMargins(lp, corner, HEAD_X, dx);
        if (top) lp.bottomMargin = px(Math.max(0, HEAD_Y - dy)); else lp.topMargin = px(Math.max(0, HEAD_Y + dy));
        head.setLayoutParams(lp);
        head.setGravity(horizontal(corner));
        // Die Glas-Uhr hat 10 dp Rand für den Schatten; auf der Seite der Ausrichtung zurückziehen.
        LinearLayout.LayoutParams clp = (LinearLayout.LayoutParams) clock.getLayoutParams();
        int pad = px(10);
        clp.setMargins(corner == RIGHT ? 0 : -pad, px(8) - pad, corner == LEFT ? 0 : -pad, px(9) - pad);
        clock.setLayoutParams(clp);
    }

    private FrameLayout.LayoutParams rootBarParams(boolean top, boolean left) {
        int dx = Config.val(Opts.BAR_DX), dy = Config.val(Opts.BAR_DY);
        int side = left ? LEFT : RIGHT;
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, (top ? Gravity.BOTTOM : Gravity.TOP) | horizontal(side));
        sideMargins(lp, side, BAR_X, dx);
        if (top) lp.bottomMargin = px(Math.max(0, BAR_Y - dy)); else lp.topMargin = px(Math.max(0, BAR_Y + dy));
        return lp;
    }

    /** Leiste unter der Uhr (gleiche Seite oder Mitte): richtet sich nach der Ausrichtung der Gruppe. */
    private LinearLayout.LayoutParams headBarParams(int corner) {
        int dx = Config.val(Opts.BAR_DX), dy = Config.val(Opts.BAR_DY);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.topMargin = px(Math.max(0, 12 + dy));
        if (corner == LEFT) lp.leftMargin = px(Math.max(0, dx));
        else if (corner == RIGHT) lp.rightMargin = -px(dx);
        else if (dx > 0) lp.leftMargin = px(2 * dx); else lp.rightMargin = px(-2 * dx);
        return lp;
    }

    /** Dock-Spalte: Dock und Titel in der Reihenfolge zur Dockposition, auf Wunsch mit der Leiste auf der Innenseite. */
    private void placeBottom(boolean top, boolean barInColumn) {
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(-1, -2, top ? Gravity.TOP : Gravity.BOTTOM);
        blp.setMargins(px(DOCK_X), top ? px(DOCK_Y) : 0, px(DOCK_X), top ? 0 : px(DOCK_Y));
        bottom.setLayoutParams(blp);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-2, -2);
        tlp.setMargins(px(TITLE_X) + dockInset(), top ? px(TITLE_GAP) : 0, 0, top ? 0 : px(TITLE_GAP));
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-2, -2);   // das Glas folgt der Kachelgröße (Mindestbreite setzt HomeActivity)
        dlp.gravity = Gravity.CENTER_HORIZONTAL;   // schmaleres Glas bleibt mittig in der Spalte
        LinearLayout.LayoutParams barLp = columnBarParams(top);
        int key = (top ? 1 : 0) | (barInColumn ? 2 : 0);
        if (key == bottomKey) {
            title.setLayoutParams(tlp);
            if (barInColumn) bar.setLayoutParams(barLp);
            return;
        }
        bottomKey = key;
        bottom.removeAllViews();
        if (!top) {
            if (barInColumn) bottom.addView(bar, barLp);
            bottom.addView(title, tlp);
            bottom.addView(dock, dlp);
        } else {
            bottom.addView(dock, dlp);
            bottom.addView(title, tlp);
            if (barInColumn) bottom.addView(bar, barLp);
        }
    }

    private LinearLayout.LayoutParams columnBarParams(boolean top) {
        int dx = Config.val(Opts.BAR_DX), dy = Config.val(Opts.BAR_DY);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.gravity = Gravity.END;
        lp.rightMargin = -px(dx) + dockInset();   // bündig am rechten Rand des (mittigen, schmaleren) Glases
        if (top) {
            lp.topMargin = px(6 + dy);
            lp.bottomMargin = -px(dy);
        } else {
            lp.topMargin = px(dy);
            lp.bottomMargin = px(4 - dy);
        }
        return lp;
    }

    private void place(ViewGroup parent, ViewGroup.LayoutParams lp) {
        if (bar.getParent() == parent) bar.setLayoutParams(lp); else parent.addView(bar, lp);
    }

    private static int horizontal(int corner) {
        return corner == LEFT ? Gravity.START : corner == RIGHT ? Gravity.END : Gravity.CENTER_HORIZONTAL;
    }

    /** Seitenrand eines Blocks: links/rechts vom Rand her, in der Mitte durch einseitigen Rand verschoben (plus = nach rechts). */
    private void sideMargins(FrameLayout.LayoutParams lp, int corner, int base, int dx) {
        if (corner == LEFT) lp.leftMargin = px(Math.max(0, base + dx));
        else if (corner == RIGHT) lp.rightMargin = px(Math.max(0, base - dx));
        else if (dx > 0) lp.leftMargin = px(2 * dx);
        else lp.rightMargin = px(-2 * dx);
    }

    private int px(float dips) {
        return Math.round(dips * dp);
    }
}
