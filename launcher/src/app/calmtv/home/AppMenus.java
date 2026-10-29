package app.calmtv.home;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Die Glas-Menüs zu Apps und Ordnern: Menü einer Dock-Kachel (verschieben, entfernen), einer App im Raster, in einem Ordner und
 * in der Suche sowie die Ordner-Menüs. Jede Änderung geht über {@link AppLayout} und {@link DockStore}; danach bauen
 * Dock, Raster und offener Ordner sich neu auf.
 */
final class AppMenus {
    private final HomeActivity home;
    private final HomeScene scene;
    private final AppCatalog catalog;
    private final DockStore store;
    private final AppLayout layout;

    AppMenus(HomeActivity home, HomeScene scene, AppCatalog catalog, DockStore store, AppLayout layout) {
        this.home = home;
        this.scene = scene;
        this.catalog = catalog;
        this.store = store;
        this.layout = layout;
    }

    /** Taste "Menü" auf einer Kachel: die Aktion läuft beim Loslassen. */
    static View.OnKeyListener menuKey(final Runnable action) {
        return (v, code, event) -> {
            if (code != KeyEvent.KEYCODE_MENU) return false;
            if (event.getAction() == KeyEvent.ACTION_UP) action.run();
            return true;
        };
    }

    // ---- Dock ------------------------------------------------------------------------------------

    /** Menü einer Dock-Kachel: nach links oder rechts schieben, entfernen, umbenennen, App-Verwaltung. */
    void dock(final String id) {
        final List<String> ids = store.visible();
        final int position = ids.indexOf(id);
        final AppEntry app = catalog.get(id);
        List<GlassMenu.Item> items = new ArrayList<>();
        if (position > 0) items.add(new GlassMenu.Item("Nach links", Icons.PFEIL_ZURUECK, () -> move(ids, position, position - 1, id)));
        if (position < ids.size() - 1) {
            items.add(new GlassMenu.Item("Nach rechts", Icons.PFEIL_RECHTS, () -> move(ids, position, position + 1, id)));
        }
        items.add(new GlassMenu.Item("Aus dem Dock entfernen", "", Icons.ENTFERNEN, false, () -> {
            ids.remove(position);
            store.save(ids);
            home.dock().forgetFocus();
            home.dock().rebuild(ids.isEmpty() ? null : ids.get(Math.min(position, ids.size() - 1)));
            if (home.grid().isOpen()) home.grid().rebuild();
        }));
        items.add(new GlassMenu.Item("Umbenennen …", Icons.UMBENENNEN, () -> rename(app)));
        items.add(new GlassMenu.Item("Apps verwalten", Icons.EINSTELLUNGEN, () -> openAppInfo(id)));
        addBarFallbacks(items);
        GlassMenu.show(scene.modalLayer, home.nameOf(app), items, null);
    }

    private void move(List<String> ids, int from, int to, String focusId) {
        Collections.swap(ids, from, to);
        store.save(ids);
        home.dock().forgetFocus();
        home.dock().rebuild(focusId);
        if (home.grid().isOpen()) home.grid().rebuild();
    }

    private void toggleDock(AppEntry app) {
        List<String> ids = store.visible();
        if (ids.remove(app.pkg)) {
            home.toast(home.nameOf(app) + " wurde aus dem Dock entfernt");
        } else {
            ids.add(app.pkg);
            home.toast(home.nameOf(app) + " wurde zum Dock hinzugefügt");
        }
        store.save(ids);
        home.dock().rebuild(null);
    }

    // ---- Apps und Ordner -------------------------------------------------------------------------

