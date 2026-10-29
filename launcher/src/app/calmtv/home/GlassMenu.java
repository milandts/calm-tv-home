package app.calmtv.home;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.FocusFinder;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/**
 * Kontextmenü im Glas-Look, mittig über allem. Wird in die Modal-Ebene gelegt, implementiert {@link Ui.Modal} und stellt
 * beim Schließen den vorherigen Fokus wieder her. Die Richtungstasten bleiben im Menü (am Rand werden sie verbraucht).
 *
 * Die statischen Helfer weiter unten (Abdeckung, Panel, Ein- und Ausblenden, Fokus) teilt {@link GlassPrompt}.
 */
final class GlassMenu extends FrameLayout implements Ui.Modal {
    /** Ein Menüeintrag. */
    static final class Item {
        final String label;
        final String detail;       // optionale zweite Zeile, sonst ""
        final int icon;            // Icons.* oder Icons.NONE
        final boolean destructive; // rot
        final Runnable action;     // läuft, nachdem das Menü geschlossen ist

        Item(String label, String detail, int icon, boolean destructive, Runnable action) {
            this.label = label;
            this.detail = detail;
            this.icon = icon;
            this.destructive = destructive;
            this.action = action;
        }

        Item(String label, int icon, Runnable action) {
            this(label, "", icon, false, action);
        }

        Item(String label, Runnable action) {
            this(label, "", Icons.NONE, false, action);
        }
    }

    private static final float MIN_WIDTH_DP = 280, MAX_WIDTH_DP = 320, HEIGHT_SHARE = 0.8f;
    private static final float ROW_MIN_HEIGHT_DP = 52, ROW_PAD_H_DP = 16, ICON_DP = 22, ICON_GAP_DP = 14;
    private static final float LIST_PAD_H_DP = 8, LIST_PAD_TOP_DP = 8, LIST_PAD_BOTTOM_DP = 16, ROW_GAP_DP = 2;
    private static final int WHITE_DIM = 0xCCFFFFFF;
    private static final ArgbEvaluator ARGB = new ArgbEvaluator();

    private ViewGroup layer;
    private View previousFocus;
    private View scrim;
    private ViewGroup panel;
    private Runnable onClosed;
    private boolean closing;

    private GlassMenu(Context c) {
        super(c);
    }

    /**
     * Zeigt ein Menü. {@code title} darf leer sein. {@code onClosed} läuft nach dem Schließen (auch ohne Auswahl), z. B. um den Fokus
     * zurückzugeben. Lange Listen scrollen. Der erste Eintrag hat zu Beginn den Fokus. Reihenfolge beim Schließen: Fokus
     * zurückgeben, {@code onClosed}, dann die Aktion des Eintrags (so kann sie ein weiteres Modal öffnen, ohne dass
     * {@code onClosed} ihm den Fokus nimmt); beides läuft sofort beim Schließen, das Ausblenden läuft parallel weiter.
     */
    static void show(ViewGroup layer, String title, List<Item> items, Runnable onClosed) {
        if (layer == null) return;
        if (items == null || items.isEmpty()) {
            if (onClosed != null) onClosed.run();
            return;
        }
        new GlassMenu(layer.getContext()).open(layer, title, items, onClosed);
    }

