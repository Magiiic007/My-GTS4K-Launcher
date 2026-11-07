package app.mylauncher.gtv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.Collator;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The home screen, laid out on a fixed canvas that is scaled to the TV (see Stage) so it matches the design pictures:
 * the settings list (left), the prayer times (centre), the clock, dates and countdowns (right) and a row of apps
 * (bottom), with the weather, radio and status icons in the top corner. Everything can be selected with the remote.
 * Two looks, chosen in the settings list: royal (green and gold) and kids (pink).
 */
public class MainActivity extends Activity {

    /** One app in the row. */
    static final class AppItem {
        String label;
        ComponentName component;
        Drawable image;
        /** A wide TV banner (fills the tile) rather than a square icon (sits in the middle of it). */
        boolean banner;
    }

    /** Every app found, and the ones shown (without the hidden ones, in the user's order). */
    private final List<AppItem> allApps = new ArrayList<>();
    private final List<AppItem> apps = new ArrayList<>();
    private LinearLayout appRow;
    private TextView empty;
    private String focusPkg;

    private static final String PREFS = "magiic";
    private static final long WEATHER_EVERY_MS = 30L * 60L * 1000L;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView weatherView;
    private TextView radioBtn;
    /** The radio controls at the top of the settings list: previous, play or stop, next. */
    private TextView[] radioKeys;
    private TextView radioName;
    private ImageView radioDisc, radioBg;
    private android.animation.ObjectAnimator radioSpin;
    private int radioBgIdx = -1;
    private TextView permRow;
    private TextView noticeChip;
    private TextView noticesRow;
    private java.util.List<Health.Issue> lastIssues = new java.util.ArrayList<>();
    private Ambience ambience;
    private Sparkle sparkle;
    private FrameLayout rootView;
    private final Runnable ambienceTick = new Runnable() {
        @Override
        public void run() {
            ambience.update();
            // Automatic look: when day turns to night (or back) the screen switches by itself.
            if (Theme.id(MainActivity.this) != lookNow) {
                recreate();
                return;
            }
            handler.postDelayed(this, 60000L);
        }
    };
    private int lookNow;
    private LinearLayout settingsList;
    private boolean cityTried;
    private ImageView art;
    private long weatherAt;
    private PrayerBoard board;
    private StatusIcons status;
    private final RadioService.Listener radioListener = new RadioService.Listener() {
        @Override
        public void onRadio(int state) {
            updateRadioButton();
        }
    };
    private final Runnable weatherTick = new Runnable() {
        @Override
        public void run() {
            weatherAt = System.currentTimeMillis();
            loadWeather();
            handler.postDelayed(this, WEATHER_EVERY_MS);
        }
    };
    /** Run after any prayer or iqama setting changes: new alarm, new numbers on screen. */
    final Runnable prayerChanged = new Runnable() {
        @Override
        public void run() {
            Scheduler.scheduleNext(MainActivity.this);
            refreshBoard();
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        lookNow = Theme.id(this);
        final boolean kids = Theme.kids(this);
        Geo g = Geo.of(this);
        final FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Theme.edgeColour(this));
        art = new ImageView(this);
        art.setImageResource(kids ? R.drawable.bg_kids : R.drawable.bg_royal);
        art.setScaleType(ImageView.ScaleType.FIT_CENTER);
        root.addView(art, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        rootView = root;
        // The veil of the day's light sits on the picture, under everything else.
        View veil = new View(this);
        root.addView(veil, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        ambience = new Ambience(this, veil);

        final FrameLayout stage = new FrameLayout(this);
        stage.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        Motion.fadeIn(root, 350L);
        root.addView(stage, new FrameLayout.LayoutParams(Stage.W, Stage.H, Gravity.TOP | Gravity.LEFT));
        root.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
                Stage.fit(root, stage);
            }
        });

        // The twinkling stars come first so everything else is drawn over them.
        sparkle = new Sparkle(this, kids);
        Stage.add(stage, sparkle, 0, 0, Stage.W, Stage.H);

