package app.mylauncher.gtv;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AppOpsManager;
import android.app.usage.StorageStats;
import android.app.usage.StorageStatsManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Process;
import android.os.StatFs;
import android.os.UserHandle;
import android.os.storage.StorageManager;
import android.provider.Settings;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * "Deep clean". An app is not allowed to wipe other apps' files, so this only does what is safe: it clears Magiic TV's
 * own temporary files, lists the apps that use the most space (each opens its own settings screen, where the person
 * clears that app's cache), and lists big files in the Downloads folder, each deleted only after a confirmation.
 * Nothing is deleted on its own, and no running app is touched.
 */
final class Cleaner {
    private Cleaner() {}

    static String size(long bytes) {
        double mb = bytes / 1048576.0;
        if (mb >= 1024) return String.format(Locale.US, "%.1f GB", mb / 1024.0);
        if (mb >= 1) return String.format(Locale.US, "%.0f MB", mb);
        return String.format(Locale.US, "%.0f KB", bytes / 1024.0);
    }

    // ---------------------------------------------------------------- the menu

    static void open(final Activity a) {
        String space = "";
        try {
            StatFs sf = new StatFs(Environment.getDataDirectory().getPath());
            space = a.getString(R.string.clean_space, size(sf.getAvailableBytes()), size(sf.getTotalBytes()));
        } catch (RuntimeException ignored) {
            // No figure to show.
        }
        String[] items = {
            a.getString(R.string.clean_own), a.getString(R.string.clean_apps), a.getString(R.string.clean_files),
        };
        new AlertDialog.Builder(a)
            .setTitle(a.getString(R.string.menu_clean) + (space.isEmpty() ? "" : "\n" + space))
            .setItems(items, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int which) {
                    if (which == 0) clearOwn(a);
                    else if (which == 1) apps(a);
                    else files(a);
                }
            })
            .setNegativeButton(R.string.close, null)
            .show();
    }

    // ---------------------------------------------------------------- 1) the launcher's own temporary files

    private static long delete(File f) {
        long n = 0;
        if (f == null || !f.exists()) return 0;
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) n += delete(k);
        else n += f.length();
        //noinspection ResultOfMethodCallIgnored
        if (!f.equals(f.getParentFile()) && f.isFile()) f.delete();
        return n;
    }

    private static void clearOwn(Activity a) {
        long freed = delete(a.getCacheDir()) + delete(a.getExternalCacheDir());
        Toast.makeText(a, a.getString(R.string.clean_own_done, size(freed)), Toast.LENGTH_LONG).show();
    }

    // ---------------------------------------------------------------- 2) apps by space used

    private static boolean usageAllowed(Context c) {
        try {
            AppOpsManager ao = (AppOpsManager) c.getSystemService(Context.APP_OPS_SERVICE);
            int mode = Build.VERSION.SDK_INT >= 29
                ? ao.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.getPackageName())
                : ao.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.getPackageName());
            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static final class Row {
        String label, pkg;
        long total, cache;
    }

    private static void apps(final Activity a) {
        if (!usageAllowed(a)) {
            new AlertDialog.Builder(a)
                .setTitle(R.string.clean_apps)
                .setMessage(R.string.clean_usage_help)
                .setPositiveButton(R.string.clean_allow, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        SettingsMenu.launch(a, Settings.ACTION_USAGE_ACCESS_SETTINGS, R.string.clean_usage_tip);
                    }
                })
                .setNegativeButton(R.string.close, null)
                .show();
            return;
        }
        Toast.makeText(a, R.string.clean_working, Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<Row> rows = scanApps(a);
                a.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        showApps(a, rows);
                    }
                });
            }
        }).start();
    }

    private static List<Row> scanApps(Context c) {
        List<Row> rows = new ArrayList<>();
        try {
            PackageManager pm = c.getPackageManager();
            StorageStatsManager ssm = (StorageStatsManager) c.getSystemService(Context.STORAGE_STATS_SERVICE);
            Set<String> seen = new HashSet<>();
            List<ResolveInfo> found = new ArrayList<>();
            found.addAll(pm.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0));
            found.addAll(pm.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0));
            UserHandle me = Process.myUserHandle();
            for (ResolveInfo ri : found) {
                if (ri.activityInfo == null) continue;
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(c.getPackageName()) || !seen.add(pkg)) continue;
                try {
                    StorageStats s = ssm.queryStatsForPackage(StorageManager.UUID_DEFAULT, pkg, me);
                    Row r = new Row();
                    r.label = String.valueOf(ri.loadLabel(pm));
                    r.pkg = pkg;
                    r.cache = s.getCacheBytes();
                    r.total = s.getAppBytes() + s.getDataBytes() + s.getCacheBytes();
                    rows.add(r);
                } catch (java.io.IOException | RuntimeException e) {
                    // This app's figure is not available: leave it out.
                }
            }
        } catch (RuntimeException e) {
            // Nothing to show.
        }
        Collections.sort(rows, new Comparator<Row>() {
            @Override
            public int compare(Row x, Row y) {
                return x.total < y.total ? 1 : x.total > y.total ? -1 : 0;
            }
        });
        return rows;
    }

    private static void showApps(final Activity a, final List<Row> rows) {
        if (rows.isEmpty()) {
            Toast.makeText(a, R.string.clean_apps_none, Toast.LENGTH_LONG).show();
            return;
        }
        int n = Math.min(30, rows.size());
        String[] items = new String[n];
        for (int i = 0; i < n; i++) {
            Row r = rows.get(i);
            items[i] = r.label + " - " + size(r.total) + "  (" + a.getString(R.string.clean_cache) + " " + size(r.cache) + ")";
        }
        new AlertDialog.Builder(a)
            .setTitle(R.string.clean_apps)
            .setItems(items, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int which) {
                    try {
                        a.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:" + rows.get(which).pkg)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                        Toast.makeText(a, R.string.clean_app_tip, Toast.LENGTH_LONG).show();
                    } catch (ActivityNotFoundException | SecurityException e) {
                        Toast.makeText(a, R.string.settings_missing, Toast.LENGTH_LONG).show();
                    }
                }
            })
            .setNegativeButton(R.string.close, null)
            .show();
    }

    // ---------------------------------------------------------------- 3) files in Downloads

    private static boolean allFiles() {
        return Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager();
    }

    private static void files(final Activity a) {
        File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        final List<File> list = new ArrayList<>();
        File[] all = dir == null ? null : dir.listFiles();
        if (all != null) {
            for (File f : all) if (f.isFile() && f.length() > 0) list.add(f);
        }
        Collections.sort(list, new Comparator<File>() {
            @Override
            public int compare(File x, File y) {
                return x.length() < y.length() ? 1 : x.length() > y.length() ? -1 : 0;
            }
        });
        if (list.isEmpty()) {
            AlertDialog.Builder b = new AlertDialog.Builder(a)
                .setTitle(R.string.clean_files)
                .setMessage(allFiles() ? R.string.clean_files_none : R.string.clean_files_hidden)
                .setNegativeButton(R.string.close, null);
            if (!allFiles()) {
                b.setPositiveButton(R.string.clean_allow, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        try {
                            a.startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                Uri.parse("package:" + a.getPackageName())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                        } catch (ActivityNotFoundException | SecurityException e) {
                            Toast.makeText(a, R.string.clean_files_nosetting, Toast.LENGTH_LONG).show();
                        }
                    }
                });
            }
            b.show();
            return;
        }
        int n = Math.min(40, list.size());
        String[] items = new String[n];
        for (int i = 0; i < n; i++) items[i] = list.get(i).getName() + "  -  " + size(list.get(i).length());
        new AlertDialog.Builder(a)
            .setTitle(R.string.clean_files)
            .setItems(items, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int which) {
                    confirmDelete(a, list.get(which));
                }
            })
            .setNegativeButton(R.string.close, null)
            .show();
    }

    private static void confirmDelete(final Activity a, final File f) {
        new AlertDialog.Builder(a)
            .setTitle(f.getName())
            .setMessage(a.getString(R.string.clean_delete_ask, size(f.length())))
            .setPositiveButton(R.string.clean_delete, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int w) {
                    boolean ok = f.delete();
                    Toast.makeText(a, ok ? R.string.clean_deleted : R.string.clean_delete_failed, Toast.LENGTH_LONG).show();
                    if (ok) files(a);
                }
            })
            .setNegativeButton(R.string.close, null)
            .show();
    }
}
