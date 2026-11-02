package app.mylauncher.gtv;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The full-screen popup over whatever is playing: used by the adhan, the iqama and the reminders. Its background is a
 * gradient in the colours of the look chosen on the home screen, with an Islamic star pattern and a frame. OK or
 * Back closes it (the volume keys still work).
 */
final class Popup {
    interface OnClose {
        void closed();
    }

    private final Context ctx;
    private final WindowManager windows;
    private View root;

    private Popup(Context c) {
        ctx = c;
        windows = c.getSystemService(WindowManager.class);
    }

    /** Shows the popup; returns null if there is no permission to draw over other apps (nothing is shown). */
    static Popup show(Context c, String title, String sub, String hint, final OnClose onClose) {
        if (!Settings.canDrawOverlays(c)) return null;
        Popup p = new Popup(c);
        try {
            p.build(title, sub, hint, onClose);
            return p;
        } catch (RuntimeException e) {
            p.root = null;
            return null;
        }
    }

    private int dp(float v) {
        return Theme.dp(ctx, v);
    }

    private void build(String title, String sub, String hint, final OnClose onClose) {
        FrameLayout frame = new FrameLayout(ctx) {
            @Override
            public boolean dispatchKeyEvent(KeyEvent e) {
                int k = e.getKeyCode();
                if (k == KeyEvent.KEYCODE_VOLUME_UP || k == KeyEvent.KEYCODE_VOLUME_DOWN
                        || k == KeyEvent.KEYCODE_VOLUME_MUTE) {
                    return super.dispatchKeyEvent(e);
                }
                if (e.getAction() == KeyEvent.ACTION_UP && (k == KeyEvent.KEYCODE_BACK
                        || k == KeyEvent.KEYCODE_ENTER || k == KeyEvent.KEYCODE_DPAD_CENTER)) {
                    onClose.closed();
                }
                return true;
            }
        };
        frame.setBackground(Theme.popup(ctx));

        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(dp(60), dp(20), dp(60), dp(20));

        ImageView moon = new ImageView(ctx);
        moon.setImageResource(R.drawable.ic_mosque);
        moon.setColorFilter(Theme.accent(ctx));
        col.addView(moon, new LinearLayout.LayoutParams(dp(72), dp(72)));

        TextView name = new TextView(ctx);
        name.setText(title);
        name.setTextColor(Theme.popupText(ctx));
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 44);
        name.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        name.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nameLp.topMargin = dp(12);
        col.addView(name, nameLp);

        View bar = new View(ctx);
        bar.setBackgroundColor(Theme.accent(ctx));
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(dp(140), dp(3));
        barLp.topMargin = dp(18);
        barLp.bottomMargin = dp(18);
        col.addView(bar, barLp);

        TextView subView = new TextView(ctx);
        subView.setText(sub);
        subView.setTextColor(Theme.popupDim(ctx));
        subView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        subView.setGravity(Gravity.CENTER);
        col.addView(subView, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView hintView = new TextView(ctx);
        hintView.setText(hint);
        hintView.setTextColor(Theme.popupDim(ctx));
        hintView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        hintView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hintLp.topMargin = dp(40);
        col.addView(hintView, hintLp);

        frame.addView(col, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT);
        windows.addView(frame, lp);
        root = frame;
        frame.setFocusable(true);
        frame.setFocusableInTouchMode(true);
        frame.requestFocus();
    }

    void hide() {
        if (root != null && windows != null) {
            try {
                windows.removeView(root);
            } catch (RuntimeException ignored) {
                // Already gone.
            }
        }
        root = null;
    }
}
