package app.calmtv.home;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/**
 * Symbole, im Code gezeichnet, für Einstellungen, Menüs und Kontrollzentrum.
 * Die Kennungen stehen fest, damit alle Ansichten dieselben nutzen; gezeichnet wird in {@link Glyph}.
 *
 * Stil wie SF Symbols: Umrisse mit abgerundeten Enden und Ecken, Strichstärke 8 % der Größe, 12 % Rand.
 * Neue Symbole bekommen Nummern ab 33 und einen Zweig in {@link Glyph#build}.
 */
final class Icons {
    static final int NONE = -1;
    static final int DARSTELLUNG = 0, APPS = 1, SCHIRMSCHONER = 2, VERBINDUNG = 3, SYSTEM = 4, DATENSCHUTZ = 5,
            SICHERUNG = 6, INFO = 7, ORDNER = 8, AUSGEBLENDET = 9, NACH_VORN = 10, UMBENENNEN = 11, PAPIERKORB = 12,
            PFEIL_RECHTS = 13, PFEIL_ZURUECK = 14, HAKEN = 15, WLAN = 16, BLUETOOTH = 17, TEMPO = 18, SONNE = 19,
            MOND = 20, SCHILD = 21, DOCK = 22, WETTER = 23, FILTER = 24, ABDUNKELN = 25, FARBE = 26, BILD = 27,
            SUCHE = 28, EINSTELLUNGEN = 29, LAUFZEIT = 30, SPEICHER = 31, STERN = 32,
            HINZUFUEGEN = 33, ENTFERNEN = 34, TV = 35, UNSCHARF = 36, HERUNTERLADEN = 37;

    /** Seitenlänge des Entwurfsrasters; alle Koordinaten unten liegen in diesem Raster (Inhalt etwa zwischen 4 und 20). */
    private static final float GRID = 24f;
    private static final float STROKE_SHARE = 0.08f;
    private static final int DEFAULT_SIZE_PX = 44;

    private Icons() {}

    /** Ein Symbol in der gewünschten Farbe; Größe ergibt sich aus den Grenzen der Ansicht. */
    static Glyph icon(int id, int color) {
        Glyph g = new Glyph(id);
        g.setColor(color);
        return g;
    }

    /** Das gezeichnete Symbol: Linien (Umriss) und kleine gefüllte Punkte, beide in einer Farbe. */
    static class Glyph extends Drawable {
        final int id;
        private int color;
        private int alpha = 255;
        private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint solidPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path lines = new Path();
        private final Path solids = new Path();
        private final Matrix fit = new Matrix();

        Glyph(int id) {
            this.id = id;
            linePaint.setStyle(Paint.Style.STROKE);
            linePaint.setStrokeCap(Paint.Cap.ROUND);
            linePaint.setStrokeJoin(Paint.Join.ROUND);
            solidPaint.setStyle(Paint.Style.FILL);
            applyColor();
        }

        void setColor(int color) {
            this.color = color;
            applyColor();
            invalidateSelf();
        }

        int color() {
            return color;
        }

        /** Die gesetzte Farbe mit dem Gesamt-Alpha der Zeichnung verrechnen. */
        private void applyColor() {
            int effective = Math.round(Color.alpha(color) * alpha / 255f);
            linePaint.setColor(color);
            linePaint.setAlpha(effective);
            solidPaint.setColor(color);
            solidPaint.setAlpha(effective);
        }

        @Override protected void onBoundsChange(Rect b) {
            lines.reset();
            solids.reset();
            float size = Math.min(b.width(), b.height());
            if (id < 0 || size <= 0) return;
            build(id, lines, solids);
            float scale = size / GRID;
            fit.setScale(scale, scale);
            fit.postTranslate(b.left + (b.width() - size) / 2f, b.top + (b.height() - size) / 2f);
            lines.transform(fit);
            solids.transform(fit);
            linePaint.setStrokeWidth(size * STROKE_SHARE);
        }

        @Override public void draw(Canvas canvas) {
            if (id < 0) return;
            canvas.drawPath(lines, linePaint);
            canvas.drawPath(solids, solidPaint);
        }

        @Override public void setAlpha(int alpha) {
            this.alpha = alpha;
            applyColor();
            invalidateSelf();
        }

        @Override public void setColorFilter(ColorFilter filter) {}

        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }

        @Override public int getIntrinsicWidth() { return DEFAULT_SIZE_PX; }

        @Override public int getIntrinsicHeight() { return DEFAULT_SIZE_PX; }

        // ---- Zeichnungen im 24er-Raster -----------------------------------------------------------

