package app.mylauncher.gtv;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;

/**
 * A row of small status icons in the header: the connection (Wi-Fi strength, cable, no connection, or connected
 * without internet), Bluetooth and the radio. Only what is on is shown. The whole row is one
 * button: OK opens the network settings.
 */
final class StatusIcons {
    final LinearLayout view;

    private final Context ctx;
    private final ImageView net, bluetooth, radio;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            update();
            handler.postDelayed(this, 10000L);
        }
    };
    private final ConnectivityManager.NetworkCallback callback = new ConnectivityManager.NetworkCallback() {
        @Override
        public void onCapabilitiesChanged(Network n, NetworkCapabilities caps) {
            poke();
        }

        @Override
        public void onLost(Network n) {
            poke();
        }

        @Override
        public void onAvailable(Network n) {
            poke();
        }
    };
    private final BroadcastReceiver btReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            update();
        }
    };
    private final RadioService.Listener radioListener = new RadioService.Listener() {
        @Override
        public void onRadio(int state) {
            update();
        }
    };
    private boolean running;

    StatusIcons(Context context, View.OnClickListener open) {
        ctx = context;
        view = new LinearLayout(ctx);
        view.setOrientation(LinearLayout.HORIZONTAL);
        view.setGravity(android.view.Gravity.CENTER_VERTICAL);
        view.setPadding(dp(8), dp(4), dp(8), dp(4));
        view.setBackground(Theme.button(ctx, 14));
        view.setFocusable(true);
        view.setClickable(true);
        view.setOnClickListener(open);
        final android.graphics.drawable.Drawable ring = Theme.ring(ctx, 14);
        view.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean has) {
                v.setForeground(has ? ring : null);
                Motion.pop(v, has, 1.06f);
            }
        });
        radio = icon(R.drawable.ic_volume);
        bluetooth = icon(R.drawable.ic_bluetooth);
        net = icon(R.drawable.ic_wifi_off);
        update();
    }

    private ImageView icon(int res) {
        ImageView v = new ImageView(ctx);
        v.setImageResource(res);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(24), dp(24));
        lp.setMarginStart(dp(5));
        lp.setMarginEnd(dp(5));
        view.addView(v, lp);
        return v;
    }

    /** The home screen is drawn on a scaled canvas (see Stage), so sizes here are canvas units, not dp. */
    private int dp(int value) {
        return value;
    }

    void start() {
        running = true;
        try {
            ctx.getSystemService(ConnectivityManager.class).registerDefaultNetworkCallback(callback);
        } catch (RuntimeException e) {
            // No permission or no service: the 10-second refresh below still keeps the icon honest.
        }
        ctx.registerReceiver(btReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
        RadioService.listen(radioListener);
        handler.removeCallbacks(tick);
        handler.post(tick);
    }

    void stop() {
        running = false;
        handler.removeCallbacks(tick);
        try {
            ctx.getSystemService(ConnectivityManager.class).unregisterNetworkCallback(callback);
        } catch (RuntimeException ignored) {
            // It was never registered.
        }
        try {
            ctx.unregisterReceiver(btReceiver);
        } catch (RuntimeException ignored) {
            // It was never registered.
        }
        RadioService.unlisten(radioListener);
    }

    /** The network callbacks arrive on another thread. */
    private void poke() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (running) update();
            }
        });
    }

    private void tint(ImageView v, int colour) {
        v.setColorFilter(colour);
    }

    void update() {
        // The connection.
        int white = Color.WHITE;
        int amber = Color.parseColor("#FFFFB74D");
        int icon = R.drawable.ic_wifi_off;
        int colour = Color.parseColor("#FFEF5350");
        try {
            ConnectivityManager cm = ctx.getSystemService(ConnectivityManager.class);
            Network active = cm.getActiveNetwork();
            NetworkCapabilities caps = active == null ? null : cm.getNetworkCapabilities(active);
            if (caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                boolean works = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
                colour = works ? white : amber;
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                    icon = R.drawable.ic_ethernet;
                } else if (!works) {
                    icon = R.drawable.ic_warning;
                } else {
                    icon = wifiStrong() ? R.drawable.ic_wifi : R.drawable.ic_wifi_weak;
                }
            }
        } catch (RuntimeException e) {
            // Unknown: leave it showing "no connection" rather than guess.
        }
        net.setImageResource(icon);
        tint(net, colour);

        // Bluetooth.
        boolean bt = false;
        try {
            BluetoothAdapter a = BluetoothAdapter.getDefaultAdapter();
            bt = a != null && a.isEnabled();
        } catch (RuntimeException ignored) {
            // No permission to ask: show nothing.
        }
        bluetooth.setVisibility(bt ? View.VISIBLE : View.GONE);
        tint(bluetooth, white);

        // The radio.
        int st = RadioService.state();
        radio.setVisibility(st == RadioService.OFF ? View.GONE : View.VISIBLE);
        tint(radio, st == RadioService.PLAYING ? Theme.accent(ctx) : Color.parseColor("#99FFFFFF"));
    }

    @SuppressWarnings("deprecation")
    private boolean wifiStrong() {
        try {
            WifiManager wm = (WifiManager) ctx.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            WifiInfo info = wm.getConnectionInfo();
            // Level 0..4: the two highest are "strong"; an unknown reading counts as strong.
            return info == null || info.getRssi() == -127 || WifiManager.calculateSignalLevel(info.getRssi(), 5) >= 2;
        } catch (RuntimeException e) {
            return true;
        }
    }

    static ViewGroup.LayoutParams wrap() {
        return new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }
}
