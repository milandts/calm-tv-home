package app.calmtv.home;

import android.content.ComponentCallbacks2;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import java.util.List;
import java.util.concurrent.Executor;

/**
 * Das Hintergrundbild der Startseite: laden, überblenden, nach dem Bildwechsel-Rhythmus tauschen, abdunkeln und Speicher
 * freigeben, solange eine andere App vorn ist. Die unscharfe Kopie des Bildes färbt außerdem das Glas, die Uhr und die Ansichten.
 */
final class HomeBackdrop {
    /** Fragt, ob gerade etwas Offenes (Raster, Suche, Einstellungen) vor dem Bild liegt; dann wartet der Wechsel. */
    interface Busy {
        boolean isBusy();
    }

    /** Wer sein Aussehen vom unscharfen Bild ableitet, aber kein {@link Glass} ist (die Ebenen hinter Suche und Raster). */
    interface BlurUser {
        void onBackdrop(Bitmap blurred);
    }

    private static final String TAG = HomeKeyService.TAG;
    private static final long CHECK_EVERY_MS = 5 * 60_000, CROSSFADE_MS = 1_500, FIRST_FADE_MS = 600;
    private static final long SIX_HOURS_MS = 6 * 60 * 60_000L, RELEASE_DELAY_MS = 3_000, OLD_PHOTO_GRACE_MS = 1_000;

    /** Unscharfe Kopie des Bildes auf dem Schirm; HomeKeyService zeigt sie, solange Amazons Startseite verlassen wird. */
    static volatile Bitmap coverImage;

    private final HomeActivity home;
    private final HomeScene scene;
    private final Wallpapers wallpapers;
    private final Look look;
    private final Handler ui;
    private final Executor worker;
    private final Busy busy;
    private final List<BlurUser> users = new java.util.ArrayList<>();
    private final Runnable rotate = this::rotateIfDue;
    private final Runnable release = this::releaseWhileHidden;

    private Wallpapers.Frame current;
    private boolean isStarted, isReleased;
    private long shownDay = -1;   // Tagesnummer des Bildes auf dem Schirm
    private long shownAt;         // elapsedRealtime, seit dem das Bild zu sehen ist

    HomeBackdrop(HomeActivity home, HomeScene scene, Wallpapers wallpapers, Look look, Handler ui, Executor worker, Busy busy) {
        this.home = home;
        this.scene = scene;
        this.wallpapers = wallpapers;
        this.look = look;
        this.ui = ui;
        this.worker = worker;
        this.busy = busy;
    }

    void addBlurUser(BlurUser user) {
        users.add(user);
        if (current != null) user.onBackdrop(current.blur);
    }

    void removeBlurUser(BlurUser user) {
        users.remove(user);
    }

    Bitmap blurred() {
        return current != null ? current.blur : null;
    }

    String credit() {
        return current != null ? current.credit : "";
    }

    List<String> credits() {
        return wallpapers.credits();
    }

    // ---- Lebenszyklus ----------------------------------------------------------------------------

    /** Die Startseite wird sichtbar: fehlt das Bild oder wurde es freigegeben, neu laden; sonst nur laden, wenn der Rhythmus es verlangt. */
    void onShown() {
        isStarted = true;
        ui.removeCallbacks(release);
        if (current == null || isReleased) {
            isReleased = false;
            load(false);
        } else if (isDue(true)) {
            load(false);
        } else {
            ui.postDelayed(rotate, CHECK_EVERY_MS);
        }
    }

    /** Eine andere App kommt nach vorn: nach kurzer Frist das große Bild und den Grafikspeicher zurückgeben. */
    void onHidden() {
        isStarted = false;
        ui.removeCallbacks(rotate);
        ui.postDelayed(release, RELEASE_DELAY_MS);
    }

    // ---- Laden und Wechseln ----------------------------------------------------------------------

    /** Ein von Hand gewähltes Bild; es gilt bis Mitternacht. */
    void pickNext() {
        load(true);
    }

    private void rotateIfDue() {
        if (busy.isBusy() || !isDue(false)) ui.postDelayed(rotate, CHECK_EVERY_MS);
        else load(false);
    }

    private void load(final boolean byHand) {
        ui.removeCallbacks(rotate);
        final int width = Metrics.screenWidth(home), height = Metrics.screenHeight(home);
        final long day = Wallpapers.dayNumber();   // Tag, an dem das Laden begann, falls es über Mitternacht läuft
        worker.execute(() -> {
            final Wallpapers.Frame frame = byHand ? wallpapers.next(width, height) : wallpapers.today(width, height);
            ui.post(() -> {
                if (frame != null && !isStarted) {
                    // Zu spät fertig: niemand schaut mehr hin, also keine 8 MB halten; der nächste Start lädt neu.
                    if (!frame.full.isRecycled()) frame.full.recycle();
                } else if (frame != null) {
                    show(frame);
                    shownDay = day;
                }
                if (isStarted) ui.postDelayed(rotate, CHECK_EVERY_MS);
            });
        });
    }

