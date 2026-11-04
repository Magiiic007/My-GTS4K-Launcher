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
        cache = l;
        return l;
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

    static void add(Context c, int i) {
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

    static void saveMine(Context c, List<String> l) {
        JSONArray a = new JSONArray();
        for (String s : l) a.put(s);
        p(c).edit().putString("mine", a.toString()).apply();
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
    /** The adhkar of the card over other apps: {text, "1" if on}. First time: the tasbih and the user's own adhkar. */
    static List<String[]> overlay(Context c) {
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
