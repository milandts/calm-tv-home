package app.calmtv.home;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;

import org.json.JSONException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Die Startseite von TV Home: Gruß und Uhr, drei Glas-Tasten, ein Glas-Dock mit Lieblings-Apps über wechselnden Naturfotos;
 * Runter auf dem Dock öffnet das Raster mit allen anderen Apps. Die Activity selbst hält nur Lebenszyklus, HOME-Taste,
 * Einstellungen und die Verdrahtung; das Verhalten liegt in {@link HomeDock}, {@link AppGrid}, {@link HomeBar},
 * {@link HomeOverlay}, {@link HomeScreens}, {@link HomeBackdrop} und {@link AppMenus}.
 */
public class HomeActivity extends Activity implements HomeHost {
    private static final String SETTINGS_CATEGORY = "android.intent.category.LEANBACK_SETTINGS";
    private static final long CATALOG_DELAY_MS = 500, STARTUP_MESSAGE_DELAY_MS = 900, ENTRANCE_MS = 420;
    /**
     * Zweimal HOME: Der zweite Druck zählt nur, wenn er mindestens {@code SAME_PRESS_MS} nach dem ersten kommt. Ein einzelner
     * Druck startet TV Home zweimal kurz hintereinander (Amazons Startseite meldet sich nach etwa 0,5 s noch einmal), und das darf
     * die Einstellungen nicht öffnen, wenn ihr Button in der Leiste fehlt.
     */
    private static final long SAME_PRESS_MS = 900, DOUBLE_PRESS_MS = 1_500, LATE_START_MS = 3_000;

    /** Meldung für den nächsten Start (zum Beispiel nach dem Laden einer Sicherung). */
    private static String pendingMessage;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final BroadcastReceiver timeReceiver = new Receiver(this::updateHeader);
    private final BroadcastReceiver packageReceiver = new Receiver(this::scheduleCatalogRefresh);
    private final Runnable refreshCatalog = this::refreshCatalogNow;
    private final Runnable applyConfigNow = this::applyConfig;
    private final TileSizes sizes = new TileSizes();

    private Config config;
    private AppLayout layout;
    private Look look;
    private Wallpapers wallpapers;
    private AppCatalog catalog;
    private DockStore dockStore;
    private HomeScene scene;
    private HomeGeometry geometry;
    private HomeBackdrop backdrop;
    private HomeBar bar;
    private HomeDock dock;
    private AppGrid grid;
    private AppMenus menus;
    private HomeOverlay overlay;
    private HomeScreens screens;
    private boolean isStarted;
    private long lastHomeAt, settingsByDoubleHomeAt;

    private static final class Receiver extends BroadcastReceiver {
        private final Runnable action;

        Receiver(Runnable action) {
            this.action = action;
        }

        @Override public void onReceive(Context context, Intent intent) {
            action.run();
        }
    }

    // ---- Bausteine für die anderen Klassen -------------------------------------------------------

    HomeGeometry geometry() { return geometry; }

    HomeBackdrop backdrop() { return backdrop; }

    HomeBar bar() { return bar; }

    HomeDock dock() { return dock; }

    AppGrid grid() { return grid; }

    AppMenus menus() { return menus; }

    HomeOverlay overlay() { return overlay; }

    void post(Runnable action) {
        ui.post(action);
    }

    void post(long delayMs, Runnable action) {
        ui.postDelayed(action, delayMs);
    }

    /** Der Name, den der Nutzer vergeben hat, sonst der Name der App. */
    String nameOf(AppEntry app) {
        return layout.label(app.pkg, app.label);
    }

    /** Nur die Apps der Liste, die installiert und nicht ausgeblendet sind. */
    List<String> installed(List<String> pkgs) {
        List<String> shown = new ArrayList<>();
        for (String pkg : pkgs) if (catalog.has(pkg) && !layout.isHidden(pkg)) shown.add(pkg);
        return shown;
    }

    void launch(AppEntry app) {
        try {
            startActivity(app.launch);
            layout.touch(app.pkg);   // für "Zuletzt benutzt"
        } catch (ActivityNotFoundException | SecurityException e) {
            toast("Konnte " + nameOf(app) + " nicht öffnen");
        }
    }

