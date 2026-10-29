package app.calmtv.home.dream;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.TimeInterpolator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewPropertyAnimator;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.os.Handler;
import android.os.Looper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Die Bühne des Bildschirmschoners: Naturfotos mit langsamer Kamerafahrt (Zoom und Schwenk), weicher Überblendung,
 * dazu Uhr, Datum und Wetter, die bei jedem Foto in eine andere Ecke wandern. Wird vom Dienst und von der
 * Vorschau-Activity benutzt. Zwei Bilder liegen übereinander; das neue wird über dem alten eingeblendet.
 * Nachts (23 bis 6 Uhr) wird alles abgedunkelt und die Kamerafahrt halb so schnell.
 */
final class AmbientView extends FrameLayout {
    private static final String TAG = "TVHomeDream";
    private static final String PHOTO_DIR = "photos";
    private static final long FADE_MS = 3_000, FIRST_FADE_MS = 1_800, INFO_FADE_MS = 700, RETRY_MS = 1_500;
    private static final long WEATHER_REFRESH_MS = 30 * 60_000L, STALE_MS = 4 * 60 * 60_000L;
    /** Wartezeiten nach fehlgeschlagenen Wetterabrufen (offline): erst schnell, dann seltener. */
    private static final long[] WEATHER_RETRY_MS = {30_000L, 2 * 60_000L, 5 * 60_000L};
    /** Die Kamerafahrt zoomt bis 12 % und verschiebt höchstens 5 % der Bildgröße; so bleibt nie ein Rand sichtbar. */
    private static final float[] ZOOM_LEVELS = {1.06f, 1.12f, 1.18f};   // Sanft, Normal, Kräftig
    /** Die Verschiebung ist proportional zum Zoom (Normal: 5 % bei 12 %) und bleibt unter der Hälfte des Zooms: so wird nie ein Rand sichtbar. */
    private static final float DRIFT_PER_ZOOM = 0.05f / 0.12f;
    private static final float SCRIM_DP = 400;
    private static final float NIGHT_SLOWDOWN = 2f;     // Nachts doppelt so lange pro Foto, also halbe Geschwindigkeit
    private static final long NIGHT_FADE_MS = 20_000;
    private static final int[] CORNERS = {
            Gravity.BOTTOM | Gravity.START, Gravity.TOP | Gravity.END,
            Gravity.BOTTOM | Gravity.END, Gravity.TOP | Gravity.START};
    // Gleiche Reihenfolge wie CORNERS. (Mit Gravity-Bits lässt sich das nicht prüfen: START, END, TOP und BOTTOM teilen Bits.)
    private static final boolean[] CORNER_RIGHT = {false, true, true, false};
    private static final boolean[] CORNER_TOP = {false, true, false, true};
    private static final TimeInterpolator SMOOTH = new Smooth();

    private final Handler ui = new Handler(Looper.getMainLooper());
    private ExecutorService worker;          // Fotos laden (in start() angelegt, in stop() beendet)
    private ExecutorService weatherWorker;   // eigener Thread: ein hängender Wetterabruf darf kein Foto aufhalten
    private final Random random = new Random();
    private final ImageView[] slides = new ImageView[2];
    private final Bitmap[] bitmaps = new Bitmap[2];
    private final ObjectAnimator[] motion = new ObjectAnimator[2];
    private final List<String> photos = new ArrayList<>();
    private final FrameLayout photoStack;
    private final InfoPanel info;
    private final View dimmer;
    private final ImageView scrim;
    private String datePattern;
    private int zoomLevel = AmbientSettings.DEFAULT_MOTION_LEVEL, fixedCorner = AmbientSettings.CORNER_WANDER;
    private int timeFormat, nightFrom, nightTo;
    private long showMs = 24_000;
    private float nightDim = 0.55f;   // Anteil, den der Nachtmodus abdunkelt (1 minus gewünschte Helligkeit)
    private int shown = -1;      // Platz des Fotos im Vordergrund, -1 solange noch keins da ist
    private int nextPhoto;
    private int corner;
    private int generation;      // zählt die Starts; verspätete Rückmeldungen früherer Läufe werden ignoriert
    private int weatherFailures;
    private boolean running;
    private boolean isMotionOn = true, isNightEnabled = true, isWeatherOn = true, isForcedNight;
    private boolean isNightApplied;
    private Weather.Report report;

    private final Runnable advance = this::showNextPhoto;
    private final Runnable tick = this::updateClock;
    private final Runnable refreshWeather = this::loadWeather;

