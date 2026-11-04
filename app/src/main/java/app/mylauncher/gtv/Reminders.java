package app.mylauncher.gtv;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Reminders that appear as a popup over the screen at a time the user sets: the Monday and Thursday fast, the White
 * Days fast (13, 14, 15 of the Hijri month), and any number of custom reminders. They are saved as one JSON text, and
 * one system alarm is kept for the earliest of them.
 */
final class Reminders {
    private Reminders() {}

    static final int WEEK_FAST = 1, WHITE_FAST = 2, CUSTOM = 3, ADHKAR_AM = 4, ADHKAR_PM = 5, OCCASION = 6, KAHF = 7;

    static boolean isFast(int kind) {
        return kind == WEEK_FAST || kind == WHITE_FAST;
    }
    private static final String KEY = "reminders";
    private static final int REQ = 3;
    private static final String ACTION = "app.mylauncher.gtv.REMINDER";
    /** How far ahead to look for the next time; the White Days come every month, so 45 days is plenty. */
    private static final int LOOK_AHEAD_DAYS = 45;
    /** A reminder that comes more than this late (the TV was off) is skipped. */
    static final long TOO_LATE_MS = 30L * 60L * 1000L;

    static final class Item {
        String id;
        int kind;
        boolean enabled;
        /** Custom reminders only: their title and text. */
        String title = "";
        String text = "";
        /** Weekly fast: the fasting days; custom: the days it shows. Bit 0 = Sunday ... bit 6 = Saturday. */
        int daysMask;
        /** White Days: which of the 13th (bit 0), 14th (bit 1) and 15th (bit 2) of the Hijri month. */
        int hijriMask = 7;
        /** The fasts: show the reminder the day before the fast (true) or on its day (false). */
        boolean dayBefore = true;
        int hour = 21;
        int minute = 0;
        /** Minutes it stays on screen; 0 = until it is closed by hand. */
        int durationMin = 0;
        /** Morning/evening adhkar: minutes after Fajr / before Maghrib, or -1 for the fixed time above. */
        int relMin = -1;
        /** Shows the reminder a second time this many minutes later; 0 = once only. */
        int again = 0;
    }

    // ---------------------------------------------------------------- saving

    static List<Item> load(Context c) {
        List<Item> list = new ArrayList<>();
        String json = c.getSharedPreferences("magiic", Context.MODE_PRIVATE).getString(KEY, null);
        if (json != null) {
            try {
                JSONArray a = new JSONArray(json);
                for (int i = 0; i < a.length(); i++) list.add(fromJson(a.getJSONObject(i)));
            } catch (JSONException e) {
                list.clear();
            }
        }
        if (list.isEmpty()) {
            Item w = new Item();
            w.id = "week";
            w.kind = WEEK_FAST;
            w.daysMask = (1 << (Calendar.MONDAY - 1)) | (1 << (Calendar.THURSDAY - 1));
            list.add(w);
            Item b = new Item();
            b.id = "white";
            b.kind = WHITE_FAST;
            list.add(b);
        }
        ensureExtras(list);
        android.content.SharedPreferences sp = c.getSharedPreferences("magiic", Context.MODE_PRIVATE);
        if (!sp.getBoolean("occ_eve_v45", false)) {
            // Occasion alerts now come the evening before (22:00), so people can prepare (fasting and so on).
            for (Item it : list) {
                if (it.kind == OCCASION) {
                    it.dayBefore = true;
                    it.hour = 22;
                    it.minute = 0;
                }
            }
            save(c, list);
            sp.edit().putBoolean("occ_eve_v45", true).apply();
        }
        return list;
    }

    /** The morning and evening adhkar and the occasion greetings exist for everyone, switched on at first. */
    private static void ensureExtras(List<Item> list) {
        boolean am = false, pm = false, occ = false, kahf = false;
        for (Item it : list) {
            am |= it.kind == ADHKAR_AM;
            pm |= it.kind == ADHKAR_PM;
            occ |= it.kind == OCCASION;
            kahf |= it.kind == KAHF;
        }
        if (!am) list.add(extra("adhkar_am", ADHKAR_AM, 6, 30));
        if (!pm) list.add(extra("adhkar_pm", ADHKAR_PM, 16, 30));
        if (!occ) {
            Item o = extra("occasion", OCCASION, 22, 0);
            o.dayBefore = true;
            list.add(o);
        }
        if (!kahf) {
            Item k = extra("kahf", KAHF, 9, 0);
            k.daysMask = 1 << (Calendar.FRIDAY - 1);
            list.add(k);
        }
    }

