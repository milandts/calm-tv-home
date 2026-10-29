package app.calmtv.home;

import java.util.ArrayList;
import java.util.List;

/** Die Seite "Apps & Dock" mit ihren Unterseiten Ordner und Ausgeblendete Apps. */
final class SettingsPagesApps {
    private SettingsPagesApps() {}

    static SettingsModel.Page build(final HomeHost host) {
        SettingsModel.Page page = new SettingsModel.Page("apps", "Apps & Dock")
                .subtitle("Ordner, ausgeblendete Apps und Dock").icon(Icons.APPS);
        page.add(SettingsPagesRows.link("Ordner", "Umbenennen oder auflösen", Icons.ORDNER,
                "gruppe gruppen sortieren umbenennen aufloesen", folders(host)));
        page.add(SettingsPagesRows.link("Ausgeblendete Apps", "Wieder einblenden", Icons.AUSGEBLENDET,
                "verstecken versteckt ausblenden einblenden", hiddenApps(host)));
        page.add(SettingsPagesRows.toggle("Zuletzt benutzt anzeigen", "Die zuletzt gestarteten Apps erscheinen über dem Raster",
                Icons.LAUFZEIT, "verlauf zuletzt gestartet recent",
                host::showRecent,
                show -> {
                    host.setShowRecent(show);
                    host.afterLayoutChange();
                }));
        page.add(SettingsPagesRows.button("Zuletzt benutzt zurücksetzen", "Vergisst die zuletzt gestarteten Apps",
                Icons.PAPIERKORB, "verlauf loeschen leeren recent", () -> {
                    host.layout().clearRecent();
                    host.afterLayoutChange();
                    host.toast("Zuletzt benutzt wurde zurückgesetzt");
                }));
        SettingsModel.Button resetDock = SettingsPagesRows.button("Dock zurücksetzen", "Zurück zu den Standard-Apps",
                Icons.DOCK, "dock leiste standard reset", () -> {
                    host.resetDock();
                    host.toast("Das Dock ist zurückgesetzt");
                });
        resetDock.confirmLabel("Zurücksetzen");
        resetDock.destructive("Das Dock wird auf die Standard-Apps zurückgesetzt.");
        page.add(resetDock);
        return page;
    }

    // ---- Ordner ----------------------------------------------------------------------------------

    private static SettingsModel.Page folders(final HomeHost host) {
        SettingsModel.Page page = new SettingsModel.Page("apps/ordner", "Ordner").icon(Icons.ORDNER);
        page.dynamicRows = () -> folderRows(host);
        return page;
    }

    private static List<SettingsModel.Row> folderRows(final HomeHost host) {
        AppLayout layout = host.layout();
        List<String> names = layout.folderNames();
        if (names.isEmpty()) {
            return SettingsPagesRows.onlyNote("Noch keine Ordner. Menütaste auf einer App im App-Raster: In Ordner legen.");
        }
        List<SettingsModel.Row> rows = new ArrayList<>();
        for (final String name : names) {
            String title = name + " · " + SettingsPagesRows.appCount(layout.folderApps(name).size());
            rows.add(SettingsPagesRows.button(title, "Umbenennen oder auflösen", Icons.ORDNER, "",
                    () -> showFolderMenu(host, name)));
        }
        return rows;
    }

    private static void showFolderMenu(final HomeHost host, final String name) {
        List<GlassMenu.Item> items = new ArrayList<>();
        items.add(new GlassMenu.Item("Umbenennen …", Icons.UMBENENNEN, () -> askNewName(host, name)));
        items.add(new GlassMenu.Item("Ordner auflösen", "Die Apps bleiben erhalten", Icons.PAPIERKORB, true, () -> {
            host.layout().dissolveFolder(name);
            host.afterLayoutChange();
            host.toast("Ordner „" + name + "“ aufgelöst");
        }));
        GlassMenu.show(host.modalLayer(), name, items, () -> { });
    }

    private static void askNewName(final HomeHost host, final String oldName) {
        GlassPrompt.show(host.modalLayer(), "Ordner umbenennen", "Name des Ordners", oldName, text -> {
            if (host.layout().renameFolder(oldName, text)) {
                host.afterLayoutChange();
                host.toast("Ordner umbenannt");
            } else {
                host.toast("Der Name ist leer oder schon vergeben");
            }
        });
    }

    // ---- Ausgeblendete Apps ----------------------------------------------------------------------

    private static SettingsModel.Page hiddenApps(final HomeHost host) {
        SettingsModel.Page page = new SettingsModel.Page("apps/ausgeblendet", "Ausgeblendete Apps").icon(Icons.AUSGEBLENDET);
        page.dynamicRows = () -> hiddenRows(host);
        return page;
    }

    private static List<SettingsModel.Row> hiddenRows(final HomeHost host) {
        List<SettingsModel.Row> rows = new ArrayList<>();
        for (final HomeHost.AppInfo app : host.apps()) {
            if (!host.layout().isHidden(app.pkg)) continue;
            rows.add(SettingsPagesRows.button(app.label, "Wieder einblenden", Icons.AUSGEBLENDET, "", () -> {
                host.layout().unhide(app.pkg);
                host.afterLayoutChange();
                host.toast(app.label + " ist wieder sichtbar");
            }));
        }
        if (rows.isEmpty()) {
            return SettingsPagesRows.onlyNote("Keine Apps ausgeblendet. Menütaste auf einer App: Ausblenden.");
        }
        return rows;
    }
}
