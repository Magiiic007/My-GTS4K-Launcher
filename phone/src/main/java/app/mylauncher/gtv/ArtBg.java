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
