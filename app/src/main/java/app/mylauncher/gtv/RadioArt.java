package app.mylauncher.gtv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import java.util.HashMap;
import java.util.Map;

/** The picture of each station or reciter (by its number in RadioService.STATIONS), cut as a disc. */
final class RadioArt {
    private RadioArt() {}

    private static final Map<String, Bitmap> CACHE = new HashMap<>();

    /** The photo as it is, square (null if there is none for this number). */
    static Bitmap photo(Context c, int idx, int maxSize) {
        int id = c.getResources().getIdentifier("art_" + idx, "drawable", c.getPackageName());
        if (id == 0) return null;
        BitmapFactory.Options o = new BitmapFactory.Options();
        int scale = 1;
        while (512 / (scale * 2) >= maxSize) scale *= 2;
        o.inSampleSize = scale;
        return BitmapFactory.decodeResource(c.getResources(), id, o);
    }

    /** The photo cut round with a thin gold rim and a small hole in the middle, like a record. */
    static Bitmap disc(Context c, int idx, int size) {
        String key = idx + ":" + size;
        Bitmap hit = CACHE.get(key);
        if (hit != null) return hit;
        Bitmap p = photo(c, idx, size);
        if (p == null) return null;
        Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(out);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        float r = size / 2f;
        Path clip = new Path();
        clip.addCircle(r, r, r * 0.94f, Path.Direction.CW);
        cv.save();
        cv.clipPath(clip);
        cv.drawBitmap(Bitmap.createScaledBitmap(p, size, size, true), 0, 0, paint);
        cv.restore();
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(0xFFE0B960);
        paint.setStrokeWidth(Math.max(2f, size * 0.03f));
        cv.drawCircle(r, r, r * 0.94f, paint);
        paint.setColor(0x66000000);
        paint.setStrokeWidth(Math.max(1f, size * 0.012f));
        cv.drawCircle(r, r, r * 0.80f, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF1A1A1A);
        cv.drawCircle(r, r, size * 0.045f, paint);
        paint.setColor(0xFFE0B960);
        cv.drawCircle(r, r, size * 0.02f, paint);
        CACHE.put(key, out);
        return out;
    }

    static Drawable discDrawable(Context c, int idx, int size) {
        Bitmap b = disc(c, idx, size);
        return b == null ? null : new BitmapDrawable(c.getResources(), b);
    }
}
