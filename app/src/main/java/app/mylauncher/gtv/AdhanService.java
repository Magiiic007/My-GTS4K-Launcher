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
    private WindowManager windows;
    private View overlay;
    private boolean started;
    private boolean finished;
    private boolean pausedOthers;

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
        boolean friday = intent != null && intent.getBooleanExtra("friday", false);
        boolean test = intent != null && intent.getBooleanExtra("test", false);

        Cities.City city = Cities.selected(this);
        String title = getString(R.string.popup_title, PrayerCalc.name(this, index, friday));
        if (test) title = title + " " + getString(R.string.popup_test);
        String where = city != null ? city.name("ar".equals(Locale.getDefault().getLanguage())) : "";

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
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(title)
            .setContentText(getString(R.string.popup_hint))
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
        boolean wasPlaying = audio.isMusicActive();
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

    /** The full-screen popup. Back or OK closes it and stops the adhan. */
    private void showOverlay(String title, String where) {
        if (!Settings.canDrawOverlays(this)) return;
        try {
            windows = getSystemService(WindowManager.class);
            FrameLayout root = new FrameLayout(this) {
                @Override
                public boolean dispatchKeyEvent(KeyEvent e) {
                    int k = e.getKeyCode();
                    if (k == KeyEvent.KEYCODE_VOLUME_UP || k == KeyEvent.KEYCODE_VOLUME_DOWN
                            || k == KeyEvent.KEYCODE_VOLUME_MUTE) {
                        return super.dispatchKeyEvent(e);
                    }
                    if (e.getAction() == KeyEvent.ACTION_UP && (k == KeyEvent.KEYCODE_BACK
                            || k == KeyEvent.KEYCODE_ENTER || k == KeyEvent.KEYCODE_DPAD_CENTER)) {
                        finishAdhan();
                    }
                    return true;
                }
            };
            root.setBackgroundColor(Color.parseColor("#F20F1720"));

            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER_HORIZONTAL);

            TextView name = new TextView(this);
            name.setText(title);
            name.setTextColor(Color.WHITE);
            name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 44);
            name.setGravity(Gravity.CENTER);
            col.addView(name, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            View bar = new View(this);
            bar.setBackgroundColor(Color.parseColor("#FFE0B040"));
            LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(dp(120), dp(3));
            barLp.topMargin = dp(20);
            barLp.bottomMargin = dp(20);
            col.addView(bar, barLp);

            TextView sub = new TextView(this);
            sub.setText(where);
            sub.setTextColor(Color.parseColor("#FFB8C4D0"));
            sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
            sub.setGravity(Gravity.CENTER);
            col.addView(sub, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            TextView hint = new TextView(this);
            hint.setText(R.string.popup_hint);
            hint.setTextColor(Color.parseColor("#FF7F8C99"));
            hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
            hint.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            hintLp.topMargin = dp(48);
            col.addView(hint, hintLp);

            root.addView(col, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.TRANSLUCENT);
            windows.addView(root, lp);
            overlay = root;
            root.setFocusable(true);
            root.setFocusableInTouchMode(true);
            root.requestFocus();
        } catch (RuntimeException e) {
            overlay = null;
        }
    }

    private void hideOverlay() {
        if (overlay != null && windows != null) {
            try {
                windows.removeView(overlay);
            } catch (RuntimeException ignored) {
                // Already gone.
            }
        }
        overlay = null;
    }

    private void playSound() {
        int res = getResources().getIdentifier("adhan", "raw", getPackageName());
        try {
            player = new MediaPlayer();
            player.setAudioAttributes(attrs());
            if (res != 0) {
                AssetFileDescriptor afd = getResources().openRawResourceFd(res);
                try {
                    player.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
                } finally {
                    afd.close();
                }
                handler.postDelayed(finisher, MAX_ADHAN_MS);
            } else {
                // No adhan.mp3 added yet: a short system alert, so the popup and the pause/resume can be tested.
                player.setDataSource(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM));
                handler.postDelayed(finisher, MAX_FALLBACK_MS);
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
        } catch (Exception e) {
            beep();
        }
    }

    /** The last resort when nothing can be played: three seconds of beeps. */
    private void beep() {
        releasePlayer();
        try {
            new ToneGenerator(AudioManager.STREAM_MUSIC, 100)
                .startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 3000);
        } catch (RuntimeException ignored) {
            // No sound at all; the popup still closes on time.
        }
        handler.postDelayed(finisher, 3500L);
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
        handler.removeCallbacks(finisher);
        releasePlayer();
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
            releasePlayer();
            hideOverlay();
            if (audio != null && focus != null) audio.abandonAudioFocusRequest(focus);
            if (wake != null && wake.isHeld()) wake.release();
        }
        super.onDestroy();
    }
}