        private static void build(int id, Path l, Path s) {
            switch (id) {
                case DARSTELLUNG: palette(l, s); break;
                case APPS: appsGrid(l); break;
                case SCHIRMSCHONER: monitorWithMoon(l, s); break;
                case VERBINDUNG: broadcast(l, s); break;
                case SYSTEM: gear(l); break;
                case DATENSCHUTZ: lock(l, s); break;
                case SICHERUNG: archiveBox(l); break;
                case INFO: info(l, s); break;
                case ORDNER: folder(l); break;
                case AUSGEBLENDET: eyeCrossed(l); break;
                case NACH_VORN: pin(l); break;
                case UMBENENNEN: pencil(l); break;
                case PAPIERKORB: trash(l); break;
                case PFEIL_RECHTS: polyline(l, 9, 5, 16, 12, 9, 19); break;
                case PFEIL_ZURUECK: arrowBack(l); break;
                case HAKEN: polyline(l, 5, 12.5f, 10, 17.5f, 19.5f, 7); break;
                case WLAN: wifi(l, s); break;
                case BLUETOOTH: polyline(l, 7, 8, 17, 16, 12, 20, 12, 4, 17, 8, 7, 16); break;
                case TEMPO: gauge(l, s); break;
                case SONNE: sun(l); break;
                case MOND: moon(l); break;
                case SCHILD: shield(l); break;
                case DOCK: dock(l, s); break;
                case WETTER: cloud(l); break;
                case FILTER: shield(l); polyline(l, 8.8f, 12, 11.2f, 14.4f, 15.4f, 9.6f); break;
                case ABDUNKELN: halfDisc(l, s); break;
                case FARBE: drop(l); break;
                case BILD: picture(l, s); break;
                case SUCHE: magnifier(l); break;
                case EINSTELLUNGEN: sliders(l); break;
                case LAUFZEIT: clock(l); break;
                case SPEICHER: chip(l, s); break;
                case STERN: star(l); break;
                case HINZUFUEGEN: circle(l, 12, 12, 8.2f); line(l, 12, 8, 12, 16); line(l, 8, 12, 16, 12); break;
                case TV: tv(l); break;
                case UNSCHARF: blur(l, s); break;
                case HERUNTERLADEN: download(l); break;
                case ENTFERNEN: circle(l, 12, 12, 8.2f); line(l, 8, 12, 16, 12); break;
                default: break;
            }
        }

        private static void polyline(Path p, float... xy) {
            p.moveTo(xy[0], xy[1]);
            for (int i = 2; i < xy.length; i += 2) p.lineTo(xy[i], xy[i + 1]);
        }

        private static void line(Path p, float x1, float y1, float x2, float y2) {
            p.moveTo(x1, y1);
            p.lineTo(x2, y2);
        }

        private static void roundRect(Path p, float left, float top, float right, float bottom, float radius) {
            p.addRoundRect(new RectF(left, top, right, bottom), radius, radius, Path.Direction.CW);
        }

        private static void circle(Path p, float cx, float cy, float r) {
            p.addCircle(cx, cy, r, Path.Direction.CW);
        }

        /** Kreisbogen; Winkel in Grad, 0 = rechts, im Uhrzeigersinn. */
        private static void arc(Path p, float cx, float cy, float r, float start, float sweep) {
            p.addArc(new RectF(cx - r, cy - r, cx + r, cy + r), start, sweep);
        }

        /** Farbpalette: ovaler Körper, drei Farbpunkte, Daumenloch. */
        private static void palette(Path l, Path s) {
            l.addOval(new RectF(3.8f, 4.8f, 20.2f, 19.6f), Path.Direction.CW);
            circle(l, 14.6f, 15.4f, 1.7f);
            circle(s, 8.2f, 11.4f, 1.15f);
            circle(s, 11.6f, 8.4f, 1.15f);
            circle(s, 15.6f, 9.4f, 1.15f);
        }

        private static void appsGrid(Path l) {
            roundRect(l, 4, 4, 10, 10, 2);
            roundRect(l, 14, 4, 20, 10, 2);
            roundRect(l, 4, 14, 10, 20, 2);
            roundRect(l, 14, 14, 20, 20, 2);
        }

        /** Monitor mit Fuß; im Bild ein Mond (Sichel aus zwei Kreisen). */
        private static void monitorWithMoon(Path l, Path s) {
            roundRect(l, 3.9f, 4.6f, 20.1f, 15.6f, 2.4f);
            line(l, 12, 15.6f, 12, 19.4f);
            line(l, 8, 19.4f, 16, 19.4f);
            Path crescent = new Path();
            circle(crescent, 11.6f, 10.1f, 3.1f);
            Path bite = new Path();
            circle(bite, 13.2f, 9.1f, 2.7f);
            crescent.op(bite, Path.Op.DIFFERENCE);
            s.addPath(crescent);
        }

