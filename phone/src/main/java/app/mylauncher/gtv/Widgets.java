package app.mylauncher.gtv;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.RemoteViews;

import java.util.Calendar;

/** Draws the four small widgets: prayer tracking, tasbih, the morning/evening adhkar shortcut, and the Hijri date. */
final class Widgets {
    static final String A_TRACK = "app.mylauncher.gtv.W_TRACK";
    static final String A_ADD = "app.mylauncher.gtv.W_ADD";
    static final String A_NEXT = "app.mylauncher.gtv.W_NEXT";
    static final String A_RESET = "app.mylauncher.gtv.W_RESET";

    private static final int GOLD = 0xFFE8B84A;
    private static final int[] SHOW = {0, -1, 1, 2, 3, 4}; // tile order: Fajr, sunrise, Dhuhr, Asr, Maghrib, Isha

    private Widgets() {
    }

    private static int[] ids(Context c, AppWidgetManager m, Class<?> k) {
        int[] a = m.getAppWidgetIds(new ComponentName(c, k));
        return a == null ? new int[0] : a;
    }

    private static String ap(String s) {
        return s.replace("AM", "ص").replace("PM", "م");
    }

    private static PendingIntent open(Context c, int req, Intent in) {
        return PendingIntent.getActivity(c, req, in.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP
            | Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent tap(Context c, int req, String action, int idx) {
        Intent i = new Intent(c, WidgetReceiver.class).setAction(action).putExtra("idx", idx)
            .setData(Uri.parse("widget://" + action + "/" + idx));
        return PendingIntent.getBroadcast(c, req, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void refresh(Context c) {
        try {
            AppWidgetManager m = AppWidgetManager.getInstance(c);
            track(c, m);
            tasbih(c, m);
            azkar(c, m);
            hijri(c, m);
        } catch (RuntimeException ignored) {
            // The home screen may be busy; the next change redraws it.
        }
    }

    static void handle(Context c, Intent i) {
        String a = i.getAction();
        int idx = i.getIntExtra("idx", 0);
        android.content.SharedPreferences sp = c.getSharedPreferences("magiic", Context.MODE_PRIVATE);
        if (A_TRACK.equals(a)) {
            Cities.City city = Cities.selected(c);
            if (city != null && idx >= 0 && idx < 5) {
                long now = System.currentTimeMillis();
                long[] t = PrayerCalc.day(city, now, 0, PrayerPrefs.adjust(c));
                if (t[idx] <= now) Track.toggle(c, t[idx], idx);
            }
        } else if (A_ADD.equals(a)) {
            Adhkar.add(c, sel(c));
        } else if (A_NEXT.equals(a)) {
            sp.edit().putInt("w_tk", (sel(c) + 1) % Adhkar.TASBIH.length).apply();
        } else if (A_RESET.equals(a)) {
            Adhkar.reset(c, sel(c));
        }
        refresh(c);
    }

    private static int sel(Context c) {
        return Math.max(0, Math.min(Adhkar.TASBIH.length - 1,
            c.getSharedPreferences("magiic", Context.MODE_PRIVATE).getInt("w_tk", 0)));
    }

    // ---------------------------------------------------------------- prayer tracking
    private static final int[] TK_B = {R.id.tk_b0, R.id.tk_b1, R.id.tk_b2, R.id.tk_b3, R.id.tk_b4, R.id.tk_b5};
    private static final int[] TK_D = {R.id.tk_d0, R.id.tk_d1, R.id.tk_d2, R.id.tk_d3, R.id.tk_d4, R.id.tk_d5};
    private static final int[] TK_N = {R.id.tk_n0, R.id.tk_n1, R.id.tk_n2, R.id.tk_n3, R.id.tk_n4, R.id.tk_n5};
    private static final int[] TK_T = {R.id.tk_t0, R.id.tk_t1, R.id.tk_t2, R.id.tk_t3, R.id.tk_t4, R.id.tk_t5};

    private static void track(Context c, AppWidgetManager m) {
        int[] ids = ids(c, m, TrackWidget.class);
        Cities.City city = Cities.selected(c);
        if (ids.length == 0 || city == null) return;
        long now = System.currentTimeMillis();
        int[] adj = PrayerPrefs.adjust(c);
        long[] t = PrayerCalc.day(city, now, 0, adj);
        boolean friday = PrayerCalc.isFriday(city, now);
        int curIdx = -1;
        for (int i = 0; i < 5; i++) if (t[i] <= now) curIdx = i;
        Calendar cal = Hijri.today(c);
        int bits = Track.bits(c, Track.cal(c, now));

        RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.widget_track);
        rv.setTextViewText(R.id.tk_day, String.valueOf(Hijri.of(cal, Hijri.adjust(c))[2]));
        rv.setTextViewText(R.id.tk_hijri, Hijri.hijriAr(Hijri.of(cal, Hijri.adjust(c))));
        rv.setTextViewText(R.id.tk_greg, Hijri.weekdayAr(cal) + "، " + Hijri.gregorianAr(cal));
        rv.setTextViewText(R.id.tk_count, Track.count(bits) + " / 5");
        rv.setTextViewText(R.id.tk_city, "📍 " + city.name(true));
        long rise = PrayerCalc.sunrise(city, now, 0, adj);
        for (int k = 0; k < 6; k++) {
            int p = SHOW[k];
            if (p < 0) {
                rv.setTextViewText(TK_D[k], "☀");
                rv.setTextViewText(TK_N[k], "الشروق");
                rv.setTextViewText(TK_T[k], ap(PrayerCalc.time(c, city, rise)));
                rv.setTextColor(TK_D[k], 0xFFE9DDBB);
                rv.setInt(TK_B[k], "setBackgroundResource", R.drawable.w_tile);
                rv.setOnClickPendingIntent(TK_B[k], open(c, 300, new Intent(c, PhoneActivity.class)));
                continue;
            }
            boolean done = (bits >> p & 1) != 0;
            boolean reached = t[p] <= now;
            rv.setTextViewText(TK_D[k], done ? "✓" : reached ? "○" : "·");
            rv.setTextViewText(TK_N[k], PrayerCalc.name(c, p, friday));
            rv.setTextViewText(TK_T[k], ap(PrayerCalc.time(c, city, t[p])));
            rv.setTextColor(TK_D[k], done ? GOLD : 0xFFC9CCE0);
            rv.setTextColor(TK_N[k], reached ? 0xFFFFFFFF : 0xFF8F97B5);
            rv.setTextColor(TK_T[k], reached ? 0xFFB8C2DC : 0xFF8F97B5);
            rv.setInt(TK_B[k], "setBackgroundResource", done ? R.drawable.w_tile_done : p == curIdx ? R.drawable.w_tile_cur : R.drawable.w_tile);
            rv.setOnClickPendingIntent(TK_B[k], tap(c, 310 + p, A_TRACK, p));
        }
        rv.setOnClickPendingIntent(R.id.tk_head, open(c, 301, new Intent(c, PhoneActivity.class)));
        m.updateAppWidget(ids, rv);
    }

    // ---------------------------------------------------------------- tasbih
    private static void tasbih(Context c, AppWidgetManager m) {
        int[] ids = ids(c, m, TasbihWidget.class);
        if (ids.length == 0) return;
        int i = sel(c);
        RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.widget_tasbih);
        rv.setTextViewText(R.id.tb_name, Adhkar.TASBIH[i]);
        rv.setTextViewText(R.id.tb_count, String.valueOf(Adhkar.cur(c, i)));
        rv.setTextViewText(R.id.tb_today, "اليوم: " + Adhkar.count(c, i));
        rv.setOnClickPendingIntent(R.id.tb_count, tap(c, 320, A_ADD, 0));
        rv.setOnClickPendingIntent(R.id.tb_name, tap(c, 321, A_NEXT, 0));
        rv.setOnClickPendingIntent(R.id.tb_reset, tap(c, 322, A_RESET, 0));
        rv.setOnClickPendingIntent(R.id.tb_today, open(c, 323, new Intent(c, PhoneActivity.class)));
        m.updateAppWidget(ids, rv);
    }

    // ---------------------------------------------------------------- morning / evening adhkar
    private static void azkar(Context c, AppWidgetManager m) {
        int[] ids = ids(c, m, AzkarWidget.class);
        Cities.City city = Cities.selected(c);
        if (ids.length == 0 || city == null) return;
        long now = System.currentTimeMillis();
        long[] t = PrayerCalc.day(city, now, 0, PrayerPrefs.adjust(c));
        int period = BubbleService.period(c);
        int cat;
        String sub;
        if (period == BubbleService.MORNING) {
            cat = period;
            sub = "وقتها الآن · حتى " + ap(PrayerCalc.time(c, city, t[1]));
        } else if (period == BubbleService.EVENING) {
            cat = period;
            sub = "وقتها الآن · حتى " + ap(PrayerCalc.time(c, city, t[4]));
        } else if (now >= t[1] && now < t[2]) {
            cat = BubbleService.EVENING;
            sub = "تبدأ مع العصر " + ap(PrayerCalc.time(c, city, t[2]));
        } else {
            cat = BubbleService.MORNING;
            sub = now < t[0] ? "تبدأ مع الفجر " + ap(PrayerCalc.time(c, city, t[0])) : "تبدأ مع فجر الغد";
        }
        boolean morning = cat == BubbleService.MORNING;
        RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.widget_azkar);
        rv.setTextViewText(R.id.az_icon, morning ? "☀" : "☾");
        rv.setTextViewText(R.id.az_title, morning ? "أذكار الصباح" : "أذكار المساء");
        rv.setTextViewText(R.id.az_sub, sub);
        rv.setOnClickPendingIntent(R.id.az_root, open(c, 330 + (morning ? 0 : 1), new Intent(c, PhoneActivity.class).putExtra("open_cat", cat)));
        m.updateAppWidget(ids, rv);
    }

    // ---------------------------------------------------------------- Hijri date and the white days
    private static void hijri(Context c, AppWidgetManager m) {
        int[] ids = ids(c, m, HijriWidget.class);
        if (ids.length == 0) return;
        Calendar cal = Hijri.today(c);
        int adj = Hijri.adjust(c);
        int[] h = Hijri.of(cal, adj);
        RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.widget_hijri);
        rv.setTextViewText(R.id.hj_title, Hijri.hijriAr(h));
        rv.setTextViewText(R.id.hj_sub, note(cal, adj, h));
        rv.setOnClickPendingIntent(R.id.hj_root, open(c, 340, new Intent(c, PhoneActivity.class)));
        m.updateAppWidget(ids, rv);
    }

