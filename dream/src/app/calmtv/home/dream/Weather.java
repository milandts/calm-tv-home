package app.calmtv.home.dream;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import javax.net.ssl.HttpsURLConnection;
import java.net.URL;

/**
 * Wetter von Open-Meteo (kostenlos, ohne Schlüssel, ohne Konto). Es gehen nur die Koordinaten der gewählten
 * Stadt (auf zwei Nachkommastellen gerundet) und die IP-Adresse des Sticks an den Dienst.
 * Die Stadt und der letzte Stand liegen nur auf dem Stick (SharedPreferences), nie im Repo.
 */
final class Weather {
    private static final String PREFS = "weather";
    private static final String GEOCODING = "https://geocoding-api.open-meteo.com/v1/search?count=5&language=de&format=json&name=";
    private static final String FORECAST = "https://api.open-meteo.com/v1/forecast?timezone=auto"
            + "&current=temperature_2m,weather_code,wind_speed_10m&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max&forecast_days=1";
    private static final String GEOCODING_HOST = "geocoding-api.open-meteo.com", FORECAST_HOST = "api.open-meteo.com";
    private static final int TIMEOUT_MS = 8_000, MAX_BYTES = 64 * 1024;
    /** Gesamtdauer eines Abrufs; die Zeitgrenzen oben gelten nur je Lesevorgang, eine zähe Leitung könnte sonst ewig dauern. */
    private static final long DEADLINE_MS = 20_000;
    /** Ein gespeicherter Stand gilt so lange als aktuell genug zum sofortigen Anzeigen. */
    private static final long FRESH_MS = 3 * 60 * 60 * 1000L;

    /** Eine gefundene Stadt. */
    static final class Place {
        final String name, detail;
        final double latitude, longitude;

        Place(String name, String detail, double latitude, double longitude) {
            this.name = name;
            this.detail = detail;
            this.latitude = latitude;
            this.longitude = longitude;
        }

        @Override public String toString() {
            return detail.isEmpty() ? name : name + " (" + detail + ")";
        }
    }

    /** Aktuelles Wetter am gewählten Ort. */
    static final class Report {
        final String place, description;
        final int code;
        final double temperature, low, high;   // in °C; die Anzeige rechnet bei Bedarf um
        final int rainPercent;   // höchste Regenwahrscheinlichkeit des Tages, -1 = unbekannt
        final double windKmh;    // aktueller Wind in km/h, -1 = unbekannt
        final long fetchedAt;   // Zeitpunkt des Abrufs (ms), damit veraltete Angaben ausgeblendet werden können

        Report(String place, int code, double temperature, double low, double high, int rainPercent, double windKmh, long fetchedAt) {
            this.place = place;
            this.code = code;
            this.description = describe(code);
            this.temperature = temperature;
            this.low = low;
            this.high = high;
            this.rainPercent = rainPercent;
            this.windKmh = windKmh;
            this.fetchedAt = fetchedAt;
        }
    }

    private Weather() {}

    static boolean isConfigured(Context c) {
        return prefs(c).contains("lat");
    }

    static String placeName(Context c) {
        return prefs(c).getString("place", "");
    }

    static void savePlace(Context c, Place p) {
        prefs(c).edit().clear().putString("place", p.name)
                .putFloat("lat", (float) round2(p.latitude)).putFloat("lon", (float) round2(p.longitude)).apply();
    }

    static void clear(Context c) {
        prefs(c).edit().clear().apply();
    }

    /** Der zuletzt geholte Stand, falls er noch jung genug ist; sonst null. */
    static Report cached(Context c) {
        SharedPreferences p = prefs(c);
        if (!p.contains("time") || !p.contains("tempC") || System.currentTimeMillis() - p.getLong("time", 0) > FRESH_MS) return null;
        return new Report(p.getString("place", ""), p.getInt("code", -1),
                p.getFloat("tempC", 0), p.getFloat("lowC", 0), p.getFloat("highC", 0),
                p.getInt("rain", -1), p.getFloat("windKmh", -1), p.getLong("time", 0));
    }

