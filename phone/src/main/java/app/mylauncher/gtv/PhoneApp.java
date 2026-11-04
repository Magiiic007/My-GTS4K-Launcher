package app.mylauncher.gtv;

import android.app.Application;

import java.util.Locale;

/** Arabic everywhere, with Western (0-9) digits. */
public class PhoneApp extends Application {
    static final Locale AR = Locale.forLanguageTag("ar-u-nu-latn");

    @Override
    public void onCreate() {
        super.onCreate();
        Locale.setDefault(AR);
    }
}