    private static Item extra(String id, int kind, int hour, int minute) {
        Item it = new Item();
        it.id = id;
        it.kind = kind;
        it.enabled = true;
        it.dayBefore = false;
        it.daysMask = 127;
        it.hour = hour;
        it.minute = minute;
        return it;
    }

    static void save(Context c, List<Item> items) {
        JSONArray a = new JSONArray();
        try {
            for (Item it : items) a.put(toJson(it));
        } catch (JSONException e) {
            return;
        }
        c.getSharedPreferences("magiic", Context.MODE_PRIVATE).edit().putString(KEY, a.toString()).apply();
    }

    private static JSONObject toJson(Item it) throws JSONException {
        return new JSONObject()
            .put("id", it.id).put("kind", it.kind).put("on", it.enabled)
            .put("title", it.title).put("text", it.text)
            .put("days", it.daysMask).put("hijri", it.hijriMask).put("before", it.dayBefore)
            .put("h", it.hour).put("m", it.minute).put("dur", it.durationMin).put("rel", it.relMin).put("again", it.again);
    }

    private static Item fromJson(JSONObject o) {
        Item it = new Item();
        it.id = o.optString("id", "x");
        it.kind = o.optInt("kind", CUSTOM);
        it.enabled = o.optBoolean("on", false);
        it.title = o.optString("title", "");
        it.text = o.optString("text", "");
        it.daysMask = o.optInt("days", 127);
        it.hijriMask = o.optInt("hijri", 7);
        it.dayBefore = o.optBoolean("before", true);
        it.hour = o.optInt("h", 21);
        it.minute = o.optInt("m", 0);
        it.durationMin = o.optInt("dur", 0);
        it.relMin = o.optInt("rel", -1);
        it.again = o.optInt("again", 0);
        return it;
    }

    static Item newCustom() {
        Item it = new Item();
        it.id = "c" + System.currentTimeMillis();
        it.kind = CUSTOM;
        it.enabled = true;
        it.daysMask = 127;
        it.hour = 9;
        it.minute = 0;
        return it;
    }

    // ---------------------------------------------------------------- when

    /** The first moment after {@code after} at which {@code it} should show, or -1 if never (switched off, no days). */
    static long next(Context c, Item it, long after) {
        long a = nextBase(c, it, after);
        if (it.again > 0 && it.enabled) {
            long b = nextBase(c, it, after - it.again * 60000L);
            if (b > 0 && b + it.again * 60000L > after) {
                long second = b + it.again * 60000L;
                if (a < 0 || second < a) a = second;
            }
        }
        return a;
    }

    private static long nextBase(Context c, Item it, long after) {
        if (!it.enabled) return -1;
        int adj = Hijri.adjust(c);
        // The occasions are spread over the year, so they look further ahead than the weekly and monthly ones.
        int limit = it.kind == OCCASION ? 400 : LOOK_AHEAD_DAYS;
        for (int d = 0; d <= limit; d++) {
            Calendar show = Calendar.getInstance();
            show.setTimeInMillis(after);
            show.add(Calendar.DAY_OF_MONTH, d);
            show.set(Calendar.HOUR_OF_DAY, it.hour);
            show.set(Calendar.MINUTE, it.minute);
            show.set(Calendar.SECOND, 0);
            show.set(Calendar.MILLISECOND, 0);
            long at = show.getTimeInMillis();
            if (it.relMin >= 0 && (it.kind == ADHKAR_AM || it.kind == ADHKAR_PM)) {
                Cities.City city = Cities.selected(c);
                if (city != null) {
                    show.set(Calendar.HOUR_OF_DAY, 12);
                    show.set(Calendar.MINUTE, 0);
                    long[] t = PrayerCalc.day(city, show.getTimeInMillis(), 0, PrayerPrefs.adjust(c));
                    at = it.kind == ADHKAR_AM ? t[PrayerCalc.FAJR] + it.relMin * 60000L
                        : t[PrayerCalc.MAGHRIB] - it.relMin * 60000L;
                }
            }
            if (at <= after) continue;
            Calendar target = fastDay(it, show);
            if (matches(it, target, adj)) return at;
        }
        return -1;
    }

