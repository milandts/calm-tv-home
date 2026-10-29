package app.calmtv.home;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

/**
 * Eine fokussierbare Zeile der Einstellungen (Link, Schalter, Auswahl, Button). Nicht fokussierbare Zeilen
 * (Abschnittskopf, Info, Notiz) entstehen über {@link #create} als einfache Ansichten ohne Fokus.
 * Werte werden beim Bauen frisch gelesen; nach einer Änderung baut {@link SettingsScreen} die Zeilen neu.
 */
final class SettingsRowView extends LinearLayout {
    /** Rückmeldungen der Zeile an die Seite. */
    interface Actions {
        void rowFocused(int rowIndex);

        void openLink(int rowIndex, SettingsModel.Link link);

        void toggled(SettingsModel.Toggle toggle, boolean on);

        void stepChoice(SettingsModel.Choice choice, int direction, boolean cycle);

        void runButton(SettingsModel.Button button);

        /** Aktueller Wert einer Einstellung. */
        int optValue(SettingsModel.OptRow row);

        /** Schreibt einen neuen Wert; false, wenn gerade gesperrt (10-Sekunden-Test läuft). */
        boolean optChanged(SettingsModel.OptRow row, int value);
    }

    static final int GAP_DP = 6;
    private static final int MIN_HEIGHT_DP = 56, PADDING_DP = 16, ICON_DP = 22, ARROW_DP = 18;
    private static final int RANGE_WIDTH_DP = 260, SWITCH_WIDTH_DP = 52, SWITCH_HEIGHT_DP = 30, VALUE_MAX_WIDTH_DP = 280;

    private final SettingsModel.Row row;
    private final int index;
    private final Actions actions;
    private final SettingsStepper stepper = new SettingsStepper();
    private GlassSwitch toggleView;
    private SettingsSlider sliderView;
    private TextView titleView, valueView;

    /** Baut die passende Ansicht zur Zeile; {@code separated}: unter einer Info-Zeile eine feine Trennlinie. */
    static View create(Context c, SettingsModel.Row row, int index, Actions actions, boolean separated) {
        if (row instanceof SettingsModel.Header) return header(c, row.title, index == 0);
        if (row instanceof SettingsModel.Note) return note(c, row.title);
        if (row instanceof SettingsModel.Info) return info(c, (SettingsModel.Info) row, separated);
        return new SettingsRowView(c, row, index, actions);
    }

    /** Text in Nunito ohne Schatten (die Zeilen liegen auf einem dunklen Panel). */
    static TextView text(Context c, CharSequence s, float sp, int weight, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(Ui.sp(sp));
        t.setTypeface(Ui.font(c, weight));
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        t.setLineSpacing(Ui.px(c, 2), 1f);
        return t;
    }

    /** Der gewählte Wert einer Auswahl, auf den gültigen Bereich begrenzt. */
    static int selectedIndex(SettingsModel.Choice choice) {
        Integer value = choice.selected.get();
        int count = choice.options.size();
        if (value == null || count == 0) return 0;
        return Math.max(0, Math.min(count - 1, value));
    }

    /** Eine Zeile, die nur Text zeigt ("Keine Einträge" o. ä.). */
    static View emptyHint(Context c, String message) {
        return note(c, message);
    }

