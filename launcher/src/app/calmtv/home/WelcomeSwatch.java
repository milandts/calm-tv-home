package app.calmtv.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.view.View;

/**
 * Farbfeld (Kreis) der Einführung. Der Fokus zeigt einen weißen Ring und vergrößert leicht, die gewählte Farbe trägt
 * ein Häkchen. Der Ring bleibt weiß, damit er auf jeder Akzentfarbe sichtbar ist.
 */
final class WelcomeSwatch extends View {
    private static final int RING_COLOR = 0xE6FFFFFF, LIGHT_BACKGROUND_CHECK = 0xFF1C1C1E;
    private static final int DARK_TEXT_ABOVE = 140;
    private static final float FOCUS_SCALE = 1.1f;

    private final Paint disc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint check = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path checkPath = new Path();
    private final float discInset;
    private boolean isChosen;

    WelcomeSwatch(Context c, int colorIndex) {
        super(c);
        int color = Look.accentColor(colorIndex);
        setFocusable(true);
        setClickable(true);
        setContentDescription(Look.ACCENT_NAMES[colorIndex]);
        disc.setColor(color);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(Ui.px(c, 3));
        ring.setColor(RING_COLOR);
        check.setStyle(Paint.Style.STROKE);
        check.setStrokeWidth(Ui.px(c, 3));
        check.setStrokeCap(Paint.Cap.ROUND);
        check.setStrokeJoin(Paint.Join.ROUND);
        int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, b = color & 0xFF;
        boolean isLight = (299 * r + 587 * g + 114 * b) / 1000 > DARK_TEXT_ABOVE;
        check.setColor(isLight ? LIGHT_BACKGROUND_CHECK : 0xFFFFFFFF);
        discInset = Ui.px(c, 9);
    }

    /** Markiert die Farbe als gewählt (Häkchen), unabhängig vom Fokus. */
    void setChosen(boolean chosen) {
        isChosen = chosen;
        invalidate();
    }

    @Override
    protected void onFocusChanged(boolean gainFocus, int direction, Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
        float scale = gainFocus ? FOCUS_SCALE : 1f;
        animate().scaleX(scale).scaleY(scale).setDuration(Ui.FAST).setInterpolator(Ui.EASE).start();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        float half = Math.min(getWidth(), getHeight()) / 2f;
        canvas.drawCircle(cx, cy, half - discInset, disc);
        if (isFocused()) canvas.drawCircle(cx, cy, half - ring.getStrokeWidth() / 2f, ring);
        if (isChosen) {
            float u = (half - discInset) / 10f;   // Maßeinheit für das Häkchen
            checkPath.reset();
            checkPath.moveTo(cx - 4.5f * u, cy + 0.2f * u);
            checkPath.lineTo(cx - 1.4f * u, cy + 3.3f * u);
            checkPath.lineTo(cx + 4.6f * u, cy - 3.2f * u);
            canvas.drawPath(checkPath, check);
        }
    }
}
