package app.mylauncher.gtv;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Rings at prayer time: lines up the next alarm, then starts the adhan. */
public class AdhanReceiver extends BroadcastReceiver {
    private static final long TOO_LATE_MS = 10L * 60L * 1000L;

    @Override
    public void onReceive(Context c, Intent i) {
        boolean test = i.getBooleanExtra("test", false);
        long at = i.getLongExtra("at", 0L);

        // The next prayer first, so whatever happens below there is always an alarm waiting.
        if (!test) Scheduler.scheduleNext(c);

        // The TV was off at prayer time and has only just woken: don't play an adhan for a prayer long gone.
        if (!test && at > 0 && System.currentTimeMillis() - at > TOO_LATE_MS) return;

        Intent s = new Intent(c, AdhanService.class)
            .putExtra("index", i.getIntExtra("index", PrayerCalc.DHUHR))
            .putExtra("friday", i.getBooleanExtra("friday", false))
            .putExtra("test", test);
        try {
            c.startForegroundService(s);
        } catch (RuntimeException e) {
            // Android refused to start it from the background; there is nothing more this receiver can do.
        }
    }
}
