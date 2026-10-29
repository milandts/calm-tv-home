package app.calmtv.home;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Nimmt den Alarm des Einschlaf-Timers an (Hauptthread, kurz). Nicht exportiert: nur TV Home selbst löst ihn aus. */
public class SleepTimerReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        SleepTimer.fire(context);
    }
}
