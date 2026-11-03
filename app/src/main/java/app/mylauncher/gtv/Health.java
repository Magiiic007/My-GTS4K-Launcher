package app.mylauncher.gtv;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.List;

/**
 * The quiet health check. It runs only when the home screen opens (no timers, no background work) and looks at a few
 * cheap facts. When everything is fine the person sees nothing; when something needs them, a small notice appears.
 */
final class Health {
    private Health() {}

    static final class Issue {
        final String text;
        final Runnable fix;

        Issue(String text, Runnable fix) {
            this.text = text;
            this.fix = fix;
        }
    }

    static List<Issue> check(final Activity a, Runnable askOverlay, Runnable pickCity) {
        List<Issue> out = new ArrayList<>();
        if (!Settings.canDrawOverlays(a)) out.add(new Issue(a.getString(R.string.notice_overlay), askOverlay));
        if (Build.VERSION.SDK_INT >= 33
            && a.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            out.add(new Issue(a.getString(R.string.notice_notif), new Runnable() {
                @Override
                public void run() {
                    a.requestPermissions(new String[] {Manifest.permission.POST_NOTIFICATIONS}, 7);
                }
            }));
        }
        if (Cities.selected(a) == null) out.add(new Issue(a.getString(R.string.notice_city), pickCity));
        try {
            long free = new StatFs(Environment.getDataDirectory().getPath()).getAvailableBytes();
            if (free < 400L * 1048576L) {
                out.add(new Issue(a.getString(R.string.notice_space, Cleaner.size(free)), new Runnable() {
                    @Override
                    public void run() {
                        Cleaner.open(a);
                    }
                }));
            }
        } catch (RuntimeException ignored) {
            // No figure: no notice.
        }
        if (Updater.available(a)) {
            out.add(new Issue(a.getString(R.string.notice_update, (int) Updater.latest(a)), new Runnable() {
                @Override
                public void run() {
                    Updater.offer(a);
                }
            }));
        }
        return out;
    }

    static void show(final Activity a, final List<Issue> issues) {
        if (issues.size() == 1) {
            issues.get(0).fix.run();
            return;
        }
        String[] items = new String[issues.size()];
        for (int i = 0; i < items.length; i++) items[i] = issues.get(i).text;
        new AlertDialog.Builder(a, Theme.dialogStyle(a))
            .setTitle(R.string.notice_title)
            .setItems(items, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int which) {
                    issues.get(which).fix.run();
                }
            })
            .setNegativeButton(R.string.close, null)
            .show();
    }
}
