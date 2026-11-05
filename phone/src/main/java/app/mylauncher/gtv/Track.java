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
        ed.apply();
        PrayerNote.refresh(c);
        return (b >> idx & 1) != 0;
    }

    static int count(int bits) {
        return Integer.bitCount(bits & 31);
    }
}
