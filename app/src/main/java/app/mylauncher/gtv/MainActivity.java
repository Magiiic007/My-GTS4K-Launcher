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
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
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
 * The home screen. Top: the name, radio, weather and status icons. Middle: the clock and dates (right), the prayer
 * times (centre) and the settings list (left). Bottom: a row of apps. Everything can be selected with the remote.
 * It comes in two looks, chosen from a drop-down in the settings list: royal (green and gold) and kids (pink).
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

        FrameLayout root = new FrameLayout(this);
        root.setBackground(Theme.background(this));
        addDecorations(root);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(14), dp(24), dp(12));
        root.addView(content, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // ---------------------------------------------------------------- top bar
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText(R.string.app_name);
        title.setTextColor(Theme.bgText(this));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        top.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        radioBtn = chip(R.drawable.ic_radio, null);
        radioBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                chooseRadio();
            }
        });
        top.addView(radioBtn, chipLp(10));

        weatherView = chip(0, null);
        weatherView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        weatherView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                chooseCountry();
            }
        });
        top.addView(weatherView, chipLp(10));

        status = new StatusIcons(this, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SettingsMenu.launch(MainActivity.this, Settings.ACTION_WIFI_SETTINGS);
            }
        });
        top.addView(status.view, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        updateRadioButton();
        content.addView(top, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // ---------------------------------------------------------------- the middle: clock, prayers, settings
        board = new PrayerBoard(this,
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

        LinearLayout middle = new LinearLayout(this);
        middle.setOrientation(LinearLayout.HORIZONTAL);
        // First child is on the start side: the right in Arabic (as in the design), the left in English.
        middle.addView(board.clockColumn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 30f));
        LinearLayout.LayoutParams pLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 38f);
        pLp.setMarginStart(dp(10));
        pLp.setMarginEnd(dp(10));
        middle.addView(board.prayers, pLp);
        middle.addView(buildSettingsPanel(), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 25f));
        LinearLayout.LayoutParams mLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 2.7f);
        mLp.topMargin = dp(8);
        content.addView(middle, mLp);

        // ---------------------------------------------------------------- the apps row
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setBackground(Theme.panel(this, 22));
        scroll.setClipToPadding(false);
        scroll.setPadding(dp(10), dp(8), dp(10), dp(8));
        appRow = new LinearLayout(this);
        appRow.setOrientation(LinearLayout.HORIZONTAL);
        scroll.addView(appRow, new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        scroll.setFillViewport(true);
        LinearLayout.LayoutParams aLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.15f);
        aLp.topMargin = dp(10);
        content.addView(scroll, aLp);

        empty = new TextView(this);
        empty.setText(R.string.no_apps);
        empty.setTextColor(Theme.bgText(this));
        empty.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        empty.setGravity(Gravity.CENTER);
        empty.setVisibility(View.GONE);
        content.addView(empty, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(root);
    }

    /** The skyline over the middle of the top bar, and a few stars in the corners. */
    private void addDecorations(FrameLayout root) {
        ImageView skyline = new ImageView(this);
        skyline.setImageResource(Theme.kids(this) ? R.drawable.deco_skyline_kids : R.drawable.deco_skyline_royal);
        skyline.setScaleType(ImageView.ScaleType.FIT_CENTER);
        FrameLayout.LayoutParams sLp = new FrameLayout.LayoutParams(dp(420), dp(66),
            Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        sLp.topMargin = dp(6);
        root.addView(skyline, sLp);

        int[] stars = {R.drawable.ic_crescent, R.drawable.ic_star, R.drawable.ic_star};
        int[] sizes = {34, 20, 14};
        int[] marginTop = {10, 40, 22};
        int[] marginSide = {250, 210, 300};
        for (int i = 0; i < 3; i++) {
            for (int side = 0; side < 2; side++) {
                ImageView s = new ImageView(this);
                s.setImageResource(stars[i]);
                s.setColorFilter(Theme.kids(this) ? Color.WHITE : Theme.accent(this));
                s.setAlpha(0.55f);
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(sizes[i]), dp(sizes[i]),
                    Gravity.TOP | Gravity.CENTER_HORIZONTAL);
                lp.topMargin = dp(marginTop[i]);
                s.setTranslationX(dp(side == 0 ? -marginSide[i] : marginSide[i]));
                root.addView(s, lp);
            }
        }
    }

    // ---------------------------------------------------------------- the settings list (left)

    private LinearLayout buildSettingsPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(Theme.panel(this, 22));
        panel.setPadding(dp(10), dp(10), dp(10), dp(10));

        TextView head = new TextView(this);
        head.setText(R.string.menu_title);
        head.setTextColor(Theme.accent(this));
        head.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        head.setTypeface(Typeface.DEFAULT_BOLD);
        head.setGravity(Gravity.CENTER);
        head.setCompoundDrawablePadding(dp(8));
        head.setCompoundDrawablesRelativeWithIntrinsicBounds(
            tinted(R.drawable.ic_settings, Theme.accent(this), 26), null, null, null);
        panel.addView(head, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list, new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        sLp.topMargin = dp(6);
        panel.addView(scroll, sLp);

        // The look: a drop-down with the two interfaces.
        Spinner look = new Spinner(this, Spinner.MODE_DIALOG);
        String[] names = {getString(R.string.theme_royal), getString(R.string.theme_kids)};
        look.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, names) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView t = (TextView) super.getView(position, convertView, parent);
                t.setTextColor(Theme.chipText(MainActivity.this));
                t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
                t.setTypeface(Typeface.DEFAULT_BOLD);
                t.setSingleLine(true);
                t.setCompoundDrawablePadding(dp(8));
                t.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    tinted(R.drawable.ic_palette, Theme.chipText(MainActivity.this), 22), null, null, null);
                return t;
            }
        });
        look.setSelection(Theme.id(this), false);
        look.setPrompt(getString(R.string.menu_theme));
        look.setBackground(Theme.chip(this));
        look.setPadding(dp(8), dp(4), dp(8), dp(4));
        look.setFocusable(true);
        look.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position != Theme.id(MainActivity.this)) {
                    Theme.set(MainActivity.this, position);
                    recreate();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        list.addView(look, rowLp());

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
        permRow = addRow(list, R.drawable.ic_warning, R.string.perm_btn, new Runnable() {
            @Override
            public void run() {
                askOverlayPermission();
            }
        });
        return panel;
    }

    private LinearLayout.LayoutParams rowLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(5);
        return lp;
    }

    private LinearLayout.LayoutParams chipLp(int endMarginDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMarginEnd(dp(endMarginDp));
        return lp;
    }

    private TextView addRow(LinearLayout list, int icon, int text, final Runnable click) {
        TextView row = chip(icon, getString(text));
        row.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        row.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                click.run();
            }
        });
        list.addView(row, rowLp());
        return row;
    }

    /** A selectable button with an optional icon at its start. */
    private TextView chip(int icon, String text) {
        TextView b = new TextView(this);
        if (text != null) b.setText(text);
        b.setTextColor(Theme.chipText(this));
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(12), dp(8), dp(12), dp(8));
        b.setBackground(Theme.chip(this));
        b.setFocusable(true);
        b.setClickable(true);
        if (icon != 0) {
            b.setCompoundDrawablePadding(dp(8));
            b.setCompoundDrawablesRelativeWithIntrinsicBounds(
                tinted(icon, Theme.chipText(this), 22), null, null, null);
        }
        return b;
    }

    private Drawable tinted(int res, int colour, int sizeDp) {
        Drawable d = getDrawable(res).mutate();
        d.setTint(colour);
        d.setBounds(0, 0, dp(sizeDp), dp(sizeDp));
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
        handler.removeCallbacks(weatherTick);
        handler.post(weatherTick);
        board.start();
        status.start();
        RadioService.listen(radioListener);
        updateRadioButton();
        permRow.setVisibility(Settings.canDrawOverlays(this) ? View.GONE : View.VISIBLE);
        // Line up the alarms for the next prayer and the next reminder (also set again after a restart).
        Scheduler.scheduleNext(this);
        Reminders.scheduleNext(this);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(weatherTick);
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
        String degrees = NumberFormat.getIntegerInstance().format(tempC) + "°";
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
    }

    private View buildTile(final AppItem item) {
        final boolean kids = Theme.kids(this);
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER_HORIZONTAL);
        tile.setPadding(dp(6), dp(6), dp(6), dp(6));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Theme.tileFill(this));
        bg.setCornerRadius(dp(18));
        bg.setStroke(dp(2), kids ? Color.parseColor("#FFE58FA3") : Color.parseColor("#88E8B84A"));
        tile.setBackground(bg);
        tile.setTag(item.component.getPackageName());
        tile.setFocusable(true);
        tile.setClickable(true);
        tile.setLongClickable(true);

        ImageView image = new ImageView(this);
        image.setClipToOutline(true);
        image.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(12));
            }
        });
        if (item.banner) {
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        } else {
            // A square icon sits in the middle on a lighter backing.
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            image.setPadding(dp(12), dp(8), dp(12), dp(8));
            image.setBackgroundColor(kids ? Color.WHITE : Color.parseColor("#33FFFFFF"));
        }
        image.setImageDrawable(item.image);
        tile.addView(image, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView label = new TextView(this);
        label.setText(item.label);
        label.setTextColor(Theme.tileLabel(this));
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        label.setSingleLine(true);
        label.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lLp.topMargin = dp(5);
        tile.addView(label, lLp);

        final Drawable ring = Theme.focusRing(this, 18);
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
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(126), ViewGroup.LayoutParams.MATCH_PARENT);
        lp.setMarginStart(dp(5));
        lp.setMarginEnd(dp(5));
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

    private int dp(int value) {
        return Theme.dp(this, value);
    }
}
