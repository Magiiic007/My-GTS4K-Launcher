package app.mylauncher.gtv;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import android.provider.Settings;

import java.util.List;

/**
 * Makes Magiic TV the home screen without any computer: a small accessibility service notices when the device's own
 * home screen comes up and brings Magiic TV forward instead. It can be switched off from inside the app at any time.
 */
final class HomeOverride {
    private HomeOverride() {}

    private static final String PREFS = "magiic";
    private static final String ON = "override_on";
    private static final String PAUSED = "override_paused_until";

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Is the takeover switched on (inside the app)? On by default; it only works once the service is allowed. */
    static boolean on(Context c) {
        return prefs(c).getBoolean(ON, true);
    }

    static void setOn(Context c, boolean on) {
        prefs(c).edit().putBoolean(ON, on).apply();
    }

    /** True while the device's own home screen has been opened on purpose (so it is not taken over again). */
    static boolean paused(Context c) {
        return System.currentTimeMillis() < prefs(c).getLong(PAUSED, 0L);
    }

    static void pauseFor(Context c, long ms) {
        prefs(c).edit().putLong(PAUSED, System.currentTimeMillis() + ms).apply();
    }

    /** Has the person allowed the service in the device's accessibility settings? */
    static boolean allowed(Context c) {
        try {
            String s = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            return s != null && s.contains(c.getPackageName()) && s.contains("HomeOverrideService");
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Is this window the device's own home screen? */
    static boolean isStockHome(Context c, String pkg, String cls) {
        if (pkg == null || pkg.equals(c.getPackageName())) return false;
        try {
            List<ResolveInfo> homes = c.getPackageManager().queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0);
            for (ResolveInfo ri : homes) {
                ActivityInfo ai = ri.activityInfo;
                if (ai != null && pkg.equals(ai.packageName) && cls != null && cls.equals(ai.name)) return true;
            }
        } catch (RuntimeException e) {
            // Fall through to the name check.
        }
        return pkg.startsWith("com.google.android.apps.tv.launcherx") && cls != null && cls.contains("Home");
    }
}
