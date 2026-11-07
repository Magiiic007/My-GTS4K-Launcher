package app.mylauncher.gtv;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;

/** A home-screen widget; {@link Widgets} draws it. */
public class TrackWidget extends AppWidgetProvider {
    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        Widgets.refresh(c);
    }

    @Override
    public void onEnabled(Context c) {
        Widgets.refresh(c);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context c, AppWidgetManager m, int id, android.os.Bundle o) {
        Widgets.refresh(c);
    }
}