    AmbientView(Context context) {
        super(context);
        setBackgroundColor(Color.BLACK);
        setClipChildren(true);

        // Die zwei Fotos liegen in einem eigenen Container; das neue kommt per bringToFront nach oben.
        // (Ohne translationZ, das ohne Hardware-Beschleunigung nichts bewirkt.) Schatten und Angaben liegen darüber.
        photoStack = new FrameLayout(context);
        for (int i = 0; i < 2; i++) {
            slides[i] = new ImageView(context);
            slides[i].setScaleType(ImageView.ScaleType.CENTER_CROP);
            slides[i].setAlpha(0f);
            photoStack.addView(slides[i], new LayoutParams(-1, -1));
        }
        addView(photoStack, new LayoutParams(-1, -1));

        // Ein weicher Schatten nur in der Ecke, in der die Angaben stehen (statt zwei Streifen über die ganze Breite):
        // spart pro Bild rund zwei Drittel der Füllrate, die der Stick beim Überblenden braucht.
        scrim = new ImageView(context);
        scrim.setScaleType(ImageView.ScaleType.FIT_XY);
        scrim.setImageBitmap(CornerScrim.create());
        scrim.setAlpha(0f);
        mirrorScrim();
        addView(scrim, new LayoutParams(px(SCRIM_DP), px(SCRIM_DP), CORNERS[0]));

        info = new InfoPanel(context);
        info.setAlpha(0f);   // erscheint erst, wenn das erste Foto sichtbar wird
        LayoutParams infoParams = new LayoutParams(-2, -2, CORNERS[0]);
        infoParams.setMargins(px(64), px(48), px(64), px(48));
        addView(info, infoParams);

        // Liegt ganz oben und dunkelt alles ab (Nachtmodus).
        dimmer = new FlatView(context);
        dimmer.setBackgroundColor(Color.BLACK);
        dimmer.setAlpha(0f);
        addView(dimmer, new LayoutParams(-1, -1));

        datePattern = datePatternFor(0);
        try {
            String[] files = context.getAssets().list(PHOTO_DIR);
            if (files != null) for (String f : files) if (f.endsWith(".jpg") || f.endsWith(".webp")) photos.add(PHOTO_DIR + "/" + f);
        } catch (IOException e) {
            Log.w(TAG, "Fotos nicht lesbar", e);
        }
        Collections.shuffle(photos, random);
        updateClock();
    }

    /** Nur für die Vorschau des Nachtmodus: tut so, als wäre es Nacht (gilt nur, wenn der Nachtmodus an ist). */
    void setForcedNight(boolean isForced) {
        isForcedNight = isForced;
    }

    /** Beginnt mit Fotos, Uhr und Wetter. Die Schalter werden hier gelesen. */
    void start() {
        if (running) return;
        running = true;
        generation++;
        Context context = getContext();
        isMotionOn = AmbientSettings.get(context, AmbientSettings.KEY_MOTION);
        isNightEnabled = AmbientSettings.get(context, AmbientSettings.KEY_NIGHT);
        isWeatherOn = AmbientSettings.get(context, AmbientSettings.KEY_WEATHER);
        readSettings(context);
        worker = Executors.newSingleThreadExecutor();
        isNightApplied = isNightMode();
        dimmer.setAlpha(isNightApplied ? nightDim : 0f);
        updateClock();
        showNextPhoto();
        startWeather();
    }

    /** Hält alles an und gibt die Bilder frei. */
    void stop() {
        running = false;
        generation++;
        ui.removeCallbacksAndMessages(null);
        if (worker != null) worker.shutdownNow();
        if (weatherWorker != null) weatherWorker.shutdownNow();
        worker = null;
        weatherWorker = null;
        for (int i = 0; i < 2; i++) {
            if (motion[i] != null) motion[i].cancel();
            slides[i].animate().cancel();
            slides[i].setImageDrawable(null);
            slides[i].setAlpha(0f);
            bitmaps[i] = null;   // nicht recyceln: der Renderer könnte das Bild noch halten, die Müllabfuhr räumt auf
        }
        shown = -1;
        info.animate().cancel();
        scrim.animate().cancel();
        info.setAlpha(0f);       // ein erneuter Start blendet wieder aus Schwarz ein
        scrim.setAlpha(0f);
        dimmer.animate().cancel();
    }

    // ---- Fotos -----------------------------------------------------------------------------

