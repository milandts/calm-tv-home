package app.calmtv.home.dream;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.List;

/**
 * Wettersymbol im Umriss-Stil mit runden Linienenden, vollständig im Code gezeichnet (kein Bitmap, kein Cache).
 * Die Formen entstehen einmal bei jeder Größenänderung als Pfade auf einem Raster von 48 Einheiten und werden dann nur noch gezeichnet.
 * Verdeckte Teile (Sonne hinter der Wolke) werden als Fläche der Linienkontur abzüglich der Wolke gebildet, so kreuzen sich nie zwei Linien.
 */
final class WeatherIcon extends Drawable {
    static final int SUN = 0, MOON = 1, PARTLY_SUN = 2, PARTLY_MOON = 3, OVERCAST = 4, FOG = 5, RAIN = 6, SNOW = 7, STORM = 8;
    static final int KIND_COUNT = 9;

    private static final float GRID = 48f;
    private static final float STROKE = 2.7f;      // Linienstärke in Rastereinheiten (ca. 2,5 dp bei 44 dp Größe)
    private static final float GAP = 2.4f;         // Abstand zwischen einer Wolke und dem, was dahinter liegt
    private static final float SHADOW_OFFSET = 1.1f;

    private final int kind;
    private final Paint strokePaint = linePaint(Paint.Style.STROKE);
    private final Paint fillPaint = linePaint(Paint.Style.FILL);
    private final Paint shadowStroke = linePaint(Paint.Style.STROKE);
    private final Paint shadowFill = linePaint(Paint.Style.FILL);
    private final Paint outlineMaker = linePaint(Paint.Style.STROKE);   // nur für getFillPath
    private final List<Path> outlines = new ArrayList<>();   // sichtbare Linien
    private final List<Path> fills = new ArrayList<>();      // Linien, die hinter der Wolke zugeschnitten wurden
    private final Matrix toPixels = new Matrix();
    private float shadowDy;

    /** Wählt das Symbol zum WMO-Wettercode; -1 für unbekannte Codes (dann zeigt man kein Symbol). */
    static int kindFor(int code, boolean isNight) {
        if (code == 0 || code == 1) return isNight ? MOON : SUN;
        if (code == 2) return isNight ? PARTLY_MOON : PARTLY_SUN;
        if (code == 3) return OVERCAST;
        if (code == 45 || code == 48) return FOG;
        if ((code >= 51 && code <= 67) || (code >= 80 && code <= 82)) return RAIN;
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return SNOW;
        if (code >= 95 && code <= 99) return STORM;
        return -1;
    }

    WeatherIcon(int kind, int color) {
        this.kind = kind;
        strokePaint.setColor(color);
        fillPaint.setColor(color);
        shadowStroke.setColor(0x4D000000);
        shadowFill.setColor(0x4D000000);
    }

    int kind() {
        return kind;
    }

