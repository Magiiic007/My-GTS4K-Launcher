package app.mylauncher.gtv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

/** A tiling of eight-pointed stars (the classic Islamic geometric motif), drawn thin and faint behind everything. */
final class IslamicPattern extends Drawable {
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path star = new Path();
    private final float cell;

    IslamicPattern(Context c, int argb) {
        float d = c.getResources().getDisplayMetrics().density;
        cell = 64f * d;
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(Math.max(1f, 1.2f * d));
        line.setColor(argb);
        // One star, centred on (0,0): sixteen points alternating between the outer and inner radius.
        float outer = cell * 0.46f;
        float inner = cell * 0.30f;
        for (int i = 0; i < 16; i++) {
            double a = Math.PI * i / 8.0 - Math.PI / 2.0;
            float r = i % 2 == 0 ? outer : inner;
            float x = (float) Math.cos(a) * r;
            float y = (float) Math.sin(a) * r;
            if (i == 0) star.moveTo(x, y);
            else star.lineTo(x, y);
        }
        star.close();
    }

    @Override
    public void draw(Canvas canvas) {
        int w = getBounds().width();
        int h = getBounds().height();
        for (float y = cell / 2f; y < h + cell; y += cell) {
            for (float x = cell / 2f; x < w + cell; x += cell) {
                canvas.save();
                canvas.translate(getBounds().left + x, getBounds().top + y);
                canvas.drawPath(star, line);
                canvas.restore();
                // A small diamond in the gap between four stars joins the pattern up.
                float cx = getBounds().left + x + cell / 2f;
                float cy = getBounds().top + y + cell / 2f;
                float s = cell * 0.09f;
                canvas.drawLine(cx - s, cy, cx, cy - s, line);
                canvas.drawLine(cx, cy - s, cx + s, cy, line);
                canvas.drawLine(cx + s, cy, cx, cy + s, line);
                canvas.drawLine(cx, cy + s, cx - s, cy, line);
            }
        }
    }

    @Override
    public void setAlpha(int alpha) {
        line.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter filter) {
        line.setColorFilter(filter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
