package app.mylauncher.gtv;

import android.content.Context;
import android.icu.util.IslamicCalendar;
import android.text.format.DateFormat;

import com.batoulapps.adhan.CalculationMethod;
import com.batoulapps.adhan.CalculationParameters;
import com.batoulapps.adhan.Madhab;
import com.batoulapps.adhan.Coordinates;
import com.batoulapps.adhan.PrayerAdjustments;
import com.batoulapps.adhan.PrayerTimes;
import com.batoulapps.adhan.data.DateComponents;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Prayer times, worked out on the TV with no internet (adhan-java). Egypt uses the Egyptian General Authority of
 * Survey method and Saudi Arabia uses Umm al-Qura, each country's official one. Asr is the Shafi'i/majority time.
 * Times are on the whole minute, and each prayer can be moved by a few minutes (Settings) to match a local timetable.
 */
final class PrayerCalc {
    private PrayerCalc() {}

    static final int FAJR = 0, DHUHR = 1, ASR = 2, MAGHRIB = 3, ISHA = 4;

    /** The calculation methods the phone lets you pick (index 0 of the setting means "automatic by country"). */
    static final CalculationMethod[] METHODS = {CalculationMethod.MUSLIM_WORLD_LEAGUE, CalculationMethod.EGYPTIAN,
        CalculationMethod.UMM_AL_QURA, CalculationMethod.KARACHI, CalculationMethod.NORTH_AMERICA,
        CalculationMethod.KUWAIT, CalculationMethod.QATAR};
    /** -1 = by country (the TV always does this). Set from the phone's settings. */
    private static volatile int methodIndex = -1;
    private static volatile boolean hanafi;

    static void configure(Context c) {
        android.content.SharedPreferences p = c.getSharedPreferences("magiic", Context.MODE_PRIVATE);
        int m = p.getInt("calc_method", -1);
        methodIndex = m >= 0 && m < METHODS.length ? m : -1;
        hanafi = p.getBoolean("calc_hanafi", false);
    }

    /** The next thing that rings: an adhan or an iqama, which prayer, when, and whether its Dhuhr is a Friday's. */
    static final class Event {
        final int index;
        final long at;
        final boolean friday;
        final boolean iqama;
        /** The Friday first adhan, rung before the Dhuhr adhan. */
        final boolean first;

        Event(int index, long at, boolean friday, boolean iqama) {
            this(index, at, friday, iqama, false);
        }

        Event(int index, long at, boolean friday, boolean iqama, boolean first) {
            this.first = first;
            this.index = index;
            this.at = at;
            this.friday = friday;
            this.iqama = iqama;
        }
    }

    /** The city's own clock, so a Cairo prayer is shown in Cairo time whatever zone the TV is set to. */
    static TimeZone zone(Cities.City city) {
        if (city.tz != null) return TimeZone.getTimeZone(city.tz);
        return TimeZone.getTimeZone("EG".equals(city.country) ? "Africa/Cairo" : "Asia/Riyadh");
    }

    /** The usual method of a country (Egypt and the Gulf as before; elsewhere the Muslim World League). */
    private static CalculationMethod byCountry(String c) {
        if ("EG".equals(c)) return CalculationMethod.EGYPTIAN;
        if ("SA".equals(c) || "AE".equals(c) || "BH".equals(c) || "OM".equals(c) || "YE".equals(c)) return CalculationMethod.UMM_AL_QURA;
        if ("KW".equals(c)) return CalculationMethod.KUWAIT;
        if ("QA".equals(c)) return CalculationMethod.QATAR;
        if ("PK".equals(c) || "IN".equals(c) || "BD".equals(c) || "AF".equals(c)) return CalculationMethod.KARACHI;
        if ("US".equals(c) || "CA".equals(c)) return CalculationMethod.NORTH_AMERICA;
        return CalculationMethod.MUSLIM_WORLD_LEAGUE;
    }

    static boolean isFriday(Cities.City city, long when) {
        Calendar c = Calendar.getInstance(zone(city));
        c.setTimeInMillis(when);
        return c.get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY;
    }

    /**
     * The five times (Fajr, Dhuhr, Asr, Maghrib, Isha) of the city's calendar day that holds {@code when}, plus
     * days. {@code adjust}: minutes added to each of the five.
     */
    static long[] day(Cities.City city, long when, int plusDays, int[] adjust) {
        PrayerTimes p = times(city, when, plusDays, adjust);
        return new long[] {minute(p.fajr), minute(p.dhuhr), minute(p.asr), minute(p.maghrib), minute(p.isha)};
    }

