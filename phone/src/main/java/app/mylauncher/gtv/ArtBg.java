package app.mylauncher.gtv;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/**
 * The home page background: the picture (mosque and lanterns on top, ornaments in the bottom corners) cut in three
 * horizontal bands. The top and bottom bands keep their shape; the plain middle band is stretched to the screen height.
 */
final class ArtBg extends Drawable {
    /** Where the bands start, as a share of the picture's height. */
    static final float TOP_F = 1440f / 3764f;
    static final float BOT_F = 3180f / 3764f;
    /** Where the plain panel starts (below the curve) and how much of the bottom edge the corners take. */
    static final float PANEL_F = 1130f / 3764f;
    static final float CORNER_F = 1f - 3180f / 3764f;

    private final Bitmap bmp;
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Rect src = new Rect();
    private final Rect dst = new Rect();

    ArtBg(Bitmap bmp) {
        this.bmp = bmp;
    }

    /** Pixels from the top of a screen {@code width} wide to where content can start. */
    static int panelTop(Bitmap b, int width) {
        return Math.round(b.getHeight() * PANEL_F * width / b.getWidth());
    }

    static int cornerHeight(Bitmap b, int width) {
        return Math.round(b.getHeight() * CORNER_F * width / b.getWidth());
    }

    @Override
    public void draw(Canvas c) {
        Rect b = getBounds();
        int w = b.width(), h = b.height();
        if (w <= 0 || h <= 0) return;
        float s = (float) w / bmp.getWidth();
        int bh = bmp.getHeight();
        int topSrc = Math.round(bh * TOP_F), botSrc = Math.round(bh * BOT_F);
        int topH = Math.round(topSrc * s), botH = Math.round((bh - botSrc) * s);
        if (topH + botH > h) {
            // a very short window: just fit the whole picture
            src.set(0, 0, bmp.getWidth(), bh);
            dst.set(b.left, b.top, b.right, b.bottom);
            c.drawBitmap(bmp, src, dst, paint);
            return;
        }
        src.set(0, 0, bmp.getWidth(), topSrc);
        dst.set(b.left, b.top, b.right, b.top + topH);
        c.drawBitmap(bmp, src, dst, paint);
        src.set(0, topSrc, bmp.getWidth(), botSrc);
        dst.set(b.left, b.top + topH, b.right, b.bottom - botH);
        c.drawBitmap(bmp, src, dst, paint);
        src.set(0, botSrc, bmp.getWidth(), bh);
        dst.set(b.left, b.bottom - botH, b.right, b.bottom);
        c.drawBitmap(bmp, src, dst, paint);
    }

    @Override
    public void setAlpha(int a) {
        paint.setAlpha(a);
    }

    @Override
    public void setColorFilter(ColorFilter f) {
        paint.setColorFilter(f);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.OPAQUE;
    }

    /**
     * The home page in landscape: the green of the picture, the gold line frame, and the top of the picture (mosque and
     * lanterns) in the middle at its true shape, fading into the green below it.
     */
    static final class Land extends Drawable {
        private final Bitmap bmp;
        private final int bannerH;
        private final int base;
        private final Paint bgPaint = new Paint();
        private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        private final Paint fade = new Paint();
        private final Rect src = new Rect();
        private final Rect dst = new Rect();
        private final Border border;

