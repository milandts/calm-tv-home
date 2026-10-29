package app.calmtv.home;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.TextView;

/**
 * Auswahl-Chip: abgerundete Pille mit drei Zuständen: normal, gewählt (ruhige Fläche, Häkchen, Rand, fetterer Text) und
 * fokussiert (gefüllte Akzentfläche mit Look.focusFill / focusText; gewählt und fokussiert zeigt zusätzlich das Häkchen). Zustandswechsel laufen animiert über {@link Ui#FAST}. Der Fokus wird über
 * {@code onFocusChanged} verfolgt, damit Aufrufer einen eigenen OnFocusChangeListener setzen können.
 */
final class Chip extends TextView {
    /** Kein Farbpunkt vor dem Text. */
    static final int NO_DOT = 0;

    private static final ArgbEvaluator ARGB = new ArgbEvaluator();
    private static final float DEFAULT_HEIGHT_DP = 36, SIDE_PADDING_DP = 16, BORDER_DP = 2, DOT_DP = 12, DOT_GAP_DP = 6;
    private static final float CHECK_DP = 16, CHECK_GAP_DP = 3, CHECK_MARGIN_DP = 6, RING_STRENGTH = 0.7f;
    private static final int DOT_EDGE = 0x33000000;
    private static final int CHOSEN_FILL = 0x38FFFFFF, CHOSEN_TEXT = 0xFFFFFFFF;
    private static final int WEIGHT_NORMAL = 600, WEIGHT_CHOSEN = 800;

    private final GradientDrawable shape;
    private final int borderPx, sidePaddingPx, heightPx;
    private final Icons.Glyph check = Icons.icon(Icons.HAKEN, Color.WHITE);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean chosen, danger;
    private int dotColor = NO_DOT;
    private float focusProgress, chosenProgress;
    private ValueAnimator focusAnim, chosenAnim;

    Chip(Context c, String label) {
        this(c, label, DEFAULT_HEIGHT_DP);
    }

    /** Wie {@link #Chip(Context, String)}, aber mit eigener Höhe (z. B. 44 dp für Buttons in Dialogen). */
    Chip(Context c, String label, float heightDp) {
        super(c);
        borderPx = Math.max(1, Ui.px(c, BORDER_DP));
        sidePaddingPx = Ui.px(c, SIDE_PADDING_DP);
        heightPx = Ui.px(c, heightDp);
        shape = new GradientDrawable();
        shape.setCornerRadius(Math.max(Ui.px(c, Ui.RADIUS_CHIP), heightPx / 2f));   // höhere Chips bleiben volle Pillen
        setBackground(shape);
        setTextSize(Ui.sp(15));
        setTypeface(Ui.font(c, WEIGHT_NORMAL));
        setIncludeFontPadding(false);
        setSingleLine();
        setEllipsize(TextUtils.TruncateAt.END);
        setGravity(Gravity.CENTER);
        setHeight(heightPx);
        updateLeading();
        setFocusable(true);
        setClickable(true);
        setLabel(label);
        render();
    }

    boolean isChosen() {
        return chosen;
    }

    /** Markiert den Chip als gewählt (andere Darstellung als der Fokus). */
    void setChosen(boolean chosen) {
        if (this.chosen == chosen) return;
        this.chosen = chosen;
        setTypeface(Ui.font(getContext(), chosen ? WEIGHT_CHOSEN : WEIGHT_NORMAL));
        updateLeading();
        chosenAnim = animate(chosenAnim, chosenProgress, chosen ? 1f : 0f, false);
    }

    /** Ändert die Beschriftung; die Breite richtet sich nach der fetten Schrift, damit Nachbarn beim Wählen nicht rutschen. */
    void setLabel(String label) {
        setText(label);
        setContentDescription(label);
        updateMinWidth();
    }

    /** Rote Schrift, im Fokus rote Fläche mit weißer Schrift (für destruktive Bestätigungen). */
    void setDanger(boolean danger) {
        this.danger = danger;
        render();
    }

    /** Kleiner Farbpunkt links vom Text (z. B. für die Akzentfarben); {@link #NO_DOT} entfernt ihn. */
    void setDotColor(int color) {
        dotColor = color;
        updateLeading();
        updateMinWidth();
        requestLayout();
    }

    private int checkSlotPx() {
        return Ui.px(getContext(), CHECK_DP + CHECK_GAP_DP);
    }

    private int dotSlotPx() {
        return dotColor == NO_DOT ? 0 : Ui.px(getContext(), DOT_DP + DOT_GAP_DP);
    }

    /**
     * Der Punkt steht fest vor dem Text und gehört zum Innenabstand. Das Häkchen (nur gewählt) liegt dagegen links vom Text
     * im Leerraum des Chips, ohne den Text zu verschieben: beim Wählen rutschen Ziffern und Wörter nicht zur Seite.
     * Gezeichnet wird in {@link #onDraw}.
     */
    private void updateLeading() {
        setPadding(sidePaddingPx + dotSlotPx(), 0, sidePaddingPx, 0);
    }

