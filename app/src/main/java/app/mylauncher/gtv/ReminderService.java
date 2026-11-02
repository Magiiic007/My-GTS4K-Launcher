package app.mylauncher.gtv;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Shows the reminder popups one after another. A popup stays until it is closed with OK or Back, unless the reminder
 * was given a duration, and it does not pause or touch whatever is playing.
 */
public class ReminderService extends Service {
    private static final String CHANNEL = "reminder";
    private static final int NOTE_ID = 9;

    private static final class Entry {
        final String title, text;
        final int minutes;

        Entry(String title, String text, int minutes) {
            this.title = title;
            this.text = text;
            this.minutes = minutes;
        }
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Entry> queue = new ArrayList<>();
    private final Runnable autoClose = new Runnable() {
        @Override
        public void run() {
            closeCurrent();
        }
    };
    private Popup popup;
    private PowerManager.WakeLock wake;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        goForeground();
        if (wake == null) {
            PowerManager pm = getSystemService(PowerManager.class);
            if (pm != null) {
                wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "magiic:reminder");
                wake.acquire(10L * 60L * 1000L);
            }
        }
        if (intent != null) {
            List<String> titles = intent.getStringArrayListExtra("titles");
            List<String> texts = intent.getStringArrayListExtra("texts");
            List<Integer> minutes = intent.getIntegerArrayListExtra("minutes");
            if (titles != null && texts != null && minutes != null) {
                for (int i = 0; i < titles.size() && i < texts.size() && i < minutes.size(); i++) {
                    queue.add(new Entry(titles.get(i), texts.get(i), minutes.get(i)));
                }
            }
        }
        if (popup == null) showNext();
        return START_NOT_STICKY;
    }

    private void goForeground() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(
            CHANNEL, getString(R.string.rem_channel), NotificationManager.IMPORTANCE_LOW));
        Notification n = new Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_bell)
            .setContentTitle(getString(R.string.rem_channel))
            .setOngoing(true)
            .build();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTE_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTE_ID, n);
        }
    }

    private void showNext() {
        handler.removeCallbacks(autoClose);
        if (queue.isEmpty()) {
            finish();
            return;
        }
        Entry e = queue.remove(0);
        popup = Popup.show(this, e.title, e.text, getString(R.string.rem_hint), new Popup.OnClose() {
            @Override
            public void closed() {
                closeCurrent();
            }
        });
        if (popup == null) {
            // No permission to draw over other apps: nothing can be shown, so move on.
            showNext();
            return;
        }
        beep();
        if (e.minutes > 0) handler.postDelayed(autoClose, e.minutes * 60000L);
    }

    private void closeCurrent() {
        handler.removeCallbacks(autoClose);
        if (popup != null) popup.hide();
        popup = null;
        showNext();
    }

    /** One short, soft beep so the popup is noticed; it does not take the audio from anything. */
    private void beep() {
        try {
            final ToneGenerator tone = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 60);
            tone.startTone(ToneGenerator.TONE_PROP_ACK, 400);
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    tone.release();
                }
            }, 800L);
        } catch (RuntimeException ignored) {
            // No tone; the popup is enough.
        }
    }

    private void finish() {
        if (wake != null && wake.isHeld()) wake.release();
        wake = null;
        stopForeground(true);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (popup != null) popup.hide();
        popup = null;
        if (wake != null && wake.isHeld()) wake.release();
        super.onDestroy();
    }
}
