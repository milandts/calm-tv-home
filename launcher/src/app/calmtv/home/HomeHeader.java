package app.calmtv.home;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.format.DateFormat;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Calendar;
import java.util.Date;

/**
 * Der Block oben links: Gruß, Uhr, Datum, Wetterzeile und die Filter-Anzeige. Er liest seine Einstellungen aus {@link Opts}
 * und zeigt nur, was dort eingeschaltet ist.
 */
final class HomeHeader {
    private static final int FILTER_ON = 0xFF4CD964, FILTER_OFF = 0xFFFFB340;
    private static final float CLOCK_BASE_SP = 80f;

    private final Context context;
    private final Config config;
    private final LinearLayout column;
    private final TextView greeting, date, filter;
    private final GlassText clock;
    private final Typeface clockFace;
    private final HomeWeather weather;

    HomeHeader(Context context, Config config) {
        this.context = context;
        this.config = config;
        clockFace = Ui.font(context, 700);
        greeting = HomeText.caps(context, 12);
        clock = new GlassText(context, clockFace, CLOCK_BASE_SP);
        date = HomeText.caps(context, 12);
        TextView weatherLine = HomeText.caps(context, 12);
        weatherLine.setVisibility(View.GONE);
        weather = new HomeWeather(context, weatherLine);
        filter = HomeText.caps(context, 12);

        column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setClipChildren(false);
        // Jedes Kind mit ausdrücklichem wrap_content: sonst zwingt die Uhr die schmaleren Zeilen auf ihre Breite und kürzt den Gruß.
        column.addView(greeting, wrap(0));
        // Die Glasuhr lässt 10 dp Rand für den Schatten um die Ziffern; der Außenabstand holt sie auf die Linie der anderen Zeilen zurück.
        LinearLayout.LayoutParams clockParams = wrap(0);
        int shadow = Ui.px(context, 10);
        clockParams.setMargins(-shadow, Ui.px(context, 8) - shadow, 0, Ui.px(context, 9) - shadow);
        column.addView(clock, clockParams);
        column.addView(date, wrap(0));
        column.addView(weatherLine, wrap(4));
        column.addView(filter, wrap(10));
    }

    private LinearLayout.LayoutParams wrap(int topDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
        params.topMargin = Ui.px(context, topDp);
        return params;
    }

    LinearLayout view() {
        return column;
    }

    GlassText clock() {
        return clock;
    }

    HomeWeather weather() {
        return weather;
    }

    /** Setzt Gruß, Uhrzeit, Datum und Filter-Anzeige auf den heutigen Stand. */
    void update() {
        Date now = new Date();
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String hello = hour >= 5 && hour < 12 ? "Guten Morgen" : hour >= 12 && hour < 17 ? "Guten Tag" : "Guten Abend";
        String name = config.getText(Opts.GREETING_NAME);
        greeting.setText(Config.val(Opts.GREETING) == 1 && !name.isEmpty() ? hello + ", " + name : hello);
        int format = Config.val(Opts.CLOCK_FORMAT);
        boolean is24h = format == 1 || (format == 0 && DateFormat.is24HourFormat(context));
        clock.setText(DateFormat.format(is24h ? "H:mm" : "h:mm", now));
        int dateFormat = Config.val(Opts.DATE_FORMAT);
        date.setText(DateFormat.format(dateFormat == 1 ? "EEE, d. MMM" : dateFormat == 2 ? "dd.MM.yyyy" : "EEEE, d. MMMM", now));
        showFilter();
    }

    /** Wendet Größe, Stil und Sichtbarkeit der Zeilen an und frischt die Wetterzeile auf. */
    void applyConfig() {
        clock.setSizeSp(CLOCK_BASE_SP * Config.val(Opts.CLOCK_SIZE) / 100f);
        int style = Config.val(Opts.CLOCK_STYLE);
        if (style == 0) {
            clock.setPlain(false, Color.WHITE);
            clock.setTypeface(clockFace);
        } else {
            clock.setPlain(true, Color.WHITE);
            clock.setTypeface(Ui.font(context, style == 2 ? 300 : 800));
        }
        update();
        clock.setVisibility(visibleIf(Opts.SHOW_CLOCK));
        date.setVisibility(visibleIf(Opts.SHOW_DATE));
        // Die Anzeige gehört zum DNS-Filter auf dem Fire TV; auf anderen Geräten wäre "Kein Filter" nur ein falscher Alarm.
        boolean isFilterRelevant = Platform.isFireTv(context) || isFilterActive(context);
        filter.setVisibility(isFilterRelevant ? visibleIf(Opts.SHOW_FILTER) : View.GONE);
        greeting.setVisibility(Config.val(Opts.GREETING) != 2 ? View.VISIBLE : View.GONE);
        weather.apply();
        weather.refresh(false);
    }

    private static int visibleIf(Opt option) {
        return Config.val(option) != 0 ? View.VISIBLE : View.GONE;
    }

    /** Grüner Punkt, wenn ein VPN-Dienst (der DNS-Filter) läuft, sonst ein orangefarbener. */
    private void showFilter() {
        boolean isActive = isFilterActive(context);
        SpannableString text = new SpannableString(isActive ? "● Filter aktiv" : "● Kein Filter");
        text.setSpan(new ForegroundColorSpan(isActive ? FILTER_ON : FILTER_OFF), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        filter.setText(text);
    }

    /** True, wenn irgendein Netzwerk über VPN läuft. */
    static boolean isFilterActive(Context context) {
        try {
            ConnectivityManager manager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            for (Network network : manager.getAllNetworks()) {
                NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
                if (capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return true;
            }
        } catch (RuntimeException e) {
            Log.w(HomeKeyService.TAG, "network state not readable", e);
        }
        return false;
    }
}
