package app.calmtv.home;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;


/**
 * Kontrollzentrum: ein Glas-Panel oben rechts unter der Button-Leiste mit Uhrzeit, Status und sechs Schnellkacheln.
 * Die Startseite bleibt links sichtbar (Vorschau-Modus), rechts liegt nur ein leichter Schleier.
 * Geöffnet mit der Menü-Taste auf den Buttons oben rechts.
 */
final class ControlCenter implements Screen {
    private static final int COLUMNS = 3, TILE_COUNT = 6;
    private static final int SAVER = 0, NEXT_IMAGE = 1, SHARPNESS = 2, DIMMING = 3, SETTINGS = 4, SYSTEM = 5;   // Reihenfolge im Raster

    private static final float PANEL_WIDTH_DP = 540, PANEL_TOP_DP = 84, PANEL_RIGHT_DP = 48, PANEL_PADDING_DP = 16;
    // Höhe insgesamt etwa 265 dp: das Panel bleibt zwischen Button-Leiste (bis y 66) und Dock (ab y 405).
    private static final float TILE_HEIGHT_DP = 80, TILE_GAP_DP = 8, SLIDE_DP = 24;
    private static final int VEIL_COLOR = 0x40000000;
    private static final String DREAM_PACKAGE = "app.calmtv.home.dream";
    private static final String DREAM_PREVIEW = "app.calmtv.home.dream.PreviewActivity";

    /** Vom Integrator gesetzt: öffnet die Einstellungen, nachdem das Kontrollzentrum geschlossen ist. */
    Runnable onOpenSettings;

    private final HomeHost host;
    private final Context context;
    private final Look look;
    private final View.OnKeyListener tileKeys = this::onTileKey;
    private final ControlCenterTile[] tiles = new ControlCenterTile[TILE_COUNT];
    /** Die gezeigten Kacheln in Rasterreihenfolge (Auswahl und Reihenfolge laut Einstellungen). */
    private final java.util.List<ControlCenterTile> shown = new java.util.ArrayList<>();
    private final FrameLayout root;
    private final View veil;
    private final LinearLayout panel;
    private final Intent saverIntent;
    private final boolean isSaverAvailable;

