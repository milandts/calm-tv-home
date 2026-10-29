package app.calmtv.home;

import android.app.ActivityOptions;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.util.Log;

/**
 * Die Wege, TV Home nach vorn zu holen. Android hält den Start fremder Apps nach einem HOME-Druck rund 5 Sekunden zurück; ein Alarm
 * ({@link PendingIntent}) wird vom System selbst gesendet und unterliegt dieser Sperre nicht. Er feuert sofort, wenn
 * {@code alarm_manager_constants min_futurity=0} gesetzt ist (siehe scripts/install.sh).
 */
final class HomeLauncher {
    private static final String QUICK_MENU = "com.amazon.device.settings.action.SHOW_HUD";

    private HomeLauncher() {}

    /** Derselbe Intent startet den Alarm und bricht ihn wieder ab. */
    private static PendingIntent alarmIntent(Context context) {
        Intent open = new Intent(Intent.ACTION_MAIN).setClass(context, HomeActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        return PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_UPDATE_CURRENT,
                ActivityOptions.makeCustomAnimation(context, 0, 0).toBundle());
    }

    /** Startet TV Home über einen Alarm, der sofort feuert. */
    static void viaAlarm(Context context) {
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarms.setExact(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime(), alarmIntent(context));
    }

    /** Verwirft einen Alarm-Start, der noch nicht gefeuert hat. Er feuert normalerweise sofort, die eigentliche Bremse für die Rückfälle sind die entfernten Handler-Aufrufe. */
    static void cancelAlarm(Context context) {
        ((AlarmManager) context.getSystemService(Context.ALARM_SERVICE)).cancel(alarmIntent(context));
    }

    /** Der direkte Weg über eine Anfrage an Android (unterliegt der 5-Sekunden-Sperre). */
    static void direct(Context context) {
        context.startActivity(new Intent(context, HomeActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
    }

    /** Amazons Schnellmenü (Standby, Spiegelung, Profile), das HOME bei langem Druck öffnet. */
    static void quickMenu(Context context) {
        try {
            context.startActivity(new Intent(QUICK_MENU).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException | SecurityException e) {
            Log.w(HomeKeyService.TAG, "quick menu unavailable", e);
        }
    }
}
