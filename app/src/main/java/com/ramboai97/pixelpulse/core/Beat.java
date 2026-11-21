package com.ramboai97.pixelpulse.core;

/**
 * The music bars' motion, shared by the 25x25 and 13x13 toys. Six levels
 * (bass first) that open and shut together on every hit, like a mouth.
 */
public final class Beat {
    public final float[] bars = new float[6], caps = new float[6];
    private float jaw, lastLoud;
    private long lastSound = -1;

    public void reset(long now) {
        java.util.Arrays.fill(bars, 0f);
        java.util.Arrays.fill(caps, 0f);
        jaw = 0;
        lastSound = now;
    }

    /** bands = 6 levels 0..1 or null (no audio data); playing = music still on. */
    public void update(long now, float[] bands, boolean playing) {
        boolean sound = false;
        if (bands != null) for (float v : bands) if (v > 0.04f) { sound = true; break; }
        if (sound) lastSound = now;
        // Gentle wave only when there's no audio data at all (no mic permission or
        // the visualizer failed). Real silence shows as silence, so pauses read.
        boolean pulse = bands == null && now - lastSound > 2000;

        // One shared pulse that snaps open on every hit (kick, snare, a spoken
        // syllable) and closes fast in between, so the whole shape opens and
        // shuts together. Each bar is that pulse shaped by its own band.
        float loud = 0;
        if (bands != null) {
            float bass = Math.max(bands[0], bands[1]);
            float mids = (bands[2] + bands[3]) * 0.5f;
            float highs = (bands[4] + bands[5]) * 0.5f;
            loud = Math.max(bass, Math.max(mids * 1.05f, highs * 0.8f));
        }
        boolean hit = loud - lastLoud > 0.12f && loud > 0.35f;   // a rising edge
        lastLoud = loud;
        if (!playing || pulse) jaw = 0;
        else if (hit) jaw = Math.min(1f, Math.max(jaw, loud) + 0.1f);    // pop open
        else if (loud > jaw) jaw += (loud - jaw) * 0.5f;
        else jaw *= 0.80f;                                              // shut quickly (~150 ms)

        for (int k = 0; k < 6; k++) {
            float target;
            if (!playing) target = 0;
            else if (pulse) target = (0.5f - 0.05f * k) * (0.65f + 0.35f * (float) Math.sin(now / 450.0 - k * 0.9));
            else {
                float band = bands == null ? 0 : bands[k];
                float shape = 1f - 0.09f * k;                 // tallest in the middle, like lips
                // ~0.5 for normal speech, only real hits get near the top (never pinned)
                target = Math.min(0.9f, 0.82f * (jaw * shape * (0.5f + 0.4f * band) + 0.2f * band));
            }
            bars[k] += (target - bars[k]) * (target > bars[k] ? 0.75f : 0.4f);
            caps[k] = Math.max(caps[k] - 0.03f, bars[k]);
        }
    }
}
