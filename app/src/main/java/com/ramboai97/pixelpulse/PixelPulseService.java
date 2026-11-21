package com.ramboai97.pixelpulse;

import android.Manifest;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.media.audiofx.Visualizer;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.util.Log;

import com.ramboai97.pixelpulse.core.Config;
import com.ramboai97.pixelpulse.core.Scene;
import com.ramboai97.pixelpulse.core.SmallScene;
import com.nothing.ketchum.Common;
import com.nothing.ketchum.Glyph;
import com.nothing.ketchum.GlyphMatrixManager;
import com.nothing.ketchum.GlyphToy;

import java.util.Calendar;

/** The Glyph Toy: binds when selected with the Glyph Button, unbinds when the user moves on. */
public class PixelPulseService extends Service implements SensorEventListener {
    private static final String TAG = "PixelPulse";
    private static final long FRAME_MS = 33, AOD_MS = 100, IDLE_MS = 250;

    private final Handler main = new Handler(Looper.getMainLooper());
    private GlyphMatrixManager gm;
    private boolean connected, running, aod;
    private long downAt = -1, lastChange = -1;
    private boolean lastOk;
    private Config cfg;
    private Scene scene;
    /** Phone (4a) Pro: 13x13 matrix, always-on only, no Glyph Button. Null on Phone (3). */
    private SmallScene small;
    private final Scene.Input in = new Scene.Input();

    private SensorManager sensors;
    private boolean sensorOn;
    private int rotAccuracy = SensorManager.SENSOR_STATUS_ACCURACY_HIGH;
    // Device x and y axes in world (east, north) terms, smoothed.
    private float xe = Float.NaN, xn, ye, yn, headAcc = -1;
    private final float[] rot = new float[9];

    private AudioManager audio;
    private Visualizer viz;
    private final float[] bands = new float[6], avg = new float[6];
    private long lastBandLog;
    private volatile long lastFft;

    private Vibrator vibrator;

    // ---------------------------------------------------------------- Glyph Button events

    private final Handler events = new Handler(Looper.getMainLooper()) {
        @Override public void handleMessage(Message msg) {
            if (msg.what != GlyphToy.MSG_GLYPH_TOY) { super.handleMessage(msg); return; }
            Bundle b = msg.getData();
            String ev = b == null ? null : b.getString(GlyphToy.MSG_GLYPH_TOY_DATA);
            if (ev == null || scene == null) return;
            long now = SystemClock.uptimeMillis();
            Log.d(TAG, "event " + ev + " mode=" + scene.mode());
            if (GlyphToy.EVENT_AOD.equals(ev)) {
                onAod();
            } else if (GlyphToy.EVENT_CHANGE.equals(ev)) {
                wake();
                lastChange = now;
                scene.longPress(now);
            } else if (GlyphToy.EVENT_ACTION_DOWN.equals(ev)) {
                wake();
                downAt = now;
            } else if (GlyphToy.EVENT_ACTION_UP.equals(ev)) {
                wake();
                // A release that didn't end a long press is a tap: back to the clock.
                boolean wasLong = lastChange >= 0 && now - lastChange < 1500;
                boolean held = downAt >= 0 && now - downAt > 700;
                if (!wasLong && !held) { Log.d(TAG, "tap"); scene.tap(now); }
                downAt = -1;
            }
        }
    };
    private final Messenger messenger = new Messenger(events);

    private final Scene.Listener sceneListener = new Scene.Listener() {
        @Override public void haptic(int kind) { buzz(kind); }
        @Override public void meditationUsed() { Prefs.sp(PixelPulseService.this).edit().putBoolean(Prefs.MED_USED, true).apply(); }
    };

    private final SharedPreferences.OnSharedPreferenceChangeListener prefWatcher = (p, k) -> {
        if (cfg != null) {
            Prefs.load(this, cfg);
            updateSensors();
        }
    };

