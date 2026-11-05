package app.mylauncher.gtv;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * The system forgets our alarms after a restart, an app update, or when the clock or time zone is changed: set them
 * again. (The phone's radio never starts by itself.)
 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        Scheduler.scheduleNext(c);
        Reminders.scheduleNext(c);
        BubbleService.sync(c);
        OverlayService.sync(c);
    }
}
