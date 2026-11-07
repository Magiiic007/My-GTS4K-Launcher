package app.mylauncher.gtv;

import android.view.View;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.OvershootInterpolator;

/** The appear and disappear movements of the things that float over other apps (cards, popups, the round icon). */
final class Fx {
    private Fx() {}

    private static float dp(View v, float d) {
        return d * v.getResources().getDisplayMetrics().density;
    }

    /** A card that comes in from the top: it fades in, drops a little and grows from its top-right corner. */
    static void cardIn(View v) {
        v.setPivotX(v.getResources().getDisplayMetrics().widthPixels);
        v.setPivotY(0f);
        v.setAlpha(0f);
        v.setTranslationY(-dp(v, 16));
        v.setScaleX(0.94f);
        v.setScaleY(0.94f);
        v.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f).setDuration(280L)
            .setInterpolator(new DecelerateInterpolator(1.8f)).start();
    }

    /** The round icon pops in with a small spring. */
    static void bubbleIn(View v) {
        v.setAlpha(0f);
        v.setScaleX(0.4f);
        v.setScaleY(0.4f);
        v.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(340L)
            .setInterpolator(new OvershootInterpolator(1.8f)).start();
    }

    /** A full-screen dim with a card in the middle: the dim fades in while the card rises and settles. */
    static void popupIn(View root, View card) {
        root.setAlpha(0f);
        root.animate().alpha(1f).setDuration(220L).setInterpolator(new DecelerateInterpolator()).start();
        card.setTranslationY(dp(card, 28));
        card.setScaleX(0.94f);
        card.setScaleY(0.94f);
        card.animate().translationY(0f).scaleX(1f).scaleY(1f).setDuration(320L)
            .setInterpolator(new DecelerateInterpolator(1.8f)).start();
    }

    /** Takes a floating view off the screen after a short fade; safe to call twice or when it is already gone. */
    static void remove(final WindowManager wm, final View v, final View mover) {
        if (v == null || wm == null) return;
        if (v.getTag(R.id.fx_leaving) != null) return;
        v.setTag(R.id.fx_leaving, Boolean.TRUE);
        final Runnable done = new Runnable() {
            @Override
            public void run() {
                try {
                    wm.removeViewImmediate(v);
                } catch (RuntimeException ignored) {
                    // already gone
                }
            }
        };
        if (!v.isAttachedToWindow()) {
            done.run();
            return;
        }
        View m = mover != null ? mover : v;
        if (m != v) {
            m.animate().translationY(dp(m, 12)).scaleX(0.97f).scaleY(0.97f).setDuration(180L)
                .setInterpolator(new AccelerateInterpolator()).start();
        } else {
            v.animate().translationY(-dp(v, 8)).scaleX(0.96f).scaleY(0.96f).setInterpolator(new AccelerateInterpolator());
        }
        v.animate().alpha(0f).setDuration(180L).setInterpolator(new AccelerateInterpolator())
            .withEndAction(done).start();
    }
}
