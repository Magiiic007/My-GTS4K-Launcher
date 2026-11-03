package app.mylauncher.gtv;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * The middle and the right of the home screen, placed on the design's canvas (see Stage). Middle: the six prayer
 * times, each with its iqama, the next prayer and the city. Right: the big clock, the Gregorian and Hijri dates and
 * two countdowns (to the next adhan and to the next iqama). Every part can be selected with the remote and opens the
 * matching setting.
 */
final class PrayerBoard {
    private static final int[] ROW_ICONS = {R.drawable.ic_twilight, R.drawable.ic_sunrise, R.drawable.ic_sunny,
        R.drawable.ic_cloud_sun, R.drawable.ic_sunset, R.drawable.ic_crescent};
    /** Row order: Fajr, Sunrise, Dhuhr, Asr, Maghrib, Isha. Maps a row to PrayerCalc's index (-1 for sunrise). */
    private static final int[] ROW_PRAYER = {PrayerCalc.FAJR, -1, PrayerCalc.DHUHR, PrayerCalc.ASR,
        PrayerCalc.MAGHRIB, PrayerCalc.ISHA};

    private final Context ctx;
    private final Runnable changed;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final boolean kids;

    // prayer card
    private final TextView locationText;
    private final Flag flag;
    private final View[] rows = new View[6];
    private final TextView[] rowName = new TextView[6];
    private final TextView[] rowIqama = new TextView[6];
    private final TextView[] rowTime = new TextView[6];
    private final TextView nextBar;

