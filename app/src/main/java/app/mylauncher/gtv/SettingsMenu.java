package app.mylauncher.gtv;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.text.InputType;
import android.widget.EditText;
import android.widget.Toast;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The launcher's settings menu: hidden apps, the ad-blocking DNS, and the about page.
 * The DNS option needs a one-time permission that can only be given from a computer or an ADB app (see
 * {@link #dnsHelp}); until then the app explains how, and changes nothing.
 */
final class SettingsMenu {
    private SettingsMenu() {}

    static final String PREFS = "magiic";
    private static final String HIDDEN = "hidden";
    private static final String PERMISSION = "android.permission.WRITE_SECURE_SETTINGS";

    /** One choice of DNS: a name for the list and the host name (null = back to the system's own setting). */
    private static final String[][] DNS = {
        {"AdGuard", "dns.adguard-dns.com"},
        {"AdGuard (unfiltered)", "unfiltered.adguard-dns.com"},
        {"AdGuard (family)", "family.adguard-dns.com"},
    };

    // ---------------------------------------------------------------- hidden apps

    static Set<String> hidden(Context c) {
        return new HashSet<>(c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet(HIDDEN, new HashSet<String>()));
    }

    static void setHidden(Context c, Set<String> set) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putStringSet(HIDDEN, new HashSet<>(set)).apply();
    }

    // ---------------------------------------------------------------- the menu

    static void open(final Context c, final List<String[]> allApps, final Runnable appsChanged) {
        String[] items = {
            c.getString(R.string.menu_hidden),
            c.getString(R.string.menu_dns),
            c.getString(R.string.menu_about),
        };
        new AlertDialog.Builder(c)
            .setTitle(R.string.menu_title)
            .setItems(items, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int which) {
                    if (which == 0) hiddenApps(c, allApps, appsChanged);
                    else if (which == 1) dns(c);
                    else about(c);
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
        new AlertDialog.Builder(c)
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
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    // ---------------------------------------------------------------- the ad-blocking DNS

    private static boolean canWrite(Context c) {
        return c.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED;
    }

    private static String currentDns(Context c) {
        try {
            String mode = Settings.Global.getString(c.getContentResolver(), "private_dns_mode");
            String host = Settings.Global.getString(c.getContentResolver(), "private_dns_specifier");
            if ("hostname".equals(mode) && host != null && !host.isEmpty()) return host;
        } catch (RuntimeException ignored) {
            // Unreadable: show it as automatic.
        }
        return c.getString(R.string.dns_auto);
    }

    private static void dns(final Context c) {
        if (!canWrite(c)) {
            dnsHelp(c);
            return;
        }
        String[] items = new String[DNS.length + 3];
        for (int i = 0; i < DNS.length; i++) items[i] = DNS[i][0] + "\n" + DNS[i][1];
        items[DNS.length] = c.getString(R.string.dns_custom);
        items[DNS.length + 1] = c.getString(R.string.dns_off);
        items[DNS.length + 2] = c.getString(R.string.dns_note);
        new AlertDialog.Builder(c)
            .setTitle(c.getString(R.string.dns_title, currentDns(c)))
            .setItems(items, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int which) {
                    if (which < DNS.length) apply(c, DNS[which][1]);
                    else if (which == DNS.length) custom(c);
                    else if (which == DNS.length + 1) apply(c, null);
                    else noteDialog(c);
                }
            })
            .show();
    }

    private static void custom(final Context c) {
        final EditText input = new EditText(c);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setHint("dns.example.com");
        new AlertDialog.Builder(c)
            .setTitle(R.string.dns_custom)
            .setView(input)
            .setPositiveButton(R.string.save, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int w) {
                    String host = input.getText().toString().trim().toLowerCase();
                    // A real host name: letters, digits, dots and dashes, with at least one dot.
                    if (host.length() > 253 || !host.matches("[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+")) {
                        Toast.makeText(c, R.string.dns_bad, Toast.LENGTH_LONG).show();
                        return;
                    }
                    apply(c, host);
                }
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    /** Sets the private DNS to {@code host}, or back to automatic when it is null. */
    private static void apply(Context c, String host) {
        try {
            if (host == null) {
                Settings.Global.putString(c.getContentResolver(), "private_dns_mode", "opportunistic");
            } else {
                // The host first, then the mode that uses it.
                Settings.Global.putString(c.getContentResolver(), "private_dns_specifier", host);
                Settings.Global.putString(c.getContentResolver(), "private_dns_mode", "hostname");
            }
            Toast.makeText(c, host == null ? c.getString(R.string.dns_done_off) : c.getString(R.string.dns_done, host),
                Toast.LENGTH_LONG).show();
        } catch (RuntimeException e) {
            Toast.makeText(c, R.string.dns_failed, Toast.LENGTH_LONG).show();
        }
    }

    private static void dnsHelp(Context c) {
        new AlertDialog.Builder(c)
            .setTitle(R.string.menu_dns)
            .setMessage(c.getString(R.string.dns_help, c.getPackageName(), PERMISSION))
            .setPositiveButton(android.R.string.ok, null)
            .show();
    }

    private static void noteDialog(Context c) {
        new AlertDialog.Builder(c)
            .setTitle(R.string.menu_dns)
            .setMessage(R.string.dns_note_text)
            .setPositiveButton(android.R.string.ok, null)
            .show();
    }

    // ---------------------------------------------------------------- about

    private static void about(Context c) {
        new AlertDialog.Builder(c)
            .setTitle(R.string.menu_about)
            .setMessage(R.string.about_text)
            .setPositiveButton(android.R.string.ok, null)
            .show();
    }
}
