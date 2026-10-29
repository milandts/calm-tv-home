package app.calmtv.home;

import android.content.Context;
import android.graphics.Rect;
import android.widget.ScrollView;

/** Scrollt so, dass die fokussierte Kachel samt Titelzeile mit etwas Abstand sichtbar bleibt, und meldet jedes Scrollen. */
final class GalleryScroll extends ScrollView {
    private final int marginPx;
    private Runnable onScrolled;

    GalleryScroll(Context c) {
        super(c);
        marginPx = Ui.px(c, 24);
        setVerticalScrollBarEnabled(false);
        setOverScrollMode(OVER_SCROLL_NEVER);
        setFocusable(false);
        setClipToPadding(false);
        setClipChildren(false);
    }

    void setOnScrolled(Runnable r) {
        onScrolled = r;
    }

    @Override protected int computeScrollDeltaToGetChildRectOnScreen(Rect rect) {
        Rect wider = new Rect(rect);
        wider.top -= marginPx;
        wider.bottom += marginPx;
        return super.computeScrollDeltaToGetChildRectOnScreen(wider);
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        if (onScrolled != null) onScrolled.run();   // nach dem ersten Layout wissen die Kacheln, ob sie sichtbar sind
    }

    @Override protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);
        if (onScrolled != null) onScrolled.run();
    }
}
