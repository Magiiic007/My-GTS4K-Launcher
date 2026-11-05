package app.mylauncher.gtv;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Wakes at the time of the next dhikr card, which shows it (with its notice) and sets the following alarm. */
public class OverlayReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        OverlayService.fire(c);
    }
}
