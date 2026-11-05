package app.mylauncher.gtv;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

import java.util.ArrayList;

/** Shows the reminders that are due as ordinary notifications (a heads-up banner), then stops. */
public class ReminderService extends Service {
    private static final String CHANNEL = "reminders";

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        ArrayList<String> titles = intent == null ? null : intent.getStringArrayListExtra("titles");
        ArrayList<String> texts = intent == null ? null : intent.getStringArrayListExtra("texts");
        ArrayList<Integer> minutes = intent == null ? null : intent.getIntegerArrayListExtra("minutes");
        if (Wc.on(this)) titles = null; // bathroom mode: nothing is shown
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(
            CHANNEL, getString(R.string.rem_channel), NotificationManager.IMPORTANCE_HIGH));
        int base = (int) (System.currentTimeMillis() / 1000L);
        boolean first = true;
        if (titles != null) {
            for (int i = 0; i < titles.size(); i++) {
                Notification.Builder b = new Notification.Builder(this, CHANNEL)
                    .setSmallIcon(R.drawable.ic_crescent)
                    .setContentTitle(titles.get(i))
                    .setContentText(texts != null && i < texts.size() ? texts.get(i) : "")
                    .setStyle(new Notification.BigTextStyle().bigText(texts != null && i < texts.size() ? texts.get(i) : ""))
                    .setAutoCancel(true);
                int min = minutes != null && i < minutes.size() ? minutes.get(i) : 0;
                if (min > 0) b.setTimeoutAfter(min * 60000L);
                Notification n = b.build();
                if (first) {
                    if (Build.VERSION.SDK_INT >= 29) {
                        startForeground(base, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
                    } else {
                        startForeground(base, n);
                    }
                    first = false;
                } else {
                    nm.notify(base + i, n);
                }
            }
        }
        if (first) {
            // Nothing to show, but a foreground service was started: put up something and let go at once.
            Notification n = new Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_crescent).build();
            startForeground(base, n);
            stopForeground(true);
        } else {
            stopForeground(Service.STOP_FOREGROUND_DETACH);
        }
        stopSelf();
        return START_NOT_STICKY;
    }
}
