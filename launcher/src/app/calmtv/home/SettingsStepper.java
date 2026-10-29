package app.calmtv.home;

import android.os.SystemClock;

/**
 * Berechnet den nächsten Wert einer Einstellung bei Links/Rechts. Bei gehaltener Taste wird schneller gesprungen
 * (ab dem 5. Wiederholen in 2er-, ab dem 15. in 5er-Schritten), sichtbar aber höchstens zehnmal pro Sekunde.
 */
final class SettingsStepper {
    private static final long MIN_INTERVAL_MS = 100;
    private static final int FASTER_AFTER = 5, FASTEST_AFTER = 15;

    private long lastStep;

    /** Der neue Wert; gleich {@code current}, wenn die Taste zu schnell kam oder das Ende erreicht ist. */
    int next(Opt opt, int current, int direction, int repeat) {
        long now = SystemClock.uptimeMillis();
        if (repeat > 0 && now - lastStep < MIN_INTERVAL_MS) return current;
        lastStep = now;
        int factor = 1;
        if (opt.kind == Opt.RANGE) factor = repeat >= FASTEST_AFTER ? 5 : repeat >= FASTER_AFTER ? 2 : 1;
        return opt.clamp(current + direction * opt.step * factor);
    }
}
