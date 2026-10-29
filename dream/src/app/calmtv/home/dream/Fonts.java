package app.calmtv.home.dream;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Build;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;

/** Nunito (variable Schrift aus den Assets) in der gewünschten Strichstärke; Rückfall auf die Systemschrift. */
final class Fonts {
    private static final String TAG = "TVHomeDream";
    private static final Map<Integer, Typeface> CACHE = new HashMap<>();   // nur wenige Einträge (je Strichstärke einer)

    private Fonts() {}

    /** Nur auf dem Hauptthread aufrufen. {@code weight} liegt zwischen 100 und 900. */
    static Typeface get(Context context, int weight) {
        Typeface cached = CACHE.get(weight);
        if (cached != null) return cached;
        Typeface face = null;
        if (Build.VERSION.SDK_INT >= 26) {
            try {
                face = new Typeface.Builder(context.getAssets(), "fonts/Nunito.ttf")
                        .setFontVariationSettings("'wght' " + weight).build();   // build() kann null liefern
            } catch (RuntimeException e) {
                Log.w(TAG, "Schrift nicht ladbar, nehme die Systemschrift");
            }
        }
        if (face == null) face = Typeface.create(weight < 400 ? "sans-serif-light" : "sans-serif", Typeface.NORMAL);
        CACHE.put(weight, face);
        return face;
    }
}
