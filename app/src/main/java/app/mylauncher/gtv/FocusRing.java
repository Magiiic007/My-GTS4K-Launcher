package app.mylauncher.gtv;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;

/**
 * The gold frame over whatever the remote is on. It slides from one thing to the next instead of jumping, and follows
 * the list when it scrolls. It lives on the home canvas, so windows (menus) keep their own marker.
 */
final class FocusRing {
    private final FrameLayout stage;
    private final ViewGroup root;
    private final View view;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private ValueAnimator anim;
    private float l, t, w, h;
    private boolean shown;

    private final Runnable settle = new Runnable() {
        @Override
        public void run() {
            View f = root.findFocus();
            Rect r = f == null ? null : rectOf(f);
            if (r != null) place(r.left, r.top, r.width(), r.height());
        }
    };

    FocusRing(Context c, FrameLayout stage, ViewGroup root) {
        this.stage = stage;
        this.root = root;
        view = new View(c);
        view.setBackground(Theme.ring(c, 20));
        view.setFocusable(false);
        view.setClickable(false);
        view.setAlpha(0f);
        stage.addView(view, new FrameLayout.LayoutParams(10, 10, Gravity.TOP | Gravity.LEFT));
        root.getViewTreeObserver().addOnGlobalFocusChangeListener(new android.view.ViewTreeObserver.OnGlobalFocusChangeListener() {
            @Override
            public void onGlobalFocusChanged(View oldFocus, View newFocus) {
                moved(newFocus);
            }
        });
    }

    private boolean inStage(View v) {
        ViewParent p = v.getParent();
        while (p != null) {
            if (p == stage) return true;
            p = p.getParent();
        }
        return false;
    }

    private Rect rectOf(View f) {
        if (f == view || !inStage(f) || f.getWidth() == 0 || f.getHeight() == 0 || f.getVisibility() != View.VISIBLE) return null;
        Rect r = new Rect(0, 0, f.getWidth(), f.getHeight());
        try {
            stage.offsetDescendantRectToMyCoords(f, r);
        } catch (IllegalArgumentException e) {
            return null;
        }
        r.inset(-3, -3);
        return r;
    }

    private void moved(View f) {
        handler.removeCallbacks(settle);
        Rect r = f == null ? null : rectOf(f);
        if (r == null) {
            if (shown) view.animate().alpha(0f).setDuration(150L).start();
            shown = false;
            return;
        }
        if (!shown) {
            place(r.left, r.top, r.width(), r.height());
            view.animate().alpha(1f).setDuration(180L).start();
            shown = true;
        } else {
            glide(r.left, r.top, r.width(), r.height());
        }
        // The list may still be scrolling to the new item: look again once it has settled.
        handler.postDelayed(settle, 280L);
        handler.postDelayed(settle, 620L);
    }

    private void glide(final float tl, final float tt, final float tw, final float th) {
        if (anim != null) anim.cancel();
        final float fl = l, ft = t, fw = w, fh = h;
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(240L);
        anim.setInterpolator(Motion.SMOOTH);
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                float k = (Float) a.getAnimatedValue();
                set(fl + (tl - fl) * k, ft + (tt - ft) * k, fw + (tw - fw) * k, fh + (th - fh) * k);
            }
        });
        anim.start();
    }

    private void place(float nl, float nt, float nw, float nh) {
        if (anim != null) anim.cancel();
        set(nl, nt, nw, nh);
    }

    /** Sets the frame's box directly (also in its layout params, so a later layout pass keeps it). */
    private void set(float nl, float nt, float nw, float nh) {
        l = nl;
        t = nt;
        w = nw;
        h = nh;
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) view.getLayoutParams();
        lp.leftMargin = Math.round(nl);
        lp.topMargin = Math.round(nt);
        lp.width = Math.round(nw);
        lp.height = Math.round(nh);
        view.layout(lp.leftMargin, lp.topMargin, lp.leftMargin + lp.width, lp.topMargin + lp.height);
    }
}