    /** The day the reminder is about: the fast's own day (a day after the popup when it shows the day before). */
    static Calendar fastDay(Item it, Calendar show) {
        Calendar t = (Calendar) show.clone();
        if ((isFast(it.kind) || it.kind == OCCASION) && it.dayBefore) t.add(Calendar.DAY_OF_MONTH, 1);
        return t;
    }

    private static boolean matches(Item it, Calendar target, int hijriAdj) {
        if (it.kind == OCCASION) return occasion(Hijri.of(target, hijriAdj)) >= 0;
        if (it.kind == WHITE_FAST) {
            int day = Hijri.of(target, hijriAdj)[2];
            return day >= 13 && day <= 15 && (it.hijriMask & (1 << (day - 13))) != 0;
        }
        return (it.daysMask & (1 << (target.get(Calendar.DAY_OF_WEEK) - 1))) != 0;
    }

    private static final int[] OCC_TITLE = {R.string.occ_ramadan, R.string.occ_fitr, R.string.occ_arafa,
        R.string.occ_adha, R.string.occ_newyear, R.string.occ_hijja10, R.string.occ_tasua, R.string.occ_ashura,
        R.string.occ_last10};
    private static final int[] OCC_MSG = {R.string.occ_ramadan_msg, R.string.occ_fitr_msg, R.string.occ_arafa_msg,
        R.string.occ_adha_msg, R.string.occ_newyear_msg, R.string.occ_hijja10_msg, R.string.occ_tasua_msg,
        R.string.occ_ashura_msg, R.string.occ_last10_msg};

    private static final int[] OCC_NAME = {R.string.occ_n_ramadan, R.string.occ_n_fitr, R.string.occ_n_arafa,
        R.string.occ_n_adha, R.string.occ_n_newyear, R.string.occ_n_hijja10, R.string.occ_n_tasua,
        R.string.occ_n_ashura, R.string.occ_n_last10};
    /** How many days ahead each occasion starts to show on the board: Ramadan 30 days, the others 10 days. */
    private static int window(int occ) {
        return occ == 0 ? 30 : 10;
    }

    static String occName(Context c, int occ) {
        return c.getString(OCC_NAME[occ]);
    }

    /** The occasion a popup shown at {@code shownAt} is about (the next day when it shows the evening before). */
    private static int occasionShown(Context c, Item it, long shownAt) {
        Calendar show = Calendar.getInstance();
        show.setTimeInMillis(shownAt);
        return occasion(Hijri.of(fastDay(it, show), Hijri.adjust(c)));
    }

    /** The coming occasions as {index, days from today}, soonest first, up to {@code max} and one year ahead. */
    static List<int[]> upcoming(Context c, int max) {
        List<int[]> out = new ArrayList<>();
        int adj = Hijri.adjust(c);
        Calendar d = Hijri.today(c);
        for (int i = 0; i <= 400 && out.size() < max; i++) {
            int o = occasion(Hijri.of(d, adj));
            if (o >= 0) out.add(new int[] {o, i});
            d.add(Calendar.DAY_OF_MONTH, 1);
        }
        return out;
    }

    /** {index, days} of the occasion to show on the board now (within its window), or null. */
    static int[] nearest(Context c) {
        for (int[] u : upcoming(c, 12)) {
            if (u[1] <= window(u[0])) return u;
        }
        return null;
    }

    static String occLine(Context c, int[] n) {
        String name = occName(c, n[0]);
        if (n[1] == 0) return c.getString(R.string.occ_line_today, name);
        if (n[1] == 1) return c.getString(R.string.occ_line_tomorrow, name);
        return c.getString(R.string.occ_line_left, n[1], name);
    }

