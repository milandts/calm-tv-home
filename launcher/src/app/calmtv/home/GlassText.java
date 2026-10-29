package app.calmtv.home;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;

/**
 * Eine Textzeile, aus Glas geschnitten, wie die Uhr auf dem iPhone-Sperrbildschirm: Das unscharfe Hintergrundfoto scheint durch die
 * Buchstaben, zu Weiß hin angefroren, und an den Oberkanten fängt sich Licht. Sie nutzt dieselbe vorab unscharf gerechnete,
 * bildschirmgleiche Kopie wie {@link Glass}. Auf Wunsch zeigt sie stattdessen schlichten weißen Text mit weichem Schatten.
 */
final class GlassText extends View implements Config.Listener {
    private static final float LENS_ZOOM = 1.08f;
    private static final String DIGITS = "0123456789:";
    private static final int FROSTED_WHITE = 0xF2FFFFFF;
    private static final int[] RIM_COLORS = {0xFFFFFFFF, 0x59FFFFFF, 0x40FFFFFF, 0xB3FFFFFF};
    private static final float[] RIM_STOPS = {0f, 0.35f, 0.75f, 1f};

    private final float density;
    private final Paint bodyPaint = glassPaint(), fadingPaint = glassPaint();
    private final Paint shadowPaint = flatPaint(), sheenPaint = flatPaint(), rimPaint = flatPaint(), plainPaint = flatPaint();
    private final Matrix photoMatrix = new Matrix();
    private final int[] origin = new int[2];
    private final Path outline = new Path();
    private boolean isOutlineStale = true;
    private float inkTop, inkBottom;   // wo die Ziffern wirklich sitzen, bezogen auf die Grundlinie
    private int appliedStyle;
    private boolean isPlain;
    private String text = "";
    private Bitmap photo, fadingPhoto;
    private float photoMix = 1f;
    private ValueAnimator photoFade;

    GlassText(Context c, Typeface face, float sp) {
        super(c);
        density = Ui.density(c);
        for (Paint paint : allPaints()) paint.setTypeface(face);
        applySize(sp);
        GlassStyle.ensure();
        bodyPaint.setColor(FROSTED_WHITE);   // gilt auch als Füllung, bis das erste Foto geladen ist
        // Der Schatten kommt aus einem eigenen Durchgang unter dem Glas: Auf dem Glas-Pinsel selbst würde er in der Glasfarbe
        // gezeichnet und wäre auf dunklen Fotos ein graues Leuchten.
        shadowPaint.setColor(Color.BLACK);
        plainPaint.setColor(Color.WHITE);
        plainPaint.setShadowLayer(6 * density, 0, 2 * density, 0x66000000);
        applyStyle();
        rimPaint.setStyle(Paint.Style.STROKE);
        rimPaint.setStrokeJoin(Paint.Join.ROUND);
        rimPaint.setStrokeWidth(1.6f * density);
    }

    private static Paint glassPaint() {
        return new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    }

    private static Paint flatPaint() {
        return new Paint(Paint.ANTI_ALIAS_FLAG);
    }

    private Paint[] allPaints() {
        return new Paint[] {bodyPaint, fadingPaint, shadowPaint, sheenPaint, rimPaint, plainPaint};
    }

    /** Ändert die Textgröße (in sp); Maße und Aussparungen werden neu berechnet. */
    void setSizeSp(float sp) {
        applySize(sp);
        isOutlineStale = true;
        requestLayout();
        invalidate();
    }

    private void applySize(float sp) {
        float size = sp * getContext().getResources().getDisplayMetrics().scaledDensity;
        for (Paint paint : allPaints()) paint.setTextSize(size);
        measureInk();
    }

    /** Die Höhe richtet sich nach den Ziffern selbst, nicht nach der Zeilenhöhe der Schrift: Nunito lässt viel Platz für Akzente, der Gruß, Uhr und Datum auseinanderziehen würde. */
    private void measureInk() {
        Rect ink = new Rect();
        bodyPaint.getTextBounds(DIGITS, 0, DIGITS.length(), ink);
        inkTop = ink.top;
        inkBottom = ink.bottom;
    }

    void setTypeface(Typeface face) {
        for (Paint paint : allPaints()) paint.setTypeface(face);
        measureInk();
        isOutlineStale = true;
        requestLayout();
        invalidate();
    }

    /** {@code plain}: schlichter Text in {@code color} mit weichem Schatten statt Glas; sonst Glas. */
    void setPlain(boolean plain, int color) {
        isPlain = plain;
        plainPaint.setColor(color);
        invalidate();
    }

