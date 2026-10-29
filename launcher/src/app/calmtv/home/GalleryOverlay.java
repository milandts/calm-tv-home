package app.calmtv.home;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Outline;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * Grundlage der beiden Unteransichten der Galerie (Ausschnitt, Helligkeit): Vollbild über der Galerie, Titel und Hinweis,
 * Pfeiltasten gehören dem Inhalt, Auswahltaste speichert, Zurück (über die Galerie) verwirft. Das Bild kommt klein und
 * asynchron.
 */
abstract class GalleryOverlay extends FrameLayout {
    /** Wird einmal aufgerufen, wenn die Ansicht fertig ist. */
    interface Done {
        void onDone(boolean saved);
    }

    /** Rundet ein Bild ab (Radius wie die Kacheln). */
    static final class RoundOutline extends ViewOutlineProvider {
        private final float radius;

        RoundOutline(float radius) {
            this.radius = radius;
        }

        @Override public void getOutline(View view, Outline outline) {
            outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
        }
    }

    static final int LEFT_DP = 64;
    private static final int OVERVIEW_W = 800, OVERVIEW_H = 450;

    protected final Wallpapers.Entry entry;
    private final Done done;
    private boolean finished;

    GalleryOverlay(Context c, Wallpapers.Entry entry, String title, String hint, Done done) {
        super(c);
        this.entry = entry;
        this.done = done;
        setBackgroundColor(Ui.panel(0xF2101014));
        setFocusable(true);
        setClickable(true);

        TextView heading = Ui.label(c, title, 30, 300, Ui.TEXT);
        LayoutParams hp = new LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        hp.setMargins(Ui.px(c, LEFT_DP), Ui.px(c, 34), 0, 0);
        addView(heading, hp);

        TextView note = Ui.label(c, hint, Ui.sp(Ui.SP_BODY), 500, Ui.TEXT_DIM);
        LayoutParams np = new LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.START);
        np.setMargins(Ui.px(c, LEFT_DP), 0, Ui.px(c, LEFT_DP), Ui.px(c, 26));
        addView(note, np);
    }

    /** Lädt die kleine Fassung des ganzen Bildes im Hintergrund und übergibt sie im Hauptthread. */
    final void loadImage(Wallpapers wallpapers, GalleryThumbs thumbs) {
        String id = entry.id;
        thumbs.submit(() -> {
            Bitmap b = wallpapers.overview(id, OVERVIEW_W, OVERVIEW_H);
            thumbs.post(() -> {
                if (b != null && isAttachedToWindow() && !finished) onImage(b);
            });
        });
    }

    protected abstract void onImage(Bitmap image);

    /** Eine Pfeiltaste (Wiederholung ab 0). */
    protected abstract void onArrow(int keyCode, int repeat);

    /** Auswahltaste: übernehmen. */
    protected abstract void onSave();

    final void finish(boolean saved) {
        if (finished) return;
        finished = true;
        if (saved) onSave();
        done.onDone(saved);
    }

    @Override public final boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                onArrow(keyCode, event.getRepeatCount());
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_BUTTON_A:
                if (event.getRepeatCount() == 0) finish(true);
                return true;
            default:
                return super.onKeyDown(keyCode, event);
        }
    }

    /** Beim Entfernen: Bild und Rückrufe loslassen. */
    void release() {
        finished = true;
    }
}
