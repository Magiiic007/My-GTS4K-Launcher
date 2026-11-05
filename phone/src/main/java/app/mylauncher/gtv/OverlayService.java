package app.mylauncher.gtv;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Shows a short dhikr card over whatever app is open, every few minutes. It needs the
 * "display over other apps" permission. The adhan never uses it (it plays quietly in the background).
 */
public class OverlayService extends Service {
    private static final String CHANNEL = "overlay";
    private static final long SHOWN_MS = 5000L;

    private final Handler h = new Handler(Looper.getMainLooper());
    private WindowManager wm;
    private View card;
    private SharedPreferences sp;

    private final Runnable stop = new Runnable() {
        @Override
        public void run() {
            hideCard();
            stopForeground(true);
            stopSelf();
        }
    };

    static boolean wanted(Context c) {
        return c.getSharedPreferences("magiic", MODE_PRIVATE).getBoolean("ov_on", false);
    }

    private static android.app.PendingIntent tick(Context c, int flags) {
        return android.app.PendingIntent.getBroadcast(c, 77, new Intent(c, OverlayReceiver.class),
            flags | android.app.PendingIntent.FLAG_IMMUTABLE);
    }

    /** Keeps the next card's alarm if there is one; sets it when it is missing; clears it when the setting is off. */
    static void sync(Context c) {
        schedule(c, false);
    }

    /** Same, but counts the time again from now (after the setting or the frequency changed). */
    static void syncNow(Context c) {
        schedule(c, true);
    }

    private static void schedule(Context c, boolean reset) {
        try {
            android.app.AlarmManager am = c.getSystemService(android.app.AlarmManager.class);
            if (!(wanted(c) && Settings.canDrawOverlays(c) && !Wc.on(c))) {
                android.app.PendingIntent old = tick(c, android.app.PendingIntent.FLAG_NO_CREATE);
                if (old != null) {
                    am.cancel(old);
                    old.cancel();
                }
                c.stopService(new Intent(c, OverlayService.class));
                return;
            }
            if (!reset && tick(c, android.app.PendingIntent.FLAG_NO_CREATE) != null) return;
            long ms = Math.max(1, c.getSharedPreferences("magiic", MODE_PRIVATE).getInt("ov_freq", 30)) * 60000L;
            android.app.PendingIntent pi = tick(c, android.app.PendingIntent.FLAG_UPDATE_CURRENT);
            try {
                am.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + ms, pi);
            } catch (SecurityException e) {
                am.setAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + ms, pi);
            }
        } catch (RuntimeException ignored) {
            // the next time the app opens it is tried again
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /** Runs only while a card is on the screen: the notice appears with the card and goes with it. */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        sp = getSharedPreferences("magiic", MODE_PRIVATE);
        wm = getSystemService(WindowManager.class);
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, "الأذكار فوق التطبيقات", NotificationManager.IMPORTANCE_MIN));
        Notification n = new Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_crescent)
            .setContentTitle("ذكر الآن").setOngoing(true).setContentIntent(android.app.PendingIntent.getActivity(this, 0, new Intent(this, PhoneActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP), android.app.PendingIntent.FLAG_IMMUTABLE | android.app.PendingIntent.FLAG_UPDATE_CURRENT)).build();
        if (Build.VERSION.SDK_INT >= 29) startForeground(77, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(77, n);
        h.removeCallbacks(stop);
        boolean now = intent != null && intent.getBooleanExtra("now", false);
        if (Settings.canDrawOverlays(this) && (wanted(this) || now)) {
            try {
                showCard();
            } catch (RuntimeException ignored) {
                // a failed card is skipped; the next one comes at the usual time
            }
        }
        h.postDelayed(stop, SHOWN_MS + 300L);
        return START_NOT_STICKY;
    }

    /** Called by the alarm: sets the next one, then shows a card. */
    static void fire(Context c) {
        schedule(c, true);
        if (!(wanted(c) && Settings.canDrawOverlays(c) && !Wc.on(c))) return;
        try {
            c.startForegroundService(new Intent(c, OverlayService.class));
        } catch (RuntimeException ignored) {
            // refused by the system: the next alarm tries again
        }
    }

    @Override
    public void onDestroy() {
        h.removeCallbacksAndMessages(null);
        hideCard();
        super.onDestroy();
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setGravity(Gravity.CENTER);
        return t;
    }

    private int dp(int v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }

    private void showCard() {
        if (AdhanService.playing || Wc.on(this)) return;
        hideCard();
        List<String> items = new ArrayList<>();
        for (String[] s : Adhkar.overlay(this)) if (s[1].equals("1") && !s[0].trim().isEmpty()) items.add(s[0]);
        if (items.isEmpty()) return;
        int idx = sp.getInt("ov_idx", 0) % items.size();
        sp.edit().putInt("ov_idx", idx + 1).apply();

        // A small card at the top right: no buttons, it goes by itself after 5 seconds or when it is tapped.
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        c.setPadding(dp(16), dp(12), dp(16), dp(12));
        GradientDrawable bg = new GradientDrawable();
        boolean night = (getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
            == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        // The colours of the look (Mihrab at night, Royal by day), a little lighter so the card stands out.
        final int fill = night ? 0xF2284583 : 0xF2186548;
        final int gold = night ? 0xFFDCB860 : 0xFFE8B84A;
        final int cream = night ? 0xFFF1E8D0 : 0xFFF6ECD2;
        bg.setColor(fill);
        bg.setCornerRadius(dp(16));
        bg.setStroke(dp(1), gold);
        c.setBackground(bg);
        TextView t = text(items.get(idx), Math.round(17 * Adhkar.scale(this, 2)), Adhkar.color(this, 2, cream, gold), true);
        t.setLineSpacing(0, 1.2f);
        c.addView(t);
        c.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideCard();
            }
        });

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(-2, -2,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.RIGHT;
        lp.x = dp(12);
        lp.y = dp(44);
        lp.width = Math.round(getResources().getDisplayMetrics().widthPixels * 0.72f);
        wm.addView(c, lp);
        card = c;
    }

    private void hideCard() {
        if (card != null && wm != null) {
            try {
                wm.removeView(card);
            } catch (RuntimeException ignored) {
                // already gone
            }
        }
        card = null;
    }
}
