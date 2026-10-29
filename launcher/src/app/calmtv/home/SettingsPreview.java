package app.calmtv.home;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Kompaktes Panel für Vorschau-Seiten: Seitentitel, je Auswahl (und Schalter) eine Zeile mit Chips, Buttons als Pillen
 * neben dem Titel. Links/Rechts wählt einen Chip und wendet ihn sofort an, Hoch/Runter wechselt die Zeile.
 * Die Seite dahinter (die echte Startseite) bleibt sichtbar.
 */
final class SettingsPreview extends FrameLayout {
    /** Aktionen, die die Ansicht ausführen muss (Bestätigung, Neuaufbau). */
    interface Actions {
        void runButton(SettingsModel.Button button);

        int optValue(SettingsModel.OptRow row);

        /** Schreibt einen neuen Wert; false, wenn gerade gesperrt (10-Sekunden-Test läuft). */
        boolean optChanged(SettingsModel.OptRow row, int value);

        /** Menütaste: zurück auf den Standard. */
        void optReset(SettingsModel.OptRow row);
    }

    private static final int PANEL_COLOR = 0xB3000000, ROW_HEIGHT_DP = 44, RANGE_TITLE_DP = 196, TITLE_COLUMN_DP = 196;

    /** Eine Chip-Zeile: Auswahl oder Schalter (Aus/An). */
    private static final class ChipRow {
        final SettingsModel.Choice choice;
        final SettingsModel.Toggle toggle;
        final SettingsModel.OptRow optRow;
        final Actions actions;
        final ArrayList<View> chips = new ArrayList<>();   // Chip oder SettingsSwatch
        TextView currentLine;   // "Aktuell: ..." bei Farbkreisen

        ChipRow(SettingsModel.Choice choice, SettingsModel.Toggle toggle, SettingsModel.OptRow optRow, Actions actions) {
            this.choice = choice;
            this.toggle = toggle;
            this.optRow = optRow;
            this.actions = actions;
        }

        int selectedIndex() {
            if (optRow != null) return actions.optValue(optRow);
            if (choice != null) return SettingsRowView.selectedIndex(choice);
            return Boolean.TRUE.equals(toggle.state.get()) ? 1 : 0;
        }

        void apply(int index) {
            if (choice != null) choice.select.set(index);
            else toggle.set.set(index == 1);
        }
    }

    /** Eine Zeile für die Pfeiltasten: Chips einer Auswahl oder die Pillen der Buttons. */
    private static final class Target {
        final ChipRow chipRow;
        final ArrayList<View> items = new ArrayList<>();
        SettingsModel.OptRow rangeRow;   // Regler-Zeile: items enthält nur den Regler
        SettingsSlider slider;
        TextView rangeValue;
        final SettingsStepper stepper = new SettingsStepper();

        Target(ChipRow chipRow) {
            this.chipRow = chipRow;
        }
    }

    /** Eine Info-Zeile, deren Wert beim Aktualisieren neu gelesen wird. */
    private static final class InfoLine {
        final TextView view;
        final SettingsModel.Info info;

        InfoLine(TextView view, SettingsModel.Info info) {
            this.view = view;
            this.info = info;
        }
    }

    private final Actions actions;
    /** Der scrollbare Inhalt des Panels (für das Scrollen ans Ende, siehe focusTarget). */
    private ScrollView scrollView;
    private final ArrayList<Target> targets = new ArrayList<>();
    private final ArrayList<InfoLine> infoLines = new ArrayList<>();

