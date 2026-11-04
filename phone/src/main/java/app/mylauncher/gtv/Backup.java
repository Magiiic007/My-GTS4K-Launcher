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
        return k.startsWith("dbg_") || k.equals("radio_log") || k.startsWith("sil_") || k.startsWith("warn_at_")
            || k.equals("radio_resume");
    }

    static String export(Context c) throws JSONException {
        SharedPreferences sp = c.getSharedPreferences("magiic", Context.MODE_PRIVATE);
        JSONObject out = new JSONObject();
        out.put("app", APP).put("v", 1).put("time", System.currentTimeMillis());
        JSONObject p = new JSONObject();
        for (Map.Entry<String, ?> e : sp.getAll().entrySet()) {
            String k = e.getKey();
            Object v = e.getValue();
            if (skip(k) || v == null) continue;
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

    /** Puts a saved file back. Returns how many settings were restored, or -1 if the file is not one of ours. */
    static int restore(Context c, String json) {
        try {
            JSONObject root = new JSONObject(json);
            if (!APP.equals(root.optString("app"))) return -1;
            JSONObject p = root.getJSONObject("prefs");
            SharedPreferences sp = c.getSharedPreferences("magiic", Context.MODE_PRIVATE);
            SharedPreferences.Editor ed = sp.edit();
            for (String k : sp.getAll().keySet()) if (!skip(k)) ed.remove(k);
            int n = 0;
            java.util.Iterator<String> it = p.keys();
            while (it.hasNext()) {
                String k = it.next();
                if (skip(k)) continue;
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