    private static String note(Calendar cal, int adj, int[] h) {
        int mo = h[1], d = h[2];
        if (mo == 10 && d == 1) return "عيد الفطر · لا صيام";
        if (mo == 12 && d == 10) return "عيد الأضحى · لا صيام";
        if (mo == 12 && d >= 11 && d <= 13) return "أيام التشريق · لا صيام";
        if (mo == 9) return "رمضان · اليوم " + d;
        if (d >= 13 && d <= 15) return "من الأيام البيض · يُستحب الصيام";
        int wd = cal.get(Calendar.DAY_OF_WEEK);
        if (wd == Calendar.MONDAY || wd == Calendar.THURSDAY) {
            return "اليوم " + Hijri.weekdayAr(cal) + " · يُستحب الصيام";
        }
        if (wd == Calendar.SUNDAY || wd == Calendar.WEDNESDAY) {
            return "غداً " + (wd == Calendar.SUNDAY ? "الاثنين" : "الخميس") + " · يُستحب الصيام";
        }
        for (int k = 1; k <= 31; k++) {
            int[] x = Hijri.of(cal, adj + k);
            if (x[2] == 13 && x[1] != 12 && x[1] != 9) {
                return k == 1 ? "غداً أول الأيام البيض" : "الأيام البيض بعد " + k + " يوماً";
            }
        }
        return "";
    }
}
