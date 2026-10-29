package app.calmtv.home;

import android.content.Context;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.View;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Inhalt einer Seite im Panel: Brotkrumen (nur bei Unterseiten), Überschrift mit Zurück-Pfeil, Untertitel
 * und die scrollende Zeilenliste. Die Zeilen selbst füllt {@link SettingsScreen} in {@link #rows}.
 */
final class SettingsContent extends LinearLayout {
    private static final float PAGE_FRACTION = 0.4f;
    private static final int FADE_DP = 36, REVEAL_MARGIN_DP = 40;
    private static final long GOAL_VALID_MS = 260;

    final LinearLayout rows;
    final ScrollView scroll;
    private int goalY;
    private long goalTime;

    /** {@code crumb} ist der Pfad bis zur Elternseite oder null bei einer Kategorie-Seite. */
    SettingsContent(Context c, SettingsModel.Page page, String crumb) {
        super(c);
        setOrientation(VERTICAL);
        setPadding(Ui.px(c, 2), Ui.px(c, 22), Ui.px(c, 2), 0);   // die Liste läuft fast bis zur Panelkante
        int inset = Ui.px(c, 26);   // Überschriften stehen auf der Höhe des Zeilentextes

        if (crumb != null) {
            TextView trail = SettingsRowView.text(c, crumb, Ui.SP_LABEL, 500, Ui.TEXT_DIM);
            trail.setSingleLine();
            trail.setEllipsize(TextUtils.TruncateAt.START);
            LayoutParams tlp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            tlp.setMargins(inset, 0, inset, Ui.px(c, 8));
            addView(trail, tlp);
        }

        LinearLayout headline = new LinearLayout(c);
        headline.setOrientation(HORIZONTAL);
        headline.setGravity(Gravity.CENTER_VERTICAL);
        if (crumb != null) {
            ImageView back = new ImageView(c);
            back.setImageDrawable(Icons.icon(Icons.PFEIL_ZURUECK, Ui.TEXT_DIM));
            LayoutParams blp = new LayoutParams(Ui.px(c, 24), Ui.px(c, 24));
            blp.setMarginEnd(Ui.px(c, 10));
            headline.addView(back, blp);
        }
        TextView heading = SettingsRowView.text(c, page.title, 24, 400, Ui.TEXT);
        heading.setMaxLines(2);
        heading.setEllipsize(TextUtils.TruncateAt.END);
        headline.addView(heading, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        LayoutParams hlp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        hlp.setMargins(inset, 0, inset, 0);
        addView(headline, hlp);

        if (!page.subtitle.isEmpty()) {
            TextView sub = SettingsRowView.text(c, page.subtitle, Ui.SP_BODY, 500, Ui.TEXT_DIM);
            sub.setMaxLines(3);
            sub.setEllipsize(TextUtils.TruncateAt.END);
            LayoutParams slp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            slp.setMargins(inset, Ui.px(c, 6), inset, 0);
            addView(sub, slp);
        }

        scroll = new ScrollView(c);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(OVER_SCROLL_NEVER);
        scroll.setFocusable(false);   // nur Seiten ohne fokussierbare Zeile lassen die Liste selbst den Fokus nehmen
        scroll.setPadding(0, Ui.px(c, 6), 0, 0);
        // Echtes Ausblenden des Inhalts an den Rändern (nur dort, wo noch etwas verdeckt ist; die Ebene betrifft nur den Streifen)
        scroll.setVerticalFadingEdgeEnabled(true);
        scroll.setFadingEdgeLength(Ui.px(c, FADE_DP));
        rows = new LinearLayout(c);
        rows.setOrientation(VERTICAL);
        rows.setPadding(Ui.px(c, 10), Ui.px(c, 8), Ui.px(c, 24), Ui.px(c, 20));   // rechts Platz für den Scrollbalken
        scroll.addView(rows, new ScrollView.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        FrameLayout holder = new FrameLayout(c);
        holder.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        holder.addView(new SettingsScrollHint(c, scroll, rows), new FrameLayout.LayoutParams(-1, -1));
        addView(holder, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));
    }

    /** Höhe, die der Inhalt ohne Scrollen braucht (nach dem Layout aufrufen). */
    int naturalHeight() {
        int height = getPaddingTop() + getPaddingBottom() + scroll.getPaddingTop() + scroll.getPaddingBottom() + rows.getHeight();
        for (int i = 0; i < getChildCount() - 1; i++) {
            View header = getChildAt(i);
            LayoutParams lp = (LayoutParams) header.getLayoutParams();
            height += header.getHeight() + lp.topMargin + lp.bottomMargin;
        }
        return height;
    }

    /** Eine Überblendung des ganzen Inhalts braucht keine Offscreen-Ebene (spart auf dem schwachen Stick viel). */
    @Override public boolean hasOverlappingRendering() {
        return false;
    }

    /** Scrollt die Liste um etwa 40 % ihrer Höhe. Gibt true zurück, wenn es noch etwas zu scrollen gab. */
    boolean pageScroll(int direction, boolean immediate) {
        int step = Math.round(scroll.getHeight() * PAGE_FRACTION);
        return scrollTo(effectiveScrollY() + direction * step, immediate);
    }

    /** Hält die fokussierte Zeile mit Abstand im sichtbaren Bereich; erste und letzte Zeile zeigen den Rand der Liste. */
    void reveal(View row, boolean isFirst, boolean isLast, boolean immediate) {
        int viewport = viewport();
        int margin = Ui.px(getContext(), REVEAL_MARGIN_DP);   // fokussierte Zeile liegt nie im Ausblendstreifen
        int current = effectiveScrollY();
        int want = current;
        if (row.getTop() - margin < current) want = row.getTop() - margin;
        else if (row.getBottom() + margin > current + viewport) want = row.getBottom() + margin - viewport;
        // Die erste und letzte fokussierbare Zeile zeigen den Rand der Liste, aber nur, wenn die Zeile dabei sichtbar bleibt
        // (vor der ersten Zeile können Info-Zeilen liegen, nach der letzten Notizen).
        int max = Math.max(0, rows.getHeight() - viewport);
        if (isFirst && row.getBottom() + margin <= viewport) want = 0;
        else if (isLast && row.getTop() - margin >= max) want = max;
        scrollTo(want, immediate);
    }

    private int viewport() {
        return scroll.getHeight() - scroll.getPaddingTop() - scroll.getPaddingBottom();
    }

    /** Das Ziel einer laufenden sanften Bewegung (sonst gehen schnell aufeinanderfolgende Tastendrücke verloren). */
    private int effectiveScrollY() {
        boolean moving = SystemClock.uptimeMillis() - goalTime < GOAL_VALID_MS;
        return moving ? goalY : scroll.getScrollY();
    }

    private boolean scrollTo(int wanted, boolean immediate) {
        int max = Math.max(0, rows.getHeight() - viewport());
        int want = Math.max(0, Math.min(max, wanted));
        if (want == effectiveScrollY()) return false;
        goalY = want;
        goalTime = SystemClock.uptimeMillis();
        if (immediate) scroll.scrollTo(0, want);
        else scroll.smoothScrollTo(0, want);
        return true;
    }
}