    private void showNextPhoto() {
        if (!running) return;
        if (photos.isEmpty()) {
            fadeInInfo(0);
            return;
        }
        if (nextPhoto > 0 && nextPhoto % photos.size() == 0) Collections.shuffle(photos, random);   // neue Reihenfolge
        final String file = photos.get(nextPhoto++ % photos.size());
        final int slot = shown < 0 ? 0 : 1 - shown;
        final int myGeneration = generation;
        worker.execute(() -> {
            final Bitmap bitmap = decode(file);
            ui.post(() -> {
                if (!running || myGeneration != generation) {
                    if (bitmap != null) bitmap.recycle();
                    return;
                }
                if (bitmap == null) {
                    ui.postDelayed(advance, RETRY_MS);   // nächstes Foto versuchen
                    return;
                }
                reveal(slot, bitmap);
                ui.postDelayed(advance, holdMs());
            });
        });
    }

    private Bitmap decode(String path) {
        try (InputStream in = getContext().getAssets().open(path)) {
            Bitmap bitmap = BitmapFactory.decodeStream(in);
            if (bitmap != null) bitmap.prepareToDraw();   // lädt die Textur schon hier, damit das Einblenden nicht stockt
            return bitmap;
        } catch (IOException | OutOfMemoryError e) {
            Log.w(TAG, "Foto nicht ladbar: " + path);
            return null;
        }
    }

    private long holdMs() {
        return isNightApplied ? (long) (showMs * NIGHT_SLOWDOWN) : showMs;
    }

    /** Blendet das neue Foto im Platz {@code slot} über dem alten ein und startet seine Kamerafahrt. */
    private void reveal(final int slot, Bitmap bitmap) {
        final ImageView in = slides[slot];
        final int old = shown;
        if (motion[slot] != null) motion[slot].cancel();
        releaseBitmap(slot);
        bitmaps[slot] = bitmap;
        in.setImageBitmap(bitmap);
        startMotion(slot);

        // Das neue Bild kommt nach oben und wird eingeblendet, das alte bleibt darunter stehen.
        in.setAlpha(0f);
        in.bringToFront();
        in.animate().alpha(1f).setDuration(old < 0 ? FIRST_FADE_MS : FADE_MS).setInterpolator(SMOOTH)
                .withEndAction(() -> hideOld(old)).start();
        shown = slot;
        if (old >= 0) moveInfo();   // beim ersten Foto steht die Ecke schon
        else fadeInInfo(FIRST_FADE_MS / 2);
    }

