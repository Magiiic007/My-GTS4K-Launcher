package app.mylauncher.gtv;

import android.content.Context;

/**
 * The Google Play build has no TV setup over wireless debugging (it is not allowed in the store and the library is left out).
 * The screens that use it are hidden in this build; these empty versions only keep the shared code compiling.
 */
final class TvSetup {
    static final String PKG = "app.mylauncher.gtv";
    static volatile String pairHost, connHost;
    static volatile int pairPort, connPort;

    interface Say {
        void say(String s);
    }

    private TvSetup() {
    }

    static boolean pairingRequired(Throwable e) {
        return false;
    }

    static void startDiscovery(Context c, Runnable changed) {
    }

    static void stopDiscovery() {
    }

    static boolean connected(Context c) {
        return false;
    }

    static void pair(Context c, String host, int port, String code) throws Exception {
        throw new UnsupportedOperationException();
    }

    static void connect(Context c, String host, int port) throws Exception {
        throw new UnsupportedOperationException();
    }

    static void disconnect(Context c) {
    }

    static String info(Context c) throws Exception {
        return "";
    }

    static void install(Context c, Say say) throws Exception {
    }

    static void enableKeys(Context c, Say say) {
    }

    static boolean homeSet(Context c) {
        return false;
    }

    static void keyProbe(Context c, Say say, String label) throws Exception {
    }

    static void setHome(Context c, Say say) throws Exception {
    }

    static void restore(Context c, Say say) throws Exception {
    }
}
