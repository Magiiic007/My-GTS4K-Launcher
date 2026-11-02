package app.mylauncher.gtv;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** After a restart or an app update the system forgets our alarm; set it again. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        Scheduler.scheduleNext(c);
        // Only a real start of the device brings the radio back, not an app update. It plays the last station
        // (the Cairo one the first time); if the network isn't ready yet it keeps retrying by itself.
        if (Intent.ACTION_BOOT_COMPLETED.equals(i.getAction())) {
            try {
                RadioService.play(c, RadioService.lastStation(c));
            } catch (RuntimeException e) {
                // Android refused to start it from the background; the radio starts from its button instead.
            }
        }
    }
}
