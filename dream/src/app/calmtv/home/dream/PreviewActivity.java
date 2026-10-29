package app.calmtv.home.dream;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.WindowManager;

/**
 * Zeigt den Bildschirmschoner sofort (Kachel in TV Home, Zeile "Vorschau" in den Einstellungen). Jede Taste beendet ihn.
 * Optionales Intent-Extra "night" (boolean): zeigt den Nachtmodus auch tagsüber, falls er eingeschaltet ist.
 */
public final class PreviewActivity extends Activity {
    private AmbientView view;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        view = new AmbientView(this);
        view.setForcedNight(getIntent().getBooleanExtra("night", false));
        setContentView(view);
    }

    @Override
    protected void onStart() {
        super.onStart();
        view.start();
    }

    @Override
    protected void onStop() {
        super.onStop();
        view.stop();
        finish();   // ein beendeter Bildschirmschoner soll nicht im Hintergrund weiterlaufen
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        finish();
        return true;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        finish();
        return true;
    }
}
