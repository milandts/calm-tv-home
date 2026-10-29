package app.calmtv.home;

import android.content.Context;

/**
 * Die Kachelmaße von Dock und App-Raster in Pixeln. Sie hängen von Spalten, Größenstufe, Abstand und Sicherheitsrand ab
 * ({@link HomeGeometry}); {@link #isStale} sagt, ob sich eine dieser Einstellungen geändert hat und Dock und Raster neu gebaut werden müssen.
 */
final class TileSizes {
    private static final int GRID_GAP_DP = 16;

    int columns, gridGap, gridWidth, gridHeight, dockWidth, dockHeight, dockGap;
    private String signature = "";

    void refresh(Context context) {
        signature = HomeGeometry.styleSignature();
        gridGap = Ui.px(context, GRID_GAP_DP);
        columns = HomeGeometry.columns();
        gridWidth = HomeGeometry.tileWidth(context, columns);
        gridHeight = gridWidth * 9 / 16;
        dockWidth = HomeGeometry.dockTileWidth(context);
        dockHeight = dockWidth * 9 / 16;
        dockGap = Math.round(Ui.px(context, Config.val(Opts.DOCK_GAP)) * HomeGeometry.dockScale());   // der Abstand wächst mit der Größenstufe
    }

    boolean isStale() {
        return !signature.equals(HomeGeometry.styleSignature());
    }
}
