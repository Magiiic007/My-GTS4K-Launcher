package app.mylauncher.gtv;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.provider.Settings;
import android.widget.Toast;

import java.util.List;

/** The way back: from Magiic TV to the device's own home screen (Google TV), and help for when that is switched off. */
final class StockHome {
    private StockHome() {}

    /** Opens the other home screen if there is one; returns false if none is enabled. */
    static boolean open(Context c) {
        PackageManager pm = c.getPackageManager();
        List<ResolveInfo> homes = pm.queryIntentActivities(
            new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0);
        for (ResolveInfo ri : homes) {
            ActivityInfo ai = ri.activityInfo;
            if (ai == null || c.getPackageName().equals(ai.packageName)) continue;
            try {
                HomeOverride.pauseFor(c, 15000L);
                c.startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                    .setClassName(ai.packageName, ai.name)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
                return true;
            } catch (RuntimeException e) {
                // Try the next one.
            }
        }
        return false;
    }

    /** "Make it the home screen": the state, the switch, and the device's accessibility settings. */
    static void menu(final Context c) {
        boolean allowed = HomeOverride.allowed(c);
        boolean on = HomeOverride.on(c);
        String state = !allowed ? c.getString(R.string.home_state_not_allowed)
            : on ? c.getString(R.string.home_state_on) : c.getString(R.string.home_state_off);
        String[] items = {
            c.getString(R.string.home_open_access),
            c.getString(on ? R.string.home_turn_off : R.string.home_turn_on),
            c.getString(R.string.stock_open),
            c.getString(R.string.stock_help_btn),
        };
        new AlertDialog.Builder(c)
            .setTitle(c.getString(R.string.menu_stock) + "\n" + state)
            .setItems(items, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int which) {
                    if (which == 0) {
                        SettingsMenu.launch(c, Settings.ACTION_ACCESSIBILITY_SETTINGS, R.string.home_access_tip);
                    } else if (which == 1) {
                        HomeOverride.setOn(c, !HomeOverride.on(c));
                        Toast.makeText(c, HomeOverride.on(c) ? R.string.home_now_on : R.string.home_now_off,
                            Toast.LENGTH_LONG).show();
                    } else if (which == 2) {
                        if (!open(c)) help(c, true);
                    } else {
                        help(c, false);
                    }
                }
            })
            .setNegativeButton(R.string.close, null)
            .show();
    }

    private static void help(Context c, boolean noneFound) {
        new AlertDialog.Builder(c)
            .setTitle(R.string.menu_stock)
            .setMessage((noneFound ? c.getString(R.string.stock_none) + "\n\n" : "") + c.getString(R.string.stock_help))
            .setPositiveButton(android.R.string.ok, null)
            .show();
    }
}
