package app.mylauncher.gtv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;


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

        /** A 3D Kaaba: a lit top, a front and a darker right side, a ground shadow, and a glow when it is lit. */
        private void kaaba(Canvas cv, float cx, float top, float u) {
            float t = glow;
            float h = u * 2f;
            float fw = u * 1.35f;          // width of the front face
            float sk = u * 0.22f;          // how much the side face rises towards the back
            // ground shadow
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0x33000000);
            cv.drawOval(new RectF(cx - u * 1.1f, top + h - u * 0.12f, cx + u * 1.5f, top + h + u * 0.38f), paint);
            if (t > 0.01f) {
                paint.setColor(0xFF000000); // the paint's alpha would dim the gradient
                paint.setShader(new android.graphics.RadialGradient(cx, top + u, u * 3.6f,
                    new int[] {((int) (0xAA * t) << 24) | 0x00FFD54A, 0x00FFD54A}, null, android.graphics.Shader.TileMode.CLAMP));
                cv.drawCircle(cx, top + u, u * 3.6f, paint);
                paint.setShader(null);
            }
            float l = cx - u, m = cx - u + fw, r = cx + u * 1.0f + (fw - u * 1.35f);
            float rr = m + u * 0.65f;
            // top face
            Path topFace = new Path();
            topFace.moveTo(l, top);
            topFace.lineTo(m, top);
            topFace.lineTo(rr, top - sk);
            topFace.lineTo(l + u * 0.65f, top - sk);
            topFace.close();
            paint.setColor(mix(0xFFC8C8C2, 0xFF4A4A4A, t));
            cv.drawPath(topFace, paint);
            // right side face (darkest)
            Path side = new Path();
            side.moveTo(m, top);
            side.lineTo(rr, top - sk);
            side.lineTo(rr, top - sk + h);
            side.lineTo(m, top + h);
            side.close();
            paint.setColor(mix(0xFF7E7E78, 0xFF050505, t));
            cv.drawPath(side, paint);
            // front face with a soft vertical shade
            RectF front = new RectF(l, top, m, top + h);
            paint.setColor(0xFF000000);
            paint.setShader(new android.graphics.LinearGradient(0f, top, 0f, top + h,
                mix(0xFFA8A8A2, 0xFF2A2A2A, t), mix(0xFF8C8C86, 0xFF0A0A0A, t), android.graphics.Shader.TileMode.CLAMP));
            cv.drawRect(front, paint);
            paint.setShader(null);
            // the gold band (hizam) across the front and the side, and the door
            int band = mix(0xFFD2D2CC, gold, t);
            paint.setColor(band);
            cv.drawRect(l, top + h * 0.22f, m, top + h * 0.34f, paint);
            Path sb = new Path();
            sb.moveTo(m, top + h * 0.22f);
            sb.lineTo(rr, top - sk + h * 0.22f);
            sb.lineTo(rr, top - sk + h * 0.34f);
            sb.lineTo(m, top + h * 0.34f);
            sb.close();
            paint.setColor(mix(0xFFA6A6A0, 0xFFB8892F, t));
            cv.drawPath(sb, paint);
            paint.setColor(band);
            cv.drawRect(l + fw * 0.52f, top + h * 0.5f, l + fw * 0.80f, top + h * 0.94f, paint);
            // edges
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.5f);
            paint.setColor(mix(0xFFB4B4AE, 0xFF000000, t));
            cv.drawRect(front, paint);
            cv.drawPath(topFace, paint);
            cv.drawPath(side, paint);
            paint.setStyle(Paint.Style.FILL);
        }

        /** A soft drop shadow under a circle, drawn as a few fading rings. */
        private void dropShadow(Canvas cv, float cx, float cy, float r, float dy) {
            paint.setStyle(Paint.Style.FILL);
            for (int i = 0; i < 6; i++) {
                float grow = i * r * 0.014f;
                paint.setColor(((0x0E - i) << 24));
                cv.drawCircle(cx, cy + dy, r + grow, paint);
            }
            paint.setColor(0xFF000000);
        }

        @Override
        protected void onDraw(Canvas cv) {
            float w = getWidth(), h = getHeight();
            float r = Math.min(w / 2f - 16f, (h - 22f) * 0.40f);
            float cx = w / 2f, cy = h - r - 22f;
            float t = glow;
            int hi = mix(0xFFF1E3B0, 0xFFB6F0D0, t);
            int lo = mix(0xFF8E7430, 0xFF1F7A4C, t);

            // the Kaaba above the dial
            float u = r * 0.15f;
            kaaba(cv, cx, cy - r - r * 0.10f - u * 2f, u);

            // the dial stands off the page: a drop shadow, a metal rim, a domed face
            dropShadow(cv, cx, cy, r, r * 0.05f);
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new android.graphics.LinearGradient(cx - r, cy - r, cx + r, cy + r, hi, lo,
                android.graphics.Shader.TileMode.CLAMP));
            cv.drawCircle(cx, cy, r, paint);
            paint.setShader(null);
            float face = r * 0.93f;
            paint.setShader(new android.graphics.RadialGradient(cx - face * 0.3f, cy - face * 0.35f, face * 1.45f,
                new int[] {0xFFFFFFFF, 0xFFF4EFE2, 0xFFD9D2BE}, new float[] {0f, 0.55f, 1f}, android.graphics.Shader.TileMode.CLAMP));
            cv.drawCircle(cx, cy, face, paint);
            paint.setShader(null);
            // an inner edge that gives the face a rim
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2.5f);
            paint.setColor(0x44000000);
            cv.drawCircle(cx, cy, face, paint);

            cv.save();
            cv.rotate(-heading, cx, cy);
            // degree ticks every 5, bold every 30
            for (int d = 0; d < 360; d += 5) {
                boolean big = d % 30 == 0;
                paint.setStrokeWidth(big ? 4f : 1.6f);
                paint.setColor(big ? 0xFF222222 : 0xFF9A9A92);
                cv.save();
                cv.rotate(d, cx, cy);
                cv.drawLine(cx, cy - face + 12f, cx, cy - face + (big ? 12f + r * 0.10f : 12f + r * 0.05f), paint);
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
            // the mark at the qibla bearing: a dashed line and a raised triangle at the rim
            cv.save();
            cv.rotate(target, cx, cy);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3f);
            paint.setColor(0x881F5D3A);
            paint.setPathEffect(new android.graphics.DashPathEffect(new float[] {10f, 9f}, 0f));
            cv.drawLine(cx, cy - r * 0.36f, cx, cy - r * 0.62f, paint);
            paint.setPathEffect(null);
            Path tri = new Path();
            float ty = cy - face + 2f, ts = r * 0.07f;
            tri.moveTo(cx, ty - ts * 0.4f);
            tri.lineTo(cx - ts, ty + ts * 1.7f);
            tri.lineTo(cx + ts, ty + ts * 1.7f);
            tri.close();
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0x44000000);
            cv.save();
            cv.translate(2.5f, 3.5f);
            cv.drawPath(tri, paint);
            cv.restore();
            paint.setColor(0xFF1F5D3A);
            cv.drawPath(tri, paint);
            cv.restore();
            cv.restore();

            // the fixed notch at the top: where the phone faces
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(5f);
            paint.setColor(mix(0xFF444444, 0xFF2E9E66, t));
            cv.drawLine(cx, cy - r - 2f, cx, cy - face + 10f, paint);

            // the centre: a sunken disc (dark at the top edge, light at the bottom) with a cap
            float ci = r * 0.40f;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFF000000);
            paint.setShader(new android.graphics.LinearGradient(0f, cy - ci, 0f, cy + ci, 0xFFD6D0BE, 0xFFFFFFFF,
                android.graphics.Shader.TileMode.CLAMP));
            cv.drawCircle(cx, cy, ci, paint);
            paint.setShader(null);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3f);
            paint.setColor(0x33000000);
            cv.drawCircle(cx, cy, ci, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new android.graphics.RadialGradient(cx - r * 0.02f, cy - r * 0.02f, r * 0.07f, hi, lo,
                android.graphics.Shader.TileMode.CLAMP));
            cv.drawCircle(cx, cy, r * 0.055f, paint);
            paint.setShader(null);

            // a glass-like shine across the upper left of the dial
            paint.setColor(0xFF000000);
            paint.setShader(new android.graphics.RadialGradient(cx - face * 0.35f, cy - face * 0.55f, face * 0.9f,
                0x33FFFFFF, 0x00FFFFFF, android.graphics.Shader.TileMode.CLAMP));
            cv.drawCircle(cx, cy, face, paint);
            paint.setShader(null);
        }
    }
}
