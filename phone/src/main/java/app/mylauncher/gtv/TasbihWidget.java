package app.mylauncher.gtv;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;

/** A home-screen widget; {@link Widgets} draws it. */
public class TasbihWidget extends AppWidgetProvider {
    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        Widgets.refresh(c);
    }

    @Override
    public void onEnabled(Context c) {
        Widgets.refresh(c);
    }
}