    // clock column
    private final Stage.Outlined clock;
    private final TextView ampm;
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
    PrayerBoard(Context context, FrameLayout stage, Runnable pickCity, Runnable openDate, Runnable openClock,
                Runnable changed) {
        ctx = context;
        this.changed = changed;
        kids = Theme.kids(ctx);
        Geo g = Geo.of(ctx);

        // ------------------------------------------------------------ the prayer card
        // The title is part of the picture; the city goes in the band under it.
        flag = new Flag(ctx);
        Stage.add(stage, flag, 372, 156, 436, 196);

        locationText = Stage.text(ctx, "", 19, kids ? Color.parseColor("#FFFFF4E6") : Theme.cardText(ctx), true);
        locationText.setGravity(Gravity.CENTER);
        locationText.setCompoundDrawablePadding(8);
        locationText.setCompoundDrawablesRelativeWithIntrinsicBounds(
            null, null, tinted(R.drawable.ic_location, kids ? Color.parseColor("#FFFFF4E6") : Theme.cardGold(ctx), 24), null);
        Stage.fitOneLine(locationText, 11, 19);
        focusable(locationText, 14, pickCity);
        Stage.add(stage, locationText, 448, 156, 812, 198);

        int pl = g.prayers[0], pt = g.prayers[1], pr = g.prayers[2], pb = g.prayers[3];
        int pitch = (pb - pt) / 6;
        for (int i = 0; i < 6; i++) {
            final int prayer = ROW_PRAYER[i];
            int top = pt + i * pitch;
            FrameLayout row = new FrameLayout(ctx);
            // the row is the highlight/focus area; its contents are placed inside it
            Stage.add(stage, row, pl, top, pr, top + pitch - 3);
            int rh = pitch - 3;

            rowTime[i] = Stage.text(ctx, "", 25, Theme.nextText(ctx), true);
            rowTime[i].setGravity(Gravity.CENTER);
            rowTime[i].setBackground(Theme.timePill(ctx));
            Stage.fitOneLine(rowTime[i], 12, 25);
            Stage.add(row, rowTime[i], 28, (rh - 38) / 2, 204, (rh - 38) / 2 + 38);

            rowName[i] = Stage.text(ctx, "", 27, Theme.listName(ctx), true);
            rowName[i].setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
            Stage.fitOneLine(rowName[i], 14, 27);
            rowIqama[i] = Stage.text(ctx, "", 14, Theme.listDim(ctx), false);
            rowIqama[i].setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
            Stage.fitOneLine(rowIqama[i], 9, 14);
            Stage.add(row, rowName[i], 214, 0, 376, rh - 12);
            Stage.add(row, rowIqama[i], 214, rh - 16, 376, rh - 1);

            ImageView icon = new ImageView(ctx);
            icon.setImageResource(ROW_ICONS[i]);
            icon.setColorFilter(kids ? Color.parseColor("#FFD9788E") : Theme.cardGold(ctx));
            Stage.add(row, icon, 410, (rh - 40) / 2, 450, (rh - 40) / 2 + 40);

            if (prayer >= 0) {
                focusable(row, 18, new Runnable() {
                    @Override
                    public void run() {
                        TimingDialogs.field(ctx, prayer, PrayerBoard.this.changed);
                    }
                });
            }
            rows[i] = row;
        }

        nextBar = Stage.text(ctx, "", 27, Theme.nextText(ctx), true);
        nextBar.setGravity(Gravity.CENTER);
        nextBar.setCompoundDrawablePadding(10);
        nextBar.setCompoundDrawablesRelativeWithIntrinsicBounds(
            null, null, tinted(R.drawable.ic_mosque, Theme.nextText(ctx), 34), null);
        Stage.fitOneLine(nextBar, 13, 27);
        focusable(nextBar, 24, new Runnable() {
            @Override
            public void run() {
                TimingDialogs.open(ctx, PrayerBoard.this.changed);
            }
        });
        Stage.add(stage, nextBar, g.next[0] + 30, g.next[1], g.next[2] - 30, g.next[3]);

        // ------------------------------------------------------------ the clock
        clock = new Stage.Outlined(ctx);
        clock.setTextColor(Theme.clockFill(ctx));
        clock.setTextSize(TypedValue.COMPLEX_UNIT_PX, 118);
        clock.setTypeface(kids ? Typeface.create("sans-serif-medium", Typeface.BOLD) : Typeface.create("serif", Typeface.BOLD));
        clock.setIncludeFontPadding(false);
        clock.setGravity(Gravity.CENTER);
        clock.setMaxLines(1);
        clock.setAutoSizeTextTypeUniformWithConfiguration(40, 118, 1, TypedValue.COMPLEX_UNIT_PX);
        if (kids) clock.setOutline(Theme.clockOutline(ctx), 7f);
        focusable(clock, 30, openClock);
        Stage.add(stage, clock, 930, 92, 1252, 214);

        ampm = Stage.text(ctx, "", 22, kids ? Color.parseColor("#FF7A3347") : Theme.cardGold(ctx), true);
        ampm.setGravity(Gravity.CENTER);
        GradientDrawable ap = new GradientDrawable();
        ap.setCornerRadius(18);
        ap.setColor(Color.parseColor(kids ? "#FFFBDDE0" : "#99062B1F"));
        ap.setStroke(2, Color.parseColor(kids ? "#FFE5A9B7" : "#FFB88A22"));
        ampm.setBackground(ap);
        Stage.add(stage, ampm, 1258, 164, 1324, 202);

        // ------------------------------------------------------------ the dates
        int dl = g.date[0], dt = g.date[1];
        int cx0 = dl + 36, cx1 = g.date[2] - 96;
        weekday = Stage.text(ctx, "", 34, Theme.cardGold(ctx), true);
        weekday.setGravity(Gravity.CENTER);
        Stage.add(stage, weekday, cx0, dt + 6, cx1, dt + 46);
        gregAr = Stage.text(ctx, "", 28, Theme.cardText(ctx), true);
        gregAr.setGravity(Gravity.CENTER);
        Stage.fitOneLine(gregAr, 14, 28);
        Stage.add(stage, gregAr, cx0, dt + 46, cx1, dt + 82);
        gregEn = Stage.text(ctx, "", 17, Theme.cardDim(ctx), false);
        gregEn.setGravity(Gravity.CENTER);
        Stage.fitOneLine(gregEn, 9, 17);
        Stage.add(stage, gregEn, cx0, dt + 82, cx1, dt + 104);
        View line = new View(ctx);
        line.setBackgroundColor(kids ? Color.parseColor("#88FFE9D2") : Color.parseColor("#88E8C15C"));
        Stage.add(stage, line, dl + 26, dt + 111, g.date[2] - 26, dt + 112);
        hijriAr = Stage.text(ctx, "", 27, Theme.cardText(ctx), true);
        hijriAr.setGravity(Gravity.CENTER);
        Stage.fitOneLine(hijriAr, 14, 27);
        Stage.add(stage, hijriAr, cx0, dt + 116, cx1, dt + 152);
        hijriEn = Stage.text(ctx, "", 16, Theme.cardDim(ctx), false);
        hijriEn.setGravity(Gravity.CENTER);
        Stage.fitOneLine(hijriEn, 9, 16);
        Stage.add(stage, hijriEn, cx0, dt + 152, cx1, dt + 172);
        ImageView cal = new ImageView(ctx);
        cal.setImageResource(R.drawable.ic_calendar);
        cal.setColorFilter(Theme.cardGold(ctx));
        Stage.add(stage, cal, g.date[2] - 78, dt + 36, g.date[2] - 30, dt + 84);
        ImageView moon = new ImageView(ctx);
        moon.setImageResource(R.drawable.ic_crescent);
        moon.setColorFilter(Theme.cardGold(ctx));
        Stage.add(stage, moon, g.date[2] - 78, dt + 118, g.date[2] - 36, dt + 160);
        View dateFocus = new View(ctx);
        focusable(dateFocus, 22, openDate);
        Stage.add(stage, dateFocus, dl, dt, g.date[2], g.date[3]);

        // ------------------------------------------------------------ the two countdowns
        TextView[] names = new TextView[2];
        TextView[] times = new TextView[2];
        int[] labels = {R.string.count_adhan, R.string.count_iqama};
        int[][] boxes = {g.countAdhan, g.countIqama};
        int[] icons = {R.drawable.ic_sunset, R.drawable.ic_mosque};
        for (int i = 0; i < 2; i++) {
            int l = boxes[i][0], t = boxes[i][1], r = boxes[i][2], b = boxes[i][3];
            TextView label = Stage.text(ctx, ctx.getString(labels[i]), 16, Theme.cardDim(ctx), false);
            label.setGravity(Gravity.CENTER);
            Stage.fitOneLine(label, 9, 16);
            Stage.add(stage, label, l + 12, t + 10, r - 12, t + 36);
            names[i] = Stage.text(ctx, "", 25, Theme.cardGold(ctx), true);
            names[i].setGravity(Gravity.CENTER);
            Stage.fitOneLine(names[i], 12, 25);
            Stage.add(stage, names[i], l + 36, t + 38, r - 70, t + 74);
            ImageView ic = new ImageView(ctx);
            ic.setImageResource(icons[i]);
            ic.setColorFilter(Theme.cardGold(ctx));
            Stage.add(stage, ic, r - 66, t + 38, r - 28, t + 76);
            if (kids) {
                View pill = new View(ctx);
                pill.setBackground(Theme.timePill(ctx));
                Stage.add(stage, pill, l + 24, t + 84, r - 24, t + 140);
            }
            times[i] = Stage.text(ctx, "--:--:--", 40, kids ? Color.parseColor("#FF9A3D55") : Theme.cardGold(ctx), true);
            times[i].setGravity(Gravity.CENTER);
            Stage.fitOneLine(times[i], 18, 40);
            Stage.add(stage, times[i], l + 24, t + 84, r - 24, t + 140);
            View hit = new View(ctx);
            focusable(hit, 24, new Runnable() {
                @Override
                public void run() {
                    TimingDialogs.open(ctx, PrayerBoard.this.changed);
                }
            });
            Stage.add(stage, hit, l, t, r, b);
        }
        adhanName = names[0];
        adhanTime = times[0];
        iqamaName = names[1];
        iqamaTime = times[1];
    }

