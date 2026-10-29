package app.calmtv.home;

import android.view.View;

/**
 * Eine Vollbild-Ansicht über der Startseite (Einstellungen, Kontrollzentrum, Willkommen). Genau eine ist offen;
 * {@link HomeHost#openScreen} ersetzt die aktuelle, {@link HomeHost#closeScreen} schließt sie.
 */
interface Screen {
    /** Die Wurzel-Ansicht; sie füllt die Screen-Ebene und wird vom Host eingeblendet. */
    View view();

    /** Wird nach dem Einfügen aufgerufen: Fokus setzen. */
    void onShown();

    /** Zurück gedrückt. true = verbraucht (z. B. eine Unterseite geschlossen), false = die Ansicht soll geschlossen werden. */
    boolean onBack();

    /** Menü-Taste gedrückt, während die Ansicht offen ist. true = verbraucht. */
    boolean onMenu();

    /** Die Ansicht wurde entfernt: Ressourcen freigeben, Vorschau-Modus beenden. */
    void onClosed();
}
