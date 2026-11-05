package app.mylauncher.gtv;

import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** The dua after the adhan as a card over whatever app is open. The button, or a tap anywhere outside the card, closes it. */
final class DuaPopup {
    private static final Handler H = new Handler(Looper.getMainLooper());
    private static View shown;
    private static WindowManager wm;

    private DuaPopup() {
    }

    static final String TEXT = "اللَّهُمَّ رَبَّ هَذِهِ الدَّعْوَةِ التَّامَّةِ، وَالصَّلَاةِ الْقَائِمَةِ، آتِ مُحَمَّدًا الْوَسِيلَةَ وَالْفَضِيلَةَ، "
        + "وَابْعَثْهُ مَقَامًا مَحْمُودًا الَّذِي وَعَدْتَهُ.";
    static final String HINT = "ثم صلِّ على النبي ﷺ، وادعُ بما شئت، فالدعاء بين الأذان والإقامة لا يُرَدّ.";

    private static int dp(Context c, int v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics()));
    }

    /** Returns false when it cannot be shown (no permission), so the caller can use a notification instead. */
    static boolean show(Context ctx) {
        final Context c = ctx.getApplicationContext();
        if (!Settings.canDrawOverlays(c)) return false;
        try {
            hide();
            wm = c.getSystemService(WindowManager.class);
            final int gold = 0xFFE8B84A;
            final int cream = 0xFFF4EBD0;

            FrameLayout root = new FrameLayout(c);
            root.setBackgroundColor(0xB0000000);
            root.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    hide();
                }
            });

            LinearLayout card = new LinearLayout(c);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            card.setPadding(dp(c, 20), dp(c, 18), dp(c, 20), dp(c, 16));
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(0xFF0B2A4A);
            bg.setCornerRadius(dp(c, 22));
            bg.setStroke(dp(c, 2), gold);
            card.setBackground(bg);
            card.setClickable(true); // taps inside the card do not close it

            TextView title = new TextView(c);
            title.setText("دعاء بعد الأذان");
            title.setTextColor(gold);
            title.setTextSize(20);
            title.setTypeface(Typeface.DEFAULT_BOLD);
            title.setGravity(Gravity.CENTER);
            card.addView(title, new LinearLayout.LayoutParams(-1, -2));

            ScrollView sv = new ScrollView(c);
            LinearLayout inner = new LinearLayout(c);
            inner.setOrientation(LinearLayout.VERTICAL);
            TextView dua = new TextView(c);
            dua.setText(TEXT);
            dua.setTextColor(cream);
            dua.setTextSize(22);
            dua.setLineSpacing(0, 1.3f);
            dua.setGravity(Gravity.CENTER);
            dua.setPadding(0, dp(c, 14), 0, dp(c, 10));
            inner.addView(dua, new LinearLayout.LayoutParams(-1, -2));
            TextView hint = new TextView(c);
            hint.setText(HINT);
            hint.setTextColor(0xFFB8C2DC);
            hint.setTextSize(14);
            hint.setGravity(Gravity.CENTER);
            inner.addView(hint, new LinearLayout.LayoutParams(-1, -2));
            sv.addView(inner);
            card.addView(sv, new LinearLayout.LayoutParams(-1, -2));

            TextView ok = new TextView(c);
            ok.setText("تم");
            ok.setTextColor(0xFF0B2A4A);
            ok.setTextSize(17);
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
            LinearLayout.LayoutParams op = new LinearLayout.LayoutParams(-1, dp(c, 46));
            op.topMargin = dp(c, 14);
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
            H.postDelayed(new Runnable() {
                @Override
                public void run() {
                    hide();
                }
            }, 10 * 60_000L);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    static void hide() {
        H.removeCallbacksAndMessages(null);
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
