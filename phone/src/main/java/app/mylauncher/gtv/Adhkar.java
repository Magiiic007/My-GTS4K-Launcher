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

    /** A section: its key, its name and its items as {text, repeat}. */
    static final class Section {
        final String key;
        final String name;
        final String[][] items;

        Section(String key, String name, String[][] items) {
            this.key = key;
            this.name = name;
            this.items = items;
        }
    }

    static final Section[] SECTIONS = {
        new Section("am", "أذكار الصباح", new String[][] {
            {"أصبحنا وأصبح الملك لله، والحمد لله، لا إله إلا الله وحده لا شريك له، له الملك وله الحمد وهو على كل شيء قدير", "1"},
            {"بسم الله الذي لا يضر مع اسمه شيء في الأرض ولا في السماء وهو السميع العليم", "3"},
            {"سبحان الله وبحمده", "100"},
            {"أستغفر الله وأتوب إليه", "100"}}),
        new Section("pm", "أذكار المساء", new String[][] {
            {"أمسينا وأمسى الملك لله، والحمد لله، لا إله إلا الله وحده لا شريك له، له الملك وله الحمد وهو على كل شيء قدير", "1"},
            {"بسم الله الذي لا يضر مع اسمه شيء في الأرض ولا في السماء وهو السميع العليم", "3"},
            {"سبحان الله وبحمده", "100"},
            {"أستغفر الله وأتوب إليه", "100"}}),
        new Section("sleep", "أذكار النوم", new String[][] {
            {"باسمك اللهم أموت وأحيا", "1"},
            {"سبحان الله", "33"},
            {"الحمد لله", "33"},
            {"الله أكبر", "34"}}),
        new Section("wake", "أذكار الاستيقاظ", new String[][] {
            {"الحمد لله الذي أحيانا بعد ما أماتنا وإليه النشور", "1"}}),
        new Section("mosque", "أذكار المسجد", new String[][] {
            {"عند الدخول: اللهم افتح لي أبواب رحمتك", "1"},
            {"عند الخروج: اللهم إني أسألك من فضلك", "1"}}),
        new Section("duas", "أدعية مأثورة", new String[][] {
            {"ربنا آتنا في الدنيا حسنة وفي الآخرة حسنة وقنا عذاب النار", "1"},
            {"ربنا ظلمنا أنفسنا وإن لم تغفر لنا وترحمنا لنكونن من الخاسرين", "1"},
            {"اللهم إنك عفو تحب العفو فاعف عني", "1"},
            {"اللهم أعني على ذكرك وشكرك وحسن عبادتك", "1"}})
    };

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
