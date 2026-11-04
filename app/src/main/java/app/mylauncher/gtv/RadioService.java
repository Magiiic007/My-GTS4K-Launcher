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
        new Station("إذاعة نداء الإسلام", "Nida Al-Islam Radio", "نداء الإسلام", "Nida Al-Islam",
            "https://live.kwikmotion.com/sbrksanedaradiolive/ksanedaradio/playlist.m3u8"),
        reciter("مشاري العفاسي", "Mishary Alafasy", "mishary_alafasi"),
        reciter("عبد الباسط عبد الصمد", "Abdul Basit Abdul Samad", "abdulbasit_abdulsamad"),
        reciter("محمد صديق المنشاوي", "Mohamed Siddiq Al-Minshawi", "mohammed_siddiq_alminshawi"),
        reciter("محمود خليل الحصري", "Mahmoud Khalil Al-Husary", "mahmoud_khalil_alhussary"),
        reciter("ماهر المعيقلي", "Maher Al-Muaiqly", "maher"),
        reciter("سعد الغامدي", "Saad Al-Ghamdi", "saad_alghamdi"),
        reciter("عبد الرحمن السديس", "Abdul Rahman Al-Sudais", "abdulrahman_alsudaes"),
        reciter("ياسر الدوسري", "Yasser Al-Dosari", "yasser_aldosari"),
        reciter("ناصر القطامي", "Nasser Al-Qatami", "nasser_alqatami"),
        reciter("محمد رشاد الشريف", "Mohammad Rashad Al-Shareef", "mohammad_rashad_alshareef"),
    };

    /** A reciter's own channel, around the clock (mp3quran.net, through qurango.net). */
    private static Station reciter(String ar, String en, String id) {
        return new Station(ar, en, ar, en, "https://backup.qurango.net/radio/" + id);
    }

    static final int OFF = 0, CONNECTING = 1, PLAYING = 2, RETRYING = 3, PAUSED = 4;

    interface Listener {
        void onRadio(int state);
    }

    private static final String ACTION_PLAY = "app.mylauncher.gtv.RADIO_PLAY";
    private static final String ACTION_STOP = "app.mylauncher.gtv.RADIO_STOP";
    private static final String CHANNEL = "radio2";
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

    /** The station played last (the first one if the radio was never used). */
    static int lastStation(Context c) {
        int i = c.getSharedPreferences("magiic", MODE_PRIVATE).getInt("radio_station", 0);
        return Math.max(0, Math.min(STATIONS.length - 1, i));
    }

    /** True when the radio was on and nobody stopped it (so a force stop or an update cut it off). */
    static boolean shouldResume(Context c) {
        return c.getSharedPreferences("magiic", MODE_PRIVATE).getBoolean("radio_resume", false);
    }

    /** The last few radio events (kept in the settings, shown in "About"), to see why it did or did not start. */
    static void log(Context c, String what) {
        android.content.SharedPreferences sp = c.getSharedPreferences("magiic", MODE_PRIVATE);
        String[] old = sp.getString("radio_log", "").split("\n");
        StringBuilder b = new StringBuilder();
        int from = Math.max(0, old.length - 13);
        for (int i = from; i < old.length; i++) if (!old[i].isEmpty()) b.append(old[i]).append('\n');
        b.append(new java.text.SimpleDateFormat("dd/MM HH:mm:ss", Locale.US).format(new java.util.Date())).append(' ').append(what);
        sp.edit().putString("radio_log", b.toString()).apply();
    }

    /** True from the moment another app took the sound until the radio is started again (this process only). */
    private static boolean yielded;
    private static boolean coldChecked;

    /**
     * The home screen calls this: bring the radio back if it was cut off rather than stopped. After a fresh start of
     * the app (an update, a force stop, a crash) it always comes back if it was on; later it stays quiet only when
     * another app took the sound meanwhile.
     */
    static void resumeIfCutOff(Context c) {
        boolean cold = !coldChecked;
        coldChecked = true;
        if (cold || state == OFF && shouldResume(c)) log(c, "home: cold=" + cold + " state=" + state + " resume=" + shouldResume(c) + " yielded=" + yielded);
        if (state == OFF && shouldResume(c) && (cold || !yielded)) {
            try {
                play(c, lastStation(c));
            } catch (RuntimeException e) {
                // Not allowed to start right now; the radio button still works.
                c.getSharedPreferences("magiic", MODE_PRIVATE).edit().putString("dbg_radio", String.valueOf(e)).apply();
            }
        }
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
    private android.media.session.MediaSession session;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || ACTION_STOP.equals(intent.getAction())) {
            log(this, "stop button" + (intent == null ? " (no intent)" : ""));
            shutdown();
            return START_NOT_STICKY;
        }
        yielded = false;
        log(this, "start station " + intent.getIntExtra("station", 0));
        stationIndex = Math.max(0, Math.min(STATIONS.length - 1, intent.getIntExtra("station", 0)));
        getSharedPreferences("magiic", MODE_PRIVATE).edit().putInt("radio_station", stationIndex)
            .putBoolean("radio_resume", true).apply();
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
        NotificationChannel ch = new NotificationChannel(
            CHANNEL, getString(R.string.radio_btn), NotificationManager.IMPORTANCE_DEFAULT);
        ch.setSound(null, null);
        ch.enableVibration(false);
        ch.setShowBadge(false);
        ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(ch);
        nm.deleteNotificationChannel("radio");
        if (session == null) {
            session = new android.media.session.MediaSession(this, "radio");
            session.setCallback(new android.media.session.MediaSession.Callback() {
                @Override
                public void onPause() {
                    // The system or a remote key paused it: step aside, but stay "on" for a fresh start.
                    yielded = true;
                    log(RadioService.this, "media pause");
                    shutdown(false);
                }

                @Override
                public void onStop() {
                    log(RadioService.this, "media stop");
                    shutdown();
                }
            });
        }
        session.setPlaybackState(new android.media.session.PlaybackState.Builder()
            .setActions(android.media.session.PlaybackState.ACTION_STOP | android.media.session.PlaybackState.ACTION_PAUSE)
            .setState(android.media.session.PlaybackState.STATE_PLAYING, 0, 1f).build());
        session.setActive(true);
        boolean arabic = "ar".equals(Locale.getDefault().getLanguage());
        android.app.PendingIntent stop = android.app.PendingIntent.getService(this, 41,
            new Intent(this, RadioService.class).setAction(ACTION_STOP),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE);
        Notification.Action stopAction = new Notification.Action.Builder(
            android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_crescent),
            getString(R.string.radio_stop), stop).build();
        Notification n = new Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_crescent)
            .setContentTitle(STATIONS[stationIndex].name(arabic))
            .setContentText(getString(R.string.radio_btn))
            .setOngoing(true)
            .addAction(stopAction)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setStyle(new Notification.MediaStyle().setMediaSession(session.getSessionToken()).setShowActionsInCompactView(0))
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
                // Another app is playing sound now (a video, music): the radio steps aside. It is still "on" for a
                // fresh start of the app (see resumeIfCutOff), only not for the rest of this run.
                yielded = true;
                log(this, "focus lost");
                shutdown(false);
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
        shutdown(true);
    }

    private void shutdown(boolean onPurpose) {
        // Stopped on purpose: do not bring it back on its own.
        if (onPurpose) getSharedPreferences("magiic", MODE_PRIVATE).edit().putBoolean("radio_resume", false).apply();
        wanted = false;
        handler.removeCallbacksAndMessages(null);
        release();
        if (audio != null && focus != null) audio.abandonAudioFocusRequest(focus);
        focus = null;
        wifi(false);
        set(OFF);
        stopForeground(true);
        if (session != null) {
            session.setActive(false);
            session.release();
            session = null;
        }
        stopSelf();
    }

    @Override
    public void onDestroy() {
        // Killed by the system: leave nothing playing and tell the screen.
        if (state != OFF) {
            log(this, "service destroyed while playing");
            wanted = false;
            handler.removeCallbacksAndMessages(null);
            release();
            if (audio != null && focus != null) audio.abandonAudioFocusRequest(focus);
            wifi(false);
            set(OFF);
        }
        if (session != null) {
            session.release();
            session = null;
        }
        super.onDestroy();
    }
}
