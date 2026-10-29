package app.calmtv.home.dream;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Einstellungen des Bildschirmschoners im Look des Launchers: Wetter (Stadtsuche), Schalter für Wetter,
 * Kamerafahrt und Nachtmodus, Vorschau. Optionales Intent-Extra "accent" (int ARGB): Akzentfarbe des Launchers, Standard Weiß.
 * Gespeichert werden nur die Schalter ("ambient") und die gewählte Stadt ("weather"), beides nur auf dem Stick.
 */
public final class SetupActivity extends Activity {
    private static final String EXTRA_ACCENT = "accent";
    private static final int DEFAULT_ACCENT = 0xFFFFFFFF;
    private static final int MAX_RESULTS = 4, SCROLL_MARGIN_DP = 40;
    private static final float BACKDROP_ALPHA = 0.32f;   // das unscharfe Foto scheint nur schwach durch den dunklen Grund

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private SetupStyle style;
    private FrameLayout root;
    private View mainPage, cityPage;
    private static final String RESET_HINT = "Setzt alle Auswahlen und Schalter zurück, die Stadt bleibt";
    private static final long RESET_CONFIRM_MS = 5_000;
    private final List<SetupRow> switchRows = new ArrayList<>();
    private final List<String> switchKeys = new ArrayList<>();
    private final List<SetupChoice> choices = new ArrayList<>();
    private final Runnable disarmReset = this::disarmReset;
    private SetupRow cityRow, weatherRow, motionRow, motionLevelRow, nightRow, previewRow, resetRow;
    private SetupRow unitRow, extraRow, refreshRow, placeRow, rangeRow, cornerRow;
    private SetupRow[] nightRows;
    private boolean isResetArmed;
    private EditText field;
    private TextView status;
    private LinearLayout results;
    private ImageView backdrop;
    private SetupRow removeRow;
    private int searchId;   // nur die jüngste Suche darf die Trefferliste füllen

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        int accent = getIntent() != null ? getIntent().getIntExtra(EXTRA_ACCENT, DEFAULT_ACCENT) : DEFAULT_ACCENT;
        style = new SetupStyle(this, accent);
        root = new FrameLayout(this);
        root.setBackground(style.background());
        backdrop = new ImageView(this);
        backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
        backdrop.setAlpha(0f);
        root.addView(backdrop, new FrameLayout.LayoutParams(-1, -1));
        loadBackdrop();
        mainPage = buildMainPage();
        cityPage = buildCityPage();
        cityPage.setVisibility(View.GONE);
        root.addView(mainPage, new FrameLayout.LayoutParams(-1, -1));
        root.addView(cityPage, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        refreshCity();
        previewRow.requestFocus();
    }

    /** Das Foto wird im Hintergrund dekodiert und weich eingeblendet; ohne Foto bleibt der dunkle Verlauf. */
    private void loadBackdrop() {
        final Context context = getApplicationContext();
        worker.execute(() -> {
            final Bitmap photo = BlurredBackdrop.load(context);
            if (photo == null) return;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                backdrop.setImageBitmap(photo);
                backdrop.animate().alpha(BACKDROP_ALPHA).setDuration(400).start();
            });
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        backdrop.setImageDrawable(null);
        worker.shutdownNow();
    }

    @Override
    public void onBackPressed() {
        if (cityPage.getVisibility() == View.VISIBLE) showMain();
        else super.onBackPressed();
    }

    // ---- Hauptseite --------------------------------------------------------------------------