    void hideKeyboard(View view) {
        InputMethodManager keyboard = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (keyboard != null) keyboard.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    // ---- Lebenszyklus ----------------------------------------------------------------------------

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        SharedPreferences homePrefs = getSharedPreferences("home", MODE_PRIVATE);
        SharedPreferences layoutPrefs = getSharedPreferences("layout", MODE_PRIVATE);
        config = new Config(this);
        config.addListener(key -> {   // ein Durchlauf je Bild, auch bei Voreinstellungen mit vielen Werten
            ui.removeCallbacks(applyConfigNow);
            ui.post(applyConfigNow);
        });
        layout = new AppLayout(layoutPrefs);
        look = new Look(layoutPrefs);
        wallpapers = new Wallpapers(this);
        catalog = new AppCatalog(this);
        dockStore = new DockStore(homePrefs, catalog, layout);
        sizes.refresh(this);

        scene = new HomeScene(this, config);
        geometry = new HomeGeometry(this, scene.root, scene.header.view(), scene.bar, scene.bottom, scene.header.clock(), scene.title,
                scene.dock, scene.pills());
        backdrop = new HomeBackdrop(this, scene, wallpapers, look, ui, worker,
                () -> overlay.isOpen() || grid.isOpen() || screens.isOpen());
        bar = new HomeBar(this, scene);
        dock = new HomeDock(this, scene, catalog, dockStore, sizes);
        grid = new AppGrid(this, scene, catalog, dockStore, layout, sizes);
        menus = new AppMenus(this, scene, catalog, dockStore, layout);
        overlay = new HomeOverlay(this, scene, catalog, layout);
        screens = new HomeScreens(this, scene);
        bar.install();

        setContentView(scene.root);
        applyConfig();
        refreshCatalogNow();
        ui.postDelayed(this::showStartupMessages, STARTUP_MESSAGE_DELAY_MS);
    }

    /** Beim ersten Start die Einführung, sonst eine gemerkte Meldung (zum Beispiel "Sicherung geladen"). */
    private void showStartupMessages() {
        if (pendingMessage != null) {
            toast(pendingMessage);
            pendingMessage = null;
        } else if (!Welcome.isDone(this) && !screens.isOpen() && isStarted) {
            showWelcome();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        isStarted = true;
        IntentFilter clockChanges = new IntentFilter();
        for (String action : new String[] {Intent.ACTION_TIME_TICK, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED}) {
            clockChanges.addAction(action);
        }
        registerReceiver(timeReceiver, clockChanges);
        IntentFilter packages = new IntentFilter(Intent.ACTION_PACKAGE_ADDED);
        packages.addAction(Intent.ACTION_PACKAGE_REMOVED);
        packages.addAction(Intent.ACTION_PACKAGE_CHANGED);
        packages.addDataScheme("package");
        registerReceiver(packageReceiver, packages);
        scene.header.update();
        scene.header.weather().refresh(false);
        playEntrance();
        scheduleCatalogRefresh();   // Apps, die installiert oder entfernt wurden, während wir gestoppt waren
        backdrop.onShown();
    }

    @Override
    protected void onStop() {
        super.onStop();
        isStarted = false;
        backdrop.onHidden();
        unregisterReceiver(timeReceiver);
        unregisterReceiver(packageReceiver);
    }

    @Override
    protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }

    private void updateHeader() {
        scene.header.update();
    }

    /** Pakete ändern sich in Schüben (und noch einmal, wenn wir zurückkommen): kurz warten, dann einmal neu lesen. */
    private void scheduleCatalogRefresh() {
        ui.removeCallbacks(refreshCatalog);
        ui.postDelayed(refreshCatalog, CATALOG_DELAY_MS);
    }

    private void refreshCatalogNow() {
        catalog.refresh(worker, ui, () -> {
            dock.rebuild(null);
            if (grid.isOpen()) grid.rebuild();
        });
    }

    // ---- HOME, Zurück, Menü ----------------------------------------------------------------------

