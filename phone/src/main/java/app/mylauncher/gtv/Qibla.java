package app.mylauncher.gtv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

import java.util.Locale;

/** The qibla: its bearing from a city, and a compass dial that turns with the phone. */
final class Qibla {
    private Qibla() {}

    static final double KAABA_LAT = 21.4225, KAABA_LON = 39.8262;

    /** Degrees clockwise from true north to the Kaaba. */
    static double bearing(double lat, double lon) {
        double p1 = Math.toRadians(lat), p2 = Math.toRadians(KAABA_LAT), dl = Math.toRadians(KAABA_LON - lon);
        double y = Math.sin(dl) * Math.cos(p2);
        double x = Math.cos(p1) * Math.sin(p2) - Math.sin(p1) * Math.cos(p2) * Math.cos(dl);
        return (Math.toDegrees(Math.atan2(y, x)) + 360.0) % 360.0;
    }

    static double distanceKm(double lat, double lon) {
        double r = 6371.0, p1 = Math.toRadians(lat), p2 = Math.toRadians(KAABA_LAT);
        double dp = p2 - p1, dl = Math.toRadians(KAABA_LON - lon);
        double a = Math.sin(dp / 2) * Math.sin(dp / 2) + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2);
        return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    /** The dial: north marks turn opposite to the phone's heading; the gold arrow at the top is where you face. */
    static final class Dial extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int gold, cream, dim;
        private float heading;
        private float target;
        private boolean live;

        Dial(Context c, int gold, int cream, int dim) {
            super(c);
            this.gold = gold;
            this.cream = cream;
            this.dim = dim;
        }

        void set(float headingDeg, float bearingDeg, boolean liveCompass) {
            heading = headingDeg;
            target = bearingDeg;
            live = liveCompass;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas cv) {
            float w = getWidth(), h = getHeight();
            float r = Math.min(w, h) / 2f - 14f;
            float cx = w / 2f, cy = h / 2f + 8f;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3f);
            paint.setColor(gold);
            cv.drawCircle(cx, cy, r, paint);
            paint.setStrokeWidth(1.5f);
            paint.setColor((gold & 0x00FFFFFF) | 0x66000000);
            cv.drawCircle(cx, cy, r - 14f, paint);
            cv.save();
            cv.rotate(-heading, cx, cy);
            paint.setStrokeWidth(2f);
            paint.setColor(dim);
            for (int d = 0; d < 360; d += 15) {
                cv.save();
                cv.rotate(d, cx, cy);
                cv.drawLine(cx, cy - r, cx, cy - r + (d % 90 == 0 ? 16f : 8f), paint);
                cv.restore();
            }
            paint.setStyle(Paint.Style.FILL);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(r * 0.14f);
            String[] n = {"N", "E", "S", "W"};
            for (int i = 0; i < 4; i++) {
                cv.save();
                cv.rotate(i * 90f, cx, cy);
                paint.setColor(i == 0 ? cream : dim);
                cv.save();
                cv.translate(cx, cy - r + 38f);
                cv.drawText(n[i], 0, paint.getTextSize() / 3f, paint);
                cv.restore();
                cv.restore();
            }
            // the qibla marker
            cv.save();
            cv.rotate(target, cx, cy);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(5f);
            paint.setColor(gold);
            cv.drawLine(cx, cy, cx, cy - r + 46f, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFF111111);
            RectF k = new RectF(cx - 16f, cy - r + 14f, cx + 16f, cy - r + 46f);
            cv.drawRoundRect(k, 4f, 4f, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3f);
            paint.setColor(gold);
            cv.drawRoundRect(k, 4f, 4f, paint);
            cv.restore();
            cv.restore();
            // where you face
            Path t = new Path();
            t.moveTo(cx, cy - r - 12f);
            t.lineTo(cx - 12f, cy - r + 12f);
            t.lineTo(cx + 12f, cy - r + 12f);
            t.close();
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(gold);
            cv.drawPath(t, paint);
            if (!live) {
                paint.setColor(dim);
                paint.setTextSize(r * 0.11f);
                cv.drawText(String.format(Locale.US, "%.0f°", target), cx, cy + r * 0.2f, paint);
            }
        }
    }
}
