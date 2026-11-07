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
    /** The guard that stays on the TV: if the launcher is ever removed, it brings the original home screen back. */
    private static final String GUARD_PID = "/data/local/tmp/salah_guard.pid";
    private static final String GUARD_STOP = "/data/local/tmp/salah_guard.stop";

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
        // After a restart of the TV the guard is gone: put it back whenever the phone connects.
        try {
            if (homeSet(c)) armGuard(c);
        } catch (Exception ignored) {
            // Best effort.
        }
    }

    static void disconnect(Context c) {
        up = false;
        try {
            TvAdb.get(c).disconnect();
        } catch (Exception ignored) {
            // Nothing to close.
        }
    }

    private static final java.util.concurrent.ExecutorService IO = java.util.concurrent.Executors.newCachedThreadPool();

    /** Runs one command on the TV and returns what it printed; gives up after 25 seconds instead of waiting forever. */
    static String sh(Context c, String cmd) throws Exception {
        return sh(c, cmd, 25);
    }

    static String sh(final Context c, final String cmd, int secs) throws Exception {
        java.util.concurrent.Future<String> f = IO.submit(new java.util.concurrent.Callable<String>() {
            @Override
            public String call() throws Exception {
                return shRaw(c, cmd);
            }
        });
        try {
            return f.get(secs, TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            f.cancel(true);
            throw new java.io.IOException("انتهت مهلة أمر على التلفزيون: " + (cmd.length() > 60 ? cmd.substring(0, 60) + "…" : cmd));
        } catch (java.util.concurrent.ExecutionException e) {
            Throwable t = e.getCause();
            if (t instanceof Exception) throw (Exception) t;
            throw e;
        }
    }

    private static String shRaw(Context c, String cmd) throws Exception {
        // The ADB library sizes its packet by characters, not bytes: a non-ASCII command would overflow it.
        for (int i = 0; i < cmd.length(); i++) {
            if (cmd.charAt(i) > 127) throw new java.io.IOException("أمر غير ASCII: " + cmd);
        }
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
        // while the launcher is replaced it is briefly missing: the guard must not take that for a removal
        stopGuard(c);
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
        enableKeys(c, say);
        if (homeSet(c)) armGuard(c);
    }

    /** Whether the remote-buttons service is installed, enabled and actually running on the TV (for the log). */
    static String keysStatus(Context c) {
        try {
            String v = sh(c, "dumpsys package " + PKG + " | grep -m1 versionName").trim();
            String en = sh(c, "settings get secure enabled_accessibility_services").trim();
            String on = sh(c, "settings get secure accessibility_enabled").trim();
            String run = sh(c, "dumpsys accessibility > /data/local/tmp/salah_acc.txt 2>&1; grep -c RemoteKeys /data/local/tmp/salah_acc.txt").trim();
            return "اللانشر: " + v + " | الخدمات المفعّلة: " + cut(en) + " | التفعيل العام: " + on + " | ظهور الخدمة في النظام: " + run;
        } catch (Exception e) {
            return "تعذّر فحص الخدمة: " + e.getMessage();
        }
    }

    private static final String KEYS_REPORT = "/data/local/tmp/salah_keys.txt";

    /**
     * Switches on the launcher's remote-buttons service. The work runs on the TV itself and writes a report file,
     * because the TV may stop answering for a few seconds while the system starts the service. Best effort.
     */
    static void enableKeys(Context c, Say say) {
        try {
            String old = sh(c, "cat " + KEYS_REPORT + " 2>/dev/null").trim();
            if (!old.isEmpty()) say.say("تقرير المحاولة السابقة:\n" + old);
            String svc = PKG + "/" + PKG + ".RemoteKeys";
            String script = "rm -f " + KEYS_REPORT + "; "
                + "appops set " + PKG + " ACCESS_RESTRICTED_SETTINGS allow; "
                + "cur=$(settings get secure enabled_accessibility_services); "
                + "case \"$cur\" in *RemoteKeys*) ;; null|'') settings put secure enabled_accessibility_services " + svc + ";; "
                + "*) settings put secure enabled_accessibility_services \"$cur:" + svc + "\";; esac; "
                + "settings put secure accessibility_enabled 1; sleep 5; "
                + "{ echo \"enabled=$(settings get secure enabled_accessibility_services)\"; "
                + "echo \"general=$(settings get secure accessibility_enabled)\"; "
                + "dumpsys accessibility > /data/local/tmp/salah_acc.txt 2>&1; "
                + "echo \"in_system=$(grep -c RemoteKeys /data/local/tmp/salah_acc.txt)\"; "
                + "logcat -d -b crash,main,system -t 600 | grep -E 'RemoteKeys|FATAL|AndroidRuntime' | tail -8 | cut -c1-200; "
                + "echo done; } > " + KEYS_REPORT + " 2>&1";
            sh(c, "nohup sh -c '" + script.replace("'", "'\\''") + "' >/dev/null 2>&1 &");
            say.say("بدأ التفعيل على التلفزيون… انتظر ثوانٍ ثم اضغط الزر مرة أخرى لقراءة التقرير.");
            sleep(8000);
            String rep = "";
            try {
                rep = sh(c, "cat " + KEYS_REPORT + " 2>/dev/null").trim();
            } catch (Exception ignored) {
                // The TV may still be busy; the next press reads the report.
            }
            if (!rep.isEmpty()) say.say("تقرير التفعيل:\n" + rep);
        } catch (Exception e) {
            say.say("تعذّر تفعيل خدمة أزرار الريموت: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------ the home screen
    private static SharedPreferences p(Context c) {
        return c.getSharedPreferences("magiic", Context.MODE_PRIVATE);
    }

    static boolean homeSet(Context c) {
        return p(c).getBoolean("tv_home", false);
    }

    /** What the TV says is its home screen, and what is on the screen now. */
    private static String[] homeInfo(Context c) throws Exception {
        String r = sh(c, "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -n 1").trim();
        String t = sh(c, "dumpsys activity activities | grep -m1 -E 'topResumedActivity|mResumedActivity'").trim();
        return new String[] {r, t};
    }

    private static String cut(String s) {
        s = s.replace('\n', ' ');
        return s.length() > 90 ? s.substring(0, 90) + "…" : s;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Listens on the TV for 12 seconds while the user presses one remote button, and reports what the press tried to
     * start (from the system log) and the raw key names (from the input devices).
     */
    static void keyProbe(Context c, Say say, String label) throws Exception {
        say.say("الآن اضغط زر «" + label + "» على ريموت التلفزيون (مرة أو مرتين). الاختبار 12 ثانية…");
        String ge = "/data/local/tmp/salah_ge.txt";
        String out = sh(c, "rm -f " + ge + "; logcat -c; (timeout 12 getevent -l > " + ge + " 2>&1 &); "
            + "timeout 12 logcat -v brief ActivityTaskManager:I *:S | grep -E 'START|cmp=|Force|finish' | cut -c1-230 | head -20; "
            + "echo '- SVC:'; logcat -d -s SalahKeys:I | tail -6; "
            + "echo '- RAW:'; sleep 1; grep -E 'KEY_|BTN_' " + ge + " | grep DOWN | cut -c1-110 | head -8", 35);
        out = out.trim();
        boolean started = out.contains("START") || out.contains("cmp=");
        boolean raw = out.contains("KEY_") || out.contains("BTN_");
        String verdict = started ? "النظام حاول يفتح شاشة (التفاصيل تحت)."
            : raw ? "وصل الزر للتلفزيون لكن لا شيء بدأ."
            : "لم يصل أي شيء للتلفزيون (أو لم تضغط الزر).";
        say.say("نتيجة «" + label + "»: " + verdict + "\n" + out);
    }

    /** Stops the guard on the TV (before a deliberate change). */
    private static void stopGuard(Context c) {
        try {
            sh(c, "touch " + GUARD_STOP + "; [ -f " + GUARD_PID + " ] && kill $(cat " + GUARD_PID + "); rm -f " + GUARD_PID);
        } catch (Exception ignored) {
            // Not reachable: nothing to stop.
        }
    }

    /**
     * Starts the guard on the TV: every 15 seconds it checks that the launcher is still installed; when it is gone
     * (two checks in a row, so an update does not count) it enables the original home screen again. It lives until the
     * TV restarts; the phone puts it back at its next connection.
     */
    static void armGuard(Context c) throws Exception {
        String list = p(c).getString("tv_disabled", "");
        String stock = p(c).getString("tv_stock", "");
        if (list.isEmpty() || stock.isEmpty()) return;
        stopGuard(c);
        sh(c, "rm -f " + GUARD_STOP);
        sh(c, "nohup sh -c 'echo $$ > " + GUARD_PID + "; m=0; while true; do [ -f " + GUARD_STOP + " ] && exit 0; "
            + "if pm path " + PKG + " | grep -q package:; then m=0; else m=$((m+1)); fi; "
            + "if [ $m -ge 2 ]; then for p in " + list + "; do pm enable $p; done; cmd package set-home-activity --user 0 "
            + stock + "; input keyevent KEYCODE_HOME; rm -f " + GUARD_PID + "; exit 0; fi; sleep 15; done' "
            + "</dev/null >/dev/null 2>&1 &");
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
            // Google's emergency home screen (setupwraith) takes over when the launcher is off, so it goes too.
            off = new String[] {SETUP, STOCK_X};
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
            p(c).edit().putString("tv_disabled", list.toString().trim()).putString("tv_stock", stockAct).apply();
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
            for (String o : off) {
                say.say("تعطيل " + o);
                sh(c, "pm disable-user --user 0 " + o + " 2>&1");
            }
            // after the others are off, so nothing resets the choice
            sh(c, "cmd package set-home-activity --user 0 " + PKG + "/.MainActivity");
            boolean ok = false;
            for (int i = 0; i < 3 && !ok; i++) {
                sh(c, "input keyevent KEYCODE_HOME");
                sleep(2500);
                String[] hi = homeInfo(c);
                ok = hi[0].startsWith(PKG + "/") && hi[1].contains(" " + PKG + "/");
                say.say("فحص " + (i + 1) + ": الرئيسية = " + cut(hi[0]) + " | المعروض = " + cut(hi[1]));
            }
            if (ok) {
                sh(c, "touch " + OK);
                p(c).edit().putBoolean("tv_home", true).apply();
                armGuard(c);
                enableKeys(c, say);
                say.say("تم: اللانشر هو الشاشة الرئيسية الآن. وعلى التلفزيون حارس يُرجع الشاشة الأصلية تلقائياً لو حُذف اللانشر.");
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

    /**
     * Brings the voice assistant back to a normal state: our old remote-keys service is taken out of the accessibility list,
     * Google's own app is enabled, the microphone allowed and its data cleared. The result is listed for the person to see.
     */
    static void fixAssistant(Context c, Say say) throws Exception {
        String g = "com.google.android.katniss";
        String pk = sh(c, "pm list packages");
        say.say(has(pk, g) ? "تطبيق Google (المساعد) موجود على التلفزيون" : "تطبيق Google (المساعد) غير موجود على التلفزيون!");
        String off = sh(c, "pm list packages -d").trim();
        say.say("المعطّلة قبل الإصلاح:\n" + (off.isEmpty() ? "(لا شيء)" : off.replace("package:", "")));
        // Google's own parts that the voice button and the home screen lean on: if any of them was switched off, switch it on,
        // and show what the TV really answered.
        String[] parts = {"com.google.android.tvrecommendations", STOCK_X, STOCK_T, SETUP, g};
        for (String part : parts) {
            if (!off.contains(part)) continue;
            String r1 = sh(c, "pm enable --user 0 " + part + " 2>&1", 60).trim();
            String r2 = sh(c, "pm default-state --user 0 " + part + " 2>&1", 60).trim();
            say.say("تفعيل " + part + ":\n" + r1 + (r2.isEmpty() ? "" : "\n" + r2));
        }
        String after = sh(c, "pm list packages -d", 60).trim();
        say.say("المعطّلة بعد الإصلاح:\n" + (after.isEmpty() ? "(لا شيء)" : after.replace("package:", "")));
        String cur = sh(c, "settings get secure enabled_accessibility_services", 60).trim();
        say.say("خدمات إمكانية الوصول: " + (cur.isEmpty() ? "(لا شيء)" : cur));
        if (cur.contains("RemoteKeys")) {
            sh(c, "settings delete secure enabled_accessibility_services; settings put secure accessibility_enabled 0", 60);
            say.say("أُزيلت خدمة الأزرار القديمة");
        }
        if (has(pk, g)) {
            say.say("الميكروفون: " + sh(c, "pm grant " + g + " android.permission.RECORD_AUDIO 2>&1", 60).trim());
        }
        say.say("انتهى. أعد تشغيل التلفزيون من القائمة ثم جرّب زر المساعد.");
    }

    static void restore(Context c, Say say) throws Exception {
        stopGuard(c);
        String pk = sh(c, "pm list packages");
        String saved = p(c).getString("tv_disabled", "");
        StringBuilder all = new StringBuilder(STOCK_X + " " + SETUP + " " + STOCK_T + " com.google.android.tvrecommendations");
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
