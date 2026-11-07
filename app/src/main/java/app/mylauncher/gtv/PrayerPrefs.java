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

    /** Index of the Friday prayer in {@link #adjust} and {@link #iqama} (after the five daily prayers). */
    static final int FRIDAY = 5;

    /** Minutes added to each of the five prayers, then to the Friday prayer (it starts from Dhuhr's value). */
    static int[] adjust(Context c) {
        int[] a = new int[6];
        for (int i = 0; i < 5; i++) a[i] = p(c).getInt("adj_" + i, 0);
        a[FRIDAY] = p(c).getInt("adj_5", a[1]);
        return a;
    }

    static int[] iqama(Context c) {
        int[] a = new int[6];
        for (int i = 0; i < 5; i++) a[i] = p(c).getInt("iqama_" + i, 0);
        a[FRIDAY] = p(c).getInt("iqama_5", a[1]);
        return a;
    }

    /** The iqama delay of prayer {@code index}; Dhuhr on a Friday uses the Friday prayer's own setting. */
    static int iqamaOf(int[] iqama, int index, boolean friday) {
        return friday && index == 1 && iqama.length > FRIDAY ? iqama[FRIDAY] : iqama[index];
    }

    /** Minutes before the Friday adhan at which the first adhan rings (0 = off). */
    static int firstAdhan(Context c) {
        return p(c).getInt("first_adhan", 30);
    }

    static void setFirstAdhan(Context c, int minutes) {
        p(c).edit().putInt("first_adhan", minutes).apply();
    }

    static String firstAdhanText(Context c, int minutes) {
        return minutes == 0 ? c.getString(R.string.first_adhan_off) : c.getString(R.string.minutes_plain, minutes);
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

    /** How the adhan of prayer {@code index} sounds: 0 sound, 1 vibration only, 2 silent. The Friday prayer follows Dhuhr until it is set. */
    static int adhanMode(SharedPreferences sp, int index, boolean friday) {
        if (friday && sp.contains("ad_mode_" + FRIDAY)) return sp.getInt("ad_mode_" + FRIDAY, 0);
        return sp.getInt("ad_mode_" + index, 0);
    }

    /** Whether the adhan still sounds while the phone is on silent or vibrate. */
    static boolean adhanRing(SharedPreferences sp, int index, boolean friday) {
        if (friday && sp.contains("ad_ring_" + FRIDAY)) return sp.getBoolean("ad_ring_" + FRIDAY, true);
        return sp.getBoolean("ad_ring_" + index, true);
    }
}
