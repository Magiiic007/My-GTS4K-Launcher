package app.mylauncher.gtv;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Updates the launcher from inside itself. Every build is published as the GitHub release "latest" whose notes carry
 * the build's version number; this reads it, and when it is newer than the installed one, downloads the APK and hands
 * it to Android's installer (which asks the person to confirm). The check runs at most twice a day, only while the
 * home screen is open, and costs one small request.
 */
final class Updater {
    private Updater() {}

    private static final String REPO = "Magiiic007/My-GTS4K-Launcher";
    private static final String API = "https://api.github.com/repos/" + REPO + "/releases/tags/latest";
    private static final String APK = "https://github.com/" + REPO + "/releases/download/latest/my-launcher.apk";
    private static final long CHECK_EVERY_MS = 12L * 60L * 60L * 1000L;
    private static final String PREFS = "magiic";

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static long installed(Context c) {
        try {
            PackageInfo p = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            return p.getLongVersionCode();
        } catch (PackageManager.NameNotFoundException e) {
            return 0L;
        }
    }

    static String installedName(Context c) {
        try {
            return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "?";
        }
    }

    static long latest(Context c) {
        return prefs(c).getLong("upd_latest", 0L);
    }

    static boolean available(Context c) {
        return latest(c) > installed(c);
    }