    private SettingsRowView(Context c, SettingsModel.Row row, int index, Actions actions) {
        super(c);
        this.row = row;
        this.index = index;
        this.actions = actions;
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setMinimumHeight(Ui.px(c, MIN_HEIGHT_DP));
        int padH = Ui.px(c, PADDING_DP), padV = Ui.px(c, 10);
        setPadding(padH, padV, padH, padV);
        GradientDrawable background = Ui.round(c, Ui.ROW, Ui.RADIUS_ROW, 0, 0);
        setBackground(background);
        setFocusable(true);
        setClickable(true);

        SettingsFocus focus = new SettingsFocus(background, Ui.ROW);
        boolean danger = row instanceof SettingsModel.Button && ((SettingsModel.Button) row).destructive;

        if (row.icon != Icons.NONE) {
            int iconColor = danger ? Ui.DANGER : Ui.TEXT_DIM;
            Icons.Glyph glyph = Icons.icon(row.icon, iconColor);
            focus.addGlyph(glyph, iconColor, !danger, danger);
            ImageView icon = new ImageView(c);
            icon.setImageDrawable(glyph);
            LayoutParams ilp = new LayoutParams(Ui.px(c, ICON_DP), Ui.px(c, ICON_DP));
            ilp.setMarginEnd(Ui.px(c, 14));
            addView(icon, ilp);
        }

        LinearLayout column = textColumn(c, row, danger ? Ui.DANGER : Ui.TEXT, danger, focus);
        titleView = (TextView) column.getChildAt(0);
        addView(column, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

        if (row instanceof SettingsModel.Link) {
            Icons.Glyph arrow = Icons.icon(Icons.PFEIL_RECHTS, Ui.TEXT_DIM);
            focus.addGlyph(arrow, Ui.TEXT_DIM, true, false);
            ImageView view = new ImageView(c);
            view.setImageDrawable(arrow);
            LayoutParams alp = new LayoutParams(Ui.px(c, ARROW_DP), Ui.px(c, ARROW_DP));
            alp.setMarginStart(Ui.px(c, 12));
            addView(view, alp);
        } else if (row instanceof SettingsModel.Toggle) {
            toggleView = new GlassSwitch(c);
            toggleView.setChecked(Boolean.TRUE.equals(((SettingsModel.Toggle) row).state.get()));
            focus.setSwitch(toggleView);
            LayoutParams slp = new LayoutParams(Ui.px(c, SWITCH_WIDTH_DP), Ui.px(c, SWITCH_HEIGHT_DP));
            slp.setMarginStart(Ui.px(c, 16));
            addView(toggleView, slp);
        } else if (row instanceof SettingsModel.Choice) {
            addChoiceValue(c, (SettingsModel.Choice) row, focus);
        } else if (row instanceof SettingsModel.OptRow) {
            addOption(c, (SettingsModel.OptRow) row, focus);
        }

        focus.setGained(v -> actions.rowFocused(this.index));
        setOnFocusChangeListener(focus);
        setOnClickListener(v -> activate());
    }

    int index() {
        return index;
    }

    /** Die Einstellung dieser Zeile oder null. */
    SettingsModel.OptRow optRow() {
        return row instanceof SettingsModel.OptRow ? (SettingsModel.OptRow) row : null;
    }

    /**
     * Links (-1) / Rechts (+1) auf Auswahl und Regler: ändert den Wert ohne Umlauf. true = die Taste ist verbraucht
     * (auch am Ende des Bereichs), false = die Zeile kennt keine Links/Rechts-Bedienung.
     */
    boolean stepHorizontal(int direction, int repeat) {
        if (row instanceof SettingsModel.Choice) {
            actions.stepChoice((SettingsModel.Choice) row, direction, false);
            return true;
        }
        if (!(row instanceof SettingsModel.OptRow)) return false;
        SettingsModel.OptRow opt = (SettingsModel.OptRow) row;
        if (opt.opt.kind == Opt.BOOL) return false;
        int current = actions.optValue(opt);
        int next = stepper.next(opt.opt, current, direction, repeat);
        if (next != current && actions.optChanged(opt, next) && opt.opt.kind == Opt.RANGE) refreshRange(opt);
        return true;
    }

    private void activate() {
        if (row instanceof SettingsModel.Link) {
            actions.openLink(index, (SettingsModel.Link) row);
        } else if (row instanceof SettingsModel.Toggle) {
            boolean on = toggleView.toggle();
            actions.toggled((SettingsModel.Toggle) row, on);
        } else if (row instanceof SettingsModel.Choice) {
            actions.stepChoice((SettingsModel.Choice) row, 1, true);
        } else if (row instanceof SettingsModel.OptRow) {
            activateOption((SettingsModel.OptRow) row);
        } else if (row instanceof SettingsModel.Button) {
            actions.runButton((SettingsModel.Button) row);
        }
    }

    private void activateOption(SettingsModel.OptRow opt) {
        int current = actions.optValue(opt);
        if (opt.opt.kind == Opt.BOOL) {
            boolean on = toggleView.toggle();
            if (!actions.optChanged(opt, on ? 1 : 0)) toggleView.setChecked(current != 0);
        } else if (opt.opt.kind == Opt.CHOICE) {
            int count = opt.opt.labels.length;
            if (count > 0) actions.optChanged(opt, (current + 1) % count);
        }   // Regler: die Auswahltaste tut nichts
    }

    private void addOption(Context c, SettingsModel.OptRow opt, SettingsFocus focus) {
        int value = actions.optValue(opt);
        if (opt.opt.kind == Opt.BOOL) {
            toggleView = new GlassSwitch(c);
            toggleView.setChecked(value != 0);
            focus.setSwitch(toggleView);
            LayoutParams slp = new LayoutParams(Ui.px(c, SWITCH_WIDTH_DP), Ui.px(c, SWITCH_HEIGHT_DP));
            slp.setMarginStart(Ui.px(c, 16));
            addView(toggleView, slp);
        } else if (opt.opt.kind == Opt.CHOICE) {
            valueView = text(c, opt.opt.describe(value), 16, 500, Ui.TEXT_DIM);
            valueView.setSingleLine();
            valueView.setEllipsize(TextUtils.TruncateAt.END);
            valueView.setMaxWidth(Ui.px(c, VALUE_MAX_WIDTH_DP));
            focus.addText(valueView, Ui.TEXT_DIM, true, false);
            LayoutParams vlp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            vlp.setMarginStart(Ui.px(c, 10));
            addView(valueView, vlp);
        } else {
            LinearLayout right = new LinearLayout(c);
            right.setOrientation(VERTICAL);
            valueView = text(c, opt.opt.describe(value), 16, 500, Ui.TEXT_DIM);
            valueView.setGravity(Gravity.END);
            focus.addText(valueView, Ui.TEXT_DIM, true, false);
            right.addView(valueView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            sliderView = new SettingsSlider(c, false);
            sliderView.setRange(opt.opt.min, opt.opt.max, value);
            focus.setSlider(sliderView);
            LayoutParams slp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            slp.topMargin = Ui.px(c, 4);
            right.addView(sliderView, slp);
            // Bei schmalem Panel (großer Sicherheitsrand) darf der Regler höchstens 40 % der Zeile belegen, sonst wird der Titel zu "Si-cher…".
            float panelDp = Ui.usableWidth(c) / c.getResources().getDisplayMetrics().density - 64 - 260 - 28 - 64 - 80;
            int rangeDp = Math.max(120, Math.min(RANGE_WIDTH_DP, Math.round(panelDp * 0.4f)));
            LayoutParams rlp = new LayoutParams(Ui.px(c, rangeDp), LayoutParams.WRAP_CONTENT);
            rlp.setMarginStart(Ui.px(c, 16));
            addView(right, rlp);
        }
        showChangedDot(opt.opt, value);
    }

    /** Liest den Wert eines Reglers neu und zeigt ihn an, ohne die Zeile neu zu bauen (gehaltene Taste). */
    private void refreshRange(SettingsModel.OptRow opt) {
        int value = actions.optValue(opt);
        sliderView.setRange(opt.opt.min, opt.opt.max, value);
        valueView.setText(opt.opt.describe(value));
        showChangedDot(opt.opt, value);
    }

    /** Kleiner Akzentpunkt hinter dem Titel, wenn der Wert vom Standard abweicht. */
    private void showChangedDot(Opt opt, int value) {
        if (value == opt.def) {
            titleView.setCompoundDrawablesRelative(null, null, null, null);
            return;
        }
        int size = Ui.px(getContext(), 6);
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(Look.focusRing());
        dot.setBounds(0, 0, size, size);
        titleView.setCompoundDrawablesRelative(null, null, dot, null);
        titleView.setCompoundDrawablePadding(Ui.px(getContext(), 8));
    }

    // ---- Bausteine -------------------------------------------------------------------------------

    private static LinearLayout textColumn(Context c, SettingsModel.Row row, int titleColor, boolean danger, SettingsFocus focus) {
        LinearLayout column = new LinearLayout(c);
        column.setOrientation(VERTICAL);
        TextView title = text(c, row.title, 17, 600, titleColor);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        if (focus != null) focus.addText(title, titleColor, false, danger);
        column.addView(title, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        if (!row.subtitle.isEmpty()) {
            TextView subtitle = text(c, row.subtitle, Ui.SP_BODY, 500, Ui.TEXT_DIM);
            subtitle.setMaxLines(3);
            subtitle.setEllipsize(TextUtils.TruncateAt.END);
            if (focus != null) focus.addText(subtitle, Ui.TEXT_DIM, true, false);
            LayoutParams slp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            slp.topMargin = Ui.px(c, 3);
            column.addView(subtitle, slp);
        }
        return column;
    }

    private void addChoiceValue(Context c, SettingsModel.Choice choice, SettingsFocus focus) {
        int selected = selectedIndex(choice);
        if (choice.swatches != null && selected < choice.swatches.size()) {
            View dot = new View(c);
            GradientDrawable oval = new GradientDrawable();
            oval.setShape(GradientDrawable.OVAL);
            oval.setColor(choice.swatches.get(selected));
            oval.setStroke(Math.max(1, Ui.px(c, 1)), Ui.HAIRLINE);
            dot.setBackground(oval);
            LayoutParams dlp = new LayoutParams(Ui.px(c, 14), Ui.px(c, 14));
            dlp.setMarginStart(Ui.px(c, 12));
            addView(dot, dlp);
        }
        String label = choice.options.isEmpty() ? "" : choice.options.get(selected);
        TextView value = text(c, label, 16, 500, Ui.TEXT_DIM);
        value.setSingleLine();
        value.setEllipsize(TextUtils.TruncateAt.END);
        value.setMaxWidth(Ui.px(c, VALUE_MAX_WIDTH_DP));
        focus.addText(value, Ui.TEXT_DIM, true, false);
        LayoutParams vlp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        vlp.setMarginStart(Ui.px(c, 10));
        addView(value, vlp);
    }

    private static View header(Context c, String title, boolean first) {
        TextView t = text(c, title.toUpperCase(Locale.GERMAN), Ui.SP_CAPS, 700, Ui.TEXT_DIM);
        t.setLetterSpacing(0.12f);
        t.setPadding(Ui.px(c, PADDING_DP), Ui.px(c, first ? 4 : 20), Ui.px(c, PADDING_DP), Ui.px(c, 6));
        return t;
    }

    private static View note(Context c, String text) {
        TextView t = text(c, text, Ui.SP_BODY, 500, Ui.TEXT_DIM);
        t.setLineSpacing(Ui.px(c, 3), 1f);
        t.setPadding(Ui.px(c, PADDING_DP), Ui.px(c, 10), Ui.px(c, PADDING_DP), Ui.px(c, 10));
        return t;
    }

    private static View info(Context c, SettingsModel.Info info, boolean separated) {
        LinearLayout line = new LinearLayout(c);
        line.setOrientation(HORIZONTAL);   // Standard-Schwerkraft oben: Titel und Wert stehen auf einer Grundlinie
        line.setMinimumHeight(Ui.px(c, MIN_HEIGHT_DP));
        line.setPadding(Ui.px(c, PADDING_DP), Ui.px(c, 16), Ui.px(c, PADDING_DP), Ui.px(c, 16));
        line.addView(textColumn(c, info, Ui.TEXT, false, null), new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        String shown = info.value == null ? null : info.value.get();
        TextView value = text(c, shown == null ? "" : shown, 16, 500, Ui.TEXT_DIM);
        value.setGravity(Gravity.END);
        value.setMaxWidth(Ui.px(c, VALUE_MAX_WIDTH_DP));
        LayoutParams vlp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        vlp.setMarginStart(Ui.px(c, 16));
        line.addView(value, vlp);
        if (!separated) return line;
        LinearLayout block = new LinearLayout(c);
        block.setOrientation(VERTICAL);
        block.addView(line, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        View divider = new View(c);
        divider.setBackgroundColor(Ui.HAIRLINE);
        LayoutParams dlp = new LayoutParams(LayoutParams.MATCH_PARENT, Math.max(1, Ui.px(c, 1)));
        dlp.setMargins(Ui.px(c, PADDING_DP), 0, Ui.px(c, PADDING_DP), 0);
        block.addView(divider, dlp);
        return block;
    }
}
