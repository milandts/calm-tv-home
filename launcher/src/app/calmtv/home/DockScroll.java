package app.calmtv.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.widget.HorizontalScrollView;

/**
 * Die waagerechte Rolle des Docks. Android rollt gerade so weit, dass die Kante der fokussierten Kachel sichtbar ist; dann
 * wäre das Anheben der letzten Kachel angeschnitten. Mit {@code margin} bleibt der Innenabstand der Reihe mit im Bild.
 */
final class DockScroll extends HorizontalScrollView {
    private final int margin;

    DockScroll(Context context, int margin) {
        super(context);
        this.margin = margin;
    }

    /** Beschneidet auf die eigene Fläche; das Glas selbst darf nicht beschneiden, weil der Kanten-Hinweis über seinen Rand ragt. */
    @Override protected void dispatchDraw(Canvas canvas) {
        canvas.save();
        canvas.clipRect(getScrollX(), 0, getScrollX() + getWidth(), getHeight());
        super.dispatchDraw(canvas);
        canvas.restore();
    }

    @Override protected int computeScrollDeltaToGetChildRectOnScreen(Rect rect) {
        Rect widened = new Rect(rect);
        widened.inset(-margin, 0);
        return super.computeScrollDeltaToGetChildRectOnScreen(widened);
    }
}
