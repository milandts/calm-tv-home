package app.calmtv.home;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Linke Spalte der Einstellungen: eine Zeile je Kategorie. Die Kategorie unter dem Fokus wird sofort gewählt;
 * die gewählte bleibt auch ohne Fokus dezent hervorgehoben. Die Zeilenhöhe passt sich der Anzahl an (36 bis 52 dp),
 * bei sehr vielen Kategorien scrollt die Spalte.
 */
final class SettingsSidebar extends ScrollView {
    /** Meldungen an die Ansicht. */
    interface Listener {
        void onFocused(int index);

        void onClicked(int index);
    }

    private static final int ROW_MAX_DP = 52, ROW_MIN_DP = 30, GAP_DP = 2, ICON_DP = 22;
    /** Platz über (110 dp) und unter (40 dp) der Spalte. */
    private static final int RESERVED_DP = 150;

    private final ArrayList<View> rowViews = new ArrayList<>();
    private final ArrayList<SettingsFocus> focuses = new ArrayList<>();

    SettingsSidebar(Context c, List<SettingsModel.Page> categories, Listener listener) {
        super(c);
        setVerticalScrollBarEnabled(false);
        setOverScrollMode(OVER_SCROLL_NEVER);
        setVerticalFadingEdgeEnabled(true);   // bei Überlauf weicher Rand; die ScrollView hält den Fokus sichtbar
        setFadingEdgeLength(Ui.px(c, 28));
        setFocusable(false);
        LinearLayout column = new LinearLayout(c);
        column.setOrientation(LinearLayout.VERTICAL);
        addView(column, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        DisplayMetrics metrics = c.getResources().getDisplayMetrics();
        float heightDp = Ui.usableHeight(c) / metrics.density;
        int count = Math.max(1, categories.size());
        int rowDp = Math.round((heightDp - RESERVED_DP - (count - 1) * GAP_DP) / count);
        rowDp = Math.max(ROW_MIN_DP, Math.min(ROW_MAX_DP, rowDp));
        rowDp = Math.round(rowDp * Math.max(1f, Ui.sp(1f)));   // größere Schrift braucht höhere Zeilen

        for (int i = 0; i < categories.size(); i++) {
            final int index = i;
            SettingsModel.Page page = categories.get(i);
            LinearLayout row = new LinearLayout(c);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(Ui.px(c, 16), 0, Ui.px(c, 12), 0);
            GradientDrawable background = Ui.round(c, 0, Ui.RADIUS_ROW, 0, 0);
            row.setBackground(background);
            row.setFocusable(true);
            row.setClickable(true);
            SettingsFocus focus = new SettingsFocus(background, 0);

            if (page.icon != Icons.NONE) {
                Icons.Glyph glyph = Icons.icon(page.icon, Ui.TEXT_DIM);
                focus.addGlyph(glyph, Ui.TEXT_DIM, false, false);
                ImageView icon = new ImageView(c);
                icon.setImageDrawable(glyph);
                LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(Ui.px(c, ICON_DP), Ui.px(c, ICON_DP));
                ilp.setMarginEnd(Ui.px(c, 14));
                row.addView(icon, ilp);
            }
            TextView title = SettingsRowView.text(c, page.title, 17, 600, Ui.TEXT);
            title.setSingleLine();
            title.setEllipsize(TextUtils.TruncateAt.END);
            focus.addText(title, Ui.TEXT, false, false);
            row.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            focus.setGained(v -> listener.onFocused(index));
            row.setOnFocusChangeListener(focus);
            row.setOnClickListener(v -> listener.onClicked(index));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.px(c, rowDp));
            if (i > 0) lp.topMargin = Ui.px(c, GAP_DP);
            column.addView(row, lp);
            rowViews.add(row);
            focuses.add(focus);
        }
    }

    int size() {
        return rowViews.size();
    }

    /** Nummer der Zeile, die {@code view} ist, sonst -1. */
    int indexOf(View view) {
        return view == null ? -1 : rowViews.indexOf(view);
    }

    void focusIndex(int index) {
        if (index >= 0 && index < rowViews.size()) rowViews.get(index).requestFocus();
    }

    /** Hebt die gewählte Kategorie dezent hervor. */
    void setSelected(int index) {
        for (int i = 0; i < focuses.size(); i++) focuses.get(i).setRestFill(i == index ? Ui.ROW_PRESSED : 0);
    }
}
