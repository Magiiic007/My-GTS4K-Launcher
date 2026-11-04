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

    /** Which group the editor goes back to: 0 = the main list, 1 = fasting, 2 = adhkar, 3 = occasions. */
    private static int group = 0;

    static void open(Context c) {
        group = 0;
        showMain(c);
    }

    private static void back(Context c) {
        if (group == 0) showMain(c);
        else showGroup(c, group);
    }

    /** Groups first, then the Friday reminder and the custom ones: no times in the text (they are set inside). */
    private static void showMain(final Context c) {
        final List<Reminders.Item> list = Reminders.load(c);
        final java.util.ArrayList<String> labels = new java.util.ArrayList<>();
        final java.util.ArrayList<Integer> target = new java.util.ArrayList<>(); // >= 0 item index, -1..-3 group, -9 add
        labels.add(c.getString(R.string.rem_group_fast));
        target.add(-1);
        labels.add(c.getString(R.string.rem_group_dhikr));
        target.add(-2);
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).kind == Reminders.KAHF) {
                labels.add(mark(c, list.get(i)));
                target.add(i);
            }
        }
        labels.add(c.getString(R.string.rem_group_occ));
        target.add(-3);
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).kind == Reminders.CUSTOM) {
                labels.add(mark(c, list.get(i)));
                target.add(i);
            }
        }
        labels.add(c.getString(R.string.rem_add));
        target.add(-9);
        new Menu.Builder(c)
            .setTitle(R.string.menu_reminders)
            .setItems(labels.toArray(new String[0]), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int which) {
                    int t = target.get(which);
                    if (t >= 0) {
                        group = 0;
                        edit(c, list, t);
                    } else if (t == -9) {
                        group = 0;
                        list.add(Reminders.newCustom());
                        save(c, list);
                        edit(c, list, list.size() - 1);
                    } else {
                        showGroup(c, -t);
                    }
                }
            })
            .show();
    }

    private static String mark(Context c, Reminders.Item it) {
        return (it.enabled ? "● " : "○ ") + Reminders.title(c, it);
    }

    private static void showGroup(final Context c, final int g) {
        group = g;
        final List<Reminders.Item> list = Reminders.load(c);
        final java.util.ArrayList<String> labels = new java.util.ArrayList<>();
        final java.util.ArrayList<Integer> target = new java.util.ArrayList<>(); // item index, or -5 = upcoming list
        for (int i = 0; i < list.size(); i++) {
            int k = list.get(i).kind;
            boolean in = g == 1 ? Reminders.isFast(k)
                : g == 2 ? (k == Reminders.ADHKAR_AM || k == Reminders.ADHKAR_PM)
                : k == Reminders.OCCASION;
            if (in) {
                labels.add(mark(c, list.get(i)));
                target.add(i);
            }
        }
        if (g == 3) {
            labels.add(c.getString(R.string.rem_upcoming));
            target.add(-5);
        }
        int title = g == 1 ? R.string.rem_group_fast : g == 2 ? R.string.rem_group_dhikr : R.string.rem_group_occ;
        new Menu.Builder(c)
            .setTitle(title)
            .setItems(labels.toArray(new String[0]), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int which) {
                    int t = target.get(which);
                    if (t == -5) showUpcoming(c);
                    else edit(c, list, t);
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

    /** The coming occasions with their dates and the days left. */
    private static void showUpcoming(final Context c) {
        boolean ar = "ar".equals(java.util.Locale.getDefault().getLanguage());
        List<int[]> up = Reminders.upcoming(c, 12);
        String[] rows = new String[up.size()];
        for (int i = 0; i < rows.length; i++) {
            int[] u = up.get(i);
            java.util.Calendar d = Hijri.today(c);
            d.add(java.util.Calendar.DAY_OF_MONTH, u[1]);
            String date = ar ? Hijri.weekdayAr(d) + " " + Hijri.gregorianAr(d) : Hijri.gregorianEn(d);
            String left = u[1] == 0 ? c.getString(R.string.rem_today)
                : u[1] == 1 ? c.getString(R.string.rem_tomorrow) : c.getString(R.string.rem_in_days, u[1]);
            rows[i] = c.getString(R.string.rem_upcoming_row, Reminders.occName(c, u[0]), date, left);
        }
        new Menu.Builder(c)
            .setTitle(R.string.rem_upcoming)
            .setItems(rows, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int which) {
                    showGroup(c, 3);
                }
            })
            .setNegativeButton(R.string.back, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int w) {
                    showGroup(c, 3);
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
        if (Reminders.isFast(it.kind) || it.kind == Reminders.OCCASION) {
            boolean occ = it.kind == Reminders.OCCASION;
            labels.add(c.getString(R.string.rem_when, c.getString(it.dayBefore
                ? (occ ? R.string.rem_day_before_occ : R.string.rem_day_before)
                : (occ ? R.string.rem_same_day_occ : R.string.rem_same_day))));
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

        new Menu.Builder(c)
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
                    back(c);
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
        new Menu.Builder(c)
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
        new Menu.Builder(c)
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
        new Menu.Builder(c)
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