        // ---------------------------------------------------------------- top-left: weather, radio, status icons
        weatherView = chip(0, null);
        weatherView.setTextSize(TypedValue.COMPLEX_UNIT_PX, 15);
        weatherView.setMaxLines(2);
        weatherView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                chooseCountry();
            }
        });
        Stage.add(stage, weatherView, 44, 20, 168, 92);

        // A notice that only appears when something needs the person (see Health); hidden otherwise.
        noticeChip = chip(R.drawable.ic_warning, "");
        noticeChip.setTextSize(TypedValue.COMPLEX_UNIT_PX, 15);
        noticeChip.setMaxLines(1);
        noticeChip.setVisibility(View.GONE);
        Stage.add(stage, noticeChip, 1090, 22, 1336, 62);

        radioBtn = chip(R.drawable.ic_radio, null);
        radioBtn.setTextSize(TypedValue.COMPLEX_UNIT_PX, 14);
        radioBtn.setMaxLines(1);
        radioBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                chooseRadio();
            }
        });
        Stage.add(stage, radioBtn, 176, 20, 296, 54);

        status = new StatusIcons(this, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SettingsMenu.launch(MainActivity.this, Settings.ACTION_WIFI_SETTINGS);
            }
        });
        Stage.add(stage, status.view, 176, 58, 296, 92);
        updateRadioButton();
        // The header is for looking only: the arrows never land on it (its settings are in the list on the left).
        weatherView.setFocusable(false);
        radioBtn.setFocusable(false);
        status.view.setFocusable(false);
        noticeChip.setFocusable(false);

        // ---------------------------------------------------------------- the clock, prayers and countdowns
        board = new PrayerBoard(this, stage,
            new Runnable() {
                @Override
                public void run() {
                    chooseCountry();
                }
            },
            new Runnable() {
                @Override
                public void run() {
                    chooseHijri();
                }
            },
            new Runnable() {
                @Override
                public void run() {
                    SettingsMenu.launch(MainActivity.this, Settings.ACTION_DATE_SETTINGS);
                }
            },
            prayerChanged);

        // ---------------------------------------------------------------- the settings list
        Stage.add(stage, buildSettingsColumn(), g.settings[0], g.settings[1], g.settings[2], g.settings[3]);

        // ---------------------------------------------------------------- the apps row
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setClipChildren(false);
        // The apps run from the right, like the Arabic text: the first app is at the right edge.
        scroll.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        appRow = new LinearLayout(this);
        appRow.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        appRow.setOrientation(LinearLayout.HORIZONTAL);
        appRow.setGravity(Gravity.CENTER);
        scroll.addView(appRow, new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        Stage.add(stage, scroll, g.bar[0], g.bar[1], g.bar[2], g.bar[3]);

        empty = Stage.text(this, getString(R.string.no_apps), 24, Theme.cardText(this), true);
        empty.setGravity(Gravity.CENTER);
        empty.setVisibility(View.GONE);
        Stage.add(stage, empty, g.bar[0], g.bar[1], g.bar[2], g.bar[3]);
        addRadioBg(stage);

        setContentView(root);
        if (state == null) splash(root);
    }

    // ---------------------------------------------------------------- the opening screen
    private static final String[] SPLASH_WORDS = {"النوم", "الغفلة", "اللهو", "اللعب", "اللغو", "الكسل", "التأجيل", "المال", "التجارة", "العمل", "الدنيا وما فيها", "الراحة", "السهر"};
    private View splashView;
    private FrameLayout splashHost;
    private int splashStep;

    private int dp(int v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }

    /** The opening screen, as on the phone: the background, the icon in the middle, and "الصلاة خير من" with a word that changes. OK or Back skips it. */
    private void splash(FrameLayout host) {
        final FrameLayout v = new FrameLayout(this);
        v.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        // A calm, plain background of its own (a soft gradient and a thin gold frame), so nothing of the screen behind it shows.
        boolean kidsLook = Theme.kids(this);
        android.graphics.drawable.GradientDrawable calm = new android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
            kidsLook ? new int[] {0xFFFBD3DE, 0xFFEFA6BB} : new int[] {0xFF0D4220, 0xFF062711});
        calm.setStroke(dp(2), kidsLook ? 0x99FFF0CC : 0x99D9A93A);
        v.setBackground(calm);
        v.setClickable(true);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_launcher);
        col.addView(icon, new LinearLayout.LayoutParams(dp(150), dp(150)));
        final TextView phrase = new TextView(this);
        phrase.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
        phrase.setTypeface(Typeface.DEFAULT_BOLD);
        phrase.setGravity(Gravity.CENTER);
        phrase.setTextColor(0xFFFFFFFF);
        phrase.setShadowLayer(dp(2), 0, dp(1), 0x66000000);
        phrase.setText("الصلاة خير من");
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-2, -2);
        rp.topMargin = dp(20);
        col.addView(phrase, rp);
        final TextView word = new TextView(this);
        word.setTypeface(Typeface.DEFAULT_BOLD);
        word.setGravity(Gravity.CENTER);
        word.setMaxLines(1);
        word.setTextColor(0x00FFD54A);
        word.setShadowLayer(dp(2), 0, dp(1), 0x66000000);
        word.setTextSize(TypedValue.COMPLEX_UNIT_SP, 40);
        LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(-2, dp(64));
        wp.topMargin = dp(8);
        col.addView(word, wp);
        v.addView(col, new FrameLayout.LayoutParams(-1, -1));
        host.addView(v, new FrameLayout.LayoutParams(-1, -1));
        splashView = v;
        splashHost = host;
        splashStep = 0;
        final List<String> pool = new ArrayList<>(java.util.Arrays.asList(SPLASH_WORDS));
        Collections.shuffle(pool);
        final List<String> words = pool.subList(0, 3);
        final Runnable next = new Runnable() {
            @Override
            public void run() {
                if (splashView == null || isFinishing()) return;
                if (splashStep < words.size()) {
                    final String w = words.get(splashStep++);
                    android.animation.ValueAnimator an = android.animation.ValueAnimator.ofInt(0, 255);
                    an.setDuration(250);
                    an.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() {
                        @Override
                        public void onAnimationUpdate(android.animation.ValueAnimator x) {
                            word.setText(w);
                            word.setTextColor(((Integer) x.getAnimatedValue() << 24) | 0x00FFD54A);
                        }
                    });
                    an.start();
                    handler.postDelayed(this, 1500L);
                } else {
                    endSplash();
                }
            }
        };
        handler.postDelayed(next, 200L);
    }

    private void endSplash() {
        final View v = splashView;
        if (v == null) return;
        splashView = null;
        v.animate().alpha(0f).setDuration(300).withEndAction(new Runnable() {
            @Override
            public void run() {
                try {
                    splashHost.removeView(v);
                } catch (RuntimeException ignored) {
                    // already gone
                }
            }
        }).start();
    }

    // ---------------------------------------------------------------- the settings list (left)

    private View buildSettingsList() {
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        LinearLayout list = new LinearLayout(this);
        settingsList = list;
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list, new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Ordered by how often each is used: the most common first.
        // First while there is something that needs the person (see refreshNotices); hidden otherwise.
        noticesRow = addRow(list, R.drawable.ic_warning, R.string.menu_notices, new Runnable() {
            @Override
            public void run() {
                if (!lastIssues.isEmpty()) Health.show(MainActivity.this, lastIssues);
            }
        });
        ((View) noticesRow.getTag()).setVisibility(View.GONE);
        addRow(list, R.drawable.ic_bell, R.string.menu_adhan, new Runnable() {
            @Override
            public void run() {
                TimingDialogs.open(MainActivity.this, prayerChanged);
            }
        });
        addRow(list, R.drawable.ic_radio, R.string.menu_radio, new Runnable() {
            @Override
            public void run() {
                chooseRadio();
            }
        });
        addRow(list, R.drawable.ic_palette, R.string.menu_theme, new Runnable() {
            @Override
            public void run() {
                chooseLook();
            }
        });
        addRow(list, R.drawable.ic_star, R.string.menu_reminders, new Runnable() {
            @Override
            public void run() {
                ReminderDialogs.open(MainActivity.this);
            }
        });
        addRow(list, R.drawable.ic_account, R.string.menu_device, new Runnable() {
            @Override
            public void run() {
                SettingsMenu.device(MainActivity.this);
            }
        });
        addRow(list, R.drawable.ic_update, R.string.menu_update, new Runnable() {
            @Override
            public void run() {
                Updater.menu(MainActivity.this);
            }
        });
        permRow = addRow(list, R.drawable.ic_warning, R.string.perm_btn, new Runnable() {
            @Override
            public void run() {
                askOverlayPermission();
            }
        });
        return scroll;
    }

    /** The settings list with the radio keys fixed under it (they do not scroll with the list). */
    private View buildSettingsColumn() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(buildSettingsList(), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 104);
        lp.topMargin = 4;
        col.addView(buildRadioBar(), lp);
        return col;
    }

    /**
     * Remote movement of the settings list: up from the apps lands on the LAST setting (the nearest one to the
     * apps), and the list wraps round at both ends, so the far end is one press away.
     */
    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (splashView != null) {
            // The opening screen: OK or Back skips it; every key is swallowed so nothing behind it is pressed.
            if (e.getAction() == KeyEvent.ACTION_DOWN) {
                int k = e.getKeyCode();
                if (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER || k == KeyEvent.KEYCODE_BACK) endSplash();
            }
            return true;
        }
        if (e.getAction() == KeyEvent.ACTION_DOWN && settingsList != null && appRow != null) {
            int k = e.getKeyCode();
            View f = getCurrentFocus();
            if (f != null && (k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_DPAD_DOWN)) {
                View first = null, last = null;
                for (int i = 0; i < settingsList.getChildCount(); i++) {
                    View r = settingsList.getChildAt(i);
                    if (r.getVisibility() == View.VISIBLE && r.isFocusable()) {
                        if (first == null) first = r;
                        last = r;
                    }
                }
                if (first != null && last != null && first != last && k == KeyEvent.KEYCODE_DPAD_UP && f == first) {
                    last.requestFocus();
                    return true;
                }
            }
        }
        return super.dispatchKeyEvent(e);
    }

    /** Previous, play or stop, next: one row of three buttons, so the radio starts and stops at once. */
    private View buildRadioBar() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Theme.tile(this, 16));
        card.setPadding(4, 6, 4, 2);
        radioName = Stage.text(this, "", 16, Theme.tileLabel(this), true);
        radioName.setGravity(Gravity.CENTER);
        radioName.setMaxLines(1);
        Stage.fitOneLine(radioName, 10, 16);
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        radioDisc = new ImageView(this);
        radioDisc.setScaleType(ImageView.ScaleType.FIT_CENTER);
        top.addView(radioDisc, new LinearLayout.LayoutParams(36, 36));
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        nlp.leftMargin = 6;
        top.addView(radioName, nlp);
        card.addView(top, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 40));
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        card.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 46));
        radioKeys = new TextView[3];
        String[] glyph = {"\u25C0\u25C0", "\u25B6", "\u25B6\u25B6"};
        for (int i = 0; i < 3; i++) {
            final int which = i;
            final TextView b = Stage.text(this, glyph[i], 20, Theme.cardText(this), true);
            b.setGravity(Gravity.CENTER);
            b.setBackground(Theme.button(this, 22));
            b.setFocusable(true);
            b.setClickable(true);
            final Drawable ring = Theme.ring(this, 22);
            b.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean has) {
                    v.setForeground(has ? ring : null);
                    Motion.pop(v, has, 1.05f);
                }
            });
            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    radioKey(which);
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, which == 1 ? 1.4f : 1f);
            lp.leftMargin = 2;
            lp.rightMargin = 2;
            lp.bottomMargin = 5;
            bar.addView(b, lp);
            radioKeys[i] = b;
        }
        updateRadioButton();
        return card;
    }

    /** A big, faint picture of the station that is playing, behind everything (it never takes a click or the focus). */
    private void addRadioBg(FrameLayout stage) {
        if (true) { radioBg = null; return; } // no big faint picture: only the turning disc
        radioBg = new ImageView(this);
        radioBg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        radioBg.setAlpha(0f);
        radioBg.setFocusable(false);
        radioBgIdx = -1;
        Stage.add(stage, radioBg, 299, 0, 1067, 768);
        updateRadioButton();
    }

    /** 0 = previous station, 1 = play or stop, 2 = next station. */
    void radioKey(int which) {
        if (which == 1) {
            if (RadioService.on()) RadioService.stop(this);
            else RadioService.play(this, RadioService.lastStation(this));
        } else {
            int from = RadioService.on() ? RadioService.station() : RadioService.lastStation(this);
            RadioService.play(this, RadioStats.step(this, from, which == 2 ? 1 : -1));
        }
    }

    /** Redraws the prayer numbers of whichever look is on. */
    private void refreshBoard() {
        if (board != null) board.refresh();
    }

    private void refreshBoardDates() {
        if (board != null) board.refreshDates();
    }

    void openTiming() {
        TimingDialogs.open(this, prayerChanged);
    }

    /** The two looks, in a list; picking the other one restarts the screen in it. */
    private void chooseLook() {
        String[] names = {getString(R.string.theme_royal), getString(R.string.theme_kids), getString(R.string.theme_auto)};
        new Menu.Builder(this)
            .setTitle(R.string.menu_theme)
            .setSingleChoiceItems(names, Theme.mode(this), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int which) {
                    d.dismiss();
                    if (which != Theme.mode(MainActivity.this)) {
                        Theme.set(MainActivity.this, which);
                        // Fade out, switch, and the new look fades in.
                        rootView.animate().alpha(0f).setDuration(220L).withEndAction(new Runnable() {
                            @Override
                            public void run() {
                                recreate();
                            }
                        }).start();
                    }
                }
            })
            .setNegativeButton(R.string.close, null)
            .show();
    }

    /** One row of the settings list: the icon on the left, the words on the right, as in the design. */
    private TextView addRow(LinearLayout list, int icon, int text, final Runnable click) {
        final FrameLayout row = new FrameLayout(this);
        row.setBackground(Theme.button(this, 22));
        row.setFocusable(true);
        row.setClickable(true);
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                click.run();
            }
        });
        ImageView iv = new ImageView(this);
        iv.setImageResource(icon);
        iv.setColorFilter(Theme.kids(this) ? Color.parseColor("#FFFFF0CC") : Theme.cardGold(this));
        FrameLayout.LayoutParams ilp = new FrameLayout.LayoutParams(30, 30, Gravity.LEFT | Gravity.CENTER_VERTICAL);
        ilp.leftMargin = 14;
        row.addView(iv, ilp);
        final TextView label = Stage.text(this, getString(text), 18, Theme.cardText(this), true);
        label.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        label.setPadding(52, 0, 18, 0);
        Stage.fitOneLine(label, 11, 18);
        row.addView(label, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        final Drawable ring = Theme.ring(this, 22);
        row.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean has) {
                v.setForeground(has ? ring : null);
                Motion.pop(v, has, 1.03f);
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 46);
        lp.bottomMargin = 5;
        lp.leftMargin = 2;
        lp.rightMargin = 2;
        list.addView(row, lp);
        // The caller wants to hide a row (the permission one): hide the whole row, not just the words.
        label.setTag(row);
        return label;
    }

    /** A small selectable chip (weather, radio). */
    private TextView chip(int icon, String text) {
        TextView b = Stage.text(this, text == null ? "" : text, 15, Theme.cardText(this), true);
        b.setGravity(Gravity.CENTER);
        b.setPadding(10, 4, 10, 4);
        b.setBackground(Theme.button(this, 14));
        b.setFocusable(true);
        b.setClickable(true);
        if (icon != 0) {
            b.setCompoundDrawablePadding(6);
            b.setCompoundDrawablesRelativeWithIntrinsicBounds(
                tinted(icon, Theme.cardText(this), 20), null, null, null);
        }
        final Drawable ring = Theme.ring(this, 14);
        b.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean has) {
                v.setForeground(has ? ring : null);
                Motion.pop(v, has, 1.06f);
            }
        });
        return b;
    }

    private Drawable tinted(int res, int colour, int size) {
        Drawable d = getDrawable(res).mutate();
        d.setTint(colour);
        d.setBounds(0, 0, size, size);
        return d;
    }

    // ---------------------------------------------------------------- lifecycle

    @Override
    protected void onResume() {
        super.onResume();
        if (Theme.id(this) != lookNow) {
            recreate();
            return;
        }
        loadApps();
        RadioService.resumeIfCutOff(this);
        if (selectedCity() == null && !cityTried) {
            cityTried = true;
            autoCity(false);
        }
        // The weather is not fetched again each time the home screen comes back: only when it is 30 minutes old.
        handler.removeCallbacks(weatherTick);
        long age = System.currentTimeMillis() - weatherAt;
        handler.postDelayed(weatherTick, weatherAt == 0L || age >= WEATHER_EVERY_MS ? 0L : WEATHER_EVERY_MS - age);
        board.start();
        status.start();
        RadioService.listen(radioListener);
        updateRadioButton();
        ((View) permRow.getTag()).setVisibility(Settings.canDrawOverlays(this) ? View.GONE : View.VISIBLE);
        wireFocus();
        // Line up the alarms for the next prayer and the next reminder (also set again after a restart). Done off the
        // main thread so opening the home screen is never held up by it.
        final android.content.Context app = getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                Scheduler.scheduleNext(app);
                Reminders.scheduleNext(app);
            }
        }).start();
        ambience.update();
        handler.removeCallbacks(ambienceTick);
        handler.postDelayed(ambienceTick, 60000L);
        sparkle.start();
        refreshNotices();
        Updater.checkIfDue(this, new Runnable() {
            @Override
            public void run() {
                refreshNotices();
            }
        });
    }

    /** Shows the notice chip only when the quiet health check found something. */
    private void refreshNotices() {
        if (noticeChip == null) return;
        final java.util.List<Health.Issue> issues = Health.check(this, new Runnable() {
            @Override
            public void run() {
                askOverlayPermission();
            }
        }, new Runnable() {
            @Override
            public void run() {
                chooseCountry();
            }
        });
        lastIssues = issues;
        if (noticesRow != null) {
            ((View) noticesRow.getTag()).setVisibility(issues.isEmpty() ? View.GONE : View.VISIBLE);
        }
        if (issues.isEmpty()) {
            noticeChip.setVisibility(View.GONE);
            wireFocus();
            return;
        }
        boolean onlyUpdate = issues.size() == 1 && Updater.available(this);
        noticeChip.setText(onlyUpdate ? getString(R.string.menu_update) : getString(R.string.notice_chip, issues.size()));
        noticeChip.setVisibility(View.VISIBLE);
        wireFocus();
        noticeChip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Health.show(MainActivity.this, issues);
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (art != null && art.getDrawable() == null) {
            art.setImageResource(Theme.kids(this) ? R.drawable.bg_kids : R.drawable.bg_royal);
        }
    }

    @Override
    protected void onStop() {
        // While another app is in front, the big background picture (about 8 MB in memory) is let go.
        if (art != null) art.setImageDrawable(null);
        super.onStop();
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(weatherTick);
        handler.removeCallbacks(ambienceTick);
        sparkle.stop();
        board.stop();
        status.stop();
        RadioService.unlisten(radioListener);
        super.onPause();
    }

    // ---------------------------------------------------------------- radio

    private void updateRadioButton() {
        if (radioKeys != null && radioKeys[1] != null) radioKeys[1].setText(RadioService.on() ? "\u25A0" : "\u25B6");
        if (radioName != null) {
            int idx = RadioService.on() ? RadioService.station() : RadioService.lastStation(this);
            String nm = RadioService.STATIONS[idx].name(isArabic());
            int st0 = RadioService.state();
            radioName.setText(st0 == RadioService.PLAYING ? "\u25CF " + nm
                : st0 == RadioService.OFF ? nm : nm + " \u2026");
            radioName.setAlpha(st0 == RadioService.OFF ? 0.8f : 1f);
            if (radioDisc != null) {
                android.graphics.drawable.Drawable dd = RadioArt.discDrawable(this, idx, 96);
                radioDisc.setImageDrawable(dd);
                radioDisc.setVisibility(dd == null ? View.GONE : View.VISIBLE);
                if (radioSpin == null || radioSpin.getTarget() != radioDisc) {
                    radioSpin = android.animation.ObjectAnimator.ofFloat(radioDisc, "rotation", 0f, 360f);
                    radioSpin.setDuration(8000L);
                    radioSpin.setRepeatCount(android.animation.ValueAnimator.INFINITE);
                    radioSpin.setInterpolator(new android.view.animation.LinearInterpolator());
                }
                if (st0 == RadioService.PLAYING) {
                    if (!radioSpin.isStarted()) radioSpin.start();
                    else if (radioSpin.isPaused()) radioSpin.resume();
                } else if (radioSpin.isRunning()) {
                    radioSpin.pause();
                }
            }
            if (radioBg != null) {
                if (st0 == RadioService.PLAYING) {
                    if (radioBgIdx != idx) {
                        Bitmap pb = RadioArt.photo(this, idx, 512);
                        if (pb != null) radioBg.setImageBitmap(pb);
                        radioBgIdx = idx;
                    }
                    radioBg.animate().alpha(0.16f).setDuration(600L).start();
                } else {
                    radioBg.animate().alpha(0f).setDuration(400L).start();
                }
            }
        }
        if (radioBtn == null) return;
        int st = RadioService.state();
        if (st == RadioService.OFF) {
            radioBtn.setText(R.string.radio_btn);
            return;
        }
        String name = RadioService.STATIONS[RadioService.station()].shortName(isArabic());
        int label = st == RadioService.PLAYING ? R.string.radio_on
            : st == RadioService.PAUSED ? R.string.radio_paused : R.string.radio_connecting;
        radioBtn.setText(getString(label, name));
    }

    /** The two stations; the one playing is marked, and Stop appears while the radio is on. */
    void chooseRadio() {
        final boolean arabic = isArabic();
        final boolean on = RadioService.on();
        // Radio stations first, then the reciters, each under its own title.
        final List<String> items = new ArrayList<>();
        final List<Integer> station = new ArrayList<>();
        final List<android.graphics.drawable.Drawable> pics = new ArrayList<>();
        items.add(getString(on ? R.string.radio_stop : R.string.radio_play));
        station.add(-2);
        items.add(getString(R.string.radio_next));
        station.add(-3);
        items.add(getString(R.string.radio_prev));
        station.add(-4);
        items.add(getString(RadioService.playOnBoot(this) ? R.string.radio_boot_on : R.string.radio_boot_off));
        station.add(-5);
        for (int pass = 0; pass < 2; pass++) {
            boolean reciters = pass == 1;
            boolean titled = false;
            for (int i : RadioStats.order(this)) {
                if (RadioService.STATIONS[i].url.contains("qurango") != reciters) continue;
                if (!titled) {
                    items.add(Menu.HEADER + getString(reciters ? R.string.radio_group_reciters : R.string.radio_group_stations));
                    station.add(-1);
                    pics.add(null);
                    titled = true;
                }
                items.add((on && i == RadioService.station() ? "\u25CF " : "") + RadioService.STATIONS[i].name(arabic));
                station.add(i);
                pics.add(RadioArt.discDrawable(this, i, 96));
            }
        }
        pics.add(0, null);
        pics.add(0, null);
        pics.add(0, null);
        pics.add(0, null);
        new Menu.Builder(this)
            .setTitle(R.string.radio_title)
            .setIcons(pics)
            .setItems(items.toArray(new String[0]), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int which) {
                    int st = station.get(which);
                    if (st >= 0) RadioService.play(MainActivity.this, st);
                    else if (st == -2) radioKey(1);
                    else if (st == -3) radioKey(2);
                    else if (st == -4) radioKey(0);
                    else if (st == -5) {
                        boolean now = !RadioService.playOnBoot(MainActivity.this);
                        RadioService.setPlayOnBoot(MainActivity.this, now);
                        Toast.makeText(MainActivity.this, now ? R.string.radio_boot_now_on : R.string.radio_boot_now_off,
                            Toast.LENGTH_SHORT).show();
                    }
                }
            })
            .setNegativeButton(R.string.close, null)
            .show();
    }

    // ---------------------------------------------------------------- permissions, Hijri

    /** The system screen for "display over other apps" if this TV has one; otherwise instructions. */
    private void askOverlayPermission() {
        try {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                android.net.Uri.parse("package:" + getPackageName())));
        } catch (ActivityNotFoundException | SecurityException e) {
            new Menu.Builder(this)
                .setTitle(R.string.overlay_title)
                .setMessage(R.string.overlay_help)
                .setPositiveButton(android.R.string.ok, null)
                .show();
        }
    }

    /** Moves the Hijri date by up to three days either way, for when the local moon sighting differs. */
    void chooseHijri() {
        NumberDialog.show(this, getString(R.string.hijri_title), -3, 3, Hijri.adjust(this),
            getString(R.string.days_plain), getString(R.string.days_signed), true, getString(R.string.no_adjust),
            new NumberDialog.OnSave() {
                @Override
                public void saved(int value) {
                    Hijri.setAdjust(MainActivity.this, value);
                    refreshBoardDates();
                    // The White Days follow the Hijri date.
                    Reminders.scheduleNext(MainActivity.this);
                }
            });
    }

    // ---------------------------------------------------------------- city and weather

    private SharedPreferences prefs() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    private boolean isArabic() {
        return "ar".equals(Locale.getDefault().getLanguage());
    }

    /** The city chosen on this device, or null the first time. */
    private Cities.City selectedCity() {
        return Cities.find(prefs().getString("city", null));
    }

    /** Step 1 of choosing a city: the country. */
    void chooseCountry() {
        String[] countries = {getString(R.string.city_auto), getString(R.string.egypt), getString(R.string.saudi)};
        new Menu.Builder(this)
            .setTitle(R.string.choose_country)
            .setItems(countries, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    if (which == 0) autoCity(true);
                    else chooseCity(which == 1 ? Cities.EGYPT : Cities.SAUDI);
                }
            })
            .setNegativeButton(R.string.close, null)
            .show();
    }

    /** Picks the nearest city from the TV's internet address; says so, or says why not. */
    private void autoCity(final boolean tell) {
        if (tell) Toast.makeText(this, R.string.city_auto_wait, Toast.LENGTH_SHORT).show();
        AutoCity.find(new AutoCity.Callback() {
            @Override
            public void found(final Cities.City city) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (city == null) {
                            if (tell) Toast.makeText(MainActivity.this, R.string.city_auto_none, Toast.LENGTH_LONG).show();
                            return;
                        }
                        applyCity(city);
                        Toast.makeText(MainActivity.this, getString(R.string.city_auto_done, city.name(isArabic())),
                            Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    private void applyCity(Cities.City city) {
        prefs().edit().putString("city", city.key()).apply();
        loadWeather();
        Scheduler.scheduleNext(MainActivity.this);
        refreshBoard();
        refreshBoardDates();
    }

    /** Step 2: the city, listed alphabetically in the TV's language. */
    private void chooseCity(Cities.City[] all) {
        final boolean arabic = isArabic();
        final List<Cities.City> sorted = new ArrayList<>();
        Collections.addAll(sorted, all);
        final Collator collator = Collator.getInstance();
        Collections.sort(sorted, new Comparator<Cities.City>() {
            @Override
            public int compare(Cities.City a, Cities.City b) {
                return collator.compare(a.name(arabic), b.name(arabic));
            }
        });
        String[] names = new String[sorted.size()];
        for (int i = 0; i < names.length; i++) names[i] = sorted.get(i).name(arabic);
        new Menu.Builder(this)
            .setTitle(R.string.choose_city)
            .setItems(names, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    applyCity(sorted.get(which));
                }
            })
            .show();
    }

    /** Shows the last saved weather at once, then asks for a fresh one. */
    private void loadWeather() {
        final Cities.City city = selectedCity();
        if (city == null) {
            weatherView.setText(R.string.weather_pick);
            return;
        }
        SharedPreferences p = prefs();
        if (city.key().equals(p.getString("w_key", null))) {
            showWeather(city, p.getInt("w_temp", 0), p.getInt("w_code", 0));
        } else {
            weatherView.setText(city.name(isArabic()));
        }
        Weather.fetch(city, new Weather.Callback() {
            @Override
            public void onResult(int tempC, int code) {
                prefs().edit().putString("w_key", city.key()).putInt("w_temp", tempC).putInt("w_code", code).apply();
                // The city may have been changed while this was loading; show it only if it is still the one.
                Cities.City now = selectedCity();
                if (now != null && now.key().equals(city.key())) showWeather(city, tempC, code);
            }

            @Override
            public void onError() {
                // Keep what is on screen; if nothing was shown yet, say so.
                if (!city.key().equals(prefs().getString("w_key", null))) {
                    weatherView.setText(city.name(isArabic()) + "\n" + getString(R.string.weather_unavailable));
                }
            }
        });
    }

    private void showWeather(Cities.City city, int tempC, int code) {
        String degrees = NumberFormat.getIntegerInstance(Locale.US).format(tempC) + "°";
        weatherView.setText(degrees + "  " + conditionText(code) + "\n" + city.name(isArabic()));
    }

    /** WMO weather code to words. */
    private String conditionText(int code) {
        int res;
        if (code == 0) res = R.string.w_clear;
        else if (code <= 2) res = R.string.w_partly;
        else if (code == 3) res = R.string.w_cloudy;
        else if (code == 45 || code == 48) res = R.string.w_fog;
        else if (code >= 51 && code <= 57) res = R.string.w_drizzle;
        else if ((code >= 61 && code <= 67)) res = R.string.w_rain;
        else if ((code >= 71 && code <= 77) || code == 85 || code == 86) res = R.string.w_snow;
        else if (code >= 80 && code <= 82) res = R.string.w_showers;
        else if (code >= 95) res = R.string.w_storm;
        else res = R.string.w_cloudy;
        return getString(res);
    }

    // ---------------------------------------------------------------- the apps

    /** Reads the installed TV apps (or, on a device with none, every launchable app). */
    private void loadApps() {
        PackageManager pm = getPackageManager();
        List<ResolveInfo> found = query(pm, Intent.CATEGORY_LEANBACK_LAUNCHER);
        if (found.isEmpty()) found = query(pm, Intent.CATEGORY_LAUNCHER);

        List<AppItem> list = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ResolveInfo ri : found) {
            if (ri.activityInfo == null) continue;
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(getPackageName()) || !seen.add(pkg)) continue;
            AppItem item = new AppItem();
            item.label = String.valueOf(ri.loadLabel(pm));
            item.component = new ComponentName(pkg, ri.activityInfo.name);
            Drawable image = ri.activityInfo.loadBanner(pm);
            item.banner = image != null;
            item.image = image != null ? image : ri.loadIcon(pm);
            list.add(item);
        }
        final Collator collator = Collator.getInstance();
        Collections.sort(list, new Comparator<AppItem>() {
            @Override
            public int compare(AppItem a, AppItem b) {
                return collator.compare(a.label, b.label);
            }
        });
        allApps.clear();
        allApps.addAll(list);
        applyHidden();
    }

    /** The apps to show: all but the hidden ones, in the user's order (then alphabetical), and the row is redrawn. */
    private void applyHidden() {
        Set<String> hidden = SettingsMenu.hidden(this);
        final List<String> order = SettingsMenu.order(this);
        apps.clear();
        for (AppItem a : allApps) {
            if (!hidden.contains(a.component.getPackageName())) apps.add(a);
        }
        // A stable sort: apps with a saved place go first in that order; the others keep their alphabetical order.
        Collections.sort(apps, new Comparator<AppItem>() {
            @Override
            public int compare(AppItem a, AppItem b) {
                int ia = order.indexOf(a.component.getPackageName());
                int ib = order.indexOf(b.component.getPackageName());
                if (ia < 0) ia = Integer.MAX_VALUE;
                if (ib < 0) ib = Integer.MAX_VALUE;
                return ia < ib ? -1 : ia > ib ? 1 : 0;
            }
        });
        rebuildRow();
        empty.setVisibility(apps.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void rebuildRow() {
        appRow.removeAllViews();
        View toFocus = null;
        for (int i = 0; i < apps.size(); i++) {
            AppItem item = apps.get(i);
            View tile = buildTile(item);
            appRow.addView(tile);
            if (item.component.getPackageName().equals(focusPkg) || (toFocus == null && focusPkg == null && i == 0)) {
                toFocus = tile;
            }
        }
        appRow.addView(buildAllTile());
        focusPkg = null;
        if (toFocus != null && getCurrentFocus() == null) toFocus.requestFocus();
        else if (toFocus != null && appRow.hasFocus()) toFocus.requestFocus();
        wireFocus();
    }

    private static int idOf(View v) {
        if (v.getId() == View.NO_ID) v.setId(View.generateViewId());
        return v.getId();
    }

    /**
     * Fixes where each remote arrow goes, so it is always predictable. Only two groups can be selected: the settings
     * list on the left and the apps row at the bottom. Up and down move between them; left and right never jump.
     */
    private void wireFocus() {
        if (settingsList == null || appRow == null || weatherView == null || status == null || radioBtn == null) return;
        java.util.ArrayList<View> rows = new java.util.ArrayList<>();
        for (int i = 0; i < settingsList.getChildCount(); i++) {
            View r = settingsList.getChildAt(i);
            if (r.getVisibility() == View.VISIBLE && r.isFocusable()) rows.add(r);
        }
        java.util.ArrayList<View> tiles = new java.util.ArrayList<>();
        for (int i = 0; i < appRow.getChildCount(); i++) {
            if (appRow.getChildAt(i).isFocusable()) tiles.add(appRow.getChildAt(i));
        }
        View firstRow = rows.isEmpty() ? null : rows.get(0);
        View lastRow = rows.isEmpty() ? null : rows.get(rows.size() - 1);
        View firstTile = tiles.isEmpty() ? null : tiles.get(0);
        View lastTile = tiles.isEmpty() ? null : tiles.get(tiles.size() - 1);

        if (radioKeys != null) {
            // The radio keys (fixed under the list): left and right between the three, up to the last setting,
            // down to the apps.
            View up = lastRow != null ? lastRow : radioKeys[1];
            View down = firstTile != null ? firstTile : radioKeys[1];
            link(radioKeys[0], radioKeys[0], radioKeys[1], up, down);
            link(radioKeys[1], radioKeys[0], radioKeys[2], up, down);
            link(radioKeys[2], radioKeys[1], radioKeys[2], up, down);
        }
        for (View r : rows) {
            // Left, right: stay. Up, down: the neighbouring row; the first row stays, the last goes to the apps.
            link(r, r, r, r == firstRow ? r : null, r == lastRow ? (radioKeys != null ? radioKeys[1] : firstTile != null ? firstTile : r) : null);
        }
        for (View t : tiles) {
            link(t, t == lastTile ? t : null, t == firstTile ? t : null, radioKeys != null ? radioKeys[1] : firstRow != null ? firstRow : t, t);
        }
    }

    /** Sets where left, right, up and down go from {@code v}; a null direction is left to the normal neighbour. */
    private static void link(View v, View left, View right, View up, View down) {
        if (left != null) v.setNextFocusLeftId(idOf(left));
        if (right != null) v.setNextFocusRightId(idOf(right));
        if (up != null) v.setNextFocusUpId(idOf(up));
        if (down != null) v.setNextFocusDownId(idOf(down));
    }

    /** The last tile of the row: every app in one grid. */
    private View buildAllTile() {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER_HORIZONTAL);
        tile.setPadding(6, 6, 6, 4);
        tile.setBackground(Theme.tile(this, 16));
        tile.setFocusable(true);
        tile.setClickable(true);
        TextView glyph = Stage.text(this, "\u25A6", 64, Theme.tileLabel(this), true);
        glyph.setGravity(Gravity.CENTER);
        tile.addView(glyph, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        TextView label = Stage.text(this, getString(R.string.all_apps), 17, Theme.tileLabel(this), true);
        label.setGravity(Gravity.CENTER);
        Stage.fitOneLine(label, 10, 17);
        LinearLayout.LayoutParams lLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 26);
        lLp.topMargin = 3;
        tile.addView(label, lLp);
        final Drawable ring = Theme.ring(this, 16);
        tile.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean has) {
                v.setForeground(has ? ring : null);
                v.animate().scaleX(has ? 1.06f : 1f).scaleY(has ? 1.06f : 1f).setDuration(120).start();
            }
        });
        tile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openAllApps();
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(160, ViewGroup.LayoutParams.MATCH_PARENT);
        lp.setMargins(12, 6, 12, 6);
        tile.setLayoutParams(lp);
        return tile;
    }

    void openAllApps() {
        List<AllApps.Entry> entries = new ArrayList<>();
        for (AppItem a : allApps) {
            AllApps.Entry e = new AllApps.Entry();
            e.label = a.label;
            e.component = a.component;
            e.image = a.image;
            e.banner = a.banner;
            entries.add(e);
        }
        AllApps.show(this, entries, new Runnable() {
            @Override
            public void run() {
                applyHidden();
            }
        }, new AllApps.Opener() {
            @Override
            public void open(ComponentName component) {
                for (AppItem a : allApps) {
                    if (a.component.equals(component)) {
                        MainActivity.this.open(a);
                        return;
                    }
                }
            }
        });
    }

    private View buildTile(final AppItem item) {
        final boolean kids = Theme.kids(this);
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER_HORIZONTAL);
        tile.setPadding(6, 6, 6, 4);
        tile.setBackground(Theme.tile(this, 16));
        tile.setTag(item.component.getPackageName());
        tile.setFocusable(true);
        tile.setClickable(true);
        tile.setLongClickable(true);

        ImageView image = new ImageView(this);
        image.setClipToOutline(true);
        image.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), 10);
            }
        });
        if (item.banner) {
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        } else {
            // A square icon sits in the middle on a lighter backing.
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            image.setPadding(10, 6, 10, 6);
            image.setBackgroundColor(kids ? Color.WHITE : Color.parseColor("#33FFFFFF"));
        }
        image.setImageDrawable(item.image);
        tile.addView(image, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView label = Stage.text(this, item.label, 17, Theme.tileLabel(this), true);
        label.setGravity(Gravity.CENTER);
        Stage.fitOneLine(label, 10, 17);
        LinearLayout.LayoutParams lLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 26);
        lLp.topMargin = 3;
        tile.addView(label, lLp);

        final Drawable ring = Theme.ring(this, 16);
        tile.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean has) {
                v.setForeground(has ? ring : null);
                v.animate().scaleX(has ? 1.06f : 1f).scaleY(has ? 1.06f : 1f).setDuration(120).start();
            }
        });
        tile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                open(item);
            }
        });
        tile.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                appActions(item);
                return true;
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(160, ViewGroup.LayoutParams.MATCH_PARENT);
        lp.setMargins(12, 6, 12, 6);
        tile.setLayoutParams(lp);
        return tile;
    }


    /** Hold OK on an app: move it, take it off the screen, uninstall it or see its info. */
    private void appActions(final AppItem item) {
        final String pkg = item.component.getPackageName();
        String[] items = {
            getString(R.string.app_earlier), getString(R.string.app_later), getString(R.string.app_first),
            getString(R.string.app_hide), getString(R.string.app_uninstall), getString(R.string.app_info),
        };
        new Menu.Builder(this)
            .setTitle(item.label)
            .setItems(items, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int which) {
                    if (which <= 2) {
                        move(pkg, which);
                        focusPkg = pkg;
                        applyHidden();
                        // Open again, so the app can be moved several places in a row.
                        appActions(item);
                    } else if (which == 3) {
                        Set<String> hidden = SettingsMenu.hidden(MainActivity.this);
                        hidden.add(pkg);
                        SettingsMenu.setHidden(MainActivity.this, hidden);
                        applyHidden();
                        Toast.makeText(MainActivity.this, R.string.app_hidden_tip, Toast.LENGTH_LONG).show();
                    } else if (which == 4) {
                        SettingsMenu.uninstall(MainActivity.this, pkg);
                    } else {
                        SettingsMenu.appInfo(MainActivity.this, pkg);
                    }
                }
            })
            .setNegativeButton(R.string.close, null)
            .show();
    }

    /** Saves a new order: 0 = one place earlier, 1 = one place later, 2 = to the first place. */
    private void move(String pkg, int how) {
        List<String> order = new ArrayList<>();
        for (AppItem a : apps) order.add(a.component.getPackageName());
        int i = order.indexOf(pkg);
        if (i < 0) return;
        if (how == 0 && i > 0) {
            Collections.swap(order, i, i - 1);
        } else if (how == 1 && i < order.size() - 1) {
            Collections.swap(order, i, i + 1);
        } else if (how == 2 && i > 0) {
            order.remove(i);
            order.add(0, pkg);
        }
        SettingsMenu.setOrder(this, order);
    }

    private static List<ResolveInfo> query(PackageManager pm, String category) {
        Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(category);
        return pm.queryIntentActivities(intent, 0);
    }

    void open(AppItem item) {
        Intent intent = new Intent(Intent.ACTION_MAIN)
            .setComponent(item.component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.open_failed, Toast.LENGTH_SHORT).show();
        }
    }

}
