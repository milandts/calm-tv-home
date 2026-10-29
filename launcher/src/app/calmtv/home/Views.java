package app.calmtv.home;

import android.content.Context;
import android.widget.LinearLayout;
import android.widget.ScrollView;

/** Kleine Bausteine, die der Aufbau der Oberfläche immer wieder braucht. Alle lassen fokussierte Kinder über ihren Rand wachsen. */
final class Views {
    private Views() {}

    /** Eine Spalte oder Zeile ohne Beschneiden (eine fokussierte Kachel wächst über die Reihe hinaus). */
    static LinearLayout linear(Context context, int orientation) {
        LinearLayout box = new LinearLayout(context);
        box.setClipChildren(false);
        box.setClipToPadding(false);
        box.setOrientation(orientation);
        return box;
    }

    /** Senkrecht scrollbare Fläche ohne Balken und ohne Beschneiden. */
    static ScrollView scroller(Context context) {
        ScrollView scroll = new ScrollView(context);
        scroll.setClipToPadding(false);
        scroll.setClipChildren(false);
        scroll.setVerticalScrollBarEnabled(false);
        return scroll;
    }
}
