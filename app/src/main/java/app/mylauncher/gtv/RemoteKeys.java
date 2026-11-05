package app.mylauncher.gtv;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Gives the remote's special buttons a job while this app is the home screen. Google's own home screen used to
 * react to them; with it switched off nothing did. The service only watches key presses (nothing on screen is read)
 * and is switched on by the phone app's TV setup. Scan codes: 240 = YouTube button, 423 = star (long press picks an app, short press opens it).
 */
public class RemoteKeys extends AccessibilityService {
    private static final String TAG = "SalahKeys";
    private static final String[] YT = {"com.google.android.youtube.tv", "com.google.android.youtube.tvunplugged"};

    @Override
    protected void onServiceConnected() {
        AccessibilityServiceInfo i = getServiceInfo();
        if (i == null) i = new AccessibilityServiceInfo();
        i.flags |= AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
        setServiceInfo(i);
    }

    @Override
    protected boolean onKeyEvent(KeyEvent e) {
        int scan = e.getScanCode();
        boolean special = scan == 240 || scan == 423;
        if (e.getKeyCode() > KeyEvent.KEYCODE_BUTTON_MODE || special) {
            Log.i(TAG, "key=" + e.getKeyCode() + " scan=" + scan + " action=" + e.getAction());
        }
        if (!special) return false;
        if (scan == 240) {
            if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) openYoutube();
            return true;
        }
        // Star: a long press picks the app, a short press opens the picked one (like the original home screen did).
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            if (e.getRepeatCount() == 0) longDone = false;
            else if (!longDone && e.getRepeatCount() >= 2) {
                longDone = true;
                open(new Intent(this, PickFavorite.class));
            }
        } else if (e.getAction() == KeyEvent.ACTION_UP && !longDone) {
            openFavorite();
        }
        return true;
    }

    private boolean longDone;

    private void openFavorite() {
        String p = getSharedPreferences(PickFavorite.PREFS, MODE_PRIVATE).getString(PickFavorite.KEY, null);
        Intent in = null;
        if (p != null) {
            in = getPackageManager().getLeanbackLaunchIntentForPackage(p);
            if (in == null) in = getPackageManager().getLaunchIntentForPackage(p);
        }
        open(in != null ? in : new Intent(this, PickFavorite.class));
    }

    private void openYoutube() {
        PackageManager pm = getPackageManager();
        for (String p : YT) {
            Intent in = pm.getLeanbackLaunchIntentForPackage(p);
            if (in == null) in = pm.getLaunchIntentForPackage(p);
            if (in != null) {
                open(in);
                return;
            }
        }
    }

    private void open(Intent in) {
        try {
            in.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(in);
        } catch (Exception ex) {
            Log.w(TAG, "open failed: " + ex);
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }
}
