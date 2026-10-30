package app.calmtv.home;

/**
 * Bremse gegen Schleifen: Holt der Dienst TV Home mehrfach hintereinander nach vorn (zum Beispiel weil TV Home abstürzt und
 * die Startseite des Geräts gleich wieder erscheint), pausiert er eine Weile, damit der Nutzer die Startseite des Geräts erreicht
 * und den Dienst ausschalten kann. Reine Logik, die Zeit kommt von außen.
 */
final class TakeoverGuard {
    static final int MAX_TAKEOVERS = 3;
    static final long WINDOW_MS = 10_000, PAUSE_MS = 60_000;

    private final long[] times = new long[MAX_TAKEOVERS];
    private int count;
    private long pausedUntil;

    /** True, wenn jetzt übernommen werden darf; zählt die Übernahme mit. */
    boolean allow(long now) {
        if (now < pausedUntil) return false;
        int recent = 0;
        for (int i = 0; i < count; i++) if (now - times[i] < WINDOW_MS) times[recent++] = times[i];
        count = recent;
        if (count >= MAX_TAKEOVERS) {
            pausedUntil = now + PAUSE_MS;
            count = 0;
            return false;
        }
        times[count++] = now;
        return true;
    }
}
