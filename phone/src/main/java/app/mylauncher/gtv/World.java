package app.mylauncher.gtv;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/** Every country, and the provinces/governorates of each (data: dr5hn/countries-states-cities-database, ODbL), read from a file in the app. */
final class World {
    private World() {}

    private static JSONObject data;

    private static synchronized JSONObject data(Context c) {
        if (data != null) return data;
        try {
            InputStream in = c.getResources().openRawResource(R.raw.world_places);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            in.close();
            data = new JSONObject(out.toString("UTF-8"));
        } catch (Exception e) {
            data = new JSONObject();
        }
        return data;
    }

    /** {code, Arabic name} for every country. */
    static List<String[]> countries(Context c) {
        List<String[]> l = new ArrayList<>();
        JSONArray a = data(c).optJSONArray("c");
        if (a == null) return l;
        for (int i = 0; i < a.length(); i++) {
            JSONArray x = a.optJSONArray(i);
            if (x != null) l.add(new String[] {x.optString(0), x.optString(1)});
        }
        return l;
    }

    /** The places of a country as City objects (Arabic name, English name, coordinates). */
    static List<Cities.City> places(Context c, String code) {
        List<Cities.City> l = new ArrayList<>();
        JSONArray a = data(c).optJSONArray("s");
        if (a == null) return l;
        for (int i = 0; i < a.length(); i++) {
            JSONArray x = a.optJSONArray(i);
            if (x != null && code.equals(x.optString(0))) {
                Cities.City c2 = new Cities.City(code, x.optString(2).replace("|", " "), x.optString(1).replace("|", " "),
                    x.optDouble(3), x.optDouble(4));
                if (!x.optString(5).isEmpty()) c2.tz = x.optString(5);
                l.add(c2);
            }
        }
        return l;
    }
}
