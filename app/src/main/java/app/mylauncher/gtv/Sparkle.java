package app.mylauncher.gtv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;

/**
 * A few small stars in the sky of the picture that twinkle slowly (and, in the pink look, drift up and down a little).
 * It draws about ten tiny shapes ten times a second, only while the home screen is in front, so it costs next to nothing.
 */
final class Sparkle extends View {
    private final boolean kids;
    private final float[][] stars;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean running;
    private final Runnable frame = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            invalidate();
            handler.postDelayed(this, 100L);
        }
    };

    /** x, y (in the 1365 x 768 canvas) and size of each star, placed in the empty sky of each picture. */
    private static final float[][] ROYAL = {{322, 28, 5}, {366, 62, 4}, {410, 22, 5}, {470, 14, 4}, {515, 48, 4},
        {700, 16, 4}, {772, 28, 5}, {830, 70, 4}, {918, 30, 4}};
    private static final float[][] KIDS = {{300, 20, 5}, {330, 60, 6}, {425, 12, 6}, {520, 10, 5}, {690, 10, 5},
        {785, 22, 6}, {860, 12, 5}, {945, 62, 5}};

    Sparkle(Context c, boolean kids) {
        super(c);
        this.kids = kids;
        stars = kids ? KIDS : ROYAL;
        setFocusable(false);
        setClickable(false);
        paint.setStyle(Paint.Style.FILL);
    }

    void start() {
        if (running) return;
        running = true;
        handler.post(frame);
    }

    void stop() {
        running = false;
        handler.removeCallbacks(frame);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float t = SystemClock.uptimeMillis() / 1000f;
        for (int i = 0; i < stars.length; i++) {
            float[] s = stars[i];
            float phase = t * (0.7f + 0.13f * (i % 5)) + i * 1.7f;
            float twinkle = 0.5f + 0.5f * (float) Math.sin(phase);
            float alpha = 0.25f + 0.75f * twinkle;
            float r = s[2] * (0.75f + 0.5f * twinkle);
            float dy = kids ? 3f * (float) Math.sin(phase * 0.6f) : 0f;
            paint.setColor(kids ? 0xFFFFFFFF : 0xFFFFE9A8);
            paint.setAlpha(Math.round(255 * alpha));
            float x = s[0], y = s[1] + dy;
            path.reset();
            path.moveTo(x, y - r);
            path.lineTo(x + r * 0.25f, y - r * 0.25f);
            path.lineTo(x + r, y);
            path.lineTo(x + r * 0.25f, y + r * 0.25f);
            path.lineTo(x, y + r);
            path.lineTo(x - r * 0.25f, y + r * 0.25f);
            path.lineTo(x - r, y);
            path.lineTo(x - r * 0.25f, y - r * 0.25f);
            path.close();
            canvas.drawPath(path, paint);
        }
    }
}
