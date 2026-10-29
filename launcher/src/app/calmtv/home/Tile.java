package app.calmtv.home;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Eine abgerundete Karte, die sich bei Fokus wie bei tvOS zu einem hebt: Sie wächst, wirft einen weichen Schatten und fängt etwas Licht.
 * Bild-Kacheln (Apps) tragen einen Fokusring und zeigen auf Wunsch ihren Namen darunter; Text-Kacheln (Suche, Einstellungen) sind
 * Milchglas, das bei Fokus weiß (oder in der Akzentfarbe) wird.
 */
final class Tile extends LinearLayout {
    private static final DecelerateInterpolator EASE = new DecelerateInterpolator(1.6f);
    private static final ArgbEvaluator MIX = new ArgbEvaluator();
    private static final int GLASS_IDLE = 0x2EFFFFFF, GLASS_EDGE = 0x40FFFFFF, ART_BACKDROP = 0xFF2A2D33, DEFAULT_RADIUS_DP = 12;
    private static final float DEFAULT_SCALE = 1.1f, TEXT_TILE_SCALE = 1.08f;
    private static final long FOCUS_MS = 170;

    final FrameLayout card;
    private final float density, scaleRequest;
    private final boolean isArt;
    private final View gloss;
    private final TextView nameBelow;           // unter der Karte, nur bei Fokus sichtbar (oder null)
    private final TextView nameInside;          // in der Karte (Text-Kachel, sonst null)
    private final GradientDrawable glassFill;   // Füllung der Text-Kachel (sonst null)
    private final GradientDrawable ring;        // Fokusring der Bild-Kachel (sonst null); die Farbe folgt der Akzentfarbe
    private final View ringView;
    private OnFocusChangeListener extraListener;
    private ValueAnimator fillAnimation;        // Text-Kachel: Füllung, Schriftfarbe und Glanz laufen in einer Animation
    private float fillProgress;                 // 0 = ohne Fokus, 1 = mit Fokus

    /** Eine Kachel mit Bild (App-Banner oder Symbol-Kachel); der Name steht auf Wunsch unter der Karte. */
    static Tile art(Context context, int width, int height, Drawable picture, String name, boolean withLabel, float focusScale) {
        return new Tile(context, width, height, picture, name, withLabel, focusScale);
    }

    /** Eine Kachel aus Milchglas mit Text darin. */
    static Tile text(Context context, int width, int height, String name) {
        return new Tile(context, width, height, null, name, false, TEXT_TILE_SCALE);
    }

