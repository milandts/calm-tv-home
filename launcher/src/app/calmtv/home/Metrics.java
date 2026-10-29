package app.calmtv.home;

import android.content.Context;

/**
 * Zentrale Maße: Bildschirm, nutzbarer Bereich (ohne Sicherheitsrand), Abstandsleiter und der Platz für die Dock-Kacheln.
 * Layout-Code liest die Bildschirmgröße nur hier (scripts/check-rules.sh prüft das); siehe docs/DESIGN.md.
 */
final class Metrics {
    /** Abstandsleiter in dp; Abstände zwischen Elementen nehmen eine dieser Stufen. */
    static final int SPACE_XS = 4, SPACE_S = 8, SPACE_M = 12, SPACE_L = 16, SPACE_XL = 24, SPACE_XXL = 32;

    private Metrics() {}

    /** Gesamtbreite des Bildschirms in Pixeln (nur für Bilder in Bildschirmgröße; Layouts nehmen {@link #usableWidth}). */
    static int screenWidth(Context c) {
        return c.getResources().getDisplayMetrics().widthPixels;
    }

    static int screenHeight(Context c) {
        return c.getResources().getDisplayMetrics().heightPixels;
    }

    /** Seitenverhältnis des Bildschirms (Breite durch Höhe). */
    static float screenAspect(Context c) {
        return screenWidth(c) / (float) screenHeight(c);
    }

    /** Breite des nutzbaren Bereichs: Bildschirm minus Sicherheitsrand (die Ebenen liegen im Rand der Startseite). */
    static int usableWidth(Context c) {
        return screenWidth(c) - 2 * safeMarginPx(c);
    }

    static int usableHeight(Context c) {
        return screenHeight(c) - 2 * safeMarginPx(c);
    }

    static int safeMarginPx(Context c) {
        return Ui.px(c, Config.val(Opts.SAFE_MARGIN));
    }

    /** Platz für die Kachelreihe: nutzbare Breite minus seitlicher Rand und Innenabstand der Reihe. */
    static int rowInnerWidth(Context c, int sideDp, int rowPadDp) {
        return usableWidth(c) - 2 * Ui.px(c, sideDp) - 2 * Ui.px(c, rowPadDp);
    }
}
