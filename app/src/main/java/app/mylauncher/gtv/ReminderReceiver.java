package app.mylauncher.gtv;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.util.ArrayList;
import java.util.List;

/** Rings when a reminder is due: lines up the next one, then shows what is due. */
public class ReminderReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        long at = i.getLongExtra("at", 0L);
        // The next one first, so whatever happens below there is always an alarm waiting.
        Reminders.scheduleNext(c, at);
        if (at <= 0 || System.currentTimeMillis() - at > Reminders.TOO_LATE_MS) return;

        List<Reminders.Item> due = Reminders.dueAt(c, at);
        if (due.isEmpty()) return;
        ArrayList<String> titles = new ArrayList<>();
        ArrayList<String> texts = new ArrayList<>();
        ArrayList<Integer> minutes = new ArrayList<>();
        for (Reminders.Item it : due) {
            titles.add(Reminders.title(c, it));
            texts.add(Reminders.message(c, it, at));
            minutes.add(it.durationMin);
        }
        Intent s = new Intent(c, ReminderService.class)
            .putStringArrayListExtra("titles", titles)
            .putStringArrayListExtra("texts", texts)
            .putIntegerArrayListExtra("minutes", minutes);
        try {
            c.startForegroundService(s);
        } catch (RuntimeException e) {
            // Android refused to start it from the background; nothing more can be done here.
        }
    }
}
