package app.mylauncher.gtv;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * The prayer strip under the header: the next prayer with a live countdown, the five times of the day, and two
 * small buttons (test the adhan in a minute; allow the popup over other apps while that is not yet allowed).
 */
final class PrayerPanel {
    final LinearLayout view;

    private final Context ctx;
    private final TextView nextLine;
    private final TextView testBtn;
    private final TextView permBtn;
    private final TextView[] names = new TextView[5];
    private final TextView[] times = new TextView[5];
    private final LinearLayout[] cells = new LinearLayout[5];
    private final GradientDrawable highlight = new GradientDrawable();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            update();
            handler.postDelayed(this, 1000L);
        }
    };

    private String cacheKey;
    private long cacheMinute = -1;
    private long[] shown = new long[5];
    private PrayerCalc.Next next;
    private boolean fridayToday;

    PrayerPanel(Context context) {
        ctx = context;
        highlight.setColor(Color.parseColor("#33E0B040"));
        highlight.setCornerRadius(dp(12));
        highlight.setStroke(dp(2), Color.parseColor("#FFE0B040"));

        view = new LinearLayout(ctx);
        view.setOrientation(LinearLayout.VERTICAL);

        LinearLayout top = new LinearLayout(ctx);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        nextLine = new TextView(ctx);
        nextLine.setTextColor(Color.WHITE);
        nextLine.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        top.addView(nextLine, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        permBtn = button(R.string.perm_btn, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                askOverlayPermission();
            }
        });
        testBtn = button(R.string.test_btn, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Scheduler.scheduleTest(ctx);
                Toast.makeText(ctx, R.string.test_scheduled, Toast.LENGTH_LONG).show();
            }
        });
        top.addView(permBtn);
        top.addView(testBtn);
        view.addView(top, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout strip = new LinearLayout(ctx);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < 5; i++) {
            LinearLayout cell = new LinearLayout(ctx);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER);
            cell.setPadding(dp(8), dp(10), dp(8), dp(10));

            names[i] = new TextView(ctx);
            names[i].setTextColor(Color.parseColor("#FFB8C4D0"));
            names[i].setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
            names[i].setGravity(Gravity.CENTER);
            cell.addView(names[i]);

            times[i] = new TextView(ctx);
            times[i].setTextColor(Color.WHITE);
            times[i].setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
            times[i].setGravity(Gravity.CENTER);
            cell.addView(times[i]);

            cells[i] = cell;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMargins(dp(4), dp(8), dp(4), 0);
            strip.addView(cell, lp);
        }
        view.addView(strip, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, value, ctx.getResources().getDisplayMetrics()));
    }

    private TextView button(int text, View.OnClickListener click) {
        TextView b = new TextView(ctx);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(14), dp(8), dp(14), dp(8));
        b.setBackgroundResource(R.drawable.weather_bg);
        b.setFocusable(true);
        b.setClickable(true);
        b.setOnClickListener(click);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMarginStart(dp(12));
        b.setLayoutParams(lp);
        return b;
    }

    /** The system screen for "display over other apps" if this TV has one; otherwise instructions. */
    private void askOverlayPermission() {
        try {
            ctx.startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + ctx.getPackageName())));
        } catch (ActivityNotFoundException | SecurityException e) {
            new AlertDialog.Builder(ctx)
                .setTitle(R.string.overlay_title)
                .setMessage(R.string.overlay_help)
                .setPositiveButton(android.R.string.ok, null)
                .show();
        }
    }

    void start() {
        handler.removeCallbacks(tick);
        handler.post(tick);
        permBtn.setVisibility(Settings.canDrawOverlays(ctx) ? View.GONE : View.VISIBLE);
    }

    void stop() {
        handler.removeCallbacks(tick);
    }

    /** Called every second; the five times are only recalculated once a minute or when the city changes. */
    void update() {
        Cities.City city = Cities.selected(ctx);
        if (city == null) {
            nextLine.setText(R.string.pick_city_hint);
            for (int i = 0; i < 5; i++) {
                names[i].setText(PrayerCalc.name(ctx, i, false));
                times[i].setText("--:--");
                cells[i].setAlpha(1f);
                cells[i].setBackground(null);
            }
            cacheKey = null;
            return;
        }

        long now = System.currentTimeMillis();
        if (next != null && next.at <= now) cacheMinute = -1;
        long minute = now / 60000L;
        if (!city.key().equals(cacheKey) || minute != cacheMinute) {
            cacheKey = city.key();
            cacheMinute = minute;
            shown = PrayerCalc.day(city, now, 0);
            next = PrayerCalc.next(city, now);
            fridayToday = PrayerCalc.isFriday(city, now);
            // After Isha the next prayer is tomorrow's Fajr: show that time in its cell.
            if (next.at != shown[next.index]) shown[next.index] = next.at;
            for (int i = 0; i < 5; i++) {
                boolean isNext = i == next.index;
                boolean jumuah = i == PrayerCalc.DHUHR && (isNext ? next.friday : fridayToday);
                names[i].setText(PrayerCalc.name(ctx, i, jumuah));
                times[i].setText(PrayerCalc.time(ctx, city, shown[i]));
                cells[i].setAlpha(shown[i] <= now && !isNext ? 0.45f : 1f);
                cells[i].setBackground(isNext ? highlight : null);
            }
        }
        long left = Math.max(0L, next.at - now);
        nextLine.setText(ctx.getString(R.string.next_in,
            PrayerCalc.name(ctx, next.index, next.friday), PrayerCalc.countdown(left)));
    }
}
