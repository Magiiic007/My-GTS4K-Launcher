package app.mylauncher.gtv;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The third look, "Mihrab": navy and gold, with the prayer cards in a row, the favourite apps, and a tall mihrab arch
 * holding the next prayer and its countdown. The layout follows the idea of the Afnon launcher (MIT licence, see
 * THIRD_PARTY.md); the code is our own. It is a separate screen: the royal and kids looks do not touch it.
 */
final class MihrabHome {
    private static final int CREAM = Color.parseColor("#FFF4EEDC");
    private static final int DIM = Color.parseColor("#FF9FB0D6");
    private static final int GOLD = Color.parseColor("#FFE0B960");
    private static final int CARD = Color.parseColor("#FF12234D");
    private static final int[] ROW_PRAYER = {PrayerCalc.FAJR, -1, PrayerCalc.DHUHR, PrayerCalc.ASR,
        PrayerCalc.MAGHRIB, PrayerCalc.ISHA};
    private static final String FAV_KEY = "mih_fav";

    private final MainActivity a;
    private final FrameLayout stage;
    private final Handler handler = new Handler(Looper.getMainLooper());

    final TextView weather, radio, notice;
    private final TextView clock, ampm, dateLine, hijriLine, verse, verseRef, nextName, countdown, nextInfo;
    private final TextView[] cardTag = new TextView[6], cardName = new TextView[6], cardTime = new TextView[6];
    private final View[] card = new View[6];
    private final FrameLayout favBox, moreBox;
    private final FrameLayout rootStage;