    private void open(ViewGroup layerView, String title, List<Item> items, Runnable closedCallback) {
        layer = layerView;
        onClosed = closedCallback;
        previousFocus = currentFocus(layerView);
        Context c = getContext();
        setClickable(true);   // verschluckt Berührungen, die sonst die Ansicht darunter erreichen würden
        setOnClickListener(v -> dismiss());

        scrim = newScrim(c);
        addView(scrim, new LayoutParams(-1, -1));

        CappedPanel box = newPanel(c);
        box.setMaxWidthPx(Ui.px(c, MAX_WIDTH_DP));
        box.setMinimumWidth(Ui.px(c, MIN_WIDTH_DP));
        panel = box;
        if (title != null && !title.isEmpty()) {
            TextView heading = Ui.label(c, title, Ui.sp(Ui.SP_BODY), 600, Ui.TEXT_DIM);
            heading.setShadowLayer(0, 0, 0, 0);
            heading.setSingleLine();
            heading.setEllipsize(TextUtils.TruncateAt.END);
            heading.setPadding(Ui.px(c, 24), Ui.px(c, 20), Ui.px(c, 24), Ui.px(c, 4));
            box.addView(heading, new LinearLayout.LayoutParams(-1, -2));
        }

        LinearLayout list = new LinearLayout(c);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(Ui.px(c, LIST_PAD_H_DP), Ui.px(c, LIST_PAD_TOP_DP), Ui.px(c, LIST_PAD_H_DP), Ui.px(c, LIST_PAD_BOTTOM_DP));
        ItemRow first = null;
        for (Item item : items) {
            ItemRow row = new ItemRow(c, item);
            row.setOnClickListener(v -> choose(item));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = list.getChildCount() == 0 ? 0 : Ui.px(c, ROW_GAP_DP);
            list.addView(row, lp);
            if (first == null) first = row;
        }
        ScrollView scroll = new ScrollView(c);
        scroll.setFocusable(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(OVER_SCROLL_NEVER);
        scroll.addView(list, new ScrollView.LayoutParams(-1, -2));
        // Lange Listen: Balken und wippender Pfeil zeigen, dass es unten weitergeht (wie auf den Einstellungsseiten)
        FrameLayout scrollHolder = new FrameLayout(c);
        scrollHolder.addView(scroll, new FrameLayout.LayoutParams(-1, -2));
        scrollHolder.addView(new SettingsScrollHint(c, scroll, list), new FrameLayout.LayoutParams(-1, -1));
        box.addView(scrollHolder, new LinearLayout.LayoutParams(-1, -2));

        addView(box, new LayoutParams(-2, -2, Gravity.CENTER));
        layerView.addView(this, new LayoutParams(-1, -1));
        first.requestFocus();
        animateIn(scrim, panel);
    }

    private void choose(Item item) {
        close(item.action);
    }

    @Override public void dismiss() {
        close(null);
    }

    /**
     * Schließt sofort: Das Menü verlässt die Ebene noch in diesem Aufruf (siehe {@link #retire}), nur das Ausblenden läuft
     * danach auf einer Kopie weiter. Damit findet {@link Ui#dismissTopModal} nie ein halb geschlossenes Menü, und ein neues
     * Modal trifft nie auf eines, das noch schließt. Danach laufen {@code onClosed} und die Aktion des Eintrags.
     */
    private void close(Runnable afterwards) {
        if (closing) return;
        closing = true;
        retire(layer, this, scrim, panel, previousFocus);
        if (onClosed != null) onClosed.run();
        if (afterwards != null) afterwards.run();
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        if (code == KeyEvent.KEYCODE_BACK) return super.dispatchKeyEvent(event);   // Zurück kommt über Ui.dismissTopModal
        if (code == KeyEvent.KEYCODE_MENU) {
            if (event.getAction() == KeyEvent.ACTION_UP) dismiss();
            return true;
        }
        if (closing) return true;   // während des Ausblendens nichts mehr annehmen
        return super.dispatchKeyEvent(event) || trapDirections(this, event);
    }

    // ---- Helfer, die auch GlassPrompt nutzt -------------------------------------------------------

    /** Der Ansicht mit Fokus im ganzen Fenster (oder null), um sie nach dem Schließen wieder zu fokussieren. */
    static View currentFocus(ViewGroup layer) {
        return layer.getRootView().findFocus();
    }

    static void restoreFocus(View previous) {
        if (previous != null && previous.isAttachedToWindow() && previous.getVisibility() == View.VISIBLE) previous.requestFocus();
    }

    /** Dunkle Abdeckung über der Ansicht darunter. */
    static View newScrim(Context c) {
        View v = new View(c);
        v.setBackgroundColor(Ui.SCRIM);
        return v;
    }

    /** Das Glas-Panel: dunkel durchscheinend, feine Kante, weicher Schatten, Ecken mit {@link Ui#RADIUS_MENU}. */
    static CappedPanel newPanel(Context c) {
        CappedPanel box = new CappedPanel(c);
        box.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = Ui.round(c, Ui.panel(Ui.PANEL_MODAL), Ui.RADIUS_MENU, Ui.HAIRLINE, Math.max(1, Ui.px(c, 1)));
        box.setBackground(bg);
        box.setOutlineProvider(new PanelOutline(Ui.px(c, Ui.RADIUS_MENU)));
        box.setClipToOutline(true);
        box.setElevation(Ui.px(c, 18));
        box.setClickable(true);   // Berührungen im Panel schließen das Menü nicht
        return box;
    }

    /**
     * Nimmt ein Modal sofort aus der Ebene und blendet Abdeckung und Panel auf einer unauffälligen Hülle aus (kein
     * {@link Ui.Modal}, nicht fokussierbar). Der Fokus geht zuerst zurück, damit beim Entfernen kein fremdes Element
     * den Fokus an sich zieht.
     */
    static void retire(ViewGroup layer, FrameLayout modal, View scrim, ViewGroup panel, View previousFocus) {
        restoreFocus(previousFocus);
        int index = layer.indexOfChild(modal);
        if (index < 0) return;
        panel.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        FadeShell shell = new FadeShell(layer.getContext());
        modal.removeView(scrim);
        modal.removeView(panel);
        shell.addView(scrim);
        shell.addView(panel);
        layer.removeViewAt(index);
        layer.addView(shell, index, new LayoutParams(-1, -1));
        animateOut(scrim, panel, new RemoveShell(layer, shell));
        shell.postDelayed(new RemoveShell(layer, shell), Ui.ms(Ui.SLOW));   // falls die Animation nie endet
    }

    /** Träger der auslaufenden Animation; absichtlich kein Modal. */
    private static final class FadeShell extends FrameLayout {
        FadeShell(Context c) {
            super(c);
            setFocusable(false);
            setClickable(false);
        }
    }

    private static final class RemoveShell implements Runnable {
        private final ViewGroup layer;
        private final View shell;

        RemoveShell(ViewGroup layer, View shell) {
            this.layer = layer;
            this.shell = shell;
        }

        @Override public void run() {
            if (shell.getParent() == layer) layer.removeView(shell);
        }
    }

    /** Einblenden: Abdeckung blendet auf, das Panel wächst von 0.94 auf 1 und wird sichtbar. */
    static void animateIn(View scrim, View panel) {
        scrim.setAlpha(0f);
        panel.setAlpha(0f);
        panel.setScaleX(0.94f);
        panel.setScaleY(0.94f);
        scrim.animate().alpha(1f).setDuration(Ui.ms(Ui.MEDIUM)).setInterpolator(Ui.EASE).start();
        panel.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(Ui.ms(Ui.MEDIUM)).setInterpolator(Ui.EASE).start();
    }

    /** Ausblenden in {@link Ui#FAST}; danach läuft {@code end}. */
    static void animateOut(View scrim, View panel, Runnable end) {
        scrim.animate().alpha(0f).setDuration(Ui.ms(Ui.FAST)).setInterpolator(Ui.EASE).start();
        panel.animate().alpha(0f).scaleX(0.97f).scaleY(0.97f).setDuration(Ui.ms(Ui.FAST)).setInterpolator(Ui.EASE)
                .withEndAction(end).start();
    }

    /**
     * Hält die Richtungstasten im Modal: sucht den nächsten Fokus nur innerhalb von {@code modal} und verbraucht die Taste
     * auch am Rand. So wandert der Fokus nie in die Ansicht darunter. Gibt false zurück, wenn es keine Richtungstaste ist.
     */
    static boolean trapDirections(ViewGroup modal, KeyEvent event) {
        int direction = directionOf(event.getKeyCode());
        if (direction == 0) return false;
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            View next = FocusFinder.getInstance().findNextFocus(modal, modal.findFocus(), direction);
            if (next != null) next.requestFocus(direction);
        }
        return true;
    }

