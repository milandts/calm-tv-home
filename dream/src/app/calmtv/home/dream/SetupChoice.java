package app.calmtv.home.dream;

import android.content.Context;
import android.view.View;

/** Verbindet eine Zeile mit einer Auswahl aus festen Werten: Drücken schaltet zum nächsten Wert weiter und speichert ihn. */
final class SetupChoice implements View.OnClickListener {
    private final Context context;
    private final SetupRow row;
    private final String key;
    private final String[] labels;
    private final int[] values;
    private final int fallback;
    private final Runnable afterChange;

    SetupChoice(Context context, SetupRow row, String key, String[] labels, int[] values, int fallback, Runnable afterChange) {
        this.context = context;
        this.row = row;
        this.key = key;
        this.labels = labels;
        this.values = values;
        this.fallback = fallback;
        this.afterChange = afterChange;
        row.setOnClickListener(this);
        refresh();
    }

    int current() {
        int stored = AmbientSettings.getInt(context, key, fallback);
        for (int i = 0; i < values.length; i++) if (values[i] == stored) return i;
        return indexOf(fallback);
    }

    int currentValue() {
        return values[current()];
    }

    private int indexOf(int value) {
        for (int i = 0; i < values.length; i++) if (values[i] == value) return i;
        return 0;
    }

    /** Zeigt den gespeicherten Wert an (auch nach "Alles zurücksetzen"). */
    void refresh() {
        row.setValue(labels[current()]);
    }

    @Override
    public void onClick(View v) {
        AmbientSettings.setInt(context, key, values[(current() + 1) % values.length]);
        refresh();
        if (afterChange != null) afterChange.run();
    }
}
