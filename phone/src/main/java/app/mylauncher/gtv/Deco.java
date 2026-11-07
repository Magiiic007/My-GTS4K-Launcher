package app.mylauncher.gtv;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/** The ornaments of the two looks, drawn with paths so they stay sharp on any screen. */
final class Deco {
    private Deco() {}

    /** An eight-pointed star inside a 56 x 56 box (the same star as the TV and the preview page). */
    private static Path star() {
        float[][] p = {{28, 4}, {33, 15}, {45, 11}, {41, 23}, {52, 28}, {41, 33}, {45, 45}, {33, 41}, {28, 52},
            {23, 41}, {11, 45}, {15, 33}, {4, 28}, {15, 23}, {11, 11}, {23, 15}};
        Path s = new Path();
        s.moveTo(p[0][0], p[0][1]);
        for (int i = 1; i < p.length; i++) s.lineTo(p[i][0], p[i][1]);
        s.close();
        return s;
    }

    /** One cell of the geometric pattern (100 x 100): a twelve-point star in a ring, and small diamonds at the corners. */
    private static Path latticeCell() {
        Path p = new Path();
        float cx = 50f, cy = 50f;
        // the twelve-point star
        for (int k = 0; k < 24; k++) {
            double a = Math.toRadians(k * 15 - 90);
            float r = k % 2 == 0 ? 31f : 21f;
            float x = cx + r * (float) Math.cos(a), y = cy + r * (float) Math.sin(a);
            if (k == 0) p.moveTo(x, y);
            else p.lineTo(x, y);
        }
        p.close();
        p.addCircle(cx, cy, 10f, Path.Direction.CW);
        p.addCircle(cx, cy, 47f, Path.Direction.CW);
        for (int k = 0; k < 12; k++) {
            double a = Math.toRadians(k * 30 - 90);
            float vx = cx + 31f * (float) Math.cos(a), vy = cy + 31f * (float) Math.sin(a);
            for (int d = -1; d <= 1; d += 2) {
                double b = a + Math.toRadians(15 * d);
                p.moveTo(vx, vy);
                p.lineTo(cx + 47f * (float) Math.cos(b), cy + 47f * (float) Math.sin(b));
            }
            double a2 = a + Math.toRadians(15);
            p.moveTo(cx + 21f * (float) Math.cos(a2), cy + 21f * (float) Math.sin(a2));
            p.lineTo(cx + 10f * (float) Math.cos(a2), cy + 10f * (float) Math.sin(a2));
        }
        float[][] corners = {{0, 0}, {100, 0}, {0, 100}, {100, 100}};
        for (float[] c : corners) {
            p.moveTo(c[0], c[1] - 7f);
            p.lineTo(c[0] + 7f, c[1]);
            p.lineTo(c[0], c[1] + 7f);
            p.lineTo(c[0] - 7f, c[1]);
            p.close();
        }
        // a thin line from each rosette to the diamond at the nearest corner
        for (int k = 0; k < 4; k++) {
            double a = Math.toRadians(45 + 90 * k);
            float tx = Math.cos(a) > 0 ? 100f : 0f, ty = Math.sin(a) > 0 ? 100f : 0f;
            p.moveTo(cx + 47f * (float) Math.cos(a), cy + 47f * (float) Math.sin(a));
            p.lineTo(tx + (tx > 0 ? -5f : 5f), ty + (ty > 0 ? -5f : 5f));
        }
        return p;
    }

    /** A faint geometric pattern over the whole screen (twelve-point star rosettes), drawn with paths so it stays sharp. */
    static final class Lattice extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path cell = latticeCell();
        private final float tile;

        Lattice(int color, float tilePx) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(color);
            paint.setStrokeJoin(Paint.Join.ROUND);
            tile = tilePx;
            // one pixel-ish line whatever the tile size (the path is scaled by tile / 100)
            paint.setStrokeWidth(Math.max(1f, tilePx / 130f) * 100f / tilePx);
        }

        @Override
        public void draw(Canvas c) {
            Rect b = getBounds();
            float k = tile / 100f;
            for (float y = b.top; y < b.bottom; y += tile) {
                for (float x = b.left; x < b.right; x += tile) {
                    int n = c.save();
                    c.translate(x, y);
                    c.scale(k, k);
                    c.drawPath(cell, paint);
                    c.restoreToCount(n);
                }
            }
        }

        @Override
        public void setAlpha(int a) {
        }

        @Override
        public void setColorFilter(ColorFilter f) {
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    /**
     * Drawn behind the next-prayer card. Mihrab: two nested arches. Royal: a dotted frame with a star on each
     * corner and one at the top.
     */
    static final class Frame extends Drawable {
        private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final boolean arch;
        private final float dp;
        private final Path star = star();

        Frame(boolean arch, int color, float density) {
            this.arch = arch;
            this.dp = density;
            line.setStyle(Paint.Style.STROKE);
            line.setColor(color);
            fill.setStyle(Paint.Style.FILL);
            fill.setColor(color);
        }

        @Override
        public void draw(Canvas c) {
            Rect b = getBounds();
            float w = b.width(), h = b.height();
            if (arch) {
                // The preview page's arches, scaled to this card (viewBox 380 x 230).
                float sx = w / 380f, sy = h / 230f;
                line.setPathEffect(null);
                line.setStrokeWidth(2f * dp);
                c.drawPath(archPath(60, 320, 120, 6, sx, sy, b), line);
                line.setStrokeWidth(1f * dp);
                int a = line.getAlpha();
                line.setAlpha(a * 6 / 10);
                c.drawPath(archPath(82, 298, 124, 28, sx, sy, b), line);
                line.setAlpha(a);
            } else {
                float in = 8f * dp;
                line.setStrokeWidth(1.4f * dp);
                line.setPathEffect(new DashPathEffect(new float[] {2f * dp, 6f * dp}, 0));
                c.drawRoundRect(new RectF(b.left + in, b.top + in, b.right - in, b.bottom - in), 16 * dp, 16 * dp, line);
                line.setPathEffect(null);
                float s = 14f * dp / 56f;
                float[][] at = {{in, in}, {w - in, in}, {in, h - in}, {w - in, h - in}, {w / 2f, in}};
                for (float[] p : at) {
                    int n = c.save();
                    c.translate(b.left + p[0] - 28f * s, b.top + p[1] - 28f * s);
                    c.scale(s, s);
                    c.drawPath(star, fill);
                    c.restoreToCount(n);
                }
            }
        }

        private Path archPath(float l, float r, float side, float topY, float sx, float sy, Rect b) {
            float mid = 190f;
            Path p = new Path();
            p.moveTo(b.left + l * sx, b.top + 230f * sy);
            p.lineTo(b.left + l * sx, b.top + side * sy);
            p.cubicTo(b.left + l * sx, b.top + (side - 60f) * sy, b.left + (mid - 70f) * sx, b.top + (topY + 14f) * sy,
                b.left + mid * sx, b.top + topY * sy);
            p.cubicTo(b.left + (mid + 70f) * sx, b.top + (topY + 14f) * sy, b.left + r * sx, b.top + (side - 60f) * sy,
                b.left + r * sx, b.top + side * sy);
            p.lineTo(b.left + r * sx, b.top + 230f * sy);
            return p;
        }

        @Override
        public void setAlpha(int a) {
        }

        @Override
        public void setColorFilter(ColorFilter f) {
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
