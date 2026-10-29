package app.calmtv.home;

/**
 * Beschreibung einer Einstellung (Schlüssel, Art, Standard, Bereich). Alle Werte sind ganze Zahlen: Schalter 0/1,
 * Auswahl = Nummer der Option, Regler = Wert im Bereich. Der Standardwert entspricht dem Aussehen vor der Einstellbarkeit.
 * Die Liste aller Einstellungen steht in {@link Opts}; gespeichert und gelesen wird über {@link Config}.
 */
final class Opt {
    /** Arten (keine Enum: D8 aus Build-Tools 33 stürzt bei Enum-Klassen ab). */
    static final int BOOL = 0, CHOICE = 1, RANGE = 2;

    final String key;
    final int kind;
    final int def, min, max, step;
    /** Beschriftungen einer Auswahl (leer bei Schalter und Regler). */
    final String[] labels;
    /** Einheit hinter dem Wert eines Reglers, z. B. " %" oder " dp". */
    final String unit;
    /** Riskante Einstellung: nach der Änderung erscheint die Rückfrage "Behalten?" (10-Sekunden-Test). */
    final boolean risky;

    private Opt(String key, int kind, int def, int min, int max, int step, String[] labels, String unit, boolean risky) {
        this.key = key;
        this.kind = kind;
        this.def = def;
        this.min = min;
        this.max = max;
        this.step = step;
        this.labels = labels;
        this.unit = unit;
        this.risky = risky;
    }

    static Opt bool(String key, boolean def) {
        return new Opt(key, BOOL, def ? 1 : 0, 0, 1, 1, new String[0], "", false);
    }

    static Opt choice(String key, int def, String... labels) {
        return new Opt(key, CHOICE, def, 0, labels.length - 1, 1, labels, "", false);
    }

    static Opt range(String key, int def, int min, int max, int step, String unit) {
        return new Opt(key, RANGE, def, min, max, step, new String[0], unit, false);
    }

    /** Dieselbe Einstellung, aber mit Rückfrage "Behalten?" nach der Änderung. */
    Opt risky() {
        return new Opt(key, kind, def, min, max, step, labels, unit, true);
    }

    int clamp(int v) {
        return Math.max(min, Math.min(max, v));
    }

    /** Text zum Wert für Anzeigen ("Normal", "80 %", "An"). */
    String describe(int value) {
        // Kein switch über die Enum: javac erzeugt dafür eine anonyme Hilfsklasse, an der D8 aus Build-Tools 33 abstürzt.
        if (kind == BOOL) return value != 0 ? "An" : "Aus";
        if (kind == CHOICE) return labels[clamp(value)];
        return clamp(value) + unit;
    }
}
