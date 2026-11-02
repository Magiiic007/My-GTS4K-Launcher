package app.mylauncher.gtv;

import android.content.Context;
import android.icu.util.IslamicCalendar;

import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

/** The Hijri (Umm al-Qura) date, moved by the days the user sets when the local sighting differs. Also the date names. */
final class Hijri {
    private Hijri() {}

    private static final String[] MONTHS_AR = {"محرم", "صفر", "ربيع الأول", "ربيع الآخر", "جمادى الأولى",
        "جمادى الآخرة", "رجب", "شعبان", "رمضان", "شوال", "ذو القعدة", "ذو الحجة"};
    private static final String[] MONTHS_EN = {"Muharram", "Safar", "Rabi' Al-Awwal", "Rabi' Al-Thani",
        "Jumada Al-Awwal", "Jumada Al-Thani", "Rajab", "Sha'ban", "Ramadan", "Shawwal", "Dhu Al-Qi'dah",
        "Dhu Al-Hijjah"};
    private static final String[] GREG_AR = {"يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو", "يوليو", "أغسطس",
        "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر"};
    private static final String[] GREG_EN = {"January", "February", "March", "April", "May", "June", "July", "August",
        "September", "October", "November", "December"};
    private static final String[] DAYS_AR = {"الأحد", "الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت"};
    private static final String[] DAYS_EN = {"Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday",
        "Saturday"};

    static int adjust(Context c) {
        return c.getSharedPreferences("magiic", Context.MODE_PRIVATE).getInt("hijri_adj", 0);
    }

    static void setAdjust(Context c, int days) {
        c.getSharedPreferences("magiic", Context.MODE_PRIVATE).edit().putInt("hijri_adj", days).apply();
    }

    /** {year, month 1..12, day} for the calendar day of {@code day} (any time on it), moved by {@code adjustDays}. */
    static int[] of(Calendar day, int adjustDays) {
        Calendar c = (Calendar) day.clone();
        c.set(Calendar.HOUR_OF_DAY, 12);
        c.add(Calendar.DAY_OF_MONTH, adjustDays);
        IslamicCalendar h = new IslamicCalendar();
        h.setCalculationType(IslamicCalendar.CalculationType.ISLAMIC_UMALQURA);
        h.setTime(c.getTime());
        return new int[] {h.get(IslamicCalendar.YEAR), h.get(IslamicCalendar.MONTH) + 1,
            h.get(IslamicCalendar.DAY_OF_MONTH)};
    }

    static String hijriAr(int[] h) {
        return h[2] + " " + MONTHS_AR[h[1] - 1] + " " + h[0] + " هـ";
    }

    static String hijriEn(int[] h) {
        return h[2] + " " + MONTHS_EN[h[1] - 1] + " " + h[0] + " AH";
    }

    /** Arabic day name; index 0 = Sunday ... 6 = Saturday. */
    static String dayNameAr(int index) {
        return DAYS_AR[index];
    }

    static String weekdayAr(Calendar c) {
        return DAYS_AR[c.get(Calendar.DAY_OF_WEEK) - 1];
    }

    static String weekdayEn(Calendar c) {
        return DAYS_EN[c.get(Calendar.DAY_OF_WEEK) - 1];
    }

    static String gregorianAr(Calendar c) {
        return c.get(Calendar.DAY_OF_MONTH) + " " + GREG_AR[c.get(Calendar.MONTH)] + " " + c.get(Calendar.YEAR);
    }

    static String gregorianEn(Calendar c) {
        return weekdayEn(c) + ", " + GREG_EN[c.get(Calendar.MONTH)] + " " + c.get(Calendar.DAY_OF_MONTH) + ", "
            + c.get(Calendar.YEAR);
    }

    /** Today in the city's own clock, or in the TV's when no city is chosen yet. */
    static Calendar today(Context c) {
        Cities.City city = Cities.selected(c);
        Calendar cal = Calendar.getInstance(city != null ? PrayerCalc.zone(city) : TimeZone.getDefault(),
            Locale.ENGLISH);
        cal.setTimeInMillis(System.currentTimeMillis());
        return cal;
    }
}
