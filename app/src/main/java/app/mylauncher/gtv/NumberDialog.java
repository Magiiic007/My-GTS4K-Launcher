package app.mylauncher.gtv;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Picks a number of minutes with the remote: Up and Down change it by one (hold to run), or use the - and +
 * buttons; Save keeps it. Nothing needs typing.
 */
final class NumberDialog {
    private NumberDialog() {}

    interface OnSave {
        void saved(int value);
    }

    /**
     * @param signed   show a plus sign for positive values (for an adjustment)
     * @param zeroText what to show for 0 ("no adjustment", "no iqama")
     */
    static void show(Context c, String title, int min, int max, int start,
                     boolean signed, String zeroText, OnSave save) {
        show(c, title, min, max, start, c.getString(R.string.minutes_plain), c.getString(R.string.minutes_signed),
            signed, zeroText, save);
    }

    /** Like the above, with the wording of the number given as format texts (for example "%1$d days"). */
    static void show(final Context c, String title, final int min, final int max, int start,
                     final String plainFormat, final String signedFormat,
                     final boolean signed, final String zeroText, final OnSave save) {
        final int[] value = {Math.max(min, Math.min(max, start))};
        final TextView shown = new TextView(c);
        shown.setTextColor(Theme.kids(c) ? Color.parseColor("#FF7A3347") : Color.WHITE);
        shown.setTextSize(TypedValue.COMPLEX_UNIT_SP, 36);
        shown.setGravity(Gravity.CENTER);

        final Runnable refresh = new Runnable() {
            @Override
            public void run() {
                int v = value[0];
                shown.setText(v == 0 && zeroText != null ? zeroText
                    : String.format(java.util.Locale.getDefault(), signed ? signedFormat : plainFormat, v));
            }
        };
        refresh.run();

        final int pad = dp(c, 24);
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, pad, pad, pad);
        box.addView(shown, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView tip = new TextView(c);
        tip.setText(R.string.number_tip);
        tip.setTextColor(Color.parseColor("#FF7F8C99"));
        tip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tip.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tipLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tipLp.topMargin = dp(c, 8);
        box.addView(tip, tipLp);

        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowLp.topMargin = dp(c, 16);
        box.addView(row, rowLp);

        final Dialog[] dialog = new Dialog[1];
        final TextView minus = button(c, "−");
        final TextView plus = button(c, "+");
        final TextView ok = button(c, c.getString(R.string.save));
        final TextView cancel = button(c, c.getString(R.string.cancel));
        row.addView(minus);
        row.addView(plus);
        row.addView(ok);
        row.addView(cancel);

        minus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                value[0] = Math.max(min, value[0] - 1);
                refresh.run();
            }
        });
        plus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                value[0] = Math.min(max, value[0] + 1);
                refresh.run();
            }
        });
        ok.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Menu.Box before = Menu.Box.lastShown;
                dialog[0].dismiss();
                save.saved(value[0]);
                if (Menu.Box.lastShown == before && dialog[0] instanceof Menu.Box) ((Menu.Box) dialog[0]).reopenParent();
            }
        });
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog[0].cancel();
            }
        });

        dialog[0] = new Menu.Builder(c).setTitle(title).setView(box).create();
        dialog[0].setOnKeyListener(new DialogInterface.OnKeyListener() {
            @Override
            public boolean onKey(DialogInterface d, int keyCode, KeyEvent e) {
                if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                    value[0] = Math.min(max, value[0] + 1);
                    refresh.run();
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    value[0] = Math.max(min, value[0] - 1);
                    refresh.run();
                    return true;
                }
                return false;
            }
        });
        dialog[0].show();
        ok.requestFocus();
    }

    private static TextView button(Context c, String text) {
        TextView b = new TextView(c);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(c, 18), dp(c, 10), dp(c, 18), dp(c, 10));
        b.setBackgroundResource(R.drawable.weather_bg);
        b.setFocusable(true);
        b.setClickable(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMarginStart(dp(c, 8));
        lp.setMarginEnd(dp(c, 8));
        b.setLayoutParams(lp);
        return b;
    }

    private static int dp(Context c, int value) {
        return Math.round(TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, value, c.getResources().getDisplayMetrics()));
    }
}
