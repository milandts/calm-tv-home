package app.calmtv.home;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;

/**
 * Flüssiges Glas: Die Fläche zeigt das Hintergrundfoto dahinter unscharf, leicht vergrößert und kräftiger gefärbt, so dass sie wie eine
 * dicke Linse wirkt. Dazu kommen ein heller Rand, der an den runden Enden Licht fängt, und ein weicher Glanz oben.
 * Gezeichnet wird eine kleine, vorab unscharf gerechnete Kopie des Fotos, die deckungsgleich unter der Fläche liegt; live
 * unscharf zu rechnen schafft ein Fire TV Stick nicht flüssig. Eine fokussierte Taste füllt sich weiß (oder in der Akzentfarbe).
 */
class Glass extends FrameLayout implements Config.Listener {
    private static final float LENS_ZOOM = 1.08f;
    private static final int BASE_TINT = 0x14FFFFFF;
    private static final int[] RIM_COLORS = {0x8CFFFFFF, 0x0FFFFFFF, 0x0FFFFFFF, 0x66FFFFFF};
    private static final int FOCUS_FILL_ALPHA = 245;
    private static final long FILL_MS = 140;

    private float corner;
    private final Paint photoPaint = softPaint(true), fadingPaint = softPaint(true);
    private final Paint tintPaint = softPaint(false), sheenPaint = softPaint(false), rimPaint = softPaint(false);
    private final Paint fillPaint = softPaint(false), darkPaint = softPaint(false);
    private final RectF bounds = new RectF();
    private final Matrix photoMatrix = new Matrix();
    private final int[] origin = new int[2];
    private Bitmap photo, fadingPhoto;
    private float photoMix = 1f, whiteness;
    private ValueAnimator photoFade, whiteFade;
    private int appliedStyle;

    Glass(Context c, float radius) {
        super(c);
        corner = radius;
        setWillNotDraw(false);
        darkPaint.setColor(Color.BLACK);
        GlassStyle.ensure();
        applyStyle();
        tintPaint.setColor(BASE_TINT);
        rimPaint.setStyle(Paint.Style.STROKE);
        rimPaint.setStrokeWidth(Ui.density(c));
        setOutlineProvider(new Silhouette(radius));   // für den weichen Schatten
    }

    private static Paint softPaint(boolean isPhoto) {
        return new Paint(isPhoto ? Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG : Paint.ANTI_ALIAS_FLAG);
    }

    /** Ändert die Rundung der Ecken (zum Beispiel, wenn das Dock mit seiner Größenstufe skaliert). */
    void setRadius(float next) {
        if (next == corner) return;
        corner = next;
        setOutlineProvider(new Silhouette(next));
        onSizeChanged(getWidth(), getHeight(), getWidth(), getHeight());   // der Randverlauf richtet sich nach der Rundung
        invalidate();
    }

    /** Umriss für den Schattenwurf; die Deckkraft folgt der Einstellung "Schatten". */
    private static final class Silhouette extends ViewOutlineProvider {
        private final float radius;

        Silhouette(float radius) {
            this.radius = radius;
        }

