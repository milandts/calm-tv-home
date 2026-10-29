package app.calmtv.home;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Texte für Zeiten und Größen (deutsch, mit Dezimalkomma). */
final class SettingsPagesFormat {
    private static final long SECONDS_PER_MINUTE = 60, MINUTES_PER_HOUR = 60, HOURS_PER_DAY = 24;
    private static final double BYTES_PER_GB = 1_000_000_000d;
    private static final long BYTES_PER_MB = 1024L * 1024L;

    /** Bekannte Fire-TV-Modellcodes (Build.MODEL) in Klartext. */
    private static final String[][] FIRE_TV_MODELS = {
            {"AFTSS", "Fire TV Stick HD"},
            {"AFTSSS", "Fire TV Stick (3. Generation)"},
            {"AFTMM", "Fire TV Stick 4K"},
            {"AFTKA", "Fire TV Stick 4K Max"},
            {"AFTN", "Fire TV (3. Generation)"},
            {"AFTRS", "Fire TV Cube (2. Generation)"},
            {"AFTGAZL", "Fire TV Cube (3. Generation)"}};

    private SettingsPagesFormat() {}

    /** Gerätename: bekannte Fire-TV-Codes in Klartext, unbekannte als "Fire TV (CODE)". */
    static String deviceName(String manufacturer, String model) {
        String code = model == null ? "" : model.trim();
        if (code.isEmpty()) return SettingsPagesRows.UNKNOWN;
        for (String[] known : FIRE_TV_MODELS) if (known[0].equalsIgnoreCase(code)) return known[1];
        boolean isFireTv = code.toUpperCase(Locale.ROOT).startsWith("AFT");
        if (isFireTv) return "Fire TV (" + code + ")";
        return manufacturer == null || manufacturer.isEmpty() ? code : manufacturer + " " + code;
    }

    /** Sicherheitspatch "2026-03-01" als "Sicherheitsstand 1. März 2026"; bei Fehlern "unbekannt". */
    static String securityPatch(String isoDate) {
        try {
            Date date = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).parse(isoDate);
            if (date == null) return SettingsPagesRows.UNKNOWN;
            return "Sicherheitsstand " + new SimpleDateFormat("d. MMMM yyyy", Locale.GERMANY).format(date);
        } catch (ParseException | RuntimeException e) {
            return SettingsPagesRows.UNKNOWN;
        }
    }

    /** Zeit bis zum Bildschirmschoner aus Millisekunden: "30 Sekunden", "5 Minuten", "1 Stunde". */
    static String timeout(long millis) {
        if (millis <= 0) return SettingsPagesRows.UNKNOWN;
        long seconds = millis / 1000;
        if (seconds >= HOURS_PER_DAY * MINUTES_PER_HOUR * SECONDS_PER_MINUTE) return "Nie";
        if (seconds < SECONDS_PER_MINUTE || seconds % SECONDS_PER_MINUTE != 0) return seconds + " Sekunden";
        long minutes = seconds / SECONDS_PER_MINUTE;
        if (minutes < MINUTES_PER_HOUR || minutes % MINUTES_PER_HOUR != 0) {
            return minutes == 1 ? "1 Minute" : minutes + " Minuten";
        }
        long hours = minutes / MINUTES_PER_HOUR;
        return hours == 1 ? "1 Stunde" : hours + " Stunden";
    }

    /** Laufzeit seit dem Start: "3 Std. 12 Min.", "2 Tg. 3 Std.". */
    static String uptime(long millis) {
        long totalMinutes = Math.max(0, millis) / 60_000;
        long days = totalMinutes / (MINUTES_PER_HOUR * HOURS_PER_DAY);
        long hours = (totalMinutes / MINUTES_PER_HOUR) % HOURS_PER_DAY;
        long minutes = totalMinutes % MINUTES_PER_HOUR;
        if (days > 0) return days + " Tg. " + hours + " Std.";
        if (hours > 0) return hours + " Std. " + minutes + " Min.";
        return minutes > 0 ? minutes + " Min." : "unter 1 Min.";
    }

    /** Speicher in GB mit einer Nachkommastelle (Android rechnet Speicher dezimal). */
    static String gigabytes(long bytes) {
        return String.format(Locale.GERMANY, "%.1f GB", bytes / BYTES_PER_GB);
    }

    /** Arbeitsspeicher in MB mit Tausenderpunkt. */
    static String megabytes(long bytes) {
        return String.format(Locale.GERMANY, "%,d MB", bytes / BYTES_PER_MB);
    }
}
