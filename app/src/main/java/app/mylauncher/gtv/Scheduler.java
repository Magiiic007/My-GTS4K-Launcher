package app.mylauncher.gtv;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/**
 * Sets the system alarm for the next adhan or iqama. One alarm is kept at a time: when it rings, the receiver sets
 * the one after it. It is set again whenever the app opens, the city or a setting changes, or the TV restarts.
 */
final class Scheduler {
    private Scheduler() {}

    private static final int REQ_REAL = 1;
    private static final String ACTION = "app.mylauncher.gtv.ADHAN";

    /** Alarm for the next adhan or iqama of the chosen city (replaces the previous one). */
    static void scheduleNext(Context c) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        if (am == null) return;
        Cities.City city = Cities.selected(c);
        if (city == null) {
            am.cancel(pending(c, REQ_REAL, 0, false, 0, false, false, false));
            return;
        }
        PrayerCalc.Event e = PrayerCalc.nextAlarm(
            city, System.currentTimeMillis(), PrayerPrefs.adjust(c), PrayerPrefs.iqama(c), PrayerPrefs.firstAdhan(c));
        if (e == null) return;
        set(am, e.at, pending(c, REQ_REAL, e.index, e.friday, e.at, false, e.iqama, e.first));
    }

    private static PendingIntent pending(Context c, int request, int index, boolean friday, long at,
                                         boolean test, boolean iqama, boolean first) {
        Intent i = new Intent(c, AdhanReceiver.class).setAction(ACTION)
            .putExtra("index", index)
            .putExtra("friday", friday)
            .putExtra("at", at)
            .putExtra("test", test)
            .putExtra("iqama", iqama)
            .putExtra("first", first);
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
