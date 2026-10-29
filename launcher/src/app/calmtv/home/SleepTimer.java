package app.calmtv.home;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;

/**
 * Einschlaf-Timer: gilt einmal (kein Dauerzustand) und läuft über einen Alarm, nicht über die Startseite. Nach Ablauf startet der
 * Bildschirmschoner oder, je nach {@link Opts#SLEEP_ACTION}, der Bildschirm geht aus (Bedienungshilfen-Aktion, siehe
 * {@link HomeKeyService#sleepNow}). Der Zustand liegt in den Preferences "sleep" (nicht in der Sicherung): ein Timer, der nach
 * einem Neustart des Sticks nicht mehr läuft, wird nie wiederhergestellt.
 */
final class SleepTimer {
    /** Auswahl in Minuten; Index 0 = aus. Die Beschriftungen stehen daneben, damit beide gleich lang bleiben. */
    static final int[] MINUTES = {0, 15, 30, 60};
    static final String[] LABELS = {"Aus", "15 Minuten", "30 Minuten", "60 Minuten"};

    private static final String PREFS = "sleep";
    private static final String KEY_END = "end";         // Ablaufzeit in SystemClock.elapsedRealtime()
    private static final String KEY_INDEX = "index";     // gewählter Eintrag aus MINUTES
    private static final long MILLIS_PER_MINUTE = 60_000L;
    private static final String DREAM_PACKAGE = "app.calmtv.home.dream";

    private SleepTimer() {}

    /** Der Alarm-Intent; derselbe dient zum Setzen, Abbrechen und Prüfen. */
    private static Intent alarmIntent(Context context) {
        return new Intent(context, SleepTimerReceiver.class);
    }

    private static PendingIntent alarmPending(Context context, int flags) {
        return PendingIntent.getBroadcast(context, 0, alarmIntent(context), flags);
    }

    /** Ob der Alarm wirklich noch eingetragen ist (nach einem Neustart des Sticks ist er weg). */
    private static boolean isScheduled(Context context) {
        return alarmPending(context, PendingIntent.FLAG_NO_CREATE) != null;
    }

    /** Stellt den Timer auf den Eintrag {@code index} aus {@link #MINUTES}; 0 schaltet ihn aus. */
    static void set(Context context, int index) {
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pending = alarmPending(context, PendingIntent.FLAG_UPDATE_CURRENT);
        alarms.cancel(pending);
        SharedPreferences.Editor edit = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
        if (index <= 0 || index >= MINUTES.length) {
            edit.clear().apply();
            return;
        }
        long end = SystemClock.elapsedRealtime() + MINUTES[index] * MILLIS_PER_MINUTE;
        if (Build.VERSION.SDK_INT >= 23) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, end, pending);
        } else {
            alarms.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, end, pending);
        }
        edit.putLong(KEY_END, end).putInt(KEY_INDEX, index).apply();
    }

    /** Der gewählte Eintrag, solange der Timer läuft; sonst 0 (aus). */
    static int selectedIndex(Context context) {
        if (remainingMillis(context) <= 0) return 0;
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_INDEX, 0);
    }

    /** Restzeit in Millisekunden; 0, wenn kein Timer läuft. */
    static long remainingMillis(Context context) {
        long end = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_END, 0);
        long left = end - SystemClock.elapsedRealtime();
        return left > 0 && isScheduled(context) ? left : 0;
    }

    /** "Noch 12 Minuten" (aufgerundet) oder "Aus". */
    static String describeRemaining(Context context) {
        long left = remainingMillis(context);
        if (left <= 0) return "Aus";
        long minutes = (left + MILLIS_PER_MINUTE - 1) / MILLIS_PER_MINUTE;
        return minutes == 1 ? "Noch 1 Minute" : "Noch " + minutes + " Minuten";
    }

    /** Läuft beim Alarm: Zustand löschen, dann Ruhezustand oder Bildschirmschoner (Rückfall, wenn der Ruhezustand nicht geht). */
    static void fire(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply();
        boolean asleep = false;
        // Startet der Alarm den Prozess neu, gibt es noch keine Config: dann gleich eine anlegen (die Startseite ersetzt sie später).
        Config config = Config.i() != null ? Config.i() : new Config(context);
        if (config.get(Opts.SLEEP_ACTION) == 1) asleep = HomeKeyService.sleepNow();
        if (!asleep) startSaver(context);
    }

    private static void startSaver(Context context) {
        Intent saver = new Intent().setClassName(DREAM_PACKAGE, DREAM_PACKAGE + ".PreviewActivity")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(saver);
        } catch (RuntimeException e) {
            Log.w(HomeKeyService.TAG, "sleep timer: no screensaver to start", e);
        }
    }
}
