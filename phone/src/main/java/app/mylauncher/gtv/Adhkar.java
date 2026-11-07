package app.mylauncher.gtv;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** The adhkar texts of the phone, the daily tasbih counters and the user's own adhkar. */
final class Adhkar {
    private Adhkar() {}

    /** A category of Hisn al-Muslim: its name and its items (the text and how many times to say it). */
    static final class Category {
        final int id;
        final String name;
        final String[] texts;
        final int[] counts;
        final String[] pre;
        final String[] post;
        boolean[] info;

        Category(int id, String name, String[] texts, int[] counts, String[] pre, String[] post) {
            this.pre = pre;
            this.post = post;
            this.id = id;
            this.name = name;
            this.texts = texts;
            this.counts = counts;
        }
    }

    // ------------------------------------------------------------ groups of chapters
    /** The groups the chapters are sorted into on the adhkar page (the user can move a chapter to another group). */
    static final String[] GROUPS = {
        "أذكار اليوم والليلة", "الصلاة والمسجد", "الطهارة والمنزل واللباس", "الطعام والصيام",
        "الهموم والكروب والحاجات", "التحصين والتوبة والوساوس", "الخوف والأعداء والسلطان", "المرض والموت والجنائز",
        "السفر والحج والعمرة", "الزواج والمولود والأسرة", "السلام والمجلس وآداب المعاملة", "الطقس والمطر والهلال",
        "التسبيح والاستغفار وجوامع الخير", "أبواب أخرى"
    };
    private static final int[][] GROUP_IDS = {
        {1, 133, 2, 3, 29, 30, 31},
        {10, 11, 12, 13, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 134, 28, 32, 33},
        {4, 5, 6, 7, 8, 9, 14, 15, 16, 17},
        {68, 69, 70, 71, 72, 73, 74, 75, 76, 127},
        {34, 35, 41, 43, 46, 106, 122, 123},
        {40, 42, 44, 45, 88, 92, 94, 110, 111, 125, 126, 128},
        {36, 37, 38, 39},
        {49, 50, 51, 52, 53, 54, 55, 56, 57, 58, 59, 60, 124},
        {95, 96, 97, 98, 99, 100, 101, 102, 103, 104, 105, 115, 116, 117, 118, 119, 120, 121},
        {47, 48, 77, 78, 79, 80, 81},
        {82, 83, 84, 85, 86, 87, 89, 90, 91, 93, 107, 108, 109, 112, 113, 114},
        {61, 62, 63, 64, 65, 66, 67},
        {129, 130, 131, 132},
    };

    /** The group a chapter was made for. */
    static int defaultGroup(int id) {
        for (int g = 0; g < GROUP_IDS.length; g++) {
            for (int x : GROUP_IDS[g]) if (x == id) return g;
        }
        return GROUPS.length - 1;
    }

    /** The group a chapter is in now: the one the user moved it to, or its own. */
    static int groupOf(Context c, int id) {
        int g = p(c).getInt("ag_" + id, -1);
        return g >= 0 && g < GROUPS.length ? g : defaultGroup(id);
    }

    /** Moves a chapter to a group; -1 puts it back in its own group. */
    static void setGroup(Context c, int id, int g) {
        if (g < 0 || g == defaultGroup(id)) p(c).edit().remove("ag_" + id).apply();
        else p(c).edit().putInt("ag_" + id, g).apply();
    }

    private static List<Category> cache;