    // ---------------------------------------------------------------- small helpers

    private Drawable tinted(int res, int colour, int size) {
        Drawable d = ctx.getDrawable(res).mutate();
        d.setTint(colour);
        d.setBounds(0, 0, size, size);
        return d;
    }

    /** Makes a view selectable with the remote (a ring around it) and clickable. */
    private void focusable(final View v, float radius, final Runnable click) {
        v.setFocusable(true);
        v.setClickable(true);
        final Drawable ring = Theme.ring(ctx, radius);
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
        ampm.setVisibility(DateFormat.is24HourFormat(ctx) ? View.GONE : View.VISIBLE);
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
    /** The big clock, with Western digits like the design. */
    private void updateClock() {
        boolean h24 = DateFormat.is24HourFormat(ctx);
        Date now = new Date();
        clock.setText(new SimpleDateFormat(h24 ? "HH:mm" : "hh:mm", Locale.US).format(now));
        if (!h24) ampm.setText(new SimpleDateFormat("a", Locale.US).format(now).toUpperCase(Locale.US));
    }

    void update() {
        updateClock();
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

            flag.setEgypt("EG".equals(city.country));
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
                rows[i].setAlpha(at <= now && !isNext ? 0.55f : 1f);
                rows[i].setBackground(isNext ? Theme.highlight(ctx, 18) : null);
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