    SettingsPreview(Context c, SettingsModel.Page page, Actions actions) {
        super(c);
        this.actions = actions;
        // Die Ansicht selbst ist ein durchsichtiger Platz (feste Höhe); das Panel darin passt sich dem Inhalt an.
        LinearLayout panel = new LinearLayout(c);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(Ui.round(c, Ui.panel(PANEL_COLOR), Ui.RADIUS_PANEL, Ui.HAIRLINE, Math.max(1, Ui.px(c, 1))));
        panel.setClipToOutline(true);
        addView(panel, new LayoutParams(-1, -2, Gravity.CENTER_VERTICAL));

        LinearLayout titleLine = new LinearLayout(c);
        titleLine.setOrientation(LinearLayout.HORIZONTAL);
        titleLine.setGravity(Gravity.CENTER_VERTICAL);
        titleLine.setPadding(Ui.px(c, 24), Ui.px(c, 6), Ui.px(c, 16), 0);
        TextView title = SettingsRowView.text(c, page.title, 18, 600, Ui.TEXT);
        title.setSingleLine();
        title.setEllipsize(TextUtils.TruncateAt.END);
        titleLine.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        titleLine.setMinimumHeight(Ui.px(c, ROW_HEIGHT_DP));
        panel.addView(titleLine, new LinearLayout.LayoutParams(-1, -2));

        ScrollView scroll = new ScrollView(c);
        this.scrollView = scroll;
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(OVER_SCROLL_NEVER);
        scroll.setFocusable(false);
        scroll.setVerticalFadingEdgeEnabled(true);
        scroll.setFadingEdgeLength(Ui.px(c, 20));
        LinearLayout column = new LinearLayout(c);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(Ui.px(c, 24), 0, Ui.px(c, 24), Ui.px(c, 10));
        scroll.addView(column, new ScrollView.LayoutParams(-1, -2));
        // Der "Mehr"-Hinweis liegt über der Liste und erscheint nur, solange darunter noch Zeilen verborgen sind (wie auf den normalen Seiten).
        FrameLayout scrollHolder = new FrameLayout(c);
        scrollHolder.addView(scroll, new FrameLayout.LayoutParams(-1, -2));
        scrollHolder.addView(new SettingsScrollHint(c, scroll, column), new FrameLayout.LayoutParams(-1, -1));
        panel.addView(scrollHolder, new LinearLayout.LayoutParams(-1, -2));
        boolean hasOptions = false;
        for (SettingsModel.Row r : page.currentRows()) hasOptions |= r instanceof SettingsModel.OptRow;

        Target pills = new Target(null);
        ArrayList<TextView> notes = new ArrayList<>();
        List<SettingsModel.Row> rows = page.currentRows();
        for (SettingsModel.Row row : rows) {
            if (row instanceof SettingsModel.Choice) {
                addChipRow(c, column, new ChipRow((SettingsModel.Choice) row, null, null, actions));
            } else if (row instanceof SettingsModel.Toggle) {
                addChipRow(c, column, new ChipRow(null, (SettingsModel.Toggle) row, null, actions));
            } else if (row instanceof SettingsModel.OptRow) {
                SettingsModel.OptRow opt = (SettingsModel.OptRow) row;
                if (opt.opt.kind == Opt.RANGE) addRangeRow(c, column, opt);
                else addChipRow(c, column, new ChipRow(null, null, opt, actions));
            } else if (row instanceof SettingsModel.Button) {
                View pill = pill(c, (SettingsModel.Button) row);
                LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(-2, -2);
                plp.setMarginStart(Ui.px(c, 8));
                titleLine.addView(pill, plp);
                pills.items.add(pill);
            } else if (row instanceof SettingsModel.Info) {
                SettingsModel.Info info = (SettingsModel.Info) row;
                TextView line = SettingsRowView.text(c, infoText(info), Ui.SP_BODY, 500, Ui.TEXT_DIM);
                infoLines.add(new InfoLine(line, info));
                notes.add(line);
            } else if (row instanceof SettingsModel.Note) {
                notes.add(SettingsRowView.text(c, row.title, Ui.SP_BODY, 500, Ui.TEXT_DIM));
            } else {   // Überschriften und Links kann diese Ansicht nicht zeigen; scripts/check-rules.sh (Regel 2) fängt das schon beim Bauen ab
                Log.e(HomeKeyService.TAG, "preview page can't show row: " + row.title);
            }
        }
        for (TextView note : notes) {
            LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(-1, -2);
            nlp.topMargin = Ui.px(c, 8);
            column.addView(note, nlp);
        }
        if (hasOptions) {
            TextView hint = SettingsRowView.text(c, "Zurück: Änderungen prüfen  \u00B7  Menütaste: Zurücksetzen", Ui.SP_LABEL, 500, Ui.TEXT_DIM);
            LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(-1, -2);
            hlp.topMargin = Ui.px(c, 8);
            column.addView(hint, hlp);
        }
        if (!pills.items.isEmpty()) targets.add(0, pills);
        refresh();
    }

