package app.calmtv.home.dream;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

/**
 * Gibt den zuletzt geholten Wetterstand an TV Home weiter, das selbst kein Internet hat. Nur lesen: Es gibt weder
 * Ort noch Koordinaten, nur Temperatur und Beschreibung, und das nur für Apps mit demselben Signaturschlüssel
 * (readPermission im Manifest). Der Provider holt nie selbst etwas aus dem Netz; ist der Stand älter als
 * {@link Weather#cached}, kommt eine leere Antwort.
 */
public final class WeatherProvider extends ContentProvider {
    /** Spalten der einen Zeile; TV Home liest sie über diese Namen. */
    private static final String[] COLUMNS = {"temperature", "description", "time"};

    @Override public boolean onCreate() {
        return true;   // bewusst leer: der Prozess soll beim Start nichts laden
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        MatrixCursor cursor = new MatrixCursor(COLUMNS, 1);
        if (getContext() == null) return cursor;
        WeatherRefresh.sync(getContext());   // nach einem Neustart ist der Zeitplan weg; TV Home fragt bei jedem Heimkommen
        Weather.Report report = Weather.cached(getContext());
        if (report == null) return cursor;
        boolean isFahrenheit = AmbientSettings.getInt(getContext(), AmbientSettings.KEY_UNIT, AmbientSettings.DEFAULT_UNIT) == 1;
        long value = Math.round(isFahrenheit ? report.temperature * 9 / 5 + 32 : report.temperature);
        cursor.addRow(new Object[]{value + (isFahrenheit ? "°F" : "°"), report.description, report.fetchedAt});
        return cursor;
    }

    @Override public String getType(Uri uri) {
        return "vnd.android.cursor.item/vnd.app.calmtv.home.dream.weather";
    }

    @Override public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("nur lesen");
    }

    @Override public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("nur lesen");
    }

    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("nur lesen");
    }
}
