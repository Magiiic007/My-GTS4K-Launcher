package app.mylauncher.gtv;

import android.app.Activity;
import android.content.ActivityNotFoundException;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
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
