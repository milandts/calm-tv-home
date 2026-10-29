package app.calmtv.home.dream;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

/**
 * Holt das Wetter auf Wunsch alle 2 Stunden im Hintergrund (Einstellung "Auffrischen" = alle 2 Stunden), damit TV Home es auf der Startseite
 * aktuell zeigen kann. Standard ist aus: dann wird nur geholt, wenn der Bildschirmschoner läuft.
 *
 * <p>Es gibt genau eine Stelle, die den Zeitplan anlegt ({@link #sync}) und genau eine, die abruft ({@link Job}); beide prüfen dieselben
 * Bedingungen: Auswahl "alle 2 Stunden", Wetter eingeschaltet und Stadt gewählt. Sonst wird der Plan gelöscht und nichts abgerufen.
 */
final class WeatherRefresh {
    private static final int JOB_ID = 2001;
    private static final long PERIOD_MS = 2 * 60 * 60 * 1000L;
    /** Wert von {@link AmbientSettings#KEY_REFRESH} für "alle 2 Stunden". */
    static final int EVERY_TWO_HOURS = 1;

    private WeatherRefresh() {}

    /** Ob im Hintergrund geholt werden darf. */
    static boolean isWanted(Context context) {
        return AmbientSettings.getInt(context, AmbientSettings.KEY_REFRESH, AmbientSettings.DEFAULT_REFRESH) == EVERY_TWO_HOURS
                && AmbientSettings.get(context, AmbientSettings.KEY_WEATHER) && Weather.isConfigured(context);
    }

    /** Legt den Zeitplan an oder löscht ihn, je nach Einstellung; billig und darf oft laufen (zum Beispiel bei jeder Abfrage von TV Home). */
    static void sync(Context context) {
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler == null) return;
        if (!isWanted(context)) {
            if (scheduler.getPendingJob(JOB_ID) != null) scheduler.cancel(JOB_ID);
            return;
        }
        if (scheduler.getPendingJob(JOB_ID) != null) return;
        scheduler.schedule(new JobInfo.Builder(JOB_ID, new ComponentName(context, Job.class))
                .setPeriodic(PERIOD_MS)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .build());
    }

    /** Der geplante Abruf. */
    public static final class Job extends JobService {
        @Override public boolean onStartJob(final JobParameters params) {
            if (!isWanted(this)) {   // Einstellung inzwischen geändert: nichts abrufen, Plan löschen
                sync(this);
                return false;
            }
            new Thread(() -> {
                Weather.fetch(this);
                jobFinished(params, false);
            }, "tvhome-weather-refresh").start();
            return true;
        }

        @Override public boolean onStopJob(JobParameters params) {
            return false;
        }
    }
}
