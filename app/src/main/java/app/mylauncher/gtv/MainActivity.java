package app.mylauncher.gtv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.Collator;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The home screen, laid out on a fixed canvas that is scaled to the TV (see Stage) so it matches the design pictures:
 * the settings list (left), the prayer times (centre), the clock, dates and countdowns (right) and a row of apps
 * (bottom), with the weather, radio and status icons in the top corner. Everything can be selected with the remote.
 * Two looks, chosen in the settings list: royal (green and gold) and kids (pink).
 */
public class MainActivity extends Activity {

    /** One app in the row. */
    private static final class AppItem {
        String label;
        ComponentName component;
        Drawable image;
        /** A wide TV banner (fills the tile) rather than a square icon (sits in the middle of it). */
        boolean banner;
    }

    /** Every app found, and the ones shown (without the hidden ones, in the user's order). */
    private final List<AppItem> allApps = new ArrayList<>();
    private final List<AppItem> apps = new ArrayList<>();
    private LinearLayout appRow;
    private TextView empty;
    private String focusPkg;

    private static final String PREFS = "magiic";
    private static final long WEATHER_EVERY_MS = 30L * 60L * 1000L;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView weatherView;
    private TextView radioBtn;
    private TextView permRow;
    private TextView noticeChip;
    private Ambience ambience;
    private Sparkle sparkle;
    private FrameLayout rootView;
    private final Runnable ambienceTick = new Runnable() {
        @Override
        public void run() {
            ambience.update();
            handler.postDelayed(this, 60000L);
        }
    };
    private LinearLayout settingsList;
    private ImageView art;
    private long weatherAt;
    private PrayerBoard board;
    private StatusIcons status;
    private final RadioService.Listener radioListener = new RadioService.Listener() {
        @Override
        public void onRadio(int state) {
            updateRadioButton();
        }
    };
    private final Runnable weatherTick = new Runnable() {
        @Override
        public void run() {
            weatherAt = System.currentTimeMillis();
            loadWeather();
            handler.postDelayed(this, WEATHER_EVERY_MS);
        }
    };
    /** Run after any prayer or iqama setting changes: new alarm, new numbers on screen. */
    private final Runnable prayerChanged = new Runnable() {
        @Override
        public void run() {
            Scheduler.scheduleNext(MainActivity.this);
            board.refresh();
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        final boolean kids = Theme.kids(this);
        Geo g = Geo.of(this);

        final FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Theme.edgeColour(this));
        art = new ImageView(this);
        art.setImageResource(kids ? R.drawable.bg_kids : R.drawable.bg_royal);
        art.setScaleType(ImageView.ScaleType.FIT_CENTER);
        root.addView(art, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        rootView = root;
        // The veil of the day's light sits on the picture, under everything else.
        View veil = new View(this);
        root.addView(veil, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        ambience = new Ambience(this, veil);

        final FrameLayout stage = new FrameLayout(this);
        stage.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        Motion.fadeIn(root, 350L);
        root.addView(stage, new FrameLayout.LayoutParams(Stage.W, Stage.H, Gravity.TOP | Gravity.LEFT));
        root.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
                Stage.fit(root, stage);
            }
        });

        // The twinkling stars come first so everything else is drawn over them.
        sparkle = new Sparkle(this, kids);
        Stage.add(stage, sparkle, 0, 0, Stage.W, Stage.H);

