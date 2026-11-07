package app.mylauncher.gtv;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.widget.ImageView;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * The menus and questions of the app, drawn like the design page: a title plate on the frame, recessed rows with a
 * bright outline where the remote is, section headers and a tick for the chosen row. It takes the place of the
 * system's plain list dialog and offers the same calls (title, items, single and multiple choice, message, view and
 * buttons). An item whose text starts with {@link #HEADER} is a section title, not a row.
 */
final class Menu {
    private Menu() {}

    /** Put this in front of an item to make it a section title. */
    static final String HEADER = "\u0001";

    /** The dialog; its message can be changed while it is shown (the download progress). */
    static final class Box extends Dialog {
        TextView message;
        View firstFocus;
        View panelView;
        android.view.ViewGroup bodyView;
        /** The menu this one was opened from: Back (or Close) comes back to it, on the row that opened this one. */
        Box parent;
        static Box lastShown;

        Box(Context c) {
            super(c, R.style.MagiicMenu);
        }

        /** Back goes one step: to the menu this one was opened from, if there is one. */
        @Override
        public void cancel() {
            super.cancel();
            reopenParent();
        }

        void reopenParent() {
            Box p = parent;
            parent = null;
            if (p != null) p.show();
        }

        @Override
        public void show() {
            lastShown = this;
            super.show();
            Window w = getWindow();
            if (w != null) {
                DisplayMetrics dm = getContext().getResources().getDisplayMetrics();
                w.setLayout(Math.round(dm.widthPixels * 0.42f), ViewGroup.LayoutParams.WRAP_CONTENT);
                w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                w.setDimAmount(0.65f);
            }
            if (firstFocus != null) firstFocus.requestFocus();
            final View pv = panelView;
            final android.view.ViewGroup bv = bodyView;
            if (pv != null) {
                pv.post(new Runnable() {
                    @Override
                    public void run() {
                        Motion.panelIn(pv);
                        if (bv != null) Motion.rowsIn(bv);
                    }
                });
            }
        }

        void setMessage(CharSequence text) {
            if (message != null) message.setText(text);
        }
    }

    /** The icon that goes with a menu row, chosen by what the row says (0 = none). */
    private static int iconFor(String t) {
        String[][] k = {
            {"الشبكة", "واي", "wifi", "network"}, {"ic_wifi"},
            {"بلوتوث", "bluetooth"}, {"ic_bluetooth"},
            {"الصوت", "صوت", "sound"}, {"ic_volume"},
            {"الشاشة الرئيسية", "home screen"}, {"ic_home"},
            {"شاشة التوقف", "screen saver", "الشاشة", "display"}, {"ic_display"},
            {"التاريخ والوقت", "الساعة", "الدقيقة", "وقت الظهور", "مدة", "date and time", "duration"}, {"ic_clock"},
            {"التقويم", "الهجري", "المناسبات", "الأيام", "أيام", "calendar", "days"}, {"ic_calendar"},
            {"اللغة", "language"}, {"ic_language"},
            {"التخزين", "storage"}, {"ic_storage"},
            {"التنظيف", "clean"}, {"ic_clean"},
            {"عن الجهاز", "عن التطبيق", "about"}, {"ic_info"},
            {"كل إعدادات", "إعدادات الجهاز", "all device", "device settings"}, {"ic_settings"},
            {"الحساب", "account"}, {"ic_account"},
            {"الأذان", "الإقامة", "adhan", "iqama"}, {"ic_bell"},
            {"الراديو", "radio"}, {"ic_radio"},
            {"شكل", "ثيم", "look", "theme"}, {"ic_palette"},
            {"الكهف"}, {"ic_book"},
            {"أذكار الصباح"}, {"ic_sunrise"},
            {"أذكار المساء"}, {"ic_sunset"},
            {"الأذكار", "أذكار"}, {"ic_mosque"},
            {"الصيام", "صيام"}, {"ic_crescent"},
            {"التذكير", "تذكير", "reminder"}, {"ic_star"},
            {"حذف", "delete"}, {"ic_trash"},
            {"إضافة", "add "}, {"ic_plus"},
            {"العنوان", "النص", "title:", "text:"}, {"ic_edit"},
            {"المدينة", "city"}, {"ic_location"},
            {"تحديث", "update"}, {"ic_update"},
            {"التنبيهات", "تنبيه", "notices"}, {"ic_warning"},
            {"التطبيقات", "تطبيقات", "إخفاء", "apps", "hide"}, {"ic_apps"},
            {"الطقس", "weather"}, {"ic_cloud_sun"},
            {"التفعيل", "enabled"}, {"ic_shield"},
        };
        String low = t.toLowerCase(java.util.Locale.ROOT);
        for (int i = 0; i < k.length; i += 2) {
            for (String w : k[i]) {
                if (low.contains(w)) {
                    try {
                        return R.drawable.class.getField(k[i + 1][0]).getInt(null);
                    } catch (ReflectiveOperationException e) {
                        return 0;
                    }
                }
            }
        }
        return 0;
    }

    /** Colours of the active look (the same ones as on the design page). */
    private static final class Pal {
        int top, bottom, border, halo, halo2, ink, accent, rowFill, rowLine, plateTop, plateBottom, plateLine, plateText,
            closeFill, scrim;
    }

    private static Pal pal(Context c) {
        Pal p = new Pal();
        if (Theme.kids(c)) {
            p.top = 0xFFFBD3DE; p.bottom = 0xFFEFA6BB; p.border = 0xFFFFF0CC; p.halo = 0xFFE58FA3; p.halo2 = 0xFFFFF0CC;
            p.ink = 0xFF7A3347; p.accent = 0xFFB8566F; p.rowFill = 0x99FFFFFF; p.rowLine = 0xE6E58FA3;
            p.plateTop = 0xFFFFF8EC; p.plateBottom = 0xFFFBE3C6; p.plateLine = 0xFFE5A9B7; p.plateText = 0xFF7A3347;
            p.closeFill = 0x66FFFFFF; p.scrim = 0xCCC8607F;
        } else {
            p.top = 0xFF0D4220; p.bottom = 0xFF062711; p.border = 0xFFD9A93A; p.halo = 0xFF05230F; p.halo2 = 0xCCD9A93A;
            p.ink = 0xFFFFF8E7; p.accent = 0xFFE8C15C; p.rowFill = 0x66000000; p.rowLine = 0x8CD9A93A;
            p.plateTop = 0xFFF8DD84; p.plateBottom = 0xFFD9A93A; p.plateLine = 0xFF8A5D12; p.plateText = 0xFF3A2A00;
            p.closeFill = 0x14FFFFFF; p.scrim = 0xE0031A12;
        }
        return p;
    }

    private static GradientDrawable shape(int fill, float r, int strokePx, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(r);
        if (strokePx > 0) g.setStroke(strokePx, stroke);
        return g;
    }

    /** A scroll area that never grows past a given height. */
    private static final class MaxScroll extends ScrollView {
        private final int max;

        MaxScroll(Context c, int max) {
            super(c);
            this.max = max;
            setVerticalScrollBarEnabled(false);
            setFillViewport(false);
        }

        @Override
        protected void onMeasure(int w, int h) {
            super.onMeasure(w, MeasureSpec.makeMeasureSpec(max, MeasureSpec.AT_MOST));
        }
    }

    static final class Builder {
        private final Context c;
        private CharSequence title, message;
        private CharSequence[] items;
        private boolean[] checked;
        private int single = -1;
        private int kind;   // 0 none, 1 items, 2 single, 3 multi
        private DialogInterface.OnClickListener itemClick;
        private DialogInterface.OnMultiChoiceClickListener multiClick;
        private View view;
        private CharSequence posText, negText, neuText;
        private DialogInterface.OnClickListener posClick, negClick, neuClick;
        private boolean cancelable = true;
        private List<Drawable> icons;

        Builder(Context context) {
            c = context;
        }

        Builder setTitle(int res) { title = c.getString(res); return this; }
        Builder setTitle(CharSequence t) { title = t; return this; }
        Builder setMessage(int res) { message = c.getString(res); return this; }
        Builder setMessage(CharSequence m) { message = m; return this; }
        Builder setView(View v) { view = v; return this; }
        Builder setCancelable(boolean b) { cancelable = b; return this; }
        /** A picture for each row (null = none), in the order of the items. */
        Builder setIcons(List<Drawable> list) { icons = list; return this; }

        Builder setItems(CharSequence[] it, DialogInterface.OnClickListener l) {
            items = it; itemClick = l; kind = 1; return this;
        }

        Builder setSingleChoiceItems(CharSequence[] it, int current, DialogInterface.OnClickListener l) {
            items = it; single = current; itemClick = l; kind = 2; return this;
        }

        Builder setMultiChoiceItems(CharSequence[] it, boolean[] on, DialogInterface.OnMultiChoiceClickListener l) {
            items = it; checked = on; multiClick = l; kind = 3; return this;
        }

        Builder setPositiveButton(int res, DialogInterface.OnClickListener l) {
            posText = c.getString(res); posClick = l; return this;
        }
        Builder setPositiveButton(CharSequence t, DialogInterface.OnClickListener l) {
            posText = t; posClick = l; return this;
        }
        Builder setNegativeButton(int res, DialogInterface.OnClickListener l) {
            negText = c.getString(res); negClick = l; return this;
        }
        Builder setNegativeButton(CharSequence t, DialogInterface.OnClickListener l) {
            negText = t; negClick = l; return this;
        }
        Builder setNeutralButton(int res, DialogInterface.OnClickListener l) {
            neuText = c.getString(res); neuClick = l; return this;
        }

        Box show() {
            Box d = create();
            d.show();
            return d;
        }

        Box create() {
            final Pal p = pal(c);
            DisplayMetrics dm = c.getResources().getDisplayMetrics();
            final float k = Math.min(dm.widthPixels / 1365f, dm.heightPixels / 768f);
            final Box d = new Box(c);
            d.requestWindowFeature(Window.FEATURE_NO_TITLE);
            d.setCancelable(cancelable);

            final FrameLayout root = new FrameLayout(c);
            root.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
            root.setClipChildren(false);

            int plateH = title != null ? Math.round(52 * k) : 0;
            LinearLayout panel = new LinearLayout(c);
            panel.setOrientation(LinearLayout.VERTICAL);
            int pad = Math.round(20 * k);
            panel.setPadding(pad, plateH + Math.round(18 * k), pad, Math.round(18 * k));
            GradientDrawable fill = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {p.top, p.bottom});
            fill.setCornerRadius(28 * k);
            fill.setStroke(Math.max(2, Math.round(3 * k)), p.border);
            LayerDrawable bg = new LayerDrawable(new Drawable[] {
                shape(Color.TRANSPARENT, 34 * k, Math.max(2, Math.round(2 * k)), p.halo2),
                shape(p.halo, 33 * k, 0, 0), fill});
            int m1 = Math.round(3 * k), m2 = Math.round(7 * k);
            bg.setLayerInset(1, m1, m1, m1, m1);
            bg.setLayerInset(2, m2, m2, m2, m2);
            panel.setBackground(bg);
            FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            plp.topMargin = plateH / 2;
            root.addView(panel, plp);
            d.panelView = panel;

            if (title != null) {
                TextView plate = new TextView(c);
                plate.setText(title);
                plate.setTextColor(p.plateText);
                plate.setTypeface(Typeface.DEFAULT_BOLD);
                plate.setTextSize(TypedValue.COMPLEX_UNIT_PX, 27 * k);
                plate.setSingleLine(true);
                plate.setGravity(Gravity.CENTER);
                plate.setPadding(Math.round(38 * k), 0, Math.round(38 * k), 0);
                GradientDrawable pg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[] {p.plateTop, p.plateBottom});
                pg.setCornerRadius(26 * k);
                pg.setStroke(Math.max(2, Math.round(3 * k)), p.plateLine);
                plate.setBackground(pg);
                FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, plateH, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
                root.addView(plate, tlp);
            }

            final LinearLayout body = new LinearLayout(c);
            body.setOrientation(LinearLayout.VERTICAL);
            body.setPadding(Math.round(3 * k), Math.round(3 * k), Math.round(3 * k), Math.round(3 * k));
            MaxScroll scroll = new MaxScroll(c, Math.round(dm.heightPixels * 0.62f));
            scroll.setClipToPadding(false);
            d.bodyView = body;
            scroll.addView(body, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            panel.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            if (message != null) {
                TextView m = new TextView(c);
                m.setText(message);
                m.setTextColor(p.ink);
                m.setTextSize(TypedValue.COMPLEX_UNIT_PX, 22 * k);
                m.setGravity(Gravity.RIGHT);
                m.setLineSpacing(0f, 1.2f);
                m.setPadding(Math.round(10 * k), Math.round(6 * k), Math.round(10 * k), Math.round(10 * k));
                body.addView(m);
                d.message = m;
            }
            if (view != null) {
                if (view.getParent() instanceof ViewGroup) ((ViewGroup) view.getParent()).removeView(view);
                body.addView(view, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }

            if (items != null) {
                final TextView[] ticks = new TextView[items.length];
                for (int i = 0; i < items.length; i++) {
                    final int which = i;
                    String text = String.valueOf(items[i]);
                    if (text.startsWith(HEADER)) {
                        TextView h = new TextView(c);
                        h.setText(text.substring(1));
                        h.setTextColor(p.accent);
                        h.setTypeface(Typeface.DEFAULT_BOLD);
                        h.setTextSize(TypedValue.COMPLEX_UNIT_PX, 20 * k);
                        h.setGravity(Gravity.CENTER);
                        h.setPadding(0, Math.round(10 * k), 0, Math.round(4 * k));
                        body.addView(h);
                        continue;
                    }
                    final LinearLayout row = new LinearLayout(c);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    row.setPadding(Math.round(20 * k), Math.round(11 * k), Math.round(20 * k), Math.round(11 * k));
                    StateListDrawable sl = new StateListDrawable();
                    sl.addState(new int[] {android.R.attr.state_focused},
                        shape(0x33FFFFFF & p.accent | 0x33000000, 16 * k, Math.max(3, Math.round(3 * k)), p.accent));
                    sl.addState(new int[] {}, shape(p.rowFill, 16 * k, Math.max(1, Math.round(1.5f * k)), p.rowLine));
                    row.setBackground(sl);
                    row.setFocusable(true);
                    row.setClickable(true);
                    TextView tick = new TextView(c);
                    tick.setTextColor(p.accent);
                    tick.setTextSize(TypedValue.COMPLEX_UNIT_PX, 26 * k);
                    tick.setTypeface(Typeface.DEFAULT_BOLD);
                    boolean on = kind == 3 ? checked[i] : kind == 2 && single == i;
                    tick.setText(on ? "✓" : "");
                    ticks[i] = tick;
                    row.addView(tick, new LinearLayout.LayoutParams(Math.round(34 * k),
                        ViewGroup.LayoutParams.WRAP_CONTENT));
                    TextView label = new TextView(c);
                    label.setText(text);
                    label.setTextColor(p.ink);
                    label.setTypeface(Typeface.DEFAULT_BOLD);
                    label.setTextSize(TypedValue.COMPLEX_UNIT_PX, 23 * k);
                    label.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
                    row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                    Drawable pic = icons != null && i < icons.size() ? icons.get(i) : null;
                    int ico = kind == 1 && pic == null ? iconFor(text) : 0;
                    if (pic != null) {
                        ImageView pv = new ImageView(c);
                        pv.setImageDrawable(pic);
                        LinearLayout.LayoutParams plp2 = new LinearLayout.LayoutParams(Math.round(52 * k), Math.round(52 * k));
                        plp2.leftMargin = Math.round(12 * k);
                        row.addView(pv, plp2);
                    }
                    if (ico != 0) {
                        ImageView iv = new ImageView(c);
                        iv.setImageResource(ico);
                        iv.setColorFilter(p.accent);
                        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(Math.round(30 * k), Math.round(30 * k));
                        ilp.leftMargin = Math.round(12 * k);
                        row.addView(iv, ilp);
                    }
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    lp.bottomMargin = Math.round(9 * k);
                    body.addView(row, lp);
                    row.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            if (kind == 1) {
                                Box before = Box.lastShown;
                                if (itemClick != null) itemClick.onClick(d, which);
                                Box child = Box.lastShown;
                                if (child != null && child != before && child != d) {
                                    child.parent = d;
                                    d.firstFocus = row;
                                }
                                d.dismiss();
                            } else if (kind == 2) {
                                for (int j = 0; j < ticks.length; j++) if (ticks[j] != null) ticks[j].setText(j == which ? "✓" : "");
                                single = which;
                                if (itemClick != null) itemClick.onClick(d, which);
                            } else {
                                checked[which] = !checked[which];
                                ticks[which].setText(checked[which] ? "✓" : "");
                                if (multiClick != null) multiClick.onClick(d, which, checked[which]);
                            }
                        }
                    });
                    if (d.firstFocus == null || (kind == 2 && single == i)) d.firstFocus = row;
                }
            }

            // Buttons: one row at the bottom (the close button is the common one).
            List<Object[]> buttons = new ArrayList<>();
            if (posText != null) buttons.add(new Object[] {posText, posClick, DialogInterface.BUTTON_POSITIVE});
            if (neuText != null) buttons.add(new Object[] {neuText, neuClick, DialogInterface.BUTTON_NEUTRAL});
            if (negText != null) buttons.add(new Object[] {negText, negClick, DialogInterface.BUTTON_NEGATIVE});
            if (!buttons.isEmpty()) {
                LinearLayout bar = new LinearLayout(c);
                bar.setOrientation(LinearLayout.HORIZONTAL);
                for (int i = 0; i < buttons.size(); i++) {
                    Object[] b = buttons.get(i);
                    final DialogInterface.OnClickListener l = (DialogInterface.OnClickListener) b[1];
                    final int id = (Integer) b[2];
                    TextView t = new TextView(c);
                    t.setText((CharSequence) b[0]);
                    t.setTextColor(p.ink);
                    t.setTypeface(Typeface.DEFAULT_BOLD);
                    t.setTextSize(TypedValue.COMPLEX_UNIT_PX, 21 * k);
                    t.setGravity(Gravity.CENTER);
                    t.setPadding(0, Math.round(11 * k), 0, Math.round(11 * k));
                    StateListDrawable sl = new StateListDrawable();
                    sl.addState(new int[] {android.R.attr.state_focused},
                        shape(0x33FFFFFF & p.accent | 0x33000000, 16 * k, Math.max(3, Math.round(3 * k)), p.accent));
                    sl.addState(new int[] {}, shape(p.closeFill, 16 * k, Math.max(1, Math.round(1.5f * k)), p.rowLine));
                    t.setBackground(sl);
                    t.setFocusable(true);
                    t.setClickable(true);
                    t.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            if (l != null) l.onClick(d, id);
                            d.dismiss();
                            if (id == DialogInterface.BUTTON_NEGATIVE) d.reopenParent();
                        }
                    });
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                    if (i > 0) lp.leftMargin = Math.round(10 * k);
                    bar.addView(t, lp);
                    if (d.firstFocus == null && id == DialogInterface.BUTTON_POSITIVE) d.firstFocus = t;
                    if (d.firstFocus == null) d.firstFocus = t;
                }
                LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                blp.topMargin = Math.round(10 * k);
                panel.addView(bar, blp);
            }
            d.setContentView(root);
            return d;
        }
    }
}
