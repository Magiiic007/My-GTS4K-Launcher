package app.mylauncher.gtv;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Rings at prayer time: lines up the next alarm, then starts the adhan. */
public class AdhanReceiver extends BroadcastReceiver {
    private static final long TOO_LATE_MS = 10L * 60L * 1000L;
    /** An iqama alert is only worth showing at its moment. */
    private static final long IQAMA_TOO_LATE_MS = 2L * 60L * 1000L;

    @Override
    public void onReceive(Context c, Intent i) {
        // For the phone's test screen: when the alarm last reached us, and why the service could not start.
        c.getSharedPreferences("magiic", Context.MODE_PRIVATE).edit()
            .putLong("dbg_rx", System.currentTimeMillis()).apply();
        boolean test = i.getBooleanExtra("test", false);
        boolean iqama = i.getBooleanExtra("iqama", false);
        long at = i.getLongExtra("at", 0L);

        // The next prayer first, so whatever happens below there is always an alarm waiting.
        if (!test) Scheduler.scheduleNext(c);

        // The TV was off at prayer time and has only just woken: don't play an adhan for a prayer long gone.
        long limit = iqama ? IQAMA_TOO_LATE_MS : TOO_LATE_MS;
        if (!test && at > 0 && System.currentTimeMillis() - at > limit) return;

        Intent s = new Intent(c, AdhanService.class)
            .putExtra("index", i.getIntExtra("index", PrayerCalc.DHUHR))
            .putExtra("friday", i.getBooleanExtra("friday", false))
            .putExtra("test", test)
            .putExtra("iqama", iqama)
            .putExtra("first", i.getBooleanExtra("first", false));
        try {
            c.startForegroundService(s);
        } catch (RuntimeException e) {
            c.getSharedPreferences("magiic", Context.MODE_PRIVATE).edit()
                .putString("dbg_err", String.valueOf(e)).apply();
            // Android refused to start it from the background; there is nothing more this receiver can do.
        }
    }
}