    /**
     * All 132 categories of "Hisn al-Muslim" (the complete book), read once from the file inside the app.
     * Source: github.com/rn0x/Adhkar-json, text taken from Hisn al-Muslim by Sa'id bin Ali bin Wahf al-Qahtani.
     */
    static synchronized List<Category> all(Context c) {
        if (cache != null) return cache;
        List<Category> l = new ArrayList<>();
        try {
            java.io.InputStream in = c.getResources().openRawResource(R.raw.hisn_adhkar);
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            in.close();
            JSONArray a = new JSONArray(out.toString("UTF-8"));
            for (int i = 0; i < a.length(); i++) {
                org.json.JSONObject o = a.getJSONObject(i);
                JSONArray items = o.getJSONArray("items");
                String[] t = new String[items.length()];
                int[] k = new int[items.length()];
                String[] pr = new String[t.length];
                String[] po = new String[t.length];
                boolean[] infos = new boolean[t.length];
                for (int j = 0; j < t.length; j++) {
                    org.json.JSONObject it = items.getJSONObject(j);
                    t[j] = it.optString("text", "").trim();
                    k[j] = Math.max(1, it.optInt("count", 1));
                    pr[j] = it.optString("pre", "").trim();
                    if (it.optInt("info", 0) == 1) infos[j] = true;
                    po[j] = it.optString("post", "").trim();
                }
                Category cat = new Category(o.optInt("id", i + 1), o.optString("category", ""), t, k, pr, po);
                cat.info = infos;
                l.add(cat);
            }
        } catch (Exception ignored) {
            // an unreadable file shows an empty list
        }
        splitAfterPrayer(l);
        cache = l;
        return l;
    }

    /** The chapter of what is said after the prayer, as two: Fajr and Maghrib (with the ten times), and the others. */
    static final int AFTER_OTHER = 27;
    static final int AFTER_FAJR_MAGHRIB = 134;

    private static Category slice(Category c, int from, int to, int id, String name) {
        int n = to - from;
        String[] t = new String[n];
        int[] k = new int[n];
        String[] pr = new String[n];
        String[] po = new String[n];
        boolean[] inf = new boolean[n];
        for (int i = 0; i < n; i++) {
            t[i] = c.texts[from + i];
            k[i] = c.counts[from + i];
            pr[i] = c.pre[from + i];
            po[i] = c.post[from + i];
            inf[i] = c.info != null && c.info[from + i];
        }
        Category r = new Category(id, name, t, k, pr, po);
        r.info = inf;
        return r;
    }

    private static void splitAfterPrayer(List<Category> l) {
        for (int i = 0; i < l.size(); i++) {
            Category c = l.get(i);
            if (c.id != AFTER_OTHER || c.texts.length < 14) continue;
            // the supplication starts at "اللهم لا مانع لما أعطيت", without the tahlil before it
            for (int q = 0; q < c.texts.length; q++) {
                int m = c.texts[q].indexOf("مَانِعَ");
                int st = m > 0 ? c.texts[q].lastIndexOf("\u0627\u0644\u0644\u0651", m) : -1;
                if (st > 0) c.texts[q] = c.texts[q].substring(st);
            }
            // 0..11 are said after every prayer; 12 (ten times) is for Maghrib and Fajr; 13 is for Fajr only
            Category other = slice(c, 0, 12, AFTER_OTHER, "الأذكار بعد الظهر والعصر والعشاء");
            Category fm = slice(c, 0, 14, AFTER_FAJR_MAGHRIB, "الأذكار بعد الفجر والمغرب");
            // the three "Quls" (Ikhlas, Falaq, Nas) are said three times after Fajr and Maghrib, once after the others
            for (int q = 8; q <= 10; q++) fm.counts[q] = 3;
            fm.post[8] = "ثلاث مرات بعد صلاتي الفجر والمغرب";
            fm.post[10] = "ثلاث مرات بعد صلاتي الفجر والمغرب";
            l.set(i, other);
            l.add(i + 1, fm);
            return;
        }
    }

    /** Hadiths with a short explanation: {text, source, explanation}. */
    static final String[][] HADITHS = {
        {"إنما الأعمال بالنيات، وإنما لكل امرئ ما نوى", "متفق عليه",
            "قبول العمل وثوابه مرتبطان بنية صاحبه وقصده، فالعمل الواحد قد يكون عبادة أو عادة بحسب النية."},
        {"الدين النصيحة", "رواه مسلم",
            "النصيحة تشمل إخلاص العبادة لله، وحب الخير للناس وإرشادهم إليه، فهي أساس التعامل في الدين."},
        {"من كان يؤمن بالله واليوم الآخر فليقل خيرًا أو ليصمت", "متفق عليه",
            "حث على حفظ اللسان: إما كلمة طيبة نافعة، وإما سكوت يسلم صاحبه من الإثم."},
        {"لا يؤمن أحدكم حتى يحب لأخيه ما يحب لنفسه", "متفق عليه",
            "كمال الإيمان أن يتمنى المسلم لإخوانه من الخير ما يتمناه لنفسه."}};

