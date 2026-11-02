package app.mylauncher.gtv;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Holy Quran radio, streamed live. It runs as a foreground service so it keeps playing while another screen or app
 * is open. The design follows the Afnon project's radio, which was tested on a real TV:
 * <ul>
 * <li>It holds the audio focus. Another app starting sound (a video) stops the radio for good.</li>
 * <li>The call to prayer takes the focus for a while. A live stream can't be paused, so the radio is dropped and
 *     reconnects by itself when the adhan ends.</li>
 * <li>A stream that drops is reconnected, waiting a little longer each time (3 s, 6 s ... up to 30 s).</li>
 * </ul>
 * The screen talks to it through the static methods below and hears about changes through a {@link Listener}.
 */
public class RadioService extends Service {

    /** A radio station and its live stream. */
    static final class Station {
        final String ar, en, shortAr, shortEn, url;

        Station(String ar, String en, String shortAr, String shortEn, String url) {
            this.ar = ar;
            this.en = en;
            this.shortAr = shortAr;
            this.shortEn = shortEn;
            this.url = url;
        }

        String name(boolean arabic) {
            return arabic ? ar : en;
        }

        String shortName(boolean arabic) {
            return arabic ? shortAr : shortEn;
        }
    }

    /**
     * Each station's own public stream, as used by the Afnon project. Cairo's address redirects to plain http (allowed
     * for radiojar.com in network_security.xml); the Saudi one is an HLS playlist. The stations are not affiliated
     * with this app.
     */
    static final Station[] STATIONS = {
        new Station("إذاعة القرآن الكريم من القاهرة", "Holy Quran Radio, Cairo", "القاهرة", "Cairo",
            "https://stream.radiojar.com/8s5u5tpdtwzuv"),
        new Station("إذاعة القرآن الكريم السعودية", "Holy Quran Radio, Saudi Arabia", "السعودية", "Saudi Arabia",
            "https://live.kwikmotion.com/sbrksaquranradiolive/ksaquranradio/playlist.m3u8"),
    };

    static final int OFF = 0, CONNECTING = 1, PLAYING = 2, RETRYING = 3, PAUSED = 4;

    interface Listener {
        void onRadio(int state);
    }

    private static final String ACTION_PLAY = "app.mylauncher.gtv.RADIO_PLAY";
    private static final String ACTION_STOP = "app.mylauncher.gtv.RADIO_STOP";
    private static final String CHANNEL = "radio";
    private static final int NOTE_ID = 8;

    // What the screen reads. Main thread only.
    private static final List<Listener> listeners = new ArrayList<>();
    private static int state = OFF;
    private static int stationIndex = 0;

    static int state() {
        return state;
    }

    /** Playing, connecting, reconnecting, or paused for the adhan: on as far as the listener is concerned. */
    static boolean on() {
        return state != OFF;
    }

    static int station() {
        return stationIndex;
    }

    static void listen(Listener l) {
        if (!listeners.contains(l)) listeners.add(l);
    }

    static void unlisten(Listener l) {
        listeners.remove(l);
    }

    private static void set(int s) {
        state = s;
        for (Listener l : new ArrayList<>(listeners)) l.onRadio(s);
    }

    /** Starts a station (or switches to it). Call from the screen the user is looking at. */
    static void play(Context c, int index) {
        c.startForegroundService(new Intent(c, RadioService.class).setAction(ACTION_PLAY).putExtra("station", index));
    }

    static void stop(Context c) {
        if (state == OFF) return;
        c.startService(new Intent(c, RadioService.class).setAction(ACTION_STOP));
    }

    // ---------------------------------------------------------------- the service

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable reconnect = new Runnable() {
        @Override
        public void run() {
            connect();
        }
    };

