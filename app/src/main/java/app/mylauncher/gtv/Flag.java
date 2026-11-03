package app.mylauncher.gtv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.text.TextPaint;
import android.view.View;

/** A small flag drawn in code: Egypt's or Saudi Arabia's. */
final class Flag extends View {
    private boolean egypt;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG);

    Flag(Context c) {
        super(c);
    }

    void setEgypt(boolean egypt) {
        this.egypt = egypt;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth(), h = getHeight();
        Path clip = new Path();
        clip.addRoundRect(new RectF(0, 0, w, h), 6, 6, Path.Direction.CW);
        canvas.save();
        canvas.clipPath(clip);
        if (egypt) {
            paint.setColor(0xFFCE1126);
            canvas.drawRect(0, 0, w, h / 3f, paint);
            paint.setColor(0xFFFFFFFF);
            canvas.drawRect(0, h / 3f, w, 2f * h / 3f, paint);
            paint.setColor(0xFF000000);
            canvas.drawRect(0, 2f * h / 3f, w, h, paint);
            paint.setColor(0xFFC09300);
            canvas.drawCircle(w / 2f, h / 2f, h * 0.13f, paint);
        } else {
            paint.setColor(0xFF006C35);
            canvas.drawRect(0, 0, w, h, paint);
            text.setColor(0xFFFFFFFF);
            text.setFakeBoldText(true);
            text.setTextAlign(Paint.Align.CENTER);
            text.setTextSize(h * 0.26f);
            canvas.drawText("لا إله إلا الله", w / 2f, h * 0.45f, text);
            text.setTextSize(h * 0.2f);
            canvas.drawText("محمد رسول الله", w / 2f, h * 0.64f, text);
            paint.setColor(0xFFFFFFFF);
            paint.setStrokeWidth(Math.max(2f, h * 0.05f));
            canvas.drawLine(w * 0.2f, h * 0.76f, w * 0.8f, h * 0.76f, paint);
        }
        canvas.restore();
    }
}
