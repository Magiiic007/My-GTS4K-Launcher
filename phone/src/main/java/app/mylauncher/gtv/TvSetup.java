package app.mylauncher.gtv;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.util.concurrent.TimeUnit;

import io.github.muntashirakon.adb.AdbStream;
import io.github.muntashirakon.adb.android.AdbMdns;

/**
 * Installs the TV launcher from the phone and makes it the home screen, through the TV's wireless debugging.
 * Everything here blocks, so it is called from a worker thread. Every step that changes the TV is checked, and if
 * the launcher does not come up as the home screen, the original home screen is put back.
 */
final class TvSetup {
    private TvSetup() {
    }

    static final String PKG = "app.mylauncher.gtv";
    private static final String APK_URL = "https://github.com/Magiiic007/My-GTS4K-Launcher/releases/download/latest/my-launcher.apk";
    private static final String STOCK_X = "com.google.android.apps.tv.launcherx";
    private static final String STOCK_T = "com.google.android.tvlauncher";
    private static final String SETUP = "com.google.android.tungsten.setupwraith";
    /** Created on the TV when the new home screen has been checked; until then a timer there undoes everything. */
    private static final String OK = "/data/local/tmp/salah_home_ok";

    interface Say {
        void say(String s);
    }

    // ------------------------------------------------------------ finding the TV on the Wi-Fi
    static volatile String pairHost, connHost;
    static volatile int pairPort, connPort;
    private static AdbMdns mdnsPair, mdnsConn;

    static synchronized void startDiscovery(Context c, final Runnable changed) {
        stopDiscovery();
        Context a = c.getApplicationContext();
        pairHost = connHost = null;
        pairPort = connPort = 0;
        try {
            mdnsPair = new AdbMdns(a, AdbMdns.SERVICE_TYPE_TLS_PAIRING, new AdbMdns.OnAdbDaemonDiscoveredListener() {
                @Override
                public void onPortChanged(InetAddress host, int port) {
                    if (host == null || port <= 0) return;
                    pairHost = host.getHostAddress();
                    pairPort = port;
                    changed.run();
                }
            });
            mdnsPair.start();
            mdnsConn = new AdbMdns(a, AdbMdns.SERVICE_TYPE_TLS_CONNECT, new AdbMdns.OnAdbDaemonDiscoveredListener() {
                @Override
                public void onPortChanged(InetAddress host, int port) {
                    if (host == null || port <= 0) return;
                    connHost = host.getHostAddress();
                    connPort = port;
                    changed.run();
                }
            });
            mdnsConn.start();
        } catch (RuntimeException ignored) {
            // Searching is a convenience: the address can always be typed.
        }
    }

    static synchronized void stopDiscovery() {
        try {
            if (mdnsPair != null) mdnsPair.stop();
            if (mdnsConn != null) mdnsConn.stop();
        } catch (RuntimeException ignored) {
            // Already stopped.
        }
        mdnsPair = null;
        mdnsConn = null;
    }

    // ------------------------------------------------------------ pairing and connecting
    static boolean connected(Context c) {
        try {
            return TvAdb.get(c).isConnected();
        } catch (Exception e) {
            return false;
        }
    }

    static void pair(Context c, String host, int port, String code) throws Exception {
        TvAdb.get(c).pair(host, port, code.trim());
    }

    static void connect(Context c, String host, int port) throws Exception {
        TvAdb m = TvAdb.get(c);
        if (m.isConnected()) m.disconnect();
        m.setTimeout(30, TimeUnit.SECONDS);
        if (!m.connect(host, port)) throw new java.io.IOException("لم يتم الاتصال");
    }

    static void disconnect(Context c) {
        try {
            TvAdb.get(c).disconnect();
        } catch (Exception ignored) {
            // Nothing to close.
        }
    }

    /** Runs one command on the TV and returns what it printed. */
    static String sh(Context c, String cmd) throws Exception {
        AdbStream s = TvAdb.get(c).openStream("shell:" + cmd);
        try {
            return readAll(s.openInputStream()).replace("\r", "");
        } finally {
            try {
                s.close();
            } catch (Exception ignored) {
                // Already closed by the TV.
            }
        }
    }