    private static Paint linePaint(Paint.Style style) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(style);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        return p;
    }

    // ---- Zeichnen ----------------------------------------------------------------------------

    @Override
    public void draw(Canvas canvas) {
        if (outlines.isEmpty() && fills.isEmpty()) return;
        canvas.save();
        canvas.translate(0, shadowDy);
        for (Path p : outlines) canvas.drawPath(p, shadowStroke);
        for (Path p : fills) canvas.drawPath(p, shadowFill);
        canvas.restore();
        for (Path p : outlines) canvas.drawPath(p, strokePaint);
        for (Path p : fills) canvas.drawPath(p, fillPaint);
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);
        float size = Math.min(bounds.width(), bounds.height());
        if (size <= 0) {
            outlines.clear();
            fills.clear();
            return;
        }
        float scale = size / GRID;
        toPixels.setScale(scale, scale);
        toPixels.postTranslate(bounds.left + (bounds.width() - size) / 2f, bounds.top + (bounds.height() - size) / 2f);
        strokePaint.setStrokeWidth(STROKE * scale);
        shadowStroke.setStrokeWidth((STROKE + 1.4f) * scale);
        outlineMaker.setStrokeWidth(STROKE * scale);
        shadowDy = SHADOW_OFFSET * scale;
        build();
    }

    @Override public void setAlpha(int alpha) {
        strokePaint.setAlpha(alpha);
        fillPaint.setAlpha(alpha);
        shadowStroke.setAlpha(alpha * 0x4D / 255);
        shadowFill.setAlpha(alpha * 0x4D / 255);
        invalidateSelf();
    }

    @Override public void setColorFilter(ColorFilter filter) {
        strokePaint.setColorFilter(filter);
        fillPaint.setColorFilter(filter);
        invalidateSelf();
    }

    @Override public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    // ---- Formen ------------------------------------------------------------------------------

    private void build() {
        outlines.clear();
        fills.clear();
        switch (kind) {
            case SUN:
                line(sun(24, 24, 8.5f, 13.5f, 18.5f, 0));
                break;
            case MOON:
                line(crescent(24, 25.5f, 14, 33, 19.5f, 11.5f));
                line(cross(35.5f, 13.5f, 2.6f));
                break;
            case PARTLY_SUN:
                cloudInFront(new Cloud(9, 10.4f, 0.85f), sun(17, 17, 6.5f, 10.5f, 14.5f, 0b110));
                break;
            case PARTLY_MOON:
                cloudInFront(new Cloud(9, 10.4f, 0.85f), crescent(17, 17.5f, 10.5f, 24, 12.5f, 8.7f));
                break;
            case OVERCAST:
                cloudInFront(new Cloud(-1.65f, 6.8f, 0.95f), new Cloud(14.3f, -4.1f, 0.7f).outline());
                break;
            case FOG:
                line(new Cloud(0, -6, 1).outline());
                line(segments(9, 36, 39, 36, 15, 42, 41, 42));
                break;
            case RAIN:
                line(new Cloud(0, -6, 1).outline());
                line(segments(17.3f, 35.5f, 14.3f, 43, 25.8f, 35.5f, 22.8f, 43, 34.3f, 35.5f, 31.3f, 43));
                break;
            case SNOW:
                line(new Cloud(0, -6, 1).outline());
                fillDots(15, 37, 24, 37, 33, 37, 19.5f, 43, 28.5f, 43);
                break;
            case STORM:
                line(new Cloud(0, -6, 1).outline());
                Path bolt = polygon(26.5f, 33, 19.5f, 41.5f, 24.5f, 41.5f, 22, 46.5f, 29, 38, 24, 38);
                line(bolt);
                fills.add(toPixels(bolt));   // gefüllt wirkt der kleine Blitz kräftiger
                break;
            default:
                break;
        }
    }

    /** Die Wolke vorn als Linie; {@code behind} (Raster-Pfad) wird dahinter gezeichnet und an der Wolke zugeschnitten. */
    private void cloudInFront(Cloud front, Path behind) {
        Path cutter = toPixels(front.filled(GAP));
        Path behindPixels = toPixels(behind);
        Path ring = new Path();
        outlineMaker.getFillPath(behindPixels, ring);
        ring.op(cutter, Path.Op.DIFFERENCE);
        fills.add(ring);
        line(front.outline());
    }

    private void line(Path gridPath) {
        outlines.add(toPixels(gridPath));
    }

    private Path toPixels(Path gridPath) {
        Path p = new Path(gridPath);
        p.transform(toPixels);
        return p;
    }

    /** {@code skippedRays}: Bitmaske der Strahlen, die entfallen (Bit i = Winkel i * 45 Grad, im Uhrzeigersinn ab Osten). */
    private static Path sun(float cx, float cy, float radius, float rayFrom, float rayTo, int skippedRays) {
        Path p = new Path();
        p.addCircle(cx, cy, radius, Path.Direction.CW);
        for (int i = 0; i < 8; i++) {
            if ((skippedRays >> i & 1) != 0) continue;
            double angle = Math.toRadians(i * 45);
            float cos = (float) Math.cos(angle), sin = (float) Math.sin(angle);
            p.moveTo(cx + cos * rayFrom, cy + sin * rayFrom);
            p.lineTo(cx + cos * rayTo, cy + sin * rayTo);
        }
        return p;
    }

    /** Mondsichel: großer Kreis minus ein versetzter kleiner Kreis. */
    private static Path crescent(float cx, float cy, float radius, float cutX, float cutY, float cutRadius) {
        Path moon = new Path();
        moon.addCircle(cx, cy, radius, Path.Direction.CW);
        Path cut = new Path();
        cut.addCircle(cutX, cutY, cutRadius, Path.Direction.CW);
        moon.op(cut, Path.Op.DIFFERENCE);
        return moon;
    }

    private static Path cross(float cx, float cy, float arm) {
        return segments(cx - arm, cy, cx + arm, cy, cx, cy - arm, cx, cy + arm);
    }

    /** Einzelne Striche; Koordinaten paarweise (x1, y1, x2, y2, ...). */
    private static Path segments(float... c) {
        Path p = new Path();
        for (int i = 0; i + 3 < c.length; i += 4) {
            p.moveTo(c[i], c[i + 1]);
            p.lineTo(c[i + 2], c[i + 3]);
        }
        return p;
    }

    /** Gefüllte Punkte; Koordinaten paarweise (x, y, ...). */
    private void fillDots(float... c) {
        Path p = new Path();
        for (int i = 0; i + 1 < c.length; i += 2) p.addCircle(c[i], c[i + 1], STROKE * 0.62f, Path.Direction.CW);
        fills.add(toPixels(p));
    }

    private static Path polygon(float... c) {
        Path p = new Path();
        p.moveTo(c[0], c[1]);
        for (int i = 2; i + 1 < c.length; i += 2) p.lineTo(c[i], c[i + 1]);
        p.close();
        return p;
    }

    /** Eine Wolke aus drei Kreisen und einer Grundfläche, verschoben und skaliert auf dem Raster. */
    private static final class Cloud {
        private final float offsetX, offsetY, scale;

        Cloud(float offsetX, float offsetY, float scale) {
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.scale = scale;
        }

        Path outline() {
            return filled(0);
        }

        /** Die Fläche der Wolke, rundum um {@code grow} Einheiten vergrößert. */
        Path filled(float grow) {
            Path union = new Path();
            union.addCircle(x(14), y(29), scale * 7 + grow, Path.Direction.CW);
            union(union, x(24), y(23), scale * 10 + grow);
            union(union, x(34), y(29), scale * 7 + grow);
            Path base = new Path();
            base.addRect(x(14), y(26), x(34), y(36) + grow, Path.Direction.CW);
            union.op(base, Path.Op.UNION);
            return union;
        }

        private void union(Path into, float cx, float cy, float radius) {
            Path circle = new Path();
            circle.addCircle(cx, cy, radius, Path.Direction.CW);
            into.op(circle, Path.Op.UNION);
        }

        private float x(float v) {
            return offsetX + scale * v;
        }

        private float y(float v) {
            return offsetY + scale * v;
        }
    }
}
