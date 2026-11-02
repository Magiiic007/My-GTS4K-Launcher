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

/**
 * The prayer strip under the header: what rings next (an adhan or an iqama) with a live countdown, the five times
 * of the day with each iqama time under it, and two small buttons: the adhan and iqama settings, and (until it is
 * allowed) the permission for the popup over other apps.
 */
final class PrayerPanel {
    final LinearLayout view;

    private final Context ctx;
    private final TextView nextLine;
    private final TextView settingsBtn;
    private final TextView permBtn;
    private final TextView[] names = new TextView[5];
    private final TextView[] times = new TextView[5];
    private final TextView[] iqamas = new TextView[5];
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
    /** Run after any setting changes: new alarm, new numbers on screen. */
    private final Runnable changed = new Runnable() {
        @Override
        public void run() {
            Scheduler.scheduleNext(ctx);
            refresh();
        }
    };

    private String cacheKey;
    private long cacheMinute = -1;
    private long[] shown = new long[5];
    private PrayerCalc.Event event;
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
        settingsBtn = button(R.string.settings_btn, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                TimingDialogs.open(ctx, changed);
            }
        });
        top.addView(permBtn);
        top.addView(settingsBtn);
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

            iqamas[i] = new TextView(ctx);
            iqamas[i].setTextColor(Color.parseColor("#FFE0B040"));
            iqamas[i].setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            iqamas[i].setGravity(Gravity.CENTER);
            cell.addView(iqamas[i]);

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

    /** Recalculate everything now (the city or a setting changed). */
    void refresh() {
        cacheKey = null;
        update();
    }

    /** Called every second; the times are only recalculated once a minute or when something changes. */
    void update() {
        Cities.City city = Cities.selected(ctx);
        if (city == null) {
            nextLine.setText(R.string.pick_city_hint);
            for (int i = 0; i < 5; i++) {
                names[i].setText(PrayerCalc.name(ctx, i, false));
                times[i].setText("--:--");
                iqamas[i].setVisibility(View.GONE);
                cells[i].setAlpha(1f);
                cells[i].setBackground(null);
            }
            cacheKey = null;
            return;
        }

        long now = System.currentTimeMillis();
        if (event != null && event.at <= now) cacheMinute = -1;
        long minute = now / 60000L;
        if (!city.key().equals(cacheKey) || minute != cacheMinute) {
            cacheKey = city.key();
            cacheMinute = minute;
            int[] adjust = PrayerPrefs.adjust(ctx);
            int[] iqama = PrayerPrefs.iqama(ctx);
            shown = PrayerCalc.day(city, now, 0, adjust);
            event = PrayerCalc.nextEvent(city, now, adjust, iqama);
            fridayToday = PrayerCalc.isFriday(city, now);
            if (event == null) return;
            // After Isha the next adhan is tomorrow's Fajr: show that time in its cell.
            if (!event.iqama && event.at != shown[event.index]) shown[event.index] = event.at;
            for (int i = 0; i < 5; i++) {
                boolean isNext = i == event.index;
                boolean jumuah = i == PrayerCalc.DHUHR && (isNext ? event.friday : fridayToday);
                names[i].setText(PrayerCalc.name(ctx, i, jumuah));
                times[i].setText(PrayerCalc.time(ctx, city, shown[i]));
                if (iqama[i] > 0) {
                    iqamas[i].setText(ctx.getString(R.string.iqama_at,
                        PrayerCalc.time(ctx, city, shown[i] + iqama[i] * 60000L)));
                    iqamas[i].setVisibility(View.VISIBLE);
                } else {
                    iqamas[i].setVisibility(View.GONE);
                }
                cells[i].setAlpha(shown[i] <= now && !isNext ? 0.45f : 1f);
                cells[i].setBackground(isNext ? highlight : null);
            }
        }
        if (event == null) return;
        long left = Math.max(0L, event.at - now);
        nextLine.setText(ctx.getString(event.iqama ? R.string.iqama_in : R.string.next_in,
            PrayerCalc.name(ctx, event.index, event.friday), PrayerCalc.countdown(left)));
    }
}
