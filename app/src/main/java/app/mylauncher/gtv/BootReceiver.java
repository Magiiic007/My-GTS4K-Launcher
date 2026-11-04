package app.mylauncher.gtv;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * The system forgets our alarms after a restart, an app update, or when the clock or time zone is changed: set them
 * again. A restart brings the radio back; an update brings it back too if it was playing before.
 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        Scheduler.scheduleNext(c);
        Reminders.scheduleNext(c);
        String action = i.getAction();
        // A real start of the device plays the last station (the Cairo one the first time); if the network isn't ready
        // yet it keeps retrying by itself. An update brings it back only if it was playing when the update came.
        boolean boot = Intent.ACTION_BOOT_COMPLETED.equals(action);
        boolean update = Intent.ACTION_MY_PACKAGE_REPLACED.equals(action);
        if (boot || (update && RadioService.shouldResume(c))) {
            try {
                RadioService.play(c, RadioService.lastStation(c));
            } catch (RuntimeException e) {
                // Android refused to start it from the background; the home screen starts it when it opens.
            }
        }
    }
}