    /** Liest alle gewählten Werte und Infotexte neu und zeigt sie an (ohne die Ansicht neu zu bauen). */
    void refresh() {
        for (Target target : targets) {
            if (target.rangeRow != null) {
                int value = actions.optValue(target.rangeRow);
                target.slider.setRange(target.rangeRow.opt.min, target.rangeRow.opt.max, value);
                target.rangeValue.setText(target.rangeRow.opt.describe(value));
            }
            if (target.chipRow == null) continue;
            int selected = target.chipRow.selectedIndex();
            ChipRow row = target.chipRow;
            for (int i = 0; i < row.chips.size(); i++) {
                View option = row.chips.get(i);
                if (option instanceof SettingsSwatch) ((SettingsSwatch) option).setChosen(i == selected);
                else ((Chip) option).setChosen(i == selected);
            }
            if (row.currentLine != null) row.currentLine.setText("Aktuell: " + row.choice.options.get(selected));
        }
        for (InfoLine line : infoLines) line.view.setText(infoText(line.info));
    }

    /** Fokus auf den gewählten Chip der ersten Auswahl (sonst die erste Pille). */
    void focusInitial() {
        for (int t = 0; t < targets.size(); t++) {
            if (targets.get(t).chipRow != null) {
                focusTarget(t, 0);
                return;
            }
        }
        if (!targets.isEmpty()) focusTarget(0, 0);
    }

    /** Merkt die Fokusposition (Zeile * 100 + Eintrag), -1 wenn der Fokus nicht hier liegt. */
    int focusCode() {
        for (int t = 0; t < targets.size(); t++) {
            int i = indexInTarget(targets.get(t), findFocus());
            if (i >= 0) return t * 100 + i;
        }
        return -1;
    }

    void restoreFocus(int code) {
        int t = code / 100, i = code % 100;
        if (code < 0 || t >= targets.size() || i >= targets.get(t).items.size()) {
            focusInitial();
            return;
        }
        targets.get(t).items.get(i).requestFocus();
    }

