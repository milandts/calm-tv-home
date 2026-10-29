package app.calmtv.home;

import android.content.Context;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Einführung beim ersten Start (wiederholbar über {@link HomeHost#showWelcome}). Fünf Schritte in einem Glas-Panel
 * rechts von Uhr und Datum; in den Schritten mit Live-Vorschau (Farbe, Hintergrund) wird der Schleier durchsichtiger,
 * damit man die echte Startseite sieht. Am Ende ruft {@link #finish} {@code host.closeScreen()} und danach
 * {@code onFinished}: Welcome schließt sich also selbst, der Aufrufer muss nichts schließen.
 */
final class Welcome implements Screen {
    private static final String PREFS = "layout", KEY_DONE = "welcomeDone";
    private static final int STEP_WELCOME = 0, STEP_COLOR = 1, STEP_BACKGROUND = 2, STEP_USAGE = 3, STEP_READY = 4, STEP_COUNT = 5;

    private static final float PANEL_WIDTH_DP = 632, PANEL_HEIGHT_DP = 320, SLIDE_DP = 40;
    // Freie Zone der Startseite: y 74 bis 398 dp (Button-Leiste oben, Dock unten); links oben stehen Uhr und Datum bis x = 270 dp.
    private static final float PANEL_TOP_DP = 76, PANEL_RIGHT_DP = 48, PANEL_MIN_LEFT_DP = 280;
    private static final int PANEL_FILL = Ui.PANEL_PHOTO;
    private static final float PREVIEW_SCRIM_ALPHA = 0.45f;

    private final HomeHost host;
    private final Context context;
    private final Look look;
    private final Runnable onFinished;
    private final View.OnKeyListener stepKeys = this::onStepKey;

    private final FrameLayout root;
    private final View scrim;
    private final LinearLayout stage;
    private final WelcomeDots dots;
    private final WelcomeButton skipButton;
    private final WelcomeButton backButton;
    private final WelcomeButton nextButton;

    /** Fokus-Zeilen des aktuellen Schritts von oben nach unten: Auswahlzeilen, Fußzeile. */
    private final List<List<View>> rows = new ArrayList<>();
    private final List<List<View>> choiceRows = new ArrayList<>();
    private int[] rowMemory = new int[0];
    private int step;
    private boolean isPreviewOn;
    private boolean isFinished;

    Welcome(HomeHost host, Runnable onFinished) {
        this.host = host;
        this.context = host.context();
        this.look = host.look();
        this.onFinished = onFinished;

        root = new FrameLayout(context);
        scrim = new View(context);
        scrim.setBackgroundColor(Ui.SCRIM);
        root.addView(scrim, new FrameLayout.LayoutParams(-1, -1));

        skipButton = new WelcomeButton(context, "Überspringen", WelcomeButton.GHOST);
        skipButton.setOnClickListener(v -> finish());
        skipButton.setOnKeyListener(stepKeys);

        LinearLayout panel = buildPanel();
        stage = new LinearLayout(context);
        stage.setOrientation(LinearLayout.VERTICAL);
        stage.setClipChildren(false);
        panel.addView(stage, new LinearLayout.LayoutParams(-1, 0, 1f));

        dots = new WelcomeDots(context, STEP_COUNT);
        backButton = new WelcomeButton(context, "Zurück", WelcomeButton.SECONDARY);
        nextButton = new WelcomeButton(context, "Weiter", WelcomeButton.PRIMARY);
        backButton.setOnClickListener(v -> goTo(step - 1, -1));
        nextButton.setOnClickListener(v -> onNext());
        backButton.setOnKeyListener(stepKeys);
        nextButton.setOnKeyListener(stepKeys);
        panel.addView(buildFooter(), new LinearLayout.LayoutParams(-1, Ui.px(context, 44)));

        showStep(STEP_WELCOME);
    }

    // ---- Zustand der Einführung ------------------------------------------------------------------

    /** True, wenn die Einführung schon einmal beendet oder übersprungen wurde. */
    static boolean isDone(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_DONE, false);
    }

    static void markDone(Context c) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_DONE, true).apply();
    }

    // ---- Aufbau ----------------------------------------------------------------------------------

    /** Glas-Panel in der freien Zone rechts von Uhr und Datum; gibt die senkrechte Spalte für Inhalt und Fußzeile zurück. */
    private LinearLayout buildPanel() {
        int screenWidth = Ui.usableWidth(context);
        int width = Math.min(Ui.px(context, PANEL_WIDTH_DP), screenWidth - Ui.px(context, PANEL_MIN_LEFT_DP + PANEL_RIGHT_DP));
        FrameLayout frame = new FrameLayout(context);
        frame.setBackground(Ui.round(context, PANEL_FILL, Ui.RADIUS_PANEL, Ui.HAIRLINE, Math.max(1, Ui.px(context, 1))));
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(Ui.px(context, 32), Ui.px(context, 24), Ui.px(context, 32), Ui.px(context, 18));
        // Fokus-Vergrößerung (Buttons, Farbfelder) darf über die Ränder der Eltern hinausragen.
        frame.setClipChildren(false);
        column.setClipChildren(false);
        column.setClipToPadding(false);
        frame.addView(column, new FrameLayout.LayoutParams(-1, -1));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(width, panelHeight(width), Gravity.TOP | Gravity.END);
        lp.topMargin = Ui.px(context, PANEL_TOP_DP);
        lp.rightMargin = Ui.px(context, PANEL_RIGHT_DP);
        root.addView(frame, lp);
        return column;
    }

    /** Schmales Panel (großer Sicherheitsrand) bricht Titel um und braucht mehr Höhe; nie höher als der nutzbare Bildschirm. */
    private int panelHeight(int widthPx) {
        float fullWidth = Ui.px(context, PANEL_WIDTH_DP);
        float missing = Math.max(0f, 1f - widthPx / fullWidth);
        int wanted = Ui.px(context, PANEL_HEIGHT_DP + Math.round(missing * 480f));
        int room = Ui.usableHeight(context) - Ui.px(context, PANEL_TOP_DP + 16);
        return Math.min(wanted, room);
    }

    private LinearLayout buildFooter() {
        LinearLayout footer = new LinearLayout(context);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        footer.setClipChildren(false);
        footer.addView(skipButton, new LinearLayout.LayoutParams(-2, Ui.px(context, 36)));
        LinearLayout.LayoutParams dotsParams = new LinearLayout.LayoutParams(-2, -2);
        dotsParams.leftMargin = Ui.px(context, 14);
        footer.addView(dots, dotsParams);
        footer.addView(new View(context), new LinearLayout.LayoutParams(0, 1, 1f));
        footer.addView(backButton, new LinearLayout.LayoutParams(-2, -1));
        LinearLayout.LayoutParams nextParams = new LinearLayout.LayoutParams(-2, -1);
        nextParams.leftMargin = Ui.px(context, 12);
        footer.addView(nextButton, nextParams);
        return footer;
    }

    private LinearLayout.LayoutParams topMargin(float dips) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Ui.px(context, dips);
        return lp;
    }

    private void addTitle(String text) {
        stage.addView(Ui.label(context, text, 30, 300, Ui.TEXT), new LinearLayout.LayoutParams(-1, -2));
    }

    private void addBody(String text) {
        TextView body = Ui.label(context, text, 17, 400, Ui.TEXT_DIM);
        body.setLineSpacing(0, 1.15f);
        stage.addView(body, topMargin(10));
    }

    /** Kleine Beschriftung über einer Auswahlzeile. */
    private void addSectionLabel(String text, float topDips) {
        TextView label = Ui.label(context, text.toUpperCase(Locale.GERMAN), Ui.SP_CAPS, 700, Ui.TEXT_FAINT);
        label.setLetterSpacing(0.12f);
        stage.addView(label, topMargin(topDips));
    }

    // ---- Schritte --------------------------------------------------------------------------------

    private void onNext() {
        if (step >= STEP_COUNT - 1) finish();
        else goTo(step + 1, 1);
    }

    /** Wechselt den Schritt; direction +1 gleitet von rechts ein, -1 von links. */
    private void goTo(int newStep, int direction) {
        if (newStep < 0 || newStep >= STEP_COUNT) return;
        showStep(newStep);
        stage.setAlpha(0f);
        stage.setTranslationX(direction * Ui.px(context, SLIDE_DP));
        stage.animate().alpha(1f).translationX(0f).setDuration(Ui.MEDIUM).setInterpolator(Ui.EASE).start();
        nextButton.requestFocus();
    }

    private void showStep(int newStep) {
        step = newStep;
        stage.removeAllViews();
        choiceRows.clear();
        // Schritte ohne Auswahl (Willkommen, Alles bereit) stehen mittig in der festen Panelhöhe.
        boolean isCentered = step == STEP_WELCOME || step == STEP_READY;
        stage.setGravity(isCentered ? Gravity.CENTER_VERTICAL : Gravity.TOP);
        switch (step) {
            case STEP_WELCOME: buildWelcomeStep(); break;
            case STEP_COLOR: buildColorStep(); break;
            case STEP_BACKGROUND: buildBackgroundStep(); break;
            case STEP_USAGE: buildUsageStep(); break;
            default: buildReadyStep(); break;
        }
        backButton.setVisibility(step == STEP_WELCOME ? View.GONE : View.VISIBLE);
        nextButton.setText(step == STEP_READY ? "Los geht's" : "Weiter");
        dots.setStep(step);
        setPreview(step == STEP_COLOR || step == STEP_BACKGROUND);
        rebuildRows();
    }

    /** Vorschau-Modus nur in den Schritten, in denen man etwas am Aussehen ändert. */
    private void setPreview(boolean on) {
        if (on != isPreviewOn) {
            isPreviewOn = on;
            host.setPreviewMode(on);
        }
        scrim.animate().alpha(on ? PREVIEW_SCRIM_ALPHA : 1f).setDuration(Ui.MEDIUM).setInterpolator(Ui.EASE).start();
    }

    private void buildWelcomeStep() {
        addTitle("Willkommen bei TV Home");
        addBody("TV Home ist werbefrei, ruhig und ohne Tracker. Es merkt sich nur, was du hier einstellst, "
                + "und das bleibt auf diesem Gerät.");
        LinearLayout tags = new LinearLayout(context);
        tags.setOrientation(LinearLayout.HORIZONTAL);
        for (String tag : new String[] {"Werbefrei", "Ruhig", "Ohne Tracker"}) {
            TextView t = Ui.label(context, tag, Ui.SP_BODY, 600, Ui.TEXT);
            t.setGravity(Gravity.CENTER);
            t.setPadding(Ui.px(context, 16), 0, Ui.px(context, 16), 0);
            t.setBackground(Ui.round(context, Ui.ROW, 100, 0, 0));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, Ui.px(context, 32));
            lp.rightMargin = Ui.px(context, 10);
            tags.addView(t, lp);
        }
        stage.addView(tags, topMargin(20));
    }

    private void buildColorStep() {
        addTitle("Wähle deine Farbe");
        addBody("Mit dieser Farbe leuchtet die Auswahl auf, wenn du dich durch TV Home bewegst.");
        final WelcomeSwatch[] swatches = new WelcomeSwatch[Look.ACCENT_NAMES.length];
        final TextView caption = Ui.label(context, "", 14, 600, Ui.TEXT_DIM);
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        List<View> focusRow = new ArrayList<>();
        for (int i = 0; i < swatches.length; i++) {
            final int index = i;
            WelcomeSwatch swatch = new WelcomeSwatch(context, i);
            swatch.setChosen(i == look.accentIndex());
            swatch.setOnKeyListener(stepKeys);
            swatch.setOnClickListener(v -> chooseAccent(index, swatches, caption));
            swatches[i] = swatch;
            focusRow.add(swatch);
            row.addView(swatch, new LinearLayout.LayoutParams(Ui.px(context, 52), Ui.px(context, 52)));
        }
        caption.setText("Aktuell: " + Look.ACCENT_NAMES[look.accentIndex()]);
        stage.addView(row, topMargin(14));
        stage.addView(caption, topMargin(2));
        choiceRows.add(focusRow);
    }

    private void chooseAccent(int index, WelcomeSwatch[] swatches, TextView caption) {
        look.setAccentIndex(index);
        host.applyLook();
        for (int i = 0; i < swatches.length; i++) swatches[i].setChosen(i == index);
        caption.setText("Aktuell: " + Look.ACCENT_NAMES[index]);
    }

    private void buildBackgroundStep() {
        addTitle("Wie soll dein Hintergrund aussehen?");
        addBody("Probiere es aus: Du siehst die Änderung sofort.");

        addSectionLabel("Schärfe", 12);
        final Chip[] sharpness = {new Chip(context, "Scharf"), new Chip(context, "Unscharf")};
        for (int i = 0; i < sharpness.length; i++) {
            final boolean blurred = i == 1;
            sharpness[i].setChosen(blurred == look.isBlurred());
            sharpness[i].setOnClickListener(v -> {
                look.setBlurred(blurred);
                host.applyLook();
                for (Chip chip : sharpness) chip.setChosen(chip == v);
            });
        }
        stage.addView(chipRow(sharpness), topMargin(6));

        addSectionLabel("Abdunkeln", 10);
        final Chip[] dimming = new Chip[Look.DIM_PERCENT.length];
        for (int i = 0; i < dimming.length; i++) {
            final int level = i;
            dimming[i] = new Chip(context, Look.DIM_PERCENT[i] + " %");
            dimming[i].setChosen(level == look.dimLevel());
            dimming[i].setOnClickListener(v -> {
                look.setDimLevel(level);
                host.applyLook();
                for (Chip chip : dimming) chip.setChosen(chip == v);
            });
        }
        stage.addView(chipRow(dimming), topMargin(6));
    }

    /** Legt Chips nebeneinander und trägt sie als Fokus-Zeile ein. */
    private LinearLayout chipRow(Chip[] chips) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        List<View> focusRow = new ArrayList<>();
        for (Chip chip : chips) {
            chip.setGravity(Gravity.CENTER);
            chip.setMinWidth(Ui.px(context, 88));
            chip.setOnKeyListener(stepKeys);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, Ui.px(context, 36));
            lp.rightMargin = Ui.px(context, 10);
            row.addView(chip, lp);
            focusRow.add(chip);
        }
        choiceRows.add(focusRow);
        return row;
    }

    private void buildUsageStep() {
        addTitle("So bedienst du TV Home");
        stage.addView(usageRow(Icons.DOCK, "Menütaste auf einer App",
                "Öffnet ein Menü: ins Dock legen, in einen Ordner sortieren oder umbenennen."), topMargin(12));
        stage.addView(usageRow(Icons.EINSTELLUNGEN, "Menütaste oben rechts",
                "Öffnet das Kontrollzentrum mit den Schnellschaltern."), topMargin(10));
        stage.addView(usageRow(Icons.PFEIL_ZURUECK, "Zurück",
                "Geht immer eine Ebene höher."), topMargin(10));
    }

    private LinearLayout usageRow(int iconId, String title, String text) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout badge = new FrameLayout(context);
        badge.setBackground(Ui.round(context, Ui.ROW, 100, 0, 0));
        ImageView icon = new ImageView(context);
        icon.setImageDrawable(Icons.icon(iconId, Ui.TEXT));
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(Ui.px(context, 22), Ui.px(context, 22), Gravity.CENTER);
        badge.addView(icon, iconParams);
        row.addView(badge, new LinearLayout.LayoutParams(Ui.px(context, 44), Ui.px(context, 44)));

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(Ui.label(context, title, 17, 600, Ui.TEXT), new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams detail = new LinearLayout.LayoutParams(-1, -2);
        detail.topMargin = Ui.px(context, 2);
        texts.addView(Ui.label(context, text, 14, 400, Ui.TEXT_DIM), detail);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, -2, 1f);
        textParams.leftMargin = Ui.px(context, 16);
        row.addView(texts, textParams);
        return row;
    }

    private void buildReadyStep() {
        addTitle("Alles bereit");
        addBody("Du kannst jede Einstellung später ändern. Das Wetter für den Bildschirmschoner richtest du in den "
                + "Einstellungen unter Bildschirmschoner ein. Diese Einführung findest du dort unter Info wieder.");
    }

    // ---- Navigation ------------------------------------------------------------------------------

    /** Baut die Fokus-Zeilen neu auf: die Auswahlzeilen oben, unten die Fußzeile (Überspringen, Zurück, Weiter). */
    private void rebuildRows() {
        rows.clear();
        rows.addAll(choiceRows);
        List<View> buttons = new ArrayList<>();
        buttons.add(skipButton);
        if (step != STEP_WELCOME) buttons.add(backButton);
        buttons.add(nextButton);
        rows.add(buttons);
        rowMemory = new int[rows.size()];
        rowMemory[rows.size() - 1] = buttons.size() - 1;   // von oben landet man auf "Weiter"
    }

    /** Pfeiltasten bleiben in den Zeilen und werden am Rand verbraucht; Menü tut hier nichts; Zurück geht an den Host. */
    private boolean onStepKey(View view, int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) return true;
        int rowStep = 0, columnStep = 0;
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP: rowStep = -1; break;
            case KeyEvent.KEYCODE_DPAD_DOWN: rowStep = 1; break;
            case KeyEvent.KEYCODE_DPAD_LEFT: columnStep = -1; break;
            case KeyEvent.KEYCODE_DPAD_RIGHT: columnStep = 1; break;
            default: return false;
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN) moveFocus(view, rowStep, columnStep);
        return true;
    }

    private void moveFocus(View from, int rowStep, int columnStep) {
        int row = -1, column = -1;
        for (int r = 0; r < rows.size(); r++) {
            int c = rows.get(r).indexOf(from);
            if (c >= 0) {
                row = r;
                column = c;
            }
        }
        if (row < 0) return;
        if (rowStep != 0) {
            int targetRow = row + rowStep;
            if (targetRow < 0 || targetRow >= rows.size()) return;
            rowMemory[row] = column;
            List<View> target = rows.get(targetRow);
            target.get(Math.min(rowMemory[targetRow], target.size() - 1)).requestFocus();
        } else {
            int targetColumn = column + columnStep;
            List<View> current = rows.get(row);
            if (targetColumn < 0 || targetColumn >= current.size()) return;
            current.get(targetColumn).requestFocus();
        }
    }

    // ---- Beenden ---------------------------------------------------------------------------------

    /** Merkt sich das Ende, schließt die Ansicht und meldet es dem Aufrufer. */
    private void finish() {
        if (isFinished) return;
        isFinished = true;
        markDone(context);
        host.setPreviewMode(false);
        host.closeScreen();
        if (onFinished != null) onFinished.run();
    }

    // ---- Screen ----------------------------------------------------------------------------------

    @Override public View view() {
        return root;
    }

    @Override public void onShown() {
        nextButton.requestFocus();
    }

    @Override public boolean onBack() {
        if (step > STEP_WELCOME) goTo(step - 1, -1);
        else finish();
        return true;
    }

    @Override public boolean onMenu() {
        return true;
    }

    @Override public void onClosed() {
        isFinished = true;
        scrim.animate().cancel();
        stage.animate().cancel();
        host.setPreviewMode(false);
    }
}