    private static String readAll(InputStream in) throws java.io.IOException {
        StringBuilder b = new StringBuilder();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) b.append(new String(buf, 0, n, "UTF-8"));
        return b.toString();
    }

    static String info(Context c) throws Exception {
        String model = sh(c, "getprop ro.product.model").trim();
        String ver = sh(c, "getprop ro.build.version.release").trim();
        return model + " · Android " + ver;
    }

    private static boolean has(String packages, String p) {
        return ("\n" + packages + "\n").contains("\npackage:" + p + "\n");
    }

    // ------------------------------------------------------------ installing
    private static File download(Context c, Say say) throws Exception {
        File f = new File(c.getCacheDir(), "tv-launcher.apk");
        HttpURLConnection h = (HttpURLConnection) new URL(APK_URL).openConnection();
        try {
            h.setConnectTimeout(15000);
            h.setReadTimeout(30000);
            h.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) Salah-Khair");
            int code = h.getResponseCode();
            if (code != 200) throw new java.io.IOException("التحميل فشل (HTTP " + code + ")");
            long total = h.getContentLengthLong();
            long got = 0;
            int lastPct = -1;
            try (InputStream in = h.getInputStream(); OutputStream out = new FileOutputStream(f)) {
                byte[] buf = new byte[32768];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    got += n;
                    if (total > 0) {
                        int pct = (int) (got * 100 / total) / 20 * 20;
                        if (pct != lastPct) {
                            lastPct = pct;
                            say.say("تحميل اللانشر… " + pct + "%");
                        }
                    }
                }
            }
        } finally {
            h.disconnect();
        }
        if (f.length() < 100_000) throw new java.io.IOException("الملف المحمّل غير صالح");
        return f;
    }

    static void install(Context c, Say say) throws Exception {
        File apk = download(c, say);
        say.say("تثبيت اللانشر على التلفزيون…");
        AdbStream s = TvAdb.get(c).openStream("exec:cmd package install -r -S " + apk.length());
        String result;
        try {
            OutputStream out = s.openOutputStream();
            try (java.io.FileInputStream in = new java.io.FileInputStream(apk)) {
                byte[] buf = new byte[32768];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            }
            out.flush();
            result = readAll(s.openInputStream());
        } finally {
            try {
                s.close();
            } catch (Exception ignored) {
                // Already closed by the TV.
            }
        }
        // no-op if the file could not be removed
        //noinspection ResultOfMethodCallIgnored
        apk.delete();
        if (!result.contains("Success")) throw new java.io.IOException("التلفزيون رفض التثبيت: " + result.trim());
        // The permissions the launcher's own screens ask for, so they need no clicking on the TV (best effort).
        sh(c, "pm grant " + PKG + " android.permission.POST_NOTIFICATIONS; appops set " + PKG
            + " SYSTEM_ALERT_WINDOW allow; appops set " + PKG + " MANAGE_EXTERNAL_STORAGE allow; appops set " + PKG
            + " GET_USAGE_STATS allow");
        String v = sh(c, "dumpsys package " + PKG + " | grep -m1 versionName").trim();
        say.say("تم التثبيت" + (v.isEmpty() ? "" : " (" + v + ")"));
    }

    // ------------------------------------------------------------ the home screen
    private static SharedPreferences p(Context c) {
        return c.getSharedPreferences("magiic", Context.MODE_PRIVATE);
    }

    static boolean homeSet(Context c) {
        return p(c).getBoolean("tv_home", false);
    }

    private static boolean isOurHome(Context c) throws Exception {
        String r = sh(c, "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -n 1").trim();
        return r.startsWith(PKG + "/");
    }

    private static boolean onScreen(Context c) throws Exception {
        String r = sh(c, "dumpsys activity activities | grep -m1 -E 'topResumedActivity|mResumedActivity'");
        return r.contains(" " + PKG + "/");
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    /** Makes the launcher the home screen; if that does not work, puts the original one back. */
    static void setHome(Context c, Say say) throws Exception {
        String pk = sh(c, "pm list packages -e");
        if (!has(pk, PKG)) throw new java.io.IOException("اللانشر غير مثبّت على التلفزيون بعد. ثبّته أولاً.");
        String stockAct;
        String[] off;
        if (has(pk, STOCK_X)) {
            stockAct = STOCK_X + "/" + STOCK_X + ".home.HomeActivity";
            off = has(pk, SETUP) ? new String[] {SETUP, STOCK_X} : new String[] {STOCK_X};
        } else if (has(pk, STOCK_T)) {
            stockAct = STOCK_T + "/.MainActivity";
            off = new String[] {STOCK_T};
        } else {
            stockAct = null;
            off = new String[0];
        }
        StringBuilder list = new StringBuilder();
        for (String o : off) list.append(o).append(' ');
        if (off.length > 0) {
            say.say("الشاشة الأصلية على التلفزيون: " + off[off.length - 1]);
            p(c).edit().putString("tv_disabled", list.toString().trim()).apply();
            // A timer on the TV itself: if nothing confirms the new home screen in 3 minutes, everything is undone,
            // even if the phone is gone by then.
            sh(c, "rm -f " + OK);
            sh(c, "nohup sh -c 'i=0; while [ $i -lt 180 ]; do [ -f " + OK + " ] && exit 0; sleep 1; i=$((i+1)); done; for p in "
                + list + "; do pm enable $p; done; cmd package set-home-activity --user 0 " + stockAct
                + "; input keyevent KEYCODE_HOME' </dev/null >/dev/null 2>&1 &");
        }
        say.say("تعيين اللانشر كشاشة رئيسية…");
        sh(c, "cmd role add-role-holder --user 0 android.app.role.HOME " + PKG + " >/dev/null 2>&1");
        sh(c, "cmd package set-home-activity --user 0 " + PKG + "/.MainActivity");
        for (String o : off) {
            say.say("تعطيل " + o);
            sh(c, "pm disable-user --user 0 " + o);
        }
        boolean ok = false;
        for (int i = 0; i < 3 && !ok; i++) {
            sh(c, "input keyevent KEYCODE_HOME");
            sleep(2500);
            ok = isOurHome(c) && onScreen(c);
        }
        if (ok) {
            sh(c, "touch " + OK);
            p(c).edit().putBoolean("tv_home", true).apply();
            say.say("تم: اللانشر هو الشاشة الرئيسية الآن.");
        } else {
            say.say("لم يظهر اللانشر كشاشة رئيسية، سأعيد الشاشة الأصلية…");
            restore(c, say);
            throw new java.io.IOException("لم يتم التعيين، والشاشة الأصلية رجعت كما كانت");
        }
    }

    /** Back to the TV's own home screen. */
    static void restore(Context c, Say say) throws Exception {
        String pk = sh(c, "pm list packages");
        String saved = p(c).getString("tv_disabled", "");
        StringBuilder all = new StringBuilder(STOCK_X + " " + SETUP + " " + STOCK_T);
        if (!saved.isEmpty()) all.append(' ').append(saved);
        for (String x : all.toString().split(" ")) {
            if (has(pk, x)) sh(c, "pm enable " + x);
        }
        String stockAct = null;
        if (has(pk, STOCK_X)) stockAct = STOCK_X + "/" + STOCK_X + ".home.HomeActivity";
        else if (has(pk, STOCK_T)) stockAct = STOCK_T + "/.MainActivity";
        sh(c, "touch " + OK);
        if (stockAct != null) {
            sh(c, "cmd package set-home-activity --user 0 " + stockAct);
        }
        sh(c, "input keyevent KEYCODE_HOME");
        p(c).edit().putBoolean("tv_home", false).apply();
        say.say(stockAct != null ? "رجعت الشاشة الأصلية للتلفزيون." : "لم أجد الشاشة الأصلية على هذا التلفزيون.");
    }
}