    // ---------------------------------------------------------------- tasbih
    static final String[] TASBIH = {"سبحان الله", "الحمد لله", "الله أكبر", "أستغفر الله", "اللهم صل على محمد"};

    /** The tasbih list: the five built in, then the ones the user added (a deleted one stays as an empty name). */
    static String[] tasbih(Context c) {
        java.util.ArrayList<String> l = new java.util.ArrayList<>(java.util.Arrays.asList(TASBIH));
        try {
            org.json.JSONArray a = new org.json.JSONArray(p(c).getString("tas_extra", "[]"));
            for (int i = 0; i < a.length(); i++) l.add(a.getString(i));
        } catch (org.json.JSONException ignored) {
            // an unreadable list: only the built-in ones
        }
        return l.toArray(new String[0]);
    }

    static boolean tasbihAlive(Context c, int i) {
        String[] t = tasbih(c);
        return i >= 0 && i < t.length && !t[i].isEmpty();
    }

    static void addTasbih(Context c, String text) {
        try {
            org.json.JSONArray a = new org.json.JSONArray(p(c).getString("tas_extra", "[]"));
            a.put(text.trim());
            p(c).edit().putString("tas_extra", a.toString()).apply();
        } catch (org.json.JSONException ignored) {
            // nothing added
        }
    }

    /** Removes an added tasbih (the built-in five stay). Its place is kept so the counts of the others do not move. */
    static void removeTasbih(Context c, int i) {
        if (i < TASBIH.length) return;
        try {
            org.json.JSONArray a = new org.json.JSONArray(p(c).getString("tas_extra", "[]"));
            if (i - TASBIH.length < a.length()) a.put(i - TASBIH.length, "");
            p(c).edit().putString("tas_extra", a.toString()).apply();
        } catch (org.json.JSONException ignored) {
            // nothing removed
        }
    }

    /** The next tasbih after i that is still there. */
    static int nextTasbih(Context c, int i) {
        String[] t = tasbih(c);
        for (int k = 1; k <= t.length; k++) {
            int j = (i + k) % t.length;
            if (!t[j].isEmpty()) return j;
        }
        return i;
    }

    // ---------------------------------------------------------------- text size and colour (per place)
    /** The places with their own size and colour: 0 the adhkar chapters, 1 the tasbih page, 2 the card over other apps. */
    static final String[] TEXT_PLACES = {"الأذكار والأدعية", "السبحة والاستغفار", "الأذكار فوق التطبيقات"};
    static final String[] SIZE_NAMES = {"صغير", "عادي", "كبير", "كبير جدًا"};
    static final float[] SIZES = {0.9f, 1f, 1.2f, 1.4f};
    static final String[] COLOR_NAMES = {"الافتراضي", "أبيض", "ذهبي", "أصفر فاتح", "أخضر فاتح", "سماوي فاتح"};
    private static final int[] COLORS = {0, 0xFFFFFFFF, 0, 0xFFFFF0A0, 0xFFB8F0C0, 0xFFA8E8FF};

    static int sizeIdx(Context c, int place) {
        return Math.max(0, Math.min(SIZES.length - 1, p(c).getInt("txt_size_" + place, 1)));
    }

    static int colorIdx(Context c, int place) {
        return Math.max(0, Math.min(COLORS.length - 1, p(c).getInt("txt_col_" + place, 0)));
    }

    static float scale(Context c, int place) {
        return SIZES[sizeIdx(c, place)];
    }

    /** The colour chosen for a place; {@code def} when it is left on "default" and {@code gold} for "gold". */
    static int color(Context c, int place, int def, int gold) {
        int i = colorIdx(c, place);
        return i == 0 ? def : i == 2 ? gold : COLORS[i];
    }

    private static SharedPreferences p(Context c) {
        return c.getSharedPreferences("magiic", Context.MODE_PRIVATE);
    }

