package app.mylauncher.gtv;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.NotificationManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
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
        final Runnable fix;

        Item(String name, String why, boolean ok, Runnable fix) {
            this.name = name;
            this.why = why;
            this.ok = ok;
            this.fix = fix;
        }
    }

    private static boolean flow;
    private static final Set<String> tried = new HashSet<>();

    private static SharedPreferences sp(Activity a) {
        return a.getSharedPreferences("permgate", Activity.MODE_PRIVATE);
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
                        // This TV has no settings screen for it.
                    }
                }
            }
        };
    }

    static List<Item> items(final Activity a) {
        List<Item> out = new ArrayList<>();
        NotificationManager nm = a.getSystemService(NotificationManager.class);
        boolean notif = nm == null || nm.areNotificationsEnabled();
        if (Build.VERSION.SDK_INT >= 33) {
            notif = notif && a.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        }
        out.add(new Item(a.getString(R.string.pg_notif), a.getString(R.string.pg_notif_why), notif, null));
        AlarmManager am = a.getSystemService(AlarmManager.class);
        out.add(new Item(a.getString(R.string.pg_exact), a.getString(R.string.pg_exact_why),
            Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms(),
            settings(a, Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, true)));
        out.add(new Item(a.getString(R.string.pg_overlay), a.getString(R.string.pg_overlay_why),
            Settings.canDrawOverlays(a), settings(a, Settings.ACTION_MANAGE_OVERLAY_PERMISSION, true)));
        out.add(new Item(a.getString(R.string.pg_install), a.getString(R.string.pg_install_why),
            a.getPackageManager().canRequestPackageInstalls(),
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

    /** An update over an existing install (not a fresh install): nothing to show when everything is already granted. */
    private static boolean upgraded(Activity a) {
        try {
            android.content.pm.PackageInfo i = a.getPackageManager().getPackageInfo(a.getPackageName(), 0);
            return i.lastUpdateTime > i.firstInstallTime;
        } catch (PackageManager.NameNotFoundException | RuntimeException e) {
            return false;
        }
    }

    /** First run, a new version, or a few days since the last look: show the list (on later looks only when something is missing). */
    static void maybeShow(Activity a) {
        if (flow || a.isFinishing()) return;
        SharedPreferences s = sp(a);
        int ver = s.getInt("ver", 0);
        long now = System.currentTimeMillis();
        boolean first = ver == 0;
        boolean due = first || ver != versionCode(a) || now - s.getLong("last", 0L) > EVERY_MS;
        if (!due) return;
        s.edit().putInt("ver", versionCode(a)).putLong("last", now).apply();
        // only when something is missing, and then only the missing ones are listed
        if (missing(a).isEmpty()) return;
        show(a);
    }

    private static void show(final Activity a) {
        StringBuilder sb = new StringBuilder();
        for (Item i : missing(a)) sb.append("\u2022  ").append(i.name).append("\n     ").append(i.why).append("\n");
        new Menu.Builder(a)
            .setTitle(a.getString(R.string.pg_title))
            .setMessage(sb.toString().trim())
            .setPositiveButton(R.string.pg_grant, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int w) {
                    flow = true;
                    tried.clear();
                    next(a);
                }
            }).setNegativeButton(R.string.pg_later, null).show();
    }

    /** Asks for the next missing permission. */
    private static void next(Activity a) {
        if (Build.VERSION.SDK_INT >= 33
            && a.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            && tried.add("rt")) {
            try {
                a.requestPermissions(new String[] {Manifest.permission.POST_NOTIFICATIONS}, REQ);
                return;
            } catch (RuntimeException ignored) {
                // go on to the next one
            }
        }
        List<Item> m = missing(a);
        for (Item i : m) {
            if (i.fix != null && tried.add(i.name)) {
                i.fix.run();
                return;
            }
        }
        flow = false;
        Toast.makeText(a, m.isEmpty() ? R.string.pg_done : R.string.pg_left, Toast.LENGTH_LONG).show();
    }

    /** Called when the screen comes back: carries on with the next missing permission. */
    static void resume(Activity a) {
        if (flow) next(a);
    }
}