    ControlCenter(HomeHost host) {
        this.host = host;
        this.context = host.context();
        this.look = host.look();
        saverIntent = new Intent().setClassName(DREAM_PACKAGE, DREAM_PREVIEW).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        isSaverAvailable = host.canStart(saverIntent);

        root = new FrameLayout(context);
        root.setClipChildren(false);

        // Leichter Schleier nur auf der rechten Hälfte, weich auslaufend nach links.
        veil = new View(context);
        veil.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[] {0x00000000, VEIL_COLOR}));
        int halfWidth = Ui.usableWidth(context) / 2;
        root.addView(veil, new FrameLayout.LayoutParams(halfWidth, -1, Gravity.END));

        panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.px(context, PANEL_PADDING_DP);
        panel.setPadding(pad, pad, pad, Ui.px(context, 12));
        panel.setBackground(Ui.round(context, Ui.PANEL_PHOTO, Ui.RADIUS_PANEL, Ui.HAIRLINE, Math.max(1, Ui.px(context, 1))));
        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(Ui.px(context, PANEL_WIDTH_DP), -2, Gravity.TOP | Gravity.END);
        panelParams.topMargin = Ui.px(context, PANEL_TOP_DP);
        panelParams.rightMargin = Ui.px(context, PANEL_RIGHT_DP);
        root.addView(panel, panelParams);

        // Kopf in einer Zeile: links der Titel, rechts die Statuspillen (die Uhr zeigt schon die Startseite).
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(Ui.label(context, "Kontrollzentrum", 17, 600, Ui.TEXT), new LinearLayout.LayoutParams(0, -2, 1f));
        header.addView(buildStatusRow(), new LinearLayout.LayoutParams(-2, -2));
        panel.addView(header, new LinearLayout.LayoutParams(-1, -2));
        panel.addView(buildGrid(), topMargin(12));

        TextView hint = Ui.label(context, "Zurück oder Menü schließt", Ui.SP_LABEL, 500, Ui.TEXT_DIM);
        hint.setGravity(Gravity.CENTER);
        panel.addView(hint, topMargin(10));
        // Bei großem Sicherheitsrand würde das Panel das Dock berühren: dann entfällt die Hinweiszeile.
        panel.post(() -> {
            if (root.getHeight() > 0 && panel.getBottom() > root.getHeight() - Ui.px(context, 130)) hint.setVisibility(View.GONE);
        });

        refreshStates();

        // Anfangszustand der Einblendung (so blitzt das Panel nicht kurz in voller Größe auf).
        panel.setAlpha(0f);
        panel.setTranslationY(-Ui.px(context, SLIDE_DP));
        veil.setAlpha(0f);
    }

    // ---- Aufbau ----------------------------------------------------------------------------------

    private LinearLayout.LayoutParams topMargin(float dips) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Ui.px(context, dips);
        return lp;
    }

    /** Zwei kleine Pillen: Filter-Zustand und Verbindungsart (nur Transporttyp, kein Netzwerkname). */
    private LinearLayout buildStatusRow() {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        boolean isFilterActive = host.isFilterActive();
        row.addView(statusPill(null, isFilterActive ? Ui.OK : Ui.WARN, isFilterActive ? "Filter aktiv" : "Kein Filter"),
                new LinearLayout.LayoutParams(-2, Ui.px(context, 28)));

        String connection = connectionLabel();
        int iconId = "WLAN".equals(connection) ? Icons.WLAN : Icons.VERBINDUNG;
        LinearLayout.LayoutParams linkParams = new LinearLayout.LayoutParams(-2, Ui.px(context, 28));
        linkParams.leftMargin = Ui.px(context, 8);
        row.addView(statusPill(Icons.icon(iconId, Ui.TEXT_DIM), 0, connection), linkParams);
        return row;
    }

    /** Eine Pille mit Punkt (dotColor) oder Symbol vor dem Text. */
    private LinearLayout statusPill(Icons.Glyph glyph, int dotColor, String text) {
        LinearLayout pill = new LinearLayout(context);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setPadding(Ui.px(context, 11), 0, Ui.px(context, 13), 0);
        pill.setBackground(Ui.round(context, Ui.ROW, 100, 0, 0));
        int size = Ui.px(context, glyph != null ? 14 : 8);
        if (glyph != null) {
            ImageView icon = new ImageView(context);
            icon.setImageDrawable(glyph);
            pill.addView(icon, new LinearLayout.LayoutParams(size, size));
        } else {
            View dot = new View(context);
            GradientDrawable disc = new GradientDrawable();
            disc.setShape(GradientDrawable.OVAL);
            disc.setColor(dotColor);
            dot.setBackground(disc);
            pill.addView(dot, new LinearLayout.LayoutParams(size, size));
        }
        TextView label = Ui.label(context, text, Ui.SP_LABEL, 600, Ui.TEXT);
        label.setSingleLine();
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.leftMargin = Ui.px(context, 7);
        pill.addView(label, lp);
        return pill;
    }

    private LinearLayout buildGrid() {
        tiles[SAVER] = new ControlCenterTile(context, Icons.SCHIRMSCHONER, "Bildschirmschoner");
        tiles[NEXT_IMAGE] = new ControlCenterTile(context, Icons.BILD, "Nächstes Bild");
        tiles[SHARPNESS] = new ControlCenterTile(context, Icons.UNSCHARF, "Schärfe");
        tiles[DIMMING] = new ControlCenterTile(context, Icons.ABDUNKELN, "Abdunkeln");
        tiles[SETTINGS] = new ControlCenterTile(context, Icons.SYSTEM, "Einstellungen");
        tiles[SYSTEM] = new ControlCenterTile(context, Icons.TV, "Fire TV");

        tiles[SAVER].setOnClickListener(v -> startSaver());
        tiles[NEXT_IMAGE].setOnClickListener(v -> host.nextWallpaper());
        tiles[SHARPNESS].setOnClickListener(v -> toggleSharpness());
        tiles[DIMMING].setOnClickListener(v -> cycleDimming());
        tiles[SETTINGS].setOnClickListener(v -> openSettings());
        tiles[SYSTEM].setOnClickListener(v -> openSystem());
        tiles[SAVER].setAvailable(isSaverAvailable);

        chooseShownTiles();
        LinearLayout grid = new LinearLayout(context);
        grid.setOrientation(LinearLayout.VERTICAL);
        int gap = Ui.px(context, TILE_GAP_DP);
        int rows = (shown.size() + COLUMNS - 1) / COLUMNS;
        for (int r = 0; r < rows; r++) {
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int col = 0; col < COLUMNS; col++) {
                int position = r * COLUMNS + col;
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.px(context, TILE_HEIGHT_DP), 1f);
                if (col > 0) lp.leftMargin = gap;
                if (position < shown.size()) {
                    ControlCenterTile tile = shown.get(position);
                    tile.setOnKeyListener(tileKeys);
                    row.addView(tile, lp);
                } else {
                    row.addView(new View(context), lp);   // leerer Platz, damit die Kacheln der letzten Zeile gleich breit bleiben
                }
            }
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
            if (r > 0) rowParams.topMargin = gap;
            grid.addView(row, rowParams);
        }
        return grid;
    }

    /** Legt fest, welche Kacheln erscheinen: "Zuerst" vorn (in der Grundreihenfolge), dann "Normal"; "Aus" fällt weg. Einstellungen ist immer dabei. */
    private void chooseShownTiles() {
        Opt[] options = {Opts.CC_SAVER, Opts.CC_NEXT, Opts.CC_SHARP, Opts.CC_DIM, Opts.CC_SETTINGS, Opts.CC_SYSTEM};   // Reihenfolge der Kennungen SAVER bis SYSTEM
        for (int mode : new int[] {1, 0}) {
            for (int id = 0; id < TILE_COUNT; id++) if (Config.val(options[id]) == mode) shown.add(tiles[id]);
        }
        if (!shown.contains(tiles[SETTINGS])) shown.add(tiles[SETTINGS]);   // Sicherheitsnetz: nie ohne Weg zu den Einstellungen
    }

    // ---- Zustände und Aktionen -------------------------------------------------------------------

    /** Schreibt die Zustandszeilen aller Kacheln neu; läuft nach jedem Schalten. */
    private void refreshStates() {
        tiles[SAVER].setState(isSaverAvailable ? "Jetzt starten" : "Nicht verfügbar");
        tiles[NEXT_IMAGE].setState("Anderes Foto");
        tiles[SHARPNESS].setState(look.isBlurred() ? "Unscharf" : "Scharf");
        tiles[DIMMING].setState(Look.DIM_PERCENT[look.dimLevel()] + " %");
        tiles[SETTINGS].setState("Alle Optionen");
        tiles[SYSTEM].setState("Töne, Netzwerk, Konto");
    }

    private void startSaver() {
        if (host.start(saverIntent)) host.closeScreen();
    }

    private void toggleSharpness() {
        look.toggleBlurred();
        host.applyLook();
        refreshStates();
    }

    private void cycleDimming() {
        look.nextDim();
        host.applyLook();
        refreshStates();
    }

    private void openSettings() {
        Runnable openAction = onOpenSettings;
        host.closeScreen();
        if (openAction != null) openAction.run();
    }

    private void openSystem() {
        host.closeScreen();
        host.openAmazonOverview();
    }

    /** Nur der Transporttyp der echten Verbindung; VPN-Netze (der Filter) zählen nicht. */
    private String connectionLabel() {
        try {
            ConnectivityManager manager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (manager == null) return "Offline";
            boolean hasWifi = false, hasEthernet = false;
            for (Network network : manager.getAllNetworks()) {
                NetworkCapabilities caps = manager.getNetworkCapabilities(network);
                if (caps == null || caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
                        || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) continue;
                hasWifi |= caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
                hasEthernet |= caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET);
            }
            if (hasEthernet) return "Ethernet";
            return hasWifi ? "WLAN" : "Offline";
        } catch (RuntimeException e) {
            return "Unbekannt";
        }
    }

    // ---- Navigation ------------------------------------------------------------------------------

    /** D-Pad bleibt im Raster (am Rand verbraucht), Menü schließt, Zurück geht an den Host. */
    private boolean onTileKey(View view, int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            if (event.getAction() == KeyEvent.ACTION_UP) host.closeScreen();
            return true;
        }
        int rowStep = 0, columnStep = 0;
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP: rowStep = -1; break;
            case KeyEvent.KEYCODE_DPAD_DOWN: rowStep = 1; break;
            case KeyEvent.KEYCODE_DPAD_LEFT: columnStep = -1; break;
            case KeyEvent.KEYCODE_DPAD_RIGHT: columnStep = 1; break;
            default: return false;
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN) moveFocus(view, rowStep, columnStep);
        return true;
    }

    /** Springt zur nächsten verfügbaren Kachel in Richtung; am Rand bleibt der Fokus stehen. */
    private void moveFocus(View from, int rowStep, int columnStep) {
        int index = -1;
        for (int i = 0; i < shown.size(); i++) if (shown.get(i) == from) index = i;
        if (index < 0) return;
        int row = index / COLUMNS, column = index % COLUMNS;
        while (true) {
            row += rowStep;
            column += columnStep;
            int position = row * COLUMNS + column;
            if (row < 0 || column < 0 || column >= COLUMNS || position >= shown.size()) return;
            ControlCenterTile target = shown.get(position);
            if (target.isFocusable()) {
                target.requestFocus();
                return;
            }
        }
    }

    // ---- Screen ----------------------------------------------------------------------------------

    @Override public View view() {
        return root;
    }

    @Override public void onShown() {
        host.setPreviewMode(true);
        veil.animate().alpha(1f).setDuration(Ui.MEDIUM).setInterpolator(Ui.EASE).start();
        panel.animate().alpha(1f).translationY(0f).setDuration(Ui.MEDIUM).setInterpolator(Ui.EASE).start();
        for (ControlCenterTile tile : shown) {
            if (tile.isFocusable()) {
                tile.requestFocus();
                break;
            }
        }
    }

    @Override public boolean onBack() {
        return false;
    }

    @Override public boolean onMenu() {
        host.closeScreen();
        return true;
    }

    @Override public void onClosed() {
        panel.animate().cancel();
        veil.animate().cancel();
        host.setPreviewMode(false);
    }
}
