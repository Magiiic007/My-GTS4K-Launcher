package app.mylauncher.gtv;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

/** Which of the five prayers were marked as prayed, one bit each (0 Fajr .. 4 Isha) per day. Saved with the backup. */
final class Track {
    private Track() {
    }

    private static SharedPreferences p(Context c) {
        return c.getSharedPreferences("magiic", Context.MODE_PRIVATE);
    }

    private static TimeZone zone(Context c) {
        Cities.City city = Cities.selected(c);
        return city != null ? PrayerCalc.zone(city) : TimeZone.getDefault();
    }

    static Calendar cal(Context c, long millis) {
        Calendar k = Calendar.getInstance(zone(c), Locale.ENGLISH);
        k.setTimeInMillis(millis);
        return k;
    }

    static String key(Calendar k) {
        return "trk_" + (k.get(Calendar.YEAR) * 10000 + (k.get(Calendar.MONTH) + 1) * 100 + k.get(Calendar.DAY_OF_MONTH));
    }

    static int bits(Context c, Calendar k) {
        return p(c).getInt(key(k), 0);
    }

    static boolean done(Context c, long dayMillis, int idx) {
        return (bits(c, cal(c, dayMillis)) >> idx & 1) != 0;
    }

    static boolean toggle(Context c, long dayMillis, int idx) {
        Calendar k = cal(c, dayMillis);
        int b = bits(c, k) ^ (1 << idx);
        SharedPreferences.Editor ed = p(c).edit();
        if (b == 0) ed.remove(key(k));
        else ed.putInt(key(k), b);
        // A prayer already counted as missed that is now marked as prayed comes off the make-up count; one whose time
        // is over and gets un-marked goes back on it.
        String mk = "qmiss_" + dayNum(k);
        int miss = p(c).getInt(mk, 0);
        boolean on = (b >> idx & 1) != 0;
        if (on && (miss >> idx & 1) != 0) {
            miss &= ~(1 << idx);
            if (miss == 0) ed.remove(mk);
            else ed.putInt(mk, miss);
            ed.putInt("qada_" + idx, Math.max(0, p(c).getInt("qada_" + idx, 0) - 1));
        } else if (!on) {
            long end = endOf(c, dayMillis, idx);
            long scanned = p(c).getLong("qada_scan", 0L);
            if (end > 0 && end <= scanned && end > p(c).getLong("qada_from", Long.MAX_VALUE)) {
                ed.putInt(mk, miss | (1 << idx));
                ed.putInt("qada_" + idx, p(c).getInt("qada_" + idx, 0) + 1);
            }
        }
        ed.apply();
        PrayerNote.refresh(c);
        return (b >> idx & 1) != 0;
    }

    static int count(int bits) {
        return Integer.bitCount(bits & 31);
    }

    // ------------------------------------------------------------ make-up prayers (qada)
    static int dayNum(Calendar k) {
        return k.get(Calendar.YEAR) * 10000 + (k.get(Calendar.MONTH) + 1) * 100 + k.get(Calendar.DAY_OF_MONTH);
    }

    /** When the time of a prayer runs out: sunrise for Fajr, the next prayer, or the middle of the night for Isha. */
    static long endOf(Context c, long dayMillis, int idx) {
        Cities.City city = Cities.selected(c);
        if (city == null) return 0L;
        int[] adj = PrayerPrefs.adjust(c);
        long[] t = PrayerCalc.day(city, dayMillis, 0, adj);
        if (idx == PrayerCalc.FAJR) return PrayerCalc.sunrise(city, dayMillis, 0, adj);
        if (idx < 4) return t[idx + 1];
        long nextFajr = PrayerCalc.day(city, dayMillis, 1, adj)[0];
        return t[3] + (nextFajr - t[3]) / 2L;
    }

    /** When counting began: the first time the app was installed (changeable from the make-up page). */
    static long from(Context c) {
        long f = p(c).getLong("qada_from", 0L);
        if (f == 0L) {
            try {
                f = c.getPackageManager().getPackageInfo(c.getPackageName(), 0).firstInstallTime;
            } catch (Exception e) {
                f = System.currentTimeMillis();
            }
            p(c).edit().putLong("qada_from", f).apply();
        }
        return f;
    }

    static void setFrom(Context c, long millis) {
        p(c).edit().putLong("qada_from", millis).putLong("qada_scan", millis).apply();
    }