    static int directionOf(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP: return View.FOCUS_UP;
            case KeyEvent.KEYCODE_DPAD_DOWN: return View.FOCUS_DOWN;
            case KeyEvent.KEYCODE_DPAD_LEFT: return View.FOCUS_LEFT;
            case KeyEvent.KEYCODE_DPAD_RIGHT: return View.FOCUS_RIGHT;
            default: return 0;
        }
    }

    /** Senkrechtes Panel, das höchstens 80 % der Höhe der Ebene einnimmt (Inhalt in einer ScrollView scrollt dann). */
    static final class CappedPanel extends LinearLayout {
        private int maxWidthPx;   // 0 = keine Begrenzung

        CappedPanel(Context c) {
            super(c);
        }

        void setMaxWidthPx(int px) {
            maxWidthPx = px;
        }

        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            if (maxWidthPx > 0 && MeasureSpec.getMode(widthSpec) == MeasureSpec.AT_MOST) {
                widthSpec = MeasureSpec.makeMeasureSpec(Math.min(MeasureSpec.getSize(widthSpec), maxWidthPx), MeasureSpec.AT_MOST);
            }
            if (MeasureSpec.getMode(heightSpec) == MeasureSpec.UNSPECIFIED) {
                super.onMeasure(widthSpec, heightSpec);
                return;
            }
            int available = MeasureSpec.getSize(heightSpec);
            int cap = Math.round(available * HEIGHT_SHARE);
            super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(cap, MeasureSpec.AT_MOST));
        }
    }

    private static final class PanelOutline extends ViewOutlineProvider {
        private final float radius;

        PanelOutline(float radius) {
            this.radius = radius;
        }

        @Override public void getOutline(View v, Outline outline) {
            outline.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radius);
            outline.setAlpha(0.5f);
        }
    }

    // ---- Zeile ------------------------------------------------------------------------------------

    /** Eine Zeile: Symbol, Titel, Detail. Fokus füllt sie mit {@link Look#focusFill()}; Symbol und Text wechseln mit. */
    private static final class ItemRow extends LinearLayout {
        private final boolean destructive;
        private final GradientDrawable shape;
        private final TextView title, detail;
        private final Icons.Glyph glyph;
        private float progress;
        private ValueAnimator anim;

        ItemRow(Context c, Item item) {
            super(c);
            destructive = item.destructive;
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setMinimumHeight(Ui.px(c, ROW_MIN_HEIGHT_DP));
            setPadding(Ui.px(c, ROW_PAD_H_DP), Ui.px(c, 8), Ui.px(c, ROW_PAD_H_DP), Ui.px(c, 8));
            setFocusable(true);
            setFocusableInTouchMode(true);
            setClickable(true);
            setContentDescription(item.label);
            shape = new GradientDrawable();
            shape.setCornerRadius(Ui.px(c, Ui.RADIUS_ROW));
            setBackground(shape);

            if (item.icon != Icons.NONE) {
                glyph = Icons.icon(item.icon, restTitleColor());
                ImageView iconView = new ImageView(c);
                iconView.setImageDrawable(glyph);
                LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(Ui.px(c, ICON_DP), Ui.px(c, ICON_DP));
                ilp.rightMargin = Ui.px(c, ICON_GAP_DP);
                addView(iconView, ilp);
            } else {
                glyph = null;
            }

            LinearLayout texts = new LinearLayout(c);
            texts.setOrientation(VERTICAL);
            title = plainLabel(c, item.label, 17, 600, restTitleColor());
            title.setSingleLine();
            title.setEllipsize(TextUtils.TruncateAt.END);
            texts.addView(title, new LinearLayout.LayoutParams(-1, -2));
            if (item.detail != null && !item.detail.isEmpty()) {
                detail = plainLabel(c, item.detail, Ui.SP_LABEL, 500, Ui.TEXT_DIM);
                detail.setMaxLines(2);
                detail.setEllipsize(TextUtils.TruncateAt.END);
                LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-1, -2);
                dlp.topMargin = Ui.px(c, 2);
                texts.addView(detail, dlp);
            } else {
                detail = null;
            }
            addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));
            apply(0f);
        }

        /** Text ohne Schatten (der Schatten von {@link Ui#label} würde dunkle Schrift auf heller Fläche verschmieren). */
        private static TextView plainLabel(Context c, String text, float sp, int weight, int color) {
            TextView t = Ui.label(c, text, Ui.sp(sp), weight, color);
            t.setShadowLayer(0, 0, 0, 0);
            return t;
        }

        private int restTitleColor() {
            return destructive ? Ui.DANGER : Ui.TEXT;
        }

        @Override protected void onFocusChanged(boolean gained, int direction, Rect previouslyFocused) {
            super.onFocusChanged(gained, direction, previouslyFocused);
            if (anim != null) anim.cancel();
            anim = ValueAnimator.ofFloat(progress, gained ? 1f : 0f);
            anim.setDuration(Ui.ms(Ui.FAST));
            anim.setInterpolator(Ui.EASE);
            anim.addUpdateListener(a -> apply((float) a.getAnimatedValue()));
            anim.start();
        }

        @Override protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            if (anim != null) anim.cancel();
        }

        /** Fläche, Schrift und Symbol für einen Zwischenstand (0 = ruhig, 1 = fokussiert). */
        private void apply(float p) {
            progress = p;
            int fill = destructive ? Ui.DANGER : Look.focusFill();
            int ink = destructive ? Color.WHITE : Look.focusText();
            int inkDim = destructive ? WHITE_DIM : (ink & 0x00FFFFFF) | 0xB3000000;
            shape.setColor((int) ARGB.evaluate(p, fill & 0x00FFFFFF, fill));
            int titleColor = (int) ARGB.evaluate(p, restTitleColor(), ink);
            title.setTextColor(titleColor);
            if (glyph != null) glyph.setColor(titleColor);
            if (detail != null) detail.setTextColor((int) ARGB.evaluate(p, Ui.TEXT_DIM, inkDim));
        }
    }
}