    /** Menü einer App (Menütaste oder langes Drücken) im Raster, in einem Ordner und in der Suche. */
    void app(final AppEntry app) {
        List<GlassMenu.Item> items = new ArrayList<>();
        boolean isDocked = store.visible().contains(app.pkg);
        items.add(item(isDocked ? "Aus dem Dock entfernen" : "Zum Dock hinzufügen", isDocked ? Icons.ENTFERNEN : Icons.HINZUFUEGEN,
                () -> toggleDock(app)));
        final String folder = layout.folderOf(app.pkg);
        if (folder != null) items.add(item("Aus Ordner „" + folder + "“ entfernen", Icons.ORDNER, () -> layout.removeFromFolder(app.pkg)));
        items.add(item(folder != null ? "In anderen Ordner legen …" : "In Ordner legen …", Icons.ORDNER, () -> chooseFolder(app)));
        items.add(item(layout.isPinned(app.pkg) ? "Nicht mehr nach vorn" : "Nach vorn ziehen", Icons.NACH_VORN, () -> layout.togglePinned(app.pkg)));
        items.add(item("Umbenennen …", Icons.UMBENENNEN, () -> rename(app)));
        if (layout.isRenamed(app.pkg)) items.add(item("Originalnamen wiederherstellen", Icons.UMBENENNEN, () -> layout.rename(app.pkg, "")));
        items.add(item("Ausblenden", Icons.AUSGEBLENDET, () -> {
            layout.hide(app.pkg);
            home.toast(home.nameOf(app) + " ist ausgeblendet. Einblenden: Einstellungen > Apps & Dock.");
        }));
        items.add(item("Apps verwalten", Icons.EINSTELLUNGEN, () -> openAppInfo(app.pkg)));
        addBarFallbacks(items);
        GlassMenu.show(scene.modalLayer, home.nameOf(app), items, null);
    }

    void folder(final String folder) {
        List<GlassMenu.Item> items = new ArrayList<>();
        items.add(item("Ordner umbenennen …", Icons.UMBENENNEN, () ->
                GlassPrompt.show(scene.modalLayer, "Ordner umbenennen", "Name des Ordners", folder, name -> {
                    if (!layout.renameFolder(folder, name)) home.toast("Dieser Name ist leer oder schon vergeben");
                    home.afterLayoutChange();
                })));
        items.add(new GlassMenu.Item("Ordner auflösen", "", Icons.PAPIERKORB, false, () -> {
            layout.dissolveFolder(folder);
            home.afterLayoutChange();
        }));
        GlassMenu.show(scene.modalLayer, folder, items, null);
    }

    /** Fragt, in welchen Ordner die App kommt, oder legt einen neuen an. */
    private void chooseFolder(final AppEntry app) {
        List<GlassMenu.Item> items = new ArrayList<>();
        for (final String name : layout.folderNames()) {
            items.add(new GlassMenu.Item(name, Icons.ORDNER, () -> {
                layout.putInFolder(app.pkg, name);
                home.afterLayoutChange();
            }));
        }
        items.add(new GlassMenu.Item("Neuer Ordner …", Icons.ORDNER, () ->
                GlassPrompt.show(scene.modalLayer, "Neuer Ordner", "Name des Ordners", "", "Anlegen", name -> {
                    if (name.isEmpty()) return;
                    layout.putInFolder(app.pkg, name);
                    home.afterLayoutChange();
                })));
        GlassMenu.show(scene.modalLayer, "In welchen Ordner?", items, null);
    }

    void rename(final AppEntry app) {
        GlassPrompt.show(scene.modalLayer, "Neuer Name für " + app.label, "Name", home.nameOf(app), name -> {
            layout.rename(app.pkg, name);
            home.afterLayoutChange();
        });
    }

    /** Amazons App-Verwaltung (Fire OS ignoriert das Paket): Stoppen erzwingen, Deinstallieren, Daten löschen. */
    private void openAppInfo(String pkg) {
        Intent info = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + catalog.get(pkg).pkg))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            home.startActivity(info);
        } catch (ActivityNotFoundException | SecurityException e) {
            home.toast("Die App-Verwaltung ist nicht verfügbar");
        }
    }

    /** Ist die Einstellungen-Taste (oder die ganze Leiste) ausgeblendet, bleibt der Weg über das Menü einer App. */
    private void addBarFallbacks(List<GlassMenu.Item> items) {
        HomeGeometry geometry = home.geometry();
        if (!geometry.isPillVisible(scene.settingsPill)) items.add(new GlassMenu.Item("Einstellungen", Icons.SYSTEM, () -> home.openSettings(null)));
        if (!geometry.barShown()) items.add(new GlassMenu.Item("Kontrollzentrum", Icons.EINSTELLUNGEN, home::openControlCenter));
    }

    /** Menüeintrag, nach dessen Aktion Dock, Raster und Ordner neu aufgebaut werden. */
    private GlassMenu.Item item(String label, int icon, final Runnable action) {
        return new GlassMenu.Item(label, icon, () -> {
            action.run();
            home.afterLayoutChange();
        });
    }
}