    private View buildMainPage() {
        LinearLayout panel = newPanel();
        previewRow = new SetupRow(this, style, "Vorschau", "Den Bildschirmschoner jetzt zeigen").withChevron();
        previewRow.setOnClickListener(v -> startActivity(new Intent(this, PreviewActivity.class)));
        addRow(panel, previewRow);

        section(panel, "Fotos");
        addChoice(panel, "Dauer je Foto", "So lange bleibt ein Foto stehen", AmbientSettings.KEY_PHOTO_SECONDS,
                new String[] {"10 s", "15 s", "24 s", "40 s", "60 s"}, new int[] {10, 15, 24, 40, 60}, AmbientSettings.DEFAULT_PHOTO_SECONDS);
        motionRow = addSwitch(panel, "Kamerafahrt", "Langsamer Zoom und Schwenk über die Fotos", AmbientSettings.KEY_MOTION);
        motionLevelRow = addChoice(panel, "Stärke der Kamerafahrt", "Wie weit Zoom und Schwenk gehen", AmbientSettings.KEY_MOTION_LEVEL,
                new String[] {"Sanft", "Normal", "Kräftig"}, new int[] {0, 1, 2}, AmbientSettings.DEFAULT_MOTION_LEVEL);

        section(panel, "Uhr und Datum");
        addChoice(panel, "Uhrzeitformat", "", AmbientSettings.KEY_TIME_FORMAT,
                new String[] {"Wie das System", "24 Stunden", "12 Stunden"}, new int[] {0, 1, 2}, AmbientSettings.DEFAULT_TIME_FORMAT);
        addChoice(panel, "Größe der Uhr", "", AmbientSettings.KEY_CLOCK_SIZE,
                new String[] {"Klein", "Normal", "Groß"}, new int[] {0, 1, 2}, AmbientSettings.DEFAULT_CLOCK_SIZE);
        addChoice(panel, "Stil der Uhr", "", AmbientSettings.KEY_CLOCK_STYLE,
                new String[] {"Dünn", "Kräftig"}, new int[] {0, 1}, AmbientSettings.DEFAULT_CLOCK_STYLE);
        addChoice(panel, "Datumsformat", "Lang: Sonntag, 25. Oktober. Kurz: So., 25. Okt.", AmbientSettings.KEY_DATE_FORMAT,
                new String[] {"Lang", "Kurz"}, new int[] {0, 1}, AmbientSettings.DEFAULT_DATE_FORMAT);
        cornerRow = addChoice(panel, "Position der Angaben", "", AmbientSettings.KEY_CORNER,
                new String[] {"Wechselt pro Foto", "Unten links", "Unten rechts", "Oben links", "Oben rechts"},
                new int[] {AmbientSettings.CORNER_WANDER, 0, 2, 3, 1}, AmbientSettings.DEFAULT_CORNER);

        section(panel, "Wetter");
        cityRow = new SetupRow(this, style, "Wetter", "").withChevron();
        cityRow.setOnClickListener(v -> showCity());
        addRow(panel, cityRow);
        weatherRow = addSwitch(panel, "Wetter anzeigen", "", AmbientSettings.KEY_WEATHER);
        unitRow = addChoice(panel, "Temperatur", "", AmbientSettings.KEY_UNIT,
                new String[] {"°C", "°F"}, new int[] {0, 1}, AmbientSettings.DEFAULT_UNIT);
        extraRow = addChoice(panel, "Zusatzangaben", "Regenwahrscheinlichkeit des Tages und aktueller Wind (km/h)", AmbientSettings.KEY_EXTRA,
                new String[] {"Keine", "Regen", "Wind", "Beides"}, new int[] {0, 1, 2, 3}, AmbientSettings.DEFAULT_EXTRA);
        refreshRow = addChoice(panel, "Auffrischen", "", AmbientSettings.KEY_REFRESH,
                new String[] {"Nur beim Bildschirmschoner", "Alle 2 Stunden"}, new int[] {0, WeatherRefresh.EVERY_TWO_HOURS}, AmbientSettings.DEFAULT_REFRESH);
        placeRow = addSwitch(panel, "Ort anzeigen", "Den Namen der Stadt unter der Temperatur", AmbientSettings.KEY_SHOW_PLACE);
        rangeRow = addSwitch(panel, "Tiefst- und Höchstwert", "Die Spanne des Tages", AmbientSettings.KEY_SHOW_RANGE);

        section(panel, "Nachtmodus");
        nightRow = addSwitch(panel, "Nachtmodus", "Dunkler und langsamer, wenn der Fernseher nachts ruht", AmbientSettings.KEY_NIGHT);
        nightRows = new SetupRow[] {
                addChoice(panel, "Beginn", "", AmbientSettings.KEY_NIGHT_FROM,
                        new String[] {"20 Uhr", "21 Uhr", "22 Uhr", "23 Uhr", "0 Uhr", "1 Uhr"}, new int[] {20, 21, 22, 23, 0, 1},
                        AmbientSettings.DEFAULT_NIGHT_FROM),
                addChoice(panel, "Ende", "", AmbientSettings.KEY_NIGHT_TO,
                        new String[] {"4 Uhr", "5 Uhr", "6 Uhr", "7 Uhr", "8 Uhr", "9 Uhr"}, new int[] {4, 5, 6, 7, 8, 9},
                        AmbientSettings.DEFAULT_NIGHT_TO),
                addChoice(panel, "Helligkeit", "Wie hell der Bildschirm nachts bleibt", AmbientSettings.KEY_NIGHT_BRIGHTNESS,
                        new String[] {"30 %", "45 %", "60 %"}, new int[] {30, 45, 60}, AmbientSettings.DEFAULT_NIGHT_BRIGHTNESS)};

        section(panel, "Zurücksetzen");
        resetRow = new SetupRow(this, style, "Alles zurücksetzen", RESET_HINT);
        resetRow.setOnClickListener(v -> onReset());
        resetRow.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) disarmReset();
        });
        addRow(panel, resetRow);
        refreshAll();

        String privacy = "Nur das Wetter geht ins Internet, und nur an Open-Meteo: beim Suchen der eingetippte Stadtname, "
                + "danach die gerundeten Koordinaten der Stadt, jeweils mit der IP-Adresse des Sticks. "
                + "Auf Wunsch (Auffrischen: alle 2 Stunden) holt der Bildschirmschoner die Koordinaten auch im Hintergrund erneut, sonst nur beim Laufen. "
                + "Fotos und Einstellungen bleiben auf dem Stick.";
        return page("", "Naturfotos mit Uhr, Datum und Wetter, wenn der Fernseher ruht.", privacy, panel);
    }

    private void section(LinearLayout into, String title) {
        TextView t = style.text(this, title.toUpperCase(Locale.GERMAN), 13, style.bold, SetupStyle.WEAK);
        t.setLetterSpacing(0.1f);
        t.setPadding(style.px(16), style.px(into.getChildCount() == 0 ? 4 : 18), 0, style.px(6));
        into.addView(t, new LinearLayout.LayoutParams(-2, -2));
    }

    private SetupRow addSwitch(LinearLayout into, String title, String subtitle, String key) {
        SetupRow row = new SetupRow(this, style, title, subtitle).withSwitch(AmbientSettings.get(this, key));
        row.setOnClickListener(v -> {
            AmbientSettings.set(this, key, row.toggle());
            refreshDependencies();
        });
        addRow(into, row);
        switchRows.add(row);
        switchKeys.add(key);
        return row;
    }

    private SetupRow addChoice(LinearLayout into, String title, String subtitle, String key, String[] labels, int[] values, int fallback) {
        SetupRow row = new SetupRow(this, style, title, subtitle).withValue(labels[0]);
        choices.add(new SetupChoice(this, row, key, labels, values, fallback, this::refreshDependencies));
        addRow(into, row);
        return row;
    }

    /** Liest alle Werte neu (Start und "Alles zurücksetzen"). */
    private void refreshAll() {
        for (int i = 0; i < switchRows.size(); i++) switchRows.get(i).setChecked(AmbientSettings.get(this, switchKeys.get(i)));
        for (SetupChoice choice : choices) choice.refresh();
        refreshDependencies();
    }

    /** Zeilen ohne Wirkung werden abgeblendet, die Position zeigt ihren Hinweis. */
    private void refreshDependencies() {
        motionLevelRow.setDimmed(!motionRow.isChecked());
        boolean isWeather = weatherRow.isChecked();
        weatherRow.setSubtitle(isWeather ? "Temperatur und Wettersymbol neben der Uhr" : "Aus: der Bildschirmschoner nutzt das Internet nicht");
        unitRow.setDimmed(!isWeather);
        extraRow.setDimmed(!isWeather);
        refreshRow.setDimmed(!isWeather);
        boolean isTimed = AmbientSettings.getInt(this, AmbientSettings.KEY_REFRESH, AmbientSettings.DEFAULT_REFRESH) == WeatherRefresh.EVERY_TWO_HOURS;
        refreshRow.setSubtitle(isTimed ? "Holt das Wetter alle 2 Stunden im Hintergrund, nur von Open-Meteo und nur mit gewählter Stadt"
                : "TV Home zeigt das Wetter nur, wenn der Bildschirmschoner es zuletzt geholt hat");
        WeatherRefresh.sync(this);
        placeRow.setDimmed(!isWeather);
        rangeRow.setDimmed(!isWeather);
        for (SetupRow row : nightRows) row.setDimmed(!nightRow.isChecked());
        boolean isFixed = AmbientSettings.getInt(this, AmbientSettings.KEY_CORNER, AmbientSettings.DEFAULT_CORNER) >= 0;
        cornerRow.setSubtitle(isFixed ? "Einbrenngefahr bei OLED" : "Die Angaben wechseln bei jedem Foto die Ecke");
    }

    private void onReset() {
        if (!isResetArmed) {
            isResetArmed = true;
            resetRow.setSubtitle("Drücke noch einmal zum Bestätigen");
            resetRow.postDelayed(disarmReset, RESET_CONFIRM_MS);
            return;
        }
        disarmReset();
        AmbientSettings.reset(this);
        refreshAll();
        resetRow.setSubtitle("Zurückgesetzt");
    }

    private void disarmReset() {
        resetRow.removeCallbacks(disarmReset);
        if (!isResetArmed) return;
        isResetArmed = false;
        resetRow.setSubtitle(RESET_HINT);
    }

    private void refreshCity() {
        cityRow.setSubtitle(Weather.isConfigured(this) ? Weather.placeName(this) : "Noch keine Stadt gewählt");
        refreshRemoveRow();
    }

    /** "Stadt entfernen" gibt es nur mit gewählter Stadt und nur, solange keine Trefferliste den Platz braucht. */
    private void refreshRemoveRow() {
        boolean isShown = Weather.isConfigured(this) && results.getChildCount() == 0;
        removeRow.setVisibility(isShown ? View.VISIBLE : View.GONE);
    }

    // ---- Stadtsuche --------------------------------------------------------------------------

    private View buildCityPage() {
        LinearLayout panel = newPanel();
        field = new EditText(this);
        field.setSingleLine();
        field.setTextSize(19);
        field.setTypeface(style.medium);
        field.setTextColor(SetupStyle.TEXT);
        field.setHintTextColor(SetupStyle.WEAK);
        field.setHint("Stadt eintippen, dann Enter");
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS);
        field.setImeOptions(EditorInfo.IME_ACTION_SEARCH | EditorInfo.IME_FLAG_NO_EXTRACT_UI | EditorInfo.IME_FLAG_NO_FULLSCREEN);
        field.setPadding(style.px(16), style.px(14), style.px(16), style.px(14));
        field.setBackground(fieldBackground());
        field.setOnEditorActionListener((v, actionId, e) -> {
            boolean isEnter = e != null && e.getKeyCode() == KeyEvent.KEYCODE_ENTER && e.getAction() == KeyEvent.ACTION_DOWN;
            if (actionId != EditorInfo.IME_ACTION_SEARCH && !isEnter) return false;
            search(field.getText().toString().trim());
            return true;
        });
        LinearLayout.LayoutParams fieldParams = new LinearLayout.LayoutParams(-1, -2);
        fieldParams.bottomMargin = style.px(4);
        panel.addView(field, fieldParams);

        status = style.text(this, "", 14, style.medium, SetupStyle.MUTED);
        status.setPadding(style.px(16), style.px(8), style.px(16), style.px(8));
        status.setVisibility(View.GONE);
        panel.addView(status, new LinearLayout.LayoutParams(-1, -2));

        results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        panel.addView(results, new LinearLayout.LayoutParams(-1, -2));

        removeRow = new SetupRow(this, style, "Stadt entfernen", "Dann gibt es kein Wetter");
        removeRow.setOnClickListener(v -> {
            Weather.clear(this);
            WeatherRefresh.sync(this);
            refreshCity();
            showMain();
        });
        addRow(panel, removeRow);

        return page("Stadt für das Wetter", "Tippe den Namen ein und bestätige mit Enter. Danach wählst du die Stadt aus der Liste.",
                "Die Stadt wird nur auf dem Stick gespeichert.", panel);
    }

    private StateListDrawable fieldBackground() {
        GradientDrawable idle = style.rounded(SetupStyle.ROW_FILL, SetupStyle.ROW_RADIUS);
        GradientDrawable focused = style.rounded(0x2EFFFFFF, SetupStyle.ROW_RADIUS);
        focused.setStroke(style.px(2), style.accent);
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[] {android.R.attr.state_focused}, focused);
        states.addState(new int[0], idle);
        return states;
    }

    private void search(final String name) {
        if (name.isEmpty()) return;
        final int myId = ++searchId;
        results.removeAllViews();
        refreshRemoveRow();
        setStatus("Suche …");
        worker.execute(() -> {
            List<Weather.Place> found = null;
            try {
                found = Weather.search(name);
            } catch (IOException e) {
                // Kein Netz oder Dienst nicht erreichbar: unten gemeldet.
            }
            final List<Weather.Place> result = found;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed() || myId != searchId) return;
                if (result == null) setStatus("Keine Verbindung zu Open-Meteo. Prüfe das WLAN.");
                else if (result.isEmpty()) setStatus("Keine Stadt gefunden");
                else showResults(result);
            });
        });
    }

    private void setStatus(String text) {
        status.setText(text);
        status.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void showResults(List<Weather.Place> places) {
        setStatus("Welche Stadt?");
        for (int i = 0; i < places.size() && i < MAX_RESULTS; i++) {
            final Weather.Place place = places.get(i);
            SetupRow row = new SetupRow(this, style, place.name, place.detail);
            row.setOnClickListener(v -> {
                Weather.savePlace(this, place);
                WeatherRefresh.sync(this);
                refreshCity();
                showMain();
            });
            addRow(results, row);
        }
        InputMethodManager keyboard = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (keyboard != null) keyboard.hideSoftInputFromWindow(field.getWindowToken(), 0);
        refreshRemoveRow();
        results.getChildAt(0).requestFocus();
    }

    // ---- Seiten und Bausteine ----------------------------------------------------------------

    private void showCity() {
        results.removeAllViews();
        refreshRemoveRow();
        setStatus("");
        switchPage(mainPage, cityPage);
        field.requestFocus();
    }

    private void showMain() {
        searchId++;   // laufende Suche verwerfen
        switchPage(cityPage, mainPage);
        cityRow.requestFocus();   // der Fokus landet dort, wo man herkam
    }

    private void switchPage(View from, View to) {
        from.setVisibility(View.GONE);
        to.setAlpha(0f);
        to.setTranslationX(style.px(40));
        to.setVisibility(View.VISIBLE);
        to.animate().alpha(1f).translationX(0f).setDuration(220).setInterpolator(new DecelerateInterpolator(1.8f)).start();
    }

    /** Oben links der Titel wie in den Launcher-Einstellungen, darunter das Panel mit den Zeilen und daneben die Erklärung. */
    private View page(String heading, String description, String note, LinearLayout panel) {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(style.px(64), style.px(34), style.px(64), style.px(24));
        page.addView(style.text(this, "Bildschirmschoner", 30, style.light, SetupStyle.TEXT), new LinearLayout.LayoutParams(-2, -2));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(-1, 0, 1f);
        bodyParams.topMargin = style.px(20);
        page.addView(body, bodyParams);
        FocusScrollView scroll = new FocusScrollView(this, style.px(SCROLL_MARGIN_DP));
        scroll.setBackground(style.panel());
        scroll.setPadding(style.px(12), style.px(12), style.px(12), style.px(12));
        scroll.addView(panel, new FrameLayout.LayoutParams(-1, -2));
        body.addView(scroll, new LinearLayout.LayoutParams(0, -2, 1f));

        LinearLayout side = new LinearLayout(this);
        side.setOrientation(LinearLayout.VERTICAL);
        if (!heading.isEmpty()) {
            side.addView(style.text(this, heading, 24, style.medium, SetupStyle.TEXT), new LinearLayout.LayoutParams(-2, -2));
        }
        side.addView(sideText(description, 16, SetupStyle.MUTED, heading.isEmpty() ? 0 : 12), new LinearLayout.LayoutParams(-2, -2));
        side.addView(sideText(note, 14, SetupStyle.NOTE, 24), new LinearLayout.LayoutParams(-2, -2));
        LinearLayout.LayoutParams sideParams = new LinearLayout.LayoutParams(style.px(300), -2);
        sideParams.leftMargin = style.px(40);
        body.addView(side, sideParams);
        return page;
    }

    private TextView sideText(String content, float sp, int color, int topMarginDp) {
        TextView t = style.text(this, content, sp, style.medium, color);
        t.setLineSpacing(0, 1.15f);
        t.setPadding(0, style.px(topMarginDp), 0, 0);
        return t;
    }

    private LinearLayout newPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        return panel;
    }

    private void addRow(LinearLayout into, SetupRow row) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        if (into.getChildCount() > 0) lp.topMargin = style.px(4);
        into.addView(row, lp);
    }
}
