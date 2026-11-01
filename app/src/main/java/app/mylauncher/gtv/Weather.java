package app.mylauncher.gtv;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

/**
 * Current weather from Open-Meteo (free, no account, no key). The only thing the app sends is the chosen city's
 * coordinates. The request runs on its own thread and the answer comes back on the main thread.
 */
final class Weather {
    private Weather() {}

    interface Callback {
        /** temperature in whole degrees Celsius, and the WMO weather code (0 clear ... 99 thunderstorm). */
        void onResult(int tempC, int code);

        void onError();
    }

    static void fetch(final Cities.City city, final Callback callback) {
        final Handler main = new Handler(Looper.getMainLooper());
        new Thread(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection conn = null;
                try {
                    // Locale.US so the decimal point is a "." whatever the TV's language is.
                    String url = String.format(Locale.US,
                        "https://api.open-meteo.com/v1/forecast?latitude=%.2f&longitude=%.2f"
                            + "&current=temperature_2m,weather_code&timezone=auto", city.lat, city.lon);
                    conn = (HttpURLConnection) new URL(url).openConnection();
                    conn.setConnectTimeout(10000);
                    conn.setReadTimeout(10000);
                    if (conn.getResponseCode() != 200) throw new Exception("HTTP " + conn.getResponseCode());
                    StringBuilder body = new StringBuilder();
                    InputStream in = conn.getInputStream();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
                    String line;
                    while ((line = reader.readLine()) != null) body.append(line);
                    reader.close();
                    JSONObject current = new JSONObject(body.toString()).getJSONObject("current");
                    final int temp = (int) Math.round(current.getDouble("temperature_2m"));
                    final int code = current.getInt("weather_code");
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onResult(temp, code);
                        }
                    });
                } catch (Exception e) {
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onError();
                        }
                    });
                } finally {
                    if (conn != null) conn.disconnect();
                }
            }
        }).start();
    }
}
