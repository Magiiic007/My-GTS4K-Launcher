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
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * A small round icon over other apps while it is the time of the morning adhkar (Fajr until Dhuhr) or the evening
 * adhkar (Asr until Isha). Tapping it opens that chapter in the app; the small X hides it for the rest of that
 * period. Needs the "display over other apps" permission, and can be switched off in the adhkar settings.
 */
public class BubbleService extends Service {
    private static final String CHANNEL = "bubble";
    static final int MORNING = 1;   // chapter ids of "أذكار الصباح" and "أذكار المساء" in the adhkar file
    static final int EVENING = 133;

    private final Handler h = new Handler(Looper.getMainLooper());
    private WindowManager wm;
    private SharedPreferences sp;
    private View view;
    private int shownFor;

    private final Runnable check = new Runnable() {
        @Override
        public void run() {
            try {
                update();
            } catch (RuntimeException ignored) {
                // skipped; the next check is in 30 seconds
            }
            h.postDelayed(this, 30_000L);
        }
    };

    static boolean wanted(Context c) {
        return c.getSharedPreferences("magiic", MODE_PRIVATE).getBoolean("az_bubble", true);
    }

    private static String day() {
        return new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());
    }

    static String doneKey(int cat) {
        return "az_done_" + day() + "_" + cat;
    }

    /** Which period it is now (MORNING, EVENING or 0), by the selected city's prayer times. */
    static int period(Context c) {
        Cities.City city = Cities.selected(c);
        if (city == null) return 0;
        long now = System.currentTimeMillis();
        long[] t = PrayerCalc.day(city, now, 0, PrayerPrefs.adjust(c));
        if (now >= t[0] && now < t[1]) return MORNING;
        if (now >= t[2] && now < t[4]) return EVENING;
        return 0;
    }

    /** The period to show the icon for right now: none when it is not the time, or when it was tapped or closed today. */
    static int active(Context c) {
        if (Wc.on(c)) return 0;
        int p = period(c);
        if (p != 0 && c.getSharedPreferences("magiic", MODE_PRIVATE).getBoolean(doneKey(p), false)) return 0;
        return p;
    }

    /**
     * The service runs only while the icon is on screen (so its notification shows only then): it is started at the
     * beginning of each period by an alarm, and stops when the icon is tapped, closed, or the period ends.
     */
    static void sync(Context c) {
        try {
            schedule(c);
            if (wanted(c) && Settings.canDrawOverlays(c) && active(c) != 0) {
                c.startForegroundService(new Intent(c, BubbleService.class));
            } else {
                c.stopService(new Intent(c, BubbleService.class));
            }
        } catch (RuntimeException ignored) {
            // starting from the background can be refused; it is tried again at the next alarm or when the app opens
        }
    }

    /** An alarm at the start of the next period (Fajr or Asr). */
    private static void schedule(Context c) {
        android.app.AlarmManager am = c.getSystemService(android.app.AlarmManager.class);
        Cities.City city = Cities.selected(c);
        android.app.PendingIntent pi = android.app.PendingIntent.getBroadcast(c, 77,
            new Intent(c, BubbleReceiver.class), android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE);
        if (am == null || city == null || !wanted(c)) {
            if (am != null) am.cancel(pi);
            return;
        }
        long now = System.currentTimeMillis();
        int[] adj = PrayerPrefs.adjust(c);
        long next = Long.MAX_VALUE;
        for (int off = 0; off <= 1; off++) {
            long[] t = PrayerCalc.day(city, now, off, adj);
            if (t[0] > now + 1000L) next = Math.min(next, t[0]);
            if (t[2] > now + 1000L) next = Math.min(next, t[2]);
        }
        if (next == Long.MAX_VALUE) return;
        try {
            am.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, next, pi);
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, next, pi);
        }
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
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, "أيقونة الأذكار", NotificationManager.IMPORTANCE_MIN));
        Notification n = new Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_crescent)
            .setContentTitle("أيقونة أذكار الصباح والمساء تعمل").setOngoing(true).setContentIntent(android.app.PendingIntent.getActivity(this, 0, new Intent(this, PhoneActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP), android.app.PendingIntent.FLAG_IMMUTABLE | android.app.PendingIntent.FLAG_UPDATE_CURRENT)).build();
        if (Build.VERSION.SDK_INT >= 29) startForeground(78, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(78, n);
        if (!Settings.canDrawOverlays(this) || !wanted(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        h.removeCallbacks(check);
        h.post(check);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        h.removeCallbacksAndMessages(null);
        hide();
        super.onDestroy();
    }

    private void update() {
        int p = active(this);
        if (p == 0) {
            hide();
            stopSelf(); // not the time, or already tapped or closed: the icon and its notification go
            return;
        }
        if (view != null && shownFor == p) return;
        hide();
        show(p);
    }

    private int dp(int v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }

    private void show(final int cat) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView b = new TextView(this);
        b.setText(cat == MORNING ? "أذكار\nالصباح" : "أذكار\nالمساء");
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(0xFFF6ECD2);
        b.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(cat == MORNING ? 0xF2186548 : 0xF2284583);
        bg.setStroke(dp(2), 0xFFE8B84A);
        b.setBackground(bg);
        col.addView(b, new LinearLayout.LayoutParams(dp(64), dp(64)));

        TextView x = new TextView(this);
        x.setText("✕");
        x.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        x.setTextColor(0xFFFFFFFF);
        x.setGravity(Gravity.CENTER);
        GradientDrawable xb = new GradientDrawable();
        xb.setShape(GradientDrawable.OVAL);
        xb.setColor(0xCC333333);
        x.setBackground(xb);
        LinearLayout.LayoutParams xp = new LinearLayout.LayoutParams(dp(28), dp(28));
        xp.topMargin = dp(8);
        col.addView(x, xp);
        x.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sp.edit().putBoolean(doneKey(cat), true).apply();
                hide();
                stopSelf();
            }
        });

        final WindowManager.LayoutParams lp = new WindowManager.LayoutParams(-2, -2,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.RIGHT;
        lp.x = dp(12);
        lp.y = dp(sp.getInt("az_bubble_y", 90));

        // A tap opens the chapter; dragging moves the icon (its height is remembered).
        b.setOnTouchListener(new View.OnTouchListener() {
            float downX, downY;
            int startX, startY;
            boolean moved;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = e.getRawX();
                        downY = e.getRawY();
                        startX = lp.x;
                        startY = lp.y;
                        moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = e.getRawX() - downX;
                        float dy = e.getRawY() - downY;
                        if (Math.abs(dx) > dp(8) || Math.abs(dy) > dp(8)) moved = true;
                        if (moved) {
                            lp.x = startX - Math.round(dx);
                            lp.y = startY + Math.round(dy);
                            try {
                                wm.updateViewLayout(view, lp);
                            } catch (RuntimeException ignored) {
                                // the view is already gone
                            }
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (moved) {
                            sp.edit().putInt("az_bubble_y", Math.round(lp.y / getResources().getDisplayMetrics().density)).apply();
                        } else {
                            open(cat);
                        }
                        return true;
                    default:
                        return false;
                }
            }
        });
        wm.addView(col, lp);
        view = col;
        shownFor = cat;
    }

    private void open(int cat) {
        sp.edit().putBoolean(doneKey(cat), true).apply();
        hide();
        stopSelf();
        Intent in = new Intent(this, PhoneActivity.class)
            .putExtra("open_cat", cat)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try {
            startActivity(in);
        } catch (RuntimeException ignored) {
            // refused by the system; the user can open the app by hand
        }
    }

    private void hide() {
        if (view != null && wm != null) {
            try {
                wm.removeView(view);
            } catch (RuntimeException ignored) {
                // already gone
            }
        }
        view = null;
        shownFor = 0;
    }
}
