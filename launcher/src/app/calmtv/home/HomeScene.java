package app.calmtv.home;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Der Ansichtenbaum der Startseite, nur Gerüst ohne Verhalten: von hinten nach vorn Hintergrund, Schatten, Kopfzeile, Leiste,
 * Raster, Dock mit Titel, Vollbild-Ebene und Menü-Ebene. Das Verhalten (Fokus, Tasten, Animationen) liegt in den Bausteinen
 * {@link HomeDock}, {@link AppGrid}, {@link HomeBar}, {@link HomeOverlay} und {@link HomeScreens}, die diese Ansichten nutzen.
 */
final class HomeScene {
    /** Bremsende Bewegung für das Gleiten von Dock, Raster und Kopfzeile. */
    static final DecelerateInterpolator EASE = new DecelerateInterpolator(1.8f);
    private static final int EDGE_DP = 44;   // seitlicher Rand von Dock und Raster; die Leiste schließt rechts bündig ab

    final Context context;
    final FrameLayout root, screenLayer, modalLayer;
    final ImageView wallBottom, wallTop, libraryBg;
    final View wallDim, libraryDim;
    final HomeHeader header;
    final LinearLayout bar, bottom, dockRow, gridRows;
    final HorizontalScrollView dockScroll;
    final ScrollView grid;
    final TextView title, dockHint;
    final Glass dock;
    final Pill appsPill, searchPill, settingsPill;
    /** Alles, was das unscharfe Hintergrundbild als Glas zeigt (Leiste, Dock, Suchfeld). */
    final List<Glass> glass = new ArrayList<>();

    HomeScene(Context context, Config config) {
        this.context = context;
        root = new FrameLayout(context);
        root.setBackgroundColor(Color.BLACK);
        root.setClipChildren(false);   // fokussierte Tasten wachsen über ihre Reihe hinaus

        wallBottom = photoView();
        wallTop = photoView();
        wallTop.setAlpha(0f);
        wallDim = new View(context);
        wallDim.setBackgroundColor(Color.BLACK);
        root.addView(wallBottom, fill());
        root.addView(wallTop, fill());
        root.addView(wallDim, fill());
        // Weiche Schatten oben und unten, damit Uhr, Tasten und Dock auf hellen Fotos lesbar bleiben.
        root.addView(shade(GradientDrawable.Orientation.TOP_BOTTOM, 0xA0000000), new FrameLayout.LayoutParams(-1, px(250), Gravity.TOP));
        root.addView(shade(GradientDrawable.Orientation.BOTTOM_TOP, 0xB0000000), new FrameLayout.LayoutParams(-1, px(300), Gravity.BOTTOM));

        // Hinter dem App-Raster: das Foto, unscharf und abgedunkelt.
        libraryBg = photoView();
        libraryBg.setAlpha(0f);
        libraryDim = new View(context);
        libraryDim.setBackgroundColor(0x59000000);
        libraryDim.setAlpha(0f);
        root.addView(libraryBg, fill());
        root.addView(libraryDim, fill());

        header = new HomeHeader(context, config);
        FrameLayout.LayoutParams headParams = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        headParams.setMargins(px(56), px(30), 0, 0);   // linke Kante wie die erste Dock-Kachel
        root.addView(header.view(), headParams);

        appsPill = new Pill(context, Icons.APPS, "Apps");
        searchPill = new Pill(context, Icons.SUCHE, "Suche");
        settingsPill = new Pill(context, Icons.SYSTEM, "Einstellungen");
        bar = new LinearLayout(context);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setClipChildren(false);
        for (Pill pill : pills()) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, px(36));
            params.leftMargin = px(9);
            bar.addView(pill, params);
            glass.add(pill);
        }
        FrameLayout.LayoutParams barParams = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        barParams.setMargins(0, px(32), px(EDGE_DP), 0);
        root.addView(bar, barParams);

        // Alle übrigen Apps, unter dem Dock, wenn man nach unten drückt.
        grid = new GridScroll(context);
        grid.setClipToPadding(true);
        grid.setVisibility(View.GONE);
        gridRows = Views.linear(context, LinearLayout.VERTICAL);   // eine fokussierte Kachel in der ersten Spalte wächst in den Rand
        gridRows.setPadding(px(EDGE_DP + 12), 0, px(EDGE_DP + 12), 0);
        grid.addView(gridRows);
        root.addView(grid, fill());

        // Name der fokussierten App, darunter das Dock.
        bottom = new LinearLayout(context);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setClipChildren(false);
        title = Ui.label(context, "", 27, 400, Color.WHITE);
        title.setShadowLayer(px(6), 0, px(1), 0x66000000);
        title.setSingleLine();
        title.setAlpha(0f);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-2, -2);
        titleParams.setMargins(px(12), 0, 0, px(10));
        bottom.addView(title, titleParams);

        dock = new Glass(context, px(26));
        dock.setElevation(px(12));
        dockScroll = new DockScroll(context, px(12));
        dockScroll.setHorizontalScrollBarEnabled(false);
        dockScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        dockRow = Views.linear(context, LinearLayout.HORIZONTAL);
        dockRow.setPadding(px(12), px(14), px(12), px(14));
        dockScroll.addView(dockRow);
        dock.addView(dockScroll, new FrameLayout.LayoutParams(-1, -2));
        // Der Hinweis "hier geht es weiter" ragt über den Glasrand hinaus, also darf das Glas nicht beschneiden.
        dock.setClipChildren(false);
        FrameLayout.LayoutParams hintParams = new FrameLayout.LayoutParams(-1, -1);
        hintParams.setMargins(-px(DockEdgeHint.OUTSET_DP), 0, -px(DockEdgeHint.OUTSET_DP), 0);
        dock.addView(new DockEdgeHint(context, dockScroll, dockRow), hintParams);
        dockHint = HomeText.plain(context, 14, "sans-serif", 0xCCFFFFFF);
        dockHint.setText("Dein Dock ist leer. Öffne Apps und drücke die Menütaste auf einer App, um sie hier hinzuzufügen.");
        dockHint.setVisibility(View.GONE);
        glass.add(dock);
        dock.addView(dockHint, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        bottom.addView(dock, new LinearLayout.LayoutParams(-1, -2));
        FrameLayout.LayoutParams bottomParams = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        bottomParams.setMargins(px(EDGE_DP), 0, px(EDGE_DP), px(30));
        root.addView(bottom, bottomParams);

        screenLayer = layer();   // genau eine Vollbild-Ansicht (Einstellungen, Kontrollzentrum, Willkommen)
        modalLayer = layer();    // Menüs, Eingabefelder, Meldungen: immer ganz oben
        root.addView(screenLayer, fill());
        root.addView(modalLayer, fill());
    }

    Pill[] pills() {
        return new Pill[] {appsPill, searchPill, settingsPill};
    }

    int px(float dips) {
        return Ui.px(context, dips);
    }

    static FrameLayout.LayoutParams fill() {
        return new FrameLayout.LayoutParams(-1, -1);
    }

    ImageView photoView() {
        ImageView view = new ImageView(context);
        view.setScaleType(ImageView.ScaleType.CENTER_CROP);
        return view;
    }

    private FrameLayout layer() {
        FrameLayout layer = new FrameLayout(context);
        layer.setClipChildren(false);
        return layer;
    }

    private View shade(GradientDrawable.Orientation direction, int color) {
        View view = new View(context);
        view.setBackground(new GradientDrawable(direction, new int[] {color, 0x00000000}));
        return view;
    }
}