    /** HOME, während wir schon vorn sind: zurück zum Dock; zweimal schnell HOME öffnet die Einstellungen, wenn ihre Taste fehlt. */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        long now = SystemClock.elapsedRealtime();
        // Die Nachzügler-Starts des HOME-Dienstes (Rückfälle nach bis zu 2,5 s) dürfen gerade geöffnete Einstellungen nicht gleich schließen.
        if (screens.isOpen() && now - settingsByDoubleHomeAt < LATE_START_MS) return;
        long sinceLast = now - lastHomeAt;
        boolean isSamePress = sinceLast < SAME_PRESS_MS;
        boolean isDouble = !isSamePress && sinceLast < DOUBLE_PRESS_MS && !screens.isOpen() && !overlay.isOpen() && !grid.isOpen();
        if (!isSamePress) lastHomeAt = now;   // der Doppelstart eines Drucks verschiebt den Bezugspunkt nicht
        hideKeyboard(scene.modalLayer);
        scene.modalLayer.removeAllViews();   // HOME schließt alle Menüs sofort, ohne Ausblend-Animation
        if (screens.isOpen()) closeScreen();
        if (overlay.isOpen()) overlay.close();
        if (grid.isOpen()) grid.hide(); else dock.focus();
        if (isDouble && Config.val(Opts.SHOW_BAR_SETTINGS) == 0) {
            settingsByDoubleHomeAt = now;
            openSettings(null);
        }
    }

    @Override
    public void onBackPressed() {
        if (Ui.dismissTopModal(scene.modalLayer)) return;
        if (screens.isOpen()) {
            if (!screens.current().onBack()) closeScreen();
            return;
        }
        if (overlay.isOpen()) overlay.close();
        else if (grid.isOpen()) grid.hide();
        // sonst bleiben wir: das ist die Startseite
    }

    /** Menütaste bei offener Vollbild-Ansicht: die Ansicht entscheidet (Menüs und Eingaben haben Vorrang). */
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_MENU && screens.isOpen() && !screens.hasModal()) {
            if (event.getAction() == KeyEvent.ACTION_UP) screens.current().onMenu();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    // ---- Einstellungen anwenden ------------------------------------------------------------------

    /**
     * Wendet die Einstellungen aus {@link Opts} auf die Startseite an: Kopfzeile, Sicherheitsrand, Platz der Blöcke, Kachelmaße
     * und Abdunkeln. Läuft bei jeder Änderung (auch einer Probe) und beim Start; ist billig und darf oft laufen.
     */
    private void applyConfig() {
        if (scene == null) return;
        scene.header.applyConfig();
        scene.title.setVisibility(Config.val(Opts.SHOW_DOCK_TITLE) == 0 ? View.GONE : View.VISIBLE);
        int margin = Ui.px(this, Config.val(Opts.SAFE_MARGIN));
        scene.root.setPadding(margin, margin, margin, margin);
        geometry.apply(dock.isEmptyAndLoaded());
        if (sizes.isStale()) {   // Kachelmaße, Spalten oder Sortierung geändert: Dock und Raster neu bauen
            sizes.refresh(this);
            if (!catalog.isEmpty()) {
                dock.rebuild(null);
                if (grid.isOpen()) grid.rebuild();
            }
        }
        applyLook();
    }

    /** Beim Heimkommen: Kopfzeile und Leiste gleiten von oben ein, das Dock von unten. */
    private void playEntrance() {
        if (grid.isOpen() || overlay.isOpen() || Config.val(Opts.ENTRANCE) == 0 || Config.val(Opts.MOTION) == 3) return;
        int sign = HomeGeometry.stripSign();   // Dock oben: alles gleitet in die Gegenrichtung
        View head = scene.header.view();
        head.setTranslationY(sign * Ui.px(this, 14));
        head.setAlpha(0f);
        scene.bar.setTranslationY(sign * Ui.px(this, 14));
        scene.bar.setAlpha(0f);
        scene.bottom.setTranslationY(-sign * Ui.px(this, 34));
        head.animate().translationY(0).alpha(1f).setDuration(Ui.ms(ENTRANCE_MS)).setInterpolator(HomeScene.EASE).start();
        scene.bar.animate().translationY(0).alpha(1f).setDuration(Ui.ms(ENTRANCE_MS)).setInterpolator(HomeScene.EASE).start();
        scene.bottom.animate().translationY(0).setDuration(Ui.ms(ENTRANCE_MS)).setInterpolator(HomeScene.EASE).start();
    }

    // ---- Ansichten öffnen ------------------------------------------------------------------------

    /** Öffnet die Einstellungen, auf Wunsch gleich auf einer Seite (Tiefenlink aus der Suche). */
    void openSettings(String pageId) {
        SettingsScreen screen = new SettingsScreen(this, SettingsPages.build(this));
        openScreen(screen);
        if (pageId != null) screen.openPath(pageId);
    }

    @Override
    public void openControlCenter() {
        ControlCenter center = new ControlCenter(this);
        center.onOpenSettings = () -> openSettings(null);
        openScreen(center);
    }

    @Override
    public void openGallery() {
        openScreen(new GalleryScreen(this));
    }

    @Override
    public void showWelcome() {
        openScreen(new Welcome(this, () -> { }));
    }

    @Override public void openScreen(Screen screen) { screens.open(screen); }

    @Override public void closeScreen() { screens.close(); }

    /** Die Ansichten machen sich selbst durchsichtig; die Startseite muss dafür nichts tun. */
    @Override public void setPreviewMode(boolean on) { }

    // ---- HomeHost: was die Ansichten brauchen ----------------------------------------------------

    @Override public Context context() { return this; }

    @Override public ViewGroup modalLayer() { return scene.modalLayer; }

    @Override public AppLayout layout() { return layout; }

    @Override public Look look() { return look; }

    @Override public Config config() { return config; }

    @Override public Wallpapers wallpapers() { return wallpapers; }

    @Override public void applyLook() { backdrop.applyLook(); }

    @Override public void nextWallpaper() { backdrop.pickNext(); }

    @Override public Bitmap blurredWallpaper() { return backdrop.blurred(); }

    @Override public List<String> wallpaperCredits() { return backdrop.credits(); }

    @Override public String currentWallpaperCredit() { return backdrop.credit(); }

    @Override
    public List<AppInfo> apps() {
        List<AppInfo> list = new ArrayList<>();
        for (AppEntry app : catalog.all()) list.add(new AppInfo(app.pkg, nameOf(app)));
        Collections.sort(list, (a, b) -> a.label.compareToIgnoreCase(b.label));
        return list;
    }

    /** Baut Dock, Raster und offenen Ordner nach einer Änderung an Ordnern, Namen oder Sichtbarkeit neu auf. */
    @Override
    public void afterLayoutChange() {
        dock.rebuild(null);
        if (grid.isOpen()) grid.rebuild();
        overlay.refreshFolder();
    }

    @Override
    public void resetDock() {
        dockStore.reset();
        dock.forgetFocus();
        dock.rebuild(null);
        if (grid.isOpen()) grid.rebuild();
    }

    @Override public boolean showRecent() { return layout.showRecent(); }

    @Override public void setShowRecent(boolean show) { layout.setShowRecent(show); }

    @Override public boolean isFilterActive() { return HomeHeader.isFilterActive(this); }

    @Override
    public boolean canStart(Intent intent) {
        ResolveInfo found = getPackageManager().resolveActivity(intent, 0);
        if (found == null || !found.activityInfo.exported) return false;
        String required = found.activityInfo.permission;
        return required == null || checkSelfPermission(required) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public boolean start(Intent intent) {
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            toast("Das lässt sich hier nicht öffnen");
            return false;
        }
    }

    @Override
    public void openAmazonOverview() {
        start(new Intent(Intent.ACTION_MAIN).addCategory(SETTINGS_CATEGORY));
    }

    /** Öffnet eine Seite in Silk (oder einem anderen Browser); false, wenn es keinen gibt. */
    @Override
    public boolean openUrl(String url) {
        Intent page = new Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(new Intent(page).setPackage(AppCatalog.SILK));
            return true;
        } catch (ActivityNotFoundException | SecurityException silkMissing) {
            try {
                startActivity(page);
                return true;
            } catch (ActivityNotFoundException | SecurityException noBrowser) {
                toast("Kein Webbrowser verfügbar");
                return false;
            }
        }
    }

    @Override
    public String saveBackup() {
        try {
            File file = Backup.save(this);
            return file == null ? "Kein Speicher verfügbar" : "Sicherung gespeichert";
        } catch (IOException | JSONException e) {
            Log.w(HomeKeyService.TAG, "backup failed", e);
            return "Sichern ist fehlgeschlagen";
        }
    }

    @Override
    public String restoreBackup() {
        try {
            if (!Backup.restore(this)) return "Keine Sicherung gefunden";
            Config current = Config.i();
            if (current != null) current.reloaded();   // der Glasstil hält seine Werte im Speicher und liest sonst die alten
            pendingMessage = "Sicherung geladen";
            recreate();   // alles mit den geladenen Werten neu aufbauen
            return "Lade Sicherung …";
        } catch (IOException | JSONException e) {
            Log.w(HomeKeyService.TAG, "restore failed", e);
            return "Die Sicherung ist nicht lesbar";
        }
    }

    @Override
    public void toast(String message) {
        GlassToast.show(scene.modalLayer, message);
    }
}
