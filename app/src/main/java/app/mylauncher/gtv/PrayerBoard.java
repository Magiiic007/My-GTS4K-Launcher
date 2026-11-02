package app.mylauncher.gtv;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import java.util.Calendar;

/**
 * The middle and the right of the home screen. Middle: the prayer card (the six times, each with its iqama, the next
 * prayer, and the city). Right: the big clock, the Gregorian and Hijri dates, and two countdowns (to the next adhan
 * and to the next iqama). Every part can be selected with the remote and opens the matching setting.
 */
final class PrayerBoard {
    /** The middle column (prayer times). */
    final LinearLayout prayers;
    /** The right column (clock, dates, countdowns). */
    final LinearLayout clockColumn;

    private static final int[] ROW_ICONS = {R.drawable.ic_twilight, R.drawable.ic_sunrise, R.drawable.ic_sunny,
        R.drawable.ic_cloud_sun, R.drawable.ic_sunset, R.drawable.ic_crescent};
    /** Row order: Fajr, Sunrise, Dhuhr, Asr, Maghrib, Isha. Maps a row to PrayerCalc's index (-1 for sunrise). */
    private static final int[] ROW_PRAYER = {PrayerCalc.FAJR, -1, PrayerCalc.DHUHR, PrayerCalc.ASR,
        PrayerCalc.MAGHRIB, PrayerCalc.ISHA};

    private final Context ctx;
    private final Runnable changed;
    private final Handler handler = new Handler(Looper.getMainLooper());

    // prayer card
    private final TextView locationText;
    private final LinearLayout[] rows = new LinearLayout[6];
    private final TextView[] rowName = new TextView[6];
    private final TextView[] rowIqama = new TextView[6];
    private final TextView[] rowTime = new TextView[6];
    private final ImageView[] rowIcon = new ImageView[6];
    private final TextView nextBar;

    // clock column
    private final TextClock clock;
    private final TextClock ampm;
    private final TextView weekday, gregAr, gregEn, hijriAr, hijriEn;
    private final TextView adhanName, adhanTime, iqamaName, iqamaTime;

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
    private long sunriseAt;
    private PrayerCalc.Event adhanEvent;
    private PrayerCalc.Event iqamaEvent;
    private boolean fridayToday;

