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
        new int[] {46, 196, 298, 566}, new int[] {344, 214, 842, 512}, new int[] {354, 518, 829, 576},
        new int[] {886, 232, 1336, 412}, new int[] {870, 429, 1099, 579}, new int[] {1114, 429, 1341, 579},
        new int[] {40, 606, 1336, 736});
    private static final Geo KIDS = new Geo(
        new int[] {46, 186, 296, 566}, new int[] {338, 212, 842, 512}, new int[] {358, 522, 826, 578},
        new int[] {886, 234, 1336, 414}, new int[] {872, 430, 1098, 582}, new int[] {1115, 430, 1338, 582},
        new int[] {34, 608, 1334, 742});

    static Geo of(Context c) {
        return Theme.kids(c) ? KIDS : ROYAL;
    }
}