        Land(Bitmap bmp, float density, int bannerH, int gold) {
            this.bmp = bmp;
            this.bannerH = bannerH;
            int c = 0xFF0F5A1E;
            try {
                c = bmp.getPixel(bmp.getWidth() / 2, bmp.getHeight() * 2 / 3);
            } catch (RuntimeException ignored) {
                // keep the fallback green
            }
            base = c | 0xFF000000;
            border = new Border((gold & 0x00FFFFFF) | 0x99000000, density);
            fade.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN));
        }

        private static int darker(int c, float k) {
            return 0xFF000000 | (Math.round(((c >> 16) & 0xFF) * k) << 16) | (Math.round(((c >> 8) & 0xFF) * k) << 8) | Math.round((c & 0xFF) * k);
        }

        @Override
        public void draw(Canvas c) {
            Rect b = getBounds();
            if (b.width() <= 0 || b.height() <= 0) return;
            bgPaint.setShader(new android.graphics.LinearGradient(0, b.top, 0, b.bottom, base, darker(base, 0.7f), android.graphics.Shader.TileMode.CLAMP));
            c.drawRect(b, bgPaint);
            border.setBounds(b);
            border.draw(c);
            int topSrc = Math.round(bmp.getHeight() * TOP_F);
            int dw = Math.round(bannerH * (float) bmp.getWidth() / topSrc);
            int left = b.left + (b.width() - dw) / 2;
            int save = c.saveLayer(left, b.top, left + dw, b.top + bannerH, null);
            src.set(0, 0, bmp.getWidth(), topSrc);
            dst.set(left, b.top, left + dw, b.top + bannerH);
            c.drawBitmap(bmp, src, dst, paint);
            fade.setShader(new android.graphics.LinearGradient(0, b.top + bannerH * 0.7f, 0, b.top + bannerH,
                0xFF000000, 0x00000000, android.graphics.Shader.TileMode.CLAMP));
            c.drawRect(left, b.top, left + dw, b.top + bannerH, fade);
            fade.setShader(new android.graphics.LinearGradient(left, 0, left + dw, 0,
                new int[] {0x00000000, 0xFF000000, 0xFF000000, 0x00000000}, new float[] {0f, 0.16f, 0.84f, 1f}, android.graphics.Shader.TileMode.CLAMP));
            c.drawRect(left, b.top, left + dw, b.top + bannerH, fade);
            c.restoreToCount(save);
        }

        @Override
        public void setAlpha(int a) {
            paint.setAlpha(a);
        }

        @Override
        public void setColorFilter(ColorFilter f) {
            paint.setColorFilter(f);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.OPAQUE;
        }
    }

    /** The right panel of the landscape home: the mosque picture across the top, green below, framed in gold. */
    static final class Panel extends Drawable {
        private final Bitmap bmp;
        private final float d;
        private final int base;
        private final int line;
        private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Rect src = new Rect();
        private final Rect dst = new Rect();

        Panel(Bitmap bmp, float density, int gold) {
            this.bmp = bmp;
            this.d = density;
            int c = 0xFF0F5A1E, l = gold;
            try {
                c = bmp.getPixel(bmp.getWidth() / 2, bmp.getHeight() * 2 / 3);
                l = bmp.getPixel(Math.max(2, bmp.getWidth() / 140), bmp.getHeight() / 2);
            } catch (RuntimeException ignored) {
                // keep the fallbacks
            }
            base = c | 0xFF000000;
            line = l | 0xFF000000;
            stroke.setStyle(Paint.Style.STROKE);
        }

        @Override
        public void draw(Canvas c) {
            Rect b = getBounds();
            if (b.width() <= 0 || b.height() <= 0) return;
            float w = b.width();
            int topSrc = Math.round(bmp.getHeight() * TOP_F);
            float bandH = topSrc * (w / bmp.getWidth());
            float r = 18 * d;
            // the green, with rounded corners at the bottom
            fill.setColor(base);
            android.graphics.Path body = new android.graphics.Path();
            body.moveTo(b.left, b.top);
            body.lineTo(b.right, b.top);
            body.lineTo(b.right, b.bottom - r);
            body.quadTo(b.right, b.bottom, b.right - r, b.bottom);
            body.lineTo(b.left + r, b.bottom);
            body.quadTo(b.left, b.bottom, b.left, b.bottom - r);
            body.close();
            c.drawPath(body, fill);
            // the top of the picture, as wide as the panel
            src.set(0, 0, bmp.getWidth(), topSrc);
            dst.set(b.left, b.top, b.right, Math.round(b.top + bandH));
            c.drawBitmap(bmp, src, dst, paint);
            // the gold frame goes on down both sides and round the bottom
            float x0 = b.left + 2.5f * d, x1 = b.right - 2.5f * d, y0 = b.top + bandH * 0.86f, y1 = b.bottom - 2.5f * d;
            for (int i = 0; i < 2; i++) {
                float in = i * 5f * d;
                stroke.setColor(i == 0 ? line : (line & 0x00FFFFFF) | 0x66000000);
                stroke.setStrokeWidth(i == 0 ? 3f * d : 1f * d);
                android.graphics.Path f = new android.graphics.Path();
                f.moveTo(x0 + in, y0);
                f.lineTo(x0 + in, y1 - r + in);
                f.quadTo(x0 + in, y1 - in, x0 + in + r, y1 - in);
                f.lineTo(x1 - in - r, y1 - in);
                f.quadTo(x1 - in, y1 - in, x1 - in, y1 - r + in);
                f.lineTo(x1 - in, y0);
                c.drawPath(f, stroke);
            }
        }

        @Override
        public void setAlpha(int a) {
            paint.setAlpha(a);
        }

        @Override
        public void setColorFilter(ColorFilter f) {
            paint.setColorFilter(f);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.OPAQUE;
        }
    }

    /** A gold line frame with rounded corners, for the pages that are not the home page. */
    static final class Border extends Drawable {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int color;
        private final float density;

        Border(int color, float density) {
            this.color = color;
            this.density = density;
            p.setStyle(Paint.Style.STROKE);
        }

        @Override
        public void draw(Canvas c) {
            Rect b = getBounds();
            float d = density;
            p.setColor(color);
            p.setStrokeWidth(2.5f * d);
            c.drawRoundRect(new RectF(b.left + 5 * d, b.top + 5 * d, b.right - 5 * d, b.bottom - 5 * d), 24 * d, 24 * d, p);
            p.setColor((color & 0x00FFFFFF) | 0x44000000);
            p.setStrokeWidth(1f * d);
            c.drawRoundRect(new RectF(b.left + 11 * d, b.top + 11 * d, b.right - 11 * d, b.bottom - 11 * d), 20 * d, 20 * d, p);
        }

        @Override
        public void setAlpha(int a) {
            p.setAlpha(a);
        }

        @Override
        public void setColorFilter(ColorFilter f) {
            p.setColorFilter(f);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
