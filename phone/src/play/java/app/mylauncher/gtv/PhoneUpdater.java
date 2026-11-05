package app.mylauncher.gtv;

import android.app.Activity;
import android.content.Context;

/** In the Google Play build the store updates the app, so there is no update check or installer here. */
final class PhoneUpdater {
    private PhoneUpdater() {
    }

    static String installedName(Context c) {
        try {
            return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "?";
        }
    }

    static void check(Activity a) {
    }
}
