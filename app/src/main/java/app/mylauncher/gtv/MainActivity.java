package app.mylauncher.gtv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
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
 * Phase 1: a grid of the TV apps installed on the device. Move with the remote's arrows, press OK to open.
 * Built with plain Android Views, no extra libraries, so there is very little that can go wrong.
 */
public class MainActivity extends Activity {

    /** One app in the grid. */
    private static final class AppItem {
        String label;
        ComponentName component;
        Drawable image;
    }

    private final List<AppItem> apps = new ArrayList<>();
    private GridView grid;
    private TextView empty;
    private AppAdapter adapter;

    private static final String PREFS = "magiic";
    private static final long WEATHER_EVERY_MS = 30L * 60L * 1000L;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView weatherView;
    private PrayerPanel prayerPanel;
    private TextView radioBtn;
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

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#FF0F1720"));
        root.setPadding(dp(48), dp(32), dp(48), dp(24));

        // Header: the name on the start side, the clock and date on the end side. In Arabic (right-to-left)
        // the start side is the right, so the layout mirrors by itself.
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText(R.string.app_name);
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        // The radio: OK opens the two stations (and Stop while it plays).
        radioBtn = new TextView(this);
        radioBtn.setTextColor(Color.WHITE);
        radioBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        radioBtn.setGravity(Gravity.CENTER);
        radioBtn.setPadding(dp(16), dp(8), dp(16), dp(8));
        radioBtn.setBackgroundResource(R.drawable.weather_bg);
        radioBtn.setFocusable(true);
        radioBtn.setClickable(true);
        radioBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                chooseRadio();
            }
        });
        LinearLayout.LayoutParams radioLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        radioLp.setMarginEnd(dp(16));
        header.addView(radioBtn, radioLp);
        updateRadioButton();

        // The weather. Reach it with the remote's arrows and press OK to change the city.
        weatherView = new TextView(this);
        weatherView.setTextColor(Color.WHITE);
        weatherView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        weatherView.setGravity(Gravity.CENTER);
        weatherView.setPadding(dp(16), dp(8), dp(16), dp(8));
        weatherView.setBackgroundResource(R.drawable.weather_bg);
        weatherView.setFocusable(true);
        weatherView.setClickable(true);
        weatherView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                chooseCountry();
            }
        });
        LinearLayout.LayoutParams weatherLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        weatherLp.setMarginEnd(dp(32));
        header.addView(weatherView, weatherLp);

        LinearLayout when = new LinearLayout(this);
        when.setOrientation(LinearLayout.VERTICAL);
        when.setGravity(Gravity.END);

        // The clock follows the TV's own 12/24-hour setting and updates itself every minute.
        TextClock clock = new TextClock(this);
        clock.setTextColor(Color.WHITE);
        clock.setTextSize(TypedValue.COMPLEX_UNIT_SP, 36);
        when.addView(clock, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // The date, in the TV's language: for example "الأحد 1 نوفمبر 2026" or "Sunday 1 November 2026".
        TextClock date = new TextClock(this);
        date.setFormat12Hour("EEEE d MMMM yyyy");
        date.setFormat24Hour("EEEE d MMMM yyyy");
        date.setTextColor(Color.parseColor("#FFB8C4D0"));
        date.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        when.addView(date, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        header.addView(when, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(header, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // The prayer times strip, with the countdown to the next prayer.
        prayerPanel = new PrayerPanel(this);
        LinearLayout.LayoutParams panelLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        panelLp.topMargin = dp(12);
        root.addView(prayerPanel.view, panelLp);

        grid = new GridView(this);
        grid.setNumColumns(5);
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setHorizontalSpacing(dp(16));
        grid.setVerticalSpacing(dp(16));
        grid.setClipToPadding(false);
        grid.setPadding(0, dp(24), 0, dp(24));
        grid.setSelector(R.drawable.tile_selector);
        grid.setDrawSelectorOnTop(true);
        grid.setFocusable(true);
        adapter = new AppAdapter();
        grid.setAdapter(adapter);
        grid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                open(apps.get(position));
            }
        });
        root.addView(grid, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        empty = new TextView(this);
        empty.setText(R.string.no_apps);
        empty.setTextColor(Color.WHITE);
        empty.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        empty.setGravity(Gravity.CENTER);
        empty.setVisibility(View.GONE);
        root.addView(empty, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadApps();
        grid.requestFocus();
        handler.removeCallbacks(weatherTick);
        handler.post(weatherTick);
        prayerPanel.start();
        RadioService.listen(radioListener);
        updateRadioButton();
        // Line up the alarm for the next prayer (it is also set again after a restart).
        Scheduler.scheduleNext(this);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(weatherTick);
        prayerPanel.stop();
        RadioService.unlisten(radioListener);
        super.onPause();
    }

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
            .setItems(items, new android.content.DialogInterface.OnClickListener() {
                @Override
                public void onClick(android.content.DialogInterface d, int which) {
                    if (which < RadioService.STATIONS.length) {
                        RadioService.play(MainActivity.this, which);
                    } else {
                        RadioService.stop(MainActivity.this);
                    }
                }
            })
            .show();
    }

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
            .setItems(countries, new android.content.DialogInterface.OnClickListener() {
                @Override
                public void onClick(android.content.DialogInterface dialog, int which) {
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
            .setItems(names, new android.content.DialogInterface.OnClickListener() {
                @Override
                public void onClick(android.content.DialogInterface dialog, int which) {
                    prefs().edit().putString("city", sorted.get(which).key()).apply();
                    loadWeather();
                    Scheduler.scheduleNext(MainActivity.this);
                    prayerPanel.update();
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

    /** Reads the installed TV apps (or, on a device with none, every launchable app), sorted by name. */
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
            item.image = image != null ? image : ri.loadIcon(pm);
            list.add(item);
        }
        final Collator collator = Collator.getInstance();
        Collections.sort(list, new java.util.Comparator<AppItem>() {
            @Override
            public int compare(AppItem a, AppItem b) {
                return collator.compare(a.label, b.label);
            }
        });
        apps.clear();
        apps.addAll(list);
        adapter.notifyDataSetChanged();
        empty.setVisibility(apps.isEmpty() ? View.VISIBLE : View.GONE);
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
        return Math.round(TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics()));
    }

    /** Draws one tile: the app's banner or icon above its name. */
    private final class AppAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return apps.size();
        }

        @Override
        public Object getItem(int position) {
            return apps.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout tile;
            ImageView image;
            TextView label;
            if (convertView == null) {
                tile = new LinearLayout(MainActivity.this);
                tile.setOrientation(LinearLayout.VERTICAL);
                tile.setGravity(Gravity.CENTER_HORIZONTAL);
                tile.setBackgroundResource(R.drawable.tile_bg);
                tile.setPadding(dp(8), dp(8), dp(8), dp(8));

                image = new ImageView(MainActivity.this);
                image.setScaleType(ImageView.ScaleType.FIT_CENTER);
                tile.addView(image, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(100)));

                label = new TextView(MainActivity.this);
                label.setTextColor(Color.WHITE);
                label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                label.setSingleLine(true);
                label.setGravity(Gravity.CENTER);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.topMargin = dp(6);
                tile.addView(label, lp);

                tile.setTag(new View[] {image, label});
            } else {
                tile = (LinearLayout) convertView;
            }
            View[] parts = (View[]) tile.getTag();
            image = (ImageView) parts[0];
            label = (TextView) parts[1];
            AppItem item = apps.get(position);
            image.setImageDrawable(item.image);
            label.setText(item.label);
            return tile;
        }
    }
}