    private String cacheKey;
    private long cacheMinute = -1;
    private long[] shown = new long[5];
    private long sunriseAt;
    private PrayerCalc.Event adhanEvent;
    private boolean fridayToday;
    private int ramadan = -1;
    private String extraLine;
    private String lastDay = "";
    private String infoShown = "";
    private String baseInfo = "";
    private List<MainActivity.AppItem> apps = new ArrayList<>();
    private int total;
    /** The packages shown on the home screen (used to pick the default favourites). */
    private static List<String> lastShown = new ArrayList<>();

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            update();
            handler.postDelayed(this, 1000L);
        }
    };

    // ---------------------------------------------------------------- favourites

    /** The favourites: what the person chose, or (never chosen) the first nine apps. */
    static Set<String> favs(Context c) {
        SharedPreferences p = c.getSharedPreferences("magiic", Context.MODE_PRIVATE);
        Set<String> s = p.getStringSet(FAV_KEY, null);
        if (s != null) return new HashSet<>(s);
        Set<String> d = new HashSet<>();
        for (int i = 0; i < lastShown.size() && i < 9; i++) d.add(lastShown.get(i));
        return d;
    }

    static boolean isFav(Context c, String pkg) {
        return favs(c).contains(pkg);
    }

    /** Adds or removes a favourite; returns true when it is now a favourite. */
    static boolean toggleFav(Context c, String pkg) {
        Set<String> s = favs(c);
        boolean now = !s.remove(pkg);
        if (now) s.add(pkg);
        c.getSharedPreferences("magiic", Context.MODE_PRIVATE).edit().putStringSet(FAV_KEY, s).apply();
        return now;
    }

    // ---------------------------------------------------------------- building

    private static GradientDrawable shape(int fill, float r, int strokePx, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(r);
        if (strokePx > 0) g.setStroke(strokePx, stroke);
        return g;
    }

    /** A selectable surface: quiet when off, a bright gold outline when the remote is on it. */
    private static Drawable surface(int fill, float r, int strokePx, int stroke) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[] {android.R.attr.state_focused}, shape(Color.parseColor("#33E0B960"), r, 4, GOLD));
        s.addState(new int[] {}, shape(fill, r, strokePx, stroke));
        return s;
    }

    private TextView tv(String text, float size, int colour, boolean bold, boolean serif, int gravity) {
        TextView t = Stage.text(a, text, size, colour, bold);
        if (serif) t.setTypeface(Typeface.create("serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        t.setGravity(gravity);
        return t;
    }

    private void focusable(final View v, float scale, final Runnable click) {
        v.setFocusable(true);
        v.setClickable(true);
        v.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View x) {
                click.run();
            }
        });
        v.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View x, boolean has) {
                Motion.pop(x, has, scale);
            }
        });
    }

    private TextView chip(int icon, float size) {
        TextView b = Stage.text(a, "", size, CREAM, true);
        b.setGravity(Gravity.CENTER);
        b.setPadding(10, 2, 10, 2);
        b.setBackground(surface(CARD, 26, 2, Color.parseColor("#59E0B960")));
        if (icon != 0) {
            Drawable d = a.getDrawable(icon).mutate();
            d.setTint(GOLD);
            d.setBounds(0, 0, 22, 22);
            b.setCompoundDrawablePadding(6);
            b.setCompoundDrawablesRelativeWithIntrinsicBounds(d, null, null, null);
        }
        return b;
    }

    MihrabHome(final MainActivity act, FrameLayout stage) {
        a = act;
        this.stage = stage;
        rootStage = stage;
        stage.setBackground(new LayerDrawable(new Drawable[] {
            new android.graphics.drawable.ColorDrawable(Color.parseColor("#FF0B1A3A")),
            new IslamicPattern(a, 0x14E0B960)}));

        // ------------------------------------------------------------ header
        FrameLayout gear = new FrameLayout(a);
        gear.setBackground(surface(CARD, 26, 2, Color.parseColor("#73E0B960")));
        ImageView gi = new ImageView(a);
        gi.setImageResource(R.drawable.ic_settings);
        gi.setColorFilter(CREAM);
        gear.addView(gi, new FrameLayout.LayoutParams(28, 28, Gravity.CENTER));
        focusable(gear, 1.08f, new Runnable() {
            @Override
            public void run() {
                a.showMihrabSettings();
            }
        });
        Stage.add(stage, gear, 68, 40, 120, 92);

        radio = chip(R.drawable.ic_radio, 14);
        radio.setMaxLines(1);
        focusable(radio, 1.06f, new Runnable() {
            @Override
            public void run() {
                a.chooseRadio();
            }
        });
        Stage.add(stage, radio, 256, 40, 376, 92);

        weather = chip(0, 14);
        weather.setMaxLines(2);
        focusable(weather, 1.06f, new Runnable() {
            @Override
            public void run() {
                a.chooseCountry();
            }
        });
        Stage.add(stage, weather, 384, 40, 520, 92);

        notice = chip(R.drawable.ic_warning, 15);
        notice.setMaxLines(1);
        notice.setVisibility(View.GONE);
        notice.setFocusable(false);
        Stage.add(stage, notice, 1090, 20, 1336, 60);

        // ------------------------------------------------------------ clock and dates
        ampm = tv("", 24, DIM, false, true, Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        Stage.add(stage, ampm, 560, 34, 650, 118);
        clock = tv("", 82, CREAM, true, true, Gravity.CENTER);
        Stage.fitOneLine(clock, 40, 82);
        Stage.add(stage, clock, 650, 34, 824, 118);
        dateLine = tv("", 23, CREAM, true, false, Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        Stage.fitOneLine(dateLine, 12, 23);
        Stage.add(stage, dateLine, 300, 120, 824, 152);
        hijriLine = tv("", 19, DIM, false, false, Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        Stage.fitOneLine(hijriLine, 10, 19);
        Stage.add(stage, hijriLine, 300, 154, 824, 184);

        // ------------------------------------------------------------ the six prayer cards
        for (int i = 0; i < 6; i++) {
            int x1 = 824 - i * 129, x0 = x1 - 117;
            LinearLayout c = new LinearLayout(a);
            c.setOrientation(LinearLayout.VERTICAL);
            c.setGravity(Gravity.CENTER);
            cardTag[i] = tv(a.getString(R.string.mih_next_tag), 14, GOLD, true, false, Gravity.CENTER);
            cardName[i] = tv("", 23, CREAM, true, false, Gravity.CENTER);
            Stage.fitOneLine(cardName[i], 12, 23);
            cardTime[i] = tv("", 21, CREAM, true, true, Gravity.CENTER);
            Stage.fitOneLine(cardTime[i], 11, 21);
            c.addView(cardTag[i], new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 18));
            c.addView(cardName[i], new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 30));
            c.addView(cardTime[i], new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 28));
            c.setFocusable(false);
            card[i] = c;
            Stage.add(stage, c, x0, 207, x1, 309);
        }

        // ------------------------------------------------------------ favourites and more
        TextView favTitle = tv(a.getString(R.string.mih_favs), 18, DIM, false, false, Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        Stage.add(stage, favTitle, 300, 344, 824, 372);
        favBox = new FrameLayout(a);
        favBox.setClipChildren(false);
        Stage.add(stage, favBox, 300, 378, 824, 486);
        TextView moreTitle = tv(a.getString(R.string.mih_more), 18, DIM, false, false, Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        Stage.add(stage, moreTitle, 300, 516, 824, 544);
        moreBox = new FrameLayout(a);
        moreBox.setClipChildren(false);
        Stage.add(stage, moreBox, 68, 550, 824, 636);
        TextView hint = tv(a.getString(R.string.mih_hint), 15, DIM, false, false, Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        Stage.add(stage, hint, 300, 690, 824, 716);

        // ------------------------------------------------------------ the arch
        final int ax = 869, ay = 84, aw = 430, ah = 641;
        Stage.add(stage, new ArchView(a), ax, ay, ax + aw, ay + ah);
        verse = tv("", 25, GOLD, false, true, Gravity.CENTER);
        verse.setLineSpacing(0f, 1.25f);
        verse.setMaxLines(4);
        verse.setAutoSizeTextTypeUniformWithConfiguration(14, 25, 1, TypedValue.COMPLEX_UNIT_PX);
        Stage.add(stage, verse, ax + 60, ay + 110, ax + aw - 60, ay + 216);
        verseRef = tv("", 15, DIM, false, false, Gravity.CENTER);
        Stage.add(stage, verseRef, ax + 120, ay + 218, ax + aw - 120, ay + 242);
        View rule = new View(a);
        rule.setBackgroundColor(Color.parseColor("#59E0B960"));
        Stage.add(stage, rule, ax + 150, ay + 252, ax + aw - 150, ay + 253);
        Stage.add(stage, tv(a.getString(R.string.mih_next_title), 19, DIM, false, false, Gravity.CENTER),
            ax + 60, ay + 268, ax + aw - 60, ay + 296);
        nextName = tv("", 64, GOLD, true, true, Gravity.CENTER);
        Stage.fitOneLine(nextName, 28, 64);
        Stage.add(stage, nextName, ax + 60, ay + 296, ax + aw - 60, ay + 372);
        countdown = tv("--:--:--", 78, CREAM, true, true, Gravity.CENTER);
        Stage.fitOneLine(countdown, 36, 78);
        Stage.add(stage, countdown, ax + 30, ay + 378, ax + aw - 30, ay + 468);
        nextInfo = tv("", 19, DIM, false, false, Gravity.CENTER);
        Stage.fitOneLine(nextInfo, 10, 19);
        Stage.add(stage, nextInfo, ax + 40, ay + 478, ax + aw - 40, ay + 512);

        TextView btn = tv(a.getString(R.string.mih_adhan_btn), 23, Color.parseColor("#FF2A1F00"), true, false, Gravity.CENTER);
        GradientDrawable gold = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            new int[] {Color.parseColor("#FFEBCB7C"), Color.parseColor("#FFC9A24A")});
        gold.setCornerRadius(32);
        gold.setStroke(3, Color.parseColor("#FF8A6A22"));
        LayerDrawable btnBg = new LayerDrawable(new Drawable[] {gold});
        btn.setBackground(btnBg);
        Stage.fitOneLine(btn, 12, 23);
        btn.setPadding(14, 0, 14, 0);
        focusable(btn, 1.05f, new Runnable() {
            @Override
            public void run() {
                a.openTiming();
            }
        });
        btn.setForeground(new FocusRing(32));
        Stage.add(stage, btn, ax + 36, ay + 560, ax + aw - 36, ay + 624);
    }

    /** A bright outline drawn over the view while the remote is on it. */
    private static final class FocusRing extends StateListDrawable {
        FocusRing(float r) {
            addState(new int[] {android.R.attr.state_focused}, shape(Color.TRANSPARENT, r, 5, Color.WHITE));
            addState(new int[] {}, shape(Color.TRANSPARENT, r, 0, 0));
        }
    }

    // ---------------------------------------------------------------- apps

    void setApps(List<MainActivity.AppItem> shownApps, int totalCount) {
        apps = new ArrayList<>(shownApps);
        total = totalCount;
        lastShown = new ArrayList<>();
        for (MainActivity.AppItem i : apps) lastShown.add(i.component.getPackageName());
        // Focus is only (re)placed when there is none, or when the view that had it is about to be replaced.
        final boolean hadFocus = a.getCurrentFocus() != null && !favBox.hasFocus() && !moreBox.hasFocus();
        favBox.removeAllViews();
        moreBox.removeAllViews();
        Set<String> f = favs(a);
        List<MainActivity.AppItem> fav = new ArrayList<>();
        for (MainActivity.AppItem i : apps) if (f.contains(i.component.getPackageName())) fav.add(i);
        View first = null;
        // Favourites: up to three big tiles, from the right.
        for (int i = 0; i < 3 && i < fav.size(); i++) {
            View t = tile(fav.get(i), true);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(176, 108, Gravity.TOP | Gravity.LEFT);
            lp.leftMargin = 524 - 176 - i * 188;
            favBox.addView(t, lp);
            if (first == null) first = t;
        }
        // More: up to six small tiles, then the "all apps" tile on their left.
        int more = Math.max(0, Math.min(6, fav.size() - 3));
        for (int i = 0; i < more; i++) {
            View t = tile(fav.get(3 + i), false);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(98, 86, Gravity.TOP | Gravity.LEFT);
            lp.leftMargin = 756 - 98 - i * 108;
            moreBox.addView(t, lp);
        }
        FrameLayout.LayoutParams alp = new FrameLayout.LayoutParams(98, 86, Gravity.TOP | Gravity.LEFT);
        alp.leftMargin = 756 - 98 - more * 108;
        View all = allTile();
        moreBox.addView(all, alp);
        if (!hadFocus) (first != null ? first : all).requestFocus();
    }

    private View allTile() {
        TextView t = tv(a.getString(R.string.mih_all).replace(" ", "\n") + "\n" + total, 15, CREAM, true, false, Gravity.CENTER);
        GradientDrawable dash = shape(Color.TRANSPARENT, 14, 2, Color.parseColor("#73E0B960"));
        dash.setStroke(2, Color.parseColor("#73E0B960"), 8f, 6f);
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[] {android.R.attr.state_focused}, shape(Color.parseColor("#33E0B960"), 14, 4, GOLD));
        s.addState(new int[] {}, dash);
        t.setBackground(s);
        focusable(t, 1.06f, new Runnable() {
            @Override
            public void run() {
                a.openAllApps();
            }
        });
        return t;
    }

    private View tile(final MainActivity.AppItem item, boolean big) {
        FrameLayout f = new FrameLayout(a);
        f.setBackground(surface(CARD, 16, 0, 0));
        ImageView img = new ImageView(a);
        img.setScaleType(item.banner ? ImageView.ScaleType.CENTER_CROP : ImageView.ScaleType.FIT_CENTER);
        img.setImageDrawable(item.image);
        img.setClipToOutline(true);
        img.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View v, android.graphics.Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), 8);
            }
        });
        int w = big ? (item.banner ? 74 : 42) : (item.banner ? 52 : 30);
        int h = big ? 42 : 30;
        FrameLayout.LayoutParams ilp = new FrameLayout.LayoutParams(w, h, Gravity.TOP | Gravity.RIGHT);
        ilp.topMargin = big ? 12 : 8;
        ilp.rightMargin = big ? 14 : 8;
        f.addView(img, ilp);
        TextView name = tv(item.label, big ? 23 : 16, CREAM, true, false, Gravity.RIGHT | Gravity.BOTTOM);
        name.setMaxLines(big ? 1 : 2);
        if (big) Stage.fitOneLine(name, 12, 23);
        FrameLayout.LayoutParams nlp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            big ? 34 : 40, Gravity.BOTTOM);
        nlp.setMargins(big ? 12 : 6, 0, big ? 14 : 8, big ? 10 : 6);
        f.addView(name, nlp);
        focusable(f, 1.06f, new Runnable() {
            @Override
            public void run() {
                a.open(item);
            }
        });
        f.setLongClickable(true);
        f.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                a.favMenu(item);
                return true;
            }
        });
        return f;
    }

    // ---------------------------------------------------------------- life and updates

    void start() {
        ampm.setVisibility(DateFormat.is24HourFormat(a) ? View.GONE : View.VISIBLE);
        handler.removeCallbacks(tick);
        handler.post(tick);
    }

    void stop() {
        handler.removeCallbacks(tick);
    }

    void refresh() {
        cacheKey = null;
        update();
    }

    void refreshDates() {
        lastDay = "";
        update();
    }

    private static String two(long v) {
        return v < 10 ? "0" + v : String.valueOf(v);
    }

    private static String clockText(long ms) {
        long s = Math.max(0L, ms) / 1000L;
        return two(s / 3600L) + ":" + two((s / 60L) % 60L) + ":" + two(s % 60L);
    }

    private boolean isArabic() {
        return "ar".equals(Locale.getDefault().getLanguage());
    }

    private void updateDates(Cities.City city) {
        Calendar today = Hijri.today(a);
        int[] h = Hijri.of(today, Hijri.adjust(a));
        String key = today.get(Calendar.YEAR) + "-" + today.get(Calendar.DAY_OF_YEAR) + "-" + Hijri.adjust(a)
            + "-" + (city == null ? "" : city.key());
        if (key.equals(lastDay)) return;
        lastDay = key;
        cacheMinute = -1;
        ramadan = ramadanState(today);
        // One complete verse a day (Tanzil Uthmani text, see res/values/arrays.xml).
        String[] vt = a.getResources().getStringArray(R.array.verse_text);
        String[] vr = a.getResources().getStringArray(R.array.verse_ref);
        int vi = (today.get(Calendar.YEAR) * 366 + today.get(Calendar.DAY_OF_YEAR)) % vt.length;
        verse.setText(vt[vi]);
        verseRef.setText(vr[vi]);
        String where = city == null ? "" : " · " + city.name(isArabic());
        if (isArabic()) {
            dateLine.setText(Hijri.weekdayAr(today) + "، " + Hijri.gregorianAr(today));
            hijriLine.setText(Hijri.hijriAr(h) + where);
        } else {
            dateLine.setText(Hijri.gregorianEn(today));
            hijriLine.setText(Hijri.hijriEn(h) + where);
        }
    }

    /** -2 during Ramadan; otherwise the days left until its first day when within three months; otherwise -1. */
    private int ramadanState(Calendar today) {
        int adj = Hijri.adjust(a);
        if (Hijri.of(today, adj)[1] == 9) return -2;
        Calendar d = (Calendar) today.clone();
        for (int i = 1; i <= 92; i++) {
            d.add(Calendar.DAY_OF_MONTH, 1);
            int[] h = Hijri.of(d, adj);
            if (h[1] == 9 && h[2] == 1) return i;
        }
        return -1;
    }

    private void paintCard(int i, boolean next, int alpha) {
        card[i].setBackground(shape(CARD, 14, next ? 3 : 0, GOLD));
        card[i].setAlpha(alpha / 255f);
        cardTag[i].setVisibility(next ? View.VISIBLE : View.INVISIBLE);
        int col = next ? GOLD : CREAM;
        cardName[i].setTextColor(col);
        cardTime[i].setTextColor(col);
    }

    void update() {
        boolean h24 = DateFormat.is24HourFormat(a);
        Date nowD = new Date();
        clock.setText(new SimpleDateFormat(h24 ? "HH:mm" : "hh:mm", Locale.US).format(nowD));
        if (!h24) ampm.setText(new SimpleDateFormat("a", Locale.US).format(nowD).toUpperCase(Locale.US));
        Cities.City city = Cities.selected(a);
        updateDates(city);
        if (city == null) {
            for (int i = 0; i < 6; i++) {
                cardName[i].setText(rowLabel(i, false));
                cardTime[i].setText("--:--");
                paintCard(i, false, 255);
            }
            nextName.setText(R.string.choose_city);
            countdown.setText("--:--:--");
            nextInfo.setText("");
            cacheKey = null;
            return;
        }
        long now = System.currentTimeMillis();
        if (adhanEvent != null && adhanEvent.at <= now) cacheMinute = -1;
        long minute = now / 60000L;
        if (!city.key().equals(cacheKey) || minute != cacheMinute) {
            cacheKey = city.key();
            cacheMinute = minute;
            int[] adjust = PrayerPrefs.adjust(a);
            shown = PrayerCalc.day(city, now, 0, adjust);
            sunriseAt = PrayerCalc.sunrise(city, now, 0, adjust);
            adhanEvent = PrayerCalc.nextEvent(city, now, adjust, new int[5]);
            fridayToday = PrayerCalc.isFriday(city, now);
            if (adhanEvent == null) return;
            if (adhanEvent.at != shown[adhanEvent.index]) shown[adhanEvent.index] = adhanEvent.at;
            for (int i = 0; i < 6; i++) {
                int p = ROW_PRAYER[i];
                boolean isNext = p >= 0 && p == adhanEvent.index;
                boolean jumuah = p == PrayerCalc.DHUHR && (isNext ? adhanEvent.friday : fridayToday);
                cardName[i].setText(rowLabel(i, jumuah));
                long at = p >= 0 ? shown[p] : sunriseAt;
                cardTime[i].setText(PrayerCalc.time(a, city, at));
                paintCard(i, isNext, at <= now && !isNext ? 140 : 255);
            }
            nextName.setText(PrayerCalc.name(a, adhanEvent.index, adhanEvent.friday));
            baseInfo = a.getString(R.string.mih_adhan_at, PrayerCalc.time(a, city, adhanEvent.at));
            extraLine = null;
            int firstMin = PrayerPrefs.firstAdhan(a);
            long firstAt = shown[PrayerCalc.DHUHR] - firstMin * 60000L;
            if (fridayToday && firstMin > 0 && firstAt > now) {
                extraLine = a.getString(R.string.friday_first, PrayerCalc.time(a, city, firstAt));
            } else if (ramadan == -2) {
                long[] t = PrayerCalc.day(city, now, 0, adjust);
                extraLine = a.getString(R.string.ramadan_times,
                    PrayerCalc.time(a, city, t[PrayerCalc.FAJR]), PrayerCalc.time(a, city, t[PrayerCalc.MAGHRIB]));
            } else if (ramadan == 0) {
                extraLine = a.getString(R.string.ramadan_today);
            } else if (ramadan == 1) {
                extraLine = a.getString(R.string.ramadan_tomorrow);
            } else if (ramadan > 1) {
                extraLine = a.getString(R.string.ramadan_left, ramadan);
            }
        }
        String want = extraLine != null && (now / 6000L) % 2L == 1L ? extraLine : baseInfo;
        if (!want.equals(infoShown)) {
            infoShown = want;
            nextInfo.setText(want);
        }
        if (adhanEvent != null) countdown.setText(clockText(adhanEvent.at - now));
    }

    private String rowLabel(int row, boolean jumuah) {
        int p = ROW_PRAYER[row];
        if (p < 0) return a.getString(R.string.p_sunrise);
        return PrayerCalc.name(a, p, jumuah);
    }

    // ---------------------------------------------------------------- the arch

    private static final class ArchView extends View {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint inner = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path outerPath = new Path();
        private final Path innerPath = new Path();

        ArchView(Context c) {
            super(c);
            fill.setColor(Color.parseColor("#FF0F2250"));
            edge.setStyle(Paint.Style.STROKE);
            edge.setStrokeWidth(4f);
            edge.setColor(Color.parseColor("#FFF1EBDD"));
            inner.setStyle(Paint.Style.STROKE);
            inner.setStrokeWidth(1.2f);
            inner.setColor(Color.parseColor("#73E0B960"));
            outerPath.moveTo(3, 617);
            outerPath.lineTo(3, 262);
            outerPath.cubicTo(3, 150, 95, 62, 215, 3);
            outerPath.cubicTo(335, 62, 427, 150, 427, 262);
            outerPath.lineTo(427, 617);
            outerPath.quadTo(427, 638, 406, 638);
            outerPath.lineTo(24, 638);
            outerPath.quadTo(3, 638, 3, 617);
            outerPath.close();
            innerPath.moveTo(13, 612);
            innerPath.lineTo(13, 264);
            innerPath.cubicTo(13, 158, 100, 72, 215, 16);
            innerPath.cubicTo(330, 72, 417, 158, 417, 264);
            innerPath.lineTo(417, 612);
            innerPath.quadTo(417, 628, 401, 628);
            innerPath.lineTo(29, 628);
            innerPath.quadTo(13, 628, 13, 612);
            innerPath.close();
            setFocusable(false);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.drawPath(outerPath, fill);
            canvas.drawPath(outerPath, edge);
            canvas.drawPath(innerPath, inner);
        }
    }
}
