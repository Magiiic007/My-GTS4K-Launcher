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
    // (The ADB library's own finder only reports the phone itself, so the TV is searched for here.)
    static volatile String pairHost, connHost;
    static volatile int pairPort, connPort;
    private static volatile String pairName, connName;
    private static android.net.nsd.NsdManager nsd;
    private static android.net.nsd.NsdManager.DiscoveryListener dlPair, dlConn;
    private static final java.util.ArrayDeque<Object[]> queue = new java.util.ArrayDeque<>();
    private static boolean resolving;
    private static Runnable onFound;

    private static String hostOf(android.net.nsd.NsdServiceInfo i) {
        if (android.os.Build.VERSION.SDK_INT >= 34) {
            for (InetAddress a : i.getHostAddresses()) {
                if (a instanceof java.net.Inet4Address) return a.getHostAddress();
            }
        }
        InetAddress h = i.getHost();
        if (h instanceof java.net.Inet4Address) return h.getHostAddress();
        return null; // an IPv6-only answer is not usable here
    }

    private static synchronized void next() {
        if (resolving || queue.isEmpty() || nsd == null) return;
        final Object[] job = queue.poll();
        final android.net.nsd.NsdServiceInfo info = (android.net.nsd.NsdServiceInfo) job[0];
        final boolean pairing = (Boolean) job[1];
        resolving = true;
        try {
            nsd.resolveService(info, new android.net.nsd.NsdManager.ResolveListener() {
                @Override
                public void onResolveFailed(android.net.nsd.NsdServiceInfo i, int code) {
                    synchronized (TvSetup.class) {
                        resolving = false;
                    }
                    next();
                }

                @Override
                public void onServiceResolved(android.net.nsd.NsdServiceInfo i) {
                    String h = hostOf(i);
                    if (h != null && i.getPort() > 0) {
                        if (pairing) {
                            pairHost = h;
                            pairPort = i.getPort();
                            pairName = i.getServiceName();
                        } else {
                            connHost = h;
                            connPort = i.getPort();
                            connName = i.getServiceName();
                        }
                        Runnable r = onFound;
                        if (r != null) r.run();
                    }
                    synchronized (TvSetup.class) {
                        resolving = false;
                    }
                    next();
                }
            });
        } catch (RuntimeException e) {
            resolving = false;
        }
    }

    private static android.net.nsd.NsdManager.DiscoveryListener listener(final boolean pairing) {
        return new android.net.nsd.NsdManager.DiscoveryListener() {
            @Override
            public void onStartDiscoveryFailed(String t, int code) {
            }

            @Override
            public void onStopDiscoveryFailed(String t, int code) {
            }

            @Override
            public void onDiscoveryStarted(String t) {
            }

            @Override
            public void onDiscoveryStopped(String t) {
            }

            @Override
            public void onServiceFound(android.net.nsd.NsdServiceInfo i) {
                synchronized (TvSetup.class) {
                    queue.add(new Object[] {i, pairing});
                }
                next();
            }

            @Override
            public void onServiceLost(android.net.nsd.NsdServiceInfo i) {
                String n = i.getServiceName();
                if (pairing && n != null && n.equals(pairName)) {
                    pairHost = null;
                    pairPort = 0;
                } else if (!pairing && n != null && n.equals(connName)) {
                    connHost = null;
                    connPort = 0;
                }
                Runnable r = onFound;
                if (r != null) r.run();
            }
        };
    }

    static synchronized void startDiscovery(Context c, Runnable changed) {
        stopDiscovery();
        pairHost = connHost = null;
        pairPort = connPort = 0;
        onFound = changed;
        try {
            nsd = (android.net.nsd.NsdManager) c.getApplicationContext().getSystemService(Context.NSD_SERVICE);
            dlPair = listener(true);
            dlConn = listener(false);
            nsd.discoverServices("_adb-tls-pairing._tcp", android.net.nsd.NsdManager.PROTOCOL_DNS_SD, dlPair);
            nsd.discoverServices("_adb-tls-connect._tcp", android.net.nsd.NsdManager.PROTOCOL_DNS_SD, dlConn);
        } catch (RuntimeException ignored) {
            // Searching is a convenience: the address can always be typed.
        }
    }

    static synchronized void stopDiscovery() {
        try {
            if (nsd != null && dlPair != null) nsd.stopServiceDiscovery(dlPair);
            if (nsd != null && dlConn != null) nsd.stopServiceDiscovery(dlConn);
        } catch (RuntimeException ignored) {
            // Already stopped.
        }
        dlPair = dlConn = null;
        nsd = null;
        onFound = null;
        queue.clear();
        resolving = false;
    }

    // ------------------------------------------------------------ pairing and connecting
    /** What the screen shows. A flag, because asking the connection itself waits while a pairing or connecting is running. */
    private static volatile boolean up;

    static boolean connected(Context c) {
        return up;
    }

    static void pair(Context c, String host, int port, String code) throws Exception {
        TvAdb.get(c).pair(host, port, code.trim());
    }

    static void connect(Context c, String host, int port) throws Exception {
        TvAdb m = TvAdb.get(c);
        if (m.isConnected()) m.disconnect();
        m.setTimeout(30, TimeUnit.SECONDS);
        up = false;
        if (!m.connect(host, port)) throw new java.io.IOException("لم يتم الاتصال");
        up = true;
    }

    static void disconnect(Context c) {
        up = false;
        try {
            TvAdb.get(c).disconnect();
        } catch (Exception ignored) {
            // Nothing to close.
        }
    }

    /** Runs one command on the TV and returns what it printed. */
    static String sh(Context c, String cmd) throws Exception {
        AdbStream s;
        try {
            s = TvAdb.get(c).openStream("shell:" + cmd);
        } catch (Exception e) {
            up = false; // the link is gone: the screen goes back to "connect"
            throw e;
        }
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
        java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        try {
            int n;
            while ((n = in.read(buf)) >= 0) {
                if (n > 0) b.write(buf, 0, n);
            }
        } catch (java.io.IOException e) {
            // The TV closing the stream is the normal end of a command, also when it printed nothing.
            if (!"Stream closed.".equals(e.getMessage())) throw e;
        }
        return b.toString("UTF-8");
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
        String where = sh(c, "pm path " + PKG).trim();
        if (!where.contains("package:") && !has(pk, PKG)) {
            String got = pk.length() > 80 ? pk.substring(0, 80) : pk;
            throw new java.io.IOException("اللانشر غير مثبّت على التلفزيون بعد. ثبّته أولاً. (رد التلفزيون: "
                + (where.isEmpty() ? "فارغ" : where) + " | " + pk.length() + " حرف: " + got.replace('\n', ' ') + ")");
        }
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
        boolean restored = false;
        try {
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
                restored = true;
                throw new java.io.IOException("لم يتم التعيين، والشاشة الأصلية رجعت كما كانت");
            }
        } catch (Exception e) {
            // Something broke half-way (for example the Wi-Fi dropped): try to put the original back right now; if
            // that cannot be reached either, the timer on the TV does it.
            if (!restored) {
                try {
                    restore(c, say);
                } catch (Exception ignored) {
                    // The timer on the TV is the fallback.
                }
            }
            throw e;
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