    /**
     * @param pickCity  opens the country and city choice
     * @param openDate  opens the Hijri date adjustment
     * @param openClock opens the device's date and time settings
     * @param changed   run after a prayer or iqama setting changes (new alarm, new numbers)
     */
    PrayerBoard(Context context, Runnable pickCity, Runnable openDate, Runnable openClock, Runnable changed) {
        ctx = context;
        this.changed = changed;

        // ------------------------------------------------------------ the prayer card
        prayers = new LinearLayout(ctx);
        prayers.setOrientation(LinearLayout.VERTICAL);
        prayers.setBackground(Theme.panel(ctx, 22));
        prayers.setPadding(dp(14), dp(10), dp(14), dp(10));

        LinearLayout titleRow = new LinearLayout(ctx);
        titleRow.setOrientation(LinearLayout.VERTICAL);
        titleRow.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView title = text(ctx.getString(R.string.prayer_card_title), 22, Theme.accent(ctx), true);
        title.setGravity(Gravity.CENTER);
        titleRow.addView(title);
        locationText = text("", 13, Theme.textDim(ctx), false);
        locationText.setGravity(Gravity.CENTER);
        locationText.setCompoundDrawablePadding(dp(6));
        locationText.setCompoundDrawablesRelativeWithIntrinsicBounds(
            tinted(R.drawable.ic_location, Theme.accent(ctx), 16), null, null, null);
        locationText.setPadding(dp(10), dp(3), dp(10), dp(3));
        focusable(locationText, 10, pickCity);
        titleRow.addView(locationText);
        prayers.addView(titleRow, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        for (int i = 0; i < 6; i++) {
            final int prayer = ROW_PRAYER[i];
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(8), dp(2), dp(8), dp(2));

            rowIcon[i] = new ImageView(ctx);
            rowIcon[i].setImageResource(ROW_ICONS[i]);
            rowIcon[i].setColorFilter(Theme.accent(ctx));
            row.addView(rowIcon[i], new LinearLayout.LayoutParams(dp(26), dp(26)));

            LinearLayout names = new LinearLayout(ctx);
            names.setOrientation(LinearLayout.VERTICAL);
            rowName[i] = text("", 18, Theme.text(ctx), true);
            rowName[i].setSingleLine(true);
            names.addView(rowName[i]);
            rowIqama[i] = text("", 11, Theme.accent(ctx), false);
            rowIqama[i].setSingleLine(true);
            names.addView(rowIqama[i]);
            LinearLayout.LayoutParams namesLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            namesLp.setMarginStart(dp(10));
            row.addView(names, namesLp);

            rowTime[i] = text("", 17, Theme.pillText(ctx), true);
            rowTime[i].setGravity(Gravity.CENTER);
            rowTime[i].setSingleLine(true);
            rowTime[i].setBackground(Theme.pill(ctx));
            rowTime[i].setPadding(dp(14), dp(4), dp(14), dp(4));
            row.addView(rowTime[i], new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            if (prayer >= 0) {
                focusable(row, 12, new Runnable() {
                    @Override
                    public void run() {
                        TimingDialogs.field(ctx, prayer, PrayerBoard.this.changed);
                    }
                });
            }
            rows[i] = row;
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
            rowLp.topMargin = dp(3);
            prayers.addView(row, rowLp);
        }

        nextBar = text("", 17, Theme.pillText(ctx), true);
        nextBar.setGravity(Gravity.CENTER);
        nextBar.setSingleLine(true);
        nextBar.setBackground(Theme.pill(ctx));
        nextBar.setPadding(dp(12), dp(7), dp(12), dp(7));
        nextBar.setCompoundDrawablePadding(dp(8));
        nextBar.setCompoundDrawablesRelativeWithIntrinsicBounds(
            tinted(R.drawable.ic_mosque, Theme.pillText(ctx), 22), null, null, null);
        focusable(nextBar, 16, new Runnable() {
            @Override
            public void run() {
                TimingDialogs.open(ctx, PrayerBoard.this.changed);
            }
        });
        LinearLayout.LayoutParams nextLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nextLp.topMargin = dp(6);
        prayers.addView(nextBar, nextLp);

        // ------------------------------------------------------------ the clock column
        clockColumn = new LinearLayout(ctx);
        clockColumn.setOrientation(LinearLayout.VERTICAL);

        LinearLayout clockCard = new LinearLayout(ctx);
        clockCard.setOrientation(LinearLayout.HORIZONTAL);
        clockCard.setGravity(Gravity.CENTER);
        clockCard.setBackground(Theme.panel(ctx, 26));
        clockCard.setPadding(dp(14), dp(4), dp(14), dp(4));
        clock = new TextClock(ctx);
        clock.setFormat12Hour("h:mm");
        clock.setFormat24Hour("HH:mm");
        clock.setTextColor(Theme.accent(ctx));
        clock.setTypeface(Typeface.DEFAULT_BOLD);
        clock.setGravity(Gravity.CENTER);
        clock.setAutoSizeTextTypeUniformWithConfiguration(24, 64, 2, TypedValue.COMPLEX_UNIT_SP);
        clock.setMaxLines(1);
        clockCard.addView(clock, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        ampm = new TextClock(ctx);
        ampm.setFormat12Hour("a");
        ampm.setFormat24Hour("a");
        ampm.setTextColor(Theme.text(ctx));
        ampm.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        ampm.setTypeface(Typeface.DEFAULT_BOLD);
        ampm.setPadding(dp(8), 0, 0, 0);
        clockCard.addView(ampm, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        focusable(clockCard, 26, openClock);
        clockColumn.addView(clockCard, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 0.9f));

        LinearLayout dateCard = new LinearLayout(ctx);
        dateCard.setOrientation(LinearLayout.VERTICAL);
        dateCard.setGravity(Gravity.CENTER);
        dateCard.setBackground(Theme.panel(ctx, 22));
        dateCard.setPadding(dp(12), dp(6), dp(12), dp(6));
        weekday = text("", 22, Theme.accent(ctx), true);
        weekday.setGravity(Gravity.CENTER);
        dateCard.addView(weekday);
        gregAr = text("", 17, Theme.text(ctx), true);
        gregAr.setGravity(Gravity.CENTER);
        dateCard.addView(gregAr);
        gregEn = text("", 11, Theme.textDim(ctx), false);
        gregEn.setGravity(Gravity.CENTER);
        dateCard.addView(gregEn);
        hijriAr = text("", 17, Theme.text(ctx), true);
        hijriAr.setGravity(Gravity.CENTER);
        hijriAr.setCompoundDrawablePadding(dp(6));
        hijriAr.setCompoundDrawablesRelativeWithIntrinsicBounds(
            tinted(R.drawable.ic_crescent, Theme.accent(ctx), 20), null, null, null);
        LinearLayout.LayoutParams hAr = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hAr.topMargin = dp(4);
        dateCard.addView(hijriAr, hAr);
        hijriEn = text("", 11, Theme.textDim(ctx), false);
        hijriEn.setGravity(Gravity.CENTER);
        dateCard.addView(hijriEn);
        focusable(dateCard, 22, openDate);
        LinearLayout.LayoutParams dateLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.5f);
        dateLp.topMargin = dp(8);
        clockColumn.addView(dateCard, dateLp);

        LinearLayout counts = new LinearLayout(ctx);
        counts.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout[] boxes = new LinearLayout[2];
        TextView[] names = new TextView[2];
        TextView[] times = new TextView[2];
        int[] labels = {R.string.count_adhan, R.string.count_iqama};
        for (int i = 0; i < 2; i++) {
            LinearLayout box = new LinearLayout(ctx);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setGravity(Gravity.CENTER);
            box.setBackground(Theme.panel(ctx, 18));
            box.setPadding(dp(6), dp(4), dp(6), dp(4));
            TextView label = text(ctx.getString(labels[i]), 11, Theme.textDim(ctx), false);
            label.setGravity(Gravity.CENTER);
            box.addView(label);
            names[i] = text("", 15, Theme.accent(ctx), true);
            names[i].setGravity(Gravity.CENTER);
            names[i].setSingleLine(true);
            box.addView(names[i]);
            times[i] = text("--:--:--", 20, Theme.text(ctx), true);
            times[i].setGravity(Gravity.CENTER);
            times[i].setSingleLine(true);
            box.addView(times[i]);
            focusable(box, 18, new Runnable() {
                @Override
                public void run() {
                    TimingDialogs.open(ctx, PrayerBoard.this.changed);
                }
            });
            boxes[i] = box;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            if (i == 0) lp.setMarginEnd(dp(4));
            else lp.setMarginStart(dp(4));
            counts.addView(box, lp);
        }
        adhanName = names[0];
        adhanTime = times[0];
        iqamaName = names[1];
        iqamaTime = times[1];
        LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        cLp.topMargin = dp(8);
        clockColumn.addView(counts, cLp);
    }

    // ---------------------------------------------------------------- small helpers

    private int dp(float v) {
        return Theme.dp(ctx, v);
    }

    private TextView text(String s, float sp, int colour, boolean bold) {
        TextView t = new TextView(ctx);
        t.setText(s);
        t.setTextColor(colour);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private android.graphics.drawable.Drawable tinted(int res, int colour, int sizeDp) {
        android.graphics.drawable.Drawable d = ctx.getDrawable(res).mutate();
        d.setTint(colour);
        d.setBounds(0, 0, dp(sizeDp), dp(sizeDp));
        return d;
    }

    /** Makes a view selectable with the remote (a ring around it) and clickable. */
    private void focusable(final View v, float radiusDp, final Runnable click) {
        v.setFocusable(true);
        v.setClickable(true);
        final android.graphics.drawable.Drawable ring = Theme.focusRing(ctx, radiusDp);
        v.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean has) {
                view.setForeground(has ? ring : null);
            }
        });
        v.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                click.run();
            }
        });
    }

    // ---------------------------------------------------------------- life and updates

    void start() {
        boolean h24 = DateFormat.is24HourFormat(ctx);
        ampm.setVisibility(h24 ? View.GONE : View.VISIBLE);
        handler.removeCallbacks(tick);
        handler.post(tick);
    }

    void stop() {
        handler.removeCallbacks(tick);
    }

    /** Recalculate everything now (the city or a setting changed). */
    void refresh() {
        cacheKey = null;
        update();
    }

    private static String two(long v) {
        return v < 10 ? "0" + v : String.valueOf(v);
    }

    private static String clockText(long ms) {
        long s = Math.max(0L, ms) / 1000L;
        return two(s / 3600L) + ":" + two((s / 60L) % 60L) + ":" + two(s % 60L);
    }

    /** The first iqama after {@code now} (null if no prayer has an iqama set). */
    private PrayerCalc.Event nextIqama(Cities.City city, long now, int[] adjust, int[] iqama) {
        PrayerCalc.Event best = null;
        for (int d = -1; d <= 1; d++) {
            long[] t = PrayerCalc.day(city, now, d, adjust);
            for (int i = 0; i < 5; i++) {
                if (iqama[i] <= 0) continue;
                long q = t[i] + iqama[i] * 60000L;
                if (q > now && (best == null || q < best.at)) {
                    best = new PrayerCalc.Event(i, q, i == PrayerCalc.DHUHR && PrayerCalc.isFriday(city, t[i]), true);
                }
            }
        }
        return best;
    }

    /** Called every second; the times are only recalculated once a minute or when something changes. */
    void update() {
        updateDates();
        Cities.City city = Cities.selected(ctx);
        if (city == null) {
            locationText.setText(R.string.pick_city_hint);
            for (int i = 0; i < 6; i++) {
                rowName[i].setText(rowLabel(i, false));
                rowTime[i].setText("--:--");
                rowIqama[i].setVisibility(View.GONE);
                rows[i].setAlpha(1f);
                rows[i].setBackground(null);
            }
            nextBar.setText(R.string.pick_city_hint);
            adhanName.setText("");
            iqamaName.setText("");
            adhanTime.setText("--:--:--");
            iqamaTime.setText("--:--:--");
            cacheKey = null;
            return;
        }

        long now = System.currentTimeMillis();
        if (adhanEvent != null && adhanEvent.at <= now) cacheMinute = -1;
        if (iqamaEvent != null && iqamaEvent.at <= now) cacheMinute = -1;
        long minute = now / 60000L;
        if (!city.key().equals(cacheKey) || minute != cacheMinute) {
            cacheKey = city.key();
            cacheMinute = minute;
            int[] adjust = PrayerPrefs.adjust(ctx);
            int[] iqama = PrayerPrefs.iqama(ctx);
            shown = PrayerCalc.day(city, now, 0, adjust);
            sunriseAt = PrayerCalc.sunrise(city, now, 0, adjust);
            adhanEvent = PrayerCalc.nextEvent(city, now, adjust, new int[5]);
            iqamaEvent = nextIqama(city, now, adjust, iqama);
            fridayToday = PrayerCalc.isFriday(city, now);
            if (adhanEvent == null) return;
            // After Isha the next adhan is tomorrow's Fajr: show that time in its row.
            if (adhanEvent.at != shown[adhanEvent.index]) shown[adhanEvent.index] = adhanEvent.at;

            locationText.setText(ctx.getString(R.string.location_format,
                city.name(isArabic()), ctx.getString("EG".equals(city.country) ? R.string.egypt : R.string.saudi)));
            for (int i = 0; i < 6; i++) {
                int p = ROW_PRAYER[i];
                boolean isNext = p >= 0 && p == adhanEvent.index;
                boolean jumuah = p == PrayerCalc.DHUHR && (isNext ? adhanEvent.friday : fridayToday);
                rowName[i].setText(rowLabel(i, jumuah));
                long at = p >= 0 ? shown[p] : sunriseAt;
                rowTime[i].setText(PrayerCalc.time(ctx, city, at));
                if (p >= 0 && iqama[p] > 0) {
                    rowIqama[i].setText(ctx.getString(R.string.iqama_at,
                        PrayerCalc.time(ctx, city, shown[p] + iqama[p] * 60000L)));
                    rowIqama[i].setVisibility(View.VISIBLE);
                } else {
                    rowIqama[i].setVisibility(View.GONE);
                }
                rows[i].setAlpha(at <= now && !isNext ? 0.5f : 1f);
                if (isNext) {
                    GradientDrawable hl = new GradientDrawable();
                    hl.setColor((Theme.accent(ctx) & 0x00FFFFFF) | 0x40000000);
                    hl.setCornerRadius(dp(12));
                    hl.setStroke(dp(2), Theme.accent(ctx));
                    rows[i].setBackground(hl);
                } else {
                    rows[i].setBackground(null);
                }
            }
            nextBar.setText(ctx.getString(R.string.next_prayer,
                PrayerCalc.name(ctx, adhanEvent.index, adhanEvent.friday)));
            adhanName.setText(PrayerCalc.name(ctx, adhanEvent.index, adhanEvent.friday));
            if (iqamaEvent != null) {
                iqamaName.setText(PrayerCalc.name(ctx, iqamaEvent.index, iqamaEvent.friday));
            } else {
                iqamaName.setText(R.string.count_none);
            }
        }
        if (adhanEvent != null) adhanTime.setText(clockText(adhanEvent.at - now));
        iqamaTime.setText(iqamaEvent != null ? clockText(iqamaEvent.at - now) : "--:--:--");
    }

    private String rowLabel(int row, boolean jumuah) {
        int p = ROW_PRAYER[row];
        if (p < 0) return ctx.getString(R.string.p_sunrise);
        return PrayerCalc.name(ctx, p, jumuah);
    }

    private boolean isArabic() {
        return "ar".equals(java.util.Locale.getDefault().getLanguage());
    }

    private String lastDate = "";

    private void updateDates() {
        Calendar today = Hijri.today(ctx);
        int[] h = Hijri.of(today, Hijri.adjust(ctx));
        String key = today.get(Calendar.YEAR) + "-" + today.get(Calendar.DAY_OF_YEAR) + "-" + Hijri.adjust(ctx);
        if (key.equals(lastDate)) return;
        lastDate = key;
        weekday.setText(Hijri.weekdayAr(today));
        gregAr.setText(Hijri.gregorianAr(today));
        gregEn.setText(Hijri.gregorianEn(today));
        hijriAr.setText(Hijri.hijriAr(h));
        hijriEn.setText(Hijri.hijriEn(h));
    }

    /** Forces the dates to be redrawn (the Hijri adjustment changed). */
    void refreshDates() {
        lastDate = "";
        updateDates();
    }
}
