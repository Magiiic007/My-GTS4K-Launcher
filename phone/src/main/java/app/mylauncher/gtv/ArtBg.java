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