    private MediaPlayer player;
    private AudioManager audio;
    private AudioFocusRequest focus;
    private WifiManager.WifiLock wifiLock;
    /** The listener asked for it; stays true through reconnects and the adhan's pause. */
    private boolean wanted;
    private int failures;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || ACTION_STOP.equals(intent.getAction())) {
            shutdown();
            return START_NOT_STICKY;
        }
        stationIndex = Math.max(0, Math.min(STATIONS.length - 1, intent.getIntExtra("station", 0)));
        goForeground();
        wanted = true;
        failures = 0;
        audio = getSystemService(AudioManager.class);
        takeFocus();
        wifi(true);
        connect();
        return START_NOT_STICKY;
    }

    private void goForeground() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(
            CHANNEL, getString(R.string.radio_btn), NotificationManager.IMPORTANCE_LOW));
        boolean arabic = "ar".equals(Locale.getDefault().getLanguage());
        Notification n = new Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(STATIONS[stationIndex].name(arabic))
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
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build();
    }

    private void takeFocus() {
        if (audio == null) return;
        if (focus != null) audio.abandonAudioFocusRequest(focus);
        focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attrs())
            .setOnAudioFocusChangeListener(new AudioManager.OnAudioFocusChangeListener() {
                @Override
                public void onAudioFocusChange(int change) {
                    onFocus(change);
                }
            }, handler)
            .build();
        audio.requestAudioFocus(focus);
    }

    private void onFocus(int change) {
        switch (change) {
            case AudioManager.AUDIOFOCUS_LOSS:
                // Another app is playing sound now (a video, music): the radio steps aside for good.
                shutdown();
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                // The call to prayer. A live stream can't pause, so drop it and reconnect afterwards.
                if (wanted) {
                    release();
                    set(PAUSED);
                }
                break;
            case AudioManager.AUDIOFOCUS_GAIN:
                if (state == PAUSED) {
                    failures = 0;
                    connect();
                }
                break;
            default:
                break;
        }
    }

    private void connect() {
        handler.removeCallbacks(reconnect);
        if (!wanted) return;
        release();
        set(failures == 0 ? CONNECTING : RETRYING);
        final MediaPlayer mp = new MediaPlayer();
        player = mp;
        mp.setAudioAttributes(attrs());
        mp.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK);
        mp.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer p) {
                if (p != player) return;
                failures = 0;
                p.start();
                set(PLAYING);
            }
        });
        mp.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer p, int what, int extra) {
                if (p == player) retry();
                return true;
            }
        });
        // A live stream never completes; if it does, the connection dropped.
        mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer p) {
                if (p == player) retry();
            }
        });
        try {
            mp.setDataSource(STATIONS[stationIndex].url);
            mp.prepareAsync();
        } catch (Exception e) {
            retry();
        }
    }

    /** Waits a little longer each time (3 s, 6 s ... up to 30 s) and tries again, for as long as it is wanted. */
    private void retry() {
        release();
        if (!wanted) return;
        failures++;
        set(RETRYING);
        handler.postDelayed(reconnect, Math.min(30000L, 3000L * failures));
    }

    private void release() {
        handler.removeCallbacks(reconnect);
        if (player != null) {
            final MediaPlayer p = player;
            player = null;
            // reset() waits for a stream that is still connecting, up to several seconds, so it runs off the main
            // thread. The callbacks of the old player are ignored: they check p == player.
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        p.reset();
                        p.release();
                    } catch (RuntimeException ignored) {
                        // Already gone.
                    }
                }
            }, "radio-release").start();
        }
    }

    /** Keeps Wi-Fi awake while the radio plays. */
    private void wifi(boolean on) {
        try {
            if (on) {
                if (wifiLock == null) {
                    WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                    if (wm != null) wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "magiic:radio");
                }
                if (wifiLock != null && !wifiLock.isHeld()) wifiLock.acquire();
            } else if (wifiLock != null && wifiLock.isHeld()) {
                wifiLock.release();
            }
        } catch (RuntimeException ignored) {
            // No Wi-Fi lock (Ethernet, or no permission); the stream still plays.
        }
    }

    /** Stop for good: the listener asked, or another app took the audio. */
    private void shutdown() {
        wanted = false;
        handler.removeCallbacksAndMessages(null);
        release();
        if (audio != null && focus != null) audio.abandonAudioFocusRequest(focus);
        focus = null;
        wifi(false);
        set(OFF);
        stopForeground(true);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        // Killed by the system: leave nothing playing and tell the screen.
        if (state != OFF) {
            wanted = false;
            handler.removeCallbacksAndMessages(null);
            release();
            if (audio != null && focus != null) audio.abandonAudioFocusRequest(focus);
            wifi(false);
            set(OFF);
        }
        super.onDestroy();
    }
}
