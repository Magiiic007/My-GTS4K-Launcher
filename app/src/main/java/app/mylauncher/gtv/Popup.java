package app.mylauncher.gtv;

import android.content.Context;
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
import android.widget.TextView;

/**
 * The popup over whatever is playing: used by the adhan, the iqama and the reminders. It is the dome from the design of
 * the look chosen on the home screen (green and gold, or pink) on a tinted screen. OK or Back closes it (the volume
 * keys still work).
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
        frame.setBackgroundColor(Theme.scrim(ctx));

        // The dome from the design: a top piece (arch, ornaments), a strip that stretches to the height the words
        // need, and a bottom piece. Sizes are scaled from the pictures (689 wide, 395 high top, 57 high bottom).
        final boolean kids = Theme.kids(ctx);
        android.util.DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
        final int pw = Math.min(Math.round(dm.widthPixels * 0.42f), Math.round(dm.heightPixels * 0.95f * 689f / 760f));
        final float sc = pw / 689f;
        final int topH = Math.round(395 * sc), botH = Math.round(57 * sc);
        final int ph = Math.max(topH + botH + Math.round(120 * sc), Math.round(dm.heightPixels * 0.82f));

        FrameLayout panel = new FrameLayout(ctx);
        ImageView top = new ImageView(ctx);
        top.setImageResource(kids ? R.drawable.pop_top_kids : R.drawable.pop_top_royal);
        top.setScaleType(ImageView.ScaleType.FIT_XY);
        panel.addView(top, new FrameLayout.LayoutParams(pw, topH, Gravity.TOP));
        ImageView mid = new ImageView(ctx);
        mid.setImageResource(kids ? R.drawable.pop_mid_kids : R.drawable.pop_mid_royal);
        mid.setScaleType(ImageView.ScaleType.FIT_XY);
        FrameLayout.LayoutParams midLp = new FrameLayout.LayoutParams(pw, ph - topH - botH, Gravity.TOP);
        midLp.topMargin = topH - 1;
        panel.addView(mid, midLp);
        ImageView bot = new ImageView(ctx);
        bot.setImageResource(kids ? R.drawable.pop_bot_kids : R.drawable.pop_bot_royal);
        bot.setScaleType(ImageView.ScaleType.FIT_XY);
        panel.addView(bot, new FrameLayout.LayoutParams(pw, botH, Gravity.BOTTOM));

        ImageView icon = new ImageView(ctx);
        icon.setImageResource(R.drawable.ic_mosque);
        icon.setColorFilter(Theme.popTitle(ctx));
        FrameLayout.LayoutParams iLp = new FrameLayout.LayoutParams(Math.round(64 * sc), Math.round(64 * sc),
            Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        iLp.topMargin = Math.round(163 * sc);
        panel.addView(icon, iLp);

        TextView name = new TextView(ctx);
        name.setText(title);
        name.setTextColor(Theme.popTitle(ctx));
        name.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        name.setGravity(Gravity.CENTER);
        name.setIncludeFontPadding(false);
        name.setMaxLines(1);
        name.setAutoSizeTextTypeUniformWithConfiguration(Math.round(18 * sc), Math.round(50 * sc),
            1, TypedValue.COMPLEX_UNIT_PX);
        FrameLayout.LayoutParams nLp = new FrameLayout.LayoutParams(Math.round(470 * sc), Math.round(66 * sc),
            Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        nLp.topMargin = Math.round(232 * sc);
        panel.addView(name, nLp);

        TextView subView = new TextView(ctx);
        subView.setText(sub);
        subView.setTextColor(Theme.popSub(ctx));
        subView.setTextSize(TypedValue.COMPLEX_UNIT_PX, 32 * sc);
        subView.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams sLp = new FrameLayout.LayoutParams(Math.round(520 * sc),
            ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        sLp.topMargin = Math.round(360 * sc);
        panel.addView(subView, sLp);

        TextView hintView = new TextView(ctx);
        hintView.setText(hint);
        hintView.setTextColor(Theme.popHint(ctx));
        hintView.setTextSize(TypedValue.COMPLEX_UNIT_PX, 22 * sc);
        hintView.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams hLp = new FrameLayout.LayoutParams(Math.round(520 * sc),
            ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        hLp.bottomMargin = Math.round(78 * sc);
        panel.addView(hintView, hLp);

        frame.addView(panel, new FrameLayout.LayoutParams(pw, ph, Gravity.CENTER));

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
