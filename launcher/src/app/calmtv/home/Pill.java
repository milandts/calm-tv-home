package app.calmtv.home;

import android.animation.ArgbEvaluator;
import android.content.Context;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Eine kleine Glas-Kapsel mit Symbol und Name, wie die Tasten Apps, Suche und Einstellungen oben rechts. */
final class Pill extends Glass {
    private static final int CONTENT = 0xF2FFFFFF;
    private static final float FOCUS_SCALE = 1.06f;
    private static final long FOCUS_MS = 140;
    private static final ArgbEvaluator MIX = new ArgbEvaluator();

    private final Icons.Glyph glyph;
    private final TextView name;

    Pill(Context c, int iconKind, String label) {
        super(c, Ui.px(c, 100));
        setClickable(true);
        setFocusable(true);
        setContentDescription(label);

        glyph = Icons.icon(iconKind, CONTENT);
        ImageView symbol = new ImageView(c);
        symbol.setImageDrawable(glyph);
        name = new TextView(c);
        name.setSingleLine();
        name.setTextSize(14);
        name.setText(label);
        name.setTypeface(Ui.font(c, 600));
        name.setTextColor(CONTENT);

        LinearLayout row = new LinearLayout(c);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(Ui.px(c, 13), 0, Ui.px(c, 17), 0);
        int symbolSize = Ui.px(c, 17);
        row.addView(symbol, new LinearLayout.LayoutParams(symbolSize, symbolSize));
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(-2, -2);
        nameParams.leftMargin = Ui.px(c, 7);
        row.addView(name, nameParams);
        addView(row, new LayoutParams(-2, -1));

        setOnFocusChangeListener((v, hasFocus) -> {
            setSolid(hasFocus);   // die Farben folgen dieser Animation (siehe onSolidChanged)
            float scale = hasFocus ? FOCUS_SCALE : 1f;
            animate().scaleX(scale).scaleY(scale).setDuration(Ui.ms(FOCUS_MS)).start();
        });
    }

    /** Symbol und Name wechseln gemeinsam mit der weißen Füllung die Farbe und bleiben so durchgehend lesbar. */
    @Override
    protected void onSolidChanged(float solid) {
        int color = (int) MIX.evaluate(solid, CONTENT, Look.focusText());
        glyph.setColor(color);
        name.setTextColor(color);
    }
}
