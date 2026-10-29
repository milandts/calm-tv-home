package app.calmtv.home;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;

/**
 * Eine schlanke Kachel der Galerie: Vorschaubild mit runden Ecken, Markierungen (Favorit, ausgeblendet, eigenes Bild),
 * Fokusring mit leichter Vergrößerung und die Titelzeile darunter, solange sie Fokus hat. Keine Glass-Instanz, nur Zeichnen.
 */
final class GalleryTile extends View {
    static final float WIDTH_DP = 176, TITLE_DP = 26;

    private static final float RADIUS_DP = 12, BADGE_DP = 24, EYE_DP = 30, STAR_DP = 16, DOT_DP = 5, TITLE_GAP_DP = 6;
    private static final int HIDDEN_DIM = 0xB3000000, BADGE_BACK = 0x99000000;

    private final int picW, picH, titlePx, ringPx;
    private final float radius, focusScale;
    private final RectF pic = new RectF();
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Icons.Glyph eye = Icons.icon(Icons.AUSGEBLENDET, Color.WHITE);
    private final Icons.Glyph star = Icons.icon(Icons.STERN, Ui.WARN);
    private final Matrix shaderMatrix = new Matrix();

    private Wallpapers.Entry entry;
    private String thumbKey;
    private Bitmap thumb;
    private BitmapShader shader;
    private String shownTitle = "";
    private float focusT;
    private ValueAnimator anim;

    GalleryTile(Context c, Wallpapers.Entry entry) {
        super(c);
        // Vier Spalten müssen auch bei großem Sicherheitsrand in die nutzbare Breite passen (Rand links/rechts 78 dp, Lücken 3 x 20 dp).
        float fitDp = (Ui.usableWidth(c) / c.getResources().getDisplayMetrics().density - 2 * 78 - 3 * 20) / 4f;
        picW = Ui.px(c, Math.min(WIDTH_DP, fitDp));
        picH = Math.round(picW * 9f / 16f);
        titlePx = Ui.px(c, TITLE_DP);
        ringPx = Ui.px(c, Config.val(Opts.FOCUS_RING));
        radius = Ui.px(c, RADIUS_DP);
        focusScale = Config.val(Opts.FOCUS_SCALE) / 100f;
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(ringPx);
        text.setTypeface(Ui.font(c, 600));
        text.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, Ui.sp(Ui.SP_LABEL), c.getResources().getDisplayMetrics()));
        text.setColor(Ui.TEXT);
        setFocusable(true);
        setClickable(true);
        setEntry(entry);
        setPivotY(picH / 2f);
    }

    Wallpapers.Entry entry() {
        return entry;
    }

    /** Das Vorschaubild, das zu dieser Kachel gehört (gleicher Schlüssel). */
    String wantedKey() {
        return GalleryThumbs.key(entry);
    }

    private void setEntry(Wallpapers.Entry e) {
        entry = e;
        shownTitle = TextUtils.ellipsize(e.title, text, picW, TextUtils.TruncateAt.END).toString();
        StringBuilder d = new StringBuilder(e.title);
        if (e.favorite) d.append(", Favorit");
        if (e.hidden) d.append(", ausgeblendet");
        if (e.own) d.append(", eigenes Bild");
        setContentDescription(d);
    }

    /** Setzt das Vorschaubild, wenn es zum aktuellen Eintrag gehört. */
    void setThumb(String key, Bitmap bitmap) {
        if (!key.equals(wantedKey()) || bitmap == null) return;
        thumbKey = key;
        thumb = bitmap;
        shader = new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        shaderMatrix.setScale(picW / (float) bitmap.getWidth(), picH / (float) bitmap.getHeight());
        shader.setLocalMatrix(shaderMatrix);
        invalidate();
    }

    boolean hasThumb() {
        return thumb != null && wantedKey().equals(thumbKey);
    }

    /** True, wenn ein Teil der Kachel auf dem Bildschirm zu sehen ist. */
    boolean isOnScreen() {
        return isShown() && getGlobalVisibleRect(new android.graphics.Rect());
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(picW, picH + titlePx);
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        setPivotX(w / 2f);
        setPivotY(picH / 2f);
    }

    @Override protected void onFocusChanged(boolean gained, int direction, android.graphics.Rect previous) {
        super.onFocusChanged(gained, direction, previous);
        if (anim != null) anim.cancel();
        long duration = Ui.ms(Ui.FAST);
        if (duration == 0) {
            applyFocus(gained ? 1f : 0f);
            return;
        }
        anim = ValueAnimator.ofFloat(focusT, gained ? 1f : 0f);
        anim.setDuration(duration);
        anim.setInterpolator(Ui.EASE);
        anim.addUpdateListener(a -> applyFocus((Float) a.getAnimatedValue()));
        anim.start();
    }

    private void applyFocus(float t) {
        focusT = t;
        float s = 1f + (focusScale - 1f) * t;
        setScaleX(s);
        setScaleY(s);
        invalidate();
    }

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (anim != null) anim.cancel();
    }

    @Override protected void onDraw(Canvas canvas) {
        pic.set(0, 0, picW, picH);
        if (shader != null && hasThumb()) {
            fill.setShader(shader);
            fill.setColor(Color.BLACK);
        } else {
            fill.setShader(null);
            fill.setColor(Ui.ROW);
        }
        canvas.drawRoundRect(pic, radius, radius, fill);
        fill.setShader(null);

        if (entry.hidden) {
            fill.setColor(HIDDEN_DIM);
            canvas.drawRoundRect(pic, radius, radius, fill);
            int e = Ui.px(getContext(), EYE_DP);
            eye.setBounds((picW - e) / 2, (picH - e) / 2, (picW + e) / 2, (picH + e) / 2);
            eye.draw(canvas);
        }
        if (entry.favorite) {
            float r = Ui.px(getContext(), BADGE_DP) / 2f;
            float cx = picW - r - Ui.px(getContext(), 6), cy = r + Ui.px(getContext(), 6);
            fill.setColor(BADGE_BACK);
            canvas.drawCircle(cx, cy, r, fill);
            int s = Ui.px(getContext(), STAR_DP) / 2;
            star.setBounds(Math.round(cx) - s, Math.round(cy) - s, Math.round(cx) + s, Math.round(cy) + s);
            star.draw(canvas);
        }
        if (entry.own) {
            float r = Ui.px(getContext(), DOT_DP);
            float cx = Ui.px(getContext(), 12), cy = Ui.px(getContext(), 12);
            fill.setColor(BADGE_BACK);
            canvas.drawCircle(cx, cy, r + Ui.px(getContext(), 2), fill);
            fill.setColor(Color.WHITE);
            canvas.drawCircle(cx, cy, r, fill);
        }
        if (focusT > 0f) {
            int ring = Look.focusRing();
            stroke.setColor(Color.argb(Math.round(Color.alpha(ring) * focusT), Color.red(ring), Color.green(ring), Color.blue(ring)));
            float half = ringPx / 2f;
            canvas.drawRoundRect(-half, -half, picW + half, picH + half, radius + half, radius + half, stroke);
            text.setAlpha(Math.round(255 * focusT));
            Paint.FontMetrics fm = text.getFontMetrics();
            float baseline = picH + Ui.px(getContext(), TITLE_GAP_DP) - fm.ascent;
            canvas.drawText(shownTitle, (picW - text.measureText(shownTitle)) / 2f, baseline, text);
        }
    }
}
