package app.calmtv.home;

import android.content.Context;
import android.graphics.Typeface;
import android.widget.TextView;

/** Die zwei Textarten der Startseite: gewöhnlicher Text mit weichem Schatten und kleine, gesperrte Großbuchstaben. */
final class HomeText {
    private static final int SOFT_SHADOW = 0x66000000, CAPS_SHADOW = 0xB3000000, CAPS_COLOR = 0xD9FFFFFF;

    private HomeText() {}

    /** Text in einer Systemschrift (Familienname wie "sans-serif"). */
    static TextView plain(Context c, float sp, String family, int color) {
        TextView t = new TextView(c);
        t.setTypeface(Typeface.create(family, Typeface.NORMAL));
        t.setTextColor(color);
        t.setTextSize(sp);
        t.setShadowLayer(Ui.px(c, 6), 0, Ui.px(c, 1), SOFT_SHADOW);
        return t;
    }

    /** Kleine, gesperrte Großbuchstaben in Nunito, zum Beispiel für den Gruß, das Datum und Abschnittsüberschriften. */
    static TextView caps(Context c, float sp) {
        TextView t = plain(c, sp, "sans-serif-medium", CAPS_COLOR);
        t.setTypeface(Ui.font(c, 700));
        t.setIncludeFontPadding(false);
        t.setLetterSpacing(0.08f);   // Nunito läuft breiter als die Systemschrift, darum weniger Sperrung
        t.setAllCaps(true);
        t.setShadowLayer(Ui.px(c, 5), 0, Ui.px(c, 1), CAPS_SHADOW);   // auch auf hellen Fotos lesbar
        return t;
    }
}
