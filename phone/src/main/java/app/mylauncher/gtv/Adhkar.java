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

        Category(int id, String name, String[] texts, int[] counts) {
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
                JSONArray items = o.getJSONArray("array");
                String[] t = new String[items.length()];
                int[] k = new int[items.length()];
                for (int j = 0; j < t.length; j++) {
                    org.json.JSONObject it = items.getJSONObject(j);
                    t[j] = it.optString("text", "").trim();
                    k[j] = Math.max(1, it.optInt("count", 1));
                }
                l.add(new Category(o.optInt("id", i + 1), o.optString("category", ""), t, k));
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

    static int count(Context c, int i) {
        return p(c).getInt("tas_" + today() + "_" + i, 0);
    }

    static int add(Context c, int i) {
        int v = count(c, i) + 1;
        p(c).edit().putInt("tas_" + today() + "_" + i, v).apply();
        return v;
    }

    static void reset(Context c, int i) {
        p(c).edit().putInt("tas_" + today() + "_" + i, 0).apply();
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
}
