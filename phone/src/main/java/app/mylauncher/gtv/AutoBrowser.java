package app.mylauncher.gtv;

import android.media.MediaDescription;
import android.media.browse.MediaBrowser;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.service.media.MediaBrowserService;

import java.util.ArrayList;
import java.util.List;

/**
 * Lets Android Auto (and any car screen) list the radio stations and the reciters and play them. It only passes
 * the choice on to {@link RadioService}, which does the playing, and mirrors that service's state back to the car.
 */
public class AutoBrowser extends MediaBrowserService implements RadioService.Listener {
    private static final String ROOT = "root";
    private MediaSession session;

    @Override
    public void onCreate() {
        super.onCreate();
        session = new MediaSession(this, "auto");
        session.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        session.setCallback(new MediaSession.Callback() {
            @Override
            public void onPlay() {
                RadioService.play(AutoBrowser.this, RadioService.lastStation(AutoBrowser.this));
            }

            @Override
            public void onPlayFromMediaId(String id, Bundle extras) {
                try {
                    RadioService.play(AutoBrowser.this, Integer.parseInt(id));
                } catch (NumberFormatException ignored) {
                    // not one of ours
                }
            }

            @Override
            public void onPause() {
                RadioService.stop(AutoBrowser.this);
            }

            @Override
            public void onStop() {
                RadioService.stop(AutoBrowser.this);
            }

            @Override
            public void onSkipToNext() {
                step(1);
            }

            @Override
            public void onSkipToPrevious() {
                step(-1);
            }
        });
        session.setActive(true);
        setSessionToken(session.getSessionToken());
        RadioService.listen(this);
        onRadio(RadioService.state());
    }

    private void step(int d) {
        RadioService.play(this, RadioStats.step(this, RadioService.lastStation(this), d));
    }

    @Override
    public void onRadio(int s) {
        if (session == null) return;
        boolean on = s != RadioService.OFF;
        int idx = RadioService.lastStation(this);
        session.setMetadata(new android.media.MediaMetadata.Builder()
            .putString(android.media.MediaMetadata.METADATA_KEY_TITLE, RadioService.STATIONS[idx].name(true))
            .putString(android.media.MediaMetadata.METADATA_KEY_ARTIST, "الصلاة خير")
            .putBitmap(android.media.MediaMetadata.METADATA_KEY_ART, RadioArt.photo(this, idx, 512))
            .putBitmap(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART, RadioArt.photo(this, idx, 512)).build());
        int st = s == RadioService.PLAYING ? PlaybackState.STATE_PLAYING
            : on ? PlaybackState.STATE_BUFFERING : PlaybackState.STATE_STOPPED;
        session.setPlaybackState(new PlaybackState.Builder()
            .setActions(PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_STOP
                | PlaybackState.ACTION_PLAY_FROM_MEDIA_ID | PlaybackState.ACTION_SKIP_TO_NEXT
                | PlaybackState.ACTION_SKIP_TO_PREVIOUS)
            .setState(st, 0, 1f).build());
    }

    @Override
    public void onDestroy() {
        RadioService.unlisten(this);
        if (session != null) {
            session.release();
            session = null;
        }
        super.onDestroy();
    }

    @Override
    public BrowserRoot onGetRoot(String pkg, int uid, Bundle hints) {
        return new BrowserRoot(ROOT, null);
    }

    @Override
    public void onLoadChildren(String parent, Result<List<MediaBrowser.MediaItem>> result) {
        List<MediaBrowser.MediaItem> out = new ArrayList<>();
        if (ROOT.equals(parent)) {
            for (int i : RadioStats.order(this)) {
                MediaDescription d = new MediaDescription.Builder()
                    .setMediaId(String.valueOf(i))
                    .setTitle(RadioService.STATIONS[i].name(true))
                    .setSubtitle(i < 3 ? "إذاعة" : "قارئ")
                    .setIconBitmap(RadioArt.disc(this, i, 192)).build();
                out.add(new MediaBrowser.MediaItem(d, MediaBrowser.MediaItem.FLAG_PLAYABLE));
            }
        }
        result.sendResult(out);
    }
}
