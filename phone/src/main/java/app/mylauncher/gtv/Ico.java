package app.mylauncher.gtv;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/** Small line icons drawn in code (24-unit grid), so they look the same on every phone, unlike emoji. */
final class Ico extends Drawable {
    private final String kind;
    private final int color;
    private final int size;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

    Ico(String kind, int color, int size) {
        this.kind = kind;
        this.color = color;
        this.size = size;
        setBounds(0, 0, size, size);
    }

    private void line(Canvas c, float a, float b, float x, float y) { c.drawLine(a, b, x, y, p); }

    private void dot(Canvas c, float x, float y, float r) {
        p.setStyle(Paint.Style.FILL);
        c.drawCircle(x, y, r, p);
        p.setStyle(Paint.Style.STROKE);
    }

    private void poly(Canvas c, float... v) {
        Path q = new Path();
        q.moveTo(v[0], v[1]);
        for (int i = 2; i < v.length; i += 2) q.lineTo(v[i], v[i + 1]);
        c.drawPath(q, p);
    }

    private void rr(Canvas c, float l, float t, float r, float b, float rad) { c.drawRoundRect(new RectF(l, t, r, b), rad, rad, p); }

    private void cloud(Canvas c) {
        c.drawArc(new RectF(3, 10, 11, 18), 90, 180, false, p);
        c.drawArc(new RectF(7, 5, 16, 14), 180, 180, false, p);
        c.drawArc(new RectF(13, 10, 21, 18), 270, 180, false, p);
        line(c, 7, 18, 17, 18);
    }

