package app.calmtv.home.dream;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Eine Zeile der Einstellungen: Titel, Untertitel und rechts ein Schalter oder ein Pfeil. Fokus = Fläche in der Akzentfarbe. */
final class SetupRow extends LinearLayout {
    private static final float MIN_HEIGHT_DP = 56, PAD_DP = 16, SWITCH_W_DP = 46, SWITCH_H_DP = 28;

    private final SetupStyle style;
    private final TextView title, subtitle;
    private final Drawable idleFill, focusFill;
    private TextView chevron, value;
    private SetupSwitch toggle;

    SetupRow(Context context, SetupStyle style, String titleText, String subtitleText) {
        super(context);
        this.style = style;
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setMinimumHeight(style.px(MIN_HEIGHT_DP));
        setPadding(style.px(PAD_DP), style.px(8), style.px(PAD_DP), style.px(8));
        setFocusable(true);
        setClickable(true);
        idleFill = style.rounded(SetupStyle.ROW_FILL, SetupStyle.ROW_RADIUS);
        focusFill = style.rounded(style.accent, SetupStyle.ROW_RADIUS);
        setBackground(idleFill);

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(VERTICAL);
        title = style.text(context, titleText, 17, style.semibold, SetupStyle.TEXT);
        subtitle = style.text(context, subtitleText, 14, style.medium, SetupStyle.MUTED);
        texts.addView(title, new LayoutParams(-2, -2));
        LayoutParams subtitleParams = new LayoutParams(-2, -2);
        subtitleParams.topMargin = style.px(2);
        texts.addView(subtitle, subtitleParams);
        subtitle.setVisibility(subtitleText.isEmpty() ? GONE : VISIBLE);
        addView(texts, new LayoutParams(0, -2, 1f));
    }

    /** Fügt rechts einen Pfeil an (die Zeile öffnet etwas). */
    SetupRow withChevron() {
        chevron = style.text(getContext(), "›", 26, style.light, SetupStyle.MUTED);
        LayoutParams lp = new LayoutParams(-2, -2);
        lp.leftMargin = style.px(12);
        addView(chevron, lp);
        return this;
    }

    /** Fügt rechts den aktuellen Wert einer Auswahl an (Antippen schaltet weiter). */
    SetupRow withValue(String text) {
        value = style.text(getContext(), text, 17, style.semibold, SetupStyle.MUTED);
        LayoutParams lp = new LayoutParams(-2, -2);
        lp.leftMargin = style.px(16);
        addView(value, lp);
        return this;
    }

    void setValue(String text) {
        if (value != null) value.setText(text);
    }

    /** Abgeblendet, wenn die Zeile gerade ohne Wirkung ist (bleibt aber bedienbar). */
    void setDimmed(boolean isDimmed) {
        setAlpha(isDimmed ? 0.5f : 1f);
    }

    /** Fügt rechts einen Schalter an. */
    SetupRow withSwitch(boolean isChecked) {
        toggle = new SetupSwitch(getContext(), style, isChecked);
        LayoutParams lp = new LayoutParams(style.px(SWITCH_W_DP), style.px(SWITCH_H_DP));
        lp.leftMargin = style.px(16);
        addView(toggle, lp);
        return this;
    }

    void setChecked(boolean isChecked) {
        if (toggle != null) toggle.setChecked(isChecked, false);
    }

    boolean isChecked() {
        return toggle != null && toggle.isChecked();
    }

    /** Kippt den Schalter (mit kurzer Bewegung) und gibt den neuen Wert zurück. */
    boolean toggle() {
        toggle.setChecked(!toggle.isChecked(), true);
        return toggle.isChecked();
    }

    void setSubtitle(String text) {
        subtitle.setText(text);
        subtitle.setVisibility(text.isEmpty() ? GONE : VISIBLE);
    }

    @Override
    protected void onFocusChanged(boolean gainFocus, int direction, android.graphics.Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
        setBackground(gainFocus ? focusFill : idleFill);
        title.setTextColor(gainFocus ? style.onAccent : SetupStyle.TEXT);
        subtitle.setTextColor(gainFocus ? withAlpha(style.onAccent, 0xB3) : SetupStyle.MUTED);
        if (chevron != null) chevron.setTextColor(gainFocus ? style.onAccent : SetupStyle.MUTED);
        if (value != null) value.setTextColor(gainFocus ? style.onAccent : SetupStyle.MUTED);
        if (toggle != null) toggle.setRowFocused(gainFocus);
    }

    static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }
}
