package app.mylauncher.gtv;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** The list the remote's star button opens on a long press: pick the app the star button will open. */
public class PickFavorite extends Activity {
    static final String PREFS = "fav";
    static final String KEY = "pkg";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        final PackageManager pm = getPackageManager();
        Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        final List<ResolveInfo> apps = new ArrayList<>(pm.queryIntentActivities(q, 0));
        Collections.sort(apps, new Comparator<ResolveInfo>() {
            @Override
            public int compare(ResolveInfo a, ResolveInfo c) {
                return a.loadLabel(pm).toString().compareToIgnoreCase(c.loadLabel(pm).toString());
            }
        });
        List<String> names = new ArrayList<>();
        for (ResolveInfo r : apps) names.add(r.loadLabel(pm).toString());
        ListView lv = new ListView(this);
        lv.setBackgroundColor(Color.parseColor("#0f3d24"));
        lv.setPadding(120, 60, 120, 60);
        TextView h = new TextView(this);
        h.setText("اختر التطبيق الذي يفتحه زر النجمة");
        h.setTextColor(Color.parseColor("#e8c872"));
        h.setTextSize(26);
        h.setPadding(0, 0, 0, 30);
        lv.addHeaderView(h, null, false);
        lv.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, names) {
            @Override
            public View getView(int p, View v, android.view.ViewGroup g) {
                TextView t = (TextView) super.getView(p, v, g);
                t.setTextColor(Color.WHITE);
                t.setTextSize(22);
                return t;
            }
        });
        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> a, View v, int pos, long id) {
                int i = pos - 1;
                if (i < 0 || i >= apps.size()) return;
                SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
                sp.edit().putString(KEY, apps.get(i).activityInfo.packageName).apply();
                finish();
            }
        });
        setContentView(lv);
        lv.requestFocus();
    }
}
