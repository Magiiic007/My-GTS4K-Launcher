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

        private void kaaba(Canvas cv, float cx, float top, float u, boolean glow) {
            // a small Kaaba seen from the front, a little from above; u = half its width
            if (glow) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(0x554CD08A);
                cv.drawCircle(cx, top + u * 1.1f, u * 2.1f, paint);
            }
            float h = u * 2f;
            Path topFace = new Path();
            topFace.moveTo(cx - u, top);
            topFace.lineTo(cx + u, top);
            topFace.lineTo(cx + u * 0.8f, top - u * 0.45f);
            topFace.lineTo(cx - u * 0.8f, top - u * 0.45f);
            topFace.close();
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFF3A3A3A);
            cv.drawPath(topFace, paint);
            RectF front = new RectF(cx - u, top, cx + u, top + h);
            paint.setColor(0xFF0C0C0C);
            cv.drawRect(front, paint);
            // the gold band (hizam) and the door
            paint.setColor(gold);
            cv.drawRect(cx - u, top + h * 0.22f, cx + u, top + h * 0.34f, paint);
            cv.drawRect(cx - u * 0.28f, top + h * 0.5f, cx + u * 0.04f, top + h * 0.92f, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2f);
            paint.setColor(gold);
            cv.drawRect(front, paint);
            cv.drawPath(topFace, paint);
        }

        @Override
        protected void onDraw(Canvas cv) {
            float w = getWidth(), h = getHeight();
            float r = Math.min(w, h) / 2f - 10f;
            float cx = w / 2f, cy = h / 2f + 10f;
            float diff = ((target - heading + 540f) % 360f) - 180f;
            boolean on = live && Math.abs(diff) < 4f;
            int ring = on ? 0xFF4CD08A : gold;
            // the dial plate
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new android.graphics.RadialGradient(cx, cy, r, new int[] {0xFF1B2A24, 0xFF0B1410}, null,
                android.graphics.Shader.TileMode.CLAMP));
            cv.drawCircle(cx, cy, r, paint);
            paint.setShader(null);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(5f);
            paint.setColor(ring);
            cv.drawCircle(cx, cy, r, paint);
            paint.setStrokeWidth(1.5f);
            paint.setColor((ring & 0x00FFFFFF) | 0x77000000);
            cv.drawCircle(cx, cy, r - 9f, paint);
            cv.drawCircle(cx, cy, r * 0.46f, paint);
            cv.save();
            cv.rotate(-heading, cx, cy);
            // degree ticks every 5
            for (int d = 0; d < 360; d += 5) {
                boolean big = d % 30 == 0;
                paint.setStrokeWidth(big ? 3f : 1.5f);
                paint.setColor(big ? cream : dim);
                cv.save();
                cv.rotate(d, cx, cy);
                cv.drawLine(cx, cy - r + 11f, cx, cy - r + (big ? 28f : 19f), paint);
                cv.restore();
            }
            // the four directions
            paint.setStyle(Paint.Style.FILL);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(r * 0.13f);
            String[] n = {"شمال", "شرق", "جنوب", "غرب"};
            for (int i = 0; i < 4; i++) {
                cv.save();
                cv.rotate(i * 90f, cx, cy);
                paint.setColor(i == 0 ? 0xFFE5604A : cream);
                paint.setFakeBoldText(true);
                cv.drawText(n[i], cx, cy - r + 52f + paint.getTextSize() / 3f, paint);
                cv.restore();
            }
            paint.setFakeBoldText(false);
            // the line to the Kaaba and the Kaaba, on the plate at the qibla bearing
            cv.save();
            cv.rotate(target, cx, cy);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(4f);
            paint.setColor(on ? 0xFF4CD08A : gold);
            paint.setPathEffect(new android.graphics.DashPathEffect(new float[] {14f, 10f}, 0f));
            cv.drawLine(cx, cy - r * 0.12f, cx, cy - r + r * 0.40f, paint);
            paint.setPathEffect(null);
            float u = r * 0.105f;
            kaaba(cv, cx, cy - r + r * 0.19f, u, on);
            cv.restore();
            cv.restore();
            // the fixed pointer: where the phone faces
            Path t = new Path();
            t.moveTo(cx, cy - r - 4f);
            t.lineTo(cx - 13f, cy - r + 20f);
            t.lineTo(cx + 13f, cy - r + 20f);
            t.close();
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(ring);
            cv.drawPath(t, paint);
            // the centre
            paint.setColor(0xFF0B1410);
            cv.drawCircle(cx, cy, r * 0.06f, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3f);
            paint.setColor(ring);
            cv.drawCircle(cx, cy, r * 0.06f, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setTextSize(r * 0.14f);
            paint.setColor(cream);
            paint.setFakeBoldText(true);
            cv.drawText(String.format(Locale.US, "%.0f°", live ? heading : target), cx, cy + r * 0.30f, paint);
            paint.setFakeBoldText(false);
        }
    }
}
