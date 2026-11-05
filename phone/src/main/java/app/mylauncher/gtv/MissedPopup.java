package app.mylauncher.gtv;

import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * "أقم الصلاة": a card over whatever app is open that lists the prayers whose time has come but that are not yet
 * marked as prayed. It stays until the OK button is pressed, and pauses the video or audio that is playing.
 */
final class MissedPopup {
    private static View shown;
    private static WindowManager wm;

    private MissedPopup() {
    }

    private static int dp(Context c, int v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics()));
    }

    /** Pauses what is playing (video, audio) by a media key, which most players obey. */
    private static void pauseMedia(Context c) {
        try {
            AudioManager am = c.getSystemService(AudioManager.class);
            // Only other apps' sound: the radio and the reciters in this app are left alone, as the adhan does.
            if (am == null || RadioService.on() || AdhanService.playing || !am.isMusicActive()) return;
            long t = SystemClock.uptimeMillis();
            am.dispatchMediaKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE, 0));
            am.dispatchMediaKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE, 0));
        } catch (RuntimeException ignored) {
            // a player that ignores the key keeps playing
        }
    }

    /** {@code idx} are the missed prayers; {@code day} is a time inside that day (to mark them on the right day). */
    static boolean show(Context ctx, final int[] idx, final String[] names, final String[] times, final long[] at) {
        final Context c = ctx.getApplicationContext();
        if (!Settings.canDrawOverlays(c)) return false;
        try {
            hide();
            pauseMedia(c);
            wm = c.getSystemService(WindowManager.class);
            final int gold = 0xFFE8B84A;
            final int cream = 0xFFF4EBD0;

            FrameLayout root = new FrameLayout(c);
            root.setBackgroundColor(0xC8000000);
            root.setClickable(true); // swallows touches: only the OK button closes it

            LinearLayout card = new LinearLayout(c);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            card.setPadding(dp(c, 20), dp(c, 18), dp(c, 20), dp(c, 16));
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(0xFF0B2A4A);
            bg.setCornerRadius(dp(c, 22));
            bg.setStroke(dp(c, 2), gold);
            card.setBackground(bg);
            card.setClickable(true);

            TextView title = new TextView(c);
            title.setText("أَقِمِ الصَّلَاةَ");
            title.setTextColor(gold);
            title.setTextSize(24);
            title.setTypeface(Typeface.DEFAULT_BOLD);
            title.setGravity(Gravity.CENTER);
            card.addView(title, new LinearLayout.LayoutParams(-1, -2));

            TextView sub = new TextView(c);
            sub.setText(idx.length == 1 ? "لم تعلّم هذه الصلاة بعد:" : "لم تعلّم هذه الصلوات بعد:");
            sub.setTextColor(0xFFB8C2DC);
            sub.setTextSize(15);
            sub.setGravity(Gravity.CENTER);
            sub.setPadding(0, dp(c, 8), 0, dp(c, 8));
            card.addView(sub, new LinearLayout.LayoutParams(-1, -2));

            ScrollView sv = new ScrollView(c);
            LinearLayout list = new LinearLayout(c);
            list.setOrientation(LinearLayout.VERTICAL);
            for (int k = 0; k < idx.length; k++) {
                final int p = idx[k];
                final long when = at[k];
                final TextView row = new TextView(c);
                row.setText(names[k] + "  ·  " + times[k] + "      اضغط إن صلّيتها ✓");
                row.setTextColor(cream);
                row.setTextSize(18);
                row.setGravity(Gravity.CENTER);
                row.setPadding(dp(c, 8), dp(c, 12), dp(c, 8), dp(c, 12));
                GradientDrawable rb = new GradientDrawable();
                rb.setColor(0x26FFFFFF);
                rb.setCornerRadius(dp(c, 14));
                row.setBackground(rb);
                row.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Track.toggle(c, when, p);
                        row.setText("✓  " + row.getText().toString().split("  ·  ")[0] + "  ·  تم تعليمها");
                        row.setTextColor(gold);
                        row.setOnClickListener(null);
                    }
                });
                LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
                rp.topMargin = dp(c, 8);
                list.addView(row, rp);
            }
            sv.addView(list);
            card.addView(sv, new LinearLayout.LayoutParams(-1, -2));

            TextView ok = new TextView(c);
            ok.setText("OK");
            ok.setTextColor(0xFF0B2A4A);
            ok.setTextSize(18);
            ok.setTypeface(Typeface.DEFAULT_BOLD);
            ok.setGravity(Gravity.CENTER);
            GradientDrawable okBg = new GradientDrawable();
            okBg.setColor(gold);
            okBg.setCornerRadius(dp(c, 14));
            ok.setBackground(okBg);
            ok.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    hide();
                }
            });
            LinearLayout.LayoutParams op = new LinearLayout.LayoutParams(-1, dp(c, 48));
            op.topMargin = dp(c, 16);
            card.addView(ok, op);

            FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER);
            cp.leftMargin = dp(c, 20);
            cp.rightMargin = dp(c, 20);
            root.addView(card, cp);

            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(-1, -1,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
            wm.addView(root, lp);
            shown = root;
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    static boolean isShown() {
        return shown != null;
    }

    static void hide() {
        if (shown != null && wm != null) {
            try {
                wm.removeView(shown);
            } catch (RuntimeException ignored) {
                // already gone
            }
        }
        shown = null;
    }
}