    /** Which occasion a Hijri date {year, month, day} is (index into the lists above), or -1. */
    static int occasion(int[] h) {
        if (h[1] == 9 && h[2] == 1) return 0;
        if (h[1] == 10 && h[2] == 1) return 1;
        if (h[1] == 12 && h[2] == 9) return 2;
        if (h[1] == 12 && h[2] == 10) return 3;
        if (h[1] == 1 && h[2] == 1) return 4;
        if (h[1] == 12 && h[2] == 1) return 5;
        if (h[1] == 1 && h[2] == 9) return 6;
        if (h[1] == 1 && h[2] == 10) return 7;
        if (h[1] == 9 && h[2] == 20) return 8;
        return -1;
    }

    /** The reminders that are due at exactly {@code at} (several may share the same minute). */
    static List<Item> dueAt(Context c, long at) {
        List<Item> due = new ArrayList<>();
        for (Item it : load(c)) {
            if (next(c, it, at - 1L) == at) due.add(it);
        }
        return due;
    }

    // ---------------------------------------------------------------- the alarm

    /** Sets (or cancels) the one alarm for the earliest reminder after {@code max(now, after)}. */
    static void scheduleNext(Context c, long after) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        if (am == null) return;
        long from = Math.max(System.currentTimeMillis(), after);
        long best = -1;
        for (Item it : load(c)) {
            long t = next(c, it, from);
            if (t > 0 && (best < 0 || t < best)) best = t;
        }
        if (best < 0) {
            am.cancel(pending(c, 0));
            return;
        }
        PendingIntent pi = pending(c, best);
        try {
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, best, pi);
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, best, pi);
            }
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, best, pi);
        }
    }

    static void scheduleNext(Context c) {
        scheduleNext(c, 0L);
    }

    private static PendingIntent pending(Context c, long at) {
        Intent i = new Intent(c, ReminderReceiver.class).setAction(ACTION).putExtra("at", at);
        return PendingIntent.getBroadcast(c, REQ, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    // ---------------------------------------------------------------- the words

    static String title(Context c, Item it, long shownAt) {
        if (it.kind == OCCASION) {
            int o = occasionShown(c, it, shownAt);
            if (o >= 0) return it.dayBefore ? occName(c, o) : c.getString(OCC_TITLE[o]);
        }
        return title(c, it);
    }

    static String title(Context c, Item it) {
        if (it.kind == ADHKAR_AM) return c.getString(R.string.rem_adhkar_am_title);
        if (it.kind == ADHKAR_PM) return c.getString(R.string.rem_adhkar_pm_title);
        if (it.kind == OCCASION) return c.getString(R.string.rem_occasion_title);
        if (it.kind == KAHF) return c.getString(R.string.rem_kahf_title);
        if (it.kind == WEEK_FAST) return c.getString(R.string.rem_week_title);
        if (it.kind == WHITE_FAST) return c.getString(R.string.rem_white_title);
        return it.title.isEmpty() ? c.getString(R.string.rem_custom_default) : it.title;
    }

    /** The popup's second line: for the fasts, which day it is about; for a custom reminder, its text. */
    static String message(Context c, Item it, long shownAt) {
        if (it.kind == CUSTOM) return it.text;
        if (it.kind == ADHKAR_AM) return c.getString(R.string.rem_adhkar_am_msg);
        if (it.kind == ADHKAR_PM) return c.getString(R.string.rem_adhkar_pm_msg);
        if (it.kind == KAHF) return c.getString(R.string.rem_kahf_msg);
        if (it.kind == OCCASION) {
            int o = occasionShown(c, it, shownAt);
            if (o < 0) return "";
            return it.dayBefore ? c.getString(R.string.occ_line_tomorrow, occName(c, o)) : c.getString(OCC_MSG[o]);
        }
        Calendar show = Calendar.getInstance();
        show.setTimeInMillis(shownAt);
        Calendar target = fastDay(it, show);
        String when = c.getString(it.dayBefore ? R.string.rem_tomorrow : R.string.rem_today);
        if (it.kind == WEEK_FAST) {
            return c.getString(R.string.rem_week_msg, when, Hijri.weekdayAr(target));
        }
        int day = Hijri.of(target, Hijri.adjust(c))[2];
        return c.getString(R.string.rem_white_msg, when, day);
    }
}