        /** Punkt mit Funkwellen links und rechts. */
        private static void broadcast(Path l, Path s) {
            circle(s, 12, 12, 1.8f);
            arc(l, 12, 12, 4.8f, -42, 84);
            arc(l, 12, 12, 4.8f, 138, 84);
            arc(l, 12, 12, 8.2f, -42, 84);
            arc(l, 12, 12, 8.2f, 138, 84);
        }

        /** Zahnrad: acht Zähne als Umriss, Loch in der Mitte. */
        private static void gear(Path l) {
            final int teeth = 8;
            final float tip = 8.3f, root = 6.3f, half = (float) Math.PI / teeth;
            for (int i = 0; i < teeth; i++) {
                double center = i * 2 * Math.PI / teeth;
                double[] angles = {center - half * 0.72, center - half * 0.36, center + half * 0.36, center + half * 0.72};
                float[] radii = {root, tip, tip, root};
                for (int k = 0; k < 4; k++) {
                    float x = 12 + radii[k] * (float) Math.cos(angles[k]);
                    float y = 12 + radii[k] * (float) Math.sin(angles[k]);
                    if (i == 0 && k == 0) l.moveTo(x, y); else l.lineTo(x, y);
                }
            }
            l.close();
            circle(l, 12, 12, 2.7f);
        }

        private static void lock(Path l, Path s) {
            roundRect(l, 5.5f, 10.5f, 18.5f, 20, 2.4f);
            l.moveTo(8.4f, 10.5f);
            l.lineTo(8.4f, 8);
            arc(l, 12, 8, 3.6f, 180, 180);
            l.lineTo(15.6f, 10.5f);
            circle(s, 12, 15.2f, 1.3f);
        }

        /** Archivbox: Deckel, Körper, Griff. */
        private static void archiveBox(Path l) {
            roundRect(l, 4, 4.8f, 20, 9.6f, 1.8f);
            l.moveTo(5.6f, 9.6f);
            l.lineTo(5.6f, 17.4f);
            l.quadTo(5.6f, 19.6f, 7.8f, 19.6f);
            l.lineTo(16.2f, 19.6f);
            l.quadTo(18.4f, 19.6f, 18.4f, 17.4f);
            l.lineTo(18.4f, 9.6f);
            line(l, 10, 13.4f, 14, 13.4f);
        }

        private static void info(Path l, Path s) {
            circle(l, 12, 12, 8.2f);
            circle(s, 12, 8.3f, 1.2f);
            line(l, 12, 11.4f, 12, 16.4f);
        }

        private static void folder(Path l) {
            l.moveTo(4, 17);
            l.lineTo(4, 7.6f);
            l.quadTo(4, 6, 5.6f, 6);
            l.lineTo(9.4f, 6);
            l.lineTo(11.4f, 8.2f);
            l.lineTo(18.4f, 8.2f);
            l.quadTo(20, 8.2f, 20, 9.8f);
            l.lineTo(20, 17);
            l.quadTo(20, 18.6f, 18.4f, 18.6f);
            l.lineTo(5.6f, 18.6f);
            l.quadTo(4, 18.6f, 4, 17);
            l.close();
        }

        private static void eyeCrossed(Path l) {
            l.moveTo(4, 12);
            l.cubicTo(6.2f, 8, 9, 6.6f, 12, 6.6f);
            l.cubicTo(15, 6.6f, 17.8f, 8, 20, 12);
            l.cubicTo(17.8f, 16, 15, 17.4f, 12, 17.4f);
            l.cubicTo(9, 17.4f, 6.2f, 16, 4, 12);
            circle(l, 12, 12, 2.5f);
            line(l, 5.6f, 19, 18.4f, 5);
        }

        /** Stecknadel: Kopf, Spitze, Nadel. */
        private static void pin(Path l) {
            polyline(l, 9.4f, 4.6f, 14.6f, 4.6f, 14.1f, 10, 17, 13, 7, 13, 9.9f, 10);
            l.close();
            line(l, 12, 13, 12, 19.6f);
        }