    /** Sunrise of the same day (shown in the list; it has no adhan). */
    static long sunrise(Cities.City city, long when, int plusDays, int[] adjust) {
        return minute(times(city, when, plusDays, adjust).sunrise);
    }

    private static PrayerTimes times(Cities.City city, long when, int plusDays, int[] adjust) {
        TimeZone tz = zone(city);
        Calendar cal = Calendar.getInstance(tz);
        cal.setTimeInMillis(when);
        cal.add(Calendar.DAY_OF_MONTH, plusDays);
        DateComponents date = new DateComponents(
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH));

        CalculationMethod method = methodIndex >= 0 ? METHODS[methodIndex]
            : byCountry(city.country);
        CalculationParameters params = method.getParameters();
        if (hanafi) params.madhab = Madhab.HANAFI;
        // Umm al-Qura: Isha is 2 hours after Maghrib in Ramadan (1.5 hours otherwise). The evening belongs to the
        // next Hijri day, so this starts on the night before the 1st of Ramadan.
        if (method == CalculationMethod.UMM_AL_QURA && nextHijriDayIsRamadan(cal)) params.ishaInterval = 120;
        params.adjustments = new PrayerAdjustments(adjust[FAJR], 0, adjust[DHUHR], adjust[ASR],
            adjust[MAGHRIB], adjust[ISHA]);

        return new PrayerTimes(new Coordinates(city.lat, city.lon), date, params);
    }

    /**
     * The first adhan or iqama after {@code now}. {@code iqama}: minutes after each adhan (0 = none). Yesterday,
     * today and tomorrow are looked at, so an iqama after midnight or tomorrow's Fajr is never missed.
     */
    static Event nextEvent(Cities.City city, long now, int[] adjust, int[] iqama) {
        Event best = null;
        for (int d = -1; d <= 1; d++) {
            long[] t = day(city, now, d, adjust);
            for (int i = 0; i < 5; i++) {
                boolean friday = i == DHUHR && isFriday(city, t[i]);
                if (t[i] > now && (best == null || t[i] < best.at)) best = new Event(i, t[i], friday, false);
                if (iqama[i] > 0) {
                    long q = t[i] + iqama[i] * 60000L;
                    if (q > now && (best == null || q < best.at)) best = new Event(i, q, friday, true);
                }
            }
        }
        return best;
    }

    /** What the alarm must ring next: {@link #nextEvent} plus the Friday first adhan, {@code firstMin} minutes early. */
    static Event nextAlarm(Cities.City city, long now, int[] adjust, int[] iqama, int firstMin) {
        Event best = nextEvent(city, now, adjust, iqama);
        if (firstMin > 0) {
            for (int d = -1; d <= 1; d++) {
                long[] t = day(city, now, d, adjust);
                if (!isFriday(city, t[DHUHR])) continue;
                long f = t[DHUHR] - firstMin * 60000L;
                if (f > now && (best == null || f < best.at)) best = new Event(DHUHR, f, true, false, true);
            }
        }
        return best;
    }

    private static boolean nextHijriDayIsRamadan(Calendar cal) {
        Calendar c = (Calendar) cal.clone();
        c.add(Calendar.DAY_OF_MONTH, 1);
        c.set(Calendar.HOUR_OF_DAY, 12);
        IslamicCalendar h = new IslamicCalendar();
        h.setCalculationType(IslamicCalendar.CalculationType.ISLAMIC_UMALQURA);
        h.setTime(c.getTime());
        return h.get(IslamicCalendar.MONTH) == IslamicCalendar.RAMADAN;
    }

    private static long minute(Date d) {
        return d.getTime() / 60000L * 60000L;
    }

    static String name(Context c, int index, boolean friday) {
        int[] names = {R.string.p_fajr, R.string.p_dhuhr, R.string.p_asr, R.string.p_maghrib, R.string.p_isha};
        return c.getString(index == DHUHR && friday ? R.string.p_jumuah : names[index]);
    }

    /** A time of day, following the TV's 12 or 24 hour setting, in the city's own zone. */
    static String time(Context c, Cities.City city, long millis) {
        boolean h24 = DateFormat.is24HourFormat(c);
        SimpleDateFormat f = new SimpleDateFormat(h24 ? "HH:mm" : "hh:mm a", Locale.US);
        f.setTimeZone(zone(city));
        return f.format(new Date(millis));
    }

    static String countdown(long ms) {
        long s = ms / 1000L;
        return String.format(Locale.US, "%d:%02d:%02d", s / 3600L, (s / 60L) % 60L, s % 60L);
    }
}