        // ---------------------------------------------------------------- top-left: weather, radio, status icons
        weatherView = chip(0, null);
        weatherView.setTextSize(TypedValue.COMPLEX_UNIT_PX, 15);
        weatherView.setMaxLines(2);
        weatherView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                chooseCountry();
            }
        });
        Stage.add(stage, weatherView, 44, 20, 168, 92);

        // A notice that only appears when something needs the person (see Health); hidden otherwise.
        noticeChip = chip(R.drawable.ic_warning, "");
        noticeChip.setTextSize(TypedValue.COMPLEX_UNIT_PX, 15);
        noticeChip.setMaxLines(1);
        noticeChip.setVisibility(View.GONE);
        Stage.add(stage, noticeChip, 1090, 22, 1336, 62);

        radioBtn = chip(R.drawable.ic_radio, null);
        radioBtn.setTextSize(TypedValue.COMPLEX_UNIT_PX, 14);
        radioBtn.setMaxLines(1);
        radioBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                chooseRadio();
            }
        });
        Stage.add(stage, radioBtn, 176, 20, 296, 54);

        status = new StatusIcons(this, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SettingsMenu.launch(MainActivity.this, Settings.ACTION_WIFI_SETTINGS);
            }
        });
        Stage.add(stage, status.view, 176, 58, 296, 92);
        updateRadioButton();

        // ---------------------------------------------------------------- the clock, prayers and countdowns
        board = new PrayerBoard(this, stage,
            new Runnable() {
                @Override
                public void run() {
                    chooseCountry();
                }
            },
            new Runnable() {
                @Override
                public void run() {
                    chooseHijri();
                }
            },
            new Runnable() {
                @Override
                public void run() {
                    SettingsMenu.launch(MainActivity.this, Settings.ACTION_DATE_SETTINGS);
                }
            },
            prayerChanged);

        // ---------------------------------------------------------------- the settings list
        Stage.add(stage, buildSettingsList(), g.settings[0], g.settings[1], g.settings[2], g.settings[3]);

        // ---------------------------------------------------------------- the apps row
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setClipChildren(false);
        appRow = new LinearLayout(this);
        appRow.setOrientation(LinearLayout.HORIZONTAL);
        appRow.setGravity(Gravity.CENTER);
        scroll.addView(appRow, new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        Stage.add(stage, scroll, g.bar[0], g.bar[1], g.bar[2], g.bar[3]);

        empty = Stage.text(this, getString(R.string.no_apps), 24, Theme.cardText(this), true);
        empty.setGravity(Gravity.CENTER);
        empty.setVisibility(View.GONE);
        Stage.add(stage, empty, g.bar[0], g.bar[1], g.bar[2], g.bar[3]);

        setContentView(root);
    }

    // ---------------------------------------------------------------- the settings list (left)

    private View buildSettingsList() {
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        LinearLayout list = new LinearLayout(this);
        settingsList = list;
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list, new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // The look: a list with the two interfaces (the one in use is marked).
        addRow(list, R.drawable.ic_palette, R.string.menu_theme, new Runnable() {
            @Override
            public void run() {
                chooseLook();
            }
        });
        addRow(list, R.drawable.ic_bell, R.string.menu_adhan, new Runnable() {
            @Override
            public void run() {
                TimingDialogs.open(MainActivity.this, prayerChanged);
            }
        });
        addRow(list, R.drawable.ic_radio, R.string.menu_radio, new Runnable() {
            @Override
            public void run() {
                chooseRadio();
            }
        });
        addRow(list, R.drawable.ic_apps, R.string.menu_apps, new Runnable() {
            @Override
            public void run() {
                SettingsMenu.apps(MainActivity.this, appList(), new Runnable() {
                    @Override
                    public void run() {
                        applyHidden();
                    }
                });
            }
        });
        addRow(list, R.drawable.ic_calendar, R.string.menu_hijri, new Runnable() {
            @Override
            public void run() {
                chooseHijri();
            }
        });
        addRow(list, R.drawable.ic_shield, R.string.menu_clean, new Runnable() {
            @Override
            public void run() {
                Cleaner.open(MainActivity.this);
            }
        });
        addRow(list, R.drawable.ic_sunny, R.string.menu_saver, new Runnable() {
            @Override
            public void run() {
                SettingsMenu.launch(MainActivity.this, Settings.ACTION_DREAM_SETTINGS, R.string.saver_tip);
            }
        });
        addRow(list, R.drawable.ic_update, R.string.menu_update, new Runnable() {
            @Override
            public void run() {
                Updater.menu(MainActivity.this);
            }
        });
        addRow(list, R.drawable.ic_star, R.string.menu_reminders, new Runnable() {
            @Override
            public void run() {
                ReminderDialogs.open(MainActivity.this);
            }
        });
        addRow(list, R.drawable.ic_account, R.string.menu_device, new Runnable() {
            @Override
            public void run() {
                SettingsMenu.device(MainActivity.this);
            }
        });
        addRow(list, R.drawable.ic_home, R.string.menu_stock, new Runnable() {
            @Override
            public void run() {
                StockHome.menu(MainActivity.this);
            }
        });
        permRow = addRow(list, R.drawable.ic_warning, R.string.perm_btn, new Runnable() {
            @Override
            public void run() {
                askOverlayPermission();
            }
        });
        return scroll;
    }

    /** The two looks, in a list; picking the other one restarts the screen in it. */
    private void chooseLook() {
        String[] names = {getString(R.string.theme_royal), getString(R.string.theme_kids)};
        new AlertDialog.Builder(this)
            .setTitle(R.string.menu_theme)
            .setSingleChoiceItems(names, Theme.id(this), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int which) {
                    d.dismiss();
                    if (which != Theme.id(MainActivity.this)) {
                        Theme.set(MainActivity.this, which);
                        // Fade out, switch, and the new look fades in.
                        rootView.animate().alpha(0f).setDuration(220L).withEndAction(new Runnable() {
                            @Override
                            public void run() {
                                recreate();
                            }
                        }).start();
                    }
                }
            })
            .setNegativeButton(R.string.close, null)
            .show();
    }

    /** One row of the settings list: the icon on the left, the words on the right, as in the design. */
    private TextView addRow(LinearLayout list, int icon, int text, final Runnable click) {
        final FrameLayout row = new FrameLayout(this);
        row.setBackground(Theme.button(this, 22));
        row.setFocusable(true);
        row.setClickable(true);
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                click.run();
            }
        });
        ImageView iv = new ImageView(this);
        iv.setImageResource(icon);
        iv.setColorFilter(Theme.kids(this) ? Color.parseColor("#FFFFF0CC") : Theme.cardGold(this));
        FrameLayout.LayoutParams ilp = new FrameLayout.LayoutParams(30, 30, Gravity.LEFT | Gravity.CENTER_VERTICAL);
        ilp.leftMargin = 14;
        row.addView(iv, ilp);
        final TextView label = Stage.text(this, getString(text), 18, Theme.cardText(this), true);
        label.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        label.setPadding(52, 0, 18, 0);
        Stage.fitOneLine(label, 11, 18);
        row.addView(label, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        final Drawable ring = Theme.ring(this, 22);
        row.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean has) {
                v.setForeground(has ? ring : null);
                Motion.pop(v, has, 1.03f);
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 46);
        lp.bottomMargin = 5;
        lp.leftMargin = 2;
        lp.rightMargin = 2;
        list.addView(row, lp);
        // The caller wants to hide a row (the permission one): hide the whole row, not just the words.
        label.setTag(row);
        return label;
    }

    /** A small selectable chip (weather, radio). */
    private TextView chip(int icon, String text) {
        TextView b = Stage.text(this, text == null ? "" : text, 15, Theme.cardText(this), true);
        b.setGravity(Gravity.CENTER);
        b.setPadding(10, 4, 10, 4);
        b.setBackground(Theme.button(this, 14));
        b.setFocusable(true);
        b.setClickable(true);
        if (icon != 0) {
            b.setCompoundDrawablePadding(6);
            b.setCompoundDrawablesRelativeWithIntrinsicBounds(
                tinted(icon, Theme.cardText(this), 20), null, null, null);
        }
        final Drawable ring = Theme.ring(this, 14);
        b.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean has) {
                v.setForeground(has ? ring : null);
                Motion.pop(v, has, 1.06f);
            }
        });
        return b;
    }

    private Drawable tinted(int res, int colour, int size) {
        Drawable d = getDrawable(res).mutate();
        d.setTint(colour);
        d.setBounds(0, 0, size, size);
        return d;
    }

    // ---------------------------------------------------------------- lifecycle

    /** As the home screen, Back does nothing: leaving it would just bring it straight back. */
    @Override
    public void onBackPressed() {
        // Stay on the home screen.
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadApps();
        // The weather is not fetched again each time the home screen comes back: only when it is 30 minutes old.
        handler.removeCallbacks(weatherTick);
        long age = System.currentTimeMillis() - weatherAt;
        handler.postDelayed(weatherTick, weatherAt == 0L || age >= WEATHER_EVERY_MS ? 0L : WEATHER_EVERY_MS - age);
        board.start();
        status.start();
        RadioService.listen(radioListener);
        updateRadioButton();
        ((View) permRow.getTag()).setVisibility(Settings.canDrawOverlays(this) ? View.GONE : View.VISIBLE);
        wireFocus();
        // Line up the alarms for the next prayer and the next reminder (also set again after a restart). Done off the
        // main thread so opening the home screen is never held up by it.
        final android.content.Context app = getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                Scheduler.scheduleNext(app);
                Reminders.scheduleNext(app);
            }
        }).start();
        ambience.update();
        handler.removeCallbacks(ambienceTick);
        handler.postDelayed(ambienceTick, 60000L);
        sparkle.start();
        refreshNotices();
        Updater.checkIfDue(this, new Runnable() {
            @Override
            public void run() {
                refreshNotices();
            }
        });
    }

    /** Shows the notice chip only when the quiet health check found something. */
    private void refreshNotices() {
        if (noticeChip == null) return;
        final java.util.List<Health.Issue> issues = Health.check(this, new Runnable() {
            @Override
            public void run() {
                askOverlayPermission();
            }
        }, new Runnable() {
            @Override
            public void run() {
                chooseCountry();
            }
        });
        if (issues.isEmpty()) {
            noticeChip.setVisibility(View.GONE);
            wireFocus();
            return;
        }
        boolean onlyUpdate = issues.size() == 1 && Updater.available(this);
        noticeChip.setText(onlyUpdate ? getString(R.string.menu_update) : getString(R.string.notice_chip, issues.size()));
        noticeChip.setVisibility(View.VISIBLE);
        wireFocus();
        noticeChip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Health.show(MainActivity.this, issues);
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (art != null && art.getDrawable() == null) {
            art.setImageResource(Theme.kids(this) ? R.drawable.bg_kids : R.drawable.bg_royal);
        }
    }

    @Override
    protected void onStop() {
        // While another app is in front, the big background picture (about 8 MB in memory) is let go.
        if (art != null) art.setImageDrawable(null);
        super.onStop();
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(weatherTick);
        handler.removeCallbacks(ambienceTick);
        sparkle.stop();
        board.stop();
        status.stop();
        RadioService.unlisten(radioListener);
        super.onPause();
    }

    // ---------------------------------------------------------------- radio

    private void updateRadioButton() {
        if (radioBtn == null) return;
        int st = RadioService.state();
        if (st == RadioService.OFF) {
            radioBtn.setText(R.string.radio_btn);
            return;
        }
        String name = RadioService.STATIONS[RadioService.station()].shortName(isArabic());
        int label = st == RadioService.PLAYING ? R.string.radio_on
            : st == RadioService.PAUSED ? R.string.radio_paused : R.string.radio_connecting;
        radioBtn.setText(getString(label, name));
    }

    /** The two stations; the one playing is marked, and Stop appears while the radio is on. */
    private void chooseRadio() {
        final boolean arabic = isArabic();
        final boolean on = RadioService.on();
        int n = RadioService.STATIONS.length;
        String[] items = new String[on ? n + 1 : n];
        for (int i = 0; i < n; i++) {
            items[i] = (on && i == RadioService.station() ? "● " : "") + RadioService.STATIONS[i].name(arabic);
        }
        if (on) items[n] = getString(R.string.radio_stop);
        new AlertDialog.Builder(this)
            .setTitle(R.string.radio_title)
            .setItems(items, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int which) {
                    if (which < RadioService.STATIONS.length) {
                        RadioService.play(MainActivity.this, which);
                    } else {
                        RadioService.stop(MainActivity.this);
                    }
                }
            })
            .show();
    }

    // ---------------------------------------------------------------- permissions, Hijri

    /** The system screen for "display over other apps" if this TV has one; otherwise instructions. */
    private void askOverlayPermission() {
        try {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                android.net.Uri.parse("package:" + getPackageName())));
        } catch (ActivityNotFoundException | SecurityException e) {
            new AlertDialog.Builder(this)
                .setTitle(R.string.overlay_title)
                .setMessage(R.string.overlay_help)
                .setPositiveButton(android.R.string.ok, null)
                .show();
        }
    }

    /** Moves the Hijri date by up to three days either way, for when the local moon sighting differs. */
    private void chooseHijri() {
        NumberDialog.show(this, getString(R.string.hijri_title), -3, 3, Hijri.adjust(this),
            getString(R.string.days_plain), getString(R.string.days_signed), true, getString(R.string.no_adjust),
            new NumberDialog.OnSave() {
                @Override
                public void saved(int value) {
                    Hijri.setAdjust(MainActivity.this, value);
                    board.refreshDates();
                    // The White Days follow the Hijri date.
                    Reminders.scheduleNext(MainActivity.this);
                }
            });
    }

    // ---------------------------------------------------------------- city and weather

    private SharedPreferences prefs() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    private boolean isArabic() {
        return "ar".equals(Locale.getDefault().getLanguage());
    }

    /** The city chosen on this device, or null the first time. */
    private Cities.City selectedCity() {
        return Cities.find(prefs().getString("city", null));
    }

    /** Step 1 of choosing a city: the country. */
    private void chooseCountry() {
        String[] countries = {getString(R.string.egypt), getString(R.string.saudi)};
        new AlertDialog.Builder(this)
            .setTitle(R.string.choose_country)
            .setItems(countries, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    chooseCity(which == 0 ? Cities.EGYPT : Cities.SAUDI);
                }
            })
            .show();
    }

    /** Step 2: the city, listed alphabetically in the TV's language. */
    private void chooseCity(Cities.City[] all) {
        final boolean arabic = isArabic();
        final List<Cities.City> sorted = new ArrayList<>();
        Collections.addAll(sorted, all);
        final Collator collator = Collator.getInstance();
        Collections.sort(sorted, new Comparator<Cities.City>() {
            @Override
            public int compare(Cities.City a, Cities.City b) {
                return collator.compare(a.name(arabic), b.name(arabic));
            }
        });
        String[] names = new String[sorted.size()];
        for (int i = 0; i < names.length; i++) names[i] = sorted.get(i).name(arabic);
        new AlertDialog.Builder(this)
            .setTitle(R.string.choose_city)
            .setItems(names, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    prefs().edit().putString("city", sorted.get(which).key()).apply();
                    loadWeather();
                    Scheduler.scheduleNext(MainActivity.this);
                    board.refresh();
                    board.refreshDates();
                }
            })
            .show();
    }

    /** Shows the last saved weather at once, then asks for a fresh one. */
    private void loadWeather() {
        final Cities.City city = selectedCity();
        if (city == null) {
            weatherView.setText(R.string.weather_pick);
            return;
        }
        SharedPreferences p = prefs();
        if (city.key().equals(p.getString("w_key", null))) {
            showWeather(city, p.getInt("w_temp", 0), p.getInt("w_code", 0));
        } else {
            weatherView.setText(city.name(isArabic()));
        }
        Weather.fetch(city, new Weather.Callback() {
            @Override
            public void onResult(int tempC, int code) {
                prefs().edit().putString("w_key", city.key()).putInt("w_temp", tempC).putInt("w_code", code).apply();
                // The city may have been changed while this was loading; show it only if it is still the one.
                Cities.City now = selectedCity();
                if (now != null && now.key().equals(city.key())) showWeather(city, tempC, code);
            }

            @Override
            public void onError() {
                // Keep what is on screen; if nothing was shown yet, say so.
                if (!city.key().equals(prefs().getString("w_key", null))) {
                    weatherView.setText(city.name(isArabic()) + "\n" + getString(R.string.weather_unavailable));
                }
            }
        });
    }

    private void showWeather(Cities.City city, int tempC, int code) {
        String degrees = NumberFormat.getIntegerInstance(Locale.US).format(tempC) + "°";
        weatherView.setText(degrees + "  " + conditionText(code) + "\n" + city.name(isArabic()));
    }

    /** WMO weather code to words. */
    private String conditionText(int code) {
        int res;
        if (code == 0) res = R.string.w_clear;
        else if (code <= 2) res = R.string.w_partly;
        else if (code == 3) res = R.string.w_cloudy;
        else if (code == 45 || code == 48) res = R.string.w_fog;
        else if (code >= 51 && code <= 57) res = R.string.w_drizzle;
        else if ((code >= 61 && code <= 67)) res = R.string.w_rain;
        else if ((code >= 71 && code <= 77) || code == 85 || code == 86) res = R.string.w_snow;
        else if (code >= 80 && code <= 82) res = R.string.w_showers;
        else if (code >= 95) res = R.string.w_storm;
        else res = R.string.w_cloudy;
        return getString(res);
    }

    // ---------------------------------------------------------------- the apps

    /** Reads the installed TV apps (or, on a device with none, every launchable app). */
    private void loadApps() {
        PackageManager pm = getPackageManager();
        List<ResolveInfo> found = query(pm, Intent.CATEGORY_LEANBACK_LAUNCHER);
        if (found.isEmpty()) found = query(pm, Intent.CATEGORY_LAUNCHER);

        List<AppItem> list = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ResolveInfo ri : found) {
            if (ri.activityInfo == null) continue;
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(getPackageName()) || !seen.add(pkg)) continue;
            AppItem item = new AppItem();
            item.label = String.valueOf(ri.loadLabel(pm));
            item.component = new ComponentName(pkg, ri.activityInfo.name);
            Drawable image = ri.activityInfo.loadBanner(pm);
            item.banner = image != null;
            item.image = image != null ? image : ri.loadIcon(pm);
            list.add(item);
        }
        final Collator collator = Collator.getInstance();
        Collections.sort(list, new Comparator<AppItem>() {
            @Override
            public int compare(AppItem a, AppItem b) {
                return collator.compare(a.label, b.label);
            }
        });
        allApps.clear();
        allApps.addAll(list);
        applyHidden();
    }

    /** The apps to show: all but the hidden ones, in the user's order (then alphabetical), and the row is redrawn. */
    private void applyHidden() {
        Set<String> hidden = SettingsMenu.hidden(this);
        final List<String> order = SettingsMenu.order(this);
        apps.clear();
        for (AppItem a : allApps) {
            if (!hidden.contains(a.component.getPackageName())) apps.add(a);
        }
        // A stable sort: apps with a saved place go first in that order; the others keep their alphabetical order.
        Collections.sort(apps, new Comparator<AppItem>() {
            @Override
            public int compare(AppItem a, AppItem b) {
                int ia = order.indexOf(a.component.getPackageName());
                int ib = order.indexOf(b.component.getPackageName());
                if (ia < 0) ia = Integer.MAX_VALUE;
                if (ib < 0) ib = Integer.MAX_VALUE;
                return ia < ib ? -1 : ia > ib ? 1 : 0;
            }
        });
        rebuildRow();
        empty.setVisibility(apps.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void rebuildRow() {
        appRow.removeAllViews();
        View toFocus = null;
        for (int i = 0; i < apps.size(); i++) {
            AppItem item = apps.get(i);
            View tile = buildTile(item);
            appRow.addView(tile);
            if (item.component.getPackageName().equals(focusPkg) || (toFocus == null && focusPkg == null && i == 0)) {
                toFocus = tile;
            }
        }
        focusPkg = null;
        if (toFocus != null && getCurrentFocus() == null) toFocus.requestFocus();
        else if (toFocus != null && appRow.hasFocus()) toFocus.requestFocus();
        wireFocus();
    }

    private static int idOf(View v) {
        if (v.getId() == View.NO_ID) v.setId(View.generateViewId());
        return v.getId();
    }

    /**
     * Fixes where each remote arrow goes, so it is always predictable. Only three groups can be selected: the header
     * buttons (weather, radio, connection, notice), the settings list on the left, and the apps row at the bottom.
     * Up and down move between the groups; left and right never jump to another group.
     */
    private void wireFocus() {
        if (settingsList == null || appRow == null || weatherView == null || status == null || radioBtn == null) return;
        java.util.ArrayList<View> rows = new java.util.ArrayList<>();
        for (int i = 0; i < settingsList.getChildCount(); i++) {
            View r = settingsList.getChildAt(i);
            if (r.getVisibility() == View.VISIBLE && r.isFocusable()) rows.add(r);
        }
        java.util.ArrayList<View> tiles = new java.util.ArrayList<>();
        for (int i = 0; i < appRow.getChildCount(); i++) {
            if (appRow.getChildAt(i).isFocusable()) tiles.add(appRow.getChildAt(i));
        }
        View firstRow = rows.isEmpty() ? null : rows.get(0);
        View lastRow = rows.isEmpty() ? null : rows.get(rows.size() - 1);
        View firstTile = tiles.isEmpty() ? null : tiles.get(0);
        View lastTile = tiles.isEmpty() ? null : tiles.get(tiles.size() - 1);
        boolean notice = noticeChip != null && noticeChip.getVisibility() == View.VISIBLE;
        View radio = radioBtn, net = status.view, weather = weatherView;

        View below = firstRow != null ? firstRow : firstTile;
        link(weather, weather, weather, radio, below);
        link(radio, weather, notice ? noticeChip : radio, radio, net);
        link(net, weather, notice ? noticeChip : net, radio, below);
        if (notice) link(noticeChip, radio, noticeChip, noticeChip, firstTile != null ? firstTile : noticeChip);

        for (View r : rows) {
            // Left, right: stay. Up, down: the neighbouring row, except at the ends of the list.
            link(r, r, r, r == firstRow ? weather : null, r == lastRow ? (firstTile != null ? firstTile : r) : null);
        }
        for (View t : tiles) {
            link(t, t == firstTile ? t : null, t == lastTile ? t : null, firstRow != null ? firstRow : t, t);
        }
    }

    /** Sets where left, right, up and down go from {@code v}; a null direction is left to the normal neighbour. */
    private static void link(View v, View left, View right, View up, View down) {
        if (left != null) v.setNextFocusLeftId(idOf(left));
        if (right != null) v.setNextFocusRightId(idOf(right));
        if (up != null) v.setNextFocusUpId(idOf(up));
        if (down != null) v.setNextFocusDownId(idOf(down));
    }

    private View buildTile(final AppItem item) {
        final boolean kids = Theme.kids(this);
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER_HORIZONTAL);
        tile.setPadding(6, 6, 6, 4);
        tile.setBackground(Theme.tile(this, 16));
        tile.setTag(item.component.getPackageName());
        tile.setFocusable(true);
        tile.setClickable(true);
        tile.setLongClickable(true);

        ImageView image = new ImageView(this);
        image.setClipToOutline(true);
        image.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), 10);
            }
        });
        if (item.banner) {
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        } else {
            // A square icon sits in the middle on a lighter backing.
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            image.setPadding(10, 6, 10, 6);
            image.setBackgroundColor(kids ? Color.WHITE : Color.parseColor("#33FFFFFF"));
        }
        image.setImageDrawable(item.image);
        tile.addView(image, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView label = Stage.text(this, item.label, 17, Theme.tileLabel(this), true);
        label.setGravity(Gravity.CENTER);
        Stage.fitOneLine(label, 10, 17);
        LinearLayout.LayoutParams lLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 26);
        lLp.topMargin = 3;
        tile.addView(label, lLp);

        final Drawable ring = Theme.ring(this, 16);
        tile.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean has) {
                v.setForeground(has ? ring : null);
                v.animate().scaleX(has ? 1.06f : 1f).scaleY(has ? 1.06f : 1f).setDuration(120).start();
            }
        });
        tile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                open(item);
            }
        });
        tile.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                appActions(item);
                return true;
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(160, ViewGroup.LayoutParams.MATCH_PARENT);
        lp.setMargins(12, 6, 12, 6);
        tile.setLayoutParams(lp);
        return tile;
    }

    /** (name, package) of every app, for the "hide or show apps" list. */
    private List<String[]> appList() {
        List<String[]> out = new ArrayList<>();
        for (AppItem a : allApps) out.add(new String[] {a.label, a.component.getPackageName()});
        return out;
    }

    /** Hold OK on an app: move it, take it off the screen, uninstall it or see its info. */
    private void appActions(final AppItem item) {
        final String pkg = item.component.getPackageName();
        String[] items = {
            getString(R.string.app_earlier), getString(R.string.app_later), getString(R.string.app_first),
            getString(R.string.app_hide), getString(R.string.app_uninstall), getString(R.string.app_info),
        };
        new AlertDialog.Builder(this)
            .setTitle(item.label)
            .setItems(items, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int which) {
                    if (which <= 2) {
                        move(pkg, which);
                        focusPkg = pkg;
                        applyHidden();
                        // Open again, so the app can be moved several places in a row.
                        appActions(item);
                    } else if (which == 3) {
                        Set<String> hidden = SettingsMenu.hidden(MainActivity.this);
                        hidden.add(pkg);
                        SettingsMenu.setHidden(MainActivity.this, hidden);
                        applyHidden();
                        Toast.makeText(MainActivity.this, R.string.app_hidden_tip, Toast.LENGTH_LONG).show();
                    } else if (which == 4) {
                        SettingsMenu.uninstall(MainActivity.this, pkg);
                    } else {
                        SettingsMenu.appInfo(MainActivity.this, pkg);
                    }
                }
            })
            .setNegativeButton(R.string.close, null)
            .show();
    }

    /** Saves a new order: 0 = one place earlier, 1 = one place later, 2 = to the first place. */
    private void move(String pkg, int how) {
        List<String> order = new ArrayList<>();
        for (AppItem a : apps) order.add(a.component.getPackageName());
        int i = order.indexOf(pkg);
        if (i < 0) return;
        if (how == 0 && i > 0) {
            Collections.swap(order, i, i - 1);
        } else if (how == 1 && i < order.size() - 1) {
            Collections.swap(order, i, i + 1);
        } else if (how == 2 && i > 0) {
            order.remove(i);
            order.add(0, pkg);
        }
        SettingsMenu.setOrder(this, order);
    }

    private static List<ResolveInfo> query(PackageManager pm, String category) {
        Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(category);
        return pm.queryIntentActivities(intent, 0);
    }

    private void open(AppItem item) {
        Intent intent = new Intent(Intent.ACTION_MAIN)
            .setComponent(item.component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.open_failed, Toast.LENGTH_SHORT).show();
        }
    }

}
