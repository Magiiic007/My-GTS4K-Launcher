package app.mylauncher.gtv;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Taps on the widgets: marking a prayer, counting the tasbih, changing the dhikr. */
public class WidgetReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        try {
            Widgets.handle(c.getApplicationContext(), i);
        } catch (RuntimeException ignored) {
            // a failed tap is ignored; the next one works
        }
    }
}
