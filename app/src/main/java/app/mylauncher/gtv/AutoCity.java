package app.mylauncher.gtv;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Finds the nearest city in our lists from the TV's internet address. The TV has no GPS, and this needs no location
 * permission. Only the address of the connection is seen by the lookup service (ipwho.is); nothing else is sent.
 */
final class AutoCity {
    private AutoCity() {}

    interface Callback {
        /** On the worker thread: the nearest city, or null when none is within about 400 km or there is no network. */
        void found(Cities.City city);
    }

    private static final double MAX_KM = 400;

    static void find(final Callback cb) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Cities.City best = null;
                try {
                    HttpURLConnection c = (HttpURLConnection) new URL(
                        "https://ipwho.is/?fields=success,latitude,longitude").openConnection();
                    c.setConnectTimeout(8000);
                    c.setReadTimeout(8000);
                    BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = r.readLine()) != null) sb.append(line);
                    r.close();
                    JSONObject j = new JSONObject(sb.toString());
                    if (j.optBoolean("success", false)) {
                        best = nearest(j.getDouble("latitude"), j.getDouble("longitude"));
                    }
                } catch (Exception ignored) {
                    // No network or an unexpected answer: nothing found.
                }
                cb.found(best);
            }
        }, "auto-city").start();
    }

    static Cities.City nearest(double lat, double lon) {
        Cities.City best = null;
        double bestKm = MAX_KM;
        for (Cities.City[] list : new Cities.City[][] {Cities.EGYPT, Cities.SAUDI}) {
            for (Cities.City c : list) {
                double km = km(lat, lon, c.lat, c.lon);
                if (km < bestKm) {
                    bestKm = km;
                    best = c;
                }
            }
        }
        return best;
    }

    private static double km(double la1, double lo1, double la2, double lo2) {
        double dLa = Math.toRadians(la2 - la1), dLo = Math.toRadians(lo2 - lo1);
        double a = Math.sin(dLa / 2) * Math.sin(dLa / 2)
            + Math.cos(Math.toRadians(la1)) * Math.cos(Math.toRadians(la2)) * Math.sin(dLo / 2) * Math.sin(dLo / 2);
        return 6371 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
