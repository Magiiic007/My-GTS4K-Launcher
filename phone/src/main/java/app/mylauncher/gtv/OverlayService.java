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
import java.util.Locale;

/**
 * Shows a short dhikr card over whatever app is open, every few minutes, with the live tasbih counters. It needs the
 * "display over other apps" permission. The adhan never uses it (it plays quietly in the background).
 */
public class OverlayService extends Service {
    private static final String CHANNEL = "overlay";
    private static final long SHOWN_MS = 15000L;

    private final Handler h = new Handler(Looper.getMainLooper());
    private WindowManager wm;
    private View card;
    private SharedPreferences sp;

    private final Runnable show = new Runnable() {
        @Override
        public void run() {
            try {
                showCard();
            } catch (RuntimeException ignored) {
                // a failed card is skipped; the next one is tried at the usual time
            }
            h.postDelayed(this, freqMs());
        }
    };
    private final Runnable hide = new Runnable() {
        @Override
        public void run() {
            hideCard();
        }
    };

    static boolean wanted(Context c) {
        return c.getSharedPreferences("magiic", MODE_PRIVATE).getBoolean("ov_on", false);
    }

    /** Starts or stops the service to match the setting and the permission. */
    static void sync(Context c) {
        try {
            if (wanted(c) && Settings.canDrawOverlays(c)) {
                c.startForegroundService(new Intent(c, OverlayService.class));
            } else {
                c.stopService(new Intent(c, OverlayService.class));
            }
        } catch (RuntimeException ignored) {
            // starting from the background can be refused; the next time the app opens it is tried again
        }
    }

    private long freqMs() {
        return Math.max(1, sp.getInt("ov_freq", 30)) * 60000L;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        sp = getSharedPreferences("magiic", MODE_PRIVATE);
        wm = getSystemService(WindowManager.class);
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, "الأذكار فوق التطبيقات", NotificationManager.IMPORTANCE_MIN));
        Notification n = new Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_crescent)
            .setContentTitle("الأذكار فوق التطبيقات تعمل").setOngoing(true).build();
        if (Build.VERSION.SDK_INT >= 29) startForeground(77, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(77, n);
        boolean now = intent != null && intent.getBooleanExtra("now", false);
        if (!Settings.canDrawOverlays(this) || (!wanted(this) && !now)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        h.removeCallbacks(show);
        if (now) {
            try {
                showCard();
            } catch (RuntimeException ignored) {
                // the preview failed; nothing else to do
            }
        }
        if (wanted(this)) {
            h.postDelayed(show, freqMs());
            return START_STICKY;
        }
        h.postDelayed(new Runnable() {
            @Override
            public void run() {
                stopSelf();
            }
        }, SHOWN_MS + 1000L);
        return START_NOT_STICKY;
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
        if (AdhanService.playing) return;
        hideCard();
        List<String> items = new ArrayList<>();
        for (String s : Adhkar.TASBIH) items.add(s);
        items.addAll(Adhkar.mine(this));
        int idx = sp.getInt("ov_idx", 0) % items.size();
        sp.edit().putInt("ov_idx", idx + 1).apply();
        final int fi = idx;

        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        c.setPadding(dp(16), dp(12), dp(16), dp(12));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF2101418);
        bg.setCornerRadius(dp(18));
        bg.setStroke(dp(1), 0xFFE8B84A);
        c.setBackground(bg);
        c.addView(text(items.get(idx), 18, 0xFFF6ECD2, true));
        final boolean counters = sp.getBoolean("ov_cnt", true);
        final TextView count = text("", 14, 0xFFE8B84A, true);
        if (counters) {
            count.setText(counterLine(fi));
            c.addView(count);
        }
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER);
        if (fi < Adhkar.TASBIH.length) {
            TextView plus = text("عدّ +1", 15, 0xFF1B1500, true);
            GradientDrawable pb = new GradientDrawable();
            pb.setColor(0xFFE8B84A);
            pb.setCornerRadius(dp(14));
            plus.setBackground(pb);
            plus.setPadding(dp(18), dp(8), dp(18), dp(8));
            plus.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Adhkar.add(OverlayService.this, fi);
                    if (counters) count.setText(counterLine(fi));
                    h.removeCallbacks(hide);
                    h.postDelayed(hide, SHOWN_MS);
                }
            });
            row.addView(plus);
        }
        TextView close = text("إغلاق", 15, 0xFFF6ECD2, true);
        close.setPadding(dp(18), dp(8), dp(18), dp(8));
        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideCard();
            }
        });
        row.addView(close);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-2, -2);
        rp.topMargin = dp(8);
        rp.gravity = Gravity.CENTER_HORIZONTAL;
        c.addView(row, rp);

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(-1, -2,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.BOTTOM;
        lp.setMargins(dp(12), 0, dp(12), dp(24));
        wm.addView(c, lp);
        card = c;
        h.removeCallbacks(hide);
        h.postDelayed(hide, SHOWN_MS);
    }

    private String counterLine(int i) {
        return String.format(Locale.US, "تسبيح %d · استغفار %d",
            Adhkar.count(this, 0) + Adhkar.count(this, 1) + Adhkar.count(this, 2) + Adhkar.count(this, 4),
            Adhkar.count(this, 3));
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
