package app.calmtv.home;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;
import android.widget.HorizontalScrollView;

/**
 * Leiser Hinweis, dass das Dock in eine Richtung weitergeht: ein kleines, dünnes Zeichen "‹" oder "›" neben dem Glasrand,
 * nur auf der Seite, wo noch Apps verborgen sind. Es liegt als Kind im Dock-Glas, ragt aber seitlich in den Rand der
 * Startseite hinaus (das Glas füllt bei mehr als sechs Apps die ganze Spalte), so verdeckt es nie eine Kachel oder
 * den Fokusrahmen. Blendet weich ein und aus; bei "Bewegung: Aus" sofort. Nimmt keine Tasten.
 */
final class DockEdgeHint extends View {
    /** Wie weit die Ansicht links und rechts über das Glas hinausragt (Aufrufer setzt gleich große negative Ränder). */
    static final int OUTSET_DP = 36;
    private static final int HALF_HEIGHT_DP = 8, DEPTH_DP = 5, STROKE_DP = 2;
    private static final int ALPHA = 0x99;
    private static final long FADE_MS = 160;
    private static final int SLACK_DP = 2;

    private final HorizontalScrollView scroll;
    private final View row;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path chevron = new Path();
    private final float halfHeightPx, depthPx, centerOffsetPx;
    private final int slackPx;
    private float leftAlpha, rightAlpha;
    private ValueAnimator animator;

    DockEdgeHint(Context c, HorizontalScrollView scroll, View row) {
        super(c);
        this.scroll = scroll;
        this.row = row;
        setFocusable(false);
        setClickable(false);
        halfHeightPx = Ui.px(c, HALF_HEIGHT_DP);
        depthPx = Ui.px(c, DEPTH_DP);
        centerOffsetPx = Ui.px(c, OUTSET_DP) / 2f;
        slackPx = Ui.px(c, SLACK_DP);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeWidth(Ui.px(c, STROKE_DP));
        paint.setShadowLayer(Ui.px(c, 3), 0, 0, 0x66000000);   // bleibt auch auf hellem Hintergrund zu sehen
        scroll.setOnScrollChangeListener((v, x, y, oldX, oldY) -> update());
        row.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> update());
    }

    /** Bestimmt nie die Größe des Glases: erst die endgültige (feste) Größe wird übernommen, in der ersten Messung zählt sie 0. */
    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getMode(widthSpec) == MeasureSpec.EXACTLY ? MeasureSpec.getSize(widthSpec) : 0;
        int height = MeasureSpec.getMode(heightSpec) == MeasureSpec.EXACTLY ? MeasureSpec.getSize(heightSpec) : 0;
        setMeasuredDimension(width, height);
    }

    private void update() {
        int max = row.getWidth() - scroll.getWidth();
        float wantLeft = max > slackPx && scroll.getScrollX() > slackPx ? 1f : 0f;
        float wantRight = max > slackPx && scroll.getScrollX() < max - slackPx ? 1f : 0f;
        if (wantLeft == leftAlpha && wantRight == rightAlpha && animator == null) return;
        if (animator != null) animator.cancel();
        long duration = Ui.ms(FADE_MS);
        if (duration == 0) {
            leftAlpha = wantLeft;
            rightAlpha = wantRight;
            invalidate();
            return;
        }
        final float fromLeft = leftAlpha, fromRight = rightAlpha;
        animator = ValueAnimator.ofFloat(0f, 1f).setDuration(duration);
        animator.addUpdateListener(a -> {
            float t = (float) a.getAnimatedValue();
            leftAlpha = fromLeft + (wantLeft - fromLeft) * t;
            rightAlpha = fromRight + (wantRight - fromRight) * t;
            invalidate();
        });
        animator.start();
    }

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (animator != null) animator.cancel();
        animator = null;
    }

    @Override protected void onDraw(Canvas canvas) {
        float midY = getHeight() / 2f;
        if (leftAlpha > 0f) drawChevron(canvas, centerOffsetPx, midY, -1, leftAlpha);
        if (rightAlpha > 0f) drawChevron(canvas, getWidth() - centerOffsetPx, midY, 1, rightAlpha);
    }

    /** direction -1: Spitze zeigt nach links, 1: nach rechts. */
    private void drawChevron(Canvas canvas, float centerX, float midY, int direction, float strength) {
        chevron.reset();
        chevron.moveTo(centerX - direction * depthPx / 2f, midY - halfHeightPx);
        chevron.lineTo(centerX + direction * depthPx / 2f, midY);
        chevron.lineTo(centerX - direction * depthPx / 2f, midY + halfHeightPx);
        paint.setColor(0xFFFFFFFF);
        paint.setAlpha(Math.round(ALPHA * strength));
        canvas.drawPath(chevron, paint);
    }
}
