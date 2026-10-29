package app.calmtv.home;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

/**
 * Die Wetterzeile unter dem Datum. TV Home hat kein Internet: Gelesen wird nur der letzte Stand, den der
 * Bildschirmschoner (Paket app.calmtv.home.dream) bereithält. Ohne gewählte Stadt, ohne den Bildschirmschoner oder bei
 * zu altem Stand bleibt die Zeile weg.
 *
 * <p>Der Provider kommt nur in Frage, wenn er zum Paket des Bildschirmschoners gehört und mit demselben Schlüssel
 * signiert ist wie TV Home; sonst könnte eine fremde App unter derselben Adresse beliebigen Text auf die Startseite bringen.
 * Die Abfrage startet den Prozess des Bildschirmschoners und läuft darum nie auf dem Hauptthread und höchstens alle paar Minuten.
 */
final class HomeWeather {
    private static final String TAG = "TVHome";
    private static final String DREAM_PACKAGE = "app.calmtv.home.dream", AUTHORITY = "app.calmtv.home.dream.weather";
    private static final Uri URI = Uri.parse("content://" + AUTHORITY + "/current");
    /** Wie lange ein Stand angezeigt wird; derselbe Wert wie im Bildschirmschoner (Weather.FRESH_MS). */
    private static final long MAX_AGE_MS = 3 * 60 * 60 * 1000L;
    /** Mindestabstand zwischen zwei Abfragen; der Stand ändert sich nur, wenn der Bildschirmschoner lief. */
    private static final long MIN_GAP_MS = 5 * 60 * 1000L;

    private final Context context;
    private final TextView view;
    private String text = "";
    private long askedAt = -MIN_GAP_MS;
    private boolean isAsking;

    HomeWeather(Context context, TextView view) {
        this.context = context;
        this.view = view;
    }

    /** Fragt den letzten Stand ab (im Hintergrund), wenn die letzte Abfrage lange genug her ist oder {@code force} gesetzt ist. */
    void refresh(boolean force) {
        long now = SystemClock.elapsedRealtime();
        if (isAsking || Config.val(Opts.SHOW_WEATHER) == 0 || (!force && now - askedAt < MIN_GAP_MS)) return;
        isAsking = true;
        askedAt = now;
        new Thread(() -> {
            final String found = read();
            view.post(() -> {
                isAsking = false;
                text = found;
                apply();
            });
        }, "tvhome-weather").start();
    }

    /** Zeigt oder versteckt die Zeile gemäß Einstellung und letztem Stand. */
    void apply() {
        boolean isShown = Config.val(Opts.SHOW_WEATHER) != 0 && !text.isEmpty();
        view.setText(text);
        view.setVisibility(isShown ? View.VISIBLE : View.GONE);
    }

    /** Liest die eine Zeile; leerer Text, wenn es keinen brauchbaren Stand gibt. Läuft im Hintergrund. */
    private String read() {
        if (!isTrustedProvider()) return "";
        try (Cursor c = context.getContentResolver().query(URI, null, null, null, null)) {
            if (c == null || !c.moveToFirst()) return "";
            long age = System.currentTimeMillis() - c.getLong(c.getColumnIndexOrThrow("time"));
            if (age < 0 || age > MAX_AGE_MS) return "";
            String temperature = c.getString(c.getColumnIndexOrThrow("temperature"));
            String description = c.getString(c.getColumnIndexOrThrow("description"));
            if (temperature == null || temperature.isEmpty()) return "";
            return description == null || description.isEmpty() ? temperature : temperature + "  ·  " + description;
        } catch (RuntimeException e) {   // Provider fehlt, Zugriff verweigert oder unerwartete Spalten: dann einfach keine Zeile
            Log.w(TAG, "weather not available", e);
            return "";
        }
    }

    private boolean isTrustedProvider() {
        PackageManager pm = context.getPackageManager();
        ProviderInfo info = pm.resolveContentProvider(AUTHORITY, 0);
        return info != null && DREAM_PACKAGE.equals(info.packageName)
                && pm.checkSignatures(context.getPackageName(), DREAM_PACKAGE) == PackageManager.SIGNATURE_MATCH;
    }
}
