package app.calmtv.home.dream;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Uhr, Datum und Wetter des Bildschirmschoners (eine Gruppe, die als Ganzes in die Ecken wandert). */
final class InfoPanel extends LinearLayout {
    private static final int ICON_DP = 44;
    private static final int WHITE = 0xFFFFFFFF, SOFT_WHITE = 0xF2FFFFFF, MUTED_WHITE = 0xE6FFFFFF;

    private final float dp;
    private final TextView clock, date, temperature, description, place;
    private final ImageView icon;
    private final LinearLayout weatherRow;
    private static final float[] CLOCK_SP = {64, 88, 112};   // Klein, Normal, Groß
    private Weather.Report report;
    private boolean isNight, isFahrenheit, isPlaceShown = true, isRangeShown = true;
    private int extra;   // 0 keine, 1 Regen, 2 Wind, 3 beides

    InfoPanel(Context context) {
        super(context);
        dp = context.getResources().getDisplayMetrics().density;
        setOrientation(VERTICAL);
        Typeface light = Fonts.get(context, 400), semibold = Fonts.get(context, 600), bold = Fonts.get(context, 700);

        clock = text(88, light, WHITE, -0.02f);
        date = text(21, semibold, SOFT_WHITE, 0.01f);
        temperature = text(32, bold, WHITE, 0f);
        description = text(21, semibold, WHITE, 0.01f);
        place = text(16, semibold, MUTED_WHITE, 0.02f);

        // Überall wrap_content angeben: LinearLayouts Vorgabe (match_parent) kürzt sonst Uhr und Datum auf die Breite der Wetterzeile.
        addView(clock, new LayoutParams(-2, -2));
        LayoutParams dateParams = new LayoutParams(-2, -2);
        dateParams.topMargin = px(-8);   // Nunito lässt unter der großen Uhr viel Luft
        addView(date, dateParams);

        icon = new ImageView(context);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);

        LinearLayout temperatureLine = new LinearLayout(context);
        temperatureLine.setOrientation(HORIZONTAL);
        temperatureLine.addView(temperature, new LayoutParams(-2, -2));
        LayoutParams descriptionParams = new LayoutParams(-2, -2);
        descriptionParams.leftMargin = px(12);
        temperatureLine.addView(description, descriptionParams);

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(VERTICAL);
        texts.addView(temperatureLine, new LayoutParams(-2, -2));
        LayoutParams placeParams = new LayoutParams(-2, -2);
        placeParams.topMargin = px(2);
        texts.addView(place, placeParams);

        weatherRow = new LinearLayout(context);
        weatherRow.setOrientation(HORIZONTAL);
        weatherRow.setGravity(Gravity.CENTER_VERTICAL);
        weatherRow.addView(icon, new LayoutParams(px(ICON_DP), px(ICON_DP)));
        LayoutParams textsParams = new LayoutParams(-2, -2);
        textsParams.leftMargin = px(14);
        weatherRow.addView(texts, textsParams);
        LayoutParams rowParams = new LayoutParams(-2, -2);
        rowParams.topMargin = px(20);
        addView(weatherRow, rowParams);
        weatherRow.setVisibility(View.GONE);
    }

    void setTime(CharSequence time, CharSequence day) {
        clock.setText(time);
        date.setText(day);
    }

    /** Liest Uhrgröße und -stil, Temperatureinheit und Wetter-Zusatz aus den Einstellungen (beim Start). */
    void applySettings() {
        Context context = getContext();
        int size = AmbientSettings.getInt(context, AmbientSettings.KEY_CLOCK_SIZE, AmbientSettings.DEFAULT_CLOCK_SIZE);
        clock.setTextSize(CLOCK_SP[Math.max(0, Math.min(CLOCK_SP.length - 1, size))]);
        boolean isBold = AmbientSettings.getInt(context, AmbientSettings.KEY_CLOCK_STYLE, AmbientSettings.DEFAULT_CLOCK_STYLE) == 1;
        clock.setTypeface(Fonts.get(context, isBold ? 700 : 400));
        isFahrenheit = AmbientSettings.getInt(context, AmbientSettings.KEY_UNIT, AmbientSettings.DEFAULT_UNIT) == 1;
        isPlaceShown = AmbientSettings.get(context, AmbientSettings.KEY_SHOW_PLACE);
        isRangeShown = AmbientSettings.get(context, AmbientSettings.KEY_SHOW_RANGE);
        extra = AmbientSettings.getInt(context, AmbientSettings.KEY_EXTRA, AmbientSettings.DEFAULT_EXTRA);
        if (report != null) render();
    }

    void showWeather(Weather.Report newReport, boolean night) {
        report = newReport;
        isNight = night;
        render();
        applyIcon();
        weatherRow.setVisibility(View.VISIBLE);
    }

    private void render() {
        temperature.setText(degrees(report.temperature, true));
        description.setText(report.description);
        description.setVisibility(report.description.isEmpty() ? View.GONE : View.VISIBLE);
        StringBuilder line = new StringBuilder();
        if (isPlaceShown) line.append(report.place);
        if (isRangeShown) {
            if (line.length() > 0) line.append("  ·  ");
            line.append(degrees(report.low, false)).append(" bis ").append(degrees(report.high, true));
        }
        if ((extra & 1) != 0 && report.rainPercent >= 0) append(line, "Regen " + report.rainPercent + " %");
        if ((extra & 2) != 0 && report.windKmh >= 0) append(line, "Wind " + Math.round(report.windKmh) + " km/h");
        place.setText(line);
        place.setVisibility(line.length() == 0 ? View.GONE : View.VISIBLE);
    }

    private static void append(StringBuilder line, String part) {
        if (line.length() > 0) line.append("  ·  ");
        line.append(part);
    }

    /** Gerundete Gradzahl; die Einheit (nur "F") steht nur dort, wo {@code withUnit} gesetzt ist. */
    private String degrees(double celsius, boolean withUnit) {
        long value = Math.round(isFahrenheit ? celsius * 9 / 5 + 32 : celsius);
        return value + "°" + (withUnit && isFahrenheit ? "F" : "");
    }

    void hideWeather() {
        report = null;
        icon.setImageDrawable(null);
        weatherRow.setVisibility(View.GONE);
    }

    /** Tag-/Nachtwechsel: tauscht Sonne und Mond, falls nötig. */
    void setNight(boolean night) {
        if (night == isNight) return;
        isNight = night;
        if (report != null) applyIcon();
    }

    private void applyIcon() {
        int kind = WeatherIcon.kindFor(report.code, isNight);
        if (kind < 0) {
            icon.setImageDrawable(null);
            icon.setVisibility(View.GONE);
            return;
        }
        icon.setVisibility(View.VISIBLE);
        if (icon.getDrawable() instanceof WeatherIcon && ((WeatherIcon) icon.getDrawable()).kind() == kind) return;
        icon.setImageDrawable(new WeatherIcon(kind, WHITE));
    }

    private TextView text(float sp, Typeface face, int color, float letterSpacing) {
        TextView t = new TextView(getContext());
        t.setTextSize(sp);
        t.setTypeface(face);
        t.setTextColor(color);
        t.setLetterSpacing(letterSpacing);
        t.setIncludeFontPadding(false);
        t.setShadowLayer(px(10), 0, px(2), 0x80000000);   // kräftig genug für helle Motive (Schnee, Himmel)
        return t;
    }

    private int px(float dips) {
        return Math.round(dips * dp);
    }
}
