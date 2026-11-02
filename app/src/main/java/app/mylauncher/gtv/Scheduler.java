package app.mylauncher.gtv;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/**
 * Sets the system alarm for the next prayer. One alarm is kept at a time: when it rings, the receiver sets the
 * one after it. It is set again whenever the app opens, the city changes, or the TV restarts.
 */
final class Scheduler {
    private Scheduler() {}

    private static final int REQ_REAL = 1;
    private static final int REQ_TEST = 2;
    private static final String ACTION = "app.mylauncher.gtv.ADHAN";

    /** Alarm for the next prayer of the chosen city (replaces the previous one). */
    static void scheduleNext(Context c) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        if (am == null) return;
        Cities.City city = Cities.selected(c);
        if (city == null) {
            am.cancel(pending(c, REQ_REAL, 0, false, 0, false));
            return;
        }
        PrayerCalc.Next n = PrayerCalc.next(city, System.currentTimeMillis());
        set(am, n.at, pending(c, REQ_REAL, n.index, n.friday, n.at, false));
    }

    /** A test adhan one minute from now, through exactly the same path as a real prayer. */
    static void scheduleTest(Context c) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        if (am == null) return;
        long at = System.currentTimeMillis() + 60000L;
        set(am, at, pending(c, REQ_TEST, PrayerCalc.DHUHR, false, at, true));
    }

    private static PendingIntent pending(Context c, int request, int index, boolean friday, long at, boolean test) {
        Intent i = new Intent(c, AdhanReceiver.class).setAction(ACTION)
            .putExtra("index", index)
            .putExtra("friday", friday)
            .putExtra("at", at)
            .putExtra("test", test);
        return PendingIntent.getBroadcast(c, request, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void set(AlarmManager am, long at, PendingIntent pi) {
        try {
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            }
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
        }
    }
}
