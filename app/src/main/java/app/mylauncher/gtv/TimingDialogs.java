package app.mylauncher.gtv;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.widget.Toast;

/**
 * "Adhan and iqama settings": pick a prayer, then either its time adjustment or its iqama delay. The same list
 * also has the two test buttons. Every change calls {@code changed} so the screen and the alarm are updated.
 */
final class TimingDialogs {
    private TimingDialogs() {}

    static void open(final Context c, final Runnable changed) {
        int[] adj = PrayerPrefs.adjust(c);
        int[] iq = PrayerPrefs.iqama(c);
        String[] items = new String[8];
        for (int i = 0; i < 5; i++) {
            items[i] = c.getString(R.string.main_row, PrayerCalc.name(c, i, false),
                PrayerPrefs.adjustText(c, adj[i]), PrayerPrefs.iqamaText(c, iq[i]));
        }
        items[5] = c.getString(R.string.test_btn);
        items[6] = c.getString(R.string.test_fajr_item);
        items[7] = c.getString(R.string.test_iqama_item);

        new AlertDialog.Builder(c)
            .setTitle(R.string.settings_title)
            .setItems(items, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int which) {
                    if (which < 5) {
                        field(c, which, changed);
                    } else {
                        Scheduler.scheduleTest(c, which == 7, which == 6 ? PrayerCalc.FAJR : PrayerCalc.DHUHR);
                        Toast.makeText(c, R.string.test_scheduled, Toast.LENGTH_LONG).show();
                    }
                }
            })
            .show();
    }

    private static void field(final Context c, final int index, final Runnable changed) {
        final int adj = PrayerPrefs.adjust(c)[index];
        final int iq = PrayerPrefs.iqama(c)[index];
        final String prayer = PrayerCalc.name(c, index, false);
        String[] items = {
            c.getString(R.string.adjust_item, PrayerPrefs.adjustText(c, adj)),
            c.getString(R.string.iqama_item, PrayerPrefs.iqamaText(c, iq)),
        };
        new AlertDialog.Builder(c)
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
                                    open(c, changed);
                                }
                            });
                    } else {
                        NumberDialog.show(c, c.getString(R.string.iqama_set_title, prayer), 0, 90, iq, false,
                            c.getString(R.string.iqama_off), new NumberDialog.OnSave() {
                                @Override
                                public void saved(int value) {
                                    PrayerPrefs.setIqama(c, index, value);
                                    changed.run();
                                    open(c, changed);
                                }
                            });
                    }
                }
            })
            .show();
    }
}
