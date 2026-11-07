package app.mylauncher.gtv;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Paint;

/** A soft drop shadow in the exact shape of the icon (its own outline, blurred and pushed down a little). */
final class IconShadow {
    private IconShadow() {}

    /** The returned picture is {@code size + 2 * pad} square; the icon itself sits in its middle. */
    static Bitmap build(Resources res, int drawable, int size, int pad, int blur, int dy, int alpha) {
        Bitmap raw = BitmapFactory.decodeResource(res, drawable);
        Bitmap src = Bitmap.createScaledBitmap(raw, size, size, true);
        Paint bp = new Paint();
        bp.setMaskFilter(new BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL));
        int[] off = new int[2];
        Bitmap mask = src.extractAlpha(bp, off);
        int w = size + 2 * pad;
        Bitmap out = Bitmap.createBitmap(w, w, Bitmap.Config.ARGB_8888);
        Paint sp = new Paint(Paint.ANTI_ALIAS_FLAG);
        sp.setColor(0xFF000000);
        sp.setAlpha(alpha);
        new Canvas(out).drawBitmap(mask, pad + off[0], pad + off[1] + dy, sp);
        return out;
    }
}
