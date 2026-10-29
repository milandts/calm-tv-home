package app.calmtv.home;

import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Schreibt Änderungen an {@link SettingsModel.OptRow} in die {@link Config}. Auf Vorschau-Seiten und bei riskanten
 * Einstellungen gelten Änderungen nur probeweise (live sichtbar, nicht gespeichert). Erst beim Verlassen des Bereichs
 * ({@link #leave}) erscheint EIN Dialog; nach "Behalten" folgt für riskante Werte der Rückzähler-Test. Während ein
 * Dialog offen ist, sind Änderungen und weitere Dialoge gesperrt.
 */
final class SettingsOptions {
    private static final int MAX_LINES = 6;

    private final HomeHost host;
    private final Runnable onSettled;   // nach Entscheidung: Seite neu lesen
    private final Map<String, String> titles = new HashMap<>();
    private boolean dialogOpen;
    private View dialogView;
    /** Läuft gerade eine Vorschau-Seite? (setzt SettingsScreen beim Anzeigen einer Seite) */
    boolean previewPage;

    SettingsOptions(HomeHost host, Runnable onSettled) {
        this.host = host;
        this.onSettled = onSettled;
    }

    int value(SettingsModel.OptRow row) {
        return host.config().get(row.opt);
    }

    /** Setzt den Wert (probeweise oder direkt). false, wenn gerade ein Dialog offen ist. Zeigt nie selbst einen Dialog. */
    boolean apply(SettingsModel.OptRow row, int value) {
        if (dialogOpen) return false;
        Config config = host.config();
        Opt opt = row.opt;
        int clamped = opt.clamp(value);
        if (clamped == config.get(opt)) return true;
        titles.put(opt.key, row.title);
        if (previewPage || opt.risky) config.trial(opt, clamped);
        else config.set(opt, clamped);
        afterChange(row);
        return true;
    }

    /** Menütaste: zurück auf den Standard (in Vorschau-Seiten ebenfalls nur probeweise). */
    void reset(SettingsModel.OptRow row) {
        if (dialogOpen) return;
        apply(row, row.opt.def);
        host.toast("Zurückgesetzt");
        onSettled.run();
    }

    /**
     * Verlässt einen Bereich. Ohne Probeänderungen läuft {@code proceed} sofort; sonst fragt ein Dialog nach.
     * {@code stay} läuft bei "Weiter bearbeiten" und Zurück (darf null sein).
     */
    void leave(Runnable proceed, Runnable stay) {
        if (dialogOpen) return;
        Config config = host.config();
        final Map<String, int[]> changes = config.trialChanges();
        if (changes.isEmpty()) {
            if (config.hasTrial()) config.revert();
            proceed.run();
            return;
        }
        dialogOpen = true;
        dialogView = SettingsChangesDialog.show(host.modalLayer(), describe(changes),
                () -> keep(changes, proceed),
                () -> {
                    config.revert();
                    close(proceed);
                },
                () -> close(stay));
    }

    /** HOME oder Schließen: Probeänderungen werden ohne Dialog gespeichert; offene Dialoge verschwinden. */
    void commitSilently() {
        removeDialog();
        dialogOpen = false;
        Config config = host.config();
        if (config != null && config.hasTrial()) config.commit();
    }

    private void keep(Map<String, int[]> changes, Runnable proceed) {
        Config config = host.config();
        final List<Opt> risky = new ArrayList<>();
        final List<Integer> olds = new ArrayList<>();
        for (Map.Entry<String, int[]> e : changes.entrySet()) {
            Opt opt = Opts.byKey(e.getKey());
            if (opt != null && opt.risky) {
                risky.add(opt);
                olds.add(e.getValue()[0]);
            }
        }
        config.commit();
        if (risky.isEmpty()) {
            close(proceed);
            return;
        }
        dialogOpen = true;
        dialogView = SettingsTrialGuard.show(host.modalLayer(), () -> close(proceed), () -> {
            for (int i = 0; i < risky.size(); i++) config.set(risky.get(i), olds.get(i));
            close(proceed);
        });
        if (dialogView == null) dialogOpen = false;
    }

    private void close(Runnable next) {
        dialogOpen = false;
        dialogView = null;
        onSettled.run();
        if (next != null) next.run();
    }

    private void removeDialog() {
        if (dialogView != null && dialogView.getParent() instanceof ViewGroup) {
            ((ViewGroup) dialogView.getParent()).removeView(dialogView);
        }
        dialogView = null;
    }

    private List<String> describe(Map<String, int[]> changes) {
        List<String> lines = new ArrayList<>();
        int shown = 0;
        for (Map.Entry<String, int[]> e : changes.entrySet()) {
            if (shown == MAX_LINES) {
                lines.set(MAX_LINES - 1, "und " + (changes.size() - MAX_LINES + 1) + " weitere");
                break;
            }
            Opt opt = Opts.byKey(e.getKey());
            String title = titles.containsKey(e.getKey()) ? titles.get(e.getKey()) : Opts.title(e.getKey());
            String text = opt == null ? title : title + ": " + opt.describe(e.getValue()[0]) + " → " + opt.describe(e.getValue()[1]);
            lines.add(text);
            shown++;
        }
        return lines;
    }

    private static void afterChange(SettingsModel.OptRow row) {
        if (row.afterChange != null) row.afterChange.run();
    }
}
