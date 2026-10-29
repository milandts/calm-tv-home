package app.calmtv.home;

import android.content.Context;
import android.widget.ScrollView;

/**
 * Die Rasterfläche der Startseite. Beim Ein- und Ausblenden zeichnet der Renderer sie sonst bei jedem Bild in eine Zwischenebene
 * (alle Kacheln samt Schatten, dann Ebene mit Deckkraft aufs Bild); das schafft ein Fire TV Stick nicht flüssig. Die Kacheln
 * überlappen sich nicht, darum darf die Deckkraft einzeln auf jede Zeichnung wirken.
 */
final class GridScroll extends ScrollView {
    GridScroll(Context context) {
        super(context);
        setClipChildren(false);
        setVerticalScrollBarEnabled(false);
    }

    @Override public boolean hasOverlappingRendering() {
        return false;
    }
}