        /** Stift: senkrecht gebaut und um 45 Grad gedreht (Spitze unten links). */
        private static void pencil(Path l) {
            Path body = new Path();
            polyline(body, 10.4f, 3.8f, 13.6f, 3.8f, 13.6f, 16.2f, 12, 19.4f, 10.4f, 16.2f);
            body.close();
            line(body, 10.4f, 7.2f, 13.6f, 7.2f);
            line(body, 10.4f, 16.2f, 13.6f, 16.2f);
            Matrix turn = new Matrix();
            turn.setRotate(45, 12, 12);
            body.transform(turn);
            l.addPath(body);
        }

        private static void trash(Path l) {
            line(l, 4.8f, 7, 19.2f, 7);
            polyline(l, 9.4f, 7, 9.4f, 4.8f, 14.6f, 4.8f, 14.6f, 7);
            l.moveTo(6.6f, 7);
            l.lineTo(7.5f, 18);
            l.quadTo(7.6f, 19.6f, 9.2f, 19.6f);
            l.lineTo(14.8f, 19.6f);
            l.quadTo(16.4f, 19.6f, 16.5f, 18);
            l.lineTo(17.4f, 7);
            line(l, 10.3f, 10.6f, 10.3f, 16.2f);
            line(l, 13.7f, 10.6f, 13.7f, 16.2f);
        }

        private static void arrowBack(Path l) {
            line(l, 19.5f, 12, 5, 12);
            polyline(l, 11, 6, 5, 12, 11, 18);
        }

        /** Drei Bögen über einem Punkt (Fächer). */
        private static void wifi(Path l, Path s) {
            circle(s, 12, 18.4f, 1.4f);
            arc(l, 12, 18.4f, 4.2f, 228, 84);
            arc(l, 12, 18.4f, 7.6f, 228, 84);
            arc(l, 12, 18.4f, 11f, 228, 84);
        }

        /** Tacho: offener Bogen unten, Zeiger, Nabe. */
        private static void gauge(Path l, Path s) {
            arc(l, 12, 13.4f, 8.2f, 150, 240);
            line(l, 12, 13.4f, 16.2f, 8.8f);
            circle(s, 12, 13.4f, 1.5f);
        }

        private static void sun(Path l) {
            circle(l, 12, 12, 3.7f);
            for (int i = 0; i < 8; i++) {
                double a = i * Math.PI / 4;
                float cos = (float) Math.cos(a), sin = (float) Math.sin(a);
                line(l, 12 + 6.5f * cos, 12 + 6.5f * sin, 12 + 8.3f * cos, 12 + 8.3f * sin);
            }
        }

        /** Mondsichel: großer Kreis minus versetzter Kreis. */
        private static void moon(Path l) {
            Path crescent = new Path();
            circle(crescent, 11.4f, 12.6f, 7.6f);
            Path bite = new Path();
            circle(bite, 16, 8.8f, 6.2f);
            crescent.op(bite, Path.Op.DIFFERENCE);
            l.addPath(crescent);
        }

        private static void shield(Path l) {
            l.moveTo(12, 4);
            l.cubicTo(14.4f, 5.8f, 16.8f, 6.5f, 19, 6.5f);
            l.lineTo(19, 12);
            l.cubicTo(19, 16.4f, 15.8f, 19, 12, 20.4f);
            l.cubicTo(8.2f, 19, 5, 16.4f, 5, 12);
            l.lineTo(5, 6.5f);
            l.cubicTo(7.2f, 6.5f, 9.6f, 5.8f, 12, 4);
            l.close();
        }

        /** Bildschirm mit Leiste unten: drei kleine Kacheln im unteren Teil. */
        private static void dock(Path l, Path s) {
            roundRect(l, 4, 5, 20, 19, 2.8f);
            roundRect(s, 7, 12.4f, 9.8f, 15.2f, 0.9f);
            roundRect(s, 10.6f, 12.4f, 13.4f, 15.2f, 0.9f);
            roundRect(s, 14.2f, 12.4f, 17, 15.2f, 0.9f);
        }

        private static void cloud(Path l) {
            l.moveTo(7.6f, 18.4f);
            l.cubicTo(5.5f, 18.4f, 4, 16.9f, 4, 14.9f);
            l.cubicTo(4, 13, 5.4f, 11.6f, 7.2f, 11.4f);
            l.cubicTo(7.7f, 8.4f, 10, 6.4f, 12.8f, 6.4f);
            l.cubicTo(15.6f, 6.4f, 17.8f, 8.4f, 18.2f, 11.2f);
            l.cubicTo(19.3f, 11.6f, 20, 12.9f, 20, 14.6f);
            l.cubicTo(20, 16.8f, 18.5f, 18.4f, 16.4f, 18.4f);
            l.close();
        }

