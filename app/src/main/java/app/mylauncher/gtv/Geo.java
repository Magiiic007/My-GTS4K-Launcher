package app.mylauncher.gtv;

import android.content.Context;

/** Where the empty panels of each background picture are, in canvas units (left, top, right, bottom). */
final class Geo {
    final int[] settings, prayers, next, date, countAdhan, countIqama, bar;

    private Geo(int[] settings, int[] prayers, int[] next, int[] date, int[] ca, int[] ci, int[] bar) {
        this.settings = settings;
        this.prayers = prayers;
        this.next = next;
        this.date = date;
        this.countAdhan = ca;
        this.countIqama = ci;
        this.bar = bar;
    }

    private static final Geo ROYAL = new Geo(
        new int[] {64, 214, 292, 556}, new int[] {360, 232, 826, 498}, new int[] {412, 529, 790, 573},
        new int[] {898, 250, 1304, 396}, new int[] {886, 442, 1084, 568}, new int[] {1130, 442, 1324, 568},
        new int[] {46, 610, 1326, 730});
    private static final Geo KIDS = new Geo(
        new int[] {46, 186, 296, 566}, new int[] {338, 212, 842, 512}, new int[] {358, 522, 826, 578},
        new int[] {886, 234, 1336, 414}, new int[] {872, 430, 1098, 582}, new int[] {1115, 430, 1338, 582},
        new int[] {34, 608, 1334, 742});

    static Geo of(Context c) {
        return Theme.kids(c) ? KIDS : ROYAL;
    }
}
