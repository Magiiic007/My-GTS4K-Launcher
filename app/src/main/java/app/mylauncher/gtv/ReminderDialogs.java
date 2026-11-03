package app.mylauncher.gtv;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.text.InputType;
import android.widget.EditText;

import java.util.List;

/** The reminders list and the editor of one reminder. Every change is saved at once and the alarm is set again. */
final class ReminderDialogs {
    private ReminderDialogs() {}

    static void open(final Context c) {
        final List<Reminders.Item> list = Reminders.load(c);
        String[] items = new String[list.size() + 1];
        for (int i = 0; i < list.size(); i++) {
            Reminders.Item it = list.get(i);
            items[i] = (it.enabled ? "● " : "○ ") + Reminders.title(c, it) + "  " + time(it);
        }
        items[list.size()] = c.getString(R.string.rem_add);
        new AlertDialog.Builder(c, Theme.dialogStyle(c))
            .setTitle(R.string.menu_reminders)
            .setItems(items, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int which) {
                    if (which < list.size()) {
                        edit(c, list, which);
                    } else {
                        list.add(Reminders.newCustom());
                        save(c, list);
                        edit(c, list, list.size() - 1);
                    }
                }
            })
            .show();
    }

    private static String time(Reminders.Item it) {
        return String.format(java.util.Locale.US, "%02d:%02d", it.hour, it.minute);
    }

    private static void save(Context c, List<Reminders.Item> list) {
        Reminders.save(c, list);
        Reminders.scheduleNext(c);
    }

    private static String daysText(Context c, int mask) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 7; i++) {
            if ((mask & (1 << i)) != 0) {
                if (b.length() > 0) b.append("، ");
                b.append(Hijri.dayNameAr(i));
            }
        }
        return b.length() == 0 ? c.getString(R.string.rem_no_days) : b.toString();
    }

    private static String whiteText(Context c, int mask) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 3; i++) {
            if ((mask & (1 << i)) != 0) {
                if (b.length() > 0) b.append("، ");
                b.append(13 + i);
            }
        }
        return b.length() == 0 ? c.getString(R.string.rem_no_days) : b.toString();
    }

    private static void edit(final Context c, final List<Reminders.Item> list, final int index) {
        final Reminders.Item it = list.get(index);
        final java.util.ArrayList<String> labels = new java.util.ArrayList<>();
        final java.util.ArrayList<Integer> actions = new java.util.ArrayList<>();
        final int ON = 0, DAYS = 1, WHITE = 2, TITLE = 3, TEXT = 4, BEFORE = 5, TIME = 6, DURATION = 7, DELETE = 8;

        labels.add(c.getString(R.string.rem_enabled, c.getString(it.enabled ? R.string.rem_yes : R.string.rem_no)));
        actions.add(ON);
        if (it.kind == Reminders.CUSTOM) {
            labels.add(c.getString(R.string.rem_title_item, Reminders.title(c, it)));
            actions.add(TITLE);
            labels.add(c.getString(R.string.rem_text_item, it.text.isEmpty() ? c.getString(R.string.rem_none) : it.text));
            actions.add(TEXT);
            labels.add(c.getString(R.string.rem_days_show, daysText(c, it.daysMask)));
            actions.add(DAYS);
        } else if (it.kind == Reminders.ADHKAR_AM || it.kind == Reminders.ADHKAR_PM) {
            labels.add(c.getString(R.string.rem_days_show, daysText(c, it.daysMask)));
            actions.add(DAYS);
        } else if (it.kind == Reminders.WEEK_FAST) {
            labels.add(c.getString(R.string.rem_days_fast, daysText(c, it.daysMask)));
            actions.add(DAYS);
        } else if (it.kind == Reminders.WHITE_FAST) {
            labels.add(c.getString(R.string.rem_days_white, whiteText(c, it.hijriMask)));
            actions.add(WHITE);
        }
        if (Reminders.isFast(it.kind)) {
            labels.add(c.getString(R.string.rem_when,
                c.getString(it.dayBefore ? R.string.rem_day_before : R.string.rem_same_day)));
            actions.add(BEFORE);
        }
        labels.add(c.getString(R.string.rem_time_item, time(it)));
        actions.add(TIME);
        labels.add(c.getString(R.string.rem_duration_item, it.durationMin == 0
            ? c.getString(R.string.rem_manual) : c.getString(R.string.minutes_plain, it.durationMin)));
        actions.add(DURATION);
        if (it.kind == Reminders.CUSTOM) {
            labels.add(c.getString(R.string.rem_delete));
            actions.add(DELETE);
        }

        new AlertDialog.Builder(c, Theme.dialogStyle(c))
            .setTitle(Reminders.title(c, it))
            .setItems(labels.toArray(new String[0]), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int which) {
                    int a = actions.get(which);
                    if (a == ON) {
                        it.enabled = !it.enabled;
                        done(c, list, index);
                    } else if (a == DAYS) {
                        pickDays(c, list, index);
                    } else if (a == WHITE) {
                        pickWhite(c, list, index);
                    } else if (a == TITLE) {
                        typeText(c, c.getString(R.string.rem_title_hint), it.title, new Setter() {
                            @Override
                            public void set(String v) {
                                it.title = v;
                            }
                        }, list, index);
                    } else if (a == TEXT) {
                        typeText(c, c.getString(R.string.rem_text_hint), it.text, new Setter() {
                            @Override
                            public void set(String v) {
                                it.text = v;
                            }
                        }, list, index);
                    } else if (a == BEFORE) {
                        it.dayBefore = !it.dayBefore;
                        done(c, list, index);
                    } else if (a == TIME) {
                        pickTime(c, list, index);
                    } else if (a == DURATION) {
                        NumberDialog.show(c, c.getString(R.string.rem_duration_title), 0, 240, it.durationMin,
                            c.getString(R.string.minutes_plain), c.getString(R.string.minutes_plain), false,
                            c.getString(R.string.rem_manual), new NumberDialog.OnSave() {
                                @Override
                                public void saved(int value) {
                                    it.durationMin = value;
                                    done(c, list, index);
                                }
                            });
                    } else if (a == DELETE) {
                        list.remove(index);
                        save(c, list);
                        open(c);
                    }
                }
            })
            .setNegativeButton(R.string.back, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int w) {
                    open(c);
                }
            })
            .show();
    }

    private static void done(Context c, List<Reminders.Item> list, int index) {
        save(c, list);
        edit(c, list, index);
    }

    private static void pickDays(final Context c, final List<Reminders.Item> list, final int index) {
        final Reminders.Item it = list.get(index);
        final boolean[] on = new boolean[7];
        String[] names = new String[7];
        for (int i = 0; i < 7; i++) {
            names[i] = Hijri.dayNameAr(i);
            on[i] = (it.daysMask & (1 << i)) != 0;
        }
        new AlertDialog.Builder(c, Theme.dialogStyle(c))
            .setTitle(R.string.rem_pick_days)
            .setMultiChoiceItems(names, on, new DialogInterface.OnMultiChoiceClickListener() {
                @Override
                public void onClick(DialogInterface d, int which, boolean checked) {
                    on[which] = checked;
                }
            })
            .setPositiveButton(R.string.save, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int w) {
                    int mask = 0;
                    for (int i = 0; i < 7; i++) if (on[i]) mask |= 1 << i;
                    it.daysMask = mask;
                    done(c, list, index);
                }
            })
            .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int w) {
                    edit(c, list, index);
                }
            })
            .show();
    }

    private static void pickWhite(final Context c, final List<Reminders.Item> list, final int index) {
        final Reminders.Item it = list.get(index);
        final boolean[] on = new boolean[3];
        String[] names = new String[3];
        for (int i = 0; i < 3; i++) {
            names[i] = c.getString(R.string.rem_hijri_day, 13 + i);
            on[i] = (it.hijriMask & (1 << i)) != 0;
        }
        new AlertDialog.Builder(c, Theme.dialogStyle(c))
            .setTitle(R.string.rem_pick_days)
            .setMultiChoiceItems(names, on, new DialogInterface.OnMultiChoiceClickListener() {
                @Override
                public void onClick(DialogInterface d, int which, boolean checked) {
                    on[which] = checked;
                }
            })
            .setPositiveButton(R.string.save, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int w) {
                    int mask = 0;
                    for (int i = 0; i < 3; i++) if (on[i]) mask |= 1 << i;
                    it.hijriMask = mask;
                    done(c, list, index);
                }
            })
            .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int w) {
                    edit(c, list, index);
                }
            })
            .show();
    }

    /** The hour, then the minute: each picked with Up and Down on the remote. */
    private static void pickTime(final Context c, final List<Reminders.Item> list, final int index) {
        final Reminders.Item it = list.get(index);
        NumberDialog.show(c, c.getString(R.string.rem_hour_title), 0, 23, it.hour, "%1$02d", "%1$02d", false, null,
            new NumberDialog.OnSave() {
                @Override
                public void saved(int hour) {
                    it.hour = hour;
                    NumberDialog.show(c, c.getString(R.string.rem_minute_title), 0, 59, it.minute, "%1$02d", "%1$02d",
                        false, null, new NumberDialog.OnSave() {
                            @Override
                            public void saved(int minute) {
                                it.minute = minute;
                                done(c, list, index);
                            }
                        });
                }
            });
    }

    private interface Setter {
        void set(String value);
    }

    private static void typeText(final Context c, String title, String current, final Setter setter,
                                 final List<Reminders.Item> list, final int index) {
        final EditText input = new EditText(c);
        input.setSingleLine(false);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setText(current);
        new AlertDialog.Builder(c, Theme.dialogStyle(c))
            .setTitle(title)
            .setView(input)
            .setPositiveButton(R.string.save, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int w) {
                    setter.set(input.getText().toString().trim());
                    done(c, list, index);
                }
            })
            .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int w) {
                    edit(c, list, index);
                }
            })
            .show();
    }
}
