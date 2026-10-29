package app.calmtv.home.dream;

import android.content.Context;
import android.view.View;

/** Eine Fläche ohne Inhalt. Meldet keine sich überlappenden Inhalte, damit Alpha direkt mitgezeichnet wird und keine Zwischenebene (Layer) entsteht. */
final class FlatView extends View {
    FlatView(Context context) {
        super(context);
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }
}
