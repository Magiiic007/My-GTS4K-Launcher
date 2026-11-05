package app.mylauncher.gtv;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Wakes at the start of the morning and evening adhkar times to show the round icon, and sets the next alarm. */
public class BubbleReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        BubbleService.sync(c);
    }
}