    /**
     * Every prayer whose time has run out since the last look, and was not marked as prayed, is added to the make-up
     * count of its prayer (once). Counting starts from {@link #from}.
     */
    static void scan(Context c) {
        try {
            Cities.City city = Cities.selected(c);
            if (city == null) return;
            long now = System.currentTimeMillis();
            long from = from(c);
            long last = Math.max(from, p(c).getLong("qada_scan", from));
            int[] adj = PrayerPrefs.adjust(c);
            Calendar day = cal(c, last);
            day.add(Calendar.DAY_OF_MONTH, -1);
            SharedPreferences.Editor ed = p(c).edit();
            int[] added = new int[5];
            for (int guard = 0; guard < 500; guard++) {
                long dm = day.getTimeInMillis();
                long[] t = PrayerCalc.day(city, dm, 0, adj);
                if (t[0] > now) break;
                long rise = PrayerCalc.sunrise(city, dm, 0, adj);
                long nextFajr = PrayerCalc.day(city, dm, 1, adj)[0];
                int bits = bits(c, day);
                String mk = "qmiss_" + dayNum(day);
                int miss = p(c).getInt(mk, 0);
                for (int i = 0; i < 5; i++) {
                    long end = i == 0 ? rise : i < 4 ? t[i + 1] : t[3] + (nextFajr - t[3]) / 2L;
                    if (end <= last || end > now || end <= from) continue;
                    if ((bits >> i & 1) != 0 || (miss >> i & 1) != 0) continue;
                    miss |= 1 << i;
                    added[i]++;
                }
                if (miss != 0) ed.putInt(mk, miss);
                day.add(Calendar.DAY_OF_MONTH, 1);
            }
            for (int i = 0; i < 5; i++) {
                if (added[i] > 0) ed.putInt("qada_" + i, p(c).getInt("qada_" + i, 0) + added[i]);
            }
            ed.putLong("qada_scan", now).apply();
        } catch (RuntimeException ignored) {
            // the count is tried again at the next look
        }
    }

    /** How many dated missed prayers of this kind are waiting. */
    static int pending(Context c, int idx) {
        int n = 0;
        for (java.util.Map.Entry<String, ?> e : p(c).getAll().entrySet()) {
            if (e.getKey().startsWith("qmiss_") && e.getValue() instanceof Integer && ((Integer) e.getValue() >> idx & 1) != 0) n++;
        }
        return n;
    }

    /** The oldest waiting missed prayer: {day as yyyymmdd, prayer index}, or null. */
    static int[] oldest(Context c) {
        int bestDay = Integer.MAX_VALUE, bestIdx = -1;
        for (java.util.Map.Entry<String, ?> e : p(c).getAll().entrySet()) {
            if (!e.getKey().startsWith("qmiss_") || !(e.getValue() instanceof Integer)) continue;
            int d;
            try {
                d = Integer.parseInt(e.getKey().substring(6));
            } catch (NumberFormatException x) {
                continue;
            }
            int m = (Integer) e.getValue();
            for (int i = 0; i < 5; i++) {
                if ((m >> i & 1) != 0 && (d < bestDay || (d == bestDay && i < bestIdx))) {
                    bestDay = d;
                    bestIdx = i;
                    break;
                }
            }
        }
        return bestIdx < 0 ? null : new int[] {bestDay, bestIdx};
    }

    /** A make-up prayer was prayed: the oldest waiting one of that kind is marked on its own day as made up. */
    static void settle(Context c, int idx) {
        int bestDay = Integer.MAX_VALUE;
        for (java.util.Map.Entry<String, ?> e : p(c).getAll().entrySet()) {
            if (!e.getKey().startsWith("qmiss_") || !(e.getValue() instanceof Integer) || ((Integer) e.getValue() >> idx & 1) == 0) continue;
            try {
                bestDay = Math.min(bestDay, Integer.parseInt(e.getKey().substring(6)));
            } catch (NumberFormatException ignored) {
                // not one of ours
            }
        }
        if (bestDay == Integer.MAX_VALUE) return;
        SharedPreferences sp = p(c);
        int miss = sp.getInt("qmiss_" + bestDay, 0) & ~(1 << idx);
        SharedPreferences.Editor ed = sp.edit();
        if (miss == 0) ed.remove("qmiss_" + bestDay);
        else ed.putInt("qmiss_" + bestDay, miss);
        ed.putInt("trk_" + bestDay, sp.getInt("trk_" + bestDay, 0) | (1 << idx));
        ed.putInt("qdone_" + bestDay, sp.getInt("qdone_" + bestDay, 0) | (1 << idx));
        ed.apply();
    }

    /** The prayers of a day that were made up later. */
    static int madeUp(Context c, Calendar k) {
        return p(c).getInt("qdone_" + dayNum(k), 0);
    }
}