    /** Übernimmt die Glas-Einstellungen, aber nur nach einer Änderung. */
    private void applyStyle() {
        appliedStyle = GlassStyle.version();
        bodyPaint.setColorFilter(GlassStyle.textFilter());
        fadingPaint.setColorFilter(GlassStyle.textFilter());
        rimPaint.setAlpha(Math.round(255 * GlassStyle.rim()));
        sheenPaint.setAlpha(Math.round(255 * GlassStyle.sheen()));
        shadowPaint.setShadowLayer(6 * density, 0, 2 * density, Math.round(0x4D * GlassStyle.shadow()) << 24);
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

    void setText(CharSequence next) {
        String line = next.toString();
        if (line.equals(text)) return;
        setContentDescription(line);   // die Uhr ist von Hand gezeichnet, ein Screenreader braucht die Zeit als Text
        boolean isWider = Math.abs(bodyPaint.measureText(line) - bodyPaint.measureText(text)) > 0.5f;
        text = line;
        isOutlineStale = true;
        if (isWider) requestLayout();
        invalidate();
    }

    /** Setzt das unscharfe Foto, auf Wunsch mit Überblendung (bei Dauer 0 sofort). */
    void setBlur(Bitmap next, long requestedFadeMs) {
        long fadeMs = Ui.ms(requestedFadeMs);
        if (photoFade != null) photoFade.cancel();
        fadingPhoto = photo != null && fadeMs > 0 ? photo : null;
        if (fadingPhoto != null) fadingPaint.setShader(clampedShader(fadingPhoto));
        photo = next;
        bodyPaint.setShader(clampedShader(next));
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

    /** Platz rund um die Ziffern für Schatten und Rand. */
    private float margin() {
        return 10 * density;
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int width = (int) Math.ceil(bodyPaint.measureText(text) + 2 * margin());
        int height = (int) Math.ceil(inkBottom - inkTop + 2 * margin());
        setMeasuredDimension(resolveSize(width, widthSpec), resolveSize(height, heightSpec));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        super.onSizeChanged(w, h, oldW, oldH);
        float top = margin(), bottom = h - margin();
        // Hell an der Oberkante, schwach an den Seiten, unten wieder etwas Licht.
        rimPaint.setShader(new LinearGradient(0, top, 0, bottom, RIM_COLORS, RIM_STOPS, Shader.TileMode.CLAMP));
        sheenPaint.setShader(new LinearGradient(0, top, 0, top + (bottom - top) * 0.5f, 0x4DFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float x = margin(), baseline = margin() - inkTop;
        if (isPlain) {
            canvas.drawText(text, x, baseline, plainPaint);
            return;
        }
        if (appliedStyle != GlassStyle.version()) applyStyle();
        canvas.drawText(text, x, baseline, shadowPaint);
        if (photo != null && GlassStyle.opacity() > 0f) {
            getLocationInWindow(origin);
            View root = getRootView();
            // Das neue Foto liegt darunter, das alte blendet darüber aus.
            drawBody(canvas, bodyPaint, photo, root, 255, x, baseline);
            if (fadingPhoto != null && photoMix < 1f) drawBody(canvas, fadingPaint, fadingPhoto, root, Math.round(255 * (1f - photoMix)), x, baseline);
        } else {
            bodyPaint.setAlpha(0xF2);
            canvas.drawText(text, x, baseline, bodyPaint);
        }
        canvas.drawText(text, x, baseline, sheenPaint);
        canvas.drawPath(letterOutline(x, baseline), rimPaint);
    }

    /**
     * Der Umriss der Buchstaben mit zusammengefassten Überlappungen: Runde Schriften bauen manche Ziffern aus sich überlappenden
     * Formen, und deren Rand würde als Naht mitten in den Buchstaben gezeichnet.
     */
    private Path letterOutline(float x, float baseline) {
        if (isOutlineStale) {
            Path raw = new Path();
            rimPaint.getTextPath(text, 0, text.length(), x, baseline, raw);
            outline.reset();
            if (!outline.op(raw, Path.Op.UNION)) outline.set(raw);
            isOutlineStale = false;
        }
        return outline;
    }

    private void drawBody(Canvas canvas, Paint paint, Bitmap bitmap, View root, int alpha, float x, float baseline) {
        photoMatrix.setScale(root.getWidth() / (float) bitmap.getWidth(), root.getHeight() / (float) bitmap.getHeight());
        photoMatrix.postTranslate(-origin[0], -origin[1]);
        photoMatrix.postScale(LENS_ZOOM, LENS_ZOOM, getWidth() / 2f, getHeight() / 2f);
        paint.getShader().setLocalMatrix(photoMatrix);
        paint.setAlpha(Math.round(alpha * GlassStyle.opacity()));
        canvas.drawText(text, x, baseline, paint);
    }
}
