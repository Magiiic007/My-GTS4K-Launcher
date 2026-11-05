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
import android.media.audiofx.LoudnessEnhancer;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The phone's own copy of the radio (the TV keeps the plain MediaPlayer one). It plays with ExoPlayer, which starts
 * live streams (mp3 and HLS) faster, follows the redirect from https to http of the Cairo station, keeps a bigger
 * buffer for a mobile network, and gives up on a stalled stream quickly so it reconnects instead of hanging.
 *
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
    private static final String ACTION_NEXT = "app.mylauncher.gtv.RADIO_NEXT";
    private static final String ACTION_PREV = "app.mylauncher.gtv.RADIO_PREV";
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
        // The home-screen widget shows the play/stop button and the station.
        if (cur != null) PrayerNote.refresh(cur);
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
        // On the phone the radio only starts when the user starts it: never by itself after an update, a restart
        // or a force stop.
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

    private ExoPlayer player;
    private LoudnessEnhancer booster;
    private static RadioService cur;

    /** Extra loudness (dB) for the radio stations, whose streams are much quieter than the reciters' ones. */
    static int boostDb(Context c) {
        return c.getSharedPreferences("magiic", MODE_PRIVATE).getInt("radio_boost", 9);
    }

    /** Applies the chosen boost to what is playing now. */
    static void refreshBoost() {
        if (cur != null) cur.applyBoost();
    }

    private void applyBoost() {
        if (booster != null) {
            try {
                booster.release();
            } catch (RuntimeException ignored) {
                // Already gone.
            }
            booster = null;
        }
        if (player == null || STATIONS[stationIndex].url.contains("qurango")) return;
        int db = boostDb(this);
        if (db <= 0) return;
        try {
            booster = new LoudnessEnhancer(player.getAudioSessionId());
            booster.setTargetGain(db * 100);
            booster.setEnabled(true);
        } catch (RuntimeException e) {
            log(this, "boost not available: " + e);
        }
    }
    private AudioManager audio;
    private AudioFocusRequest focus;
    /** The listener asked for it; stays true through reconnects and the adhan's pause. */
    private boolean wanted;
    private int failures;
    private android.media.session.MediaSession session;

    @Override
    public void onCreate() {
        super.onCreate();
        cur = this;
    }

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
        int want = intent.getIntExtra("station", 0);
        int base = state != OFF ? stationIndex : lastStation(this);
        if (ACTION_NEXT.equals(intent.getAction())) want = (base + 1) % STATIONS.length;
        if (ACTION_PREV.equals(intent.getAction())) want = (base + STATIONS.length - 1) % STATIONS.length;
        log(this, "start station " + want);
        stationIndex = Math.max(0, Math.min(STATIONS.length - 1, want));
        getSharedPreferences("magiic", MODE_PRIVATE).edit().putInt("radio_station", stationIndex)
            .putBoolean("radio_resume", true).apply();
        goForeground();
        wanted = true;
        failures = 0;
        audio = getSystemService(AudioManager.class);
        takeFocus();
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
                public void onSkipToNext() {
                    startService(new Intent(RadioService.this, RadioService.class).setAction(ACTION_NEXT));
                }

                @Override
                public void onSkipToPrevious() {
                    startService(new Intent(RadioService.this, RadioService.class).setAction(ACTION_PREV));
                }

                @Override
                public void onStop() {
                    log(RadioService.this, "media stop");
                    shutdown();
                }
            });
        }
        session.setPlaybackState(new android.media.session.PlaybackState.Builder()
            .setActions(android.media.session.PlaybackState.ACTION_STOP | android.media.session.PlaybackState.ACTION_PAUSE
                | android.media.session.PlaybackState.ACTION_SKIP_TO_NEXT | android.media.session.PlaybackState.ACTION_SKIP_TO_PREVIOUS)
            .setState(android.media.session.PlaybackState.STATE_PLAYING, 0, 1f).build());
        session.setActive(true);
        boolean arabic = true; // the phone is Arabic only
        android.app.PendingIntent stop = android.app.PendingIntent.getService(this, 41,
            new Intent(this, RadioService.class).setAction(ACTION_STOP),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE);
        Notification.Action stopAction = new Notification.Action.Builder(
            android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_launcher_phone),
            getString(R.string.radio_stop), stop).build();
        Notification.Action prevAction = new Notification.Action.Builder(
            android.graphics.drawable.Icon.createWithResource(this, android.R.drawable.ic_media_previous),
            "السابق", android.app.PendingIntent.getService(this, 42,
                new Intent(this, RadioService.class).setAction(ACTION_PREV),
                android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE)).build();
        Notification.Action nextAction = new Notification.Action.Builder(
            android.graphics.drawable.Icon.createWithResource(this, android.R.drawable.ic_media_next),
            "التالي", android.app.PendingIntent.getService(this, 43,
                new Intent(this, RadioService.class).setAction(ACTION_NEXT),
                android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE)).build();
        Notification n = new Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_phone)
            .setContentTitle(STATIONS[stationIndex].name(arabic))
            .setContentText(getString(R.string.radio_btn))
            .setOngoing(true)
            .addAction(prevAction)
            .addAction(stopAction)
            .addAction(nextAction)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setStyle(new Notification.MediaStyle().setMediaSession(session.getSessionToken()).setShowActionsInCompactView(0, 1, 2))
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

    /** A stream that has not started (or has stalled) for this long is dropped and tried again. */
    private static final long STALL_MS = 15000L;
    private final Runnable stall = new Runnable() {
        @Override
        public void run() {
            log(RadioService.this, "stalled, reconnecting");
            retry();
        }
    };

    private void connect() {
        handler.removeCallbacks(reconnect);
        if (!wanted) return;
        release();
        set(failures == 0 ? CONNECTING : RETRYING);
        DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(8000)
            .setReadTimeoutMs(10000)
            .setUserAgent("Mozilla/5.0 (Linux; Android) AlSalatKhair");
        final ExoPlayer ex = new ExoPlayer.Builder(this)
            .setMediaSourceFactory(new DefaultMediaSourceFactory(http))
            .setLoadControl(new DefaultLoadControl.Builder()
                .setBufferDurationsMs(15000, 40000, 1500, 3000).build())
            .build();
        player = ex;
        ex.setAudioAttributes(new androidx.media3.common.AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(), false);
        ex.setWakeMode(C.WAKE_MODE_NETWORK);
        ex.addListener(new Player.Listener() {
            @Override
            public void onAudioSessionIdChanged(int id) {
                if (ex == player) applyBoost();
            }

            @Override
            public void onPlaybackStateChanged(int st) {
                if (ex != player) return;
                if (st == Player.STATE_READY) {
                    handler.removeCallbacks(stall);
                    failures = 0;
                    set(PLAYING);
                } else if (st == Player.STATE_BUFFERING) {
                    handler.removeCallbacks(stall);
                    handler.postDelayed(stall, STALL_MS);
                } else if (st == Player.STATE_ENDED) {
                    // A live stream never ends; if it does, the connection dropped.
                    retry();
                }
            }

            @Override
            public void onPlayerError(PlaybackException e) {
                if (ex != player) return;
                log(RadioService.this, "error " + e.getErrorCodeName());
                retry();
            }
        });
        try {
            ex.setMediaItem(MediaItem.fromUri(STATIONS[stationIndex].url));
            ex.setPlayWhenReady(true);
            ex.prepare();
            applyBoost();
            handler.postDelayed(stall, STALL_MS);
        } catch (RuntimeException e) {
            retry();
        }
    }

    /** Waits a little longer each time (3 s, 6 s ... up to 30 s) and tries again, for as long as it is wanted. */
    private void retry() {
        release();
        if (!wanted) return;
        failures++;
        set(RETRYING);
        handler.postDelayed(reconnect, Math.min(12000L, 2000L * failures));
    }

    private void release() {
        handler.removeCallbacks(reconnect);
        handler.removeCallbacks(stall);
        if (booster != null) {
            try {
                booster.release();
            } catch (RuntimeException ignored) {
                // Already gone.
            }
            booster = null;
        }
        if (player != null) {
            ExoPlayer p = player;
            player = null;
            try {
                p.release();
            } catch (RuntimeException ignored) {
                // Already gone.
            }
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
                set(OFF);
        }
        if (session != null) {
            session.release();
            session = null;
        }
        if (cur == this) cur = null;
        super.onDestroy();
    }
}
