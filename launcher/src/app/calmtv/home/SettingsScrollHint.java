package app.calmtv.home;

import android.content.Context;
import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.animation.LinearInterpolator;
import android.graphics.RectF;
import android.view.View;
import android.widget.ScrollView;

/**
 * Dezenter Hinweis, dass eine Seite mehr Inhalt hat: ein schmaler Balken am rechten Rand und, solange unten noch etwas
 * kommt, unten mittig eine kleine Pille "Mehr" mit Pfeil (der Pfeil wippt sanft, bei "Bewegung: Aus" steht er still) auf
 * einem weichen dunklen Auslauf. Die Pille sitzt mittig und damit nie in der runden Ecke des Panels. Das weiche
 * Ausblenden an den Rändern übernimmt die ScrollView selbst. Liegt über der Liste und nimmt keine Tasten.
 */
final class SettingsScrollHint extends View {
    private static final int BAR_WIDTH_DP = 3, BAR_INSET_DP = 5, BAR_MIN_DP = 24;
    private static final int BAR_COLOR = 0x59FFFFFF;
    private static final int ARROW_HALF_WIDTH_DP = 6, ARROW_HEIGHT_DP = 6, ARROW_BOB_DP = 3, ARROW_STROKE_DP = 2;
    private static final int PILL_HEIGHT_DP = 34, PILL_SIDE_DP = 16, PILL_GAP_DP = 8, PILL_BOTTOM_DP = 14, SCRIM_DP = 96;
    private static final int PILL_FILL = 0xF2101014, PILL_EDGE = 0x33FFFFFF, SCRIM_COLOR = 0xF0101014, SCRIM_MID = 0xA0101014;
    private static final String PILL_TEXT = "Mehr";
    private static final long BOB_MS = 1400;
    /** Schritte je Wipp-Takt: die GPU des Sticks braucht je Bild lange, 60 Bilder pro Sekunde für 6 Pixel Hub wären verschwendet. */
    private static final int BOB_STEPS = 16;

    private final ScrollView scroll;
    private final View rows;
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bar = new RectF();
    private final int barWidthPx, barInsetPx, barMinPx;
    private final Paint arrowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path arrow = new Path();
    private final Paint pillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint scrimPaint = new Paint();
    private final RectF pill = new RectF();
    private final float arrowHalfWidthPx, arrowHeightPx, arrowBobPx, pillHeightPx, pillSidePx, pillGapPx, pillBottomPx, scrimPx;
    private float pillWidthPx, textWidthPx;
    private int scrimHeight = -1;   // Höhe, für die der Verlauf gebaut ist
    private ValueAnimator bob;
    private float bobPhase;   // 0..1, ein Wipp-Takt