    private final BroadcastReceiver screen = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            if (Intent.ACTION_SCREEN_ON.equals(i.getAction())) wake();
        }
    };

    // ---------------------------------------------------------------- lifecycle

    @Override public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind");
        cfg = Prefs.load(this);
        SharedPreferences sp = Prefs.sp(this);
        int opens = sp.getInt(Prefs.OPENS, 0) + 1;
        sp.edit().putInt(Prefs.OPENS, opens).apply();
        boolean medUsed = sp.getBoolean(Prefs.MED_USED, false);
        sp.registerOnSharedPreferenceChangeListener(prefWatcher);

        scene = new Scene(cfg, sceneListener);
        boolean pro4a = false;
        try { pro4a = Common.is25111p(); } catch (Throwable ignored) {}
        small = pro4a ? new SmallScene(cfg) : null;
        Log.d(TAG, "device " + (pro4a ? "Phone (4a) Pro 13x13" : "Phone (3) 25x25"));
        scene.open(SystemClock.uptimeMillis(), opens <= 5, !medUsed && opens <= 10);

        sensors = getSystemService(SensorManager.class);
        audio = getSystemService(AudioManager.class);
        VibratorManager vm = getSystemService(VibratorManager.class);
        vibrator = vm != null ? vm.getDefaultVibrator() : null;
        registerReceiver(screen, new IntentFilter(Intent.ACTION_SCREEN_ON), Context.RECEIVER_NOT_EXPORTED);

        gm = GlyphMatrixManager.getInstance(getApplicationContext());
        gm.init(new GlyphMatrixManager.Callback() {
            @Override public void onServiceConnected(ComponentName name) {
                Log.d(TAG, "glyph service connected");
                gm.register(small != null ? Glyph.DEVICE_25111p : Glyph.DEVICE_23112);
                connected = true;
                start();
            }
            @Override public void onServiceDisconnected(ComponentName name) { connected = false; }
        });
        return messenger.getBinder();
    }

    @Override public boolean onUnbind(Intent intent) {
        Log.d(TAG, "onUnbind");
        stop();
        try { unregisterReceiver(screen); } catch (Exception ignored) {}
        Prefs.sp(this).unregisterOnSharedPreferenceChangeListener(prefWatcher);
        if (gm != null) {
            gm.turnOff();
            gm.unInit();
        }
        gm = null;
        connected = false;
        scene = null;
        small = null;
        return false;
    }

    private void start() {
        if (running) return;
        running = true;
        aod = false;
        updateSensors();
        main.post(tick);
    }

    private void stop() {
        running = false;
        main.removeCallbacks(tick);
        setSensor(false);
        stopViz();
    }

    private void wake() { aod = false; if (connected) start(); }

    /**
     * Always-on display (phone face down / screen off). v1.1 froze a still clock
     * here, which hid the compass and stopped music detection. Now the loop keeps
     * running, just slower, so the needle stays live and music still takes over.
     */
    private void onAod() {
        if (!running && connected) start();
        aod = true;
    }

    // ---------------------------------------------------------------- frame loop

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!running || scene == null) return;
            long now = SystemClock.uptimeMillis();
            readClock();
            readHeading();
            boolean music = audio != null && audio.isMusicActive();
            if (music != in.musicActive) Log.d(TAG, "music " + music);
            in.musicActive = music;
            if (in.headingOk != lastOk) { lastOk = in.headingOk; Log.d(TAG, "compass ok=" + lastOk + " acc=" + rotAccuracy + " headAcc=" + headAcc); }
            in.bands = viz != null && now - lastFft < 1500 ? bands : null;

            boolean busy;
            if (small != null) {
                push(small.frame(now, in));
                if (small.wantsAudio()) startViz(); else stopViz();
                busy = small.busy();
            } else {
                push(scene.frame(now, in));
                if (scene.wantsAudio()) startViz(); else stopViz();
                busy = scene.busy();
            }

            boolean moving = busy || (cfg.compass && sensorOn);
            main.postDelayed(this, !moving ? IDLE_MS : (aod && !busy) ? AOD_MS : FRAME_MS);
        }
    };

    private void push(int[] frame) {
        if (!connected || gm == null) return;
        try { gm.setMatrixFrame(com.ramboai97.pixelpulse.core.Px.toHardware(frame)); } catch (Exception e) { Log.w(TAG, "frame", e); }
    }

    private void readClock() {
        Calendar c = Calendar.getInstance();
        in.hour = c.get(Calendar.HOUR_OF_DAY);
        in.minute = c.get(Calendar.MINUTE);
        in.second = c.get(Calendar.SECOND);
    }

    // ---------------------------------------------------------------- compass

    private void updateSensors() { setSensor(running && cfg.compass); }

    private void setSensor(boolean on) {
        if (sensors == null || on == sensorOn) return;
        Sensor s = sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        if (s == null) return;
        if (on) sensors.registerListener(this, s, SensorManager.SENSOR_DELAY_GAME);
        else { sensors.unregisterListener(this); xe = Float.NaN; }
        sensorOn = on;
    }

    @Override public void onSensorChanged(SensorEvent e) {
        SensorManager.getRotationMatrixFromVector(rot, e.values);
        // Columns of the rotation matrix = phone axes in world (east, north, up).
        float a = rot[0], b = rot[3], c = rot[1], d = rot[4];
        if (Float.isNaN(xe)) { xe = a; xn = b; ye = c; yn = d; }
        else { float k = 0.15f; xe += (a - xe) * k; xn += (b - xn) * k; ye += (c - ye) * k; yn += (d - yn) * k; }
        headAcc = e.values.length > 4 ? e.values[4] : -1;
    }

    @Override public void onAccuracyChanged(Sensor s, int accuracy) { rotAccuracy = accuracy; }

    /**
     * Where north is on the matrix, as seen by someone looking at the back.
     * v1.x projected north onto the screen plane, which only works lying flat:
     * held upright with the back facing you, that projection collapses and the
     * arrow spun or vanished. Now: the matrix's "right" is the phone's -x axis
     * (mirrored, seen from the back), flattened onto the ground; "up" on the
     * matrix is the direction 90 degrees left of that, i.e. the way you face
     * (upright) or the way the top points (flat, face down). Works for any tilt
     * in portrait. Falls back to the y axis only if the phone is on its side.
     */
    private void readHeading() {
        if (!sensorOn || Float.isNaN(xe)) { in.headingOk = false; in.heading = Float.NaN; return; }
        float lx = (float) Math.hypot(xe, xn), ly = (float) Math.hypot(ye, yn);
        if (lx > 0.3f) in.heading = (float) Math.atan2(-xn, -xe);
        else in.heading = (float) Math.atan2(-ye, yn);
        boolean accurate = rotAccuracy != SensorManager.SENSOR_STATUS_NO_CONTACT;
        in.headingOk = accurate && Math.max(lx, ly) > 0.3f;
    }

    // ---------------------------------------------------------------- music bars

    private void startViz() {
        if (viz != null) return;
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return;
        try {
            Visualizer v = new Visualizer(0);
            v.setEnabled(false);
            int[] range = Visualizer.getCaptureSizeRange();
            v.setCaptureSize(Math.min(1024, range[1]));
            // NORMALIZED (the default) boosts every capture to full scale, which pinned
            // the bars at max and hid pauses. AS_PLAYED keeps real loudness.
            v.setScalingMode(Visualizer.SCALING_MODE_AS_PLAYED);
            v.setDataCaptureListener(new Visualizer.OnDataCaptureListener() {
                @Override public void onWaveFormDataCapture(Visualizer vz, byte[] w, int rate) {}
                @Override public void onFftDataCapture(Visualizer vz, byte[] fft, int rate) { onFft(fft, rate); }
            }, Math.min(Visualizer.getMaxCaptureRate(), 30000), false, true);
            v.setEnabled(true);
            viz = v;
            java.util.Arrays.fill(avg, Float.NaN);
        } catch (Throwable t) {
            Log.w(TAG, "visualizer unavailable, using pulse", t);
        }
    }

    private void stopViz() {
        if (viz == null) return;
        try { viz.setEnabled(false); viz.release(); } catch (Throwable ignored) {}
        viz = null;
    }

    private static final float[] EDGES = {40, 150, 400, 1000, 2500, 6000, 16000};

    /**
     * Each bar shows its band's loudness relative to that band's own running
     * average: average sits a bit under half, a stressed syllable or drum hit reaches
     * the top, and a pause drops it to nothing. The average only learns from
     * sound, so a pause in speech doesn't drag it down.
     */
    private void onFft(byte[] fft, int samplingRateMilliHz) {
        int n = fft.length;
        float binHz = samplingRateMilliHz / 1000f / n;
        float loudest = -99;
        float[] db = new float[6];
        for (int b = 0; b < 6; b++) {
            int lo = Math.max(1, (int) (EDGES[b] / binHz)), hi = Math.min(n / 2 - 1, (int) (EDGES[b + 1] / binHz));
            float sum = 0;
            int cnt = 0;
            for (int k = lo; k <= hi; k++) {
                float re = fft[2 * k], im = fft[2 * k + 1];
                sum += re * re + im * im;
                cnt++;
            }
            db[b] = 10f * (float) Math.log10(Math.max(1e-4f, cnt == 0 ? 0 : sum / cnt));
            loudest = Math.max(loudest, db[b]);
        }
        boolean silent = loudest < -12f;                     // 8-bit FFT floor: nothing playing
        for (int b = 0; b < 6; b++) {
            if (Float.isNaN(avg[b])) avg[b] = db[b];
            boolean sound = !silent && db[b] > avg[b] - 12f;
            if (sound) avg[b] += (db[b] - avg[b]) * 0.02f;   // ~2.5 s memory at 20 captures/s
            // avg -> 0.45, +12 dB -> 1, -10 dB -> 0. Then a soft knee so only real
            // peaks touch the top; steady loud music hovers around the middle.
            float v = Math.max(0, (db[b] - avg[b] + 10f) / 22f);
            v = v < 0.7f ? v : 0.7f + 0.3f * (float) Math.tanh((v - 0.7f) / 0.3f);
            bands[b] = silent ? 0 : Math.max(0, Math.min(1, v));
        }
        lastFft = SystemClock.uptimeMillis();
        if (lastFft - lastBandLog > 2000) {
            lastBandLog = lastFft;
            StringBuilder sb = new StringBuilder("bands");
            for (int b = 0; b < 6; b++) sb.append(String.format(java.util.Locale.US, " %.0f/%.0f=%.2f", db[b], avg[b], bands[b]));
            Log.d(TAG, sb.toString());
        }
    }

    // ---------------------------------------------------------------- haptics

    private void buzz(int kind) {
        if (vibrator == null || !vibrator.hasVibrator()) return;
        VibrationEffect e;
        switch (kind) {
            case Scene.H_IN:     e = VibrationEffect.createOneShot(35, 90); break;
            case Scene.H_HOLD:   e = VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK); break;
            case Scene.H_OUT:    e = VibrationEffect.createWaveform(new long[]{0, 30, 110, 30}, new int[]{0, 90, 0, 90}, -1); break;
            case Scene.H_DONE:   e = VibrationEffect.createWaveform(new long[]{0, 70, 220, 70}, new int[]{0, 130, 0, 130}, -1); break;
            case Scene.H_START:  e = VibrationEffect.createOneShot(45, 120); break;
            case Scene.H_CANCEL: e = VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK); break;
            default: return;
        }
        vibrator.vibrate(e);
    }
}
