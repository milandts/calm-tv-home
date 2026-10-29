package app.calmtv.home;

import android.util.Log;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Sucht Seiten im Baum der Einstellungen (für den Tiefenlink der Suche): Kategorien und rekursiv Link-Ziele. */
final class SettingsPaths {
    private static final int MAX_DEPTH = 8;

    private SettingsPaths() {}

    /** Der Weg von einer Kategorie bis zur Seite mit dieser Kennung (einschließlich beider) oder null. */
    static ArrayList<SettingsModel.Page> find(List<SettingsModel.Page> categories, String pageId) {
        for (SettingsModel.Page start : categories) {
            ArrayList<SettingsModel.Page> trail = new ArrayList<>();
            if (descend(start, pageId, trail, new HashSet<>(), 0)) return trail;
        }
        return null;
    }

    /** Nummer der Link-Zeile in {@code parent}, die auf {@code target} zeigt, sonst -1. */
    static int rowIndexOfLink(SettingsModel.Page parent, SettingsModel.Page target) {
        List<SettingsModel.Row> rows = rowsOf(parent);
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i) instanceof SettingsModel.Link && ((SettingsModel.Link) rows.get(i)).target == target) return i;
        }
        return -1;
    }

    private static boolean descend(SettingsModel.Page page, String pageId, List<SettingsModel.Page> trail,
                                   Set<SettingsModel.Page> seen, int depth) {
        if (depth > MAX_DEPTH || !seen.add(page)) return false;
        trail.add(page);
        if (pageId.equals(page.id)) return true;
        for (SettingsModel.Row row : rowsOf(page)) {
            if (row instanceof SettingsModel.Link && ((SettingsModel.Link) row).target != null
                    && descend(((SettingsModel.Link) row).target, pageId, trail, seen, depth + 1)) {
                return true;
            }
        }
        trail.remove(trail.size() - 1);
        return false;
    }

    private static List<SettingsModel.Row> rowsOf(SettingsModel.Page page) {
        try {
            List<SettingsModel.Row> rows = page.currentRows();
            return rows == null ? new ArrayList<>() : rows;
        } catch (RuntimeException e) {
            Log.w(HomeKeyService.TAG, "Zeilen der Seite nicht lesbar: " + e.getMessage());
            return new ArrayList<>();
        }
    }
}
