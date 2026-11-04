package app.mylauncher.gtv;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

/** The neighbourhood name of a location (OpenStreetMap's Nominatim service); only the coordinates are sent. */
final class Place {
    private Place() {}

    /** On a worker thread: the neighbourhood in Arabic, or null when it cannot be found. */
    static String neighbourhood(double lat, double lon) {
        HttpURLConnection c = null;
        try {
            URL u = new URL(String.format(Locale.US,
                "https://nominatim.openstreetmap.org/reverse?format=jsonv2&zoom=16&accept-language=ar&lat=%.5f&lon=%.5f",
                lat, lon));
            c = (HttpURLConnection) u.openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(8000);
            c.setRequestProperty("User-Agent", "SalatKhair-Phone/0.6 (personal prayer-times app)");
            BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            r.close();
            JSONObject a = new JSONObject(sb.toString()).optJSONObject("address");
            if (a == null) return null;
            String[] keys = {"neighbourhood", "suburb", "quarter", "city_district", "residential", "hamlet", "village"};
            for (String k : keys) {
                String v = a.optString(k, "").trim();
                if (!v.isEmpty()) return v.replace("|", " ");
            }
        } catch (Exception ignored) {
            // no network or an unexpected answer: the city name is used
        } finally {
            if (c != null) c.disconnect();
        }
        return null;
    }

    /** A found place: name in Arabic, the region and country around it, and where it is. */
    static final class Hit {
        String name = "";
        String detail = "";
        String country = "";
        double lat;
        double lon;
    }

    /** On a worker thread: places matching a name anywhere in the world (Open-Meteo geocoding), in Arabic. */
    static java.util.List<Hit> search(String query) {
        java.util.List<Hit> out = new java.util.ArrayList<>();
        HttpURLConnection c = null;
        try {
            URL u = new URL("https://geocoding-api.open-meteo.com/v1/search?count=15&language=ar&format=json&name="
                + java.net.URLEncoder.encode(query, "UTF-8"));
            c = (HttpURLConnection) u.openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(8000);
            BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            r.close();
            org.json.JSONArray a = new JSONObject(sb.toString()).optJSONArray("results");
            if (a == null) return out;
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Hit h = new Hit();
                h.name = o.optString("name", "").replace("|", " ");
                String admin = o.optString("admin1", "");
                String ctry = o.optString("country", "");
                h.detail = (admin.isEmpty() ? "" : admin + "، ") + ctry;
                h.country = o.optString("country_code", "XX");
                h.lat = o.optDouble("latitude");
                h.lon = o.optDouble("longitude");
                if (!h.name.isEmpty()) out.add(h);
            }
        } catch (Exception ignored) {
            // no network: an empty list
        } finally {
            if (c != null) c.disconnect();
        }
        return out;
    }
}
