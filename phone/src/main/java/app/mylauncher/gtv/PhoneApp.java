package app.mylauncher.gtv;

import android.app.Application;

import java.util.Locale;

/** Arabic everywhere, with Western (0-9) digits. */
public class PhoneApp extends Application {
    static final Locale AR = Locale.forLanguageTag("ar-u-nu-latn");

    @Override
    public void onCreate() {
        super.onCreate();
        // Keep the reason of any crash, so the next start can show it (installed first, so even an early crash is kept).
        final Thread.UncaughtExceptionHandler old = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                record(e);
                if (old != null) old.uncaughtException(t, e);
            }
        });
        Locale.setDefault(AR);
        try {
            Backup.applyDefaults(this);
            PrayerCalc.configure(this);
        } catch (RuntimeException e) {
            record(e);
        }
    }

    private void record(Throwable e) {
        try {
            java.io.StringWriter w = new java.io.StringWriter();
            e.printStackTrace(new java.io.PrintWriter(w));
            getSharedPreferences("magiic", MODE_PRIVATE).edit().putString("dbg_crash", w.toString())
                .putLong("dbg_crash_at", System.currentTimeMillis()).commit();
        } catch (RuntimeException ignored) {
            // Nothing more can be done.
        }
    }
}
