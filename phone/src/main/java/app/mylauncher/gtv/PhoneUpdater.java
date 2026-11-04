package app.mylauncher.gtv;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Updates the phone app from the GitHub release "phone-latest" (its notes carry the build's versionCode). */
final class PhoneUpdater {
    private PhoneUpdater() {}

    private static final String REPO = "Magiiic007/My-GTS4K-Launcher";
    private static final String API = "https://api.github.com/repos/" + REPO + "/releases/tags/phone-latest";
    private static final String APK = "https://github.com/" + REPO + "/releases/download/phone-latest/my-phone.apk";

    /** Why the last check or download failed (shown to the user, so a failure can be understood). */
    private static volatile String err = "";

    private static String why() {
        return err.isEmpty() ? "" : "\n(" + err + ")";
    }

    static long installed(Context c) {
        try {
            return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).getLongVersionCode();
        } catch (Exception e) {
            return 0L;
        }
    }

    static String installedName(Context c) {
        try {
            return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "?";
        }
    }

    /** Looks for a newer build and offers it. */
    static void check(final Activity a) {
        Toast.makeText(a, R.string.upd_checking, Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                final long latest = fetch();
                a.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (latest < 0) {
                            Toast.makeText(a, a.getString(R.string.upd_failed) + why(), Toast.LENGTH_LONG).show();
                        } else if (latest <= installed(a)) {
                            Toast.makeText(a, a.getString(R.string.upd_uptodate, installedName(a)),
                                Toast.LENGTH_LONG).show();
                        } else {
                            offer(a, latest);
                        }
                    }
                });
            }
        }).start();
    }

    private static long fetch() {
        err = "";
        long v = fetchApi();
        if (v >= 0) return v;
        // The API allows few requests per address (a shared mobile address runs out fast); the public page has no such limit.
        long w = fetchPage();
        return w >= 0 ? w : -1;
    }

    private static String read(String url, String accept) throws java.io.IOException {
        HttpURLConnection h = (HttpURLConnection) new URL(url).openConnection();
        try {
            h.setConnectTimeout(10000);
            h.setReadTimeout(15000);
            if (accept != null) h.setRequestProperty("Accept", accept);
            h.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) Magiic-Phone-Updater");
            int code = h.getResponseCode();
            if (code != 200) {
                err = "HTTP " + code;
                return null;
            }
            StringBuilder b = new StringBuilder();
            InputStream in = h.getInputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) b.append(new String(buf, 0, n, "UTF-8"));
            in.close();
            return b.toString();
        } finally {
            h.disconnect();
        }
    }

    private static long fetchApi() {
        try {
            String body = read(API, "application/vnd.github+json");
            if (body == null) return -1;
            Matcher m = Pattern.compile("versionCode=(\\d+)").matcher(new JSONObject(body).optString("body", ""));
            return m.find() ? Long.parseLong(m.group(1)) : -1;
        } catch (Exception e) {
            err = e.getClass().getSimpleName();
            return -1;
        }
    }

    private static long fetchPage() {
        try {
            String body = read("https://github.com/" + REPO + "/releases/tag/phone-latest", null);
            if (body == null) return -1;
            Matcher m = Pattern.compile("versionCode=(\\d+)").matcher(body);
            if (m.find()) {
                err = "";
                return Long.parseLong(m.group(1));
            }
            err = "no version in page";
            return -1;
        } catch (Exception e) {
            err = e.getClass().getSimpleName();
            return -1;
        }
    }

    private static void offer(final Activity a, long latest) {
        new Pop(a).title("تحديث التطبيق")
            .message(a.getString(R.string.upd_found, (int) latest, (int) installed(a)))
            .ok(a.getString(R.string.upd_btn_install), new Runnable() {
                @Override
                public void run() {
                    start(a);
                }
            }).cancel("إغلاق").show();
    }

    private static void start(final Activity a) {
        if (!a.getPackageManager().canRequestPackageInstalls()) {
            Toast.makeText(a, "اسمح للتطبيق بتثبيت التطبيقات ثم ارجع واضغط تحديث مرة أخرى", Toast.LENGTH_LONG).show();
            a.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:" + a.getPackageName())));
            return;
        }
        Toast.makeText(a, "جارٍ التحميل...", Toast.LENGTH_LONG).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                final boolean ok = download(a);
                if (!ok) {
                    a.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(a, a.getString(R.string.upd_failed) + why(), Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        }).start();
    }

    private static boolean download(Activity a) {
        err = "";
        HttpURLConnection h = null;
        PackageInstaller.Session session = null;
        PackageInstaller pi = a.getPackageManager().getPackageInstaller();
        try {
            h = (HttpURLConnection) new URL(APK).openConnection();
            h.setConnectTimeout(15000);
            h.setReadTimeout(30000);
            h.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) Magiic-Phone-Updater");
            int code = h.getResponseCode();
            if (code != 200) {
                err = "HTTP " + code;
                return false;
            }
            long total = h.getContentLengthLong();
            PackageInstaller.SessionParams sp =
                new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            sp.setAppPackageName(a.getPackageName());
            if (Build.VERSION.SDK_INT >= 31) {
                sp.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);
            }
            int id = pi.createSession(sp);
            session = pi.openSession(id);
            OutputStream out = session.openWrite("update", 0, total > 0 ? total : -1);
            InputStream in = h.getInputStream();
            byte[] buf = new byte[32768];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            session.fsync(out);
            out.close();
            in.close();
            Intent result = new Intent(a, InstallReceiver.class).setAction("app.mylauncher.gtv.INSTALL_RESULT");
            int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
            session.commit(PendingIntent.getBroadcast(a, id, result, flags).getIntentSender());
            session.close();
            return true;
        } catch (java.io.IOException | RuntimeException e) {
            err = e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage());
            try {
                if (session != null) session.abandon();
            } catch (RuntimeException ignored) {
                // Nothing more to clean.
            }
            return false;
        } finally {
            if (h != null) h.disconnect();
        }
    }
}
