package app.mylauncher.gtv;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
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

    /** The menu behind "Back to the device's home screen". */
    static void menu(final Context c) {
        String[] items = {
            c.getString(R.string.stock_open), c.getString(R.string.stock_defaults), c.getString(R.string.stock_help_btn),
        };
        new AlertDialog.Builder(c)
            .setTitle(R.string.menu_stock)
            .setItems(items, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int which) {
                    if (which == 0) {
                        if (!open(c)) help(c, true);
                    } else if (which == 1) {
                        SettingsMenu.appInfo(c, c.getPackageName());
                        Toast.makeText(c, R.string.stock_defaults_tip, Toast.LENGTH_LONG).show();
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
