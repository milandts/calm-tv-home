package app.calmtv.home;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Handler;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Alle Apps, die TV Home zeigen darf, mit ihren Bildern. Die Abfrage beim System und das Laden der Banner laufen im
 * Hintergrund; geändert wird der Bestand nur auf dem Hauptthread und nur, wenn sich Pakete oder Namen wirklich geändert haben
 * (sonst bleiben Dock und Raster unberührt, und der Fokus springt nicht).
 */
final class AppCatalog {
    static final String AMAZON_LAUNCHER = "com.amazon.tv.launcher";
    static final String SILK = "com.amazon.cloud9";
    /** System-Apps, die auch ohne TV-Eintrag erscheinen dürfen (Appstore und Browser). */
    private static final List<String> SYSTEM_WHITELIST = java.util.Arrays.asList("com.amazon.venezia", SILK);

    private final Context context;
    private final Map<String, AppEntry> entries = new LinkedHashMap<>();
    private final Map<String, Drawable> artwork = new HashMap<>();
    private final Map<String, Long> installTimes = new HashMap<>();
    private volatile String lastSignature = "";

    AppCatalog(Context context) {
        this.context = context;
    }

    boolean isEmpty() {
        return entries.isEmpty();
    }

    boolean has(String pkg) {
        return entries.containsKey(pkg);
    }

    AppEntry get(String pkg) {
        return entries.get(pkg);
    }

    /** Alle Apps, nach Systemnamen sortiert. */
    Collection<AppEntry> all() {
        return entries.values();
    }

    Drawable art(String pkg) {
        return artwork.get(pkg);
    }

    /**
     * Fragt das System im Hintergrund ab. Hat sich nichts geändert, passiert nichts; sonst wird der Bestand ausgetauscht und
     * {@code onChanged} auf dem Hauptthread aufgerufen.
     */
    void refresh(Executor worker, final Handler ui, final Runnable onChanged) {
        worker.execute(() -> {
            final Map<String, AppEntry> found = query();
            final String signature = signatureOf(found.values());
            if (signature.equals(lastSignature)) return;
            final Map<String, Drawable> pictures = new HashMap<>();
            PackageManager pm = context.getPackageManager();
            for (AppEntry app : found.values()) pictures.put(app.pkg, loadPicture(app, pm));
            lastSignature = signature;
            ui.post(() -> {
                entries.clear();
                entries.putAll(found);
                artwork.clear();
                artwork.putAll(pictures);
                installTimes.clear();
                onChanged.run();
            });
        });
    }

    /** Zeitpunkt der ersten Installation (für "Kürzlich installiert"); je Bestand nur einmal erfragt. 0, wenn die App fehlt. */
    long installTime(String pkg) {
        Long known = installTimes.get(pkg);
        if (known != null) return known;
        long time = 0;
        try {
            time = context.getPackageManager().getPackageInfo(pkg, 0).firstInstallTime;
        } catch (PackageManager.NameNotFoundException | RuntimeException ignored) {
            // nicht mehr installiert: sortiert ganz nach hinten
        }
        installTimes.put(pkg, time);
        return time;
    }

    private static String signatureOf(Collection<AppEntry> list) {
        StringBuilder text = new StringBuilder();
        for (AppEntry app : list) text.append(app.pkg).append('\t').append(app.label).append('\n');
        return text.toString();
    }

    /** TV-Einträge zuerst; Handy-Einträge nur, wenn sie keine Systemhilfen sind (Downloads, Bildschirmschoner ...). */
    private Map<String, AppEntry> query() {
        PackageManager pm = context.getPackageManager();
        Map<String, AppEntry> byPackage = new HashMap<>();
        for (String category : new String[] {Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            boolean isTv = Intent.CATEGORY_LEANBACK_LAUNCHER.equals(category);
            for (ResolveInfo match : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category), 0)) {
                String pkg = match.activityInfo.packageName;
                if (pkg.equals(context.getPackageName()) || pkg.equals(AMAZON_LAUNCHER) || byPackage.containsKey(pkg)) continue;
                boolean isSystem = (match.activityInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                if (!isTv && isSystem && !SYSTEM_WHITELIST.contains(pkg)) continue;
                Intent launch = new Intent(Intent.ACTION_MAIN).addCategory(category)
                        .setClassName(pkg, match.activityInfo.name)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                byPackage.put(pkg, new AppEntry(pkg, match.loadLabel(pm).toString().trim(), match.activityInfo, launch));
            }
        }
        List<AppEntry> sorted = new ArrayList<>(byPackage.values());
        Collections.sort(sorted, (a, b) -> a.label.compareToIgnoreCase(b.label));
        Map<String, AppEntry> ordered = new LinkedHashMap<>();
        for (AppEntry app : sorted) ordered.put(app.pkg, app);
        return ordered;
    }

    /** Das TV-Banner der App, sonst ihr Symbol auf einer Kachel. */
    private static Drawable loadPicture(AppEntry app, PackageManager pm) {
        Drawable banner = null;
        try {
            banner = app.info.loadBanner(pm);
            if (banner == null) banner = pm.getApplicationBanner(app.pkg);
        } catch (PackageManager.NameNotFoundException | RuntimeException ignored) {
            // kein Banner: das Symbol genügt
        }
        return banner != null ? banner : IconTile.of(app.info.loadIcon(pm));
    }
}
