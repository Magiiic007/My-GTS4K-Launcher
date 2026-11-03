package app.mylauncher.gtv;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.content.res.AssetFileDescriptor;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

/**
 * The call to prayer. When the alarm rings this: takes the audio away from whatever is playing (and presses Pause
 * on it), shows a full-screen popup over the top, plays the adhan, and when it ends removes the popup, gives the
 * audio back and presses Play so the video or music carries on.
 *
 * The popup needs the "display over other apps" permission. Without it the adhan still plays and pauses the other
 * app, but nothing is drawn.
 *
 * The adhan recording is res/raw/adhan.mp3. Without that file a short alert sound plays instead, so the whole
 * path can still be tested.
 */
public class AdhanService extends Service {
    private static final String CHANNEL = "adhan";
    private static final int NOTE_ID = 7;
    private static final long MAX_ADHAN_MS = 6L * 60L * 1000L;
    private static final long MAX_FALLBACK_MS = 15L * 1000L;
    private static final long MAX_IQAMA_MS = 60L * 1000L;
    /** The iqama alert without a recording: three double beeps, and the popup stays long enough to read. */
    private static final long IQAMA_BEEPS_END_MS = 6500L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable finisher = new Runnable() {
        @Override
        public void run() {
            finishAdhan();
        }
    };

    private AudioManager audio;
    private AudioFocusRequest focus;
    private MediaPlayer player;
    private PowerManager.WakeLock wake;
    private Popup popup;
    private boolean started;
    private boolean finished;
    private boolean pausedOthers;
    private boolean isIqama;
    private int prayerIndex = PrayerCalc.DHUHR;
    private int hintRes = R.string.popup_hint;
    private ToneGenerator tone;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // A second alarm while the adhan is still playing: ignore it.
        if (started) return START_NOT_STICKY;
        started = true;

        int index = intent != null ? intent.getIntExtra("index", PrayerCalc.DHUHR) : PrayerCalc.DHUHR;
        prayerIndex = index;
        boolean friday = intent != null && intent.getBooleanExtra("friday", false);
        boolean test = intent != null && intent.getBooleanExtra("test", false);
        isIqama = intent != null && intent.getBooleanExtra("iqama", false);
        boolean first = intent != null && intent.getBooleanExtra("first", false);
        hintRes = isIqama ? R.string.iqama_hint : R.string.popup_hint;

        Cities.City city = Cities.selected(this);
        String title = getString(isIqama ? R.string.iqama_title : R.string.popup_title,
            PrayerCalc.name(this, index, friday));
        if (test) title = title + " " + getString(R.string.popup_test);
        String where = city != null ? city.name("ar".equals(Locale.getDefault().getLanguage())) : "";
        if (first) {
            title = getString(R.string.first_adhan_title);
            where = where + "\n" + getString(R.string.first_adhan_sub, PrayerPrefs.firstAdhan(this));
        }

        goForeground(title);

