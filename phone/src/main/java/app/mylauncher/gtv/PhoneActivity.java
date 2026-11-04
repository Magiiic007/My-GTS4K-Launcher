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
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
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
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * The phone app: one portrait screen with four tabs (home, radio, reminders, settings). It shares the engine with the
 * TV app (prayer times, Hijri dates, reminders, radio) and draws everything with plain views in two looks, Royal and
 * Mihrab. The "tests" section in the settings is temporary.
 */
public class PhoneActivity extends Activity {
    private static final String P = "magiic";
    private static final int REQ_NOTIF = 10, REQ_LOC = 11;

    // ---------------------------------------------------------------- state
    private final Handler h = new Handler(Looper.getMainLooper());
    private SharedPreferences sp;
    private int tab;
    private String sub;
    private int look;
    private int bg1, bg2, gold, cream, dim, card, line, hl;
    private LinearLayout body;
    private ScrollView scroll;
    private LinearLayout nav;
    private boolean autoTried;
    private boolean locAsked;
    private int dbg;          // test: 0 none, 1 occasion, 2 Ramadan, 3 Friday
    private String weather = "";
    private int[] occNear;
    private String occDay = "";
    private int shownNext = -1;
    private FlipDetector testFlip;
    private int flipCount;

    // views of the home screen that change every second
    private TextView clockTv, apTv, nameTv, cdTv, altTv, iqLabel, iqValue, weatherTv;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            tick();
            if ("flip".equals(sub) && tab == 3) flipInfo();
            h.postDelayed(this, "flip".equals(sub) ? 200L : 1000L);
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
            if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
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
        build();
    }

    @Override
    protected void onResume() {
        super.onResume();
        RadioService.listen(radioListener);
        RadioService.resumeIfCutOff(this);
        h.post(ticker);
        if (!autoTried && sp.getBoolean("ph_first", false)
            && (Cities.selected(this) == null || sp.getBoolean("city_auto", true))) {
            autoTried = true;
            autoCity(false);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        RadioService.unlisten(radioListener);
        h.removeCallbacks(ticker);
        stopFlipTest();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        if (code == REQ_NOTIF) {
            sp.edit().putBoolean("ph_first", true).apply();
            autoTried = true;
            autoCity(false);
            new AlertDialog.Builder(this).setTitle("خطوة أخيرة مهمة")
                .setMessage("لكي يعمل الأذان والراديو والشاشة مقفولة، افتح صفحة «تأكد أن الأذان يعمل» وفعّل كل بند فيها.")
                .setPositiveButton("فتح الصفحة", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        tab = 3;
                        sub = "fix";
                        build();
                    }
                }).setNegativeButton("لاحقًا", null).show();
            return;
        }
        if (code == REQ_LOC) autoCity(false);
        if (tab == 3) build();
    }

    @Override
    public void onBackPressed() {
        if (sub != null) {
            if (sub.equals("flip")) stopFlipTest();
            sub = backTarget(sub);
            build();
        } else if (tab != 0) {
            tab = 0;
            build();
        } else {
            super.onBackPressed();
        }
    }

    private String backTarget(String s) {
        if (s.equals("up")) return "occ";
        if (s.equals("flip")) return "tests";
        if (s.startsWith("item:")) return itemBack;
        if (s.equals("country") || s.equals("cities")) return null;
        return null;
    }

    // ---------------------------------------------------------------- look
    private void palette() {
        if (look == 1) {
            bg1 = 0xFF091230; bg2 = 0xFF142453; gold = 0xFFDCB860; cream = 0xFFF1E8D0; dim = 0xFFB4BDDB;
        } else {
            bg1 = 0xFF062B1F; bg2 = 0xFF0D4130; gold = 0xFFE8B84A; cream = 0xFFF6ECD2; dim = 0xFFB9C9BD;
        }
        card = 0x14FFFFFF;
        line = (gold & 0x00FFFFFF) | 0x59000000;
        hl = (gold & 0x00FFFFFF) | 0x33000000;
    }

    private int dp(int v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }

    private GradientDrawable shape(int fill, int stroke, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radiusDp));
        if (stroke != 0) g.setStroke(dp(1), stroke);
        return g;
    }

    private void root() {
        getWindow().setStatusBarColor(bg1);
        getWindow().setNavigationBarColor(bg2);
        FrameLayout f = new FrameLayout(this);
        f.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[] {bg1, bg2});
        Deco.Stars stars = new Deco.Stars((cream & 0x00FFFFFF) | 0x1A000000, dp(56));
        f.setBackground(new LayerDrawable(new android.graphics.drawable.Drawable[] {g, stars}));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(14), dp(10), dp(14), dp(16));
        scroll.addView(body, new ViewGroup.LayoutParams(-1, -2));
        col.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setBackgroundColor(0x73000000);
        col.addView(nav, new LinearLayout.LayoutParams(-1, dp(58)));
        f.addView(col, new FrameLayout.LayoutParams(-1, -1));
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
            TextView b = tv("›", 22, gold, true);
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
        TextView t1 = tv(t, 22, gold, true);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, -2);
        p.setMarginStart(dp(back ? 10 : 2));
        r.addView(t1, p);
        add(r, 6);
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
    private void build() {
        palette();
        clockTv = apTv = nameTv = cdTv = altTv = iqLabel = iqValue = weatherTv = null;
        body.removeAllViews();
        String[] names = {"الرئيسية", "الراديو", "التذكيرات", "الإعدادات"};
        nav.removeAllViews();
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            TextView t = tv(names[i], 12, i == tab ? gold : dim, true);
            t.setGravity(Gravity.CENTER);
            t.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if ("flip".equals(sub)) stopFlipTest();
                    tab = idx;
                    sub = null;
                    build();
                }
            });
            nav.addView(t, new LinearLayout.LayoutParams(0, -1, 1f));
        }
        nav.setBackgroundColor(0x73000000);
        if (tab == 0) home();
        else if (tab == 1) radio();
        else if (tab == 2) reminders();
        else settings();
        scroll.scrollTo(0, 0);
        tick();
    }

    // ---------------------------------------------------------------- home
    private void home() {
        final Cities.City city = Cities.selected(this);
        if (city == null) {
            title("Magiic", false);
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

        // top chips
        LinearLayout top = hbox();
        weatherTv = chip(weather.isEmpty() ? city.name(true) : city.name(true) + " · " + weather);
        top.addView(weatherTv);
        View sp1 = new View(this);
        top.addView(sp1, new LinearLayout.LayoutParams(0, 1, 1f));
        top.addView(chip(sp.getBoolean("city_auto", true) ? "الموقع تلقائي" : "الموقع يدوي"));
        add(top, 0);
        if (weather.isEmpty()) {
            Weather.fetch(city, new Weather.Callback() {
                @Override
                public void onResult(int tempC, int code) {
                    weather = tempC + "°";
                    if (weatherTv != null) weatherTv.setText(city.name(true) + " · " + weather);
                }

                @Override
                public void onError() {
                }
            });
        }

        // clock and dates
        LinearLayout clock = hbox();
        clock.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        clock.setGravity(Gravity.CENTER);
        clockTv = tv("--:--", 62, gold, true);
        apTv = tv("", 15, cream, true);
        apTv.setPadding(dp(8), 0, 0, 0);
        clock.addView(clockTv);
        clock.addView(apTv);
        add(clock, 4);
        TextView d1 = tv(Hijri.weekdayAr(cal) + "، " + Hijri.gregorianAr(cal), 14, cream, false);
        d1.setGravity(Gravity.CENTER);
        add(d1, 0);
        TextView d2 = tv(Hijri.hijriAr(hij), 14, gold, true);
        d2.setGravity(Gravity.CENTER);
        add(d2, 0);

        // the next prayer
        PrayerCalc.Event ad = PrayerCalc.nextEvent(city, now, adj, new int[5]);
        shownNext = ad == null ? -1 : ad.index;
        LinearLayout hero = vbox();
        hero.setGravity(Gravity.CENTER_HORIZONTAL);
        Deco.Frame frame = new Deco.Frame(look == 1, (gold & 0x00FFFFFF) | 0x99000000, getResources().getDisplayMetrics().density);
        hero.setBackground(new LayerDrawable(new android.graphics.drawable.Drawable[] {shape(card, line, 22), frame}));
        hero.setPadding(dp(14), dp(12), dp(14), dp(12));
        TextView l1 = tv("الصلاة القادمة", 12, dim, true);
        l1.setPadding(0, dp(8), 0, 0);
        hero.addView(l1);
        nameTv = tv("", 30, gold, true);
        hero.addView(nameTv);
        cdTv = tv("00:00:00", 44, cream, true);
        cdTv.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        hero.addView(cdTv);
        altTv = tv("", 14, cream, true);
        altTv.setGravity(Gravity.CENTER);
        hero.addView(altTv, lp(-2, dp(28), 4));
        LinearLayout iq = hbox();
        iq.setBackground(shape(0x38000000, line, 14));
        iq.setPadding(dp(12), dp(8), dp(12), dp(8));
        iqLabel = tv("", 12, dim, false);
        iqValue = tv("", 18, gold, true);
        iq.addView(iqLabel, new LinearLayout.LayoutParams(0, -2, 1f));
        iq.addView(iqValue);
        hero.addView(iq, lp(-1, dp(54), 8));
        add(hero, 12);

        // the six times
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
            r.setPadding(dp(14), dp(9), dp(14), dp(9));
            LinearLayout v = vbox();
            v.addView(tv(name, 18, cream, true));
            if (idx >= 0 && iqm[idx] > 0) v.addView(tv(getString(R.string.iqama_minutes, iqm[idx]), 11, dim, false));
            r.addView(v, new LinearLayout.LayoutParams(0, -2, 1f));
            TextView tt = tv(ap(PrayerCalc.time(this, city, at)), 19, gold, true);
            tt.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
            r.addView(tt);
            if (at < now && !next) r.setAlpha(0.6f);
            add(r, 6);
        }

        // verse of the day
        String[] vt = getResources().getStringArray(R.array.verse_text);
        String[] vr = getResources().getStringArray(R.array.verse_ref);
        int vi = (cal.get(Calendar.YEAR) * 366 + cal.get(Calendar.DAY_OF_YEAR)) % vt.length;
        LinearLayout vc = vbox();
        vc.setBackground(shape(card, line, 18));
        vc.setPadding(dp(14), dp(12), dp(14), dp(12));
        TextView v1 = tv(vt[vi], 20, cream, false);
        v1.setGravity(Gravity.CENTER);
        v1.setLineSpacing(0, 1.3f);
        TextView v2 = tv("آية اليوم · " + vr[vi], 12, gold, true);
        v2.setGravity(Gravity.CENTER);
        vc.addView(v1);
        vc.addView(v2);
        add(vc, 12);
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
        if (dbg == 1) return "باقي 3 أيام على يوم عرفة";
        if (dbg == 2) return "باقي 12 يوم على رمضان";
        if (dbg == 3) return "جمعة مباركة · الأذان الأول 11:45";
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
        String base = "الأذان " + ap(PrayerCalc.time(this, city, ad.at)) + " · " + city.name(true);
        String extra = extraLine(city, now, adj);
        if (extra != null) extra = ap(extra);
        altTv.setText(extra != null && (now / 3000L) % 2L == 1L ? extra : base);
        if (ev != null && ev.iqama) {
            iqLabel.setText("الإقامة بعد");
            long s = Math.max(0, (ev.at - now) / 1000L);
            iqValue.setText(String.format(Locale.US, "%02d:%02d", s / 60, s % 60));
        } else {
            iqLabel.setText("ذكر");
            String[] dh = getResources().getStringArray(R.array.dhikr);
            iqValue.setText(dh[(int) ((now / 5000L) % dh.length)]);
        }
    }

    // ---------------------------------------------------------------- radio
    private void radio() {
        title("الراديو", false);
        boolean group = false;
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
        TextView n = tv("يتوقف الراديو وقت الأذان ويعود وحده، ويرجع بعد إعادة التشغيل أو التحديث.", 12, dim, false);
        add(n, 12);
    }

    // ---------------------------------------------------------------- reminders
    private String itemBack;

    private void reminders() {
        final List<Reminders.Item> list = Reminders.load(this);
        if (sub == null) {
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
        row("الوقت", String.format(Locale.US, "%02d:%02d", it.hour, it.minute), "›", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new TimePickerDialog(PhoneActivity.this, new TimePickerDialog.OnTimeSetListener() {
                    @Override
                    public void onTimeSet(android.widget.TimePicker p, int hour, int minute) {
                        it.hour = hour;
                        it.minute = minute;
                        saveRem(list);
                        build();
                    }
                }, it.hour, it.minute, true).show();
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
                new AlertDialog.Builder(PhoneActivity.this).setTitle(title).setView(in)
                    .setPositiveButton("حفظ", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface d, int w) {
                            s.set(in.getText().toString().trim());
                            build();
                        }
                    }).setNegativeButton("إلغاء", null).show();
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
        new AlertDialog.Builder(this).setTitle("اختر الأيام")
            .setMultiChoiceItems(names, on, new DialogInterface.OnMultiChoiceClickListener() {
                @Override
                public void onClick(DialogInterface d, int which, boolean checked) {
                    on[which] = checked;
                }
            })
            .setPositiveButton("حفظ", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int w) {
                    int m = 0;
                    for (int k = 0; k < n; k++) if (on[k]) m |= 1 << k;
                    if (white) it.hijriMask = m;
                    else it.daysMask = m;
                    saveRem(list);
                    build();
                }
            }).setNegativeButton("إلغاء", null).show();
    }

    private void numberDialog(String title, int min, int max, int cur, final IntSetter s) {
        final NumberPicker np = new NumberPicker(this);
        np.setMinValue(min);
        np.setMaxValue(max);
        np.setValue(Math.max(min, Math.min(max, cur)));
        np.setWrapSelectorWheel(false);
        FrameLayout f = new FrameLayout(this);
        f.addView(np, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        new AlertDialog.Builder(this).setTitle(title).setView(f)
            .setPositiveButton("حفظ", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int w) {
                    s.set(np.getValue());
                }
            }).setNegativeButton("إلغاء", null).show();
    }

    // ---------------------------------------------------------------- settings
    private void settings() {
        if ("country".equals(sub) || "cities".equals(sub)) {
            cityPicker();
            return;
        }
        if ("prayers".equals(sub)) {
            prayersPage();
            return;
        }
        if ("fix".equals(sub)) {
            fixPage();
            return;
        }
        if ("tests".equals(sub)) {
            testsPage();
            return;
        }
        if ("flip".equals(sub)) {
            flipPage();
            return;
        }
        title("الإعدادات", false);
        Cities.City c = Cities.selected(this);
        section("الموقع والمواقيت");
        switchRow("المدينة تلقائيًا", c == null ? "لم تُحدَّد بعد" : c.name(true), sp.getBoolean("city_auto", true),
            new CompoundListener() {
                @Override
                public void changed(boolean on) {
                    sp.edit().putBoolean("city_auto", on).apply();
                    if (on) autoCity(true);
                }
            });
        row("الدولة والمدينة", null, "›", go("country"));
        row("الأذان والإقامة", "تعديل الدقائق ومدة الإقامة لكل صلاة", "›", go("prayers"));
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
        section("الشكل");
        row("شكل التطبيق", "تلقائي: الملكي نهارًا والمحراب ليلًا بحسب وضع الهاتف", look == 0 ? "ملكي" : "المحراب", null);
        section("التشغيل في الخلفية");
        row("تأكد أن الأذان يعمل", "الإشعارات والمنبّه والبطارية", "›", go("fix"));
        section("أخرى");
        row("تحديث التطبيق", "النسخة " + PhoneUpdater.installedName(this), "›", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                PhoneUpdater.check(PhoneActivity.this);
            }
        });
        section("تجارب مؤقتة");
        row("أزرار التجربة", "تشغيل الأذان والإقامة والتذكيرات والحساس", "›", go("tests"));
    }

    private void cityPicker() {
        if ("country".equals(sub)) {
            title("الدولة", true);
            row("مصر", null, "›", countryGo("EG"));
            row("السعودية", null, "›", countryGo("SA"));
            return;
        }
        title(country.equals("EG") ? "مدن مصر" : "مدن السعودية", true);
        Cities.City[] arr = country.equals("EG") ? Cities.EGYPT : Cities.SAUDI;
        for (final Cities.City c : arr) {
            row(c.name(true), null, null, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    sp.edit().putBoolean("city_auto", false).apply();
                    applyCity(c);
                    sub = null;
                    build();
                }
            });
        }
    }

    private String country = "EG";

    private View.OnClickListener countryGo(final String code) {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                country = code;
                sub = "cities";
                build();
            }
        };
    }

    private void prayersPage() {
        title("الأذان والإقامة", true);
        final int[] adj = PrayerPrefs.adjust(this);
        final int[] iq = PrayerPrefs.iqama(this);
        for (int i = 0; i < 5; i++) {
            final int idx = i;
            row(PrayerCalc.name(this, i, false), "الإقامة: " + PrayerPrefs.iqamaText(this, iq[i])
                + " · التعديل: " + PrayerPrefs.adjustText(this, adj[i]), "›", new View.OnClickListener() {
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
    }

    private void prayerDialog(final int idx) {
        LinearLayout l = hbox();
        l.setGravity(Gravity.CENTER);
        final NumberPicker a = new NumberPicker(this);
        a.setMinValue(0);
        a.setMaxValue(60);
        a.setValue(PrayerPrefs.iqama(this)[idx]);
        final NumberPicker b = new NumberPicker(this);
        b.setMinValue(-10);
        b.setMaxValue(10);
        b.setValue(PrayerPrefs.adjust(this)[idx]);
        LinearLayout c1 = vbox();
        c1.setGravity(Gravity.CENTER);
        c1.addView(new TextView(this) {{ setText("الإقامة (دقائق)"); }});
        c1.addView(a);
        LinearLayout c2 = vbox();
        c2.setGravity(Gravity.CENTER);
        c2.addView(new TextView(this) {{ setText("تعديل الوقت (دقائق)"); }});
        c2.addView(b);
        l.addView(c1, new LinearLayout.LayoutParams(0, -2, 1f));
        l.addView(c2, new LinearLayout.LayoutParams(0, -2, 1f));
        new AlertDialog.Builder(this).setTitle(PrayerCalc.name(this, idx, false)).setView(l)
            .setPositiveButton("حفظ", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int w) {
                    PrayerPrefs.setIqama(PhoneActivity.this, idx, a.getValue());
                    PrayerPrefs.setAdjust(PhoneActivity.this, idx, b.getValue());
                    Scheduler.scheduleNext(PhoneActivity.this);
                    build();
                }
            }).setNegativeButton("إلغاء", null).show();
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
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED, null);
        permRow("التشغيل التلقائي والخلفية", false, new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:" + getPackageName())));
        TextView tip = tv("بند التشغيل التلقائي لا يمكن قراءته من التطبيق. افتحه وفعّل: التشغيل التلقائي، التشغيل الثانوي، التشغيل في الخلفية (الإعدادات ← التطبيقات ← تشغيل التطبيقات ← Magiic ← إدارة يدويًا). ثم اسحب بطاقة التطبيق لأسفل في شاشة التطبيقات الحديثة لقفله.", 12, dim, false);
        add(tip, 10);
    }

    private void permRow(String name, boolean ok, final Intent open) {
        final boolean location = name.startsWith("الموقع");
        row(name, null, ok ? "✓" : "فتح", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    if (location) requestPermissions(new String[] {Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOC);
                    else if (open != null) startActivity(open);
                } catch (RuntimeException e) {
                    startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName())));
                }
            }
        });
    }

    // ---------------------------------------------------------------- city
    private void applyCity(Cities.City c) {
        sp.edit().putString("city", c.key()).apply();
        weather = "";
        Scheduler.scheduleNext(this);
        Reminders.scheduleNext(this);
        occDay = "";
        if (tab == 0 || tab == 3) build();
    }

    /** The phone's own location when allowed (nothing is sent anywhere), otherwise the internet address. */
    private void autoCity(boolean userAsked) {
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            LocationManager lm = getSystemService(LocationManager.class);
            Location best = null;
            try {
                for (String p : lm.getProviders(true)) {
                    Location l = lm.getLastKnownLocation(p);
                    if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
                }
            } catch (SecurityException ignored) {
                best = null;
            }
            if (best != null) {
                Cities.City c = AutoCity.nearest(best.getLatitude(), best.getLongitude());
                if (c != null) {
                    sp.edit().putBoolean("city_auto", true).apply();
                    applyCity(c);
                    return;
                }
            }
        } else if (!locAsked && (userAsked || Cities.selected(this) == null)) {
            locAsked = true;
            requestPermissions(new String[] {Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOC);
            return;
        }
        AutoCity.find(new AutoCity.Callback() {
            @Override
            public void found(final Cities.City city) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (city != null) {
                            sp.edit().putBoolean("city_auto", true).apply();
                            applyCity(city);
                        } else if (Cities.selected(PhoneActivity.this) == null) {
                            toast("تعذّر تحديد المدينة، اخترها يدويًا من الإعدادات");
                        }
                    }
                });
            }
        });
    }

    // ---------------------------------------------------------------- temporary tests
    private void testsPage() {
        title("أزرار التجربة", true);
        TextView n = tv("هذه الأزرار مؤقتة وستُحذف بعد انتهاء التجربة.", 12, dim, false);
        add(n, 4);
        section("الأذان والإقامة");
        row("تشغيل الأذان الآن", "اقلب الهاتف على وجهه لإيقافه", "تشغيل", testAdhan(PrayerCalc.ASR, false));
        row("أذان الفجر الآن", null, "تشغيل", testAdhan(PrayerCalc.FAJR, false));
        row("الإقامة الآن", null, "تشغيل", testAdhan(PrayerCalc.MAGHRIB, true));
        row("أذان بعد دقيقة", "اقفل الشاشة وضع الهاتف جانبًا وانتظر", "ابدأ", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AlarmManager am = getSystemService(AlarmManager.class);
                Intent i = new Intent(PhoneActivity.this, AdhanReceiver.class).setAction("app.mylauncher.gtv.ADHAN_TEST")
                    .putExtra("index", PrayerCalc.ASR).putExtra("test", true);
                PendingIntent pi = PendingIntent.getBroadcast(PhoneActivity.this, 99, i,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                long at = System.currentTimeMillis() + 60000L;
                try {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
                    toast("سيعمل الأذان بعد دقيقة");
                } catch (SecurityException e) {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
                    toast("المنبّه الدقيق غير مسموح، قد يتأخر قليلًا");
                }
            }
        });
        long rx = sp.getLong("dbg_rx", 0L);
        row("آخر وصول لمنبّه الأذان", rx == 0 ? "لم يصل أي منبّه بعد" : new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date(rx)), null, null);
        String err = sp.getString("dbg_err", "");
        row("آخر خطأ في تشغيل الخدمة", err.isEmpty() ? "لا يوجد" : err, null, null);
        section("الحساس");
        row("اختبار قلب الهاتف وهزّه", "يعرض قراءات الحساس مباشرة", "›", go("flip"));
        section("التذكيرات والعدّاد");
        row("إشعار تذكير الآن", null, "إرسال", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ArrayList<String> t = new ArrayList<>();
                ArrayList<String> x = new ArrayList<>();
                ArrayList<Integer> m = new ArrayList<>();
                t.add("تذكير تجريبي");
                x.add("غدًا: يوم عرفة");
                m.add(0);
                startForegroundService(new Intent(PhoneActivity.this, ReminderService.class)
                    .putStringArrayListExtra("titles", t).putStringArrayListExtra("texts", x)
                    .putIntegerArrayListExtra("minutes", m));
            }
        });
        row("تجربة العدّاد تحت المواقيت", dbg == 0 ? "الوضع العادي" : dbg == 1 ? "مناسبة" : dbg == 2 ? "رمضان" : "الجمعة",
            "تبديل", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dbg = (dbg + 1) % 4;
                    build();
                }
            });
    }

    private View.OnClickListener testAdhan(final int index, final boolean iqama) {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startForegroundService(new Intent(PhoneActivity.this, AdhanService.class)
                    .putExtra("index", index).putExtra("friday", false).putExtra("test", true)
                    .putExtra("iqama", iqama));
            }
        };
    }

    private TextView flipText;

    private void flipPage() {
        title("اختبار الحساس", true);
        TextView n = tv("اضغط ابدأ، ثم اقلب الهاتف على وجهه أو هزّه وهو على وجهه. العدّاد يزيد كلما كانت الحركة ستوقف الأذان.", 13, dim, false);
        add(n, 4);
        flipText = tv("", 18, cream, true);
        flipText.setBackground(shape(card, line, 14));
        flipText.setPadding(dp(14), dp(12), dp(14), dp(12));
        add(flipText, 10);
        Button b = button("ابدأ الاختبار", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startFlipTest();
            }
        });
        add(b, 10);
        Button s = button("إيقاف الاختبار", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopFlipTest();
            }
        });
        add(s, 8);
        flipInfo();
    }

    private void startFlipTest() {
        stopFlipTest();
        flipCount = 0;
        testFlip = new FlipDetector(this, new Runnable() {
            @Override
            public void run() {
                flipCount++;
                h.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (testFlip != null) testFlip.start();
                    }
                }, 1500L);
            }
        });
        if (!testFlip.available()) {
            toast("لا يوجد حساس تسارع في هذا الهاتف");
            return;
        }
        testFlip.start();
    }

    private void stopFlipTest() {
        if (testFlip != null) {
            testFlip.stop();
            testFlip = null;
        }
    }

    private void flipInfo() {
        if (flipText == null) return;
        flipText.setText(String.format(Locale.US, "z = %.1f\nالحركة = %.1f (الحد %.1f)\nعلى الوجه: %s\nمرات الإيقاف: %d\nأذان يعمل: %s",
            FlipDetector.lastZ, FlipDetector.lastMove, FlipDetector.MOVE_THRESHOLD,
            FlipDetector.lastDown ? "نعم" : "لا", flipCount, AdhanService.playing ? "نعم" : "لا"));
    }
}
