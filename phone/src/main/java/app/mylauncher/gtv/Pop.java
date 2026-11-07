package app.mylauncher.gtv;

import android.app.Activity;
import android.app.Dialog;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** A pop-up in the app's own look (the same background and colours as the screens), used instead of the system dialog. */
final class Pop {
    interface Pick {
        void on(int which);
    }

    private final Activity a;
    private final int bg1, bg2, gold, cream, dim;
    private String title, message, okText, cancelText;
    private View content;
    private String[] items;
    private int checked = -1;
    private Pick pick;
    private boolean[] multi;
    private Runnable ok;

    Pop(Activity a) {
        this.a = a;
        boolean night = (a.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
            == Configuration.UI_MODE_NIGHT_YES;
        if (night) {
            bg1 = 0xFF091230; bg2 = 0xFF142453; gold = 0xFFDCB860; cream = 0xFFF1E8D0; dim = 0xFFB4BDDB;
        } else {
            bg1 = 0xFF062B1F; bg2 = 0xFF0D4130; gold = 0xFFE8B84A; cream = 0xFFF6ECD2; dim = 0xFFB9C9BD;
        }
    }

    Pop title(String t) {
        title = t;
        return this;
    }

    Pop message(String m) {
        message = m;
        return this;
    }

    Pop view(View v) {
        content = v;
        return this;
    }

    /** One choice from a list: picking closes the pop-up. */
    Pop single(String[] items, int checked, Pick p) {
        this.items = items;
        this.checked = checked;
        this.pick = p;
        return this;
    }

    /** Several choices; the array is updated as they are ticked. */
    Pop multi(String[] items, boolean[] on) {
        this.items = items;
        this.multi = on;
        return this;
    }

    Pop ok(String text, Runnable r) {
        okText = text;
        ok = r;
        return this;
    }

    Pop cancel(String text) {
        cancelText = text;
        return this;
    }

    int goldColor() {
        return gold;
    }

    int creamColor() {
        return cream;
    }

    private boolean cancelable = true;

    /** false: the pop-up cannot be closed by tapping outside it or with the back button (for a download in progress). */
    Pop cancelable(boolean c) {
        cancelable = c;
        return this;
    }

    private int dp(int v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, a.getResources().getDisplayMetrics()));
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(a);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    Dialog show() {
        final Dialog d = new Dialog(a);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        d.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        final float radius = dp(22);
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[] {bg1, bg2});
        g.setCornerRadius(radius);
        g.setStroke(dp(1), (gold & 0x00FFFFFF) | 0x99000000);
        Drawable stars = new Deco.Crack(a.getResources(), (cream & 0x00FFFFFF) | 0x66000000);
        box.setBackground(new LayerDrawable(new Drawable[] {g, stars}));
        box.setClipToOutline(true);
        box.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radius);
            }
        });
        box.setPadding(dp(18), dp(16), dp(18), dp(8));
        if (title != null) box.addView(text(title, 20, gold, true));
        if (message != null) {
            TextView m = text(message, 15, cream, false);
            m.setLineSpacing(0, 1.2f);
            LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(-1, -2);
            mp.topMargin = dp(8);
            box.addView(m, mp);
        }
        if (content != null) {
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
            cp.topMargin = dp(10);
            box.addView(content, cp);
        }
        if (items != null) {
            LinearLayout list = new LinearLayout(a);
            list.setOrientation(LinearLayout.VERTICAL);
            for (int i = 0; i < items.length; i++) {
                final int idx = i;
                final TextView row = text((multi != null ? (multi[i] ? "☑  " : "☐  ") : (i == checked ? "●  " : "○  ")) + items[i],
                    16, cream, false);
                row.setPadding(dp(8), dp(12), dp(8), dp(12));
                row.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (multi != null) {
                            multi[idx] = !multi[idx];
                            row.setText((multi[idx] ? "☑  " : "☐  ") + items[idx]);
                        } else {
                            d.dismiss();
                            if (pick != null) pick.on(idx);
                        }
                    }
                });
                list.addView(row);
            }
            ScrollView sv = new ScrollView(a);
            sv.addView(list, new ViewGroup.LayoutParams(-1, -2));
            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
            sp.topMargin = dp(6);
            box.addView(sv, sp);
        }
        LinearLayout buttons = new LinearLayout(a);
        buttons.setGravity(Gravity.CENTER_VERTICAL);
        if (okText != null) {
            TextView b = text(okText, 16, gold, true);
            b.setPadding(dp(18), dp(12), dp(18), dp(12));
            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    d.dismiss();
                    if (ok != null) ok.run();
                }
            });
            buttons.addView(b);
        }
        if (cancelText != null) {
            TextView b = text(cancelText, 16, dim, true);
            b.setPadding(dp(18), dp(12), dp(18), dp(12));
            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    d.dismiss();
                }
            });
            buttons.addView(b);
        }
        if (okText != null || cancelText != null) {
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, -2);
            bp.topMargin = dp(6);
            box.addView(buttons, bp);
        } else {
            box.setPadding(dp(18), dp(16), dp(18), dp(14));
        }
        d.setContentView(box);
        d.setCancelable(cancelable);
        Window w = d.getWindow();
        w.setLayout(Math.min(a.getResources().getDisplayMetrics().widthPixels - dp(36), dp(520)), ViewGroup.LayoutParams.WRAP_CONTENT);
        w.setWindowAnimations(R.style.FxDialog);
        w.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        d.show();
        return d;
    }
}
