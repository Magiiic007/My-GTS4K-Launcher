package app.mylauncher.gtv;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;

/** Brings Magiic TV forward whenever the device's own home screen appears (see HomeOverride). */
public class HomeOverrideService extends AccessibilityService {
    private long last;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        if (!HomeOverride.on(this) || HomeOverride.paused(this)) return;
        CharSequence pkg = event.getPackageName();
        CharSequence cls = event.getClassName();
        if (pkg == null || !HomeOverride.isStockHome(this, pkg.toString(), cls == null ? null : cls.toString())) return;
        long now = SystemClock.uptimeMillis();
        if (now - last < 1500L) return;
        last = now;
        try {
            startActivity(new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        } catch (RuntimeException e) {
            // Not allowed right now; the next time the home screen appears it tries again.
        }
    }

    @Override
    public void onInterrupt() {
    }
}
