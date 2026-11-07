package app.mylauncher.gtv;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;

/** The home-screen widget: the next prayer (or the iqama) with a countdown. {@link PrayerNote} keeps it up to date. */
public class PrayerWidget extends AppWidgetProvider {
    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        PrayerNote.refresh(c);
    }

    @Override
    public void onEnabled(Context c) {
        PrayerNote.refresh(c);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context c, AppWidgetManager m, int id, android.os.Bundle o) {
        PrayerNote.refresh(c);
    }
}
