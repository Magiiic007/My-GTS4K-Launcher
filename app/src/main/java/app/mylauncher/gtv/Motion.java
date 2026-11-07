package app.mylauncher.gtv;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

/** The few small movements the app uses: a soft grow when an item is selected, and a fade or pop when something appears. */
final class Motion {
    private Motion() {}

    /** The one easing used everywhere: quick start, long soft landing. */
    static final PathInterpolator SMOOTH = new PathInterpolator(0.2f, 0.8f, 0.2f, 1f);

    private static float dp(View v, float x) {
        return x * v.getResources().getDisplayMetrics().density;
    }

    /** A window's panel comes up from a little below while it grows from slightly smaller. */
    static void panelIn(View v) {
        v.setPivotX(v.getWidth() / 2f);
        v.setPivotY(v.getHeight());
        v.setAlpha(0f);
        ObjectAnimator a = ObjectAnimator.ofPropertyValuesHolder(v,
            PropertyValuesHolder.ofFloat(View.ALPHA, 0f, 1f),
            PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, dp(v, 22), 0f),
            PropertyValuesHolder.ofFloat(View.SCALE_X, 0.96f, 1f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.96f, 1f));
        a.setDuration(300L);
        a.setInterpolator(SMOOTH);
        a.start();
    }

    /** The rows of a menu slide in one after another. */
    static void rowsIn(ViewGroup rows) {
        int n = Math.min(rows.getChildCount(), 14);
        for (int i = 0; i < n; i++) {
            View r = rows.getChildAt(i);
            float to = r.getAlpha();
            r.setAlpha(0f);
            ObjectAnimator a = ObjectAnimator.ofPropertyValuesHolder(r,
                PropertyValuesHolder.ofFloat(View.ALPHA, 0f, to),
                PropertyValuesHolder.ofFloat(View.TRANSLATION_X, -dp(r, 16), 0f));
            a.setStartDelay(90L + 32L * i);
            a.setDuration(320L);
            a.setInterpolator(SMOOTH);
            a.start();
        }
    }

    /** The home screen's panels rise into place one after another (after the opening screen). */
    static void stageIn(ViewGroup stage, View skipA, View skipB) {
        int n = stage.getChildCount();
        for (int i = 0; i < n; i++) {
            View c = stage.getChildAt(i);
            if (c == skipA || c == skipB) continue;
            float to = c.getAlpha();
            c.setAlpha(0f);
            ObjectAnimator a = ObjectAnimator.ofPropertyValuesHolder(c,
                PropertyValuesHolder.ofFloat(View.ALPHA, 0f, to),
                PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, 22f, 0f));
            a.setStartDelay(Math.min(i * 6L, 520L));
            a.setDuration(550L);
            a.setInterpolator(SMOOTH);
            a.start();
        }
    }

    /** Selected items grow slightly and settle back when the selection moves on. */
    static void pop(View v, boolean selected, float scale) {
        v.animate().cancel();
        v.animate().scaleX(selected ? scale : 1f).scaleY(selected ? scale : 1f).setDuration(200L)
            .setInterpolator(SMOOTH).start();
    }

    static void fadeIn(View v, long ms) {
        v.setAlpha(0f);
        v.animate().alpha(1f).setDuration(ms).setInterpolator(new DecelerateInterpolator()).start();
    }

    /** A window's panel: fades in while growing from slightly smaller, with a tiny overshoot. */
    static void popIn(View v) {
        v.setAlpha(0f);
        v.setScaleX(0.92f);
        v.setScaleY(0.92f);
        v.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(260L)
            .setInterpolator(new OvershootInterpolator(0.8f)).start();
    }
}