    private static String today() {
        return new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());
    }

    /** Today's total for one dhikr. */
    static int count(Context c, int i) {
        return p(c).getInt("tas_" + today() + "_" + i, 0);
    }

    /** The count of the current round (since the last reset). */
    static int cur(Context c, int i) {
        return p(c).getInt("tas_cur_" + i, 0);
    }

    /** The count the previous round ended on. */
    static int last(Context c, int i) {
        return p(c).getInt("tas_last_" + i, 0);
    }

    /** One count for everything (every dhikr and the tasbih), since the app was installed. */
    static long total(Context c) {
        return p(c).getLong("tot_all", 0L);
    }

    static void bump(Context c) {
        p(c).edit().putLong("tot_all", total(c) + 1L).apply();
    }

    static void add(Context c, int i) {
        bump(c);
        p(c).edit().putInt("tas_cur_" + i, cur(c, i) + 1).putInt("tas_" + today() + "_" + i, count(c, i) + 1).apply();
    }

    /** Ends the round: its count becomes "the last count" and a new round starts from 0. Today's total stays. */
    static void reset(Context c, int i) {
        int cur = cur(c, i);
        SharedPreferences.Editor e = p(c).edit().putInt("tas_cur_" + i, 0);
        if (cur > 0) e.putInt("tas_last_" + i, cur);
        e.apply();
    }

    // ---------------------------------------------------------------- the user's own adhkar
    static List<String> mine(Context c) {
        List<String> l = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(p(c).getString("mine", "[]"));
            for (int i = 0; i < a.length(); i++) l.add(a.getString(i));
        } catch (JSONException ignored) {
            // an unreadable list is shown empty
        }
        return l;
    }

    // ---------------------------------------------------------------- a chapter as the user arranged it
    /** One dhikr in a chapter, in the order the user chose; {@code key} is "i3" (the book's fourth) or "x7" (added by the user). */
    static final class Entry {
        String key = "";
        String text = "";
        String pre = "";
        String post = "";
        int count = 1;
        boolean info;
        boolean extra;
    }

    static List<Entry> entries(Context c, Category cat) {
        List<Entry> base = new ArrayList<>();
        for (int i = 0; i < cat.texts.length; i++) {
            Entry e = new Entry();
            e.key = "i" + i;
            e.text = cat.texts[i];
            e.pre = cat.pre[i];
            e.post = cat.post[i];
            e.count = cat.counts[i];
            e.info = cat.info != null && cat.info[i];
            base.add(e);
        }
        try {
            JSONArray x = new JSONArray(p(c).getString("cx_" + cat.id, "[]"));
            for (int i = 0; i < x.length(); i++) {
                org.json.JSONObject o = x.getJSONObject(i);
                Entry e = new Entry();
                e.key = o.getString("k");
                e.text = o.optString("t", "");
                e.count = Math.max(1, o.optInt("n", 1));
                e.extra = true;
                base.add(e);
            }
            JSONArray order = new JSONArray(p(c).getString("co_" + cat.id, "[]"));
            List<Entry> out = new ArrayList<>();
            for (int i = 0; i < order.length(); i++) {
                for (Entry e : base) {
                    if (e.key.equals(order.getString(i)) && !out.contains(e)) {
                        out.add(e);
                        break;
                    }
                }
            }
            for (Entry e : base) if (!out.contains(e)) out.add(e);
            return out;
        } catch (JSONException ignored) {
            return base;
        }
    }

    static void saveEntries(Context c, Category cat, List<Entry> list) {
        JSONArray order = new JSONArray();
        JSONArray x = new JSONArray();
        try {
            for (Entry e : list) {
                order.put(e.key);
                if (e.extra) x.put(new org.json.JSONObject().put("k", e.key).put("t", e.text).put("n", e.count));
            }
        } catch (JSONException ignored) {
            return;
        }
        p(c).edit().putString("co_" + cat.id, order.toString()).putString("cx_" + cat.id, x.toString()).apply();
    }

    static void resetEntries(Context c, Category cat) {
        p(c).edit().remove("co_" + cat.id).remove("cx_" + cat.id).apply();
    }

    // ---------------------------------------------------------------- what shows over other apps
    /** More short adhkar for the card over other apps, added once to the list: {text, "1" if on from the start}. */
    private static final String[][] OVERLAY_SEED = {
        {"أستغفر الله الذي لا إله إلا هو الحي القيوم وأتوب إليه", "0"},
        {"أستغفر الله العظيم", "0"},
        {"أستغفر الله العظيم وأتوب إليه", "0"},
        {"أستغفر الله، أستغفر الله، أستغفر الله", "0"},
        {"أشهد أن لا إله إلا الله وأن محمدًا عبده ورسوله", "0"},
        {"أشهد أن لا إله إلا الله وحده لا شريك له", "0"},
        {"أعوذ بكلمات الله التامات من شر ما خلق", "0"},
        {"أعوذ بالله من الشيطان من نفخه ونفثه وهمزه", "0"},
        {"اللهم أسلمت نفسي إليك وفوضت أمري إليك، لا ملجأ ولا منجا منك إلا إليك", "0"},
        {"اللهم أعني على ذكرك وشكرك وحسن عبادتك", "0"},
        {"اللهم أنت السلام ومنك السلام تباركت يا ذا الجلال والإكرام", "0"},
        {"اللهم أنت ربي لا إله إلا أنت، عليك توكلت وأنت رب العرش العظيم", "0"},
        {"اللهم أنت ربي لا إله إلا أنت، خلقتني وأنا عبدك", "0"},
        {"اللهم إني أسألك العفو والعافية في ديني ودنياي وأهلي ومالي", "0"},
        {"اللهم إني أسألك علمًا نافعًا ورزقًا طيبًا وعملًا متقبلًا", "0"},
        {"اللهم إني أعوذ بك من الكفر والفقر، وأعوذ بك من عذاب القبر", "0"},
        {"اللهم ارحمني", "1"},
        {"اللهم اعف عني", "1"},
        {"اللهم اغفر لنا ما أسررنا وما أعلنا", "1"},
        {"اللهم اغفر لي", "1"},
        {"اللهم إنك عفو كريم تحب العفو فاعف عني", "0"},
        {"اللهم بك أصبحنا وبك أمسينا وبك نحيا وبك نموت وإليك النشور", "0"},
        {"اللهم صل وسلم على نبينا محمد", "0"},
        {"اللهم صل وسلم وبارك على سيدنا محمد وعلى آله وصحبه أجمعين", "0"},
        {"اللهم عافني", "1"},
        {"اللهم قني عذابك يوم تبعث عبادك", "1"},
        {"اللهم لك الحمد كما ينبغي لجلال وجهك وعظيم سلطانك", "0"},
        {"اللهم ما أصبح بي من نعمة أو بأحد من خلقك، فمنك وحدك لا شريك لك", "0"},
        {"اللهم إني أسألك العفو والعافية في الدنيا والآخرة", "0"},
        {"اللهم إني أسألك الهدى والتقى والعفاف والغنى", "0"},
        {"بسم الله الذي لا يضر مع اسمه شيء في الأرض ولا في السماء وهو السميع العليم", "0"},
        {"حسبنا الله ونعم الوكيل", "1"},
        {"حسبي الله لا إله إلا هو عليه توكلت وهو رب العرش العظيم", "0"},
        {"رب إني مسني الضر وأنت أرحم الراحمين", "0"},
        {"رب اشرح لي صدري ويسر لي أمري", "1"},
        {"رب اغفر لي", "1"},
        {"رب اغفر لي وتب علي", "0"},
        {"رب اغفر وارحم وتجاوز عما تعلم", "0"},
        {"رب أنزلني منزلًا مباركًا وأنت خير المنزلين", "0"},
        {"ربنا آتنا في الدنيا حسنة وفي الآخرة حسنة وقنا عذاب النار", "0"},
        {"ربنا عليك توكلنا وإليك أنبنا وإليك المصير", "0"},
        {"رضيت بالله ربًا وبالإسلام دينًا وبمحمد صلى الله عليه وسلم نبيًا", "0"},
        {"سبحان الله", "0"},
        {"سبحان الله وبحمده", "1"},
        {"سبحان الله وبحمده عدد خلقه ورضا نفسه وزنة عرشه ومداد كلماته", "0"},
        {"سبحان الله وبحمده، سبحان الله العظيم", "1"},
        {"سبحان الله والحمد لله، والله أكبر — ثلاثًا وثلاثين", "0"},
        {"سبحان الله، والحمد لله، ولا إله إلا الله، والله أكبر", "0"},
        {"سبحانك اللهم إني ظلمت نفسي فاغفر لي، فإنه لا يغفر الذنوب إلا أنت", "0"},
        {"لا إله إلا أنت سبحانك إني كنت من الظالمين", "1"},
        {"لا إله إلا الله رب السماوات ورب الأرض ورب العرش العظيم", "0"},
        {"لا إله إلا الله وحده لا شريك له، له الملك وله الحمد وهو على كل شيء قدير", "0"},
        {"لا حول ولا قوة إلا بالله", "1"},
        {"ما شاء الله كان وما لم يشأ لم يكن، ولا حول ولا قوة إلا بالله العلي العظيم", "0"},
        {"هو الله الذي لا إله إلا هو عالم الغيب والشهادة هو الرحمن الرحيم", "0"},
        {"وقل رب اغفر وارحم وأنت خير الراحمين", "0"},
        {"ومن يتق الله يجعل له مخرجًا ويرزقه من حيث لا يحتسب", "0"},
        {"يا حي يا قيوم برحمتك أستغيث", "1"},
    };

    /** The text without marks, tatweel or punctuation and with the letter variants joined: two adhkar are equal if these are. */
    static String norm(String s) {
        StringBuilder b = new StringBuilder();
        boolean space = false;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if ((ch >= '\u064B' && ch <= '\u0652') || ch == '\u0670' || ch == '\u0640') continue;
            if (ch == '\u0623' || ch == '\u0625' || ch == '\u0622' || ch == '\u0671') ch = '\u0627';
            else if (ch == '\u0649') ch = '\u064A';
            else if (ch == '\u0629') ch = '\u0647';
            if (Character.isLetterOrDigit(ch)) {
                if (space && b.length() > 0) b.append(' ');
                space = false;
                b.append(ch);
            } else {
                space = true;
            }
        }
        return b.toString();
    }

    /** Adds the extra adhkar once (never twice, never a dhikr that is already there), and keeps what the user does after. */
    private static void seedOverlay(Context c, List<String[]> l) {
        if (p(c).getBoolean("ov_seed2", false)) return;
        java.util.HashSet<String> have = new java.util.HashSet<>();
        for (String[] s : l) have.add(norm(s[0]));
        for (String[] s : OVERLAY_SEED) {
            if (have.add(norm(s[0]))) l.add(new String[] {s[0], s[1]});
        }
        saveOverlay(c, l);
        p(c).edit().putBoolean("ov_seed2", true).apply();
    }

    /** The adhkar of the card over other apps: {text, "1" if on}. First time: the tasbih and the user's own adhkar. */
    static List<String[]> overlay(Context c) {
        List<String[]> l = overlayRaw(c);
        seedOverlay(c, l);
        return l;
    }

    private static List<String[]> overlayRaw(Context c) {
        List<String[]> l = new ArrayList<>();
        String saved = p(c).getString("ov_list", null);
        if (saved == null) {
            for (String s : TASBIH) l.add(new String[] {s, "1"});
            for (String s : mine(c)) l.add(new String[] {s, "1"});
            return l;
        }
        try {
            JSONArray a = new JSONArray(saved);
            for (int i = 0; i < a.length(); i++) {
                org.json.JSONObject o = a.getJSONObject(i);
                l.add(new String[] {o.optString("t", ""), o.optBoolean("on", true) ? "1" : "0"});
            }
        } catch (JSONException ignored) {
            // an unreadable list is shown empty
        }
        return l;
    }

    static void saveOverlay(Context c, List<String[]> l) {
        JSONArray a = new JSONArray();
        try {
            for (String[] s : l) a.put(new org.json.JSONObject().put("t", s[0]).put("on", s[1].equals("1")));
        } catch (JSONException ignored) {
            return;
        }
        p(c).edit().putString("ov_list", a.toString()).apply();
    }
}
