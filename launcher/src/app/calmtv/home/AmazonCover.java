package app.calmtv.home;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;

/**
 * Ein Vollbild-Foto über Amazons Startseite, solange der Wechsel zu TV Home läuft: Es zeigt die unscharfe Kopie des Hintergrunds,
 * damit Amazons Werbung nie zu sehen ist. Liegt als Bedienungshilfen-Fenster über allem und nimmt weder Fokus noch Berührung an.
 */
final class AmazonCover {
    private final Context context;
    private View view;

    AmazonCover(Context context) {
        this.context = context;
    }

    boolean isShown() {
        return view != null;
    }

    void show() {
        if (view != null) return;
        ImageView picture = new ImageView(context);
        picture.setScaleType(ImageView.ScaleType.CENTER_CROP);
        picture.setBackgroundColor(Color.BLACK);
        Bitmap wallpaper = HomeBackdrop.coverImage;
        if (wallpaper != null) picture.setImageBitmap(wallpaper);
        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_FULLSCREEN;
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, flags, PixelFormat.OPAQUE);
        try {
            windows().addView(picture, params);
            view = picture;
        } catch (RuntimeException e) {
            Log.w(HomeKeyService.TAG, "can't show cover", e);
        }
    }

    void hide() {
        if (view == null) return;
        try {
            windows().removeViewImmediate(view);
        } catch (RuntimeException e) {
            Log.w(HomeKeyService.TAG, "can't remove cover", e);
        }
        view = null;
    }

    private WindowManager windows() {
        return (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    }
}
