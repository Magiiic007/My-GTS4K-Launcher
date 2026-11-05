package app.mylauncher.gtv;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/**
 * The side menu's background: the app's top-to-bottom gradient, fully opaque at the edge it is attached to and
 * fading to {@code minAlpha} (0.2 = 80% see-through) at the inner edge. A thin gold line follows the same fade.
 */
final class FadePanel extends Drawable {
    private final int top;
    private final int bottom;
    private final int line;
    private final float minAlpha;
    private final boolean opaqueRight;
    private final float stroke;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);

    FadePanel(int top, int bottom, int line, float minAlpha, boolean opaqueRight, float strokePx) {
        this.top = top | 0xFF000000;
        this.bottom = bottom | 0xFF000000;
        this.line = line | 0xFF000000;
        this.minAlpha = minAlpha;
        this.opaqueRight = opaqueRight;
        this.stroke = strokePx;
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(strokePx);
    }

    private Shader fade(Rect b, int colorOpaque) {
        int lo = ((int) (minAlpha * 255f) << 24) | (colorOpaque & 0xFFFFFF);
        int hi = colorOpaque | 0xFF000000;
        float x0 = opaqueRight ? b.left : b.right;
        float x1 = opaqueRight ? b.right : b.left;
        return new LinearGradient(x0, 0, x1, 0, lo, hi, Shader.TileMode.CLAMP);
    }

    @Override
    protected void onBoundsChange(Rect b) {
        Shader vertical = new LinearGradient(0, b.top, 0, b.bottom, top, bottom, Shader.TileMode.CLAMP);
        // the horizontal part carries only the alpha: black that fades in; DST_IN keeps the colour of the vertical part
        fill.setColor(0xFF000000); // the paint itself stays opaque, so the shader alone sets the alpha
        fill.setShader(new android.graphics.ComposeShader(vertical, fade(b, 0x000000), PorterDuff.Mode.DST_IN));
        edge.setColor(0xFF000000);
        edge.setShader(fade(b, line));
    }

    @Override
    public void draw(Canvas canvas) {
        Rect b = getBounds();
        canvas.drawRect(b, fill);
        float h = stroke / 2f;
        canvas.drawRect(new RectF(b.left + h, b.top + h, b.right - h, b.bottom - h), edge);
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
