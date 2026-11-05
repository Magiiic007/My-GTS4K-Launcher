package app.mylauncher.gtv;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.drawable.LayerDrawable;
import android.hardware.GeomagneticField;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.CheckBox;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

/**
 * The phone app: one portrait screen with a side menu (prayer, adhkar and reminders, qibla and radio, settings). It shares the engine with the
 * TV app (prayer times, Hijri dates, reminders, radio) and draws everything with plain views in two looks, Royal and
 * Mihrab.
 */
public class PhoneActivity extends Activity {
    private static final String P = "magiic";
    private static final int REQ_NOTIF = 10, REQ_LOC = 11, REQ_SAVE = 21, REQ_LOAD = 22;

    // ---------------------------------------------------------------- state
    private final Handler h = new Handler(Looper.getMainLooper());
    // setting up the TV from this phone
    private int tvStage = 0;
    private volatile boolean tvBusy = false;
    private boolean tvSearching = false;
    private String tvAddr = "", tvCode = "";
    /** The notes of the TV setup, newest first (kept only while the app is open). */
    private final java.util.ArrayList<String> tvEntries = new java.util.ArrayList<>();
    private LinearLayout tvFound;
    private SharedPreferences sp;
    private int tab;
    private String sub;
    private int look;
    private int bg1, bg2, gold, cream, dim, card, line, hl;
    private LinearLayout body;
    private ScrollView scroll;
    private FrameLayout drawerLayer;
    private View scrim;
    private ScrollView drawerPanel;
    private int drawerW;
    private LinearLayout drawerList;
    private boolean drawerOpen;
    private Qibla.Dial dial;
    private SensorManager sm;
    private SensorEventListener qListener;
    private float heading;
    private TextView qiblaText;
    private final HashMap<String, Integer> done = new HashMap<>();
    private int tk;
    private TextView tcBig;
    private TextView[] tcRows;
    private boolean autoTried;
    private boolean locAsked;
    private String weather = "";
    private int[] occNear;
    private String occDay = "";
    private int shownNext = -1;

