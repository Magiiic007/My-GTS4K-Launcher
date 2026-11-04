package app.mylauncher.gtv;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Rings at each change of the prayer notification (adhan, iqama, silent window) and after a restart or update. */
public class NoteReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        PrayerNote.refresh(c);
    }
}