    /** Ist ein neues Bild fällig? Nach "Bildwechsel": täglich, bei jedem Heimkommen, alle 6 Stunden oder nie. */
    private boolean isDue(boolean comingHome) {
        switch (Config.val(Opts.BG_RHYTHM)) {
            case 0: return Wallpapers.dayNumber() != shownDay;
            case 1: return comingHome;
            case 2: return SystemClock.elapsedRealtime() - shownAt >= SIX_HOURS_MS;
            default: return false;
        }
    }

    private Bitmap backdropOf(Wallpapers.Frame frame) {
        return look.isBlurred() ? frame.blur : frame.full;
    }

    /** Übernimmt Abdunkeln und Unschärfe sofort (und die Farbe bei "Automatisch"). */
    void applyLook() {
        if (look.isAuto() && current != null) look.refreshAuto(current.blur);
        int alpha = look.dimAlpha();
        scene.wallDim.setAlpha(alpha / 255f);
        scene.wallDim.setVisibility(alpha == 0 ? View.GONE : View.VISIBLE);   // ohne Abdunkeln keine volle Fläche zeichnen
        if (current != null && !isReleased) scene.wallBottom.setImageBitmap(backdropOf(current));
    }

    private void show(final Wallpapers.Frame frame) {
        shownAt = SystemClock.elapsedRealtime();
        coverImage = frame.blur;
        look.refreshAuto(frame.blur);   // auch ohne "Automatisch" merken, damit die Farbe beim Wählen sofort passt
        scene.libraryBg.setImageBitmap(frame.blur);
        if (current == null) {   // erstes Bild: einblenden, Glas sofort einfärben
            current = frame;
            scene.wallBottom.setImageBitmap(backdropOf(frame));
            scene.wallBottom.setAlpha(0f);
            scene.wallBottom.animate().alpha(1f).setDuration(Ui.ms(FIRST_FADE_MS)).start();
            tint(frame.blur, 0);
            return;
        }
        final Wallpapers.Frame previous = current;
        current = frame;
        ImageView top = scene.wallTop;
        top.setImageBitmap(backdropOf(frame));
        top.setAlpha(0f);
        // Keine Hardware-Ebene: Auf einem Stick mit 1 GB RAM kostet sie 8 MB für eine Überblendung von 1,5 s.
        top.animate().alpha(1f).setDuration(Ui.ms(CROSSFADE_MS)).withEndAction(() -> {
            scene.wallBottom.setImageBitmap(backdropOf(frame));
            top.setImageDrawable(null);
            top.setAlpha(0f);
            // Das alte Bild erst freigeben, wenn der Renderer sicher damit fertig ist.
            ui.postDelayed(() -> {
                if (previous.full != current.full && !previous.full.isRecycled()) previous.full.recycle();
                trimGraphics(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN);
            }, OLD_PHOTO_GRACE_MS);
        }).start();
        tint(frame.blur, Ui.ms(CROSSFADE_MS));
    }

    private void tint(Bitmap blurred, long fadeMs) {
        for (Glass glass : scene.glass) glass.setBlur(blurred, fadeMs);
        scene.header.clock().setBlur(blurred, fadeMs);
        for (BlurUser user : users) user.onBackdrop(blurred);
    }

    // ---- Speicher --------------------------------------------------------------------------------

    /**
     * Solange eine App vorn ist, gehen das große Bild und der Grafikspeicher (auf dem Stick etwa 25 MB) zurück;
     * beim Heimkommen erscheint zuerst die unscharfe Kopie, dann blendet das nächste Foto ein.
     */
    private void releaseWhileHidden() {
        if (isStarted || current == null) return;
        scene.wallTop.animate().cancel();
        scene.wallTop.setImageDrawable(null);
        scene.wallBottom.setImageBitmap(current.blur);
        if (!current.full.isRecycled()) current.full.recycle();
        isReleased = true;
        trimGraphics(ComponentCallbacks2.TRIM_MEMORY_COMPLETE);   // was Android selbst mit Hintergrund-Apps tut
    }

    /**
     * Bittet den Renderer, zwischengespeicherten Grafikspeicher abzugeben. UI_HIDDEN lässt nur los, was nicht zu sehen ist;
     * ohne das blieben nach jedem Bildwechsel 30 bis 40 MB alter Texturen liegen.
     */
    private static void trimGraphics(int level) {
        try {
            Class<?> globals = Class.forName("android.view.WindowManagerGlobal");
            Object instance = globals.getMethod("getInstance").invoke(null);
            globals.getMethod("trimMemory", int.class).invoke(instance, level);
        } catch (ReflectiveOperationException | RuntimeException e) {
            Log.w(TAG, "can't release graphics memory", e);
        }
    }
}