    // views of the home screen that change every second
    private TextView clockTv, apTv, nameTv, cdTv, altTv, iqLabel, iqValue, weatherTv;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            tick();
            h.postDelayed(this, 1000L);
        }
    };

    private final RadioService.Listener radioListener = new RadioService.Listener() {
        @Override
        public void onRadio(int state) {
            if (tab == 1) build();
        }
    };

    // ---------------------------------------------------------------- lifecycle
    @Override
    protected void attachBaseContext(Context base) {
        Configuration c = new Configuration(base.getResources().getConfiguration());
        c.setLocale(PhoneApp.AR);
        c.setLayoutDirection(PhoneApp.AR);
        super.attachBaseContext(base.createConfigurationContext(c));
    }

    /** Royal is the day look and Mihrab the night look: they follow the phone's own light/dark setting. */
    private int lookNow() {
        int forced = sp == null ? -1 : sp.getInt("look_force", -1);
        if (forced == 0 || forced == 1) return forced;
        int night = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return night == Configuration.UI_MODE_NIGHT_YES ? 1 : 0;
    }

    @Override
    public void onConfigurationChanged(Configuration cfg) {
        super.onConfigurationChanged(cfg);
        int l = lookNow();
        if (l != look) {
            look = l;
            palette();
            root();
            build();
        }
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = getSharedPreferences(P, MODE_PRIVATE);
        look = lookNow();
        palette();
        root();
        // Everything is asked for at once on the first run: two separate requests cancel each other.
        if (!sp.getBoolean("ph_first", false)) {
            ArrayList<String> need = new ArrayList<>();
            if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                need.add(Manifest.permission.POST_NOTIFICATIONS);
            }
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                need.add(Manifest.permission.ACCESS_FINE_LOCATION);
                need.add(Manifest.permission.ACCESS_COARSE_LOCATION);
            }
            if (!need.isEmpty()) {
                locAsked = true;
                requestPermissions(need.toArray(new String[0]), REQ_NOTIF);
            } else {
                sp.edit().putBoolean("ph_first", true).apply();
            }
        }
        Scheduler.scheduleNext(this);
        Reminders.scheduleNext(this);
        openFromBubble(getIntent());
        build();
        if (b == null && sp.getBoolean("splash_on", true)) splash();
    }

    private static final String[] SPLASH_WORDS = {"النوم", "اللهو", "التجارة", "العمل", "الدنيا", "السهر", "الراحة", "الفرجة", "التصفّح", "المال", "الكسل", "التسويف"};
    private View splashView;
    private int splashStep;

    /** The opening screen: the app's background, its icon in the middle, and "الصلاة خير من" with a word that changes every second. */
    private void splash() {
        if (splashView != null) return;
        final FrameLayout v = new FrameLayout(this);
        v.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[] {bg1, bg2});
        Deco.Stars stars = new Deco.Stars((cream & 0x00FFFFFF) | 0x1A000000, dp(56));
        ArtBg.Border border = new ArtBg.Border((gold & 0x00FFFFFF) | 0x99000000, getResources().getDisplayMetrics().density);
        v.setBackground(new LayerDrawable(new android.graphics.drawable.Drawable[] {g, stars, border}));
        v.setClickable(true);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        android.widget.ImageView icon = new android.widget.ImageView(this);
        icon.setImageResource(R.drawable.ic_launcher_phone);
        col.addView(icon, new LinearLayout.LayoutParams(dp(150), dp(150)));
        // one centred line: "الصلاة خير من" in white and the changing word in yellow
        final TextView phrase = new TextView(this);
        phrase.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        phrase.setTypeface(Typeface.DEFAULT_BOLD);
        phrase.setGravity(Gravity.CENTER);
        phrase.setTextColor(0xFFFFFFFF);
        phrase.setShadowLayer(dp(2), 0, dp(1), 0x66000000);
        phrase.setText("الصلاة خير من");
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
        rp.topMargin = dp(26);
        col.addView(phrase, rp);
        v.addView(col, new FrameLayout.LayoutParams(-1, -1));
        rootFrame.addView(v, new FrameLayout.LayoutParams(-1, -1));
        splashView = v;
        splashStep = 0;
        // no tap or back button closes it: it runs its six seconds (touches are swallowed so nothing behind it is pressed)
        // three of the twelve words, in a random order each time
        final java.util.List<String> pool = new java.util.ArrayList<>(java.util.Arrays.asList(SPLASH_WORDS));
        java.util.Collections.shuffle(pool);
        final java.util.List<String> words = pool.subList(0, 3);
        final Runnable next = new Runnable() {
            @Override
            public void run() {
                if (splashView == null || isFinishing()) return;
                if (splashStep < words.size()) {
                    final String w = words.get(splashStep++);
                    android.animation.ValueAnimator an = android.animation.ValueAnimator.ofInt(0, 255);
                    an.setDuration(250);
                    an.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() {
                        @Override
                        public void onAnimationUpdate(android.animation.ValueAnimator x) {
                            int al = (Integer) x.getAnimatedValue();
                            android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder("الصلاة خير من ");
                            int st = sb.length();
                            sb.append(w);
                            sb.setSpan(new android.text.style.ForegroundColorSpan((al << 24) | 0x00FFD54A), st, sb.length(),
                                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                            phrase.setText(sb);
                        }
                    });
                    an.start();
                    h.postDelayed(this, 1500L);
                } else {
                    endSplash();
                }
            }
        };
        // 0.2 s of the icon alone, three words 1.5 s each, then a 0.3 s fade: 5 seconds in all
        h.postDelayed(next, 200L);
    }

    private void endSplash() {
        final View v = splashView;
        if (v == null) return;
        splashView = null;
        v.animate().alpha(0f).setDuration(300).withEndAction(new Runnable() {
            @Override
            public void run() {
                try {
                    rootFrame.removeView(v);
                } catch (RuntimeException ignored) {
                    // already gone
                }
            }
        }).start();
    }

    /** The round adhkar icon over other apps opens the morning or evening chapter directly. */
    private void openFromBubble(Intent in) {
        if (in == null) return;
        int cat = in.getIntExtra("open_cat", 0);
        if (cat == 0) return;
        in.removeExtra("open_cat");
        tab = 5;
        clearDone(cat);
        editCat = false;
        sub = "cat:" + cat;
    }

    @Override
    protected void onNewIntent(Intent in) {
        super.onNewIntent(in);
        setIntent(in);
        // opened again from a notification, the widget or the adhkar icon: the opening screen shows then too
        if (sp.getBoolean("splash_on", true)) splash();
        if (in != null && in.getIntExtra("open_cat", 0) != 0) {
            openFromBubble(in);
            build();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        verseIdx = -1;
        RadioService.listen(radioListener);
        RadioService.resumeIfCutOff(this);
        OverlayService.sync(this);
        BubbleService.sync(this);
        PrayerNote.refresh(this);
        if (tab == 8 || tab == 6 || (tab == 0 && sub == null) || tvVisible()) build();
        if (tab == 9) qiblaStart();
        h.post(ticker);
        if (!autoTried && sp.getBoolean("ph_first", false)
            ) {
            autoTried = true;
            autoCity(false);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (tab == 6) endTasbihRound();
        RadioService.unlisten(radioListener);
        h.removeCallbacks(ticker);
        PrayerNote.refresh(this);
        qiblaStop();
        if (tvSearching) {
            TvSetup.stopDiscovery();
            tvSearching = false;
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        if (code == REQ_NOTIF) {
            sp.edit().putBoolean("ph_first", true).apply();
            autoTried = true;
            autoCity(false);
            new Pop(this).title("خطوة أخيرة مهمة")
                .message("لكي يعمل الأذان والراديو والشاشة مقفولة، افتح صفحة «تأكد أن الأذان يعمل» وفعّل كل بند فيها.")
                .ok("فتح الصفحة", new Runnable() {
                    @Override
                    public void run() {
                        tab = 3;
                        sub = "fix";
                        build();
                    }
                }).cancel("لاحقًا").show();
            return;
        }
        if (code == REQ_LOC) autoCity(false);
        if (tab == 3 || tab == 8) build();
    }

    @Override
    public void onBackPressed() {
        if (splashView != null) return; // the opening screen is not closed by Back
        if (drawerOpen) {
            closeDrawer();
            return;
        }
        if (sub != null) {
            sub = backTarget(sub);
            backNav = true;
            build();
        } else if (tab != 0) {
            tab = 0;
            backNav = true;
            build();
        } else {
            super.onBackPressed();
        }
    }

    private String backTarget(String s) {
        if (s.equals("up")) return "occ";
        if (s.equals("tvkeys")) return "tv";
        if (s.startsWith("item:")) return itemBack;
        if (s.equals("country") || s.equals("cities")) return null;
        return null;
    }

    // ---------------------------------------------------------------- look
    private void palette() {
        if (look == 1) {
            bg1 = 0xFF011A4E; bg2 = 0xFF02266A; gold = 0xFFDCB860; cream = 0xFFF1E8D0; dim = 0xFFB4BDDB;
        } else {
            bg1 = 0xFF01490E; bg2 = 0xFF05601A; gold = 0xFFE8B84A; cream = 0xFFF6ECD2; dim = 0xFFB9C9BD;
        }
        // The cards have their own colour, different from the page, and fairly see-through: deep ink blue over the green
        // look, deep teal over the navy look.
        card = look == 1 ? 0x880B4D45 : 0x880A2A4A;
        line = (gold & 0x00FFFFFF) | 0x59000000;
        hl = (gold & 0x00FFFFFF) | 0x33000000;
    }

    private int dp(int v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }

    private android.graphics.drawable.Drawable shape(int fill, int stroke, int radiusDp) {
        // the cards of the pages look raised (a soft shadow and a light top edge); other shapes stay flat
        if (fill == card && stroke == line && radiusDp >= 10 && sp != null && sp.getBoolean("depth_on", true)) {
            return new Raised(getResources().getDisplayMetrics().density, radiusDp, stroke, fill);
        }
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radiusDp));
        if (stroke != 0) g.setStroke(dp(1), stroke);
        return g;
    }

    private LinearLayout header;
    private FrameLayout rootFrame;
    private static android.graphics.Bitmap artBmp;
    private static int artLook = -1;
    private int homeTop, homeBottom;

    /** The home page has the picture; the other pages have the same colours with a gold frame line. */
    private void applyBackground() {
        int w = getResources().getDisplayMetrics().widthPixels;
        if (tab == 0) {
            if (artBmp == null || artLook != look) {
                try {
                    artBmp = android.graphics.BitmapFactory.decodeResource(getResources(),
                        look == 1 ? R.drawable.home_night : R.drawable.home_day);
                    artLook = look;
                } catch (RuntimeException | OutOfMemoryError e) {
                    artBmp = null;
                }
            }
            if (artBmp != null) {
                homeTop = ArtBg.panelTop(artBmp, w);
                homeBottom = Math.round(ArtBg.cornerHeight(artBmp, w) * 0.7f);
                rootFrame.setBackground(new ArtBg(artBmp));
                header.setPadding(dp(26), homeTop, dp(26), dp(2));
                body.setPadding(dp(26), dp(8), dp(26), homeBottom);
                return;
            }
        }
        homeTop = 0;
        homeBottom = 0;
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[] {bg1, bg2});
        Deco.Stars stars = new Deco.Stars((cream & 0x00FFFFFF) | 0x1A000000, dp(56));
        ArtBg.Border border = new ArtBg.Border((gold & 0x00FFFFFF) | 0x99000000, getResources().getDisplayMetrics().density);
        rootFrame.setBackground(new LayerDrawable(new android.graphics.drawable.Drawable[] {g, stars, border}));
        header.setPadding(dp(18), dp(14), dp(18), dp(2));
        body.setPadding(dp(18), dp(10), dp(18), dp(20));
    }

    private void addHeader(View v) {
        header.addView(v, new LinearLayout.LayoutParams(-1, -2));
    }

    private void root() {
        getWindow().setStatusBarColor(bg1);
        getWindow().setNavigationBarColor(bg2);
        FrameLayout f = new FrameLayout(this);
        f.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        rootFrame = f;
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        // the title row (with the menu or back button) stays put while the page scrolls under it
        header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(14), dp(10), dp(14), dp(2));
        col.addView(header, new LinearLayout.LayoutParams(-1, -2));
        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(14), dp(10), dp(14), dp(16));
        scroll.addView(body, new ViewGroup.LayoutParams(-1, -2));
        col.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        f.addView(col, new FrameLayout.LayoutParams(-1, -1));
        drawerOpen = false;
        drawerLayer = new FrameLayout(this);
        drawerLayer.setVisibility(View.GONE);
        scrim = new View(this);
        scrim.setBackgroundColor(0x99000000);
        scrim.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeDrawer();
            }
        });
        drawerLayer.addView(scrim, new FrameLayout.LayoutParams(-1, -1));
        drawerPanel = new ScrollView(this);
        // opaque at the screen edge, fading to 80% see-through at the inner edge
        boolean rtl = getResources().getConfiguration().getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
        drawerPanel.setBackground(new FadePanel(bg1, bg2, gold, 0.2f, rtl, dp(1)));
        drawerList = vbox();
        drawerList.setPadding(dp(12), dp(30), dp(12), dp(16));
        drawerPanel.addView(drawerList, new ViewGroup.LayoutParams(-1, -2));
        // about half of the screen: 48% of its width, but never narrower than the longest menu name needs
        drawerW = Math.max(dp(190), Math.round(getResources().getDisplayMetrics().widthPixels * 0.48f));
        drawerLayer.addView(drawerPanel, new FrameLayout.LayoutParams(drawerW, -1, Gravity.START));
        f.addView(drawerLayer, new FrameLayout.LayoutParams(-1, -1));
        setContentView(f);
    }

    // ---------------------------------------------------------------- small view helpers
    private TextView tv(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private LinearLayout vbox() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private LinearLayout hbox() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    private LinearLayout.LayoutParams lp(int w, int hh, int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, hh);
        p.topMargin = dp(top);
        return p;
    }

    private void add(View v, int topDp) {
        body.addView(v, lp(-1, -2, topDp));
    }

    private void title(String t, boolean back) {
        LinearLayout r = hbox();
        if (back) {
            TextView b = tv("‹", 22, gold, true);
            b.setGravity(Gravity.CENTER);
            b.setBackground(shape(0, line, 18));
            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onBackPressed();
                }
            });
            r.addView(b, new LinearLayout.LayoutParams(dp(38), dp(38)));
        }
        else r.addView(hamburger(), new LinearLayout.LayoutParams(dp(38), dp(38)));
        TextView t1 = tv(t, 22, gold, true);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, -2);
        p.setMarginStart(dp(10));
        r.addView(t1, p);
        addHeader(r);
    }

    private void section(String t) {
        TextView s = tv(t, 13, gold, true);
        s.setPadding(dp(4), 0, 0, 0);
        add(s, 14);
    }

    private LinearLayout row(String t, String subText, String action, View.OnClickListener click) {
        LinearLayout r = hbox();
        r.setBackground(shape(card, line, 14));
        r.setPadding(dp(14), dp(10), dp(14), dp(10));
        r.setMinimumHeight(dp(52));
        LinearLayout v = vbox();
        v.addView(tv(t, 16, cream, false));
        if (subText != null && !subText.isEmpty()) v.addView(tv(subText, 12, dim, false));
        r.addView(v, new LinearLayout.LayoutParams(0, -2, 1f));
        if (action != null) r.addView(tv(action, 16, gold, true));
        if (click != null) r.setOnClickListener(click);
        add(r, 6);
        return r;
    }

    private void switchRow(String t, String subText, boolean on, final CompoundListener l) {
        LinearLayout r = row(t, subText, null, null);
        Switch s = new Switch(this);
        s.setChecked(on);
        s.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(android.widget.CompoundButton b, boolean checked) {
                l.changed(checked);
            }
        });
        r.addView(s);
    }

    private interface CompoundListener {
        void changed(boolean on);
    }

    private Button button(String t, View.OnClickListener c) {
        Button b = new Button(this);
        b.setText(t);
        b.setAllCaps(false);
        b.setTextColor(0xFF1B1500);
        b.setBackground(shape(gold, 0, 14));
        b.setOnClickListener(c);
        return b;
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    // ---------------------------------------------------------------- tabs
    /** The verse at the bottom of the home page: a new one each time the app is opened. */
    private int verseIdx = -1;

    private void endTasbihRound() {
        for (int i = 0; i < Adhkar.TASBIH.length; i++) Adhkar.reset(this, i);
    }

    private final HashMap<String, Integer> savedScroll = new HashMap<>();
    private String lastKey;
    private boolean backNav;
    private boolean toTop;

    private void build() {
        palette();
        String key = tab + ":" + sub;
        if (lastKey != null) savedScroll.put(lastKey, scroll.getScrollY());
        final boolean samePage = key.equals(lastKey);
        // a new visit to the tasbih starts the round from 0 (the last count and today's totals stay)
        if (tab == 6 && (lastKey == null || !lastKey.startsWith("6:"))) endTasbihRound();
        lastKey = key;
        if (tab != 3 || sub == null) addingOther = false;
        otherTick.clear();
        // The screen stays on while a dhikr (or the tasbih) is open, and goes back to the normal timeout on leaving it.
        boolean reading = (tab == 5 && sub != null && (sub.startsWith("cat:") || sub.equals("hadith"))) || tab == 6;
        if (reading) getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        clockTv = apTv = nameTv = cdTv = altTv = iqLabel = iqValue = weatherTv = null;
        applyBackground();
        body.removeAllViews();
        header.removeAllViews();
        if (tab != 9) qiblaStop();
        if (!(tab == 3 && "tv".equals(sub)) && tvSearching) {
            TvSetup.stopDiscovery();
            tvSearching = false;
        }
        try {
            if (tab == 0) home();
            else if (tab == 1) radio();
            else if (tab == 2) reminders();
            else if (tab == 3) settings();
            else if (tab == 4) adhanPage();
            else if (tab == 5) adhkarPage();
            else if (tab == 6) tasbihPage();
            else if (tab == 8) overlayPage();
            else if (tab == 10) calendarPage();
            else if (tab == 11) trackPage();
            else if (tab == 12) qadaPage();
            else if (tab == 13) otherPage();
            else qiblaPage();
        } catch (Throwable t) {
            // A screen that fails shows what went wrong instead of closing the app.
            body.removeAllViews();
            java.io.StringWriter w = new java.io.StringWriter();
            t.printStackTrace(new java.io.PrintWriter(w));
            sp.edit().putString("dbg_crash", w.toString()).apply();
            TextView e = tv("حدث خطأ في هذه الصفحة. أرسل هذا النص للمطوّر:\n\n" + w, 11, cream, false);
            e.setTextIsSelectable(true);
            e.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
            add(e, 10);
        }
        int y = 0;
        if (!toTop && (samePage || backNav)) {
            Integer v = savedScroll.get(key);
            if (v != null) y = v;
        }
        toTop = false;
        backNav = false;
        final int fy = y;
        scroll.scrollTo(0, 0);
        if (fy > 0) {
            scroll.post(new Runnable() {
                @Override
                public void run() {
                    scroll.scrollTo(0, fy);
                }
            });
        }
        try {
            tick();
        } catch (RuntimeException ignored) {
            // The next tick tries again.
        }
    }

    // ---------------------------------------------------------------- home
    private void home() {
        final Cities.City city = Cities.selected(this);
        if (city == null) {
            title("الصلاة خير", false);
            row("اختر مدينتك", "لحساب مواقيت الصلاة", "تلقائي", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    autoCity(true);
                }
            });
            row("اختيار يدوي", null, "›", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    tab = 3;
                    sub = "country";
                    build();
                }
            });
            return;
        }
        long now = System.currentTimeMillis();
        int[] adj = PrayerPrefs.adjust(this);
        Calendar cal = Hijri.today(this);
        int[] hij = Hijri.of(cal, Hijri.adjust(this));
        String day = cal.get(Calendar.YEAR) + "-" + cal.get(Calendar.DAY_OF_YEAR);
        if (!day.equals(occDay)) {
            occDay = day;
            occNear = Reminders.nearest(this);
        }

        float f = fit();
        // top row: the menu, the place (down to the neighbourhood when the phone's location is used) and the weather
        LinearLayout top = hbox();
        top.addView(hamburger(), new LinearLayout.LayoutParams(dp(38), dp(38)));
        TextView place = chip("📍 " + city.name(true));
        place.setSingleLine(true);
        place.setEllipsize(android.text.TextUtils.TruncateAt.END);
        place.setGravity(Gravity.CENTER);
        place.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tab = 3;
                sub = "country";
                build();
            }
        });
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(0, -2, 1f);
        pp.setMarginStart(dp(8));
        pp.setMarginEnd(dp(8));
        top.addView(place, pp);
        weatherTv = chip(weather.isEmpty() ? "--°" : weather);
        top.addView(weatherTv);
        addHeader(top);
        if (weather.isEmpty()) {
            Weather.fetch(city, new Weather.Callback() {
                @Override
                public void onResult(int tempC, int code) {
                    weather = tempC + "°";
                    if (weatherTv != null) weatherTv.setText(weather);
                }

                @Override
                public void onError() {
                }
            });
        }

        // one card holds the clock, the dates and the next prayer (same dotted frame in both looks)
        LinearLayout hero = vbox();
        hero.setGravity(Gravity.CENTER_HORIZONTAL);
        Deco.Frame frame = new Deco.Frame(false, (gold & 0x00FFFFFF) | 0x99000000, getResources().getDisplayMetrics().density);
        hero.setBackground(new LayerDrawable(new android.graphics.drawable.Drawable[] {shape(card, line, 22), frame}));
        hero.setPadding(dp(14), dp(Math.round(10 * f)), dp(14), dp(Math.round(10 * f)));
        // clock and dates
        LinearLayout clock = hbox();
        clock.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        clock.setGravity(Gravity.CENTER);
        clockTv = tv("--:--", Math.round(62 * f), gold, true);
        apTv = tv("", 15, cream, true);
        apTv.setPadding(dp(8), 0, 0, 0);
        clock.addView(clockTv);
        clock.addView(apTv);
        hero.addView(clock, lp(-2, -2, 0));
        TextView d1 = tv(Hijri.weekdayAr(cal) + "، " + Hijri.gregorianAr(cal), 14, cream, false);
        d1.setGravity(Gravity.CENTER);
        hero.addView(d1, lp(-2, -2, 0));
        TextView d2 = tv(Hijri.hijriAr(hij), 14, gold, true);
        d2.setGravity(Gravity.CENTER);
        hero.addView(d2, lp(-2, -2, 0));

        // the next prayer
        PrayerCalc.Event ad = PrayerCalc.nextEvent(city, now, adj, new int[5]);
        shownNext = ad == null ? -1 : ad.index;
        TextView l1 = tv("الصلاة القادمة", 12, dim, true);
        l1.setPadding(0, dp(6), 0, 0);
        l1.setGravity(Gravity.CENTER);
        hero.addView(l1);
        nameTv = tv("", Math.round(30 * f), gold, true);
        nameTv.setGravity(Gravity.CENTER);
        hero.addView(nameTv);
        cdTv = tv("00:00:00", Math.round(44 * f), cream, true);
        cdTv.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        cdTv.setGravity(Gravity.CENTER);
        hero.addView(cdTv);
        altTv = tv("", 14, cream, true);
        altTv.setGravity(Gravity.CENTER);
        hero.addView(altTv, lp(-2, -2, 2));
        LinearLayout iq = hbox();
        iq.setBackground(shape(0x38000000, line, 14));
        iq.setPadding(dp(12), dp(6), dp(12), dp(6));
        iqLabel = tv("", 12, dim, false);
        iqValue = tv("", 18, gold, true);
        iq.setGravity(Gravity.CENTER);
        iqValue.setPadding(dp(10), 0, 0, 0);
        iq.addView(iqLabel);
        iq.addView(iqValue);
        hero.addView(iq, lp(-2, dp(Math.round(50 * f)), 6));
        add(hero, 8);

        // the six times: they share whatever height is left, so the page fills the screen
        long[] t = PrayerCalc.day(city, now, 0, adj);
        long sun = PrayerCalc.sunrise(city, now, 0, adj);
        int[] iqm = PrayerPrefs.iqama(this);
        boolean fri = PrayerCalc.isFriday(city, now);
        int[] order = {0, -1, 1, 2, 3, 4};
        for (int k = 0; k < order.length; k++) {
            int idx = order[k];
            long at = idx < 0 ? sun : t[idx];
            String name = idx < 0 ? getString(R.string.p_sunrise) : PrayerCalc.name(this, idx, fri);
            boolean next = idx >= 0 && idx == shownNext;
            LinearLayout r = hbox();
            r.setBackground(shape(next ? hl : card, next ? gold : 0, 16));
            r.setPadding(dp(14), dp(Math.round(8 * f)), dp(14), dp(Math.round(8 * f)));
            LinearLayout v = vbox();
            v.addView(tv(name, Math.round(18 * f), cream, true));
            if (idx >= 0 && iqm[idx] > 0) v.addView(tv(getString(R.string.iqama_minutes, iqm[idx]), 11, dim, false));
            r.addView(v, new LinearLayout.LayoutParams(0, -2, 1f));
            TextView tt = tv(ap(PrayerCalc.time(this, city, at)), Math.round(19 * f), gold, true);
            tt.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
            r.addView(tt);
            if (idx >= 0 && at <= now) {
                final int pi = idx;
                final long pat = at;
                final TextView ck = tv(Track.done(this, at, idx) ? "✓" : "○", 22, Track.done(this, at, idx) ? gold : dim, true);
                ck.setGravity(Gravity.CENTER);
                ck.setContentDescription("صليت");
                ck.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        boolean on = Track.toggle(PhoneActivity.this, pat, pi);
                        ck.setText(on ? "✓" : "○");
                        ck.setTextColor(on ? gold : dim);
                        PrayerNote.refresh(PhoneActivity.this);
                    }
                });
                LinearLayout.LayoutParams kp = new LinearLayout.LayoutParams(dp(44), dp(40));
                kp.setMarginStart(dp(6));
                r.addView(ck, kp);
            }
            if (at < now && !next) r.setAlpha(0.6f);
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2, 1f);
            rp.topMargin = dp(5);
            r.setGravity(Gravity.CENTER_VERTICAL);
            body.addView(r, rp);
        }

        // verse of the day
        String[] vt = getResources().getStringArray(R.array.verse_text);
        String[] vr = getResources().getStringArray(R.array.verse_ref);
        if (verseIdx < 0 || verseIdx >= vt.length) verseIdx = new java.util.Random().nextInt(vt.length);
        int vi = verseIdx;
        LinearLayout vc = vbox();
        vc.setBackground(shape(card, line, 18));
        vc.setPadding(dp(14), dp(Math.round(10 * f)), dp(14), dp(Math.round(10 * f)));
        TextView v1 = tv(vt[vi], Math.round(19 * f), cream, false);
        v1.setGravity(Gravity.CENTER);
        v1.setLineSpacing(0, 1.25f);
        TextView v2 = tv(vr[vi], 12, gold, true);
        v2.setGravity(Gravity.CENTER);
        vc.addView(v1);
        vc.addView(v2);
        add(vc, 8);
    }

    /** 1.0 on a tall screen; smaller on a short one, so the home page fits without scrolling. */
    private float fit() {
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        float availDp = (dm.heightPixels - homeTop - homeBottom) / dm.density - 50f;
        return Math.max(0.62f, Math.min(1f, availDp / 780f));
    }

    /** The times come with AM/PM; the phone is Arabic only. */
    private static String ap(String s) {
        return s.replace("AM", "ص").replace("PM", "م");
    }

    private TextView chip(String s) {
        TextView c = tv(s, 12, cream, true);
        c.setBackground(shape(card, line, 18));
        c.setPadding(dp(11), dp(5), dp(11), dp(5));
        return c;
    }

    private String extraLine(Cities.City city, long now, int[] adj) {
        long[] t = PrayerCalc.day(city, now, 0, adj);
        int firstMin = PrayerPrefs.firstAdhan(this);
        long firstAt = t[PrayerCalc.DHUHR] - firstMin * 60000L;
        if (PrayerCalc.isFriday(city, now) && firstMin > 0 && firstAt > now) {
            return getString(R.string.friday_first, PrayerCalc.time(this, city, firstAt));
        }
        boolean ram = Hijri.of(Hijri.today(this), Hijri.adjust(this))[1] == 9;
        if (ram && !(occNear != null && occNear[1] <= 1)) {
            return getString(R.string.ramadan_times,
                PrayerCalc.time(this, city, t[PrayerCalc.FAJR]), PrayerCalc.time(this, city, t[PrayerCalc.MAGHRIB]));
        }
        if (occNear != null) return Reminders.occLine(this, occNear);
        return null;
    }

    private void tick() {
        if (tab == 13) {
            for (Runnable r : new ArrayList<>(otherTick)) r.run();
            return;
        }
        if (tab != 0 || sub != null || clockTv == null) return;
        Cities.City city = Cities.selected(this);
        if (city == null) return;
        long now = System.currentTimeMillis();
        int[] adj = PrayerPrefs.adjust(this);
        Date d = new Date(now);
        clockTv.setText(new SimpleDateFormat("h:mm", Locale.US).format(d));
        apTv.setText(Calendar.getInstance().get(Calendar.AM_PM) == Calendar.AM ? "ص" : "م");
        PrayerCalc.Event ad = PrayerCalc.nextEvent(city, now, adj, new int[5]);
        PrayerCalc.Event ev = PrayerCalc.nextEvent(city, now, adj, PrayerPrefs.iqama(this));
        if (ad == null) return;
        if (ad.index != shownNext) {
            build();
            return;
        }
        nameTv.setText(PrayerCalc.name(this, ad.index, ad.friday));
        cdTv.setText(PrayerCalc.countdown(Math.max(0, ad.at - now)));
        String base = "الأذان " + ap(PrayerCalc.time(this, city, ad.at));
        String extra = extraLine(city, now, adj);
        if (extra != null) extra = ap(extra);
        altTv.setText(extra != null && (now / 3000L) % 2L == 1L ? extra : base);
        if (ev != null && ev.iqama) {
            iqLabel.setVisibility(View.VISIBLE);
            iqLabel.setText("الإقامة بعد");
            long s = Math.max(0, (ev.at - now) / 1000L);
            iqValue.setText(String.format(Locale.US, "%02d:%02d", s / 60, s % 60));
        } else {
            iqLabel.setText("");
            iqLabel.setVisibility(View.GONE);
            String[] dh = getResources().getStringArray(R.array.dhikr);
            iqValue.setText(dh[(int) ((now / 5000L) % dh.length)]);
        }
    }

    // ---------------------------------------------------------------- radio
    private void radio() {
        title("الراديو", false);
        if (!getSystemService(NotificationManager.class).areNotificationsEnabled()) {
            row("الإشعارات متوقفة", "بدونها لا يظهر الراديو في شريط الإشعارات ولا زر الإيقاف. اضغط لتفعيلها", "فتح", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (Build.VERSION.SDK_INT >= 33 && !granted(Manifest.permission.POST_NOTIFICATIONS)) {
                        requestPermissions(new String[] {Manifest.permission.POST_NOTIFICATIONS}, 12);
                    } else {
                        startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
                    }
                }
            });
        }
        for (int pass = 0; pass < 2; pass++) {
            section(pass == 0 ? "المحطات" : "القرّاء");
            for (int i = 0; i < RadioService.STATIONS.length; i++) {
                final int idx = i;
                RadioService.Station st = RadioService.STATIONS[i];
                boolean reciter = st.url.contains("qurango");
                if (reciter != (pass == 1)) continue;
                boolean now = RadioService.on() && RadioService.station() == i;
                row(st.name(true), now ? "يعمل الآن" : null, now ? "إيقاف" : "تشغيل", new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (RadioService.on() && RadioService.station() == idx) {
                            RadioService.stop(PhoneActivity.this);
                            sp.edit().putBoolean("radio_resume", false).apply();
                        } else {
                            RadioService.play(PhoneActivity.this, idx);
                        }
                    }
                });
            }
        }
        final int db = RadioService.boostDb(this);
        final int[] steps = {0, 6, 9, 12, 15};
        final String[] names = {"بدون رفع", "متوسط (+6)", "عالٍ (+9)", "أعلى (+12)", "أقصى (+15)"};
        int at = 2;
        for (int i = 0; i < steps.length; i++) if (steps[i] == db) at = i;
        final int cur = at;
        row("رفع صوت الإذاعات", names[cur] + " · يطبّق فورًا", "›", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new Pop(PhoneActivity.this).title("رفع صوت الإذاعات").single(names, cur, new Pop.Pick() {
                    @Override
                    public void on(int which) {
                        sp.edit().putInt("radio_boost", steps[which]).apply();
                        RadioService.refreshBoost();
                        build();
                    }
                }).show();
            }
        });
        TextView n = tv("يتوقف الراديو وقت الأذان ويعود وحده، ويرجع بعد إعادة التشغيل أو التحديث.", 12, dim, false);
        add(n, 12);
    }

    // ---------------------------------------------------------------- reminders
    private String itemBack;

    private void reminders() {
        final List<Reminders.Item> list = Reminders.load(this);
        if (sub == null) {
            // blank custom reminders (made by pressing "new" and leaving) are dropped
            boolean dropped = false;
            for (int i = list.size() - 1; i >= 0; i--) {
                Reminders.Item b = list.get(i);
                if (b.kind == Reminders.CUSTOM && b.title.trim().isEmpty() && b.text.trim().isEmpty()) {
                    list.remove(i);
                    dropped = true;
                }
            }
            if (dropped) saveRem(list);
            title("التذكيرات", false);
            row("الصيام", null, "›", go("fast"));
            row("الأذكار", null, "›", go("dhikr"));
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).kind == Reminders.KAHF) itemRow(list, i, null);
            }
            row("المناسبات الهجرية", null, "›", go("occ"));
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).kind == Reminders.CUSTOM) itemRow(list, i, null);
            }
            row("تذكير مخصص جديد", null, "+", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    list.add(Reminders.newCustom());
                    saveRem(list);
                    itemBack = null;
                    sub = "item:" + (list.size() - 1);
                    build();
                }
            });
            return;
        }
        if (sub.equals("fast") || sub.equals("dhikr") || sub.equals("occ")) {
            title(sub.equals("fast") ? "الصيام" : sub.equals("dhikr") ? "الأذكار" : "المناسبات الهجرية", true);
            for (int i = 0; i < list.size(); i++) {
                int k = list.get(i).kind;
                boolean in = sub.equals("fast") ? Reminders.isFast(k)
                    : sub.equals("dhikr") ? (k == Reminders.ADHKAR_AM || k == Reminders.ADHKAR_PM)
                    : k == Reminders.OCCASION;
                if (in) itemRow(list, i, sub);
            }
            if (sub.equals("occ")) row("المناسبات القادمة", null, "›", go("up"));
            return;
        }
        if (sub.equals("up")) {
            title("المناسبات القادمة", true);
            boolean ar = true;
            for (int[] u : Reminders.upcoming(this, 12)) {
                Calendar c = Hijri.today(this);
                c.add(Calendar.DAY_OF_MONTH, u[1]);
                String date = Hijri.weekdayAr(c) + " " + Hijri.gregorianAr(c);
                String left = u[1] == 0 ? "اليوم" : u[1] == 1 ? "غدًا" : "بعد " + u[1] + " يوم";
                row(Reminders.occName(this, u[0]), date, left, null);
            }
            return;
        }
        if (sub.startsWith("item:")) editItem(list, Integer.parseInt(sub.substring(5)));
    }

    private View.OnClickListener go(final String s) {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sub = s;
                build();
            }
        };
    }

    private void saveRem(List<Reminders.Item> list) {
        Reminders.save(this, list);
        Reminders.scheduleNext(this);
    }

    /** A reminder in a list: its name and an on/off switch; tapping the name opens its settings. */
    private void itemRow(final List<Reminders.Item> list, final int i, final String back) {
        final Reminders.Item it = list.get(i);
        LinearLayout r = row(Reminders.title(this, it), null, null, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                itemBack = back;
                sub = "item:" + i;
                build();
            }
        });
        Switch s = new Switch(this);
        s.setChecked(it.enabled);
        s.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(android.widget.CompoundButton b, boolean on) {
                it.enabled = on;
                saveRem(list);
            }
        });
        r.addView(s);
    }

    private String daysText(int mask) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 7; i++) {
            if ((mask & (1 << i)) != 0) {
                if (b.length() > 0) b.append("، ");
                b.append(Hijri.dayNameAr(i));
            }
        }
        return b.length() == 0 ? "لا شيء" : b.toString();
    }

    private void editItem(final List<Reminders.Item> list, final int i) {
        if (i >= list.size()) {
            sub = null;
            build();
            return;
        }
        final Reminders.Item it = list.get(i);
        title(Reminders.title(this, it), true);
        switchRow("التفعيل", null, it.enabled, new CompoundListener() {
            @Override
            public void changed(boolean on) {
                it.enabled = on;
                saveRem(list);
            }
        });
        if (it.kind == Reminders.CUSTOM) {
            row("العنوان", Reminders.title(this, it), "›", typeText("عنوان التذكير", it.title, new Setter() {
                @Override
                public void set(String v) {
                    it.title = v;
                    saveRem(list);
                }
            }));
            row("النص", it.text.isEmpty() ? "بدون" : it.text, "›", typeText("نص التذكير", it.text, new Setter() {
                @Override
                public void set(String v) {
                    it.text = v;
                    saveRem(list);
                }
            }));
        }
        if (it.kind == Reminders.CUSTOM || it.kind == Reminders.ADHKAR_AM || it.kind == Reminders.ADHKAR_PM
            || it.kind == Reminders.WEEK_FAST || it.kind == Reminders.KAHF) {
            row(it.kind == Reminders.WEEK_FAST ? "أيام الصيام" : "الأيام", daysText(it.daysMask), "›",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        pickDays(list, it, 7, false);
                    }
                });
        }
        if (it.kind == Reminders.WHITE_FAST) {
            StringBuilder b = new StringBuilder();
            for (int k = 0; k < 3; k++) if ((it.hijriMask & (1 << k)) != 0) b.append(b.length() > 0 ? "، " : "").append(13 + k);
            row("أيام الشهر الهجري", b.length() == 0 ? "لا شيء" : b.toString(), "›", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    pickDays(list, it, 3, true);
                }
            });
        }
        if (Reminders.isFast(it.kind) || it.kind == Reminders.OCCASION) {
            final boolean occ = it.kind == Reminders.OCCASION;
            switchRow(occ ? "التنبيه قبل المناسبة بيوم" : "التنبيه قبل يوم الصيام بيوم", null, it.dayBefore,
                new CompoundListener() {
                    @Override
                    public void changed(boolean on) {
                        it.dayBefore = on;
                        saveRem(list);
                    }
                });
        }
        final boolean linked = it.relMin >= 0 && (it.kind == Reminders.ADHKAR_AM || it.kind == Reminders.ADHKAR_PM);
        if (it.kind == Reminders.ADHKAR_AM || it.kind == Reminders.ADHKAR_PM) {
            final boolean am = it.kind == Reminders.ADHKAR_AM;
            row("وقت التنبيه", linked ? (am ? "بعد الفجر بـ " : "قبل المغرب بـ ") + it.relMin + " دقيقة" : "وقت ثابت", "›",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        new Pop(PhoneActivity.this).title("وقت التنبيه")
                            .single(new String[] {"وقت ثابت", am ? "مرتبط بالفجر" : "مرتبط بالمغرب"}, linked ? 1 : 0,
                                new Pop.Pick() {
                                    @Override
                                    public void on(int which) {
                                        it.relMin = which == 1 ? (it.relMin >= 0 ? it.relMin : 20) : -1;
                                        saveRem(list);
                                        build();
                                    }
                                }).show();
                    }
                });
        }
        if (linked) {
            final boolean am2 = it.kind == Reminders.ADHKAR_AM;
            row(am2 ? "الدقائق بعد الفجر" : "الدقائق قبل المغرب", it.relMin + " دقيقة", "›", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    numberDialog(am2 ? "دقائق بعد الفجر" : "دقائق قبل المغرب", 0, 120, it.relMin, new IntSetter() {
                        @Override
                        public void set(int val) {
                            it.relMin = val;
                            saveRem(list);
                            build();
                        }
                    });
                }
            });
        } else row("الوقت", String.format(Locale.US, "%02d:%02d", it.hour, it.minute), "›", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                timeDialog(it.hour, it.minute, new TimeSetter() {
                    @Override
                    public void set(int hour, int minute) {
                        it.hour = hour;
                        it.minute = minute;
                        saveRem(list);
                        build();
                    }
                });
            }
        });
        row("تذكير مرة ثانية", it.again == 0 ? "مرة واحدة فقط" : "يتكرر بعد " + it.again + " دقيقة", "›",
            new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    numberDialog("يتكرر التذكير بعد كم دقيقة (0 = مرة واحدة)", 0, 240, it.again, new IntSetter() {
                        @Override
                        public void set(int val) {
                            it.again = val;
                            saveRem(list);
                            build();
                        }
                    });
                }
            });
        row("مدة بقاء الإشعار", it.durationMin == 0 ? "حتى أغلقه" : it.durationMin + " دقيقة", "›",
            new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    numberDialog("دقائق بقاء الإشعار (0 = حتى أغلقه)", 0, 240, it.durationMin, new IntSetter() {
                        @Override
                        public void set(int val) {
                            it.durationMin = val;
                            saveRem(list);
                            build();
                        }
                    });
                }
            });
        if (it.kind == Reminders.CUSTOM) {
            row("حذف هذا التذكير", null, null, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    list.remove(i);
                    saveRem(list);
                    sub = null;
                    build();
                }
            });
        }
    }

    private interface Setter {
        void set(String v);
    }

    private interface IntSetter {
        void set(int v);
    }

    private View.OnClickListener typeText(final String title, final String cur, final Setter s) {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final EditText in = new EditText(PhoneActivity.this);
                in.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
                in.setText(cur);
                in.setTextColor(cream);
                in.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
                new Pop(PhoneActivity.this).title(title).view(in).ok("حفظ", new Runnable() {
                    @Override
                    public void run() {
                        s.set(in.getText().toString().trim());
                        build();
                    }
                }).cancel("إلغاء").show();
            }
        };
    }

    private void pickDays(final List<Reminders.Item> list, final Reminders.Item it, final int n, final boolean white) {
        final boolean[] on = new boolean[n];
        String[] names = new String[n];
        int mask = white ? it.hijriMask : it.daysMask;
        for (int k = 0; k < n; k++) {
            names[k] = white ? (13 + k) + " من الشهر الهجري" : Hijri.dayNameAr(k);
            on[k] = (mask & (1 << k)) != 0;
        }
        new Pop(this).title("اختر الأيام").multi(names, on).ok("حفظ", new Runnable() {
            @Override
            public void run() {
                int m = 0;
                for (int k = 0; k < n; k++) if (on[k]) m |= 1 << k;
                if (white) it.hijriMask = m;
                else it.daysMask = m;
                saveRem(list);
                build();
            }
        }).cancel("إلغاء").show();
    }

    private static String[] labels(int min, int max) {
        String[] l = new String[max - min + 1];
        for (int v = min; v <= max; v++) l[v - min] = (v > 0 && min < 0 ? "+" : "") + v;
        return l;
    }

    private void numberDialog(String title, int min, int max, int cur, final IntSetter s) {
        final NumberPicker np = new NumberPicker(this);
        // NumberPicker does not accept negative limits: it counts from 0 and shows the real numbers as labels.
        final int off = min;
        np.setMinValue(0);
        np.setMaxValue(max - min);
        np.setDisplayedValues(labels(min, max));
        np.setValue(Math.max(min, Math.min(max, cur)) - off);
        np.setWrapSelectorWheel(false);
        FrameLayout f = new FrameLayout(this);
        f.addView(np, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        new Pop(this).title(title).view(f).ok("حفظ", new Runnable() {
            @Override
            public void run() {
                s.set(np.getValue() + off);
            }
        }).cancel("إلغاء").show();
    }

    private interface TimeSetter {
        void set(int hour, int minute);
    }

    private void timeDialog(int hour, int minute, final TimeSetter s) {
        final NumberPicker hp = new NumberPicker(this);
        hp.setMinValue(0);
        hp.setMaxValue(23);
        hp.setValue(hour);
        hp.setFormatter(new NumberPicker.Formatter() {
            @Override
            public String format(int v) {
                return String.format(Locale.US, "%02d", v);
            }
        });
        final NumberPicker mp = new NumberPicker(this);
        mp.setMinValue(0);
        mp.setMaxValue(59);
        mp.setValue(minute);
        mp.setFormatter(new NumberPicker.Formatter() {
            @Override
            public String format(int v) {
                return String.format(Locale.US, "%02d", v);
            }
        });
        LinearLayout l = hbox();
        l.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        l.setGravity(Gravity.CENTER);
        l.addView(hp, new LinearLayout.LayoutParams(dp(80), -2));
        TextView colon = tv(":", 26, gold, true);
        colon.setPadding(dp(8), 0, dp(8), 0);
        l.addView(colon);
        l.addView(mp, new LinearLayout.LayoutParams(dp(80), -2));
        new Pop(this).title("الوقت").view(l).ok("حفظ", new Runnable() {
            @Override
            public void run() {
                s.set(hp.getValue(), mp.getValue());
            }
        }).cancel("إلغاء").show();
    }

    // ---------------------------------------------------------------- settings
    private void settings() {
        if ("country".equals(sub) || "cities".equals(sub) || "find".equals(sub)) {
            cityPicker();
            return;
        }
        if ("fix".equals(sub)) {
            fixPage();
            return;
        }
        if ("about".equals(sub)) {
            aboutPage();
            return;
        }
        if (sub != null && sub.startsWith("txt:")) {
            textPage(Integer.parseInt(sub.substring(4)));
            return;
        }
        if ("backup".equals(sub)) {
            backupPage();
            return;
        }
        if ("tv".equals(sub)) {
            tvPage();
            return;
        }
        if ("tvkeys".equals(sub)) {
            tvKeysPage();
            return;
        }
        title("الإعدادات", false);
        section("الشكل");
        switchRow("ظلال وعمق البطاقات", "تعطي إحساس البروز ثلاثي الأبعاد", sp.getBoolean("depth_on", true),
            new CompoundListener() {
                @Override
                public void changed(boolean on) {
                    sp.edit().putBoolean("depth_on", on).apply();
                    build();
                }
            });
        switchRow("الشاشة الافتتاحية", "تظهر نحو 5 ثوانٍ كلما فتحت التطبيق من أي مكان", sp.getBoolean("splash_on", true),
            new CompoundListener() {
                @Override
                public void changed(boolean on) {
                    sp.edit().putBoolean("splash_on", on).apply();
                }
            });
        final int forced = sp.getInt("look_force", -1);
        final String[] looks = {"تلقائي (بحسب وضع الهاتف)", "نهاري (أخضر)", "ليلي (كحلي)"};
        row("شكل التطبيق", looks[forced + 1], "›", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new Pop(PhoneActivity.this).title("شكل التطبيق").single(looks, forced + 1, new Pop.Pick() {
                    @Override
                    public void on(int which) {
                        sp.edit().putInt("look_force", which - 1).apply();
                        look = lookNow();
                        palette();
                        root();
                        build();
                    }
                }).show();
            }
        });
        section("حجم الخط ولونه");
        for (int i = 0; i < Adhkar.TEXT_PLACES.length; i++) {
            row(Adhkar.TEXT_PLACES[i], Adhkar.SIZE_NAMES[Adhkar.sizeIdx(this, i)] + " · " + Adhkar.COLOR_NAMES[Adhkar.colorIdx(this, i)],
                "›", go("txt:" + i));
        }
        section("التشغيل في الخلفية");
        row("تأكد أن الأذان يعمل", "الإشعارات والمنبّه والبطارية", "›", go("fix"));
        row("العرض فوق التطبيقات", "لتظهر الأذكار فوق التطبيقات الأخرى", Settings.canDrawOverlays(this) ? "✓" : "فتح",
            new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!Settings.canDrawOverlays(PhoneActivity.this)) openOverlayPermission();
                }
            });
        section("أخرى");
        row("تحديث التطبيق", "النسخة " + PhoneUpdater.installedName(this), "›", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                PhoneUpdater.check(PhoneActivity.this);
            }
        });
        row("اللانشر على التلفزيون", "تثبيته وتعيينه شاشة رئيسية من هذا الهاتف", "›", go("tv"));
        row("نسخة احتياطية", "احفظ إعداداتك وأذكارك في ملف، واستعدها بعد أي حذف", "›", go("backup"));
        row("عن التطبيق", "المصادر والتشخيص", "›", go("about"));
    }

    private float tsc(int place) {
        return Adhkar.scale(this, place);
    }

    private int tsz(int place, int base) {
        return Math.round(base * tsc(place));
    }

    private int tcl(int place, int def) {
        return Adhkar.color(this, place, def, gold);
    }

    /** Size and colour of the text of one place (the adhkar, the tasbih page, the card over other apps). */
    private void textPage(final int place) {
        title(Adhkar.TEXT_PLACES[place], true);
        LinearLayout pv = vbox();
        pv.setBackground(shape(card, line, 16));
        pv.setPadding(dp(14), dp(16), dp(14), dp(16));
        TextView t = tv("سبحان الله وبحمده، سبحان الله العظيم", tsz(place, 18), tcl(place, cream), true);
        t.setGravity(Gravity.CENTER);
        t.setLineSpacing(0, 1.3f);
        pv.addView(t);
        add(pv, 6);
        section("الحجم");
        for (int i = 0; i < Adhkar.SIZE_NAMES.length; i++) {
            final int k = i;
            row(Adhkar.SIZE_NAMES[i], null, Adhkar.sizeIdx(this, place) == i ? "✓" : null, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    sp.edit().putInt("txt_size_" + place, k).apply();
                    build();
                }
            });
        }
        section("اللون");
        for (int i = 0; i < Adhkar.COLOR_NAMES.length; i++) {
            final int k = i;
            row(Adhkar.COLOR_NAMES[i], null, Adhkar.colorIdx(this, place) == i ? "✓" : null, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    sp.edit().putInt("txt_col_" + place, k).apply();
                    build();
                }
            });
        }
        add(tv("يتغير حجم الخط ولونه في هذا المكان فقط. الألوان الفاتحة مناسبة للشكل الليلي، وللشكل النهاري جرّب الأبيض أو الأصفر.", 12, dim, false), 10);
    }

    private void backupPage() {
        title("نسخة احتياطية", true);
        add(tv("يحفظ كل ما ضبطته في ملف واحد: المدينة، إعدادات الأذان والإقامة والتذكيرات، الأذكار التي أضفتها أو عدّلتها، قائمة الأذكار فوق التطبيقات، العدّادات، والشكل. احفظ الملف في مكان خارج التطبيق (التنزيلات أو Drive أو أرسله لنفسك)، فيبقى حتى لو حذفت التطبيق.", 13, dim, false), 4);
        add(button("حفظ نسخة احتياطية", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("application/json")
                    .putExtra(Intent.EXTRA_TITLE, "salat-khair-" + new SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(new Date()) + ".json");
                try {
                    startActivityForResult(i, REQ_SAVE);
                } catch (RuntimeException e) {
                    toast("تعذّر فتح اختيار الملفات");
                }
            }
        }), 12);
        add(button("استعادة من نسخة", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
                try {
                    startActivityForResult(i, REQ_LOAD);
                } catch (RuntimeException e) {
                    toast("تعذّر فتح اختيار الملفات");
                }
            }
        }), 8);
        add(tv("الاستعادة تستبدل إعداداتك الحالية بما في الملف. بعد التثبيت على هاتف جديد أو بعد حذف التطبيق: ثبّته، ثم «استعادة من نسخة».", 12, dim, false), 10);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null || data.getData() == null) return;
        android.net.Uri u = data.getData();
        try {
            if (req == REQ_SAVE) {
                java.io.OutputStream o = getContentResolver().openOutputStream(u, "wt");
                o.write(Backup.export(this).getBytes("UTF-8"));
                o.close();
                toast("تم حفظ النسخة الاحتياطية");
            } else if (req == REQ_LOAD) {
                java.io.InputStream in = getContentResolver().openInputStream(u);
                java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
                in.close();
                int k = Backup.restore(this, bo.toString("UTF-8"));
                if (k < 0) {
                    toast("هذا الملف ليس نسخة من هذا التطبيق");
                    return;
                }
                PrayerCalc.configure(this);
                Scheduler.scheduleNext(this);
                Reminders.scheduleNext(this);
                OverlayService.sync(this);
                PrayerNote.refresh(this);
                toast("تمت الاستعادة (" + k + " إعداد)");
                occDay = "";
                weather = "";
                recreate();
            }
        } catch (Exception e) {
            toast("تعذّر: " + e.getClass().getSimpleName());
        }
    }

    private void aboutPage() {
        title("عن التطبيق", true);
        TextView head = tv("الصلاة خير", 22, gold, true);
        head.setGravity(Gravity.CENTER);
        add(head, 6);
        TextView ver = tv("النسخة " + PhoneUpdater.installedName(this), 13, dim, false);
        ver.setGravity(Gravity.CENTER);
        add(ver, 2);
        section("المصادر");
        add(tv("أوقات الصلاة: مكتبة adhan-java (رخصة MIT).\n"
            + "الأذكار: حصن المسلم، من ملف rn0x/Adhkar-json.\n"
            + "الطقس: Open-Meteo. أسماء الأحياء: OpenStreetMap. الدول والمحافظات: dr5hn (ODbL). البحث عن مكان: Open-Meteo Geocoding.\n"
            + "الإذاعات والقرّاء: بثّ مواقعهم العامة، والتطبيق غير تابع لأي منها.\n"
            + "تسجيلات الأذان والإقامة من اختيار صاحب التطبيق للاستعمال الشخصي.", 12, dim, false), 4);
        section("التشخيص");
        long rx = sp.getLong("dbg_rx", 0L);
        row("آخر وصول لمنبّه الأذان", rx == 0 ? "لم يصل أي منبّه بعد" : new SimpleDateFormat("dd/MM HH:mm:ss", Locale.US).format(new Date(rx)), null, null);
        String err = sp.getString("dbg_err", "");
        row("آخر خطأ في تشغيل الأذان", err.isEmpty() ? "لا يوجد" : err, null, null);
        String rl = sp.getString("radio_log", "");
        if (!rl.isEmpty()) {
            TextView t = tv("سجل الراديو:\n" + rl, 10, dim, false);
            t.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
            t.setTextIsSelectable(true);
            add(t, 8);
        }
        final String crash = sp.getString("dbg_crash", "");
        if (crash.isEmpty()) {
            row("آخر انهيار", "لا يوجد", null, null);
        } else {
            TextView ct = tv("آخر انهيار:\n" + crash, 10, dim, false);
            ct.setTextIsSelectable(true);
            ct.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
            add(ct, 8);
            add(button("نسخ تفاصيل الانهيار", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    android.content.ClipboardManager cm = getSystemService(android.content.ClipboardManager.class);
                    if (cm != null) cm.setPrimaryClip(android.content.ClipData.newPlainText("crash", crash));
                    toast("تم النسخ");
                }
            }), 8);
            add(button("مسح", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    sp.edit().remove("dbg_crash").apply();
                    build();
                }
            }), 6);
        }
    }

    private String country = "EG";
    private String countryName = "مصر";
    private LinearLayout pickList;

    private EditText searchBox(String hint) {
        EditText search = new EditText(this);
        search.setHint(hint);
        search.setHintTextColor(dim);
        search.setTextColor(cream);
        search.setSingleLine(true);
        search.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        search.setBackground(shape(card, line, 14));
        search.setPadding(dp(14), dp(10), dp(14), dp(10));
        return search;
    }

    /** Picks a place by hand: any country, and the cities, provinces or governorates of it, or a search for a neighbourhood. */
    private void cityPicker() {
        if ("find".equals(sub)) {
            findPage();
            return;
        }
        if ("country".equals(sub)) {
            title("اختر الدولة", true);
            row("ابحث باسم مدينتك أو حيّك", "في أي مكان بالعالم، ويحتاج إنترنت", "›", go("find"));
            final EditText search = searchBox("ابحث عن دولة");
            add(search, 8);
            pickList = vbox();
            add(pickList, 2);
            fillCountries("");
            search.addTextChangedListener(new android.text.TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence x, int a, int b, int c) {
                }

                @Override
                public void onTextChanged(CharSequence x, int a, int b, int c) {
                    fillCountries(x.toString());
                }

                @Override
                public void afterTextChanged(android.text.Editable e) {
                }
            });
            return;
        }
        title(countryName, true);
        final List<Cities.City> arr = new ArrayList<>();
        if (country.equals("EG")) arr.addAll(java.util.Arrays.asList(Cities.EGYPT));
        else if (country.equals("SA")) arr.addAll(java.util.Arrays.asList(Cities.SAUDI));
        else arr.addAll(World.places(this, country));
        if (arr.isEmpty()) {
            add(tv("لا توجد مدن مسجّلة لهذه الدولة، استخدم البحث باسم مدينتك.", 14, dim, false), 8);
            row("ابحث باسم مدينتك أو حيّك", null, "›", go("find"));
            return;
        }
        for (final Cities.City c : arr) {
            row(c.name(true), null, null, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    pickPlace(c);
                }
            });
        }
        row("لم تجد مدينتك؟ ابحث بالاسم", null, "›", go("find"));
    }

    private void fillCountries(String q) {
        pickList.removeAllViews();
        q = q.trim();
        for (final String[] c : World.countries(this)) {
            if (!q.isEmpty() && !c[1].contains(q)) continue;
            LinearLayout r = hbox();
            r.setBackground(shape(card, line, 14));
            r.setPadding(dp(14), dp(10), dp(14), dp(10));
            r.setMinimumHeight(dp(48));
            r.addView(tv(c[1], 16, cream, false), new LinearLayout.LayoutParams(0, -2, 1f));
            r.addView(tv("›", 16, gold, true));
            r.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    country = c[0];
                    countryName = c[1];
                    sub = "cities";
                    build();
                }
            });
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
            rp.topMargin = dp(6);
            pickList.addView(r, rp);
        }
        if (pickList.getChildCount() == 0) pickList.addView(tv("لا توجد نتائج", 14, dim, false));
    }

    /** A place chosen by hand from a list. */
    private void pickPlace(Cities.City c) {
        if (addingOther) {
            addOther(c);
            return;
        }
        if (country.equals("EG") || country.equals("SA")) {
            applyCity(c);
        } else {
            sp.edit().putString("city", c.country + "|" + c.en).putString("city_custom",
                c.ar + "|" + c.lat + "|" + c.lon + "|" + (c.tz == null ? "" : c.tz)).apply();
            afterCity();
        }
        leavePicker();
    }

    private void leavePicker() {
        tab = 0;
        sub = null;
        build();
    }

    private LinearLayout findList;

    private void findPage() {
        title("ابحث عن مدينتك", true);
        add(tv("اكتب اسم المدينة أو الحي أو القرية، فتظهر الأماكن المطابقة من أي دولة.", 13, dim, false), 4);
        final EditText q = searchBox("مثال: المعادي، أو Dubai");
        add(q, 8);
        findList = vbox();
        add(button("بحث", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final String text = q.getText().toString().trim();
                if (text.length() < 2) return;
                findList.removeAllViews();
                findList.addView(tv("جارٍ البحث…", 14, dim, false));
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        final List<Place.Hit> hits = Place.search(text);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (findList == null || !"find".equals(sub)) return;
                                findList.removeAllViews();
                                if (hits.isEmpty()) {
                                    findList.addView(tv("لا نتائج. تأكد من الاسم ومن الاتصال بالإنترنت.", 14, dim, false));
                                }
                                for (final Place.Hit hit : hits) {
                                    LinearLayout r = vbox();
                                    r.setBackground(shape(card, line, 14));
                                    r.setPadding(dp(14), dp(10), dp(14), dp(10));
                                    r.addView(tv(hit.name, 16, cream, true));
                                    r.addView(tv(hit.detail, 12, dim, false));
                                    r.setOnClickListener(new View.OnClickListener() {
                                        @Override
                                        public void onClick(View v2) {
                                            if (addingOther) {
                                                Cities.City cc = new Cities.City(hit.country, hit.name, hit.name, hit.lat, hit.lon);
                                                if (hit.tz != null && !hit.tz.isEmpty()) cc.tz = hit.tz;
                                                addOther(cc);
                                                return;
                                            }
                                            sp.edit().putString("city", hit.country + "|" + hit.name).putString("city_custom",
                                                hit.name + "|" + hit.lat + "|" + hit.lon + "|" + hit.tz).apply();
                                            afterCity();
                                            leavePicker();
                                        }
                                    });
                                    LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
                                    rp.topMargin = dp(6);
                                    findList.addView(r, rp);
                                }
                            }
                        });
                    }
                }, "find").start();
            }
        }), 8);
        add(findList, 4);
    }

    private static final String[] METHOD_NAMES = {"رابطة العالم الإسلامي", "الهيئة المصرية العامة للمساحة", "أم القرى",
        "جامعة العلوم الإسلامية بكراتشي", "أمريكا الشمالية", "الكويت", "قطر"};

    private void calcChanged() {
        PrayerCalc.configure(this);
        Scheduler.scheduleNext(this);
        Reminders.scheduleNext(this);
        PrayerNote.refresh(this);
        occDay = "";
        build();
    }

    private void adhanPage() {
        title("الأذان والإقامة", false);
        section("وضع الخلاء");
        switchRow("وضع الخلاء", "يوقف الأذان والإقامة والأذكار والتذكيرات والإشعارات والراديو بضغطة، ويرجعها بضغطة ثانية. يتوقف وحده بعد 30 دقيقة لو نسيته. الزر أيضاً في الويدجت", Wc.on(this),
            new CompoundListener() {
                @Override
                public void changed(boolean on) {
                    if (on != Wc.on(PhoneActivity.this)) Wc.set(PhoneActivity.this, on, true);
                    build();
                }
            });
        section("طريقة الحساب");
        final int m = sp.getInt("calc_method", -1);
        switchRow("تلقائي حسب الموقع", "يختار الطريقة والمذهب حسب بلد مدينتك", m < 0, new CompoundListener() {
            @Override
            public void changed(boolean on) {
                Cities.City c = Cities.selected(PhoneActivity.this);
                int def = c != null && "EG".equals(c.country) ? 1 : 2;
                SharedPreferences.Editor e = sp.edit();
                if (on) e.putInt("calc_method", -1).putBoolean("calc_hanafi", false);
                else e.putInt("calc_method", def);
                e.apply();
                calcChanged();
            }
        });
        if (m >= 0) {
            row("طريقة الحساب", METHOD_NAMES[Math.min(m, METHOD_NAMES.length - 1)], "›", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    new Pop(PhoneActivity.this).title("طريقة الحساب").single(METHOD_NAMES, m, new Pop.Pick() {
                        @Override
                        public void on(int which) {
                            sp.edit().putInt("calc_method", which).apply();
                            calcChanged();
                        }
                    }).show();
                }
            });
            final boolean han = sp.getBoolean("calc_hanafi", false);
            row("المذهب (وقت العصر)", han ? "الحنفي" : "الجمهور (الشافعي والمالكي والحنبلي)", "›", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    new Pop(PhoneActivity.this).title("المذهب (وقت العصر)")
                        .single(new String[] {"الجمهور (الشافعي والمالكي والحنبلي)", "الحنفي"}, han ? 1 : 0, new Pop.Pick() {
                            @Override
                            public void on(int which) {
                                sp.edit().putBoolean("calc_hanafi", which == 1).apply();
                                calcChanged();
                            }
                        }).show();
                }
            });
        }
        section("لكل صلاة");
        final int[] adj = PrayerPrefs.adjust(this);
        final int[] iq = PrayerPrefs.iqama(this);
        for (int i = 0; i < 5; i++) {
            final int idx = i;
            row(PrayerCalc.name(this, i, false), "الإقامة: " + PrayerPrefs.iqamaText(this, iq[i])
                + " · التعديل: " + PrayerPrefs.adjustText(this, adj[i])
                + " · " + new String[] {"صوت", "اهتزاز", "صامت"}[Math.max(0, Math.min(2, sp.getInt("ad_mode_" + i, 0)))], "›", new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        prayerDialog(idx);
                    }
                });
        }
        row("الأذان الأول للجمعة", PrayerPrefs.firstAdhanText(this, PrayerPrefs.firstAdhan(this)), "›",
            new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    numberDialog("دقائق قبل أذان الجمعة (0 = متوقف)", 0, 60, PrayerPrefs.firstAdhan(PhoneActivity.this),
                        new IntSetter() {
                            @Override
                            public void set(int val) {
                                PrayerPrefs.setFirstAdhan(PhoneActivity.this, val);
                                Scheduler.scheduleNext(PhoneActivity.this);
                                build();
                            }
                        });
                }
            });
        section("الإشعار والصامت");
        switchRow("إشعار الصلاة الدائم", "عدّاد للأذان القادم، وخلال الإقامة يعدّ للإقامة", PrayerNote.noteOn(this),
            new CompoundListener() {
                @Override
                public void changed(boolean on) {
                    sp.edit().putBoolean("note_on", on).apply();
                    PrayerNote.refresh(PhoneActivity.this);
                }
            });
        switchRow("دعاء بعد الأذان", "نافذة بدعاء ما بعد الأذان فوق التطبيقات، تُقفل بزر «تم» أو بالضغط خارجها (وإشعار لو إذن العرض فوق التطبيقات غير مفعّل)", sp.getBoolean("ad_dua", true),
            new CompoundListener() {
                @Override
                public void changed(boolean on) {
                    sp.edit().putBoolean("ad_dua", on).apply();
                }
            });
        switchRow("أقم الصلاة", "كل فترة تحددها، لو فيه صلوات جاء وقتها ولم تعلّمها ✓ تظهر نافذة فوق التطبيقات لا تختفي إلا بزر OK، وتوقف الفيديو أو الصوت الشغّال. لا تظهر بعد منتصف الليل", PrayerNote.missedOn(this),
            new CompoundListener() {
                @Override
                public void changed(boolean on) {
                    sp.edit().putBoolean("qm_on", on).apply();
                    PrayerNote.refresh(PhoneActivity.this);
                    build();
                }
            });
        if (PrayerNote.missedOn(this)) {
            row("تكرار التذكير", "كل " + PrayerNote.missedMin(this) + " دقيقة، ما دامت هناك صلوات لم تُعلَّم", "›", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    numberDialog("التذكير كل كم دقيقة", 5, 240, PrayerNote.missedMin(PhoneActivity.this), new IntSetter() {
                        @Override
                        public void set(int val) {
                            sp.edit().putInt("qm_min", val).putLong("qm_next", 0L).apply();
                            PrayerNote.refresh(PhoneActivity.this);
                            build();
                        }
                    });
                }
            });
        }
        switchRow("رفع صوت الأذان تدريجياً", "يبدأ هادئاً ويعلو خلال 15 ثانية حتى مستوى صوت المنبّه في الهاتف", sp.getBoolean("ad_ramp", false),
            new CompoundListener() {
                @Override
                public void changed(boolean on) {
                    sp.edit().putBoolean("ad_ramp", on).apply();
                }
            });
        final boolean silent = PrayerNote.silentOn(this);
        switchRow("الصامت وقت الصلاة", "يحوّل الهاتف لصامت بعد انتهاء الأذان، ويرجعه بعد الإقامة", silent,
            new CompoundListener() {
                @Override
                public void changed(boolean on) {
                    sp.edit().putBoolean("silent_on", on).apply();
                    PrayerNote.refresh(PhoneActivity.this);
                    build();
                }
            });
        if (silent) {
            row("يرجع الصوت بعد الإقامة بـ", PrayerNote.silentAfter(this) + " دقيقة", "›", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    numberDialog("دقائق بعد الإقامة حتى يرجع الصوت", 0, 90, PrayerNote.silentAfter(PhoneActivity.this),
                        new IntSetter() {
                            @Override
                            public void set(int val) {
                                sp.edit().putInt("silent_after", val).apply();
                                PrayerNote.refresh(PhoneActivity.this);
                                build();
                            }
                        });
                }
            });
            if (!PrayerNote.policyGranted(this)) {
                row("السماح بالتحكم في الصامت", "مطلوب مرة واحدة: فعّل «الصلاة خير» في القائمة", "فتح",
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            try {
                                startActivity(new Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS));
                            } catch (RuntimeException e) {
                                toast("افتح إعدادات الهاتف ← عدم الإزعاج ← الوصول");
                            }
                        }
                    });
            }
        }
        section("تنبيه قبل خروج الوقت");
        for (int i = 0; i < 5; i++) {
            final int idx = i;
            int wm = PrayerNote.warnMin(this, i);
            row(PrayerCalc.name(this, i, false) + " — قبل " + PrayerNote.END_NAMES[i], wm == 0 ? "متوقف" : wm + " دقيقة", "›",
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        numberDialog("دقائق قبل خروج الوقت (0 = متوقف)", 0, 180, PrayerNote.warnMin(PhoneActivity.this, idx),
                            new IntSetter() {
                                @Override
                                public void set(int val) {
                                    sp.edit().putInt("warn_" + idx, val).apply();
                                    PrayerNote.refresh(PhoneActivity.this);
                                    build();
                                }
                            });
                    }
                });
        }
        section("التاريخ الهجري");
        row("تعديل التاريخ الهجري", (Hijri.adjust(this) > 0 ? "+" : "") + Hijri.adjust(this) + " يوم", "›",
            new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    numberDialog("تعديل التاريخ الهجري (أيام)", -3, 3, Hijri.adjust(PhoneActivity.this), new IntSetter() {
                        @Override
                        public void set(int val) {
                            Hijri.setAdjust(PhoneActivity.this, val);
                            Reminders.scheduleNext(PhoneActivity.this);
                            occDay = "";
                            build();
                        }
                    });
                }
            });
    }

    private void prayerDialog(final int idx) {
        LinearLayout l = hbox();
        l.setGravity(Gravity.CENTER);
        final NumberPicker a = new NumberPicker(this);
        a.setMinValue(0);
        a.setMaxValue(60);
        a.setValue(PrayerPrefs.iqama(this)[idx]);
        final NumberPicker b = new NumberPicker(this);
        b.setMinValue(0);
        b.setMaxValue(20);
        b.setDisplayedValues(labels(-10, 10));
        b.setValue(PrayerPrefs.adjust(this)[idx] + 10);
        LinearLayout c1 = vbox();
        c1.setGravity(Gravity.CENTER);
        c1.addView(tv("الإقامة (دقائق)", 13, dim, false));
        c1.addView(a);
        LinearLayout c2 = vbox();
        c2.setGravity(Gravity.CENTER);
        c2.addView(tv("تعديل الوقت (دقائق)", 13, dim, false));
        c2.addView(b);
        l.addView(c1, new LinearLayout.LayoutParams(0, -2, 1f));
        l.addView(c2, new LinearLayout.LayoutParams(0, -2, 1f));
        final LinearLayout all = vbox();
        all.addView(l);
        all.addView(tv("عند الأذان والإقامة", 13, dim, false), lp(-1, -2, 12));
        final RadioGroup modes = new RadioGroup(this);
        String[] mn = {"صوت الأذان", "اهتزاز فقط", "بدون صوت ولا اهتزاز"};
        for (int i = 0; i < 3; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setId(1000 + i);
            rb.setText(mn[i]);
            rb.setTextColor(cream);
            rb.setTextSize(15);
            modes.addView(rb);
        }
        modes.check(1000 + sp.getInt("ad_mode_" + idx, 0));
        all.addView(modes);
        final CheckBox ring = new CheckBox(this);
        ring.setText("تشغيل الأذان حتى لو الهاتف على الصامت أو الاهتزاز");
        ring.setTextColor(cream);
        ring.setTextSize(14);
        ring.setChecked(sp.getBoolean("ad_ring_" + idx, true));
        all.addView(ring);
        new Pop(this).title(PrayerCalc.name(this, idx, false)).view(all).ok("حفظ", new Runnable() {
            @Override
            public void run() {
                PrayerPrefs.setIqama(PhoneActivity.this, idx, a.getValue());
                PrayerPrefs.setAdjust(PhoneActivity.this, idx, b.getValue() - 10);
                sp.edit().putInt("ad_mode_" + idx, modes.getCheckedRadioButtonId() - 1000).putBoolean("ad_ring_" + idx, ring.isChecked()).apply();
                Scheduler.scheduleNext(PhoneActivity.this);
                build();
            }
        }).cancel("إلغاء").show();
    }

    // ---------------------------------------------------------------- side menu
    private TextView hamburger() {
        TextView b = tv("☰", 20, gold, true);
        b.setGravity(Gravity.CENTER);
        b.setBackground(shape(0, line, 19));
        b.setContentDescription("القائمة");
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openDrawer();
            }
        });
        return b;
    }

    private void goTab(int t) {
        if (t == 10 && tab != 10) calOff = 0;
        tab = t;
        sub = null;
        if (drawerOpen) closeDrawer();
        build();
    }

    private void menuItem(String name, final int t) {
        TextView it = tv(name, 16, t == tab ? gold : cream, true);
        it.setPadding(dp(12), dp(11), dp(12), dp(11));
        if (t == tab) it.setBackground(shape(hl, 0, 12));
        it.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                goTab(t);
            }
        });
        drawerList.addView(it, lp(-1, -2, 2));
    }

    private void menuGroup(String name) {
        TextView g = tv(name, 12, gold, true);
        g.setPadding(dp(8), 0, 0, 0);
        drawerList.addView(g, lp(-1, -2, 14));
    }

    private void openDrawer() {
        drawerList.removeAllViews();
        LinearLayout head = vbox();
        head.setGravity(Gravity.CENTER_HORIZONTAL);
        head.addView(tv("الصلاة خير", 24, gold, true));
        head.addView(tv("أوقات الصلاة والأذكار", 12, dim, false));
        drawerList.addView(head, lp(-1, -2, 0));
        menuGroup("الصلاة");
        menuItem("مواقيت الصلاة", 0);
        menuItem("الأذان والإقامة", 4);
        menuItem("متابعة الصلوات", 11);
        menuItem("قضاء الصلوات", 12);
        menuItem("مدن أخرى", 13);
        menuItem("التقويم الهجري", 10);
        menuGroup("الأذكار والتذكيرات");
        menuItem("الأذكار والأدعية", 5);
        menuItem("السبحة والاستغفار", 6);
        menuItem("الأذكار فوق التطبيقات", 8);
        menuItem("التذكيرات", 2);
        menuGroup("القبلة والمزيد");
        menuItem("القبلة", 9);
        menuItem("الراديو", 1);
        menuGroup("");
        menuItem("الإعدادات", 3);
        drawerOpen = true;
        drawerLayer.setVisibility(View.VISIBLE);
        drawerPanel.setTranslationX(drawerW);
        drawerPanel.animate().translationX(0).setDuration(180);
        scrim.setAlpha(0f);
        scrim.animate().alpha(1f).setDuration(180);
    }

    private void closeDrawer() {
        if (!drawerOpen) return;
        drawerOpen = false;
        scrim.animate().alpha(0f).setDuration(150);
        drawerPanel.animate().translationX(drawerW).setDuration(150).withEndAction(new Runnable() {
            @Override
            public void run() {
                if (!drawerOpen) drawerLayer.setVisibility(View.GONE);
            }
        });
    }

    // ---------------------------------------------------------------- adhkar
    private TextView smallBtn(String t, View.OnClickListener l) {
        TextView b = tv(t, 14, gold, true);
        b.setGravity(Gravity.CENTER);
        b.setBackground(shape(0x33000000, line, 8));
        b.setOnClickListener(l);
        return b;
    }

    private LinearLayout catList;

    private void fillCategories(List<Adhkar.Category> all, String q) {
        catList.removeAllViews();
        q = q.trim();
        for (final Adhkar.Category c : all) {
            if (!q.isEmpty() && !c.name.contains(q)) continue;
            LinearLayout r = hbox();
            r.setBackground(shape(card, line, 14));
            r.setPadding(dp(14), dp(10), dp(14), dp(10));
            r.setMinimumHeight(dp(48));
            r.addView(tv(c.name, 16, cream, false), new LinearLayout.LayoutParams(0, -2, 1f));
            r.addView(tv("›", 16, gold, true));
            r.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    clearDone(c.id);
                    editCat = false;
                    sub = "cat:" + c.id;
                    build();
                }
            });
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
            rp.topMargin = dp(6);
            catList.addView(r, rp);
        }
        if (catList.getChildCount() == 0) catList.addView(tv("لا توجد نتائج", 14, dim, false));
    }

    private void adhkarPage() {
        if (sub != null && sub.equals("hadith")) {
            title("أحاديث بشرحها", true);
            add(tv("الشروح مختصرة وتحتاج مراجعة قبل الاعتماد.", 12, dim, false), 4);
            for (String[] hd : Adhkar.HADITHS) {
                LinearLayout c = vbox();
                c.setBackground(shape(card, line, 16));
                c.setPadding(dp(14), dp(12), dp(14), dp(12));
                TextView t = tv("«" + hd[0] + "»", tsz(0, 19), tcl(0, cream), false);
                t.setLineSpacing(0, 1.3f);
                c.addView(t);
                c.addView(tv(hd[1], 12, gold, true));
                TextView e = tv(hd[2], 14, dim, false);
                e.setLineSpacing(0, 1.2f);
                c.addView(e, lp(-1, -2, 6));
                add(c, 8);
            }
            return;
        }
        final List<Adhkar.Category> all = Adhkar.all(this);
        if (sub != null && sub.startsWith("cat:")) {
            int id = Integer.parseInt(sub.substring(4));
            for (final Adhkar.Category cat : all) {
                if (cat.id != id) continue;
                title(cat.name, true);
                totalLine();
                final List<Adhkar.Entry> es = Adhkar.entries(this, cat);
                boolean allInfo = !es.isEmpty();
                for (Adhkar.Entry x : es) allInfo &= x.info;
                if (allInfo) {
                    // a chapter of hadiths: cards to read, like "أحاديث بشرحها"; nothing to count
                    if (totTv != null) totTv.setVisibility(View.GONE);
                    for (Adhkar.Entry x : es) {
                        LinearLayout c = vbox();
                        c.setBackground(shape(card, line, 16));
                        c.setPadding(dp(14), dp(12), dp(14), dp(12));
                        if (!x.pre.isEmpty()) {
                            TextView pn = tv(x.pre, tsz(0, 12), gold, true);
                            pn.setLineSpacing(0, 1.3f);
                            c.addView(pn, lp(-1, -2, 0));
                        }
                        TextView t = tv(x.text, tsz(0, 18), tcl(0, cream), false);
                        t.setLineSpacing(0, 1.35f);
                        c.addView(t, lp(-1, -2, x.pre.isEmpty() ? 0 : 6));
                        if (!x.post.isEmpty()) {
                            TextView pn = tv(x.post, tsz(0, 13), dim, false);
                            pn.setLineSpacing(0, 1.3f);
                            c.addView(pn, lp(-1, -2, 6));
                        }
                        add(c, 8);
                    }
                    return;
                }
                if (editCat) {
                    catEdit(cat, es);
                    return;
                }
                row("إضافة ذكر أو ترتيب الأذكار", null, "✎", new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        editCat = true;
                        build();
                    }
                });
                int shown = 0;
                for (int i = 0; i < es.size(); i++) {
                    final Adhkar.Entry e = es.get(i);
                    final String key = cat.id + "|" + e.key;
                    final int total = e.count;
                    if (e.info) {
                        // a hadith or an explanation: small, and nothing to count
                        TextView n = tv(e.text, tsz(0, 13), dim, false);
                        n.setLineSpacing(0, 1.3f);
                        n.setPadding(dp(6), dp(4), dp(6), dp(4));
                        add(n, 8);
                        continue;
                    }
                    final int[] left = {done.containsKey(key) ? done.get(key) : total};
                    if (left[0] == 0) continue;
                    shown++;
                    final TextView ct = tv("", 15, gold, true);
                    LinearLayout r = vbox();
                    r.setBackground(shape(card, line, 16));
                    r.setPadding(dp(14), dp(12), dp(14), dp(12));
                    if (!e.pre.isEmpty()) {
                        TextView pn = tv(e.pre, tsz(0, 12), dim, false);
                        pn.setLineSpacing(0, 1.3f);
                        r.addView(pn, lp(-1, -2, 0));
                    }
                    TextView t = tv(e.text, tsz(0, 18), tcl(0, cream), false);
                    t.setLineSpacing(0, 1.35f);
                    r.addView(t, lp(-1, -2, e.pre.isEmpty() ? 0 : 6));
                    if (!e.post.isEmpty()) {
                        TextView pn = tv(e.post, tsz(0, 12), dim, false);
                        pn.setLineSpacing(0, 1.3f);
                        r.addView(pn, lp(-1, -2, 6));
                    }
                    ct.setGravity(Gravity.CENTER);
                    ct.setText(left[0] + " / " + total);
                    r.addView(ct, lp(-1, -2, 6));
                    r.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            left[0]--;
                            buzz();
                            Adhkar.bump(PhoneActivity.this);
                            refreshTotal();
                            done.put(key, left[0]);
                            if (left[0] == 0) {
                                toTop = true; // the finished one disappears and the next is at the top
                                build();
                            }
                            else ct.setText(left[0] + " / " + total);
                        }
                    });
                    add(r, 8);
                }
                if (shown == 0) {
                    add(tv("أتممت هذا الباب، تقبّل الله منك.", 17, gold, true), 16);
                } else {
                    add(tv("اضغط على الذكر لتعدّه. المصدر: حصن المسلم.", 12, dim, false), 10);
                }
                return;
            }
        }
        title("الأذكار والأدعية", false);
        add(tv("حصن المسلم كاملًا: " + all.size() + " بابًا.", 13, dim, false), 2);
        totalLine();
        final EditText search = new EditText(this);
        search.setHint("ابحث عن ذكر أو دعاء");
        search.setHintTextColor(dim);
        search.setTextColor(cream);
        search.setSingleLine(true);
        search.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        search.setBackground(shape(card, line, 14));
        search.setPadding(dp(14), dp(10), dp(14), dp(10));
        add(search, 8);
        catList = vbox();
        add(catList, 2);
        fillCategories(all, "");
        search.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
                fillCategories(all, s.toString());
            }

            @Override
            public void afterTextChanged(android.text.Editable e) {
            }
        });
        row("أحاديث بشرحها", null, "›", go("hadith"));
    }

    private boolean editCat;
    private TextView totTv;

    private void totalLine() {
        totTv = tv("", 13, gold, true);
        totTv.setGravity(Gravity.CENTER);
        add(totTv, 6);
        refreshTotal();
    }

    private void refreshTotal() {
        if (totTv != null) totTv.setText("إجمالي ما عددته منذ تثبيت التطبيق: " + Adhkar.total(this));
    }

    private void clearDone(int catId) {
        String pre = catId + "|";
        for (String k : new ArrayList<>(done.keySet())) if (k.startsWith(pre)) done.remove(k);
    }

    /** Add a dhikr to a chapter and arrange its adhkar. */
    private void catEdit(final Adhkar.Category cat, final List<Adhkar.Entry> es) {
        add(tv("رتّب الأذكار بالأسهم، أو أضف ذكرًا بنصك. الأذكار التي تضيفها يمكن تعديلها وحذفها.", 13, dim, false), 4);
        for (int i = 0; i < es.size(); i++) {
            final int idx = i;
            final Adhkar.Entry e = es.get(i);
            LinearLayout r = hbox();
            r.setBackground(shape(card, line, 14));
            r.setPadding(dp(12), dp(8), dp(12), dp(8));
            TextView t = tv(e.text.length() > 60 ? e.text.substring(0, 60) + "…" : e.text, 14, e.extra ? gold : cream, false);
            r.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dp(34), dp(34));
            bp.setMarginStart(dp(4));
            r.addView(smallBtn("▲", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (idx > 0) {
                        es.add(idx - 1, es.remove(idx));
                        Adhkar.saveEntries(PhoneActivity.this, cat, es);
                        build();
                    }
                }
            }), bp);
            r.addView(smallBtn("▼", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (idx < es.size() - 1) {
                        es.add(idx + 1, es.remove(idx));
                        Adhkar.saveEntries(PhoneActivity.this, cat, es);
                        build();
                    }
                }
            }), bp);
            if (e.extra) {
                r.addView(smallBtn("✎", typeText("تعديل الذكر", e.text, new Setter() {
                    @Override
                    public void set(String v) {
                        if (!v.isEmpty()) e.text = v;
                        Adhkar.saveEntries(PhoneActivity.this, cat, es);
                    }
                })), bp);
                r.addView(smallBtn("✕", new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        es.remove(idx);
                        Adhkar.saveEntries(PhoneActivity.this, cat, es);
                        build();
                    }
                }), bp);
            }
            add(r, 6);
        }
        row("إضافة ذكر جديد", null, "+", typeText("ذكر جديد", "", new Setter() {
            @Override
            public void set(final String v) {
                if (v.isEmpty()) return;
                numberDialog("كم مرة يُقال", 1, 1000, 1, new IntSetter() {
                    @Override
                    public void set(int n) {
                        Adhkar.Entry e = new Adhkar.Entry();
                        e.key = "x" + System.currentTimeMillis();
                        e.text = v;
                        e.count = n;
                        e.extra = true;
                        es.add(e);
                        Adhkar.saveEntries(PhoneActivity.this, cat, es);
                        build();
                    }
                });
            }
        }));
        row("استعادة الترتيب الأصلي", "ويحذف ما أضفته هنا", "↺", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Adhkar.resetEntries(PhoneActivity.this, cat);
                build();
            }
        });
        add(button("تم", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                editCat = false;
                clearDone(cat.id);
                build();
            }
        }), 10);
    }

    /** A short buzz on every count. */
    private void buzz() {
        try {
            Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (v != null && v.hasVibrator()) v.vibrate(VibrationEffect.createOneShot(18, VibrationEffect.DEFAULT_AMPLITUDE));
        } catch (RuntimeException ignored) {
            // no vibration on this phone
        }
    }

    private void tasbihPage() {
        title("السبحة والاستغفار", false);
        totalLine();
        TextView sel = tv(Adhkar.TASBIH[tk], tsz(1, 20), tcl(1, cream), true);
        sel.setGravity(Gravity.CENTER);
        add(sel, 10);
        tcBig = tv("", 64, gold, true);
        tcBig.setGravity(Gravity.CENTER);
        tcBig.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        GradientDrawable ring = new GradientDrawable();
        ring.setShape(GradientDrawable.OVAL);
        ring.setColor(0x14FFFFFF);
        ring.setStroke(dp(3), gold);
        tcBig.setBackground(ring);
        tcBig.setText(String.valueOf(Adhkar.cur(this, tk)));
        tcBig.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Adhkar.add(PhoneActivity.this, tk);
                buzz();
                refreshTasbih();
            }
        });
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(dp(190), dp(190));
        rp.gravity = Gravity.CENTER_HORIZONTAL;
        rp.topMargin = dp(10);
        body.addView(tcBig, rp);
        TextView hint = tv("اضغط الدائرة للعدّ", 12, dim, false);
        hint.setGravity(Gravity.CENTER);
        add(hint, 6);
        section("الأذكار");
        tcRows = new TextView[Adhkar.TASBIH.length];
        for (int i = 0; i < Adhkar.TASBIH.length; i++) {
            final int idx = i;
            LinearLayout c = vbox();
            c.setBackground(shape(i == tk ? hl : card, i == tk ? gold : line, 14));
            c.setPadding(dp(14), dp(9), dp(14), dp(9));
            c.addView(tv(Adhkar.TASBIH[i], tsz(1, 16), tcl(1, cream), true));
            tcRows[i] = tv("", 13, dim, false);
            c.addView(tcRows[i]);
            c.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    tk = idx;
                    build();
                }
            });
            add(c, 6);
        }
        refreshTasbih();
    }

    private void refreshTasbih() {
        refreshTotal();
        if (tcBig != null) tcBig.setText(String.valueOf(Adhkar.cur(this, tk)));
        if (tcRows != null) {
            for (int i = 0; i < tcRows.length; i++) {
                tcRows[i].setText("آخر عدد: " + Adhkar.last(this, i) + "  ·  اليوم: " + Adhkar.count(this, i));
            }
        }
    }

    // ---------------------------------------------------------------- Hijri calendar
    private int calOff;


    // ---------------------------------------------------------------- the TV
    private interface TvTask {
        void run() throws Exception;
    }

    private boolean tvVisible() {
        return tab == 3 && ("tv".equals(sub) || "tvkeys".equals(sub));
    }

    private void tvSay(final String line) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                tvEntries.add(0, line);
                while (tvEntries.size() > 25) tvEntries.remove(tvEntries.size() - 1);
                if (tvVisible()) build();
            }
        });
    }

    private String tvWhy(Throwable e) {
        if (e instanceof io.github.muntashirakon.adb.AdbPairingRequiredException) {
            return "التلفزيون يطلب الاقتران أولاً (اقتران برمز من شاشة التلفزيون)";
        }
        String m = e.getMessage();
        String n = e.getClass().getSimpleName();
        return m == null || m.isEmpty() ? n : m + " (" + n + ")";
    }

    private void tvRun(String label, final TvTask t) {
        tvRun(label, t, false);
    }

    /** keepLog: the button tests keep their earlier results to compare; every other new task starts a clean list. */
    private void tvRun(String label, final TvTask t, boolean keepLog) {
        if (tvBusy) {
            toast("انتظر حتى تنتهي الخطوة الحالية");
            return;
        }
        tvBusy = true;
        if (!keepLog) tvEntries.clear();
        tvSay(label);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    t.run();
                } catch (Throwable e) {
                    tvSay("تعذّر: " + tvWhy(e));
                } finally {
                    tvBusy = false;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (tvVisible()) build();
                        }
                    });
                }
            }
        }).start();
    }

    private EditText tvField(String hint, String value, int type, final boolean isCode) {
        EditText e = searchBox(hint);
        e.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        e.setInputType(type);
        e.setText(value);
        e.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
                if (isCode) tvCode = s.toString();
                else tvAddr = s.toString();
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
            }
        });
        return e;
    }

    /** Splits "ip:port"; a missing port becomes def (0 = a port is required). Returns null if it is not an address. */
    private Object[] tvParse(String a, int def) {
        a = a.trim();
        if (a.isEmpty()) return null;
        int i = a.lastIndexOf(':');
        String host = i < 0 ? a : a.substring(0, i);
        int port = def;
        if (i >= 0) {
            try {
                port = Integer.parseInt(a.substring(i + 1).trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        if (host.isEmpty() || port <= 0 || port > 65535) return null;
        return new Object[] {host.trim(), port};
    }

    private void tvPage() {
        title("اللانشر على التلفزيون", true);
        String[] names = {"تحضير", "اتصال", "تثبيت", "إعداد"};
        LinearLayout st = hbox();
        for (int i = 0; i < 4; i++) {
            boolean on = i == tvStage;
            TextView t = tv((i + 1) + " " + names[i], 12, on ? gold : dim, on);
            t.setGravity(Gravity.CENTER);
            t.setBackground(shape(on ? hl : 0, on ? gold : 0, 12));
            t.setPadding(0, dp(7), 0, dp(7));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1f);
            p.setMargins(dp(2), 0, dp(2), 0);
            st.addView(t, p);
        }
        add(st, 6);
        if (tvBusy) {
            row("خطوة تعمل الآن…", "لو لا تنتهي، اضغط هنا لإلغائها وقطع الاتصال", "إلغاء", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    tvBusy = false;
                    TvSetup.disconnect(PhoneActivity.this);
                    tvSay("أُلغيت الخطوة الحالية وانقطع الاتصال. اتصل من جديد.");
                }
            });
        }
        if (tvStage == 0) tvPrepare();
        else if (tvStage == 1) tvConnect();
        else if (tvStage == 2) tvInstall();
        else tvHome();
        tvLogBlock();
    }

    /** The notes, newest first, at the top of the page. */
    private void tvLogBlock() {
        if (tvEntries.isEmpty()) return;
        section("آخر ما حدث");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tvEntries.size() && i < 10; i++) {
            if (i > 0) sb.append("\n— — —\n");
            sb.append(tvEntries.get(i));
        }
        TextView lg = tv(sb.toString().trim(), 12, cream, false);
        lg.setTextIsSelectable(true);
        lg.setBackground(shape(card, line, 14));
        lg.setPadding(dp(12), dp(10), dp(12), dp(10));
        add(lg, 4);
        row("مسح", null, "›", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tvEntries.clear();
                build();
            }
        });
    }

    private void tvKeysPage() {
        title("اختبار أزرار الريموت", true);
        if (!TvSetup.connected(this)) {
            add(tv("الهاتف غير متصل بالتلفزيون. ارجع واضغط «اتصال» أولاً.", 14, dim, false), 8);
            tvLogBlock();
            return;
        }
        add(tv("اختر الزر الذي تريد اختباره، وبعدها اضغطه على ريموت التلفزيون خلال 12 ثانية. سنقرأ ما فعله النظام ونعرضه تحت.", 13, dim, false), 6);
        if (tvBusy) {
            row("اختبار جارٍ…", "لو لا ينتهي، اضغط هنا لإلغائه", "إلغاء", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    tvBusy = false;
                    TvSetup.disconnect(PhoneActivity.this);
                    tvSay("أُلغي الاختبار وانقطع الاتصال.");
                }
            });
        }
        row("تفعيل أزرار اليوتيوب والنجمة", "يعمل بعد تحديث اللانشر على التلفزيون (الخطوة 3)", "›", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tvRun("تفعيل أزرار الريموت", new TvTask() {
                    @Override
                    public void run() throws Exception {
                        TvSetup.enableKeys(PhoneActivity.this, new TvSetup.Say() {
                            @Override
                            public void say(String s) {
                                tvSay(s);
                            }
                        });
                    }
                }, true);
            }
        });
        section("الأزرار");
        String[] keys = {"المساعد الصوتي", "يوتيوب", "النجمة (المفضلة)", "Netflix", "الرئيسية (Home)", "الرجوع",
            "رفع الصوت", "خفض الصوت", "كتم الصوت", "تشغيل / إيقاف مؤقت", "المدخل (Input)", "القائمة / الإعدادات"};
        for (final String k : keys) {
            row(k, null, "اختبر", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    tvRun("اختبار: " + k, new TvTask() {
                        @Override
                        public void run() throws Exception {
                            TvSetup.keyProbe(PhoneActivity.this, new TvSetup.Say() {
                                @Override
                                public void say(String s) {
                                    tvSay(s);
                                }
                            }, k);
                        }
                    }, true);
                }
            });
        }
        tvLogBlock();
    }

    private void tvPrepare() {
        section("حضّر التلفزيون");
        String[] steps = {
            "افتح على التلفزيون: الإعدادات ← النظام ← حول.",
            "اضغط OK على «إصدار نظام التشغيل» 7 مرات حتى تظهر رسالة أنك أصبحت مطوّراً.",
            "ارجع ← النظام ← خيارات المطوّر، وشغّل «تصحيح الأخطاء اللاسلكي».",
            "الهاتف والتلفزيون لازم يكونا على نفس شبكة الواي فاي."};
        for (int i = 0; i < steps.length; i++) {
            LinearLayout r = hbox();
            r.setBackground(shape(card, line, 14));
            r.setPadding(dp(12), dp(10), dp(12), dp(10));
            TextView n = tv(String.valueOf(i + 1), 16, gold, true);
            n.setPadding(0, 0, dp(10), 0);
            r.addView(n);
            r.addView(tv(steps[i], 14, cream, false), new LinearLayout.LayoutParams(0, -2, 1f));
            add(r, 5);
        }
        add(tv("ما سيحدث بعد ذلك: تثبّت التطبيق اللانشر على التلفزيون من هذا الهاتف، ثم تجعله الشاشة الرئيسية. لا حاجة لكمبيوتر.", 12, dim, false), 10);
        row("جاهز، ابحث عن التلفزيون", null, "›", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tvStage = 1;
                build();
            }
        });
    }

    private void tvConnect() {
        if (TvSetup.connected(this)) {
            section("متصل");
            add(tv("الهاتف متصل بالتلفزيون ✓", 15, gold, true), 4);
            row("التالي: تثبيت اللانشر", null, "›", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    tvStage = 2;
                    build();
                }
            });
            row("رجوع إلى الشاشة الأصلية للتلفزيون", "يعيد لانشر Google ويفعّل أزرار الريموت", "›", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    tvRun("إرجاع الشاشة الأصلية…", new TvTask() {
                        @Override
                        public void run() throws Exception {
                            TvSetup.restore(PhoneActivity.this, new TvSetup.Say() {
                                @Override
                                public void say(String s) {
                                    tvSay(s);
                                }
                            });
                        }
                    });
                }
            });
            row("إعداد الشاشة الرئيسية", null, "›", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    tvStage = 3;
                    build();
                }
            });
            row("قطع الاتصال", null, "›", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    TvSetup.disconnect(PhoneActivity.this);
                    build();
                }
            });
            return;
        }
        if (!tvSearching) {
            tvSearching = true;
            TvSetup.startDiscovery(this, new Runnable() {
                @Override
                public void run() {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            tvRefreshFound();
                        }
                    });
                }
            });
        }
        section("اقتران الهاتف بالتلفزيون");
        add(tv("على التلفزيون: خيارات المطوّر ← «تصحيح الأخطاء اللاسلكي» ← «الاقتران بجهاز باستخدام رمز الاقتران». اترك الشاشة التي فيها الرمز مفتوحة، واكتب الرمز هنا:", 13, dim, false), 4);
        add(tvField("رمز الاقتران (6 أرقام)", tvCode, android.text.InputType.TYPE_CLASS_NUMBER, true), 6);
        section("وجدت على الشبكة");
        tvFound = vbox();
        add(tvFound, 4);
        tvRefreshFound();
        section("أو اكتب العنوان بنفسك");
        add(tv("العنوان والمنفذ كما يظهران على التلفزيون في صفحة التصحيح اللاسلكي (للاقتران: عنوان صفحة الرمز، وللاتصال: العنوان الأعلى).", 12, dim, false), 4);
        add(tvField("مثال: 192.168.1.50:41234", tvAddr, android.text.InputType.TYPE_CLASS_TEXT, false), 6);
        row("اقتران بهذا العنوان", "يستخدم الرمز أعلاه", "›", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Object[] a = tvParse(tvAddr, 0);
                if (a == null) {
                    toast("اكتب العنوان والمنفذ مثل 192.168.1.50:41234");
                    return;
                }
                tvPair((String) a[0], (Integer) a[1]);
            }
        });
        row("اتصال بهذا العنوان", "بعد الاقتران", "›", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Object[] a = tvParse(tvAddr, 5555);
                if (a == null) {
                    toast("اكتب العنوان مثل 192.168.1.50:5555");
                    return;
                }
                tvConnectTo((String) a[0], (Integer) a[1]);
            }
        });
        row("رجوع إلى خطوات التحضير", null, "›", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tvStage = 0;
                build();
            }
        });
    }

    private void tvRefreshFound() {
        if (tvFound == null || !tvVisible() || tvStage < 1) return;
        tvFound.removeAllViews();
        boolean any = false;
        if (TvSetup.pairHost != null) {
            any = true;
            final String ph = TvSetup.pairHost;
            final int pp = TvSetup.pairPort;
            LinearLayout r = row("تلفزيون جاهز للاقتران", ph + ":" + pp, "اقتران", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    tvPair(ph, pp);
                }
            });
            body.removeView(r);
            tvFound.addView(r, lp(-1, -2, 5));
        }
        if (TvSetup.connHost != null) {
            any = true;
            final String ch = TvSetup.connHost;
            final int cp = TvSetup.connPort;
            LinearLayout r = row("تلفزيون جاهز للاتصال", ch + ":" + cp, "اتصال", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    tvConnectTo(ch, cp);
                }
            });
            body.removeView(r);
            tvFound.addView(r, lp(-1, -2, 5));
        }
        if (!any) {
            tvFound.addView(tv("أبحث عن التلفزيون على الواي فاي… إن لم يظهر خلال دقيقة، اكتب العنوان بنفسك.", 13, dim, false));
        }
    }

    private void tvPair(final String host, final int port) {
        final String code = tvCode.trim();
        if (code.length() != 6) {
            toast("اكتب رمز الاقتران المكوّن من 6 أرقام");
            return;
        }
        tvRun("اقتران مع " + host + ":" + port + "…", new TvTask() {
            @Override
            public void run() throws Exception {
                TvSetup.pair(PhoneActivity.this, host, port, code);
                tvSay("تم الاقتران ✓");
                // the TV announces the port to connect on once it is paired
                long end = System.currentTimeMillis() + 10000;
                while (TvSetup.connHost == null && System.currentTimeMillis() < end) Thread.sleep(500);
                if (TvSetup.connHost == null) {
                    tvSay("لم أجد منفذ الاتصال. اكتب العنوان الظاهر في صفحة التصحيح اللاسلكي (فوق) واضغط «اتصال بهذا العنوان».");
                    return;
                }
                TvSetup.connect(PhoneActivity.this, TvSetup.connHost, TvSetup.connPort);
                tvSay("متصل بالتلفزيون ✓ " + TvSetup.info(PhoneActivity.this));
                if (tvStage < 2) tvStage = 2;
            }
        });
    }

    private void tvConnectTo(final String host, final int port) {
        tvRun("اتصال بـ " + host + ":" + port + "…", new TvTask() {
            @Override
            public void run() throws Exception {
                TvSetup.connect(PhoneActivity.this, host, port);
                tvSay("متصل بالتلفزيون ✓ " + TvSetup.info(PhoneActivity.this));
                if (tvStage < 2) tvStage = 2;
            }
        });
    }

    private void tvInstall() {
        if (!TvSetup.connected(this)) {
            add(tv("انقطع الاتصال بالتلفزيون. اتصل من جديد (تبقى في نفس الخطوة):", 14, gold, true), 8);
            tvConnect();
            return;
        }
        section("تثبيت اللانشر");
        add(tv("ينزّل التطبيق آخر نسخة من اللانشر من GitHub ويثبّتها على التلفزيون، ويعطيه الأذونات التي يحتاجها.", 13, dim, false), 4);
        row("تثبيت اللانشر على التلفزيون", "يحتاج الإنترنت على الهاتف", "›", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tvRun("بدء التثبيت…", new TvTask() {
                    @Override
                    public void run() throws Exception {
                        TvSetup.install(PhoneActivity.this, new TvSetup.Say() {
                            @Override
                            public void say(String s) {
                                tvSay(s);
                            }
                        });
                        tvStage = 3;
                    }
                });
            }
        });
        row("تخطّ: اللانشر مثبّت بالفعل", null, "›", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tvStage = 3;
                build();
            }
        });
    }

    private void tvHome() {
        if (!TvSetup.connected(this)) {
            add(tv("انقطع الاتصال بالتلفزيون. اتصل من جديد (تبقى في نفس الخطوة):", 14, gold, true), 8);
            tvConnect();
            return;
        }
        section("الشاشة الرئيسية");
        add(tv(TvSetup.homeSet(this) ? "الحالة: اللانشر هو الشاشة الرئيسية ✓" : "الحالة: لم يُعيَّن بعد", 15, gold, true), 4);
        add(tv("عند التعيين يُعطَّل لانشر Google TV الأصلي ويصير هذا اللانشر هو الشاشة الرئيسية. يتأكد التطبيق من ظهوره، وإن لم يظهر يتراجع كل شيء تلقائياً. وبإمكانك الرجوع للأصلية في أي وقت من هنا. وبعد التعيين يبقى على التلفزيون حارس: لو حُذف اللانشر يرجع لانشر Google تلقائياً خلال نصف دقيقة تقريباً. الحارس يتوقف إذا أُعيد تشغيل التلفزيون، ويعود عندما يتصل الهاتف بالتلفزيون مرة أخرى. فالأفضل الرجوع للأصلية من هنا قبل حذف اللانشر.", 13, dim, false), 6);
        row("تعيين اللانشر كشاشة رئيسية", null, "›", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new Pop(PhoneActivity.this).title("تعيين الشاشة الرئيسية")
                    .message("سيُعطَّل لانشر Google TV الأصلي مؤقتاً ويظهر هذا اللانشر على التلفزيون. تابع شاشة التلفزيون. أكمل؟")
                    .ok("تعيين", new Runnable() {
                        @Override
                        public void run() {
                            tvRun("بدء التعيين…", new TvTask() {
                                @Override
                                public void run() throws Exception {
                                    TvSetup.setHome(PhoneActivity.this, new TvSetup.Say() {
                                        @Override
                                        public void say(String s) {
                                            tvSay(s);
                                        }
                                    });
                                }
                            });
                        }
                    }).cancel("إلغاء").show();
            }
        });
        row("رجوع إلى الشاشة الأصلية للتلفزيون", "يعيد لانشر Google TV كما كان", "›", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tvRun("إرجاع الشاشة الأصلية…", new TvTask() {
                    @Override
                    public void run() throws Exception {
                        TvSetup.restore(PhoneActivity.this, new TvSetup.Say() {
                            @Override
                            public void say(String s) {
                                tvSay(s);
                            }
                        });
                    }
                });
            }
        });
        row("اختبار أزرار الريموت", "اختر زرّاً من الهاتف واضغطه على الريموت لنعرف ماذا يحدث", "›", go("tvkeys"));
        row("العودة إلى خطوة التثبيت", null, "›", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tvStage = 2;
                build();
            }
        });
    }

    // ---------------------------------------------------------------- other cities
    private boolean addingOther;
    private final ArrayList<Runnable> otherTick = new ArrayList<>();
    private final java.util.HashSet<String> otherOpen = new java.util.HashSet<>();

    /** The cities the user follows besides their own: one line each, "country|en|ar|lat|lon|tz". */
    private List<Cities.City> otherCities() {
        List<Cities.City> l = new ArrayList<>();
        for (String line1 : sp.getString("other_cities", "").split("\n")) {
            String[] a = line1.split("\\|", -1);
            if (a.length < 5) continue;
            try {
                Cities.City c = new Cities.City(a[0], a[1], a[2], Double.parseDouble(a[3]), Double.parseDouble(a[4]));
                if (a.length > 5 && !a[5].isEmpty()) c.tz = a[5];
                l.add(c);
            } catch (NumberFormatException ignored) {
                // an unreadable line is skipped
            }
        }
        return l;
    }

    private void saveOther(List<Cities.City> l) {
        StringBuilder sb = new StringBuilder();
        for (Cities.City c : l) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(c.country).append('|').append(c.en.replace("|", " ")).append('|').append(c.ar.replace("|", " "))
                .append('|').append(c.lat).append('|').append(c.lon).append('|').append(c.tz == null ? "" : c.tz);
        }
        sp.edit().putString("other_cities", sb.toString()).apply();
    }

    private void addOther(Cities.City c) {
        List<Cities.City> l = otherCities();
        boolean dup = false;
        for (Cities.City x : l) if (x.ar.equals(c.ar) && x.country.equals(c.country)) dup = true;
        if (!dup && l.size() < 30) {
            l.add(c);
            saveOther(l);
        }
        addingOther = false;
        tab = 13;
        sub = null;
        build();
    }

    /** Moves a followed city one place up (-1) or down (+1) in the list. */
    private void moveOther(String key, int by) {
        List<Cities.City> l = otherCities();
        int i = -1;
        for (int j = 0; j < l.size(); j++) if ((l.get(j).country + "|" + l.get(j).en).equals(key)) i = j;
        int to = i + by;
        if (i < 0 || to < 0 || to >= l.size()) return;
        Cities.City c = l.remove(i);
        l.add(to, c);
        saveOther(l);
        build();
    }

    private static String diffText(long minutes) {
        long m = Math.abs(minutes);
        if (m == 0) return "نفس الوقت";
        long hh = m / 60, mm = m % 60;
        String h2 = hh == 0 ? "" : hh == 1 ? "ساعة" : hh == 2 ? "ساعتين" : hh <= 10 ? hh + " ساعات" : hh + " ساعة";
        String m2 = mm == 0 ? "" : (mm == 30 ? (hh == 0 ? "نصف ساعة" : "ونصف") : (hh == 0 ? mm + " دقيقة" : "و" + mm + " دقيقة"));
        return (minutes > 0 ? "متقدم بـ " : "متأخر بـ ") + (h2 + (h2.isEmpty() || m2.isEmpty() ? "" : " ") + m2).trim();
    }

    /** The other cities: for each, its own clock, how far it is from yours, and its next prayer; a tap opens the five prayers. */
    private void otherPage() {
        title("مدن أخرى", false);
        add(tv("تابع مواقيت الصلاة في مدن أخرى (أهلك أو سفرك)، مع فرق التوقيت عن مدينتك. اضغط على المدينة لترى الصلوات الخمس.", 12, dim, false), 4);
        row("إضافة مدينة", "من أي دولة، أو بالبحث عن الاسم", "+", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addingOther = true;
                tab = 3;
                sub = "country";
                build();
            }
        });
        final Cities.City mine = Cities.selected(this);
        List<Cities.City> list = otherCities();
        if (list.isEmpty()) {
            add(tv("لم تضف مدناً بعد.", 14, dim, false), 10);
            return;
        }
        final int[] zero = new int[5];
        for (final Cities.City c : list) {
            final String k = c.country + "|" + c.en;
            LinearLayout box = vbox();
            box.setBackground(shape(card, line, 16));
            box.setPadding(dp(14), dp(10), dp(14), dp(10));

            LinearLayout top = hbox();
            top.setGravity(Gravity.CENTER_VERTICAL);
            top.addView(tv(c.ar, 18, cream, true), new LinearLayout.LayoutParams(0, -2, 1f));
            final TextView local = tv("", 22, gold, true);
            top.addView(local);
            box.addView(top);
            final TextView rel = tv("", 12, dim, false);
            box.addView(rel);

            LinearLayout nx = hbox();
            nx.setGravity(Gravity.CENTER_VERTICAL);
            final TextView nxName = tv("", 15, cream, false);
            nx.addView(nxName, new LinearLayout.LayoutParams(0, -2, 1f));
            final TextView cd = tv("", 15, gold, true);
            nx.addView(cd);
            box.addView(nx, lp(-1, -2, 8));

            long now0 = System.currentTimeMillis();
            final java.util.TimeZone tz = PrayerCalc.zone(c);
            long myOff = mine == null ? tz.getOffset(now0) : PrayerCalc.zone(mine).getOffset(now0);
            final String relText = diffText((tz.getOffset(now0) - myOff) / 60000L);
            rel.setText(relText);

            final long[] nextAt = {0};
            final int[] nextIdx = {-1};
            Runnable upd = new Runnable() {
                @Override
                public void run() {
                    long now = System.currentTimeMillis();
                    Calendar cal = Calendar.getInstance(tz);
                    cal.setTimeInMillis(now);
                    int hr = cal.get(Calendar.HOUR);
                    if (hr == 0) hr = 12;
                    local.setText(String.format(Locale.US, "%d:%02d %s", hr, cal.get(Calendar.MINUTE),
                        cal.get(Calendar.AM_PM) == Calendar.AM ? "ص" : "م"));
                    if (nextIdx[0] < 0 || now >= nextAt[0]) {
                        long[] t = PrayerCalc.day(c, now, 0, zero);
                        int idx = -1;
                        long at = 0;
                        for (int i = 0; i < 5; i++) {
                            if (t[i] > now) {
                                idx = i;
                                at = t[i];
                                break;
                            }
                        }
                        long when = now;
                        if (idx < 0) {
                            at = PrayerCalc.day(c, now, 1, zero)[0];
                            idx = 0;
                            when = now + 86_400_000L;
                        }
                        nextIdx[0] = idx;
                        nextAt[0] = at;
                        nxName.setText(PrayerCalc.name(PhoneActivity.this, idx, PrayerCalc.isFriday(c, when)) + " " + ap(PrayerCalc.time(PhoneActivity.this, c, at)));
                    }
                    cd.setText(PrayerCalc.countdown(Math.max(0, nextAt[0] - now)));
                }
            };
            upd.run();
            otherTick.add(upd);

            if (otherOpen.contains(k)) {
                long[] t = PrayerCalc.day(c, System.currentTimeMillis(), 0, zero);
                for (int i = 0; i < 5; i++) {
                    LinearLayout pr = hbox();
                    pr.addView(tv(PrayerCalc.name(this, i, PrayerCalc.isFriday(c, System.currentTimeMillis())), 14, dim, false),
                        new LinearLayout.LayoutParams(0, -2, 1f));
                    pr.addView(tv(ap(PrayerCalc.time(this, c, t[i])), 14, cream, true));
                    box.addView(pr, lp(-1, -2, 4));
                }
                LinearLayout acts = hbox();
                acts.setGravity(Gravity.CENTER_VERTICAL);
                acts.addView(smallBtn("▲ أعلى", new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        moveOther(k, -1);
                    }
                }));
                acts.addView(smallBtn("▼ أسفل", new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        moveOther(k, 1);
                    }
                }), lp(-2, -2, 0));
                acts.addView(smallBtn("حذف", new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        List<Cities.City> l2 = otherCities();
                        for (int i = l2.size() - 1; i >= 0; i--) if ((l2.get(i).country + "|" + l2.get(i).en).equals(k)) l2.remove(i);
                        saveOther(l2);
                        otherOpen.remove(k);
                        build();
                    }
                }), lp(-2, -2, 0));
                for (int i = 0; i < acts.getChildCount(); i++) {
                    View bt = acts.getChildAt(i);
                    bt.setPadding(dp(14), dp(8), dp(14), dp(8));
                    if (i > 0) {
                        LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(-2, -2);
                        ml.setMarginStart(dp(8));
                        bt.setLayoutParams(ml);
                    }
                }
                box.addView(acts, lp(-2, -2, 10));
            }
            box.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!otherOpen.remove(k)) otherOpen.add(k);
                    build();
                }
            });
            add(box, 8);
        }
    }

    /** The missed prayers to make up: a counter for each prayer, + when one is missed and − when it is made up. */
    private void qadaPage() {
        title("قضاء الصلوات", false);
        add(tv("عدّاد للصلوات الفائتة. اضغط «+ فاتت» لتسجيل صلاة فاتتك، و«− قضيت» كلما قضيت واحدة. اضغط على الرقم لضبط العدد دفعة واحدة. يُحفظ مع النسخة الاحتياطية.", 12, dim, false), 4);
        final String[] names = {"الفجر", "الظهر", "العصر", "المغرب", "العشاء"};
        int total = 0;
        for (int i = 0; i < 5; i++) total += sp.getInt("qada_" + i, 0);
        TextView tt = tv(total == 0 ? "لا توجد صلوات للقضاء" : "المتبقي للقضاء: " + total + " صلاة", 18, gold, true);
        tt.setGravity(Gravity.CENTER);
        add(tt, 8);
        for (int i = 0; i < 5; i++) {
            final int idx = i;
            final String key = "qada_" + i;
            LinearLayout r = hbox();
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setBackground(shape(card, line, 14));
            r.setPadding(dp(12), dp(8), dp(12), dp(8));
            r.addView(tv(names[i], 17, cream, true), new LinearLayout.LayoutParams(0, -2, 1f));
            TextView minus = smallBtn("− قضيت", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    int c = sp.getInt(key, 0);
                    if (c > 0) sp.edit().putInt(key, c - 1).apply();
                    build();
                }
            });
            minus.setPadding(dp(10), dp(8), dp(10), dp(8));
            r.addView(minus);
            final int cur = sp.getInt(key, 0);
            TextView n = tv(String.valueOf(cur), 20, gold, true);
            n.setGravity(Gravity.CENTER);
            n.setMinWidth(dp(56));
            n.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    numberDialog("عدد صلوات " + names[idx] + " الفائتة", 0, 9999, cur, new IntSetter() {
                        @Override
                        public void set(int val) {
                            sp.edit().putInt(key, val).apply();
                            build();
                        }
                    });
                }
            });
            r.addView(n);
            TextView plus = smallBtn("+ فاتت", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    sp.edit().putInt(key, sp.getInt(key, 0) + 1).apply();
                    build();
                }
            });
            plus.setPadding(dp(10), dp(8), dp(10), dp(8));
            r.addView(plus);
            add(r, 6);
        }
    }

    private void trackPage() {
        title("متابعة الصلوات", false);
        Calendar today = Track.cal(this, System.currentTimeMillis());
        String[] names = {"الفجر", "الظهر", "العصر", "المغرب", "العشاء"};
        add(tv("علّم كل صلاة بعد أدائها من علامة ○ بجانبها في الصفحة الرئيسية. وإذا علّمتها لا يصلك تنبيه قرب خروج وقتها. تُحفظ مع النسخة الاحتياطية.", 12, dim, false), 4);

        int week = 0, month = 0, monthDays = 0, full = 0, streak = 0;
        boolean streakOn = true;
        for (int i = 0; i < 31; i++) {
            Calendar k = (Calendar) today.clone();
            k.add(Calendar.DAY_OF_MONTH, -i);
            int b = Track.count(Track.bits(this, k));
            if (i < 7) week += b;
            if (k.get(Calendar.MONTH) == today.get(Calendar.MONTH) && k.get(Calendar.YEAR) == today.get(Calendar.YEAR)) {
                month += b;
                monthDays++;
            }
            if (b == 5) {
                full++;
                if (streakOn) streak++;
            } else if (i > 0) {
                streakOn = false; // today still being open does not break the streak
            }
        }
        section("الإحصائية");
        LinearLayout st = hbox();
        String[][] cards = {
            {week + " / 35", "آخر 7 أيام"},
            {month + " / " + (monthDays * 5), "هذا الشهر"},
            {String.valueOf(streak), "أيام متتالية كاملة"}};
        for (String[] cd : cards) {
            LinearLayout c1 = vbox();
            c1.setGravity(Gravity.CENTER);
            c1.setBackground(shape(card, line, 14));
            c1.setPadding(dp(4), dp(10), dp(4), dp(10));
            TextView a = tv(cd[0], 20, gold, true);
            a.setGravity(Gravity.CENTER);
            TextView b2 = tv(cd[1], 11, dim, false);
            b2.setGravity(Gravity.CENTER);
            c1.addView(a);
            c1.addView(b2);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, -2, 1f);
            cp.setMargins(dp(3), 0, dp(3), 0);
            st.addView(c1, cp);
        }
        add(st, 6);
        add(tv("أيام كاملة (الخمس) خلال آخر 31 يوم: " + full, 12, dim, false), 6);

        section("آخر 7 أيام");
        LinearLayout head = hbox();
        head.addView(tv("", 11, dim, false), new LinearLayout.LayoutParams(0, -2, 1.6f));
        for (String n : names) {
            TextView t = tv(n, 11, dim, true);
            t.setGravity(Gravity.CENTER);
            head.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        }
        add(head, 4);
        for (int i = 0; i < 7; i++) {
            final Calendar k = (Calendar) today.clone();
            k.add(Calendar.DAY_OF_MONTH, -i);
            int b = Track.bits(this, k);
            LinearLayout r = hbox();
            r.setBackground(shape(i == 0 ? hl : card, i == 0 ? gold : 0, 12));
            r.setPadding(dp(8), dp(8), dp(8), dp(8));
            String lab = i == 0 ? "اليوم" : i == 1 ? "أمس" : Hijri.weekdayAr(k);
            r.addView(tv(lab, 13, cream, true), new LinearLayout.LayoutParams(0, -2, 1.6f));
            for (int j = 0; j < 5; j++) {
                boolean on = (b >> j & 1) != 0;
                TextView t = tv(on ? "✓" : "·", 18, on ? gold : dim, true);
                t.setGravity(Gravity.CENTER);
                r.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
            }
            add(r, 3);
        }
        add(tv("التعديل على الأيام السابقة غير متاح؛ تُعلَّم الصلاة في يومها فقط.", 11, dim, false), 8);
    }

    private void calendarPage() {
        title("التقويم الهجري", false);
        final int adj = Hijri.adjust(this);
        Calendar today = Hijri.today(this);
        int[] th = Hijri.of(today, adj);
        int idx = th[0] * 12 + (th[1] - 1) + calOff;
        final int year = Math.floorDiv(idx, 12), month0 = Math.floorMod(idx, 12);

        android.icu.util.IslamicCalendar ic = new android.icu.util.IslamicCalendar();
        ic.setCalculationType(android.icu.util.IslamicCalendar.CalculationType.ISLAMIC_UMALQURA);
        ic.set(android.icu.util.IslamicCalendar.YEAR, year);
        ic.set(android.icu.util.IslamicCalendar.MONTH, month0);
        ic.set(android.icu.util.IslamicCalendar.DAY_OF_MONTH, 1);
        int len = ic.getActualMaximum(android.icu.util.IslamicCalendar.DAY_OF_MONTH);
        Calendar first = Calendar.getInstance(today.getTimeZone(), Locale.ENGLISH);
        first.setTime(ic.getTime());
        first.set(Calendar.HOUR_OF_DAY, 12);
        first.add(Calendar.DAY_OF_MONTH, -adj);

        // month header with the arrows
        LinearLayout nav = hbox();
        nav.setBackground(shape(card, line, 16));
        nav.setPadding(dp(8), dp(6), dp(8), dp(6));
        TextView prev = tv("‹", 24, gold, true);
        prev.setGravity(Gravity.CENTER);
        prev.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                calOff--;
                build();
            }
        });
        TextView next = tv("›", 24, gold, true);
        next.setGravity(Gravity.CENTER);
        next.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                calOff++;
                build();
            }
        });
        nav.addView(prev, new LinearLayout.LayoutParams(dp(48), dp(40)));
        String gm = Hijri.gregorianAr(first).replaceFirst("^\\d+ ", "");
        TextView mt = tv(Hijri.hijriAr(new int[] {year, month0 + 1, 1}).replaceFirst("^1 ", ""), 18, gold, true);
        mt.setGravity(Gravity.CENTER);
        LinearLayout mid = vbox();
        mid.setGravity(Gravity.CENTER);
        mid.addView(mt);
        TextView gt = tv("يوافق " + gm, 11, dim, false);
        mid.addView(gt);
        mid.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                calOff = 0;
                build();
            }
        });
        nav.addView(mid, new LinearLayout.LayoutParams(0, -2, 1f));
        nav.addView(next, new LinearLayout.LayoutParams(dp(48), dp(40)));
        add(nav, 6);

        // the week
        LinearLayout wk = hbox();
        String[] wd = {"أحد", "اثنين", "ثلاثاء", "أربعاء", "خميس", "جمعة", "سبت"};
        for (int d = 0; d < 7; d++) {
            TextView t = tv(wd[d], 11, d == 5 ? gold : dim, true);
            t.setGravity(Gravity.CENTER);
            wk.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        }
        add(wk, 10);

        // the days
        int lead = first.get(Calendar.DAY_OF_WEEK) - 1;
        int cells = lead + len;
        int rows = (cells + 6) / 7;
        final StringBuilder notes = new StringBuilder();
        for (int r = 0; r < rows; r++) {
            LinearLayout row = hbox();
            for (int d = 0; d < 7; d++) {
                int day = r * 7 + d - lead + 1;
                LinearLayout cell = vbox();
                cell.setGravity(Gravity.CENTER);
                if (day >= 1 && day <= len) {
                    final Calendar g = (Calendar) first.clone();
                    g.add(Calendar.DAY_OF_MONTH, day - 1);
                    boolean isToday = calOff == 0 && day == th[2];
                    boolean white = day >= 13 && day <= 15;
                    final int occ = Reminders.occasion(new int[] {year, month0 + 1, day});
                    boolean fri = g.get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY;
                    TextView hd = tv(String.valueOf(day), 17, fri || occ >= 0 ? gold : cream, true);
                    hd.setGravity(Gravity.CENTER);
                    TextView gd = tv(String.valueOf(g.get(Calendar.DAY_OF_MONTH)), 10, dim, false);
                    gd.setGravity(Gravity.CENTER);
                    cell.addView(hd);
                    cell.addView(gd);
                    cell.setBackground(shape(isToday ? hl : white ? (gold & 0x00FFFFFF) | 0x22000000 : 0, isToday ? gold : occ >= 0 ? line : 0, 12));
                    if (occ >= 0) notes.append(day).append(" ").append(Hijri.hijriAr(new int[] {year, month0 + 1, day}).replaceFirst("^\\d+ ", "").replaceFirst(" \\d+ هـ$", ""))
                        .append(": ").append(Reminders.occName(this, occ)).append("\n");
                    cell.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            int[] hh = Hijri.of(g, adj);
                            toast(Hijri.weekdayAr(g) + " " + Hijri.hijriAr(hh) + "\nالموافق " + Hijri.gregorianAr(g)
                                + (occ >= 0 ? "\n" + Reminders.occName(PhoneActivity.this, occ) : ""));
                        }
                    });
                }
                LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, dp(52), 1f);
                cp.setMargins(dp(1), dp(1), dp(1), dp(1));
                row.addView(cell, cp);
            }
            add(row, 0);
        }
        section("في هذا الشهر");
        String extra = month0 == 8 ? "شهر رمضان: صيام الشهر كله.\n" : "";
        add(tv(extra + (notes.length() > 0 ? notes.toString() : "") + "13 و14 و15: الأيام البيض (مظلّلة).\nالإثنين والخميس: صيام السنّة.", 13, dim, false), 4);
        add(tv("التاريخ بحسب أم القرى، وتعديله من «الأذان والإقامة» ← تعديل التاريخ الهجري.", 11, dim, false), 8);
    }

    private void overlayPage() {
        title("الأذكار فوق التطبيقات", false);
        add(tv("يظهر ذكر قصير فوق أي تطبيق تستخدمه، بدون فتح التطبيق. يختفي وحده بعد 5 ثوانٍ أو بالضغط عليه. لا علاقة له بالأذان، فالأذان يعمل بإشعار صغير فقط.", 13, dim, false), 4);
        final boolean can = Settings.canDrawOverlays(this);
        switchRow("عرض الأذكار فوق التطبيقات", can ? null : "يحتاج إذن العرض فوق التطبيقات", sp.getBoolean("ov_on", false),
            new CompoundListener() {
                @Override
                public void changed(boolean on) {
                    sp.edit().putBoolean("ov_on", on).apply();
                    if (on && !Settings.canDrawOverlays(PhoneActivity.this)) openOverlayPermission();
                    OverlayService.sync(PhoneActivity.this);
                }
            });
        switchRow("أيقونة أذكار الصباح والمساء", "أيقونة دائرية فوق التطبيقات وقت الأذكار (من الفجر للظهر، ومن العصر للعشاء). اضغطها لتفتح الذكر مباشرة", sp.getBoolean("az_bubble", true),
            new CompoundListener() {
                @Override
                public void changed(boolean on) {
                    sp.edit().putBoolean("az_bubble", on).apply();
                    if (on && !Settings.canDrawOverlays(PhoneActivity.this)) openOverlayPermission();
                    BubbleService.sync(PhoneActivity.this);
                }
            });
        final int f = sp.getInt("ov_freq", 30);
        row("معدل الظهور", "يظهر ذكر كل " + f + " دقيقة", "›", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                numberDialog("كل كم دقيقة يظهر ذكر", 5, 240, f, new IntSetter() {
                    @Override
                    public void set(int val) {
                        sp.edit().putInt("ov_freq", val).apply();
                        OverlayService.sync(PhoneActivity.this);
                        build();
                    }
                });
            }
        });
        section("الأذكار التي تظهر");
        final List<String[]> ovs = Adhkar.overlay(this);
        for (int i = 0; i < ovs.size(); i++) {
            final int idx = i;
            final String[] o = ovs.get(i);
            LinearLayout r = hbox();
            r.setBackground(shape(card, line, 14));
            r.setPadding(dp(12), dp(6), dp(8), dp(6));
            Switch sw = new Switch(this);
            sw.setChecked(o[1].equals("1"));
            sw.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(android.widget.CompoundButton b, boolean on) {
                    o[1] = on ? "1" : "0";
                    Adhkar.saveOverlay(PhoneActivity.this, ovs);
                }
            });
            r.addView(sw);
            TextView t = tv(o[0], 15, cream, false);
            t.setPadding(dp(10), 0, dp(6), 0);
            r.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dp(34), dp(34));
            r.addView(smallBtn("✎", typeText("تعديل الذكر", o[0], new Setter() {
                @Override
                public void set(String v) {
                    if (!v.isEmpty()) o[0] = v;
                    Adhkar.saveOverlay(PhoneActivity.this, ovs);
                }
            })), bp);
            r.addView(smallBtn("✕", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    ovs.remove(idx);
                    Adhkar.saveOverlay(PhoneActivity.this, ovs);
                    build();
                }
            }), bp);
            add(r, 6);
        }
        row("إضافة ذكر", null, "+", typeText("ذكر يظهر فوق التطبيقات", "", new Setter() {
            @Override
            public void set(String v) {
                if (v.isEmpty()) return;
                ovs.add(new String[] {v, "1"});
                Adhkar.saveOverlay(PhoneActivity.this, ovs);
            }
        }));
    }

    private void openOverlayPermission() {
        try {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
        } catch (RuntimeException e) {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
        }
    }

    // ---------------------------------------------------------------- qibla
    private void qiblaPage() {
        title("القبلة", false);
        Cities.City city = Cities.selected(this);
        if (city == null) {
            row("اختر مدينتك أولًا", "لحساب اتجاه القبلة", "›", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    tab = 3;
                    sub = "country";
                    build();
                }
            });
            return;
        }
        double bearing = Qibla.bearing(city.lat, city.lon);
        dial = new Qibla.Dial(this, gold, cream, dim);
        dial.set(0f, (float) bearing, false);
        LinearLayout.LayoutParams dp1 = new LinearLayout.LayoutParams(dp(310), dp(380));
        dp1.gravity = Gravity.CENTER_HORIZONTAL;
        dp1.topMargin = dp(6);
        body.addView(dial, dp1);

        // the heading of the phone, big, under the dial
        qiblaHead = tv(String.format(Locale.US, "%.0f°", bearing), 46, cream, true);
        qiblaHead.setGravity(Gravity.CENTER);
        add(qiblaHead, 6);
        qiblaText = tv("", 15, gold, true);
        qiblaText.setGravity(Gravity.CENTER);
        add(qiblaText, 2);
        TextView info = tv(String.format(Locale.US, "الاتجاه التقريبي للقبلة في %s: %.0f° · المسافة نحو %.0f كم",
            city.name(true), bearing, Qibla.distanceKm(city.lat, city.lon)), 13, dim, false);
        info.setGravity(Gravity.CENTER);
        add(info, 8);

        // the sensor accuracy, and the calibrate button
        LinearLayout acc = hbox();
        acc.setGravity(Gravity.CENTER_VERTICAL);
        acc.setBackground(shape(card, line, 14));
        acc.setPadding(dp(14), dp(10), dp(14), dp(10));
        qiblaDot = new View(this);
        qiblaDot.setBackground(shape(0xFF4CD08A, 0xFF4CD08A, 8));
        acc.addView(qiblaDot, new LinearLayout.LayoutParams(dp(14), dp(14)));
        qiblaAcc = tv("دقة مستشعر الهاتف…", 13, cream, false);
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(0, -2, 1f);
        ap.setMargins(dp(10), 0, dp(10), 0);
        acc.addView(qiblaAcc, ap);
        acc.addView(smallBtn("معايرة", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new Pop(PhoneActivity.this).title("معايرة البوصلة")
                    .message("أمسك الهاتف وحرّكه في الهواء على شكل رقم 8 عدة مرات، وابعده عن المعادن والمغناطيس وأغطية الهاتف المغناطيسية. ثم راقب مؤشر الدقة.")
                    .ok("تم", null).show();
            }
        }));
        add(acc, 6);
        qiblaStart();
    }

    private TextView qiblaHead, qiblaAcc;
    private View qiblaDot;

    /** The accuracy line under the dial (green: good, amber: medium, red: poor: calibrate). */
    private void qiblaAccuracy(int a) {
        if (qiblaDot == null || qiblaAcc == null) return;
        int col;
        String t;
        if (a >= SensorManager.SENSOR_STATUS_ACCURACY_HIGH) {
            col = 0xFF4CD08A;
            t = "دقة مستشعر الهاتف جيدة";
        } else if (a == SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM) {
            col = 0xFFE8B84A;
            t = "دقة مستشعر الهاتف متوسطة";
        } else {
            col = 0xFFE5604A;
            t = "دقة مستشعر الهاتف ضعيفة، اضغط «معايرة»";
        }
        qiblaDot.setBackground(shape(col, col, 8));
        qiblaAcc.setText(t);
    }

    private void qiblaStart() {
        qiblaStop();
        final Cities.City city = Cities.selected(this);
        if (tab != 9 || city == null || dial == null) return;
        final double bearing = Qibla.bearing(city.lat, city.lon);
        sm = (SensorManager) getSystemService(SENSOR_SERVICE);
        Sensor rv = sm == null ? null : sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        if (rv == null) {
            if (qiblaText != null) qiblaText.setText("لا توجد بوصلة في هذا الهاتف. استخدم الزاوية المكتوبة من الشمال.");
            return;
        }
        final float decl = new GeomagneticField((float) city.lat, (float) city.lon, 0f, System.currentTimeMillis()).getDeclination();
        qListener = new SensorEventListener() {
            private final float[] rot = new float[9];
            private final float[] ori = new float[3];
            private boolean first = true;

            @Override
            public void onSensorChanged(SensorEvent e) {
                SensorManager.getRotationMatrixFromVector(rot, e.values);
                SensorManager.getOrientation(rot, ori);
                float az = (float) ((Math.toDegrees(ori[0]) + decl + 360.0) % 360.0);
                if (first) {
                    heading = az;
                    first = false;
                } else {
                    float d = ((az - heading + 540f) % 360f) - 180f;
                    heading = (heading + d * 0.2f + 360f) % 360f;
                }
                if (dial != null) dial.set(heading, (float) bearing, true);
                if (qiblaHead != null) qiblaHead.setText(String.format(Locale.US, "%.0f°", heading));
                qiblaAccuracy(e.accuracy);
                if (qiblaText != null) {
                    float diff = (float) (((bearing - heading + 540.0) % 360.0) - 180.0);
                    qiblaText.setText(Math.abs(diff) < 4f ? "أنت متجه نحو القبلة" : diff > 0 ? "در نحو اليمين" : "در نحو اليسار");
                }
            }

            @Override
            public void onAccuracyChanged(Sensor s, int a) {
                qiblaAccuracy(a);
            }
        };
        sm.registerListener(qListener, rv, SensorManager.SENSOR_DELAY_UI);
    }

    private void qiblaStop() {
        if (sm != null && qListener != null) sm.unregisterListener(qListener);
        qListener = null;
    }

    // ---------------------------------------------------------------- permissions
    private void fixPage() {
        title("تأكد أن الأذان يعمل", true);
        TextView note = tv("هاتف Honor يوقف التطبيقات في الخلفية بقوة. كل بند هنا يجب أن يكون ✓. المسارات قد تختلف قليلًا بحسب إصدار MagicOS.", 13, dim, false);
        add(note, 4);
        NotificationManager nm = getSystemService(NotificationManager.class);
        permRow("الإشعارات", nm.areNotificationsEnabled(), new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
        AlarmManager am = getSystemService(AlarmManager.class);
        boolean exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
        permRow("المنبّه الدقيق", exact, Build.VERSION.SDK_INT >= 31
            ? new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + getPackageName())) : null);
        PowerManager pm = getSystemService(PowerManager.class);
        permRow("البطارية: بدون قيود", pm.isIgnoringBatteryOptimizations(getPackageName()),
            new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName())));
        permRow("الموقع (للمدينة تلقائيًا)",
            granted(Manifest.permission.ACCESS_FINE_LOCATION), null);
        permRow("التشغيل التلقائي والخلفية", false, new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:" + getPackageName())));
        TextView tip = tv("بند التشغيل التلقائي لا يمكن قراءته من التطبيق. افتحه وفعّل: التشغيل التلقائي، التشغيل الثانوي، التشغيل في الخلفية (الإعدادات ← التطبيقات ← تشغيل التطبيقات ← الصلاة خير ← إدارة يدويًا). ثم اسحب بطاقة التطبيق لأسفل في شاشة التطبيقات الحديثة لقفله.", 12, dim, false);
        add(tip, 10);
    }

    private void permRow(String name, boolean ok, final Intent open) {
        final boolean location = name.startsWith("الموقع");
        row(name, null, ok ? "✓" : "فتح", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    if (location) requestPermissions(new String[] {Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOC);
                    else if (open != null) startActivity(open);
                } catch (RuntimeException e) {
                    startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName())));
                }
            }
        });
    }

    // ---------------------------------------------------------------- city
    /** A listed city (picked by hand, or found from the internet address). */
    private void applyCity(Cities.City c) {
        sp.edit().putString("city", c.key()).remove("city_custom").apply();
        afterCity();
    }

    /** The exact place found from the phone's location, kept next to the nearest listed city. */
    private void applyPlace(Cities.City base, double lat, double lon, String name) {
        sp.edit().putString("city", base.key()).putString("city_custom", name + "|" + lat + "|" + lon + "|" + (base.country.equals("EG") || base.country.equals("SA") ? "" : java.util.TimeZone.getDefault().getID())).apply();
        afterCity();
    }

    private void afterCity() {
        weather = "";
        Scheduler.scheduleNext(this);
        Reminders.scheduleNext(this);
        occDay = "";
        if (tab == 0 || tab == 3) build();
    }

    private boolean granted(String perm) {
        return checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED;
    }

    /** The phone's own location when allowed (only the coordinates go to the name lookup), otherwise the internet address. */
    private void autoCity(boolean userAsked) {
        if (granted(Manifest.permission.ACCESS_FINE_LOCATION) || granted(Manifest.permission.ACCESS_COARSE_LOCATION)) {
            locate();
        } else if (!locAsked && (userAsked || Cities.selected(this) == null)) {
            locAsked = true;
            requestPermissions(new String[] {Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOC);
        } else {
            byAddress();
        }
    }

    private void locate() {
        final LocationManager lm = getSystemService(LocationManager.class);
        Location best = null;
        try {
            for (String p : lm.getProviders(true)) {
                Location l = lm.getLastKnownLocation(p);
                if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
            }
        } catch (SecurityException ignored) {
            best = null;
        }
        final Location last = best;
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                String provider = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ? LocationManager.NETWORK_PROVIDER
                    : lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ? LocationManager.GPS_PROVIDER : null;
                if (provider != null) {
                    lm.getCurrentLocation(provider, null, getMainExecutor(), new java.util.function.Consumer<Location>() {
                        @Override
                        public void accept(Location l) {
                            useLocation(l != null ? l : last);
                        }
                    });
                    return;
                }
            } catch (RuntimeException ignored) {
                // falls back to the last known place below
            }
        }
        useLocation(last);
    }

    private void useLocation(final Location l) {
        if (l == null) {
            byAddress();
            return;
        }
        final Cities.City base = AutoCity.nearest(l.getLatitude(), l.getLongitude());
        if (base == null) {
            byAddress();
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                String hood = Place.neighbourhood(l.getLatitude(), l.getLongitude());
                final String name = (hood == null ? "" : hood + "، ") + base.ar.replace("|", " ");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        applyPlace(base, l.getLatitude(), l.getLongitude(), name);
                    }
                });
            }
        }, "place").start();
    }

    private void byAddress() {
        AutoCity.find(new AutoCity.Callback() {
            @Override
            public void found(final Cities.City city) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (city != null) {
                            if (Cities.selected(PhoneActivity.this) == null) applyCity(city);
                        } else if (Cities.selected(PhoneActivity.this) == null) {
                            // the automatic way found nothing: ask for the country and city by hand
                            tab = 3;
                            sub = "country";
                            build();
                        }
                    }
                });
            }
        });
    }
}
