package app.mylauncher.gtv;

import android.content.Context;
import android.graphics.Color;
import android.view.View;

import java.util.Calendar;

/**
 * The light of the day. A thin coloured veil over the background picture moves with the real prayer times: only
 * shades of white, grey and black, light and calm: a soft darkening before dawn, a faint haze at sunrise, clear through
 * the day, a little grey toward sunset and at Maghrib, darker after it and darkest at night. Only the picture is tinted, never the text. It is recalculated once a minute
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
            Color.argb(80, 0, 0, 0),         // before dawn: soft darkening
            Color.argb(50, 40, 40, 40),      // dawn: lighter grey
            Color.argb(28, 255, 255, 255),   // sunrise: a faint white haze
            Color.argb(0, 0, 0, 0),          // day: nothing
            Color.argb(0, 0, 0, 0),
            Color.argb(24, 128, 128, 128),   // before sunset: faint grey
            Color.argb(46, 60, 60, 60),      // Maghrib: calm grey
            Color.argb(70, 20, 20, 20),      // dusk: darker
            Color.argb(90, 0, 0, 0)};        // night: darkest, still soft   // night: deep blue
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
