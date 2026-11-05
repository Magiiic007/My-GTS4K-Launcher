package app.mylauncher.gtv;

import android.app.AlarmManager;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Build;
import android.os.SystemClock;
import android.widget.RemoteViews;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * The permanent prayer notification and the silent window.
 *
 * The notification counts down (a system chronometer, so nothing of ours runs) to the next adhan; from the adhan
 * until the iqama it counts down to the iqama. One system alarm is kept for the next change of state (adhan, iqama,
 * start or end of the silent window); when it rings, {@link #refresh} redraws everything and sets the next one.
 *
 * Silent window: from the end of the adhan until a chosen number of minutes after the iqama, the ringer is silent.
 * The ringer comes back to what it was, unless the user already changed it by hand.
 */
final class PrayerNote {
    private PrayerNote() {}

    private static final String CHANNEL = "prayer_note";
    private static final int NOTE_ID = 11;
    private static final int REQ = 31;
    private static final String ACTION = "app.mylauncher.gtv.NOTE";
    /** How long the adhan recordings last (a little more), so the silence starts when the adhan is over. */
    private static final long ADHAN_MS = 210_000L;
    private static final long FAJR_ADHAN_MS = 220_000L;

    private static SharedPreferences p(Context c) {
        return c.getSharedPreferences("magiic", Context.MODE_PRIVATE);
    }

    static boolean noteOn(Context c) {
        return p(c).getBoolean("note_on", true);
    }

    static boolean silentOn(Context c) {
        return p(c).getBoolean("silent_on", false);
    }

    /** Minutes after the iqama at which the sound comes back. */
    static int silentAfter(Context c) {
        return p(c).getInt("silent_after", 15);
    }

    /** Minutes before the time of a prayer runs out at which to warn (0 = off). Fajr and Asr are on at first. */
    static int warnMin(Context c, int i) {
        return p(c).getInt("warn_" + i, i == PrayerCalc.FAJR || i == PrayerCalc.ASR ? 30 : 0);
    }

    /** What the time of each prayer runs out at (for the settings). */
    static final String[] END_NAMES = {"الشروق", "العصر", "المغرب", "العشاء", "منتصف الليل"};

    static boolean policyGranted(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        return nm != null && nm.isNotificationPolicyAccessGranted();
    }

    private static final class Slot {
        int index;
        boolean friday;
        long a;       // adhan
        long q;       // iqama (= adhan when there is none)
        long s;       // silence starts
        long e;       // silence ends
        long end;     // the prayer's time runs out
        long w;       // the warning before it (0 = none)
        boolean hasIqama;
    }

    private static List<Slot> slots(Context c, Cities.City city, long now) {
        int[] adj = PrayerPrefs.adjust(c);
        int[] iqm = PrayerPrefs.iqama(c);
        int after = silentAfter(c);
        List<Slot> out = new ArrayList<>();
        for (int off = -1; off <= 1; off++) {
            long[] t = PrayerCalc.day(city, now, off, adj);
            long[] nx = PrayerCalc.day(city, now, off + 1, adj);
            long rise = PrayerCalc.sunrise(city, now, off, adj);
            boolean fri = PrayerCalc.isFriday(city, now + off * 86_400_000L);
            for (int i = 0; i < 5; i++) {
                Slot s = new Slot();
                s.index = i;
                s.friday = fri;
                s.a = t[i];
                s.hasIqama = iqm[i] > 0;
                s.q = s.a + iqm[i] * 60_000L;
                s.s = s.a + (i == PrayerCalc.FAJR ? FAJR_ADHAN_MS : ADHAN_MS);
                s.e = Math.max(s.q + after * 60_000L, s.s + 60_000L);
                // The time of a prayer runs out at the sunrise, the next prayer, or the middle of the night.
                if (i == PrayerCalc.FAJR) s.end = rise;
                else if (i < 4) s.end = t[i + 1];
                else s.end = t[3] + (nx[0] - t[3]) / 2L;
                int wm = warnMin(c, i);
                s.w = wm > 0 ? s.end - wm * 60_000L : 0L;
                out.add(s);
            }
        }
        Collections.sort(out, new Comparator<Slot>() {
            @Override
            public int compare(Slot x, Slot y) {
                return Long.compare(x.a, y.a);
            }
        });
        return out;
    }

    /** Redraws the notification, applies or lifts the silence, and sets the alarm for the next change. */
    static void refresh(Context c) {
        c = c.getApplicationContext();
        Cities.City city = Cities.selected(c);
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        AlarmManager am = c.getSystemService(AlarmManager.class);
        if (city == null || nm == null || am == null) return;
        long now = System.currentTimeMillis();
        List<Slot> slots = slots(c, city, now);

        Slot cur = null;
        for (Slot s : slots) {
            if (s.q > now) {
                cur = s;
                break;
            }
        }
        if (noteOn(c)) {
            if (cur != null) post(c, nm, city, cur, now);
        } else {
            nm.cancel(NOTE_ID);
        }
        if (cur != null) widgets(c, city, cur, now);
        Widgets.refresh(c);

        silence(c, slots, now);
        warn(c, nm, city, slots, now);

        long next = Long.MAX_VALUE;
        for (Slot s : slots) {
            long[] ts = {s.a, s.q, s.s, s.e, s.w};
            for (long x : ts) if (x > now && x < next) next = x;
        }
        PendingIntent pi = PendingIntent.getBroadcast(c, REQ,
            new Intent(c, NoteReceiver.class).setAction(ACTION), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (next == Long.MAX_VALUE) {
            am.cancel(pi);
            return;
        }
        try {
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi);
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi);
            }
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi);
        }
    }

    private static final int[] W_L = {R.id.w_l0, R.id.w_l1, R.id.w_l2, R.id.w_l3, R.id.w_l4};
    private static final int[] W_D = {R.id.w_d0, R.id.w_d1, R.id.w_d2, R.id.w_d3, R.id.w_d4};
    private static final int[] W_N = {R.id.w_n0, R.id.w_n1, R.id.w_n2, R.id.w_n3, R.id.w_n4};
    private static final int[] W_T = {R.id.w_t0, R.id.w_t1, R.id.w_t2, R.id.w_t3, R.id.w_t4};

    private static String ap(String s) {
        return s.replace("AM", "ص").replace("PM", "م");
    }

    private static PendingIntent radioIntent(Context c, int req, String action, boolean foreground) {
        Intent i = new Intent(c, RadioService.class).setAction(action)
            .putExtra("station", RadioService.lastStation(c));
        int fl = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        return foreground ? PendingIntent.getForegroundService(c, req, i, fl) : PendingIntent.getService(c, req, i, fl);
    }

    /** Redraws the home-screen widgets (if any): the date, the countdown, the five times and the radio buttons. */
    private static void widgets(Context c, Cities.City city, Slot s, long now) {
        try {
            AppWidgetManager awm = AppWidgetManager.getInstance(c);
            int[] ids = awm.getAppWidgetIds(new ComponentName(c, PrayerWidget.class));
            if (ids == null || ids.length == 0) return;
            boolean iqama = s.hasIqama && now >= s.a;
            long target = iqama ? s.q : s.a;
            int[] adj = PrayerPrefs.adjust(c);
            RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.widget_prayer);

            java.util.Calendar cal = Hijri.today(c);
            rv.setTextViewText(R.id.w_day, String.valueOf(cal.get(java.util.Calendar.DAY_OF_MONTH)));
            // the big number is the day of the month, so beside it: the weekday, the month and the year
            String greg = Hijri.gregorianAr(cal);
            rv.setTextViewText(R.id.w_date, Hijri.weekdayAr(cal) + "، " + greg.substring(greg.indexOf(' ') + 1));
            rv.setTextViewText(R.id.w_hijri, Hijri.hijriAr(Hijri.of(cal, Hijri.adjust(c))));

            rv.setChronometerCountDown(R.id.w_clock, true);
            rv.setChronometer(R.id.w_clock, SystemClock.elapsedRealtime() + (target - now), null, true);
            String name = PrayerCalc.name(c, s.index, s.friday);
            rv.setTextViewText(R.id.w_until, (iqama ? "حتى إقامة " : "حتى أذان ") + name);

            long[] t = PrayerCalc.day(city, now, 0, adj);
            int curIdx = 4;
            for (int i = 0; i < 5; i++) if (t[i] <= now) curIdx = i;
            if (t[0] > now) curIdx = 4;
            for (int i = 0; i < 5; i++) {
                boolean on = i == curIdx;
                rv.setTextViewText(W_N[i], PrayerCalc.name(c, i, PrayerCalc.isFriday(city, now)));
                rv.setTextViewText(W_T[i], ap(PrayerCalc.time(c, city, t[i])));
                rv.setTextColor(W_N[i], on ? 0xFFFFFFFF : 0xFFB8C2DC);
                rv.setTextColor(W_T[i], on ? 0xFFFFFFFF : 0xFFB8C2DC);
                rv.setTextColor(W_D[i], on ? 0xFFE8B84A : 0xFFC9CCE0);
                rv.setTextViewTextSize(W_D[i], android.util.TypedValue.COMPLEX_UNIT_SP, on ? 26 : 18);
                rv.setInt(W_L[i], "setBackgroundColor", i <= curIdx ? 0xFFE8B84A : 0xFF8A8FB0);
            }
            long rise = PrayerCalc.sunrise(city, now, 0, adj);
            rv.setTextViewText(R.id.w_sun, "☀ الشروق " + ap(PrayerCalc.time(c, city, rise)));
            rv.setTextViewText(R.id.w_city, "📍 " + city.name(true));

            boolean radio = RadioService.on();
            int st = radio ? RadioService.station() : RadioService.lastStation(c);
            rv.setTextViewText(R.id.w_station, RadioService.STATIONS[st].shortName(true));
            rv.setImageViewResource(R.id.w_play, radio ? R.drawable.ic_w_stop : R.drawable.ic_w_play);
            rv.setOnClickPendingIntent(R.id.w_play, radio ? radioIntent(c, 51, "app.mylauncher.gtv.RADIO_STOP", false)
                : radioIntent(c, 51, "app.mylauncher.gtv.RADIO_PLAY", true));
            rv.setOnClickPendingIntent(R.id.w_next, radioIntent(c, 52, "app.mylauncher.gtv.RADIO_NEXT", true));
            rv.setOnClickPendingIntent(R.id.w_prev, radioIntent(c, 53, "app.mylauncher.gtv.RADIO_PREV", true));

            rv.setOnClickPendingIntent(R.id.w_root, PendingIntent.getActivity(c, 2, new Intent(c, PhoneActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
            awm.updateAppWidget(ids, rv);
        } catch (RuntimeException ignored) {
            // The home screen may be busy; the next change redraws it.
        }
    }

    private static void post(Context c, NotificationManager nm, Cities.City city, Slot s, long now) {
        NotificationChannel ch = new NotificationChannel(CHANNEL, "الصلاة القادمة", NotificationManager.IMPORTANCE_LOW);
        ch.setShowBadge(false);
        ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(ch);

        boolean iqama = s.hasIqama && now >= s.a;
        long target = iqama ? s.q : s.a;
        String name = PrayerCalc.name(c, s.index, s.friday);
        String title = (iqama ? "إقامة " : "أذان ") + name + " " + ap(PrayerCalc.time(c, city, target));

        RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.note_prayer);
        rv.setTextViewText(R.id.note_title, title);
        rv.setChronometerCountDown(R.id.note_clock, true);
        rv.setChronometer(R.id.note_clock, SystemClock.elapsedRealtime() + (target - now), null, true);
        rv.setTextViewText(R.id.note_label, iqama ? "باقي على الإقامة" : "باقي على الأذان");

        PendingIntent open = PendingIntent.getActivity(c, 0, new Intent(c, PhoneActivity.class)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(c, CHANNEL)
            .setSmallIcon(R.drawable.ic_crescent)
            .setStyle(new Notification.DecoratedCustomViewStyle())
            .setCustomContentView(rv)
            .setContentTitle(title)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_STATUS)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentIntent(open)
            .build();
        try {
            nm.notify(NOTE_ID, n);
        } catch (RuntimeException ignored) {
            // No notification permission yet.
        }
    }

    /** Warns once, shortly before the time of a prayer runs out. */
    private static void warn(Context c, NotificationManager nm, Cities.City city, List<Slot> slots, long now) {
        for (Slot s : slots) {
            if (s.w <= 0 || now < s.w || now >= s.end) continue;
            if (p(c).getLong("warn_at_" + s.index, 0L) == s.a) continue;
            if (Track.done(c, s.a, s.index)) continue; // already prayed: no warning
            p(c).edit().putLong("warn_at_" + s.index, s.a).apply();
            NotificationChannel ch = new NotificationChannel("prayer_warn", "قرب خروج وقت الصلاة", NotificationManager.IMPORTANCE_HIGH);
            nm.createNotificationChannel(ch);
            long mins = Math.max(1, (s.end - now + 30_000L) / 60_000L);
            String name = PrayerCalc.name(c, s.index, s.friday);
            String until = END_NAMES[s.index] + " " + PrayerCalc.time(c, city, s.end).replace("AM", "ص").replace("PM", "م");
            PendingIntent open = PendingIntent.getActivity(c, 1, new Intent(c, PhoneActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Notification n = new Notification.Builder(c, "prayer_warn")
                .setSmallIcon(R.drawable.ic_crescent)
                .setContentTitle("اقترب خروج وقت " + name)
                .setContentText("باقي " + mins + " دقيقة، يخرج الوقت عند " + until)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_REMINDER)
                .setContentIntent(open)
                .build();
            try {
                nm.notify(20 + s.index, n);
            } catch (RuntimeException ignored) {
                // No notification permission yet.
            }
        }
    }

    /**
     * Quiets the phone inside the window and gives it back afterwards, with Do Not Disturb (priority only) instead of
     * the ringer mode, because some phones pop up a dialog every time an app changes the ringer. A short note and a
     * buzz tell the user, and the note goes away by itself.
     */
    private static void silence(Context c, List<Slot> slots, long now) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        AudioManager am = c.getSystemService(AudioManager.class);
        if (nm == null || am == null) return;
        SharedPreferences sp = p(c);
        boolean inside = false;
        for (Slot s : slots) {
            if (now >= s.s && now < s.e) {
                inside = true;
                break;
            }
        }
        boolean want = silentOn(c) && policyGranted(c) && inside;
        boolean active = sp.getBoolean("sil_active", false);
        try {
            if (want && !active) {
                int f = nm.getCurrentInterruptionFilter();
                if (f == NotificationManager.INTERRUPTION_FILTER_ALL || f == NotificationManager.INTERRUPTION_FILTER_UNKNOWN) {
                    sp.edit().putInt("sil_prev_f", f).putBoolean("sil_dnd", true).putBoolean("sil_active", true).apply();
                    nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY);
                    tell(c, nm, "الهاتف صامت أثناء الصلاة", "يرجع للوضع العادي بعد الصلاة");
                }
            } else if (!want && active) {
                boolean dnd = sp.getBoolean("sil_dnd", false);
                sp.edit().putBoolean("sil_active", false).putBoolean("sil_dnd", false).apply();
                if (dnd) {
                    // Give it back only if nobody changed it meanwhile.
                    if (nm.getCurrentInterruptionFilter() == NotificationManager.INTERRUPTION_FILTER_PRIORITY) {
                        nm.setInterruptionFilter(sp.getInt("sil_prev_f", NotificationManager.INTERRUPTION_FILTER_ALL));
                        tell(c, nm, "رجع الهاتف للوضع العادي", "انتهى وقت الصمت");
                    }
                } else if (am.getRingerMode() == AudioManager.RINGER_MODE_SILENT) {
                    am.setRingerMode(sp.getInt("sil_prev", AudioManager.RINGER_MODE_NORMAL)); // set by an older version
                }
            }
        } catch (SecurityException ignored) {
            sp.edit().putBoolean("sil_active", false).apply();
        }
    }

    /** A short note that removes itself, with a double buzz. */
    private static void tell(Context c, NotificationManager nm, String title, String text) {
        try {
            NotificationChannel ch = new NotificationChannel("silence_note", "تنبيه الصامت", NotificationManager.IMPORTANCE_DEFAULT);
            ch.setSound(null, null);
            ch.enableVibration(false);
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
            nm.notify(7311, new Notification.Builder(c, "silence_note").setSmallIcon(R.drawable.ic_crescent)
                .setContentTitle(title).setContentText(text).setTimeoutAfter(8000L).setAutoCancel(true).build());
            android.os.Vibrator v = c.getSystemService(android.os.Vibrator.class);
            if (v != null && v.hasVibrator()) {
                v.vibrate(android.os.VibrationEffect.createWaveform(new long[] {0, 180, 120, 180}, -1));
            }
        } catch (RuntimeException ignored) {
            // no permission to post the note, or no vibrator; the change itself still happened
        }
    }
}