    /** Holt das Wetter (blockiert, nur im Hintergrund-Thread aufrufen). Gibt bei Fehlern null zurück. */
    static Report fetch(Context c) {
        SharedPreferences p = prefs(c);
        if (!p.contains("lat")) return null;
        try {
            JSONObject json = getJson(FORECAST + "&latitude=" + p.getFloat("lat", 0) + "&longitude=" + p.getFloat("lon", 0));
            JSONObject current = json.getJSONObject("current");
            JSONObject daily = json.getJSONObject("daily");
            int code = current.getInt("weather_code");
            double temperature = current.getDouble("temperature_2m");
            double high = daily.getJSONArray("temperature_2m_max").getDouble(0);
            double low = daily.getJSONArray("temperature_2m_min").getDouble(0);
            // Die beiden Zusatzwerte sind freiwillig: fehlen sie in der Antwort, bleibt die Anzeige ohne sie.
            JSONArray rainDaily = daily.optJSONArray("precipitation_probability_max");
            int rain = rainDaily == null || rainDaily.isNull(0) ? -1 : rainDaily.getInt(0);
            double wind = current.optDouble("wind_speed_10m", -1);
            long now = System.currentTimeMillis();
            p.edit().putInt("code", code).putFloat("tempC", (float) temperature).putFloat("lowC", (float) low).putFloat("highC", (float) high)
                    .putInt("rain", rain).putFloat("windKmh", (float) wind).putLong("time", now).apply();
            return new Report(p.getString("place", ""), code, temperature, low, high, rain, wind, now);
        } catch (IOException | JSONException | RuntimeException e) {
            return null;
        }
    }

    /** Sucht Städte zum Namen (blockiert). Wirft IOException bei Netzfehlern. */
    static List<Place> search(String name) throws IOException {
        List<Place> out = new ArrayList<>();
        try {
            JSONObject json = getJson(GEOCODING + URLEncoder.encode(name, "UTF-8"));
            JSONArray results = json.optJSONArray("results");
            if (results == null) return out;
            for (int i = 0; i < results.length(); i++) {
                JSONObject r = results.getJSONObject(i);
                String region = r.optString("admin1", "");
                String country = r.optString("country", "");
                String detail = region.isEmpty() ? country : country.isEmpty() ? region : region + ", " + country;
                out.add(new Place(r.getString("name"), detail, r.getDouble("latitude"), r.getDouble("longitude")));
            }
        } catch (JSONException e) {
            throw new IOException("Unerwartete Antwort", e);
        }
        return out;
    }

    /** WMO-Wettercode in kurzem Deutsch. */
    static String describe(int code) {
        if (code == 0) return "Klar";
        if (code == 1) return "Überwiegend klar";
        if (code == 2) return "Teils bewölkt";
        if (code == 3) return "Bedeckt";
        if (code == 45 || code == 48) return "Nebel";
        if (code >= 51 && code <= 57) return "Nieselregen";
        if (code >= 61 && code <= 67) return "Regen";
        if (code >= 71 && code <= 77) return "Schnee";
        if (code >= 80 && code <= 82) return "Regenschauer";
        if (code == 85 || code == 86) return "Schneeschauer";
        if (code >= 95 && code <= 99) return "Gewitter";
        return "";
    }

    private static JSONObject getJson(String address) throws IOException, JSONException {
        URL url = new URL(address);
        // Einziges Tor nach draußen: nur HTTPS und nur die beiden Open-Meteo-Adressen, keine Weiterleitungen.
        if (!"https".equals(url.getProtocol()) || !(GEOCODING_HOST.equals(url.getHost()) || FORECAST_HOST.equals(url.getHost()))) {
            throw new IOException("Adresse nicht erlaubt");
        }
        HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(TIMEOUT_MS);
        connection.setReadTimeout(TIMEOUT_MS);
        final long giveUpAt = System.nanoTime() + DEADLINE_MS * 1_000_000L;
        try {
            if (connection.getResponseCode() != 200) throw new IOException("HTTP " + connection.getResponseCode());
            try (InputStream in = connection.getInputStream();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                StringBuilder body = new StringBuilder();
                char[] buffer = new char[4096];
                int n;
                while ((n = reader.read(buffer)) > 0 && body.length() < MAX_BYTES) {
                    if (System.nanoTime() > giveUpAt) throw new IOException("Zu langsam");
                    body.append(buffer, 0, n);
                }
                return new JSONObject(body.toString());
            }
        } finally {
            connection.disconnect();
        }
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