        PowerManager pm = getSystemService(PowerManager.class);
        if (pm != null) {
            wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "magiic:adhan");
            wake.acquire(MAX_ADHAN_MS + 30000L);
        }

        audio = getSystemService(AudioManager.class);
        takeAudio();
        showOverlay(title, where);
        playSound();
        return START_NOT_STICKY;
    }

    private void goForeground(String title) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(
            CHANNEL, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW));
        Notification n = new Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_crescent)
            .setContentTitle(title)
            .setContentText(getString(hintRes))
            .setOngoing(true)
            .build();
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTE_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NOTE_ID, n);
        }
    }

    private static AudioAttributes attrs() {
        return new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build();
    }

    /** Takes the audio from other apps; if something was playing, presses Pause on it as well. */
    private void takeAudio() {
        if (audio == null) return;
        // The radio steps aside by itself when it loses the focus; pressing Pause on it would do nothing useful.
        boolean wasPlaying = audio.isMusicActive() && !RadioService.on();
        focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            .setAudioAttributes(attrs())
            .setOnAudioFocusChangeListener(new AudioManager.OnAudioFocusChangeListener() {
                @Override
                public void onAudioFocusChange(int change) {
                }
            })
            .build();
        audio.requestAudioFocus(focus);
        if (wasPlaying) {
            pausedOthers = true;
            mediaKey(audio, KeyEvent.KEYCODE_MEDIA_PAUSE);
        }
    }

    private static void mediaKey(AudioManager am, int code) {
        long t = SystemClock.uptimeMillis();
        am.dispatchMediaKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0));
        am.dispatchMediaKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0));
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics()));
    }

    /** The full-screen popup (themed like the home screen). Back or OK closes it and stops the adhan. */
    private void showOverlay(String title, String where) {
        popup = Popup.show(this, title, where, getString(hintRes), new Popup.OnClose() {
            @Override
            public void closed() {
                finishAdhan();
            }
        });
    }

    private void hideOverlay() {
        if (popup != null) popup.hide();
        popup = null;
    }

    /** The adhan (res/raw/adhan.mp3) or, for an iqama, a shorter alert (res/raw/iqama.mp3). */
    private void playSound() {
        // Recordings are looked for in res/raw: iqama.mp3; adhan_fajr.mp3 for Fajr (its adhan has extra words),
        // then adhan.mp3 for every prayer.
        int res = 0;
        if (isIqama) {
            res = rawId("iqama");
        } else {
            if (prayerIndex == PrayerCalc.FAJR) res = rawId("adhan_fajr");
            if (res == 0) res = rawId("adhan");
        }
        long cap = isIqama ? MAX_IQAMA_MS : MAX_ADHAN_MS;
        if (res != 0 && playMedia(null, res, cap)) return;
        if (isIqama) {
            // No iqama.mp3 added yet: three double beeps, clearly different from the adhan.
            beepsForIqama();
            return;
        }
        // No adhan.mp3 added yet: a short system alert, so the popup and the pause/resume can be tested.
        Uri alert = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (alert == null || !playMedia(alert, 0, MAX_FALLBACK_MS)) beep();
    }

    private int rawId(String name) {
        return getResources().getIdentifier(name, "raw", getPackageName());
    }

    /** Plays a recording (a Uri, or a raw resource when the Uri is null); false if it could not be started. */
    private boolean playMedia(Uri uri, int res, long capMs) {
        try {
            player = new MediaPlayer();
            player.setAudioAttributes(attrs());
            if (uri != null) {
                player.setDataSource(this, uri);
            } else {
                AssetFileDescriptor afd = getResources().openRawResourceFd(res);
                try {
                    player.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
                } finally {
                    afd.close();
                }
            }
            player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    finishAdhan();
                }
            });
            player.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    finishAdhan();
                    return true;
                }
            });
            player.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    mp.start();
                }
            });
            player.prepareAsync();
            handler.postDelayed(finisher, capMs);
            return true;
        } catch (Exception e) {
            releasePlayer();
            return false;
        }
    }

    /** The last resort for the adhan when nothing can be played: three seconds of beeps. */
    private void beep() {
        try {
            tone = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);
            tone.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 3000);
        } catch (RuntimeException ignored) {
            // No sound at all; the popup still closes on time.
        }
        handler.postDelayed(finisher, 3500L);
    }

    private void beepsForIqama() {
        try {
            tone = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);
        } catch (RuntimeException ignored) {
            tone = null;
        }
        for (int k = 0; k < 3; k++) {
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (tone != null && !finished) tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 600);
                }
            }, k * 1800L);
        }
        handler.postDelayed(finisher, IQAMA_BEEPS_END_MS);
    }

    private void releaseTone() {
        if (tone != null) {
            try {
                tone.release();
            } catch (RuntimeException ignored) {
                // Already released.
            }
            tone = null;
        }
    }

    private void releasePlayer() {
        if (player != null) {
            try {
                player.release();
            } catch (RuntimeException ignored) {
                // Already released.
            }
            player = null;
        }
    }

    /** The adhan is over (or was stopped): popup away, audio back, and the other app carries on. */
    private void finishAdhan() {
        if (finished) return;
        finished = true;
        handler.removeCallbacksAndMessages(null);
        releasePlayer();
        releaseTone();
        hideOverlay();
        final AudioManager am = audio;
        if (am != null && focus != null) am.abandonAudioFocusRequest(focus);
        if (pausedOthers && am != null) {
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    mediaKey(am, KeyEvent.KEYCODE_MEDIA_PLAY);
                }
            }, 600L);
        }
        if (wake != null && wake.isHeld()) wake.release();
        stopForeground(true);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        // Killed by the system before the adhan ended: at least take down the popup and give the audio back.
        if (!finished) {
            finished = true;
            handler.removeCallbacksAndMessages(null);
            releasePlayer();
            releaseTone();
            hideOverlay();
            if (audio != null && focus != null) audio.abandonAudioFocusRequest(focus);
            if (wake != null && wake.isHeld()) wake.release();
        }
        super.onDestroy();
    }
}