        @Override public void getOutline(View view, Outline outline) {
            outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), Math.min(radius, view.getHeight() / 2f));
            outline.setAlpha(0.55f * GlassStyle.shadow());
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        super.onSizeChanged(w, h, oldW, oldH);
        // Licht fängt sich nur an den runden Enden; die langen geraden Kanten bleiben fast unsichtbar,
        // damit sie auf einem Fernseher mit Bildschärfung nicht wie Balken wirken.
        float endShare = Math.min(0.2f, Math.min(corner, h / 2f) * 1.2f / Math.max(1, w));
        rimPaint.setShader(new LinearGradient(0, 0, w, 0, RIM_COLORS, new float[] {0f, endShare, 1f - endShare, 1f}, Shader.TileMode.CLAMP));
        sheenPaint.setShader(new LinearGradient(0, 0, 0, h * 0.6f, 0x1FFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
    }

    /** Setzt das unscharfe Foto, auf Wunsch mit Überblendung vom bisherigen (bei Dauer 0 sofort). */
    void setBlur(Bitmap next, long requestedFadeMs) {
        long fadeMs = Ui.ms(requestedFadeMs);
        if (photoFade != null) photoFade.cancel();
        fadingPhoto = photo != null && fadeMs > 0 ? photo : null;
        if (fadingPhoto != null) fadingPaint.setShader(clampedShader(fadingPhoto));
        photo = next;
        photoPaint.setShader(clampedShader(next));
        photoMix = fadingPhoto == null ? 1f : 0f;
        if (fadingPhoto != null) {
            photoFade = ValueAnimator.ofFloat(0f, 1f).setDuration(fadeMs);
            photoFade.addUpdateListener(animation -> {
                photoMix = (float) animation.getAnimatedValue();
                invalidate();
            });
            photoFade.start();
        }
        invalidate();
    }

    private static BitmapShader clampedShader(Bitmap bitmap) {
        return new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
    }

    /** Füllt die Fläche weiß (fokussierte Taste) oder gibt sie wieder frei. */
    void setSolid(boolean on) {
        if (whiteFade != null) whiteFade.cancel();
        whiteFade = ValueAnimator.ofFloat(whiteness, on ? 1f : 0f).setDuration(Ui.ms(FILL_MS));
        whiteFade.addUpdateListener(animation -> {
            whiteness = (float) animation.getAnimatedValue();
            onSolidChanged(whiteness);
            invalidate();
        });
        whiteFade.start();
    }

    /** Wird bei jedem Schritt der Füllung gerufen (0 = Glas, 1 = weiß); Unterklassen färben damit ihren Inhalt. */
    protected void onSolidChanged(float solid) {}

    /** Übernimmt die Glas-Einstellungen in Farbfilter und Pinsel, aber nur nach einer Änderung. */
    private void applyStyle() {
        appliedStyle = GlassStyle.version();
        photoPaint.setColorFilter(GlassStyle.photoFilter());
        fadingPaint.setColorFilter(GlassStyle.photoFilter());
        rimPaint.setAlpha(Math.round(255 * GlassStyle.rim()));
        sheenPaint.setAlpha(Math.round(255 * GlassStyle.sheen()));
        darkPaint.setAlpha(GlassStyle.darkenAlpha());
        invalidateOutline();
    }

    @Override public void onChanged(String key) {
        invalidate();
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        GlassStyle.ensure();
        Config config = Config.i();
        if (config != null) config.addListener(this);
    }

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        Config config = Config.i();
        if (config != null) config.removeListener(this);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (appliedStyle != GlassStyle.version()) applyStyle();
        float radius = Math.min(corner, getHeight() / 2f);
        bounds.set(0, 0, getWidth(), getHeight());
        if (photo != null && GlassStyle.opacity() > 0f) {
            getLocationInWindow(origin);
            View root = getRootView();
            if (fadingPhoto != null && photoMix < 1f) drawPhoto(canvas, fadingPhoto, fadingPaint, root, 255, radius);
            drawPhoto(canvas, photo, photoPaint, root, Math.round(255 * photoMix), radius);
        }
        canvas.drawRoundRect(bounds, radius, radius, tintPaint);
        if (darkPaint.getAlpha() > 0) canvas.drawRoundRect(bounds, radius, radius, darkPaint);
        canvas.drawRoundRect(bounds, radius, radius, sheenPaint);
        if (whiteness > 0f) {
            fillPaint.setColor(Look.focusFill());   // Weiß oder die gewählte Akzentfarbe
            fillPaint.setAlpha(Math.round(FOCUS_FILL_ALPHA * whiteness));
            canvas.drawRoundRect(bounds, radius, radius, fillPaint);
        }
        float halfStroke = rimPaint.getStrokeWidth() / 2;
        bounds.inset(halfStroke, halfStroke);
        canvas.drawRoundRect(bounds, radius - halfStroke, radius - halfStroke, rimPaint);
    }

    /** Zeichnet das Foto so, dass es mit dem Bildschirm deckt (die Fläche zeigt den Ausschnitt dahinter), leicht vergrößert wie eine Linse. */
    private void drawPhoto(Canvas canvas, Bitmap bitmap, Paint paint, View root, int alpha, float radius) {
        photoMatrix.setScale(root.getWidth() / (float) bitmap.getWidth(), root.getHeight() / (float) bitmap.getHeight());
        photoMatrix.postTranslate(-origin[0], -origin[1]);
        photoMatrix.postScale(LENS_ZOOM, LENS_ZOOM, getWidth() / 2f, getHeight() / 2f);
        paint.getShader().setLocalMatrix(photoMatrix);
        paint.setAlpha(Math.round(alpha * GlassStyle.opacity()));
        canvas.drawRoundRect(bounds, radius, radius, paint);
    }
}
