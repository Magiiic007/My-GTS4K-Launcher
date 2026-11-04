package app.mylauncher.gtv;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.content.res.AssetFileDescriptor;
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
import android.view.KeyEvent;

import java.util.Locale;

/**
 * The call to prayer on the phone. It plays in the background with no screen of its own: a small notification is
 * all Android requires. It is stopped only by turning the phone face down (see {@link FlipDetector}). While it plays
 * the radio (or any other app's sound) steps aside and comes back afterwards.
 */
public class AdhanService extends Service {
    private static final String CHANNEL = "adhan";
    private static final int NOTE_ID = 7;
    private static final long MAX_ADHAN_MS = 6L * 60L * 1000L;
    private static final long MAX_FALLBACK_MS = 15L * 1000L;
    private static final long MAX_IQAMA_MS = 60L * 1000L;
    private static final long IQAMA_BEEPS_END_MS = 6500L;

    /** True while an adhan or iqama is playing (the test screen reads it). */
    static volatile boolean playing;

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
    private FlipDetector flip;
    private boolean started;
    private boolean finished;
    private boolean pausedOthers;
    private boolean isIqama;
    private int prayerIndex = PrayerCalc.DHUHR;
    private ToneGenerator tone;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (started) return START_NOT_STICKY;
        started = true;
        playing = true;

        int index = intent != null ? intent.getIntExtra("index", PrayerCalc.DHUHR) : PrayerCalc.DHUHR;
        prayerIndex = index;
        boolean friday = intent != null && intent.getBooleanExtra("friday", false);
        boolean test = intent != null && intent.getBooleanExtra("test", false);
        isIqama = intent != null && intent.getBooleanExtra("iqama", false);
        boolean first = intent != null && intent.getBooleanExtra("first", false);

        Cities.City city = Cities.selected(this);
        String title = getString(isIqama ? R.string.iqama_title : R.string.popup_title,
            PrayerCalc.name(this, index, friday));
        if (first) title = getString(R.string.first_adhan_title);
        if (test) title = title + " " + getString(R.string.popup_test);
        String text = "اقلب الهاتف على وجهه لإيقاف الصوت";
        if (city != null && !first) text = city.name("ar".equals(Locale.getDefault().getLanguage())) + " · " + text;

        goForeground(title, text);

        PowerManager pm = getSystemService(PowerManager.class);
        if (pm != null) {
            wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "magiic:adhan");
            wake.acquire(MAX_ADHAN_MS + 30000L);
        }

        audio = getSystemService(AudioManager.class);
        takeAudio();
        playSound();
        flip = new FlipDetector(this, new Runnable() {
            @Override
            public void run() {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        finishAdhan();
                    }
                });
            }
        });
        flip.start();
        return START_NOT_STICKY;
    }

    private void goForeground(String title, String text) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(
            CHANNEL, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW));
        Notification n = new Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_crescent)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .build();
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTE_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NOTE_ID, n);
        }
    }

    /** The alarm stream, so the adhan is heard at the alarm volume even when the media volume is low. */
    private static AudioAttributes attrs() {
        return new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build();
    }

    private void takeAudio() {
        if (audio == null) return;
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

    private void playSound() {
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
            beepsForIqama();
            return;
        }
        Uri alert = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (alert == null || !playMedia(alert, 0, MAX_FALLBACK_MS)) beep();
    }

    private int rawId(String name) {
        return getResources().getIdentifier(name, "raw", getPackageName());
    }

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

    private void beep() {
        try {
            tone = new ToneGenerator(AudioManager.STREAM_ALARM, 100);
            tone.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 3000);
        } catch (RuntimeException ignored) {
            // No sound at all.
        }
        handler.postDelayed(finisher, 3500L);
    }

    private void beepsForIqama() {
        try {
            tone = new ToneGenerator(AudioManager.STREAM_ALARM, 100);
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

    private void finishAdhan() {
        if (finished) return;
        finished = true;
        playing = false;
        handler.removeCallbacksAndMessages(null);
        if (flip != null) flip.stop();
        releasePlayer();
        releaseTone();
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
        if (!finished) {
            finished = true;
            playing = false;
            handler.removeCallbacksAndMessages(null);
            if (flip != null) flip.stop();
            releasePlayer();
            releaseTone();
            if (audio != null && focus != null) audio.abandonAudioFocusRequest(focus);
            if (wake != null && wake.isHeld()) wake.release();
        }
        super.onDestroy();
    }
}
