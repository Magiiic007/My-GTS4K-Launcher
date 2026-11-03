package app.mylauncher.gtv;

import android.app.Activity;
import android.app.Dialog;
import android.content.ComponentName;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Set;

/**
 * Every app on the device in one grid. OK opens an app; holding OK puts it on the home screen (a star) or takes it off.
 * An app taken off stays installed and is still in this list.
 */
final class AllApps {
    private AllApps() {}

    static final class Entry {
        String label;
        ComponentName component;
        Drawable image;
        boolean banner;
    }

    interface Opener {
        void open(ComponentName component);
    }

    private static int dp(Activity a, int v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, a.getResources().getDisplayMetrics()));
    }

    static void show(final Activity a, final List<Entry> entries, final Runnable changed, final Opener opener) {
        final boolean kids = Theme.kids(a);
        final boolean mih = Theme.mihrab(a);
        final int ink = kids ? Color.parseColor("#FF7A3347") : Color.parseColor("#FFFFF8E7");
        final int gold = kids ? Color.parseColor("#FFB8566F") : Color.parseColor("#FFE8C15C");

        final Dialog dialog = new Dialog(a, Theme.dialogStyle(a));
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(a, 22), dp(a, 16), dp(a, 22), dp(a, 16));

        TextView title = new TextView(a);
        title.setText(R.string.all_apps);
        title.setTextColor(gold);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        title.setGravity(Gravity.CENTER);
        box.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView hint = new TextView(a);
        hint.setText(mih ? R.string.all_apps_hint_fav : R.string.all_apps_hint);
        hint.setTextColor(ink);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        hint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT);
        hLp.topMargin = dp(a, 4);
        hLp.bottomMargin = dp(a, 10);
        box.addView(hint, hLp);

        final GridView grid = new GridView(a);
        grid.setNumColumns(5);
        grid.setHorizontalSpacing(dp(a, 12));
        grid.setVerticalSpacing(dp(a, 12));
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        GradientDrawable sel = new GradientDrawable();
        sel.setCornerRadius(dp(a, 14));
        sel.setColor(kids ? Color.parseColor("#55FFFFFF") : Color.parseColor("#33F2CE6A"));
        sel.setStroke(dp(a, 3), kids ? Color.parseColor("#FFB8566F") : Color.parseColor("#FFF2CE6A"));
        grid.setSelector(sel);
        grid.setDrawSelectorOnTop(true);
        grid.setClipToPadding(false);
        grid.setPadding(dp(a, 4), dp(a, 4), dp(a, 4), dp(a, 4));
        box.addView(grid, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        final BaseAdapter adapter = new BaseAdapter() {
            @Override
            public int getCount() {
                return entries.size();
            }

            @Override
            public Object getItem(int i) {
                return entries.get(i);
            }

            @Override
            public long getItemId(int i) {
                return i;
            }

            @Override
            public View getView(int i, View old, ViewGroup parent) {
                Entry e = entries.get(i);
                Set<String> hidden = SettingsMenu.hidden(a);
                boolean shown = mih ? MihrabHome.isFav(a, e.component.getPackageName())
                    : !hidden.contains(e.component.getPackageName());

                FrameLayout cell = new FrameLayout(a);
                cell.setLayoutParams(new AbsListViewLp(dp(a, 120)));
                GradientDrawable bg = new GradientDrawable();
                bg.setCornerRadius(dp(a, 14));
                bg.setColor(kids ? Color.parseColor("#66FFFFFF") : Color.parseColor("#55000000"));
                bg.setStroke(dp(a, 1), kids ? Color.parseColor("#B3E58FA3") : Color.parseColor("#8CD9A93A"));
                cell.setBackground(bg);

                LinearLayout col = new LinearLayout(a);
                col.setOrientation(LinearLayout.VERTICAL);
                col.setPadding(dp(a, 6), dp(a, 6), dp(a, 6), dp(a, 4));
                ImageView image = new ImageView(a);
                image.setScaleType(e.banner ? ImageView.ScaleType.CENTER_CROP : ImageView.ScaleType.FIT_CENTER);
                Drawable.ConstantState cs = e.image != null ? e.image.getConstantState() : null;
                image.setImageDrawable(cs != null ? cs.newDrawable(a.getResources()) : e.image);
                col.addView(image, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
                TextView label = new TextView(a);
                label.setText(e.label);
                label.setTextColor(ink);
                label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                label.setSingleLine(true);
                label.setGravity(Gravity.CENTER);
                col.addView(label, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
                cell.addView(col, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));

                if (shown) {
                    TextView star = new TextView(a);
                    star.setText("★");
                    star.setTextColor(Color.parseColor("#FFF2CE6A"));
                    star.setShadowLayer(4f, 0f, 1f, Color.BLACK);
                    star.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
                    FrameLayout.LayoutParams sLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END);
                    sLp.setMargins(0, dp(a, 2), dp(a, 8), 0);
                    cell.addView(star, sLp);
                }
                return cell;
            }
        };
        grid.setAdapter(adapter);
        grid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                dialog.dismiss();
                opener.open(entries.get(position).component);
            }
        });
        grid.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                String pkg = entries.get(position).component.getPackageName();
                if (mih) {
                    boolean nowFav = MihrabHome.toggleFav(a, pkg);
                    changed.run();
                    adapter.notifyDataSetChanged();
                    Toast.makeText(a, nowFav ? R.string.fav_added : R.string.fav_removed, Toast.LENGTH_SHORT).show();
                    return true;
                }
                Set<String> hidden = SettingsMenu.hidden(a);
                boolean nowShown;
                if (hidden.contains(pkg)) {
                    hidden.remove(pkg);
                    nowShown = true;
                } else {
                    hidden.add(pkg);
                    nowShown = false;
                }
                SettingsMenu.setHidden(a, hidden);
                changed.run();
                adapter.notifyDataSetChanged();
                Toast.makeText(a, nowShown ? R.string.all_apps_shown : R.string.all_apps_hidden,
                    Toast.LENGTH_SHORT).show();
                return true;
            }
        });

        dialog.setContentView(box);
        dialog.show();
        DisplayMetrics dm = a.getResources().getDisplayMetrics();
        Window w = dialog.getWindow();
        if (w != null) w.setLayout(Math.round(dm.widthPixels * 0.82f), Math.round(dm.heightPixels * 0.84f));
        grid.requestFocus();
    }

    /** Layout params for a grid cell of a fixed height. */
    private static final class AbsListViewLp extends android.widget.AbsListView.LayoutParams {
        AbsListViewLp(int height) {
            super(ViewGroup.LayoutParams.MATCH_PARENT, height);
        }
    }
}