    /** Verarbeitet eine Pfeiltaste. Gibt immer true zurück, die Taste ist verbraucht. */
    boolean onArrow(int keyCode, View focused, int repeat) {
        int t = -1, i = -1;
        for (int k = 0; k < targets.size() && t < 0; k++) {
            i = indexInTarget(targets.get(k), focused);
            if (i >= 0) t = k;
        }
        if (t < 0) {
            focusInitial();
            return true;
        }
        Target target = targets.get(t);
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            int next = t + (keyCode == KeyEvent.KEYCODE_DPAD_UP ? -1 : 1);
            if (next >= 0 && next < targets.size()) focusTarget(next, i);
            return true;
        }
        int direction = keyCode == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1;
        if (target.rangeRow != null) {
            int current = actions.optValue(target.rangeRow);
            int changed = target.stepper.next(target.rangeRow.opt, current, direction, repeat);
            if (changed != current && actions.optChanged(target.rangeRow, changed)) refresh();
            return true;
        }
        int next = i + direction;
        if (next >= 0 && next < target.items.size()) {
            target.items.get(next).requestFocus();
            if (target.chipRow != null) choose(target, next);
        }
        return true;
    }

    // ---- Aufbau ----------------------------------------------------------------------------------

    private void addChipRow(Context c, LinearLayout column, ChipRow chipRow) {
        List<String> labels = new ArrayList<>();
        List<Integer> swatches = null;
        String title;
        if (chipRow.choice != null) {
            labels = chipRow.choice.options;
            swatches = chipRow.choice.swatches;
            title = chipRow.choice.title;
        } else if (chipRow.optRow != null && chipRow.optRow.opt.kind == Opt.CHOICE) {
            labels = java.util.Arrays.asList(chipRow.optRow.opt.labels);
            title = chipRow.optRow.title;
        } else {
            labels.add("Aus");
            labels.add("An");
            title = chipRow.optRow != null ? chipRow.optRow.title : chipRow.toggle.title;
        }
        if (swatches != null && swatches.size() >= labels.size()) {
            addSwatchRow(c, column, chipRow);
            return;
        }
        Target target = new Target(chipRow);
        Flow flow = new Flow(c);
        for (int i = 0; i < labels.size(); i++) {
            final int index = i;
            Chip chip = new Chip(c, labels.get(i));
            chip.setOnClickListener(v -> choose(target, index));
            flow.addView(chip, new ViewGroup.LayoutParams(-2, -2));
            chipRow.chips.add(chip);
            target.items.add(chip);
        }
        targets.add(target);

        TextView label = SettingsRowView.text(c, title, 15, 600, Ui.TEXT);
        label.setMaxLines(2);
        label.setEllipsize(TextUtils.TruncateAt.END);
        boolean inline = swatches == null;   // viele Chips brechen im Flow um, die Beschriftung bleibt links
        LinearLayout line = new LinearLayout(c);
        line.setMinimumHeight(Ui.px(c, ROW_HEIGHT_DP));
        line.setOrientation(inline ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        line.setGravity(inline ? Gravity.CENTER_VERTICAL : Gravity.START);
        if (inline) {
            line.addView(label, new LinearLayout.LayoutParams(Ui.px(c, TITLE_COLUMN_DP), -2));
            line.addView(flow, new LinearLayout.LayoutParams(0, -2, 1f));
        } else {
            LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(-1, -2);
            llp.bottomMargin = Ui.px(c, 8);
            line.addView(label, llp);
            line.addView(flow, new LinearLayout.LayoutParams(-1, -2));
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Ui.px(c, 2);
        column.addView(line, lp);
    }

    /** Regler in der Leiste: Titel links, Leiste, Wert rechts. */
    private void addRangeRow(Context c, LinearLayout column, SettingsModel.OptRow opt) {
        Target target = new Target(null);
        target.rangeRow = opt;
        LinearLayout line = new LinearLayout(c);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.setMinimumHeight(Ui.px(c, ROW_HEIGHT_DP));
        TextView label = SettingsRowView.text(c, opt.title, 15, 600, Ui.TEXT);
        label.setMaxLines(2);
        label.setEllipsize(TextUtils.TruncateAt.END);
        line.addView(label, new LinearLayout.LayoutParams(Ui.px(c, RANGE_TITLE_DP), -2));
        target.slider = new SettingsSlider(c, true);
        target.slider.setRange(opt.opt.min, opt.opt.max, actions.optValue(opt));
        line.addView(target.slider, new LinearLayout.LayoutParams(0, -2, 1f));
        target.rangeValue = SettingsRowView.text(c, "", 15, 600, Ui.TEXT);
        target.rangeValue.setGravity(Gravity.END);
        line.addView(target.rangeValue, new LinearLayout.LayoutParams(Ui.px(c, 72), -2));
        target.items.add(target.slider);
        targets.add(target);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Ui.px(c, 2);
        column.addView(line, lp);
    }

    /** Eine Auswahl mit Farbwerten als Reihe von Farbkreisen, darüber die Zeile "Aktuell: Name". */
    private void addSwatchRow(Context c, LinearLayout column, ChipRow chipRow) {
        SettingsModel.Choice choice = chipRow.choice;
        Target target = new Target(chipRow);
        TextView current = SettingsRowView.text(c, "", Ui.SP_BODY, 600, Ui.TEXT);
        chipRow.currentLine = current;
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, -2);
        clp.topMargin = Ui.px(c, 6);
        column.addView(current, clp);

        LinearLayout line = new LinearLayout(c);
        line.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < choice.options.size(); i++) {
            final int index = i;
            SettingsSwatch swatch = new SettingsSwatch(c, choice.options.get(i), choice.swatches.get(i));
            swatch.setOnClickListener(v -> choose(target, index));
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-2, -2);
            if (i > 0) slp.setMarginStart(Ui.px(c, 8));
            line.addView(swatch, slp);
            chipRow.chips.add(swatch);
            target.items.add(swatch);
        }
        targets.add(target);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Ui.px(c, 4);
        column.addView(line, lp);
    }

    private View pill(Context c, SettingsModel.Button button) {
        int color = button.destructive ? Ui.DANGER : Ui.TEXT;
        TextView pill = SettingsRowView.text(c, button.title, 14, 600, color);
        pill.setSingleLine();
        pill.setGravity(Gravity.CENTER);
        pill.setPadding(Ui.px(c, 18), Ui.px(c, 9), Ui.px(c, 18), Ui.px(c, 9));
        GradientDrawable background = Ui.round(c, Ui.ROW, 100, 0, 0);
        pill.setBackground(background);
        pill.setFocusable(true);
        pill.setClickable(true);
        SettingsFocus focus = new SettingsFocus(background, Ui.ROW);
        focus.addText(pill, color, false, button.destructive);
        pill.setOnFocusChangeListener(focus);
        pill.setOnClickListener(v -> actions.runButton(button));
        return pill;
    }

    private static String infoText(SettingsModel.Info info) {
        String value = info.value == null ? null : info.value.get();
        return value == null || value.isEmpty() ? info.title : info.title + ": " + value;
    }

    // ---- Auswahl und Fokus -----------------------------------------------------------------------

    /** Menütaste: setzt die Einstellung unter dem Fokus auf den Standard. false, wenn dort keine liegt. */
    boolean resetFocused(View focused) {
        for (Target target : targets) {
            if (indexInTarget(target, focused) < 0) continue;
            SettingsModel.OptRow opt = target.rangeRow != null ? target.rangeRow
                    : target.chipRow != null ? target.chipRow.optRow : null;
            if (opt == null) return false;
            actions.optReset(opt);
            refresh();
            return true;
        }
        return false;
    }

    private void choose(Target target, int index) {
        if (target.chipRow.optRow != null) actions.optChanged(target.chipRow.optRow, index);
        else target.chipRow.apply(index);
        refresh();
    }

    private void focusTarget(int t, int preferredIndex) {
        Target target = targets.get(t);
        int index = target.chipRow != null ? target.chipRow.selectedIndex()
                : Math.min(preferredIndex, target.items.size() - 1);
        target.items.get(Math.max(0, index)).requestFocus();
        // Auf der letzten Zeile bis ans Ende scrollen: die Hinweistexte darunter (z. B. zum Notausgang) sind sonst abgeschnitten.
        if (t == targets.size() - 1 && scrollView != null) {
            final ScrollView scroll = scrollView;
            scroll.post(() -> {
                View column = scroll.getChildAt(0);
                if (column != null) scroll.smoothScrollTo(0, Math.max(0, column.getHeight() - scroll.getHeight()));
            });
        }
    }

    private static int indexInTarget(Target target, View focused) {
        return focused == null ? -1 : target.items.indexOf(focused);
    }

    /** Ordnet Chips von links nach rechts an und bricht bei Platzmangel um. */
    private static final class Flow extends ViewGroup {
        private final int hGap, vGap;

        Flow(Context c) {
            super(c);
            hGap = Ui.px(c, 8);
            vGap = Ui.px(c, 8);
        }

        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            int width = MeasureSpec.getSize(widthSpec);
            int x = 0, y = 0, lineHeight = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                child.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST),
                        MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                if (x > 0 && x + child.getMeasuredWidth() > width) {
                    x = 0;
                    y += lineHeight + vGap;
                    lineHeight = 0;
                }
                x += child.getMeasuredWidth() + hGap;
                lineHeight = Math.max(lineHeight, child.getMeasuredHeight());
            }
            setMeasuredDimension(width, y + lineHeight);
        }

        @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
            int width = r - l;
            int x = 0, y = 0, lineHeight = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                int w = child.getMeasuredWidth(), h = child.getMeasuredHeight();
                if (x > 0 && x + w > width) {
                    x = 0;
                    y += lineHeight + vGap;
                    lineHeight = 0;
                }
                child.layout(x, y, x + w, y + h);
                x += w + hGap;
                lineHeight = Math.max(lineHeight, h);
            }
        }
    }
}
