package app.mylauncher.gtv;

import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

/** The few small movements the app uses: a soft grow when an item is selected, and a fade or pop when something appears. */
final class Motion {
    private Motion() {}

    /** Selected items grow slightly and settle back when the selection moves on. */
    static void pop(View v, boolean selected, float scale) {
        v.animate().cancel();
        v.animate().scaleX(selected ? scale : 1f).scaleY(selected ? scale : 1f).setDuration(140L)
            .setInterpolator(new DecelerateInterpolator()).start();
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
