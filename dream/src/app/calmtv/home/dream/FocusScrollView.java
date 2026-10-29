package app.calmtv.home.dream;

import android.content.Context;
import android.graphics.Rect;
import android.view.View;
import android.widget.ScrollView;

/** ScrollView, die der Fokus-Zeile beim Nachführen etwas Luft zum Rand lässt (damit die nächste Zeile schon sichtbar ist). */
final class FocusScrollView extends ScrollView {
    private final int marginPx;

    FocusScrollView(Context context, int marginPx) {
        super(context);
        this.marginPx = marginPx;
        setVerticalScrollBarEnabled(false);
        setFadingEdgeLength(marginPx);
        setVerticalFadingEdgeEnabled(true);
    }

    @Override
    public boolean requestChildRectangleOnScreen(View child, Rect rectangle, boolean immediate) {
        Rect wider = new Rect(rectangle.left, rectangle.top - marginPx, rectangle.right, rectangle.bottom + marginPx);
        return super.requestChildRectangleOnScreen(child, wider, immediate);
    }
}
