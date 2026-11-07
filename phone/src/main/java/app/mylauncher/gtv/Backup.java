package app.mylauncher.gtv;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Map;
import java.util.Set;

/**
 * Saves everything the user set (city, adhan and iqama settings, reminders, the adhkar added or edited, the list over
 * other apps, counters, the look...) into one small text file, and brings it back from such a file. The file is
 * written wherever the user chooses, so it survives uninstalling the app.
 */
final class Backup {
    private Backup() {}

    private static final String APP = "app.magiic.phone";

    /** Things that only describe the moment (diagnostics, the silent window in progress, one-time warning marks). */
    private static boolean skip(String k) {
        return k.startsWith("dbg_") || k.startsWith("snd_") || k.equals("radio_log") || k.startsWith("sil_") || k.startsWith("warn_at_")
            || k.equals("radio_resume") || k.startsWith("tv_") || k.startsWith("az_done_")
            || k.startsWith("wc_") || k.equals("qm_next") || k.startsWith("qm_at_");
    }

    /** What can be saved or left out of a backup, one tick each. */
    static final String[] GROUPS = {
        "المدينة وطريقة حساب المواقيت", "الأذان والإقامة والتنبيهات والصامت", "التذكيرات",
        "الأذكار والسبحة والعدّادات", "الأذكار فوق التطبيقات", "متابعة وقضاء الصلوات", "الراديو", "الشكل وباقي الإعدادات"};

    /** Which of the groups a setting belongs to (the last one takes everything else). */
    static int group(String k) {
        if (k.equals("city") || k.equals("other_cities") || k.startsWith("co_") || k.startsWith("calc_") || k.startsWith("adj_")) return 0;
        if (k.startsWith("ad_") || k.startsWith("iqama_") || k.equals("first_adhan") || k.startsWith("warn_") || k.equals("note_on")
            || k.startsWith("silent_") || k.startsWith("sil_") || k.startsWith("pp_") || k.startsWith("qm_")) return 1;
        if (k.equals("reminders")) return 2;
        if (k.startsWith("ag_") || k.equals("mine") || k.startsWith("cx_") || k.startsWith("tas_") || k.equals("tot_all") || k.equals("w_tk")) return 3;
        if (k.startsWith("ov_") || k.startsWith("az_")) return 4;
        if (k.startsWith("trk_") || k.startsWith("qada_") || k.startsWith("qdone_") || k.startsWith("qmiss_")) return 5;
        if (k.startsWith("radio_")) return 6;
        return 7;
    }

    static String export(Context c) throws JSONException {
        boolean[] all = new boolean[GROUPS.length];
        java.util.Arrays.fill(all, true);
        return export(c, all);
    }

    static String export(Context c, boolean[] on) throws JSONException {
        SharedPreferences sp = c.getSharedPreferences("magiic", Context.MODE_PRIVATE);
        JSONObject out = new JSONObject();
        out.put("app", APP).put("v", 1).put("time", System.currentTimeMillis());
        JSONArray gs = new JSONArray();
        for (int i = 0; i < on.length; i++) if (on[i]) gs.put(i);
        out.put("groups", gs);
        JSONObject p = new JSONObject();
        for (Map.Entry<String, ?> e : sp.getAll().entrySet()) {
            String k = e.getKey();
            Object v = e.getValue();
            if (skip(k) || v == null || !on[group(k)]) continue;
            JSONObject o = new JSONObject();
            if (v instanceof Boolean) o.put("t", "b").put("v", ((Boolean) v).booleanValue());
            else if (v instanceof Integer) o.put("t", "i").put("v", ((Integer) v).intValue());
            else if (v instanceof Long) o.put("t", "l").put("v", ((Long) v).longValue());
            else if (v instanceof Float) o.put("t", "f").put("v", ((Float) v).doubleValue());
            else if (v instanceof String) o.put("t", "s").put("v", (String) v);
            else if (v instanceof Set) {
                JSONArray a = new JSONArray();
                for (Object x : (Set<?>) v) a.put(String.valueOf(x));
                o.put("t", "set").put("v", a);
            } else continue;
            p.put(k, o);
        }
        out.put("prefs", p);
        return out.toString(1);
    }

    /**
     * First start of a fresh install: the settings I chose as the starting point (assets/default_settings.json) are
     * applied once. Nothing is touched when the app already has any settings of its own.
     */
    static void applyDefaults(Context c) {
        try {
            SharedPreferences sp = c.getSharedPreferences("magiic", Context.MODE_PRIVATE);
            if (!sp.getAll().isEmpty()) return;
            java.io.InputStream in = c.getAssets().open("default_settings.json");
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int r;
            while ((r = in.read(buf)) > 0) bo.write(buf, 0, r);
            in.close();
            restore(c, bo.toString("UTF-8"));
        } catch (java.io.IOException | RuntimeException ignored) {
            // no defaults file: the app starts with its built-in settings
        }
    }

    /** Puts a saved file back. Returns how many settings were restored, or -1 if the file is not one of ours. */
    static int restore(Context c, String json) {
        try {
            JSONObject root = new JSONObject(json);
            if (!APP.equals(root.optString("app"))) return -1;
            JSONObject p = root.getJSONObject("prefs");
            SharedPreferences sp = c.getSharedPreferences("magiic", Context.MODE_PRIVATE);
            SharedPreferences.Editor ed = sp.edit();
            // a file that was saved with only some groups replaces only those groups; the rest stays as it is
            boolean[] had = new boolean[GROUPS.length];
            JSONArray gs = root.optJSONArray("groups");
            if (gs == null) java.util.Arrays.fill(had, true);
            else for (int i = 0; i < gs.length(); i++) if (gs.getInt(i) >= 0 && gs.getInt(i) < had.length) had[gs.getInt(i)] = true;
            for (String k : sp.getAll().keySet()) if (!skip(k) && had[group(k)]) ed.remove(k);
            int n = 0;
            java.util.Iterator<String> it = p.keys();
            while (it.hasNext()) {
                String k = it.next();
                if (skip(k) || !had[group(k)]) continue;
                JSONObject o = p.getJSONObject(k);
                String t = o.getString("t");
                if (t.equals("b")) ed.putBoolean(k, o.getBoolean("v"));
                else if (t.equals("i")) ed.putInt(k, o.getInt("v"));
                else if (t.equals("l")) ed.putLong(k, o.getLong("v"));
                else if (t.equals("f")) ed.putFloat(k, (float) o.getDouble("v"));
                else if (t.equals("s")) ed.putString(k, o.getString("v"));
                else if (t.equals("set")) {
                    JSONArray a = o.getJSONArray("v");
                    java.util.HashSet<String> set = new java.util.HashSet<>();
                    for (int i = 0; i < a.length(); i++) set.add(a.getString(i));
                    ed.putStringSet(k, set);
                } else continue;
                n++;
            }
            ed.commit();
            return n;
        } catch (JSONException e) {
            return -1;
        }
    }
}