    private Tile(Context c, int w, int h, Drawable artwork, String name, boolean showLabel, float focusScale) {
        super(c);
        density = Ui.density(c);
        scaleRequest = focusScale;
        isArt = artwork != null;
        setClipToPadding(false);
        setClipChildren(false);
        setGravity(Gravity.CENTER_HORIZONTAL);
        setOrientation(VERTICAL);

        float radius = DEFAULT_RADIUS_DP * density;
        card = new FrameLayout(c);
        card.setContentDescription(name);
        card.setClickable(true);
        card.setFocusable(true);
        card.setOutlineProvider(new Round(radius));
        card.setClipToOutline(true);

        if (isArt) {
            card.setBackgroundColor(ART_BACKDROP);
            card.addView(pictureOf(c, artwork), new FrameLayout.LayoutParams(-1, -1));
            glassFill = null;
            nameInside = null;
        } else {
            glassFill = new GradientDrawable();
            glassFill.setCornerRadius(radius);
            glassFill.setColor(GLASS_IDLE);
            glassFill.setStroke(Math.max(1, Math.round(density)), GLASS_EDGE);
            card.setBackground(glassFill);
            nameInside = insideLabel(c, name);
            card.addView(nameInside, new FrameLayout.LayoutParams(-1, -1));
        }
        gloss = new View(c);
        gloss.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[] {0x38FFFFFF, 0x0AFFFFFF, 0x00FFFFFF}));
        gloss.setAlpha(0f);
        card.addView(gloss, new FrameLayout.LayoutParams(-1, -1));
        if (isArt) {
            ring = new GradientDrawable();
            ring.setCornerRadius(radius);
            ring.setStroke(ringWidth(), Look.focusRing());
            ringView = new View(c);
            ringView.setBackground(ring);
            ringView.setAlpha(0f);
            card.addView(ringView, new FrameLayout.LayoutParams(-1, -1));
        } else {
            ring = null;
            ringView = null;
        }
        addView(card, new LayoutParams(w, h));

        if (showLabel) {
            nameBelow = belowLabel(c, name);
            LayoutParams params = new LayoutParams(w, -2);
            params.topMargin = Math.round(8 * density);
            addView(nameBelow, params);
        } else {
            nameBelow = null;
        }
        card.setOnFocusChangeListener((v, hasFocus) -> {
            animateFocus(hasFocus);
            if (extraListener != null) extraListener.onFocusChange(v, hasFocus);
        });
    }

    /** Jede Kachel bekommt ihr eigenes Bild: ein ImageView setzt die Maße seines Bildes, und Kacheln verschiedener Größe dürfen sich keines teilen. */
    private static ImageView pictureOf(Context c, Drawable artwork) {
        ImageView picture = new ImageView(c);
        picture.setScaleType(ImageView.ScaleType.CENTER_CROP);
        Drawable.ConstantState state = artwork.getConstantState();
        picture.setImageDrawable(state == null ? artwork : state.newDrawable().mutate());
        return picture;
    }

    private TextView insideLabel(Context c, String name) {
        TextView label = new TextView(c);
        label.setText(name);
        label.setTextSize(16);
        label.setTypeface(Ui.font(c, 600));
        label.setTextColor(Color.WHITE);
        label.setGravity(Gravity.CENTER);
        label.setMaxLines(2);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setPadding(Math.round(10 * density), 0, Math.round(10 * density), 0);
        return label;
    }

    private TextView belowLabel(Context c, String name) {
        TextView label = new TextView(c);
        label.setText(name);
        label.setTextSize(14);
        label.setTypeface(Ui.font(c, 600));
        label.setTextColor(Color.WHITE);
        label.setGravity(Gravity.CENTER);
        label.setSingleLine();
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setShadowLayer(4 * density, 0, density, 0xAA000000);
        label.setAlpha(0f);
        return label;
    }

    // ---- Aussehen anpassen -----------------------------------------------------------------------

    /** Setzt die Eckenrundung von Karte und Fokusring (Einstellung "Rundung der Kacheln"). */
    void setCornerDp(int dp) {
        float radius = dp * density;
        card.setOutlineProvider(new Round(radius));
        card.setClipToOutline(true);
        if (glassFill != null) glassFill.setCornerRadius(radius);
        if (ring != null) ring.setCornerRadius(radius);
    }

    /** Hält den Namen unter der Kachel dauerhaft sichtbar (Einstellung "Namen unter den Kacheln: Immer"); bei Fokus rückt er etwas nach unten. */
    void pinLabel(boolean isFocused) {
        if (nameBelow == null) return;
        nameBelow.animate().cancel();
        nameBelow.setAlpha(1f);
        nameBelow.setTranslationY(isFocused ? 4 * density : 0);
    }

    /** Ändert den Text einer Text-Kachel, die einen Zustand anzeigt. */
    void setLabel(String name) {
        if (nameInside != null) nameInside.setText(name);
        card.setContentDescription(name);
    }

    /** Wendet die aktuelle Akzentfarbe auf eine fokussierte Text-Kachel an (nach einem Farbwechsel). */
    void refreshFocusColors() {
        if (!isArt) applyFill(fillProgress);
    }

    /** Zusätzliche Fokus-Rückmeldung, zum Beispiel um den Namen der App über dem Dock zu zeigen. */
    void onFocus(OnFocusChangeListener listener) {
        extraListener = listener;
    }

    // ---- Fokus -----------------------------------------------------------------------------------

    private int ringWidth() {
        return Math.round(Config.val(Opts.FOCUS_RING) * density);
    }

    /** Kacheln mit dem Standardwert folgen der Einstellung "Größe beim Fokus"; andere (Text-Kacheln) behalten ihren Wert. */
    private float focusScale() {
        return scaleRequest == DEFAULT_SCALE ? Config.val(Opts.FOCUS_SCALE) / 100f : scaleRequest;
    }

    private void animateFocus(boolean hasFocus) {
        float scale = hasFocus ? focusScale() : 1f;
        android.view.ViewPropertyAnimator lift = card.animate().scaleX(scale).scaleY(scale);
        // Bild-Kacheln sind deckend und heben sich mit Schatten ab. Text-Kacheln bekommen keine Erhebung: Android zeichnet den Schatten
        // einer halbdurchsichtigen, angehobenen Fläche durch sie hindurch, das gab einen dunklen Rahmen, während sie von Glas zu Weiß wechselte.
        if (isArt) lift.translationZ(hasFocus ? 10 * density : 0);
        lift.setDuration(Ui.ms(FOCUS_MS)).setInterpolator(EASE).start();
        if (isArt) {
            gloss.animate().alpha(hasFocus ? 1f : 0f).setDuration(Ui.ms(FOCUS_MS)).start();
            if (hasFocus) ring.setStroke(ringWidth(), Look.focusRing());   // Einstellung und Akzentfarbe können sich seit dem Bau geändert haben
            ringView.animate().alpha(hasFocus ? 1f : 0f).setDuration(Ui.ms(FOCUS_MS)).start();
        } else {
            // Füllung, Schriftfarbe und Glanz in einer Animation: sie einzeln zu setzen, während Glanz und Größe noch ausblenden,
            // ließ die Kachel aufblitzen, wenn der Fokus sie verließ.
            if (fillAnimation != null) fillAnimation.cancel();
            fillAnimation = ValueAnimator.ofFloat(fillProgress, hasFocus ? 1f : 0f);   // setzt dort fort, wo ein unterbrochener Wechsel stand
            fillAnimation.setDuration(Ui.ms(FOCUS_MS));
            fillAnimation.setInterpolator(EASE);
            fillAnimation.addUpdateListener(animation -> applyFill((float) animation.getAnimatedValue()));
            fillAnimation.start();
        }
        if (nameBelow != null) {
            nameBelow.animate().alpha(hasFocus ? 1f : 0f).translationY(hasFocus ? 4 * density : 0)
                    .setDuration(Ui.ms(FOCUS_MS)).setInterpolator(EASE).start();
        }
    }

    private void applyFill(float progress) {
        fillProgress = progress;
        glassFill.setColor((int) MIX.evaluate(progress, GLASS_IDLE, Look.focusFill()));
        nameInside.setTextColor((int) MIX.evaluate(progress, Color.WHITE, Look.focusText()));
        gloss.setAlpha(progress);
    }

    private static final class Round extends ViewOutlineProvider {
        private final float radius;

        Round(float radius) {
            this.radius = radius;
        }

        @Override public void getOutline(View view, Outline outline) {
            outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
        }
    }
}
