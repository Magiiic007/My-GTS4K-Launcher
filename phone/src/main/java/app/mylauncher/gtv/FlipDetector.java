package app.mylauncher.gtv;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.SystemClock;

/**
 * Stops the adhan when the phone is turned face down, or shaken while it already lies face down.
 * <ul>
 * <li>Face down means the screen's normal points to the ground (the accelerometer's z axis reads about -9.8).</li>
 * <li>The first moments after the adhan starts only record where the phone is: a phone that already lies face
 *     down does not stop it by itself.</li>
 * <li>After that, the adhan stops when the phone <b>arrives</b> face down, or while it is face down it is moved
 *     (a shake, a nudge, or being picked up).</li>
 * <li>The adhan's own loudspeaker makes a small vibration on a table; that is below {@link #MOVE_THRESHOLD}.</li>
 * </ul>
 */
final class FlipDetector implements SensorEventListener {
    /** z below this means face down (gravity is about 9.8). */
    static final float DOWN_Z = -7.0f;
    /** A change from the slow average bigger than this (m/s^2) counts as moving the phone. */
    static final float MOVE_THRESHOLD = 2.0f;
    private static final long SETTLE_MS = 1500L;

    /** Live values for the test screen. */
    static volatile float lastZ, lastMove;
    static volatile boolean lastDown;

    private final SensorManager sm;
    private final Sensor accel;
    private final Runnable onStop;
    private long startedAt;
    private boolean wasDown;
    private boolean haveAvg;
    private float ax, ay, az;
    private boolean running;

    FlipDetector(Context c, Runnable onStop) {
        this.sm = c.getSystemService(SensorManager.class);
        this.accel = sm == null ? null : sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        this.onStop = onStop;
    }

    boolean available() {
        return accel != null;
    }

    void start() {
        if (accel == null || running) return;
        running = true;
        startedAt = SystemClock.elapsedRealtime();
        haveAvg = false;
        sm.registerListener(this, accel, SensorManager.SENSOR_DELAY_GAME);
    }

    void stop() {
        if (!running) return;
        running = false;
        sm.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent e) {
        float x = e.values[0], y = e.values[1], z = e.values[2];
        if (!haveAvg) {
            ax = x;
            ay = y;
            az = z;
            haveAvg = true;
        }
        float dx = x - ax, dy = y - ay, dz = z - az;
        float move = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        // A slow average follows the phone's position, so a steady tilt is not "movement".
        ax += 0.1f * dx;
        ay += 0.1f * dy;
        az += 0.1f * dz;
        boolean down = z < DOWN_Z;
        lastZ = z;
        lastMove = move;
        lastDown = down;

        if (SystemClock.elapsedRealtime() - startedAt < SETTLE_MS) {
            wasDown = down;
            return;
        }
        boolean arrivedDown = down && !wasDown;
        boolean movedWhileDown = (down || wasDown) && wasDown && move > MOVE_THRESHOLD;
        wasDown = down;
        if (arrivedDown || movedWhileDown) {
            stop();
            onStop.run();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor s, int accuracy) {
    }
}
