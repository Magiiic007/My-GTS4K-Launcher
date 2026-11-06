package app.mylauncher.gtv;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** The smaller dialogs behind the left-hand panel: which apps show and in what order, the device's own settings, credits. */
final class SettingsMenu {
    private SettingsMenu() {}

    static final String PREFS = "magiic";
    private static final String HIDDEN = "hidden";
    private static final String ORDER = "order";

    // ---------------------------------------------------------------- hidden and ordered apps

    static Set<String> hidden(Context c) {
        return new HashSet<>(c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet(HIDDEN, new HashSet<String>()));
    }

    static void setHidden(Context c, Set<String> set) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putStringSet(HIDDEN, new HashSet<>(set)).apply();
    }

    /** The package names in the order the user arranged them (apps not in it come after, alphabetically). */
    static List<String> order(Context c) {
        String s = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(ORDER, "");
        List<String> out = new ArrayList<>();
        for (String p : s.split("\n")) if (!p.isEmpty()) out.add(p);
        return out;
    }

    static void setOrder(Context c, List<String> packages) {
        StringBuilder b = new StringBuilder();
        for (String p : packages) b.append(p).append('\n');
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(ORDER, b.toString()).apply();
    }

    /** "Apps": choose which show, or put them back in alphabetical order. */
    static void apps(final Context c, final List<String[]> allApps, final Runnable appsChanged) {
        String[] items = {c.getString(R.string.apps_hide_show), c.getString(R.string.apps_reset_order),
            c.getString(R.string.apps_tip)};
        new Menu.Builder(c)
            .setTitle(R.string.menu_apps)
            .setItems(items, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int which) {
                    if (which == 0) {
                        hiddenApps(c, allApps, appsChanged);
                    } else if (which == 1) {
                        setOrder(c, new ArrayList<String>());
                        appsChanged.run();
                        Toast.makeText(c, R.string.apps_order_reset, Toast.LENGTH_SHORT).show();
                    }
                }
            })
            .show();
    }

    /** A checklist of every app; the ticked ones are hidden from the home screen (they stay installed). */
    private static void hiddenApps(final Context c, final List<String[]> allApps, final Runnable appsChanged) {
        final Set<String> hidden = hidden(c);
        final String[] labels = new String[allApps.size()];
        final boolean[] checked = new boolean[labels.length];
        for (int i = 0; i < labels.length; i++) {
            labels[i] = allApps.get(i)[0];
            checked[i] = hidden.contains(allApps.get(i)[1]);
        }
        new Menu.Builder(c)
            .setTitle(R.string.hidden_title)
            .setMultiChoiceItems(labels, checked, new DialogInterface.OnMultiChoiceClickListener() {
                @Override
                public void onClick(DialogInterface d, int which, boolean isChecked) {
                    checked[which] = isChecked;
                }
            })
            .setPositiveButton(R.string.save, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int w) {
                    for (int i = 0; i < labels.length; i++) {
                        String pkg = allApps.get(i)[1];
                        if (checked[i]) hidden.add(pkg);
                        else hidden.remove(pkg);
                    }
                    setHidden(c, hidden);
                    appsChanged.run();
                }
            })
            .setNegativeButton(R.string.cancel, null)
            .show();
    }

    // ---------------------------------------------------------------- the device's own settings

    private static final String[][] DEVICE = {
        {String.valueOf(R.string.set_all), Settings.ACTION_SETTINGS},
        {String.valueOf(R.string.menu_account), Settings.ACTION_SYNC_SETTINGS},
        {String.valueOf(R.string.set_network), Settings.ACTION_WIFI_SETTINGS},
        {String.valueOf(R.string.set_bluetooth), Settings.ACTION_BLUETOOTH_SETTINGS},
        {String.valueOf(R.string.set_sound), Settings.ACTION_SOUND_SETTINGS},
        {String.valueOf(R.string.set_display), Settings.ACTION_DISPLAY_SETTINGS},
        {String.valueOf(R.string.set_apps), Settings.ACTION_APPLICATION_SETTINGS},
        {String.valueOf(R.string.set_storage), Settings.ACTION_INTERNAL_STORAGE_SETTINGS},
        {String.valueOf(R.string.set_date), Settings.ACTION_DATE_SETTINGS},
        {String.valueOf(R.string.set_language), Settings.ACTION_LOCALE_SETTINGS},
        {String.valueOf(R.string.set_about), Settings.ACTION_DEVICE_INFO_SETTINGS},
    };

    /** The device and account settings: each opens that screen, or the main settings if this device has no such screen. */
    static void device(final Context c) {
        // Only the settings screens this device really has are listed (a Google TV leaves out some of them).
        final List<String[]> have = new ArrayList<>();
        for (String[] e : DEVICE) {
            boolean all = Settings.ACTION_SETTINGS.equals(e[1]);
            if (all || c.getPackageManager().resolveActivity(new Intent(e[1]), 0) != null) have.add(e);
        }
        final int n = have.size();
        final boolean tools = c instanceof android.app.Activity;
        String[] items = new String[n + (tools ? 4 : 1)];
        for (int i = 0; i < n; i++) items[i] = c.getString(Integer.parseInt(have.get(i)[0]));
        items[n] = c.getString(R.string.menu_about);
        if (tools) {
            items[n + 1] = c.getString(R.string.menu_saver);
            items[n + 2] = c.getString(R.string.menu_clean);
            items[n + 3] = c.getString(R.string.menu_stock);
        }
        new Menu.Builder(c)
            .setTitle(R.string.menu_device)
            .setItems(items, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int which) {
                    if (which == n) about(c);
                    else if (which == n + 1) screensaver(c);
                    else if (which == n + 2) Cleaner.open((android.app.Activity) c);
                    else if (which == n + 3) StockHome.menu(c);
                    else launch(c, have.get(which)[1], Settings.ACTION_SYNC_SETTINGS.equals(have.get(which)[1]) ? R.string.account_hint : 0);
                }
            })
            .show();
    }

    static void launch(Context c, String action) {
        launch(c, action, 0);
    }

    static void launch(Context c, String action, int hint) {
        try {
            c.startActivity(new Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            if (hint != 0) Toast.makeText(c, hint, Toast.LENGTH_LONG).show();
        } catch (RuntimeException e) {
            try {
                c.startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                Toast.makeText(c, R.string.settings_fallback, Toast.LENGTH_LONG).show();
            } catch (RuntimeException e2) {
                Toast.makeText(c, R.string.settings_missing, Toast.LENGTH_LONG).show();
            }
        }
    }

    /**
     * The screensaver (Google Photos, ambient mode). The system's own action does nothing on some Google TV builds,
     * so the known screens are tried one after the other; if none opens, the main settings open with a clear tip.
     */
    static void screensaver(Context c) {
        String[][] tries = {
            {"com.android.tv.settings", "com.android.tv.settings.device.display.daydream.DaydreamActivity"},
            {"com.google.android.apps.tv.dreamx", "com.google.android.apps.tv.dreamx.settings.AmbientSettingsActivity"},
            {"com.google.android.apps.tv.dreamx", "com.google.android.apps.tv.dreamx.settings.SettingsActivity"},
        };
        for (String[] t : tries) {
            try {
                c.startActivity(new Intent().setClassName(t[0], t[1]).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                Toast.makeText(c, R.string.saver_manual, Toast.LENGTH_LONG).show();
                return;
            } catch (RuntimeException ignored) {
                // Not on this TV: try the next one.
            }
        }
        // The system's own action: on some Google TV builds it only lands on the main settings page, so the tip
        // says where to go from there.
        try {
            c.startActivity(new Intent(Settings.ACTION_DREAM_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            Toast.makeText(c, R.string.saver_manual, Toast.LENGTH_LONG).show();
            return;
        } catch (RuntimeException ignored) {
            // Fall through to the main settings.
        }
        try {
            c.startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            Toast.makeText(c, R.string.saver_manual, Toast.LENGTH_LONG).show();
        } catch (RuntimeException e) {
            Toast.makeText(c, R.string.settings_missing, Toast.LENGTH_LONG).show();
        }
    }

    /** The system's page about one app (open, force stop, clear data). */
    static void appInfo(Context c, String pkg) {
        try {
            c.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (RuntimeException e) {
            Toast.makeText(c, R.string.settings_missing, Toast.LENGTH_LONG).show();
        }
    }

    /** Asks Android to uninstall an app; Android shows its own confirmation, and refuses for system apps. */
    static void uninstall(Context c, String pkg) {
        try {
            c.startActivity(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + pkg))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (RuntimeException e) {
            Toast.makeText(c, R.string.uninstall_failed, Toast.LENGTH_LONG).show();
        }
    }

    // ---------------------------------------------------------------- about

    private static void about(Context c) {
        new Menu.Builder(c)
            .setTitle(R.string.menu_about)
            .setMessage(c.getString(R.string.about_text) + "\n\nالراديو (تشخيص):\n"
                + c.getSharedPreferences("magiic", Context.MODE_PRIVATE).getString("radio_log", "لا أحداث بعد"))
            .setPositiveButton(android.R.string.ok, null)
            .show();
    }
}
