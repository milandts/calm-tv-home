package app.calmtv.home.dream;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.widget.TextView;

/**
 * Gestaltung der Einstellungen im Look des Launchers: dunkler Grund, Nunito, abgerundete Flächen,
 * Fokus = gefüllte Fläche in der Akzentfarbe (Schrift darauf dunkel oder weiß, je nach Helligkeit).
 */
final class SetupStyle {
    static final int TEXT = 0xF2FFFFFF, MUTED = 0xB3FFFFFF, WEAK = 0x80FFFFFF, NOTE = 0xA6FFFFFF;
    static final int ROW_FILL = 0x1FFFFFFF, PANEL_FILL = 0x99000000, PANEL_EDGE = 0x26FFFFFF;
    static final int DARK_TEXT = 0xFF1C1C1E, BACKGROUND = 0xFF141416;
    static final float ROW_RADIUS = 14, PANEL_RADIUS = 24;

    final int accent, onAccent;
    final Typeface light, medium, semibold, bold;
    private final float dp;

    /** @param accentColor Akzentfarbe (ARGB) vom Launcher; die Deckkraft wird ignoriert. */
    SetupStyle(Context context, int accentColor) {
        dp = context.getResources().getDisplayMetrics().density;
        accent = accentColor | 0xFF000000;
        onAccent = onAccentFor(accent);
        light = Fonts.get(context, 300);
        medium = Fonts.get(context, 500);
        semibold = Fonts.get(context, 600);
        bold = Fonts.get(context, 700);
    }

    /** Dunkle Schrift auf hellem Grund, sonst weiße. */
    static int onAccentFor(int color) {
        int brightness = (299 * Color.red(color) + 587 * Color.green(color) + 114 * Color.blue(color)) / 1000;
        return brightness > 140 ? DARK_TEXT : 0xFFFFFFFF;
    }

    int px(float dips) {
        return Math.round(dips * dp);
    }

    /** Dunkler Hintergrund mit leichtem Verlauf und einem zarten Schein der Akzentfarbe oben rechts. */
    LayerDrawable background() {
        GradientDrawable base = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[] {0xFF1F1F25, BACKGROUND, 0xFF0F0F11});
        GradientDrawable glow = new GradientDrawable();
        glow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        glow.setGradientRadius(px(480));
        glow.setGradientCenter(0.88f, 0.05f);
        glow.setColors(new int[] {(accent & 0x00FFFFFF) | 0x24000000, accent & 0x00FFFFFF});
        return new LayerDrawable(new GradientDrawable[] {base, glow});
    }

    GradientDrawable rounded(int color, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(px(radiusDp));
        return d;
    }

    GradientDrawable panel() {
        GradientDrawable d = rounded(PANEL_FILL, PANEL_RADIUS);
        d.setStroke(Math.max(1, px(1)), PANEL_EDGE);
        return d;
    }

    TextView text(Context context, String content, float sp, Typeface face, int color) {
        TextView t = new TextView(context);
        t.setText(content);
        t.setTextSize(sp);
        t.setTypeface(face);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        return t;
    }
}
