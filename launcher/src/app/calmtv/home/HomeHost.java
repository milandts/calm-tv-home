package app.calmtv.home;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.view.ViewGroup;

import java.util.List;

/**
 * Was die Startseite ({@link HomeActivity}) den neuen Ansichten anbietet. Einstellungen, Kontrollzentrum und
 * Willkommen sprechen nur über diese Schnittstelle mit ihr.
 */
interface HomeHost {
    /** Eine installierte, startbare App (ohne TV Home selbst). */
    final class AppInfo {
        final String pkg;
        final String label;   // der angezeigte Name (eigener oder Originalname)

        AppInfo(String pkg, String label) {
            this.pkg = pkg;
            this.label = label;
        }
    }

    Context context();

    /** Ebene für Menüs, Eingabefelder und Meldungen; liegt immer über allem. */
    ViewGroup modalLayer();

    AppLayout layout();

    /** Zentrale Einstellungen (siehe {@link Opts}); Änderungen wirken sofort auf die Startseite. */
    Config config();

    Look look();

    // ---- Darstellung -----------------------------------------------------------------------------

    /** Wendet Abdunkeln und Unschärfe auf den Hintergrund an (Akzentfarbe wirkt beim nächsten Fokus). */
    void applyLook();

    /** Das nächste Hintergrundbild (gilt bis Mitternacht). */
    void nextWallpaper();

    /** Unscharfe Kopie des aktuellen Hintergrunds (für den Hintergrund von Ansichten) oder null. */
    Bitmap blurredWallpaper();

    /** Alle Bildnachweise, eine Zeile je Foto. */
    List<String> wallpaperCredits();

    /** Urheber des Bildes, das gerade zu sehen ist; leer, wenn unbekannt. */
    String currentWallpaperCredit();

    // ---- Apps ------------------------------------------------------------------------------------

    /** Alle startbaren Apps, nach angezeigtem Namen sortiert. */
    List<AppInfo> apps();

    /** Baut Dock, Raster und Ordner nach einer Änderung an Ordnern, Namen oder Sichtbarkeit neu auf. */
    void afterLayoutChange();

    /** Stellt das Standard-Dock wieder her. */
    void resetDock();

    /** Zeigt "Zuletzt benutzt" im App-Raster (Standard: an). */
    boolean showRecent();

    void setShowRecent(boolean show);

    // ---- System ----------------------------------------------------------------------------------

    /** True, wenn der Intent zu einer Activity führt, die TV Home starten darf. */
    boolean canStart(Intent intent);

    /** Startet die Activity; bei Fehlern erscheint eine Meldung, der Rückgabewert ist false. */
    boolean start(Intent intent);

    /** Amazons Einstellungsübersicht (erreicht auch gesperrte Seiten wie "Töne und Bildschirm"). */
    void openAmazonOverview();

    /** Öffnet eine Webseite im Browser. */
    boolean openUrl(String url);

    /** True, wenn ein VPN-Netzwerk läuft (der DNS-Filter von dumbtv). */
    boolean isFilterActive();

    // ---- Sicherung -------------------------------------------------------------------------------

    /** Schreibt die Sicherung; gibt eine Meldung für den Nutzer zurück (Erfolg oder Fehler). */
    String saveBackup();

    /** Lädt die Sicherung und baut die Oberfläche neu auf; gibt eine Meldung zurück. */
    String restoreBackup();

    // ---- Oberfläche ------------------------------------------------------------------------------

    /** Kurze Meldung als Glas-Pille (statt Android-Toast). */
    void toast(String message);

    /** Öffnet eine Vollbild-Ansicht und ersetzt die aktuelle. */
    void openScreen(Screen screen);

    /** Schließt die aktuelle Vollbild-Ansicht; der Fokus kehrt dorthin zurück, wo sie geöffnet wurde. */
    void closeScreen();

    /**
     * Vorschau-Modus: Hintergrund und Abdunkelung der Ansicht werden durchsichtig, damit man die echte Startseite sieht
     * (Uhr, Dock, Foto). Die Ansicht selbst bleibt sichtbar.
     */
    void setPreviewMode(boolean on);

    /** Zeigt die Einführung (Willkommen) erneut. */
    void showWelcome();

    /** Die Bilderverwaltung (Hintergrundbilder auswählen, ausblenden, Favoriten, eigener Ordner). */
    void openGallery();

    /** Die Hintergrundbild-Bibliothek (Liste, Auswahl, Ordner). */
    Wallpapers wallpapers();

    /** Öffnet das Kontrollzentrum. */
    void openControlCenter();
}
