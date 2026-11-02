package app.mylauncher.gtv;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * What the user sets for each of the five prayers: minutes added to (or taken from) the calculated time, and the
 * minutes between the adhan and the iqama (0 means no iqama alert).
 */
final class PrayerPrefs {
    private PrayerPrefs() {}

    private static SharedPreferences p(Context c) {
        return c.getSharedPreferences("magiic", Context.MODE_PRIVATE);
    }

    static int[] adjust(Context c) {
        int[] a = new int[5];
        for (int i = 0; i < 5; i++) a[i] = p(c).getInt("adj_" + i, 0);
        return a;
    }

    static int[] iqama(Context c) {
        int[] a = new int[5];
        for (int i = 0; i < 5; i++) a[i] = p(c).getInt("iqama_" + i, 0);
        return a;
    }

    static void setAdjust(Context c, int index, int minutes) {
        p(c).edit().putInt("adj_" + index, minutes).apply();
    }

    static void setIqama(Context c, int index, int minutes) {
        p(c).edit().putInt("iqama_" + index, minutes).apply();
    }

    static String adjustText(Context c, int minutes) {
        return minutes == 0 ? c.getString(R.string.no_adjust) : c.getString(R.string.minutes_signed, minutes);
    }

    static String iqamaText(Context c, int minutes) {
        return minutes == 0 ? c.getString(R.string.iqama_off) : c.getString(R.string.minutes_plain, minutes);
    }
}
