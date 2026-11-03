package app.mylauncher.gtv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * The home screen is laid out on one fixed canvas, 1365 x 768 units (the grid of the design pictures), and the canvas
 * is scaled to fit whatever the TV's screen is. So every panel sits exactly where it does in the design, on any TV.
 */
final class Stage {
    private Stage() {}

    static final int W = 1365, H = 768;

    /** Puts a view at left, top, right, bottom (in canvas units). */
    static void add(ViewGroup parent, View v, int l, int t, int r, int b) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(r - l, b - t, Gravity.TOP | Gravity.LEFT);
        lp.leftMargin = l;
        lp.topMargin = t;
        parent.addView(v, lp);
    }

    /** Scales and centres the canvas inside the screen. */
    static void fit(View screen, View stage) {
        int w = screen.getWidth(), h = screen.getHeight();
        if (w == 0 || h == 0) return;
        float s = Math.min(w / (float) W, h / (float) H);
        stage.setPivotX(0f);
        stage.setPivotY(0f);
        stage.setScaleX(s);
        stage.setScaleY(s);
        stage.setTranslationX((w - W * s) / 2f);
        stage.setTranslationY((h - H * s) / 2f);
    }

    /** A TextView sized in canvas units. */
    static TextView text(Context c, String s, float size, int colour, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextColor(colour);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, size);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setIncludeFontPadding(false);
        return t;
    }

    /** Text that shrinks to fit one line instead of being cut. */
    static void fitOneLine(TextView t, int minPx, int maxPx) {
        t.setMaxLines(1);
        t.setAutoSizeTextTypeUniformWithConfiguration(minPx, maxPx, 1, TypedValue.COMPLEX_UNIT_PX);
    }

    /** Big text with an outline round it (the clock in the kids' look). */
    static final class Outlined extends TextView {
        private int outline = 0;
        private float width = 0f;

        Outlined(Context c) {
            super(c);
        }

        void setOutline(int colour, float widthPx) {
            outline = colour;
            width = widthPx;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (width > 0f) {
                int fill = getCurrentTextColor();
                getPaint().setStyle(Paint.Style.STROKE);
                getPaint().setStrokeWidth(width);
                getPaint().setStrokeJoin(Paint.Join.ROUND);
                setTextColor(outline);
                super.onDraw(canvas);
                getPaint().setStyle(Paint.Style.FILL);
                setTextColor(fill);
            }
            super.onDraw(canvas);
        }
    }
}
