package app.mylauncher.gtv;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;

/**
 * The two looks of the home screen: "royal" (deep green and gold) and "kids" (soft pink, cream and rose). Every colour
 * and shape comes from here, so the screens, the dialogs' popups and the status icons always agree.
 */
final class Theme {
    private Theme() {}

    static final int ROYAL = 0, KIDS = 1;
    private static final String PREFS = "magiic";
    private static final String KEY = "theme";

    static int id(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY, ROYAL) == KIDS ? KIDS : ROYAL;
    }

    static void set(Context c, int id) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY, id).apply();
    }

    static boolean kids(Context c) {
        return id(c) == KIDS;
    }

    private static int pick(Context c, String royal, String kids) {
        return Color.parseColor(kids(c) ? kids : royal);
    }

    // ---------------------------------------------------------------- colours

    /** Text drawn straight on the background (the title, the clock). */
    static int bgText(Context c) {
        return pick(c, "#FFFFF4D6", "#FF7A3347");
    }

    /** Text inside a panel. */
    static int text(Context c) {
        return pick(c, "#FFFFF8E7", "#FFFFFFFF");
    }

    static int textDim(Context c) {
        return pick(c, "#FFD9CDA6", "#FFFFE9EE");
    }

    /** Gold (royal) or warm cream (kids): outlines, the iqama times, highlights. */
    static int accent(Context c) {
        return pick(c, "#FFE8B84A", "#FFFFF0CC");
    }

    static int pillFill(Context c) {
        return pick(c, "#FFE9C766", "#FFFFF4E0");
    }

    static int pillText(Context c) {
        return pick(c, "#FF3A2A00", "#FF7A3347");
    }

    static int panelFill(Context c) {
        return pick(c, "#CC0A3B2C", "#F2E58AA2");
    }

    static int tileFill(Context c) {
        return pick(c, "#E60B4232", "#FFFFF4E0");
    }

    static int tileLabel(Context c) {
        return pick(c, "#FFF6E3A6", "#FF7A3347");
    }

    /** The first-row gradient of the popups (adhan, iqama, reminders). */
    static int popupTop(Context c) {
        return pick(c, "#FF126B50", "#FFFFB6C8");
    }

    static int popupBottom(Context c) {
        return pick(c, "#FF052B20", "#FFE5667F");
    }

    static int popupText(Context c) {
        return pick(c, "#FFFFF8E7", "#FF6B2237");
    }

    static int popupDim(Context c) {
        return pick(c, "#FFE4D8B0", "#FF8A4558");
    }

    // ---------------------------------------------------------------- shapes

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    /** The whole-screen background: a gradient with a faint Islamic star pattern on it. */
    static Drawable background(Context c) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[] {
            pick(c, "#FF0E5440", "#FFFFDDE6"), pick(c, "#FF041F17", "#FFF6B3C4")});
        return new LayerDrawable(new Drawable[] {g, new IslamicPattern(c, kids(c) ? 0x30FFFFFF : 0x22E8B84A)});
    }

    /** The popup's background: gradient in the active look, the pattern, and a gold frame. */
    static Drawable popup(Context c) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            new int[] {popupTop(c), popupBottom(c)});
        GradientDrawable frame = new GradientDrawable();
        frame.setColor(Color.TRANSPARENT);
        frame.setStroke(dp(c, 4), pick(c, "#FFE8B84A", "#FFFFF0CC"));
        frame.setCornerRadius(dp(c, 24));
        LayerDrawable l = new LayerDrawable(new Drawable[] {
            g, new IslamicPattern(c, kids(c) ? 0x40FFFFFF : 0x30E8B84A), frame});
        int m = dp(c, 18);
        l.setLayerInset(2, m, m, m, m);
        return l;
    }

    /** A rounded panel with a thin outline. */
    static GradientDrawable panel(Context c, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(panelFill(c));
        g.setCornerRadius(dp(c, radiusDp));
        g.setStroke(dp(c, 2), accent(c));
        return g;
    }

    /** A button: soft fill, thick outline in the accent colour when the remote is on it. */
    static Drawable chip(Context c) {
        StateListDrawable s = new StateListDrawable();
        GradientDrawable focused = new GradientDrawable();
        focused.setColor(pick(c, "#66E8B84A", "#FFEB7F9B"));
        focused.setCornerRadius(dp(c, 14));
        focused.setStroke(dp(c, 3), pick(c, "#FFE8B84A", "#FFFFFFFF"));
        GradientDrawable normal = new GradientDrawable();
        normal.setColor(pick(c, "#55000000", "#CCD9788E"));
        normal.setCornerRadius(dp(c, 14));
        normal.setStroke(dp(c, 1), pick(c, "#55E8B84A", "#66FFF0CC"));
        s.addState(new int[] {android.R.attr.state_focused}, focused);
        s.addState(new int[] {}, normal);
        return s;
    }

    /** Text on a chip. */
    static int chipText(Context c) {
        return pick(c, "#FFFFF8E7", "#FFFFFFFF");
    }

    /** What the chip's text turns into while focused (kids' focused chip is light). */
    static int chipTextFocused(Context c) {
        return pick(c, "#FFFFFFFF", "#FF7A3347");
    }

    static GradientDrawable pill(Context c) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(pillFill(c));
        g.setCornerRadius(dp(c, 16));
        g.setStroke(dp(c, 1), pick(c, "#FFB88A22", "#FFE0A3B3"));
        return g;
    }

    /** The marker over the app (or row) the remote is on. */
    static Drawable focusRing(Context c, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(pick(c, "#26E8B84A", "#33FFFFFF"));
        g.setCornerRadius(dp(c, radiusDp));
        g.setStroke(dp(c, 4), pick(c, "#FFE8B84A", "#FFFFFFFF"));
        return g;
    }

    static int tileColor(String pkg) {
        return Color.WHITE;
    }

    // ---------------------------------------------------------------- the home screen's own colours and shapes
    // (sizes here are canvas units, not dp: the home screen is drawn on a scaled canvas, see Stage)

    static int listName(Context c) { return pick(c, "#FFFFF8E7", "#FF7A3347"); }
    static int listDim(Context c) { return pick(c, "#FFD9CDA6", "#FFA5586D"); }
    static int cardText(Context c) { return pick(c, "#FFFFF8E7", "#FFFFFFFF"); }
    static int cardGold(Context c) { return pick(c, "#FFF2CE6A", "#FFFFF0CC"); }
    static int cardDim(Context c) { return pick(c, "#FFD9CDA6", "#FFFFE9EE"); }
    static int clockFill(Context c) { return pick(c, "#FFF7D878", "#FFFFF6E4"); }
    static int clockOutline(Context c) { return pick(c, "#FF6B4A06", "#FFB8566F"); }
    static int nextText(Context c) { return pick(c, "#FF3A2A00", "#FF7A3347"); }

    private static GradientDrawable shape(int fill, float r, int strokePx, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(r);
        if (strokePx > 0) g.setStroke(strokePx, stroke);
        return g;
    }

    /** A button on the canvas: quiet when off, bright outline when the remote is on it. */
    static Drawable button(Context c, float r) {
        boolean k = kids(c);
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[] {android.R.attr.state_focused},
            shape(Color.parseColor(k ? "#FFF4A9BC" : "#66E8B84A"), r, 3, Color.parseColor(k ? "#FFFFFFFF" : "#FFF2CE6A")));
        s.addState(new int[] {},
            shape(Color.parseColor(k ? "#FFD9788E" : "#B20A3B2C"), r, 2, Color.parseColor(k ? "#FFFFE9D2" : "#B3B88A22")));
        return s;
    }

    /** The marker over the thing the remote is on. */
    static Drawable ring(Context c, float r) {
        boolean k = kids(c);
        return shape(Color.parseColor(k ? "#33FFFFFF" : "#26E8B84A"), r, 4, Color.parseColor(k ? "#FFFFFFFF" : "#FFF2CE6A"));
    }

    /** The time pills (gold in the royal look, cream in the kids' look). */
    static Drawable timePill(Context c) {
        boolean k = kids(c);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            k ? new int[] {Color.parseColor("#FFFFF8EC"), Color.parseColor("#FFFBE3C6")}
              : new int[] {Color.parseColor("#FFF8DD84"), Color.parseColor("#FFD9A93A")});
        g.setCornerRadius(20);
        g.setStroke(2, Color.parseColor(k ? "#FFE5A9B7" : "#FFB88A22"));
        return g;
    }

    /** A soft panel behind small chips and the app tiles. */
    static Drawable tile(Context c, float r) {
        boolean k = kids(c);
        return shape(Color.parseColor(k ? "#FFFFF4E0" : "#CC082F22"), r, 2, Color.parseColor(k ? "#FFE58FA3" : "#FFB88A22"));
    }

    /** The row of the prayer that is next. */
    static Drawable highlight(Context c, float r) {
        boolean k = kids(c);
        return shape(Color.parseColor(k ? "#59FFFFFF" : "#40E8B84A"), r, 2, Color.parseColor(k ? "#FFFFFFFF" : "#FFF2CE6A"));
    }

    static int edgeColour(Context c) {
        return Color.parseColor(kids(c) ? "#FFF4B4C6" : "#FF04251A");
    }

    // ---------------------------------------------------------------- the dome popup
    static int popTitle(Context c) { return pick(c, "#FFFFF1C2", "#FF8A3B52"); }
    static int popSub(Context c) { return pick(c, "#FFFFF8E7", "#FFFFFFFF"); }
    static int popHint(Context c) { return pick(c, "#FFE8C15C", "#FFFFF0CC"); }
    static int scrim(Context c) { return pick(c, "#D9031A12", "#D9C8607F"); }
}
