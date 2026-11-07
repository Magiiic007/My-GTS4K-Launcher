package app.mylauncher.gtv;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;

/**
 * "Adhan and iqama settings": pick a prayer, then either its time adjustment or its iqama delay. Every change calls {@code changed} so the screen and the alarm are updated.
 */
final class TimingDialogs {
    private TimingDialogs() {}

    static void open(final Context c, final Runnable changed) {
        int[] adj = PrayerPrefs.adjust(c);
        int[] iq = PrayerPrefs.iqama(c);
        final boolean home = c instanceof MainActivity;
        // The five prayers, then the Friday prayer (with its first adhan), then city and Hijri date on the home screen.
        String[] items = new String[home ? 8 : 6];
        for (int i = 0; i < 6; i++) {
            items[i] = c.getString(R.string.main_row, PrayerCalc.name(c, i, false),
                PrayerPrefs.adjustText(c, adj[i]), PrayerPrefs.iqamaText(c, iq[i]));
        }
        if (home) {
            items[6] = c.getString(R.string.menu_city);
            items[7] = c.getString(R.string.menu_hijri);
        }

        new Menu.Builder(c)
            .setTitle(R.string.settings_title)
            .setItems(items, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int which) {
                    if (which == 6) {
                        ((MainActivity) c).chooseCountry();
                    } else if (which == 7) {
                        ((MainActivity) c).chooseHijri();
                    } else {
                        field(c, which, changed);
                    }
                }
            })
            .show();
    }

    /** One prayer: its time adjustment and iqama delay; the Friday prayer also has the first adhan. */
    static void field(final Context c, final int index, final Runnable changed) {
        final int adj = PrayerPrefs.adjust(c)[index];
        final int iq = PrayerPrefs.iqama(c)[index];
        final boolean friday = index == PrayerPrefs.FRIDAY;
        final String prayer = PrayerCalc.name(c, index, false);
        String[] items = new String[friday ? 3 : 2];
        items[0] = c.getString(R.string.adjust_item, PrayerPrefs.adjustText(c, adj));
        items[1] = c.getString(R.string.iqama_item, PrayerPrefs.iqamaText(c, iq));
        if (friday) items[2] = c.getString(R.string.first_adhan_item, PrayerPrefs.firstAdhanText(c, PrayerPrefs.firstAdhan(c)));
        new Menu.Builder(c)
            .setTitle(prayer)
            .setItems(items, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int which) {
                    if (which == 0) {
                        NumberDialog.show(c, c.getString(R.string.adjust_title, prayer), -10, 10, adj, true,
                            c.getString(R.string.no_adjust), new NumberDialog.OnSave() {
                                @Override
                                public void saved(int value) {
                                    PrayerPrefs.setAdjust(c, index, value);
                                    changed.run();
                                    field(c, index, changed);
                                }
                            });
                    } else if (which == 1) {
                        NumberDialog.show(c, c.getString(R.string.iqama_set_title, prayer), 0, 90, iq, false,
                            c.getString(R.string.iqama_off), new NumberDialog.OnSave() {
                                @Override
                                public void saved(int value) {
                                    PrayerPrefs.setIqama(c, index, value);
                                    changed.run();
                                    field(c, index, changed);
                                }
                            });
                    } else {
                        NumberDialog.show(c, c.getString(R.string.first_adhan_set_title), 0, 90,
                            PrayerPrefs.firstAdhan(c), false, c.getString(R.string.first_adhan_off),
                            new NumberDialog.OnSave() {
                                @Override
                                public void saved(int value) {
                                    PrayerPrefs.setFirstAdhan(c, value);
                                    changed.run();
                                    field(c, index, changed);
                                }
                            });
                    }
                }
            })
            .setNegativeButton(R.string.close, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int w) {
                    open(c, changed);
                }
            })
            .show();
    }
}
