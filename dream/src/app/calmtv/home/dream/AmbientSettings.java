package app.calmtv.home.dream;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Die Einstellungen des Bildschirmschoners (SharedPreferences "ambient"). Keine persönlichen Daten; die Stadt liegt in "weather".
 * Fehlt ein Wert, gilt der Standard, und der ergibt genau das Verhalten vor der Einführung der Einstellung.
 */
final class AmbientSettings {
    // Schalter (Standard: an)
    static final String KEY_WEATHER = "weather", KEY_MOTION = "motion", KEY_NIGHT = "night";
    static final String KEY_SHOW_PLACE = "show_place", KEY_SHOW_RANGE = "show_range";
    // Auswahlen (ganze Zahlen)
    static final String KEY_PHOTO_SECONDS = "photo_seconds", KEY_MOTION_LEVEL = "motion_level", KEY_UNIT = "unit", KEY_EXTRA = "weather_extra", KEY_REFRESH = "refresh";
    static final String KEY_TIME_FORMAT = "time_format", KEY_CLOCK_SIZE = "clock_size", KEY_CLOCK_STYLE = "clock_style";
    static final String KEY_DATE_FORMAT = "date_format", KEY_CORNER = "corner";
    static final String KEY_NIGHT_FROM = "night_from", KEY_NIGHT_TO = "night_to", KEY_NIGHT_BRIGHTNESS = "night_brightness";

    static final int DEFAULT_PHOTO_SECONDS = 24, DEFAULT_MOTION_LEVEL = 1, DEFAULT_UNIT = 0, DEFAULT_EXTRA = 0, DEFAULT_REFRESH = 0;
    static final int DEFAULT_TIME_FORMAT = 0, DEFAULT_CLOCK_SIZE = 1, DEFAULT_CLOCK_STYLE = 0, DEFAULT_DATE_FORMAT = 0;
    static final int CORNER_WANDER = -1, DEFAULT_CORNER = CORNER_WANDER;
    static final int DEFAULT_NIGHT_FROM = 23, DEFAULT_NIGHT_TO = 6, DEFAULT_NIGHT_BRIGHTNESS = 45;
    private static final String PREFS = "ambient";

    private AmbientSettings() {}

    /** Schalter; alle sind ab Werk an. */
    static boolean get(Context context, String key) {
        return prefs(context).getBoolean(key, true);
    }

    static void set(Context context, String key, boolean value) {
        prefs(context).edit().putBoolean(key, value).apply();
    }

    static int getInt(Context context, String key, int fallback) {
        return prefs(context).getInt(key, fallback);
    }

    static void setInt(Context context, String key, int value) {
        prefs(context).edit().putInt(key, value).apply();
    }

    /** Setzt alle Schalter und Auswahlen zurück (die Stadt bleibt). */
    static void reset(Context context) {
        prefs(context).edit().clear().apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
