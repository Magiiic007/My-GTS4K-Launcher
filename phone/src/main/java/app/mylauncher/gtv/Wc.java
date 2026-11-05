package app.mylauncher.gtv;

import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.widget.Toast;

/**
 * Bathroom mode: one press quiets everything (the adhan and iqama, the adhkar over apps, the dua and the reminders,
 * the notifications, the radio) and one more press brings it all back. It also ends by itself after a while, in case
 * it is forgotten.
 */
final class Wc {
    static final long AUTO_OFF_MS = 30 * 60_000L;

    private Wc() {
    }

    private static SharedPreferences p(Context c) {
        return c.getSharedPreferences("magiic", Context.MODE_PRIVATE);
    }

    /** When it will end by itself (0 = it is off). */
    static long endsAt(Context c) {
        return p(c).getBoolean("wc_on", false) ? p(c).getLong("wc_at", 0L) + AUTO_OFF_MS : 0L;
    }

    static boolean on(Context c) {
        long end = endsAt(c);
        return end != 0L && System.currentTimeMillis() < end;
    }

    /** Switches it off if it was forgotten past the limit. */
    static void expire(Context c) {
        if (p(c).getBoolean("wc_on", false) && !on(c)) set(c, false, false);
    }

    static boolean toggle(Context c) {
        boolean now = !on(c);
        set(c, now, true);
        return now;
    }

    static void set(Context c, boolean on, boolean feedback) {
        c = c.getApplicationContext();
        p(c).edit().putBoolean("wc_on", on).putLong("wc_at", on ? System.currentTimeMillis() : 0L).apply();
        if (!on) p(c).edit().remove("qm_next").apply(); // the "establish the prayer" timer starts over, so it does not pop up the moment you come out
        try {
            if (on) {
                boolean radio = RadioService.on();
                p(c).edit().putBoolean("wc_radio", radio).apply();
                if (radio) RadioService.stop(c);
                c.stopService(new android.content.Intent(c, AdhanService.class));
                DuaPopup.hide();
                MissedPopup.hide();
                NotificationManager nm = c.getSystemService(NotificationManager.class);
                if (nm != null) {
                    nm.cancel(7312); // the "establish the prayer" note
                    nm.cancelAll();  // whatever of ours is still showing; the system keeps other apps' notes
                }
            }
            BubbleService.sync(c);
            OverlayService.sync(c);
            PrayerNote.refresh(c);
            Widgets.refresh(c);
        } catch (RuntimeException ignored) {
            // a part that fails to stop does not stop the rest
        }
        if (!on && p(c).getBoolean("wc_radio", false)) {
            // The radio was playing when the mode began: it plays again, from the same station.
            p(c).edit().putBoolean("wc_radio", false).apply();
            try {
                c.startForegroundService(new android.content.Intent(c, RadioService.class)
                    .setAction("app.mylauncher.gtv.RADIO_PLAY").putExtra("station", RadioService.lastStation(c)));
            } catch (RuntimeException ignored) {
                // Android refused to start it from the background (the mode ended by itself): press play
            }
        }
        if (feedback) {
            try {
                Vibrator v = c.getSystemService(Vibrator.class);
                if (v != null && v.hasVibrator()) {
                    v.vibrate(VibrationEffect.createWaveform(on ? new long[] {0, 90, 60, 90} : new long[] {0, 220}, -1));
                }
                Toast.makeText(c, on ? "وضع الخلاء شغّال: كل شيء متوقف" : "رجع كل شيء للعمل", Toast.LENGTH_SHORT).show();
            } catch (RuntimeException ignored) {
                // no vibrator or no toast: the change itself happened
            }
        }
    }
}
