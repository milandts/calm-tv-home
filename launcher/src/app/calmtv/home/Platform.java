package app.calmtv.home;

import android.content.Context;
import android.content.pm.PackageManager;

/**
 * Auf welchem Gerät läuft TV Home? Fire OS gibt HOME nur an Amazons Startseite, Android TV und Google TV an Googles; auf beiden
 * holt {@link HomeKeyService} TV Home nach vorn, sobald die Startseite des Geräts erscheint. Hier steht nur, was die Texte der
 * Oberfläche unterscheiden müssen.
 */
final class Platform {
    private static final String FEATURE_FIRE_TV = "amazon.hardware.fire_tv";

    private Platform() {}

    /** Fire TV: das Systemmerkmal oder Amazons Startseite (ältere Geräte melden das Merkmal nicht immer). */
    static boolean isFireTv(boolean hasFireFeature, boolean hasAmazonLauncher) {
        return hasFireFeature || hasAmazonLauncher;
    }

    static boolean isFireTv(Context context) {
        PackageManager packages = context.getPackageManager();
        return isFireTv(packages.hasSystemFeature(FEATURE_FIRE_TV), isInstalled(packages, AppCatalog.AMAZON_LAUNCHER));
    }

    private static boolean isInstalled(PackageManager packages, String pkg) {
        try {
            packages.getPackageInfo(pkg, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }
}