    /** Kamerafahrt: hineinzoomen oder herauszoomen, mit leichter Verschiebung. Ohne Kamerafahrt steht das Foto still. */
    private void startMotion(int slot) {
        ImageView in = slides[slot];
        if (!isMotionOn) {
            in.setScaleX(1f);
            in.setScaleY(1f);
            in.setTranslationX(0f);
            in.setTranslationY(0f);
            return;
        }
        boolean zoomIn = random.nextBoolean();
        float zoom = ZOOM_LEVELS[zoomLevel], drift = (zoom - 1f) * DRIFT_PER_ZOOM;
        float dx = (random.nextFloat() * 2 - 1) * drift * (getWidth() > 0 ? getWidth() : 1920);
        float dy = (random.nextFloat() * 2 - 1) * drift * (getHeight() > 0 ? getHeight() : 1080);
        float s0 = zoomIn ? 1f : zoom, s1 = zoomIn ? zoom : 1f;
        float x0 = zoomIn ? 0 : dx, x1 = zoomIn ? dx : 0, y0 = zoomIn ? 0 : dy, y1 = zoomIn ? dy : 0;
        in.setScaleX(s0);
        in.setScaleY(s0);
        in.setTranslationX(x0);
        in.setTranslationY(y0);
        motion[slot] = ObjectAnimator.ofPropertyValuesHolder(in,
                PropertyValuesHolder.ofFloat(View.SCALE_X, s0, s1),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, s0, s1),
                PropertyValuesHolder.ofFloat(View.TRANSLATION_X, x0, x1),
                PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, y0, y1));
        motion[slot].setInterpolator(new LinearInterpolator());
        motion[slot].setDuration(holdMs() + FADE_MS + 1_000);
        motion[slot].start();
    }

    /** Das alte Foto ist ganz überdeckt: Animation beenden und Speicher freigeben. */
    private void hideOld(int old) {
        if (old < 0 || !running) return;
        slides[old].setAlpha(0f);
        if (motion[old] != null) motion[old].cancel();
        slides[old].setImageDrawable(null);
        releaseBitmap(old);
    }

    private void releaseBitmap(int slot) {
        final Bitmap stale = bitmaps[slot];
        bitmaps[slot] = null;
        if (stale != null && !stale.isRecycled()) ui.postDelayed(stale::recycle, 1_000);   // erst, wenn der Renderer es nicht mehr braucht
    }

    // ---- Angaben, Uhr, Nachtmodus ------------------------------------------------------------

    private void fadeInInfo(long delayMs) {
        fadeInfo(1f, delayMs, INFO_FADE_MS + 200, null);
    }

    /** Die Angaben wandern bei jedem Foto in die nächste Ecke (auch gegen Einbrennen). */
    private void moveInfo() {
        if (fixedCorner >= 0) return;   // feste Ecke: nichts wandert
        corner = (corner + 1) % CORNERS.length;
        fadeInfo(0f, 0, INFO_FADE_MS, this::placeInfoInCorner);
    }

    private void placeInfoInCorner() {
        positionInfo();
        fadeInfo(1f, 0, INFO_FADE_MS, null);
    }

    private void positionInfo() {
        LayoutParams lp = (LayoutParams) info.getLayoutParams();
        lp.gravity = CORNERS[corner];
        info.setLayoutParams(lp);
        LayoutParams scrimParams = (LayoutParams) scrim.getLayoutParams();
        scrimParams.gravity = CORNERS[corner];
        scrim.setLayoutParams(scrimParams);
        mirrorScrim();
    }

    /** Das Schattenbild hat seine Mitte unten links; für die anderen Ecken wird es gespiegelt. */
    private void mirrorScrim() {
        scrim.setScaleX(CORNER_RIGHT[corner] ? -1f : 1f);
        scrim.setScaleY(CORNER_TOP[corner] ? -1f : 1f);
    }

    /** Blendet Angaben und Schatten zusammen ein oder aus. {@code withLayer} rendert die Angaben nur einmal und blendet dann die Textur. */
    private void fadeInfo(float alpha, long delayMs, long durationMs, Runnable atEnd) {
        scrim.animate().alpha(alpha).setStartDelay(delayMs).setDuration(durationMs).setInterpolator(SMOOTH).start();
        ViewPropertyAnimator infoAnimator = info.animate().alpha(alpha).setStartDelay(delayMs).setDuration(durationMs)
                .setInterpolator(SMOOTH).withLayer();
        if (atEnd != null) infoAnimator.withEndAction(atEnd);
        infoAnimator.start();
    }

    /** Setzt die Uhrzeit und plant den nächsten Aufruf genau auf den Minutenwechsel. */
    private void updateClock() {
        long nowMs = System.currentTimeMillis();
        Date now = new Date(nowMs);
        Context context = getContext();
        boolean is24 = timeFormat == 0 ? DateFormat.is24HourFormat(context) : timeFormat == 1;
        info.setTime(DateFormat.format(is24 ? "H:mm" : "h:mm", now),
                DateFormat.format(datePattern, now));
        if (!running) return;
        info.setNight(isIconNight(nowMs));
        applyNight(true);
        if (report != null && nowMs - report.fetchedAt > STALE_MS) {   // lange offline: lieber nichts als Altes zeigen
            report = null;
            info.hideWeather();
        }
        ui.removeCallbacks(tick);
        ui.postDelayed(tick, 60_000 - nowMs % 60_000 + 30);
    }

    private static int hourOf(long timeMs) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(timeMs);
        return calendar.get(Calendar.HOUR_OF_DAY);
    }

    /** Sonne oder Mond im Wettersymbol: ab 20 Uhr bis 6 Uhr Mond. */
    private boolean isIconNight(long timeMs) {
        int hour = hourOf(timeMs);
        return isForcedNight || hour >= 20 || hour < 6;
    }

    private boolean isNightMode() {
        if (!isNightEnabled) return false;
        if (isForcedNight) return true;
        int hour = hourOf(System.currentTimeMillis());
        return nightFrom <= nightTo ? hour >= nightFrom && hour < nightTo : hour >= nightFrom || hour < nightTo;
    }

    /** Dunkelt beim Wechsel in die Nacht langsam ab und hellt am Morgen wieder auf. */
    private void applyNight(boolean isAnimated) {
        boolean night = isNightMode();
        if (night == isNightApplied) return;
        isNightApplied = night;
        float target = night ? nightDim : 0f;
        dimmer.animate().cancel();
        if (isAnimated) dimmer.animate().alpha(target).setDuration(NIGHT_FADE_MS).setInterpolator(SMOOTH).start();
        else dimmer.setAlpha(target);
    }

    // ---- Wetter ------------------------------------------------------------------------------

    /** Ohne Schalter oder ohne Stadt: keine Anzeige und keinerlei Netzwerkzugriff. */
    private void startWeather() {
        report = null;
        weatherFailures = 0;
        info.hideWeather();
        if (!isWeatherOn || !Weather.isConfigured(getContext())) return;
        weatherWorker = Executors.newSingleThreadExecutor();
        Weather.Report cached = Weather.cached(getContext());
        if (cached != null) showReport(cached);
        loadWeather();
    }

    private void loadWeather() {
        if (!running || weatherWorker == null) return;
        final Context context = getContext().getApplicationContext();
        final int myGeneration = generation;
        weatherWorker.execute(() -> {
            final Weather.Report fresh = Weather.fetch(context);
            ui.post(() -> {
                if (!running || myGeneration != generation) return;
                long waitMs;
                if (fresh != null) {
                    weatherFailures = 0;
                    showReport(fresh);
                    waitMs = WEATHER_REFRESH_MS;
                } else {   // offline: still weiter versuchen, das bisherige Wetter bleibt stehen
                    waitMs = WEATHER_RETRY_MS[Math.min(weatherFailures++, WEATHER_RETRY_MS.length - 1)];
                }
                ui.postDelayed(refreshWeather, waitMs);
            });
        });
    }

    private void showReport(Weather.Report fresh) {
        report = fresh;
        info.showWeather(fresh, isIconNight(System.currentTimeMillis()));
    }

    /** Liest alle Auswahlen; jeder Wert wird auf den erlaubten Bereich begrenzt (kaputte Werte ergeben den Standard). */
    private void readSettings(Context context) {
        int seconds = AmbientSettings.getInt(context, AmbientSettings.KEY_PHOTO_SECONDS, AmbientSettings.DEFAULT_PHOTO_SECONDS);
        showMs = Math.max(5, Math.min(120, seconds)) * 1_000L;
        int level = AmbientSettings.getInt(context, AmbientSettings.KEY_MOTION_LEVEL, AmbientSettings.DEFAULT_MOTION_LEVEL);
        zoomLevel = level >= 0 && level < ZOOM_LEVELS.length ? level : AmbientSettings.DEFAULT_MOTION_LEVEL;
        timeFormat = AmbientSettings.getInt(context, AmbientSettings.KEY_TIME_FORMAT, AmbientSettings.DEFAULT_TIME_FORMAT);
        datePattern = datePatternFor(AmbientSettings.getInt(context, AmbientSettings.KEY_DATE_FORMAT, AmbientSettings.DEFAULT_DATE_FORMAT));
        nightFrom = (AmbientSettings.getInt(context, AmbientSettings.KEY_NIGHT_FROM, AmbientSettings.DEFAULT_NIGHT_FROM) % 24 + 24) % 24;
        nightTo = (AmbientSettings.getInt(context, AmbientSettings.KEY_NIGHT_TO, AmbientSettings.DEFAULT_NIGHT_TO) % 24 + 24) % 24;
        int brightness = AmbientSettings.getInt(context, AmbientSettings.KEY_NIGHT_BRIGHTNESS, AmbientSettings.DEFAULT_NIGHT_BRIGHTNESS);
        nightDim = 1f - Math.max(10, Math.min(90, brightness)) / 100f;
        fixedCorner = AmbientSettings.getInt(context, AmbientSettings.KEY_CORNER, AmbientSettings.CORNER_WANDER);
        if (fixedCorner >= CORNERS.length || fixedCorner < -1) fixedCorner = AmbientSettings.CORNER_WANDER;
        info.applySettings();
        if (fixedCorner >= 0) {
            corner = fixedCorner;
            positionInfo();
        }
    }

    private static String datePatternFor(int format) {
        return DateFormat.getBestDateTimePattern(Locale.getDefault(), format == 1 ? "EEEdMMM" : "EEEEdMMMM");
    }

    // ---- Hilfen ------------------------------------------------------------------------------

    private int px(float dips) {
        return Math.round(dips * getResources().getDisplayMetrics().density);
    }

    /** Sanfter Verlauf mit weichem Anfang und Ende (Smoothstep). */
    private static final class Smooth implements TimeInterpolator {
        @Override public float getInterpolation(float t) {
            return t * t * (3f - 2f * t);
        }
    }
}
