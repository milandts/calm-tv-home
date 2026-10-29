package app.calmtv.home.dream;

import android.service.dreams.DreamService;

/** Der Bildschirmschoner des Systems: zeigt die {@link AmbientView}, bis eine Taste gedrückt wird. */
public final class AmbientDream extends DreamService {
    private AmbientView view;

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);   // jede Taste beendet den Bildschirmschoner
        setFullscreen(true);
        view = new AmbientView(this);
        setContentView(view);
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        if (view != null) view.start();
    }

    @Override
    public void onDreamingStopped() {
        super.onDreamingStopped();
        if (view != null) view.stop();
    }

    @Override
    public void onDetachedFromWindow() {
        if (view != null) view.stop();
        super.onDetachedFromWindow();
    }
}