    SettingsScrollHint(Context c, ScrollView scroll, View rows) {
        super(c);
        this.scroll = scroll;
        this.rows = rows;
        barWidthPx = Ui.px(c, BAR_WIDTH_DP);
        barInsetPx = Ui.px(c, BAR_INSET_DP);
        barMinPx = Ui.px(c, BAR_MIN_DP);
        barPaint.setColor(BAR_COLOR);
        arrowHalfWidthPx = Ui.px(c, ARROW_HALF_WIDTH_DP);
        arrowHeightPx = Ui.px(c, ARROW_HEIGHT_DP);
        arrowBobPx = Ui.px(c, ARROW_BOB_DP);
        pillHeightPx = Ui.px(c, PILL_HEIGHT_DP);
        pillSidePx = Ui.px(c, PILL_SIDE_DP);
        pillGapPx = Ui.px(c, PILL_GAP_DP);
        pillBottomPx = Ui.px(c, PILL_BOTTOM_DP);
        scrimPx = Ui.px(c, SCRIM_DP);
        textPaint.setColor(0xFFFFFFFF);
        textPaint.setTypeface(Ui.font(c, 700));
        textPaint.setTextSize(Ui.px(c, 14) * Config.val(Opts.TEXT_SCALE) / 100f);
        textWidthPx = textPaint.measureText(PILL_TEXT);
        pillWidthPx = 2 * pillSidePx + textWidthPx + pillGapPx + 2 * arrowHalfWidthPx;
        pillPaint.setStyle(Paint.Style.FILL);
        arrowPaint.setStyle(Paint.Style.STROKE);
        arrowPaint.setStrokeCap(Paint.Cap.ROUND);
        arrowPaint.setStrokeJoin(Paint.Join.ROUND);
        arrowPaint.setStrokeWidth(Ui.px(c, ARROW_STROKE_DP));
        scroll.setOnScrollChangeListener((v, x, y, oldX, oldY) -> invalidate());
        rows.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> invalidate());
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        // Der Takt läuft immer; bei Bewegung "Aus" (auch live umgestellt) bleibt die Phase auf 0, der Pfeil steht still.
        bob = ValueAnimator.ofFloat(0f, 1f);
        bob.setDuration(Ui.ms(BOB_MS) == 0 ? BOB_MS : Ui.ms(BOB_MS));
        bob.setRepeatCount(ValueAnimator.INFINITE);
        bob.setInterpolator(new LinearInterpolator());
        bob.addUpdateListener(a -> {
            float phase = Ui.ms(BOB_MS) == 0 ? 0f : (float) Math.floor((float) a.getAnimatedValue() * BOB_STEPS) / BOB_STEPS;
            if (phase == bobPhase) return;
            bobPhase = phase;
            if (hasMoreBelow()) {
                // Nur den Bereich des Pfeils neu zeichnen, nicht das ganze Panel (sonst ruckelt die Seite bei jedem Takt).
                int cx = getWidth() / 2, half = Math.round(pillWidthPx / 2) + Ui.px(getContext(), 8);
                invalidate(cx - half, Math.round(getHeight() - pillBottomPx - pillHeightPx) - Ui.px(getContext(), 8), cx + half, getHeight());
            }
        });
        bob.start();
    }

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (bob != null) bob.cancel();   // nichts laufen lassen, wenn die Seite weg ist
        bob = null;
    }

    private boolean hasMoreBelow() {
        int viewport = scroll.getHeight() - scroll.getPaddingTop() - scroll.getPaddingBottom();
        int max = rows.getHeight() - viewport;
        return max > 0 && scroll.getScrollY() < max - Ui.px(getContext(), 4);
    }

    @Override protected void onDraw(Canvas canvas) {
        int height = getHeight();
        int viewport = scroll.getHeight() - scroll.getPaddingTop() - scroll.getPaddingBottom();
        int max = rows.getHeight() - viewport;
        if (max <= 0 || height <= 0) return;
        int y = Math.max(0, Math.min(max, scroll.getScrollY()));

        float barHeight = Math.max(barMinPx, height * (float) viewport / rows.getHeight());
        float barTop = (height - barHeight) * y / max;
        float right = getWidth() - barInsetPx;
        bar.set(right - barWidthPx, barTop, right, barTop + barHeight);
        canvas.drawRoundRect(bar, barWidthPx, barWidthPx, barPaint);
        if (!hasMoreBelow()) return;

        // Auslauf: dunkler Verlauf am unteren Rand, damit die Pille auch auf heller Schrift ruhig steht
        if (scrimHeight != height) {   // der Verlauf hängt nur von der Höhe ab: nicht in jedem Takt neu anlegen
            scrimPaint.setShader(new LinearGradient(0, height - scrimPx, 0, height, new int[] {0x00101014, SCRIM_MID, SCRIM_COLOR}, new float[] {0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
            scrimHeight = height;
        }
        canvas.drawRect(0, height - scrimPx, getWidth(), height, scrimPaint);

        // Pille mit Text und wippendem Pfeil, mittig; der Pfeil wippt nach unten und blendet dabei etwas aus (Sinus: weicher Takt)
        float wave = (float) (0.5 - 0.5 * Math.cos(2 * Math.PI * bobPhase));
        float centerX = getWidth() / 2f;
        float bottom = height - pillBottomPx;
        pill.set(centerX - pillWidthPx / 2f, bottom - pillHeightPx, centerX + pillWidthPx / 2f, bottom);
        pillPaint.setColor(PILL_FILL);
        canvas.drawRoundRect(pill, pillHeightPx / 2f, pillHeightPx / 2f, pillPaint);
        pillPaint.setStyle(Paint.Style.STROKE);
        pillPaint.setStrokeWidth(Math.max(1, Ui.px(getContext(), 1)));
        pillPaint.setColor(PILL_EDGE);
        canvas.drawRoundRect(pill, pillHeightPx / 2f, pillHeightPx / 2f, pillPaint);
        pillPaint.setStyle(Paint.Style.FILL);

        float midY = pill.centerY();
        float textBase = midY - (textPaint.ascent() + textPaint.descent()) / 2f;
        canvas.drawText(PILL_TEXT, pill.left + pillSidePx, textBase, textPaint);
        float arrowCenterX = pill.right - pillSidePx - arrowHalfWidthPx;
        float top = midY - arrowHeightPx / 2f + wave * arrowBobPx - arrowBobPx / 2f;
        arrow.reset();
        arrow.moveTo(arrowCenterX - arrowHalfWidthPx, top);
        arrow.lineTo(arrowCenterX, top + arrowHeightPx);
        arrow.lineTo(arrowCenterX + arrowHalfWidthPx, top);
        arrowPaint.setColor(0xFFFFFFFF);
        arrowPaint.setAlpha(Math.round(255 - 90 * wave));
        canvas.drawPath(arrow, arrowPaint);
    }
}
