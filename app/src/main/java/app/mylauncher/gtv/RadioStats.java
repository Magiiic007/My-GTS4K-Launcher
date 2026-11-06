package app.mylauncher.gtv;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * How long each station has been listened to on this device. The lists show the most listened first, inside their
 * own group (the radio stations, then the reciters), and the Next and Previous buttons follow the same order.
 */
final class RadioStats {
    private RadioStats() {}

    private static Context ctx;
    private static long startMs;
    private static int startIdx = -1;

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("magiic", Context.MODE_PRIVATE);
    }

    /** The station began to play. */
    static void start(Context c, int idx) {
        ctx = c.getApplicationContext();
        startMs = System.currentTimeMillis();
        startIdx = idx;
    }

    /** The station stopped playing (or was switched): its time so far is added. */
    static void stop() {
        if (ctx == null || startIdx < 0) return;
        long ms = System.currentTimeMillis() - startMs;
        int i = startIdx;
        startIdx = -1;
        if (ms < 1000L || ms > 24L * 3600_000L) return;
        SharedPreferences s = sp(ctx);
        s.edit().putLong("rl_" + i, s.getLong("rl_" + i, 0L) + ms).apply();
    }

    /** Minutes listened to a station (counting the stretch that is still playing). */
    static long minutes(Context c, int idx) {
        long ms = sp(c).getLong("rl_" + idx, 0L);
        if (idx == startIdx) ms += System.currentTimeMillis() - startMs;
        return ms / 60_000L;
    }

    private static boolean reciter(int i) {
        return RadioService.STATIONS[i].url.contains("qurango");
    }

    /** All station numbers: the radio stations first, then the reciters, each group with the most listened first. */
    static Integer[] order(final Context c) {
        final int n = RadioService.STATIONS.length;
        Integer[] a = new Integer[n];
        for (int i = 0; i < n; i++) a[i] = i;
        final long[] t = new long[n];
        for (int i = 0; i < n; i++) t[i] = minutes(c, i);
        java.util.Arrays.sort(a, new java.util.Comparator<Integer>() {
            @Override
            public int compare(Integer x, Integer y) {
                boolean rx = reciter(x), ry = reciter(y);
                if (rx != ry) return rx ? 1 : -1;
                if (t[x] != t[y]) return t[x] > t[y] ? -1 : 1;
                return Integer.compare(x, y);
            }
        });
        return a;
    }

    /** The station after (or before) this one in that order. */
    static int step(Context c, int from, int d) {
        Integer[] a = order(c);
        int pos = 0;
        for (int i = 0; i < a.length; i++) if (a[i] == from) pos = i;
        return a[(pos + a.length + d) % a.length];
    }

    /** "5 ساعات" style text for a station's listening time, or null if none. */
    static String label(Context c, int idx) {
        long m = minutes(c, idx);
        if (m < 1) return null;
        if (m < 60) return m + " دقيقة استماع";
        long h = m / 60;
        return h + " ساعة استماع";
    }
}
