package app.mylauncher.gtv;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The list of permissions the app needs. It is shown on the first run, and again (only when something is missing) after
 * every update and every few days. "Grant" walks through the missing ones one by one.
 */
final class PermGate {
    private PermGate() {}

    static final int REQ = 41;
    private static final long EVERY_MS = 3L * 24 * 3600 * 1000;

    static final class Item {
        final String name;
        final String why;
        final boolean ok;
        final boolean runtime;
        final Runnable fix;

        Item(String name, String why, boolean ok, boolean runtime, Runnable fix) {
            this.name = name;
            this.why = why;
            this.ok = ok;
            this.runtime = runtime;
            this.fix = fix;
        }
    }

    private static boolean flow;
    private static final Set<String> tried = new HashSet<>();

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("permgate", Context.MODE_PRIVATE);
    }

    private static boolean has(Activity a, String p) {
        return a.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED;
    }

    private static Runnable settings(final Activity a, final String action, final boolean withPackage) {
        return new Runnable() {
            @Override
            public void run() {
                try {
                    Intent i = new Intent(action);
                    if (withPackage) i.setData(Uri.parse("package:" + a.getPackageName()));
                    a.startActivity(i);
                } catch (RuntimeException e) {
                    try {
                        a.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:" + a.getPackageName())));
                    } catch (RuntimeException ignored) {
                        // nothing else to open
                    }
                }
            }
        };
    }

    static List<Item> items(final Activity a) {
        List<Item> out = new ArrayList<>();
        NotificationManager nm = a.getSystemService(NotificationManager.class);
        boolean notif = nm != null && nm.areNotificationsEnabled();
        if (Build.VERSION.SDK_INT >= 33) {
            boolean g = has(a, Manifest.permission.POST_NOTIFICATIONS);
            out.add(new Item("الإشعارات", "لتنبيه الأذان والأذكار", g && notif, true, g
                ? settings(a, Settings.ACTION_APP_NOTIFICATION_SETTINGS, false) : null));
        } else {
            out.add(new Item("الإشعارات", "لتنبيه الأذان والأذكار", notif, false,
                settings(a, Settings.ACTION_APP_NOTIFICATION_SETTINGS, false)));
        }
        boolean loc = has(a, Manifest.permission.ACCESS_FINE_LOCATION) || has(a, Manifest.permission.ACCESS_COARSE_LOCATION);
        out.add(new Item("الموقع", "لتحديد المدينة ومواقيت الصلاة تلقائيًا", loc, true, null));
        AlarmManager am = a.getSystemService(AlarmManager.class);
        out.add(new Item("المنبّه الدقيق", "ليأتي الأذان في وقته بالضبط",
            Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms(), false,
            settings(a, Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, true)));
        out.add(new Item("العرض فوق التطبيقات", "لتظهر الأذكار والأيقونة الدائرية فوق التطبيقات",
            Settings.canDrawOverlays(a), false, settings(a, Settings.ACTION_MANAGE_OVERLAY_PERMISSION, true)));
        PowerManager pm = a.getSystemService(PowerManager.class);
        out.add(new Item("البطارية: بدون قيود", "حتى لا يوقف الهاتف التطبيق في الخلفية",
            pm.isIgnoringBatteryOptimizations(a.getPackageName()), false,
            settings(a, Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, true)));
        out.add(new Item("تثبيت التحديثات", "لتحديث التطبيق من داخله",
            Build.VERSION.SDK_INT < 26 || a.getPackageManager().canRequestPackageInstalls(), false,
            settings(a, Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, true)));
        return out;
    }

    private static List<Item> missing(Activity a) {
        List<Item> m = new ArrayList<>();
        for (Item i : items(a)) if (!i.ok) m.add(i);
        return m;
    }

    private static int versionCode(Activity a) {
        try {
            return (int) a.getPackageManager().getPackageInfo(a.getPackageName(), 0).getLongVersionCode();
        } catch (PackageManager.NameNotFoundException | RuntimeException e) {
            return 0;
        }
    }

    /** First run, a new version, or a few days since the last look: show the list when something is missing. */
    static void maybeShow(Activity a) {
        if (flow || a.isFinishing()) return;
        SharedPreferences s = sp(a);
        int ver = s.getInt("ver", 0);
        long last = s.getLong("last", 0L);
        long now = System.currentTimeMillis();
        boolean first = ver == 0;
        boolean due = first || ver != versionCode(a) || now - last > EVERY_MS;
        if (!due) return;
        s.edit().putInt("ver", versionCode(a)).putLong("last", now).apply();
        // only when something is missing, and then only the missing ones are listed
        if (missing(a).isEmpty()) return;
        show(a);
    }

    private static void show(final Activity a) {
        StringBuilder sb = new StringBuilder();
        for (Item i : missing(a)) sb.append("•  ").append(i.name).append("\n    ").append(i.why).append("\n");
        new Pop(a).title("صلاحيات ناقصة").message(sb.toString().trim())
            .ok("منح الناقص", new Runnable() {
                @Override
                public void run() {
                    flow = true;
                    tried.clear();
                    next(a);
                }
            }).cancel("لاحقًا").show();
    }

    /** Asks for the next missing permission (the runtime ones together, in one request). */
    private static void next(Activity a) {
        List<Item> m = missing(a);
        ArrayList<String> rt = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 33 && !has(a, Manifest.permission.POST_NOTIFICATIONS) && tried.add("rt")) {
            rt.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (!has(a, Manifest.permission.ACCESS_FINE_LOCATION) && !has(a, Manifest.permission.ACCESS_COARSE_LOCATION)
            && !tried.contains("rtl")) {
            tried.add("rtl");
            rt.add(Manifest.permission.ACCESS_FINE_LOCATION);
            rt.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }
        if (!rt.isEmpty()) {
            a.requestPermissions(rt.toArray(new String[0]), REQ);
            return;
        }
        for (Item i : m) {
            if (i.fix != null && tried.add(i.name)) {
                try {
                    i.fix.run();
                } catch (RuntimeException ignored) {
                    // try the next one on return
                }
                return;
            }
        }
        flow = false;
        Toast.makeText(a, m.isEmpty() ? "تمت الصلاحيات كلها" : "بقيت صلاحيات لم تُمنح، يمكنك ضبطها من «تأكد أن الأذان يعمل»", Toast.LENGTH_LONG).show();
    }

    /** Called when the screen comes back: carries on with the next missing permission. */
    static void resume(Activity a) {
        if (flow) next(a);
    }
}
