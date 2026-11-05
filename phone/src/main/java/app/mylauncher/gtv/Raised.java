package app.mylauncher.gtv;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/**
 * A card that looks raised from the page: a soft shadow under it (drawn only outside the card, so the translucent
 * card is not darkened), a fill that is a little lighter at the top, a thin line and a bright edge along the top.
 */
final class Raised extends Drawable {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float den, rad;
    private final int stroke, fill;
    private final Path path = new Path();
    private final RectF rf = new RectF();

    Raised(float density, int radiusDp, int stroke, int fill) {
        this.fill = fill;
        this.den = density;
        this.rad = radiusDp * density;
        this.stroke = stroke;
    }

    @Override
    public void draw(Canvas c) {
        Rect b = getBounds();
        rf.set(b.left + 2f * den, b.top + 1.5f * den, b.right - 2f * den, b.bottom - 4.5f * den);
        if (rf.width() <= 0 || rf.height() <= 0) return;
        path.reset();
        path.addRoundRect(rf, rad, rad, Path.Direction.CW);

        // the shadow: a few soft layers, spreading and fading, clipped away from the inside of the card
        c.save();
        c.clipOutPath(path);
        p.setStyle(Paint.Style.FILL);
        RectF s = new RectF();
        for (int i = 0; i < 6; i++) {
            float grow = (0.5f + i * 0.7f) * den;
            s.set(rf.left - grow, rf.top - grow + 2.2f * den, rf.right + grow, rf.bottom + grow + 2.2f * den);
            p.setColor(((0x16 - i * 2) << 24));
            c.drawRoundRect(s, rad + grow, rad + grow, p);
        }
        c.restore();

        // the face: lighter at the top, a little darker at the bottom
        p.setShader(new LinearGradient(0f, rf.top, 0f, rf.bottom, lighter(fill), fill, Shader.TileMode.CLAMP));
        c.drawPath(path, p);
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(den);
        p.setColor(stroke);
        c.drawPath(path, p);
        // the bright edge along the top
        p.setColor(0x40FFFFFF);
        p.setStrokeWidth(den);
        c.drawLine(rf.left + rad * 0.8f, rf.top + 1.2f * den, rf.right - rad * 0.8f, rf.top + 1.2f * den, p);
    }

    /** The same colour mixed a little with white (keeping its transparency). */
    private static int lighter(int c) {
        int a = c >>> 24;
        int r = ((c >> 16) & 255) + (255 - ((c >> 16) & 255)) * 18 / 100;
        int g = ((c >> 8) & 255) + (255 - ((c >> 8) & 255)) * 18 / 100;
        int b = (c & 255) + (255 - (c & 255)) * 18 / 100;
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    @Override
    public void setAlpha(int alpha) {
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