        /** Kreis, linke Hälfte gefüllt. */
        private static void halfDisc(Path l, Path s) {
            circle(l, 12, 12, 8.2f);
            s.addArc(new RectF(12 - 8.2f, 12 - 8.2f, 12 + 8.2f, 12 + 8.2f), 90, 180);
            s.close();
        }

        private static void drop(Path l) {
            l.moveTo(12, 4.2f);
            l.cubicTo(14.6f, 7.6f, 18.5f, 10.8f, 18.5f, 14.6f);
            l.cubicTo(18.5f, 18, 15.6f, 20, 12, 20);
            l.cubicTo(8.4f, 20, 5.5f, 18, 5.5f, 14.6f);
            l.cubicTo(5.5f, 10.8f, 9.4f, 7.6f, 12, 4.2f);
            l.close();
            arc(l, 12, 14.8f, 3.5f, 118, 62);
        }

        /** Landschaft: Rahmen, Sonne, zwei Berge. */
        private static void picture(Path l, Path s) {
            roundRect(l, 4, 5.4f, 20, 18.6f, 2.6f);
            circle(s, 9, 10, 1.5f);
            polyline(l, 4.4f, 16.6f, 9.4f, 12.6f, 13.2f, 16, 15.4f, 14.2f, 19.6f, 17.8f);
        }

        private static void magnifier(Path l) {
            circle(l, 10.8f, 10.8f, 6.4f);
            line(l, 15.6f, 15.6f, 19.8f, 19.8f);
        }

        /** Drei Regler mit Knopf (Einstellungen), Linien lassen den Knopf frei. */
        private static void sliders(Path l) {
            float[] knobX = {15, 8.4f, 13.6f};
            float[] rowY = {6.8f, 12, 17.2f};
            final float knobR = 2.3f, gap = 0.9f;
            for (int i = 0; i < 3; i++) {
                circle(l, knobX[i], rowY[i], knobR);
                line(l, 4, rowY[i], knobX[i] - knobR - gap, rowY[i]);
                line(l, knobX[i] + knobR + gap, rowY[i], 20, rowY[i]);
            }
        }

        private static void clock(Path l) {
            circle(l, 12, 12, 8.2f);
            polyline(l, 12, 7.2f, 12, 12, 15.6f, 14.2f);
        }

        /** Chip: Gehäuse mit Anschlüssen auf allen vier Seiten. */
        private static void chip(Path l, Path s) {
            roundRect(l, 7, 7, 17, 17, 2);
            float[] pins = {10.2f, 13.8f};
            for (float pin : pins) {
                line(l, pin, 3.8f, pin, 7);
                line(l, pin, 17, pin, 20.2f);
                line(l, 3.8f, pin, 7, pin);
                line(l, 17, pin, 20.2f, pin);
            }
            roundRect(s, 10.4f, 10.4f, 13.6f, 13.6f, 0.8f);
        }

        /** Fernseher: Bildschirm auf zwei Standfüßen. */
        private static void tv(Path l) {
            roundRect(l, 3.9f, 4.8f, 20.1f, 16.2f, 2.4f);
            line(l, 8.2f, 16.2f, 6.6f, 19.6f);
            line(l, 15.8f, 16.2f, 17.4f, 19.6f);
        }

        /** Unschärfe: zwei ineinander liegende Kreise um einen Punkt. */
        private static void blur(Path l, Path s) {
            circle(l, 12, 12, 8.3f);
            circle(l, 12, 12, 4.6f);
            circle(s, 12, 12, 1.5f);
        }

        /** Pfeil nach unten in eine Schale. */
        private static void download(Path l) {
            line(l, 12, 4.4f, 12, 14.4f);
            polyline(l, 8, 10.6f, 12, 14.6f, 16, 10.6f);
            l.moveTo(4.8f, 14.8f);
            l.lineTo(4.8f, 18);
            l.quadTo(4.8f, 19.6f, 6.4f, 19.6f);
            l.lineTo(17.6f, 19.6f);
            l.quadTo(19.2f, 19.6f, 19.2f, 18);
            l.lineTo(19.2f, 14.8f);
        }

        private static void star(Path l) {
            final float outer = 8.6f, inner = 4f, cy = 12.9f;
            for (int i = 0; i < 10; i++) {
                double a = -Math.PI / 2 + i * Math.PI / 5;
                float r = i % 2 == 0 ? outer : inner;
                float x = 12 + r * (float) Math.cos(a), y = cy + r * (float) Math.sin(a);
                if (i == 0) l.moveTo(x, y); else l.lineTo(x, y);
            }
            l.close();
        }
    }
}
