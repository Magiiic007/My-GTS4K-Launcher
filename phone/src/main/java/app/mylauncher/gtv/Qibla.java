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

    private static int mix(int from, int to, float t) {
        int r = Math.round(((from >> 16) & 255) + (((to >> 16) & 255) - ((from >> 16) & 255)) * t);
        int g = Math.round(((from >> 8) & 255) + (((to >> 8) & 255) - ((from >> 8) & 255)) * t);
        int b = Math.round((from & 255) + ((to & 255) - (from & 255)) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /**
     * The compass: a light round dial that turns with the phone (north marks, directions, an ornament ring) with a
     * mark at the qibla bearing, and the Kaaba above it. The Kaaba is grey, and lights up (black and gold with a
     * glow) when the phone faces the qibla.
     */
    static final class Dial extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int gold;
        private float heading;
        private float target;
        private boolean live;
        private float glow; // 0 = grey Kaaba, 1 = lit

        Dial(Context c, int gold, int cream, int dim) {
            super(c);
            this.gold = gold;
        }

        void set(float headingDeg, float bearingDeg, boolean liveCompass) {
            heading = headingDeg;
            target = bearingDeg;
            live = liveCompass;
            float diff = ((target - heading + 540f) % 360f) - 180f;
            boolean on = live && Math.abs(diff) < 4f;
            glow += ((on ? 1f : 0f) - glow) * 0.35f;
            if (Math.abs((on ? 1f : 0f) - glow) < 0.02f) glow = on ? 1f : 0f;
            invalidate();
        }

        private void kaaba(Canvas cv, float cx, float top, float u) {
            float t = glow;
            if (t > 0.01f) {
                paint.setStyle(Paint.Style.FILL);
                paint.setShader(new android.graphics.RadialGradient(cx, top + u, u * 3.4f,
                    new int[] {((int) (0x99 * t) << 24) | 0x00FFD54A, 0x00FFD54A}, null, android.graphics.Shader.TileMode.CLAMP));
                cv.drawCircle(cx, top + u, u * 3.4f, paint);
                paint.setShader(null);
            }
            float h = u * 2f;
            Path topFace = new Path();
            topFace.moveTo(cx - u, top);
            topFace.lineTo(cx + u, top);
            topFace.lineTo(cx + u * 0.8f, top - u * 0.45f);
            topFace.lineTo(cx - u * 0.8f, top - u * 0.45f);
            topFace.close();
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(mix(0xFFB0B0AA, 0xFF3A3A3A, t));
            cv.drawPath(topFace, paint);
            RectF front = new RectF(cx - u, top, cx + u, top + h);
            paint.setColor(mix(0xFF9C9C96, 0xFF0C0C0C, t));
            cv.drawRect(front, paint);
            int band = mix(0xFFC4C4BE, gold, t);
            paint.setColor(band);
            cv.drawRect(cx - u, top + h * 0.22f, cx + u, top + h * 0.34f, paint);
            cv.drawRect(cx - u * 0.28f, top + h * 0.5f, cx + u * 0.04f, top + h * 0.92f, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2f);
            paint.setColor(band);
            cv.drawRect(front, paint);
            cv.drawPath(topFace, paint);
        }

        @Override
        protected void onDraw(Canvas cv) {
            float w = getWidth(), h = getHeight();
            float r = Math.min(w / 2f - 14f, (h - 14f) * 0.40f);
            float cx = w / 2f, cy = h - r - 14f;
            float t = glow;
            int ring = mix(0xFFD9D4C4, 0xFF4CD08A, t);

            // the Kaaba above the dial
            float u = r * 0.16f;
            kaaba(cv, cx, cy - r - r * 0.10f - u * 2f, u);

            // the plate
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFFF7F4EC);
            cv.drawCircle(cx, cy, r, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(6f);
            paint.setColor(ring);
            cv.drawCircle(cx, cy, r - 3f, paint);

            cv.save();
            cv.rotate(-heading, cx, cy);
            // degree ticks every 5, bold every 30
            for (int d = 0; d < 360; d += 5) {
                boolean big = d % 30 == 0;
                paint.setStrokeWidth(big ? 4f : 1.6f);
                paint.setColor(big ? 0xFF222222 : 0xFF9A9A92);
                cv.save();
                cv.rotate(d, cx, cy);
                cv.drawLine(cx, cy - r + 14f, cx, cy - r + (big ? 14f + r * 0.10f : 14f + r * 0.05f), paint);
                cv.restore();
            }
            // an ornament ring of small diamonds
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(r * 0.11f);
            paint.setColor(0x22A39A55);
            cv.drawCircle(cx, cy, r * 0.54f, paint);
            paint.setStrokeWidth(2f);
            paint.setColor(0x88A39A55);
            cv.drawCircle(cx, cy, r * 0.60f, paint);
            cv.drawCircle(cx, cy, r * 0.48f, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xAA8C8440);
            for (int k = 0; k < 36; k++) {
                cv.save();
                cv.rotate(k * 10f, cx, cy);
                Path dm = new Path();
                float y0 = cy - r * 0.54f, s2 = r * 0.028f;
                dm.moveTo(cx, y0 - s2 * 1.4f);
                dm.lineTo(cx + s2, y0);
                dm.lineTo(cx, y0 + s2 * 1.4f);
                dm.lineTo(cx - s2, y0);
                dm.close();
                cv.drawPath(dm, paint);
                cv.restore();
            }
            // the four directions
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(r * 0.12f);
            paint.setFakeBoldText(true);
            String[] n = {"شمال", "شرق", "جنوب", "غرب"};
            for (int i = 0; i < 4; i++) {
                cv.save();
                cv.rotate(i * 90f, cx, cy);
                paint.setColor(i == 0 ? 0xFFD2452F : 0xFF5B6B2A);
                cv.drawText(n[i], cx, cy - r * 0.74f + paint.getTextSize() / 3f, paint);
                cv.restore();
            }
            paint.setFakeBoldText(false);
            // the mark at the qibla bearing: a dashed line and a triangle at the rim
            cv.save();
            cv.rotate(target, cx, cy);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3f);
            paint.setColor(0x881F5D3A);
            paint.setPathEffect(new android.graphics.DashPathEffect(new float[] {10f, 9f}, 0f));
            cv.drawLine(cx, cy - r * 0.36f, cx, cy - r * 0.62f, paint);
            paint.setPathEffect(null);
            Path tri = new Path();
            float ty = cy - r + 2f, ts = r * 0.07f;
            tri.moveTo(cx, ty - ts * 0.4f);
            tri.lineTo(cx - ts, ty + ts * 1.7f);
            tri.lineTo(cx + ts, ty + ts * 1.7f);
            tri.close();
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFF1F5D3A);
            cv.drawPath(tri, paint);
            cv.restore();
            cv.restore();

            // the fixed notch at the top: where the phone faces
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(5f);
            paint.setColor(mix(0xFF444444, 0xFF2E9E66, t));
            cv.drawLine(cx, cy - r - 4f, cx, cy - r + 12f, paint);

            // the empty centre
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFFFFFFFF);
            cv.drawCircle(cx, cy, r * 0.40f, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2f);
            paint.setColor(0x33000000);
            cv.drawCircle(cx, cy, r * 0.40f, paint);
        }
    }
}
