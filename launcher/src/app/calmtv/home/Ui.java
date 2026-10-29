package app.calmtv.home;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.TextView;

import java.util.HashMap;
import java.util.Map;

/**
 * Gestaltungs-Tokens und kleine Helfer, die alle neuen Ansichten teilen (siehe docs/DESIGN.md).
 * Farben, Zeiten und Schrift stehen nur hier, damit alles zusammenpasst.
 */
final class Ui {
    // ---- Farben ----------------------------------------------------------------------------------
    static final int TEXT = 0xF2FFFFFF, TEXT_DIM = 0xB3FFFFFF, TEXT_FAINT = 0x80FFFFFF, TEXT_ON_LIGHT = 0xFF1C1C1E;
    static final int ROW = 0x1FFFFFFF, ROW_PRESSED = 0x33FFFFFF, PANEL = 0x52000000, HAIRLINE = 0x26FFFFFF, SCRIM = 0x8C000000;
    static final int DANGER = 0xFFFF453A, OK = 0xFF4CD964, WARN = 0xFFFFB340;
    /** Panel auf einem scharfen Foto (Kontrollzentrum, Einführung, Vorschau-Leiste): dunkel genug für Text auf Wald und Fels. */
    static final int PANEL_PHOTO = 0x99000000;
    /** Dialoge und Menüs (Modals): fast deckend, damit nichts durchscheint. */
    static final int PANEL_MODAL = 0xE6141416;

    // ---- Mindestgrößen für Text (Lesbarkeit auf dem Fernseher aus ca. 3 m) ---------------------
    static final float SP_CAPS = 12, SP_LABEL = 13, SP_BODY = 14;

    // ---- Bewegung --------------------------------------------------------------------------------
    static final long FAST = 140, MEDIUM = 220, SLOW = 360;
    static final DecelerateInterpolator EASE = new DecelerateInterpolator(1.8f);

    // ---- Radien in dp ----------------------------------------------------------------------------
    static final float RADIUS_ROW = 14, RADIUS_PANEL = 24, RADIUS_CHIP = 18, RADIUS_MENU = 20;

    /**
     * Dauer einer Animation nach der Einstellung "Tempo" (Normal 1, Langsam 1,6, Schnell 0,6, Aus 0). Alle neuen Animationen
     * laufen über diese Methode, nie mit festen Zahlen.
     */
    static long ms(long base) {
        int motion = Config.val(Opts.MOTION);
        float scale = motion == 1 ? 1.6f : motion == 2 ? 0.6f : motion == 3 ? 0f : 1f;
        return Math.round(base * scale);
    }

    /** Deckkraft einer Panel-Farbe nach der Einstellung "Panel-Deckkraft" (Standard 100 %). */
    static int panel(int argb) {
        int a = (argb >>> 24) * Config.val(Opts.PANEL_OPACITY) / 100;
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    /** Schriftgröße nach der Einstellung "Schriftgröße" (Standard 100 %). */
    static float sp(float sp) {
        return sp * Config.val(Opts.TEXT_SCALE) / 100f;
    }

    private static final Map<Integer, Typeface> FONTS = new HashMap<>();

    private Ui() {}

    /** Eine Ansicht, die über allem liegt und mit Zurück geschlossen wird (Menü, Eingabefeld). */
    interface Modal {
        void dismiss();
    }

    /**
     * Schließt die oberste {@link Modal}-Ansicht in der Ebene. Gibt true zurück, wenn eine geschlossen wurde;
     * dann ist der Zurück-Druck verbraucht.
     */
    static boolean dismissTopModal(ViewGroup layer) {
        for (int i = layer.getChildCount() - 1; i >= 0; i--) {
            View child = layer.getChildAt(i);
            if (child instanceof Modal) {
                ((Modal) child).dismiss();
                return true;
            }
        }
        return false;
    }

    static float density(Context c) {
        return c.getResources().getDisplayMetrics().density;
    }

    /** Breite des nutzbaren Bereichs: Bildschirm minus Sicherheitsrand (die Ebenen liegen im Rand der Startseite). */
    static int usableWidth(Context c) {
        return Metrics.usableWidth(c);
    }

    /** Höhe des nutzbaren Bereichs: Bildschirm minus Sicherheitsrand. */
    static int usableHeight(Context c) {
        return Metrics.usableHeight(c);
    }

    static int px(Context c, float dips) {
        return Math.round(dips * density(c));
    }

    /** Nunito in der gewünschten Strichstärke (300 bis 800); fällt auf die Systemschrift zurück. */
    static Typeface font(Context c, int weight) {
        synchronized (FONTS) {
            Typeface f = FONTS.get(weight);
            if (f != null) return f;
            try {
                f = Build.VERSION.SDK_INT >= 26
                        ? new Typeface.Builder(c.getAssets(), "fonts/Nunito.ttf").setFontVariationSettings("'wght' " + weight).build()
                        : Typeface.createFromAsset(c.getAssets(), "fonts/Nunito.ttf");
            } catch (RuntimeException e) {
                Log.w(HomeKeyService.TAG, "Nunito nicht ladbar", e);
            }
            if (f == null) f = Typeface.create("sans-serif", weight >= 600 ? Typeface.BOLD : Typeface.NORMAL);
            FONTS.put(weight, f);
            return f;
        }
    }

    /** Text in Nunito mit dezentem Schatten (lesbar auf hellen Fotos). */
    static TextView label(Context c, CharSequence text, float sp, int weight, int color) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextSize(sp);
        t.setTypeface(font(c, weight));
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        t.setShadowLayer(px(c, 3), 0, px(c, 1), 0x66000000);
        return t;
    }

    /** Abgerundete Fläche mit optionaler Kante (strokePx 0 = keine). */
    static GradientDrawable round(Context c, int color, float radiusDp, int strokeColor, int strokePx) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(px(c, radiusDp));
        if (strokePx > 0) d.setStroke(strokePx, strokeColor);
        return d;
    }
}
