package app.calmtv.home;

import android.accessibilityservice.AccessibilityService;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

/**
 * Bringt die HOME-Taste der Fernbedienung zu TV Home.
 *
 * <p>Fire OS schickt HOME direkt an Amazons Startseite (nur von Amazon signierte Apps dürfen das übernehmen), und Android hält danach
 * den Start anderer Apps rund 5 Sekunden zurück. Sobald Amazons Startseite erscheint, tut der Dienst deshalb Folgendes:
 * <ol>
 *   <li>deckt sie mit einer unscharfen Kopie des Hintergrunds ab, damit Amazons Werbung nie zu sehen ist ({@link AmazonCover});</li>
 *   <li>startet TV Home über einen Alarm ({@link HomeLauncher#viaAlarm}), den die 5-Sekunden-Sperre nicht trifft;</li>
 *   <li>als Rückfall tippt er nach 0,5 s die TV-Home-Kachel in Amazons Favoritenreihe an und fragt nach 2,5 s direkt nach TV Home,
 *       falls der Alarm nicht ankam.</li>
 * </ol>
 * Wer im Schnellmenü von Amazon "Apps" oder "Einstellungen" wählt, wird durchgelassen, denn die liegen in Amazons Launcher.
 * Auf Geräten, die HOME an Bedienungshilfen weitergeben, wird die Taste direkt behandelt; langes Drücken öffnet das Schnellmenü.
 */
public class HomeKeyService extends AccessibilityService {
    private static final String AMAZON_SETTINGS = "com.amazon.tv.settings.v2";
    private static final String AMAZON_LAUNCHER = "com.amazon.tv.launcher";
    static final String TAG = "TVHome";
    /** Unser Name in Amazons Favoritenreihe; Amazon merkt sich Namen, darum kann dort noch der alte "Home" stehen. */
    private static final String[] TILE_NAMES = {"TV Home", "Home"};
    private static final String APP_TILE_CLASS = "android.widget.FrameLayout";
    private static final long LONG_PRESS_MS = 600, COVER_MAX_MS = 7_000, COVER_LINGER_MS = 250;
    private static final long QUICK_MENU_GRACE_MS = 2_000, TILE_FALLBACK_MS = 500, DIRECT_FALLBACK_MS = 2_500, TILE_RETRY_MS = 120;
    private static final int TILE_ATTEMPTS = 12;

