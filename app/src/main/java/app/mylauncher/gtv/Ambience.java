package app.mylauncher.gtv;

import android.content.Context;
import android.graphics.Color;
import android.view.View;

import java.util.Calendar;

/**
 * The light of the day. A thin coloured veil over the background picture moves with the real prayer times: a cool
 * blue before dawn, a soft warm glow at sunrise, clear through the day, amber toward sunset with the strongest glow at
 * Maghrib, then dusk and a deep blue night. Only the picture is tinted, never the text. It is recalculated once a minute
 * while the home screen is open.
 */
final class Ambience {
    private final Context ctx;
    private final View veil;

    Ambience(Context c, View veil) {
        ctx = c;
        this.veil = veil;
    }

    private static final long MIN = 60000L;

    void update() {
        long now = System.currentTimeMillis();
        long fajr, sunrise, asr, maghrib, isha;
        Cities.City city = Cities.selected(ctx);
        if (city != null) {
            int[] adj = PrayerPrefs.adjust(ctx);
            long[] t = PrayerCalc.day(city, now, 0, adj);
            fajr = t[PrayerCalc.FAJR];
            asr = t[PrayerCalc.ASR];
            maghrib = t[PrayerCalc.MAGHRIB];
            isha = t[PrayerCalc.ISHA];
            sunrise = PrayerCalc.sunrise(city, now, 0, adj);
        } else {
            fajr = at(5, 0);
            sunrise = at(6, 15);
            asr = at(15, 30);
            maghrib = at(18, 0);
            isha = at(19, 30);
        }
        long[] when = {fajr - 40 * MIN, fajr, sunrise, sunrise + 90 * MIN, asr, maghrib - 45 * MIN, maghrib,
            maghrib + 40 * MIN, isha};
        int[] colour = {
            Color.argb(96, 20, 36, 100),    // before dawn: deep blue
            Color.argb(78, 60, 80, 160),   // dawn: cooler, lighter blue
            Color.argb(64, 255, 190, 130), // sunrise: soft warm light
            Color.argb(0, 0, 0, 0),        // day: nothing
            Color.argb(0, 0, 0, 0),
            Color.argb(80, 255, 170, 60),  // before sunset: amber
            Color.argb(110, 255, 120, 20),  // Maghrib: the strongest glow
            Color.argb(92, 90, 40, 120),   // dusk: purple
            Color.argb(100, 14, 26, 90)};   // night: deep blue
        int c;
        if (now <= when[0] || now >= when[when.length - 1]) {
            c = colour[0];
        } else {
            c = colour[0];
            for (int i = 0; i < when.length - 1; i++) {
                if (now >= when[i] && now < when[i + 1]) {
                    float f = (now - when[i]) / (float) Math.max(1L, when[i + 1] - when[i]);
                    c = mix(colour[i], colour[i + 1], f);
                    break;
                }
            }
        }
        // The pink look gets a lighter touch so it stays bright.
        if (Theme.kids(ctx)) c = Color.argb(Math.round(Color.alpha(c) * 0.7f), Color.red(c), Color.green(c), Color.blue(c));
        veil.setBackgroundColor(c);
    }

    private static long at(int hour, int minute) {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, hour);
        c.set(Calendar.MINUTE, minute);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private static int mix(int a, int b, float f) {
        return Color.argb(
            Math.round(Color.alpha(a) + (Color.alpha(b) - Color.alpha(a)) * f),
            Math.round(Color.red(a) + (Color.red(b) - Color.red(a)) * f),
            Math.round(Color.green(a) + (Color.green(b) - Color.green(a)) * f),
            Math.round(Color.blue(a) + (Color.blue(b) - Color.blue(a)) * f));
    }
}