    /** Asks GitHub which build is the newest. Returns its version code, or -1 when it could not be read. */
    private static long fetch(Context c) {
        HttpURLConnection h = null;
        try {
            h = (HttpURLConnection) new URL(API).openConnection();
            h.setConnectTimeout(10000);
            h.setReadTimeout(10000);
            h.setRequestProperty("Accept", "application/vnd.github+json");
            h.setRequestProperty("User-Agent", "MagiicTV-Updater");
            if (h.getResponseCode() != 200) return -1;
            StringBuilder b = new StringBuilder();
            InputStream in = h.getInputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) b.append(new String(buf, 0, n, "UTF-8"));
            in.close();
            JSONObject o = new JSONObject(b.toString());
            Matcher m = Pattern.compile("versionCode=(\\d+)").matcher(o.optString("body", ""));
            if (!m.find()) return -1;
            long code = Long.parseLong(m.group(1));
            long size = 0;
            JSONArray assets = o.optJSONArray("assets");
            if (assets != null) {
                for (int i = 0; i < assets.length(); i++) {
                    JSONObject a = assets.getJSONObject(i);
                    if ("my-launcher.apk".equals(a.optString("name"))) size = a.optLong("size", 0L);
                }
            }
            prefs(c).edit().putLong("upd_latest", code).putLong("upd_size", size)
                .putLong("upd_checked", System.currentTimeMillis()).apply();
            return code;
        } catch (java.io.IOException | org.json.JSONException | RuntimeException e) {
            return -1;
        } finally {
            if (h != null) h.disconnect();
        }
    }

    /** Silent background check: only when 12 hours have passed. {@code done} runs on the main thread if it finished. */
    static void checkIfDue(final Activity a, final Runnable done) {
        long last = prefs(a).getLong("upd_checked", 0L);
        if (System.currentTimeMillis() - last < CHECK_EVERY_MS) return;
        // Mark it now so that a failed attempt is not repeated every time the home screen opens.
        prefs(a).edit().putLong("upd_checked", System.currentTimeMillis()).apply();
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (fetch(a) >= 0) a.runOnUiThread(done);
            }
        }).start();
    }

    // ---------------------------------------------------------------- the settings entry

    static void menu(final Activity a) {
        if (available(a)) {
            offer(a);
            return;
        }
        new AlertDialog.Builder(a)
            .setTitle(R.string.menu_update)
            .setMessage(a.getString(R.string.upd_current, installedName(a)))
            .setPositiveButton(R.string.upd_check, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int w) {
                    checkNow(a);
                }
            })
            .setNegativeButton(R.string.close, null)
            .show();
    }

    private static void checkNow(final Activity a) {
        Toast.makeText(a, R.string.upd_checking, Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                final long code = fetch(a);
                a.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (code < 0) {
                            Toast.makeText(a, R.string.upd_failed, Toast.LENGTH_LONG).show();
                        } else if (code > installed(a)) {
                            offer(a);
                        } else {
                            Toast.makeText(a, a.getString(R.string.upd_uptodate, installedName(a)),
                                Toast.LENGTH_LONG).show();
                        }
                    }
                });
            }
        }).start();
    }

    /** "A new version is ready": ask, then download and install. */
    static void offer(final Activity a) {
        new AlertDialog.Builder(a)
            .setTitle(R.string.menu_update)
            .setMessage(a.getString(R.string.upd_found, (int) latest(a), (int) installed(a)))
            .setPositiveButton(R.string.upd_btn_install, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int w) {
                    start(a);
                }
            })
            .setNegativeButton(R.string.close, null)
            .show();
    }

    // ---------------------------------------------------------------- download and install

    private static void start(final Activity a) {
        if (Build.VERSION.SDK_INT >= 26 && !a.getPackageManager().canRequestPackageInstalls()) {
            new AlertDialog.Builder(a)
                .setTitle(R.string.menu_update)
                .setMessage(R.string.upd_unknown_help)
                .setPositiveButton(R.string.upd_allow, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        try {
                            a.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:" + a.getPackageName())));
                        } catch (ActivityNotFoundException | SecurityException e) {
                            Toast.makeText(a, R.string.settings_missing, Toast.LENGTH_LONG).show();
                        }
                    }
                })
                .setNegativeButton(R.string.close, null)
                .show();
            return;
        }
        final AlertDialog box = new AlertDialog.Builder(a)
            .setTitle(R.string.menu_update)
            .setMessage(a.getString(R.string.upd_downloading, 0))
            .setCancelable(false)
            .show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean ok = download(a, box);
                if (!ok) {
                    a.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            box.dismiss();
                            Toast.makeText(a, R.string.upd_failed, Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        }).start();
    }

    /** Streams the APK straight into an install session (no temporary file), then commits it. */
    private static boolean download(final Activity a, final AlertDialog box) {
        HttpURLConnection h = null;
        PackageInstaller.Session session = null;
        PackageInstaller pi = a.getPackageManager().getPackageInstaller();
        int id = -1;
        try {
            h = (HttpURLConnection) new URL(APK).openConnection();
            h.setConnectTimeout(15000);
            h.setReadTimeout(20000);
            h.setRequestProperty("User-Agent", "MagiicTV-Updater");
            if (h.getResponseCode() != 200) return false;
            long total = h.getContentLengthLong();
            if (total <= 0) total = prefs(a).getLong("upd_size", 0L);

            PackageInstaller.SessionParams sp =
                new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            sp.setAppPackageName(a.getPackageName());
            if (Build.VERSION.SDK_INT >= 31) {
                sp.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);
            }
            id = pi.createSession(sp);
            session = pi.openSession(id);
            OutputStream out = session.openWrite("update", 0, total > 0 ? total : -1);
            InputStream in = h.getInputStream();
            byte[] buf = new byte[32768];
            long done = 0;
            int lastPct = -1, n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                done += n;
                final int pct = total > 0 ? (int) Math.min(99, done * 100 / total) : 0;
                if (pct != lastPct) {
                    lastPct = pct;
                    a.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            box.setMessage(a.getString(R.string.upd_downloading, pct));
                        }
                    });
                }
            }
            session.fsync(out);
            out.close();
            in.close();

            a.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    box.setMessage(a.getString(R.string.upd_installing));
                }
            });
            Intent result = new Intent(a, InstallReceiver.class).setAction("app.mylauncher.gtv.INSTALL_RESULT");
            int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
            PendingIntent pend = PendingIntent.getBroadcast(a, id, result, flags);
            session.commit(pend.getIntentSender());
            session.close();
            session = null;
            a.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    box.dismiss();
                }
            });
            return true;
        } catch (java.io.IOException | RuntimeException e) {
            try {
                if (session != null) session.abandon();
            } catch (RuntimeException | java.io.IOException ignored) {
                // Nothing more to clean.
            }
            return false;
        } finally {
            if (h != null) h.disconnect();
        }
    }
}