    @Override
    public void draw(Canvas c) {
        p.setColor(color);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.8f);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        c.save();
        c.scale(size / 24f, size / 24f);
        switch (kind) {
            case "mosque": {
                Path q = new Path();
                q.moveTo(7, 20); q.lineTo(7, 14);
                q.arcTo(new RectF(7, 7, 17, 21), 180, 180);
                q.lineTo(17, 20);
                c.drawPath(q, p);
                line(c, 12, 3.5f, 12, 7);
                line(c, 3, 20, 21, 20);
                line(c, 3.5f, 20, 3.5f, 10); line(c, 5.5f, 20, 5.5f, 10);
                line(c, 18.5f, 20, 18.5f, 10); line(c, 20.5f, 20, 20.5f, 10);
                break;
            }
            case "speaker": {
                poly(c, 4, 10, 4, 14, 8, 14, 13, 18, 13, 6, 8, 10, 4, 10);
                c.drawArc(new RectF(11, 9, 19, 15), -60, 120, false, p);
                c.drawArc(new RectF(11, 5.5f, 22, 18.5f), -55, 110, false, p);
                break;
            }
            case "check": c.drawCircle(12, 12, 9, p); poly(c, 8, 12.5f, 11, 15.5f, 16, 9); break;
            case "globe": c.drawCircle(12, 12, 9, p); c.drawOval(new RectF(8, 3, 16, 21), p); line(c, 3, 12, 21, 12); break;
            case "calendar": rr(c, 4, 5, 20, 20, 3); line(c, 4, 10, 20, 10); line(c, 8, 3, 8, 7); line(c, 16, 3, 16, 7); dot(c, 9, 14, 1); dot(c, 13, 14, 1); dot(c, 16, 14, 1); break;
            case "book": {
                Path q = new Path();
                q.moveTo(12, 6); q.cubicTo(9, 4, 6, 4, 3, 5); q.lineTo(3, 19); q.cubicTo(6, 18, 9, 18, 12, 20);
                q.cubicTo(15, 18, 18, 18, 21, 19); q.lineTo(21, 5); q.cubicTo(18, 4, 15, 4, 12, 6); q.close();
                c.drawPath(q, p); line(c, 12, 6, 12, 20);
                break;
            }
            case "beads": for (int i = 0; i < 9; i++) { double a = i * Math.PI * 2 / 9; dot(c, (float) (12 + 7.5 * Math.cos(a)), (float) (11 + 7.5 * Math.sin(a)), 1.7f); } line(c, 12, 18.5f, 12, 22); break;
            case "window": rr(c, 3, 5, 21, 19, 2.5f); line(c, 3, 9, 21, 9); dot(c, 6, 7, .8f); dot(c, 9, 7, .8f); break;
            case "clock": c.drawCircle(12, 12, 9, p); poly(c, 12, 7, 12, 12, 16, 14); break;
            case "bell": case "bellOff": {
                Path q = new Path();
                q.moveTo(6, 17); q.lineTo(6, 11); q.arcTo(new RectF(6, 4, 18, 16), 180, 180); q.lineTo(18, 17); q.lineTo(20, 18); q.lineTo(4, 18); q.close();
                c.drawPath(q, p); line(c, 10, 21, 14, 21);
                if (kind.equals("bellOff")) line(c, 4, 4, 20, 20);
                break;
            }
            case "compass": c.drawCircle(12, 12, 9, p); poly(c, 15.5f, 8.5f, 13, 13, 8.5f, 15.5f, 11, 11, 15.5f, 8.5f); break;
            case "radio": rr(c, 3, 8, 21, 20, 3); line(c, 7, 8, 17, 3); c.drawCircle(9, 14, 3, p); line(c, 15, 12, 18, 12); line(c, 15, 16, 18, 16); break;
            case "gear": c.drawCircle(12, 12, 6, p); c.drawCircle(12, 12, 2.3f, p); for (int i = 0; i < 8; i++) { double a = i * Math.PI / 4; line(c, (float) (12 + 6 * Math.cos(a)), (float) (12 + 6 * Math.sin(a)), (float) (12 + 9 * Math.cos(a)), (float) (12 + 9 * Math.sin(a))); } break;
            case "moon": {
                Path q = new Path();
                q.moveTo(20, 14.5f); q.arcTo(new RectF(4, 3, 20, 19), 25, 270, false); q.arcTo(new RectF(8, 6, 21, 19), 235, -140, false); q.close();
                c.drawPath(q, p);
                break;
            }
            case "text": { p.setStyle(Paint.Style.FILL); p.setTextSize(14); p.setFakeBoldText(true); p.setTextAlign(Paint.Align.CENTER); c.drawText("Aa", 12, 17, p); p.setFakeBoldText(false); p.setStyle(Paint.Style.STROKE); break; }
            case "save": rr(c, 4, 4, 20, 20, 2.5f); rr(c, 8, 4, 16, 9, 1); rr(c, 8, 14, 16, 20, 1); break;
            case "info": c.drawCircle(12, 12, 9, p); line(c, 12, 11, 12, 17); dot(c, 12, 7.5f, 1.2f); break;
            case "search": c.drawCircle(10, 10, 6.5f, p); line(c, 15, 15, 21, 21); break;
            case "scroll": rr(c, 6, 3, 18, 21, 2.5f); line(c, 9, 8, 15, 8); line(c, 9, 12, 15, 12); line(c, 9, 16, 13, 16); break;
            case "gamepad": rr(c, 3, 7, 21, 17, 5); line(c, 8, 10, 8, 14); line(c, 6, 12, 10, 12); dot(c, 15.5f, 11, 1.1f); dot(c, 17.5f, 13, 1.1f); break;
            case "palette": c.drawCircle(12, 12, 9, p); dot(c, 8, 10, 1.3f); dot(c, 12, 7.5f, 1.3f); dot(c, 16, 10, 1.3f); break;
            case "battery": rr(c, 3, 8, 19, 16, 2); rr(c, 19, 10.5f, 21.5f, 13.5f, .8f); line(c, 6, 12, 12, 12); break;
            case "phone": rr(c, 7, 3, 17, 21, 2.5f); dot(c, 12, 18, 1); break;
            case "calc": rr(c, 5, 3, 19, 21, 2.5f); rr(c, 8, 6, 16, 10, 1); for (int i = 0; i < 3; i++) { dot(c, 9 + i * 3, 14, 1); dot(c, 9 + i * 3, 17.5f, 1); } break;
            case "plate": c.drawCircle(12, 12, 9, p); c.drawCircle(12, 12, 5, p); break;
            case "note": rr(c, 5, 4, 19, 20, 2.5f); line(c, 8.5f, 9, 15.5f, 9); line(c, 8.5f, 13, 15.5f, 13); line(c, 8.5f, 17, 12, 17); break;
            case "chart": rr(c, 5, 12, 8.5f, 20, .8f); rr(c, 10.3f, 6, 13.7f, 20, .8f); rr(c, 15.5f, 9, 19, 20, .8f); break;
            case "repeat": c.drawArc(new RectF(4, 4, 20, 20), -50, 280, false, p); poly(c, 15, 3.5f, 19, 6.3f, 15.5f, 9.5f); break;
            case "sliders": for (int i = 0; i < 3; i++) line(c, 4, 7 + i * 5, 20, 7 + i * 5); dot(c, 9, 7, 2.2f); dot(c, 15, 12, 2.2f); dot(c, 8, 17, 2.2f); break;
            case "dotOn": p.setStyle(Paint.Style.FILL); c.drawCircle(12, 12, 6, p); break;
            case "dotOff": c.drawCircle(12, 12, 6, p); break;
            case "drop": {
                Path q = new Path();
                q.moveTo(12, 3); q.cubicTo(12, 3, 6, 10, 6, 14); q.arcTo(new RectF(6, 8, 18, 20), 180, -180, false); q.cubicTo(18, 10, 12, 3, 12, 3);
                c.drawPath(q, p);
                break;
            }
            case "ruler": rr(c, 3, 8, 21, 16, 2); for (int i = 0; i < 4; i++) line(c, 7 + i * 4, 8, 7 + i * 4, 11.5f + (i % 2)); break;
            case "books": rr(c, 4, 5, 8, 20, .8f); rr(c, 10, 5, 14, 20, .8f); c.save(); c.rotate(-12, 18, 20); rr(c, 16, 5, 20, 20, .8f); c.restore(); break;
            case "pulse": poly(c, 3, 12, 8, 12, 10, 6, 14, 18, 16, 12, 21, 12); break;
            case "pin": {
                Path q = new Path();
                q.moveTo(12, 21); q.cubicTo(12, 21, 5, 14, 5, 9.5f); q.arcTo(new RectF(5, 2.5f, 19, 16.5f), 180, 180, false); q.cubicTo(19, 14, 12, 21, 12, 21);
                c.drawPath(q, p); c.drawCircle(12, 9.5f, 2.5f, p);
                break;
            }
            case "link": rr(c, 2.5f, 9, 13, 15, 3); rr(c, 11, 9, 21.5f, 15, 3); break;
            case "wifi": dot(c, 12, 18, 1.3f); c.drawArc(new RectF(7, 13, 17, 23), 225, 90, false, p); c.drawArc(new RectF(3, 9, 21, 27), 225, 90, false, p); c.drawArc(new RectF(-1, 5, 25, 31), 228, 84, false, p); break;
            case "keyboard": rr(c, 2, 7, 22, 17, 2.5f); for (int i = 0; i < 4; i++) dot(c, 6 + i * 4, 10.5f, .9f); line(c, 7, 14, 17, 14); break;
            case "sunrise": line(c, 3, 18, 21, 18); c.drawArc(new RectF(7, 11, 17, 21), 180, 180, false, p); line(c, 12, 4, 12, 7); line(c, 5, 10, 7, 12); line(c, 19, 10, 17, 12); break;
            case "cloud": cloud(c); break;
            case "shield": { Path q = new Path(); q.moveTo(12, 3); q.lineTo(19, 6); q.lineTo(19, 12); q.cubicTo(19, 16, 16, 19, 12, 21); q.cubicTo(8, 19, 5, 16, 5, 12); q.lineTo(5, 6); q.close(); c.drawPath(q, p); break; }
            case "swords": line(c, 5, 5, 19, 19); line(c, 19, 5, 5, 19); line(c, 3.5f, 8.5f, 8.5f, 3.5f); line(c, 20.5f, 8.5f, 15.5f, 3.5f); break;
            case "hands": c.drawArc(new RectF(4, 8, 20, 22), 0, 180, false, p); line(c, 4, 15, 5, 8); line(c, 20, 15, 19, 8); dot(c, 12, 7, 1.3f); break;
            case "plane": poly(c, 3, 12, 21, 5, 15, 21, 12, 14, 3, 12); line(c, 12, 14, 21, 5); break;
            case "people": c.drawCircle(7.5f, 7.5f, 2.4f, p); c.drawCircle(16.5f, 7.5f, 2.4f, p); c.drawArc(new RectF(3, 12, 12, 21), 180, 180, false, p); c.drawArc(new RectF(12, 12, 21, 21), 180, 180, false, p); c.drawCircle(12, 14, 1.7f, p); break;
            case "handshake": poly(c, 3, 12, 8, 8, 12, 10, 16, 8, 21, 12); poly(c, 3, 12, 8, 17, 12, 19, 16, 17, 21, 12); break;
            case "rain": c.save(); c.translate(0, -4); cloud(c); c.restore(); line(c, 8, 17, 7, 21); line(c, 12, 17, 11, 21); line(c, 16, 17, 15, 21); break;
            default: c.drawCircle(12, 12, 4, p);
        }
        c.restore();
    }

    @Override public int getIntrinsicWidth() { return size; }
    @Override public int getIntrinsicHeight() { return size; }
    @Override public void setAlpha(int a) { p.setAlpha(a); }
    @Override public void setColorFilter(android.graphics.ColorFilter f) { p.setColorFilter(f); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