    /** Der laufende Dienst, solange Android ihn gebunden hat; der Einschlaf-Timer braucht ihn für den Ruhezustand. */
    private static volatile HomeKeyService running;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable showQuickMenu = this::onLongPress;
    private final Runnable tapTile = this::tapOurTile;
    private final Runnable removeCover = this::hideCover;
    private final Runnable askDirectly = () -> HomeLauncher.direct(this);
    private AmazonCover cover;
    private boolean isLongPress;
    private int tileAttempts;
    private String previousPackage = "", previousClass = "";
    /** Wer im Schnellmenü Apps oder Einstellungen wählt, öffnet Bildschirme in Amazons Launcher: dieser eine Übergang darf durch. */
    private long amazonAllowedUntil;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        cover = new AmazonCover(this);
        running = this;
    }

    @Override
    public void onInterrupt() {}

    @Override
    public void onDestroy() {
        if (running == this) running = null;
        hideCover();
        super.onDestroy();
    }

    // ---- Taste -----------------------------------------------------------------------------------

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (event.getKeyCode() != KeyEvent.KEYCODE_HOME) return false;
        int action = event.getAction();
        if (action == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            isLongPress = false;
            handler.postDelayed(showQuickMenu, LONG_PRESS_MS);
        } else if (action == KeyEvent.ACTION_UP) {
            handler.removeCallbacks(showQuickMenu);
            if (!isLongPress) HomeLauncher.direct(this);
        }
        return true;
    }

    private void onLongPress() {
        isLongPress = true;
        HomeLauncher.quickMenu(this);
    }

    // ---- Fensterwechsel --------------------------------------------------------------------------

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        String cls = String.valueOf(event.getClassName());
        String pkg = String.valueOf(event.getPackageName());
        if (getPackageName().equals(pkg)) {
            cancelFallbacks();
            handler.removeCallbacks(removeCover);
            handler.postDelayed(removeCover, COVER_LINGER_MS);   // TV Home soll zuerst zeichnen
        } else if (pkg.equals(AMAZON_LAUNCHER) && cls.startsWith(AMAZON_LAUNCHER + ".ui.HomeActivity")) {
            long now = SystemClock.uptimeMillis();
            if (previousPackage.equals(AMAZON_SETTINGS) && previousClass.contains(".hud.")) amazonAllowedUntil = now + QUICK_MENU_GRACE_MS;
            if (now >= amazonAllowedUntil) takeOverFromAmazon();
        } else if (!pkg.equals(AMAZON_LAUNCHER)) {
            // Eine andere App kam nach vorn: ein noch wartender Start darf den Nutzer nicht wieder herausreißen.
            if (!"com.android.systemui".equals(pkg) && !"android".equals(pkg)) {
                HomeLauncher.cancelAlarm(this);
                cancelFallbacks();
            }
            if (cover != null && cover.isShown()) hideCover();
        }
        previousPackage = pkg;
        previousClass = cls;
    }

    private void cancelFallbacks() {
        handler.removeCallbacks(tapTile);
        handler.removeCallbacks(askDirectly);
    }

    /** Amazons Startseite ist aufgetaucht: abdecken, TV Home per Alarm holen und die beiden Rückfälle vorbereiten. */
    private void takeOverFromAmazon() {
        showCover();
        HomeLauncher.viaAlarm(this);
        tileAttempts = 0;
        cancelFallbacks();
        handler.postDelayed(tapTile, TILE_FALLBACK_MS);   // nur nötig, wenn der Alarm nicht ankam
        handler.postDelayed(askDirectly, DIRECT_FALLBACK_MS);
    }

    /** Rückfall: die Kachel "TV Home" in Amazons Favoritenreihe antippen (App-Kacheln sind FrameLayouts, Amazons eigene Home-Taste eine ViewGroup). */
    private void tapOurTile() {
        AccessibilityNodeInfo window = getRootInActiveWindow();
        boolean isAmazonOnTop = window != null && AMAZON_LAUNCHER.equals(String.valueOf(window.getPackageName()));
        if (isAmazonOnTop) {
            for (String name : TILE_NAMES) {
                for (AccessibilityNodeInfo node : window.findAccessibilityNodeInfosByText(name)) {
                    boolean isOurTile = name.equals(String.valueOf(node.getContentDescription()))
                            && APP_TILE_CLASS.equals(String.valueOf(node.getClassName()));
                    if (isOurTile && node.isClickable() && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return;
                }
            }
        }
        if (++tileAttempts < TILE_ATTEMPTS) handler.postDelayed(tapTile, TILE_RETRY_MS);
        else Log.i(TAG, "TV Home tile not found in Amazon's favourites row; waiting for Android instead");
    }

    private void showCover() {
        if (cover == null || cover.isShown()) return;
        cover.show();
        if (cover.isShown()) handler.postDelayed(removeCover, COVER_MAX_MS);
    }

    private void hideCover() {
        handler.removeCallbacks(removeCover);
        if (cover != null) cover.hide();
    }

    // ---- Für andere Klassen ----------------------------------------------------------------------

    /**
     * Schaltet den Bildschirm aus (Ruhezustand) über die Bedienungshilfen-Aktion "Sperrbildschirm"; braucht keine Systemberechtigung.
     * false, wenn der Dienst nicht läuft oder Android die Aktion ablehnt.
     */
    static boolean sleepNow() {
        HomeKeyService service = running;
        if (service == null) return false;
        try {
            return service.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
        } catch (RuntimeException e) {
            Log.w(TAG, "can't lock screen", e);
            return false;
        }
    }
}
