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
}
