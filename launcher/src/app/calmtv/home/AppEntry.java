package app.calmtv.home;

import android.content.Intent;
import android.content.pm.ActivityInfo;

/** Eine startbare App: Paketname, Name laut System, der Intent zum Starten und die Activity-Angaben (Banner, Symbol). */
final class AppEntry {
    final String pkg;
    final String label;
    final ActivityInfo info;
    final Intent launch;

    AppEntry(String pkg, String label, ActivityInfo info, Intent launch) {
        this.info = info;
        this.launch = launch;
        this.pkg = pkg;
        this.label = label;
    }
}