    private void updateMinWidth() {
        TextPaint measure = new TextPaint(getPaint());
        measure.setTypeface(Ui.font(getContext(), WEIGHT_CHOSEN));
        float textWidth = measure.measureText(getText().toString());
        // Links vom (mittigen) Text muss das Häkchen Platz haben, auch bei kurzen Beschriftungen wie "4"; die Breite hängt
        // nicht vom Zustand ab, damit Nachbarn beim Wählen nicht rutschen.
        int checkRoom = dotSlotPx() + checkSlotPx() + Ui.px(getContext(), CHECK_MARGIN_DP);
        int plain = (int) Math.ceil(textWidth) + dotSlotPx() + 2 * sidePaddingPx;
        int withCheck = (int) Math.ceil(textWidth) + 2 * checkRoom - dotSlotPx();
        setMinWidth(Math.max(plain, withCheck) + 2);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (getLayout() == null) return;
        boolean showCheck = chosen;
        int leading = dotSlotPx() + (showCheck ? checkSlotPx() : 0);
        if (leading == 0) return;
        // Ohne getScrollX(): Die Canvas ist bereits um den Scroll-Versatz verschoben, und dieser Versatz ist bei einzeiligem,
        // zentriertem Text riesig (ca. 524000 px, genau wie lineLeft). Mit +getScrollX() lag das Häkchen weit außerhalb des Chips.
        float x = getTotalPaddingLeft() + getLayout().getLineLeft(0) - leading;
        float centerY = getHeight() / 2f;
        if (showCheck) {
            int size = Ui.px(getContext(), CHECK_DP);
            check.setBounds(Math.round(x), Math.round(centerY - size / 2f), Math.round(x) + size, Math.round(centerY + size / 2f));
            check.draw(canvas);
            x += checkSlotPx();
        }
        if (dotColor != NO_DOT) {
            float radius = Ui.px(getContext(), DOT_DP) / 2f;
            float edge = Math.max(1, Ui.px(getContext(), 1));
            dotPaint.setStyle(Paint.Style.FILL);
            dotPaint.setColor(dotColor);
            canvas.drawCircle(x + radius, centerY, radius, dotPaint);
            // feine dunkle Kante, damit ein weißer Punkt auch auf heller Fläche zu sehen ist
            dotPaint.setStyle(Paint.Style.STROKE);
            dotPaint.setStrokeWidth(edge);
            dotPaint.setColor(DOT_EDGE);
            canvas.drawCircle(x + radius, centerY, radius - edge / 2f, dotPaint);
        }
    }

    @Override protected void onFocusChanged(boolean gained, int direction, Rect previouslyFocused) {
        super.onFocusChanged(gained, direction, previouslyFocused);
        focusAnim = animate(focusAnim, focusProgress, gained ? 1f : 0f, true);
    }

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (focusAnim != null) focusAnim.cancel();
        if (chosenAnim != null) chosenAnim.cancel();
    }

    /** Läuft von der aktuellen Stelle zum Ziel (auch wenn ein Wechsel unterbrochen wird) und zeichnet jeden Schritt. */
    private ValueAnimator animate(ValueAnimator running, float from, float to, boolean isFocus) {
        if (running != null) running.cancel();
        ValueAnimator anim = ValueAnimator.ofFloat(from, to);
        anim.setDuration(Ui.ms(Ui.FAST));
        anim.setInterpolator(Ui.EASE);
        anim.addUpdateListener(a -> {
            float value = (float) a.getAnimatedValue();
            if (isFocus) focusProgress = value; else chosenProgress = value;
            render();
        });
        anim.start();
        return anim;
    }

    /** Mischt die drei Zustände: normal, gewählt, fokussiert (Fokus hat Vorrang). */
    private void render() {
        int focusFill = danger ? Ui.DANGER : Look.focusFill();
        int focusText = danger ? Color.WHITE : Look.focusText();
        int restText = danger ? Ui.DANGER : Ui.TEXT;
        int chosenText = danger ? Ui.DANGER : CHOSEN_TEXT;

        int fill = (int) ARGB.evaluate(chosenProgress, Ui.ROW, CHOSEN_FILL);
        fill = (int) ARGB.evaluate(focusProgress, fill, focusFill);
        int text = (int) ARGB.evaluate(chosenProgress, restText, chosenText);
        text = (int) ARGB.evaluate(focusProgress, text, focusText);

        // Der Rand ist nur beim gewählten, nicht fokussierten Chip zu sehen; auf der Fokusfläche würde er verschwinden.
        int ring = Look.focusRing();
        int ringAlpha = Math.round(Color.alpha(ring) * RING_STRENGTH * chosenProgress * (1f - focusProgress));
        shape.setColor(fill);
        shape.setStroke(borderPx, (ring & 0x00FFFFFF) | (ringAlpha << 24));
        setTextColor(text);
        check.setColor(text);
        check.setAlpha(Math.round(255 * chosenProgress));
        invalidate();
    }
}
