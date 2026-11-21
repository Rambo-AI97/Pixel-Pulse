package com.ramboai97.pixelpulse.core;

/**
 * Pixel Pulse for the 13x13 Glyph Matrix of the Phone (4a) Pro. That phone runs
 * toys on the always-on display only and has no Glyph Button, so there is no
 * breathing session here: just the clock + north arrow, and music bars when
 * music plays. Same four-shade retro look as the 25x25 Scene.
 *
 * The real LED outline isn't documented, so everything is drawn inside a
 * 13-pixel circle: it looks right whether the panel is round or square.
 */
public final class SmallScene {
    public static final int N = 13;
    static final float C = 6f;
    static final float HI = Px.S3, MID = Px.S2, DIM = Px.S1;

    /** Classic 3x5 digits. */
    private static final String[][] D = {
        {"###", "#.#", "#.#", "#.#", "###"}, {".#.", "##.", ".#.", ".#.", "###"},
        {"###", "..#", "###", "#..", "###"}, {"###", "..#", ".##", "..#", "###"},
        {"#.#", "#.#", "###", "..#", "..#"}, {"###", "#..", "###", "..#", "###"},
        {"###", "#..", "###", "#.#", "###"}, {"###", "..#", ".#.", ".#.", ".#."},
        {"###", "#.#", "###", "#.#", "###"}, {"###", "#.#", "###", "..#", "###"},
    };

    private final Config cfg;
    private final float[] b = new float[N * N];
    private final Beat beat = new Beat();
    private boolean music;
    private long musicOnSince = -1, musicOffSince = -1, lastFrame;
    private float arrowA;

    public SmallScene(Config cfg) { this.cfg = cfg; }

    public boolean wantsAudio() { return music; }
    public boolean busy() { return music; }

    public static boolean on(int x, int y) {
        float dx = x - C, dy = y - C;
        return x >= 0 && y >= 0 && x < N && y < N && dx * dx + dy * dy <= 6.5f * 6.5f;
    }

    public int[] frame(long now, Scene.Input in) {
        float dt = Math.max(0, Math.min(0.1f, (now - lastFrame) / 1000f));
        lastFrame = now;
        updateMusic(now, in);
        java.util.Arrays.fill(b, 0f);
        if (music) drawMusic(now, in); else drawClock(in, dt);
        int[] out = new int[N * N];
        for (int y = 0; y < N; y++)
            for (int x = 0; x < N; x++) out[y * N + x] = on(x, y) ? Px.snap(b[y * N + x]) : 0;
        return out;
    }

    private void updateMusic(long now, Scene.Input in) {
        boolean want = cfg.autoMusic && in.musicActive;
        if (want) {
            musicOffSince = -1;
            if (musicOnSince < 0) musicOnSince = now;
            if (!music && now - musicOnSince >= 800) { music = true; beat.reset(now); }
        } else {
            musicOnSince = -1;
            if (musicOffSince < 0) musicOffSince = now;
            if (music && now - musicOffSince >= 2500) music = false;
        }
    }

    // ---------------------------------------------------------------- clock

    private void drawClock(Scene.Input in, float dt) {
        int h = in.hour;
        if (!cfg.h24) { h = h % 12; if (h == 0) h = 12; }
        boolean pad = cfg.h24 || h >= 10;
        // hours on top, minutes below, ghost 8s behind like an LED scoreboard
        digit(8, 3, 1, DIM); digit(8, 7, 1, DIM);
        digit(8, 3, 7, DIM); digit(8, 7, 7, DIM);
        if (pad) digit(h / 10, 3, 1, HI);
        digit(h % 10, 7, 1, HI);
        digit(in.minute / 10, 3, 7, HI);
        digit(in.minute % 10, 7, 7, HI);
        if (cfg.compass) {
            boolean ok = in.headingOk && !Float.isNaN(in.heading);
            arrowA += ((ok ? 1f : 0f) - arrowA) * Math.min(1f, dt * 4f);
            if (arrowA >= 0.05f && !Float.isNaN(in.heading)) arrow(in.heading, arrowA);
        }
    }

    private void digit(int d, int x0, int y0, float v) {
        String[] g = D[Math.max(0, Math.min(9, d))];
        for (int y = 0; y < 5; y++)
            for (int x = 0; x < 3; x++)
                if (g[y].charAt(x) == '#') set(x0 + x, y0 + y, v);
    }

    /**
     * North marker: on 13x13 there's no room for a triangle next to the digits,
     * so it's a short bright arc on the edge (brightest in the middle), with the
     * edge pixels beside it kept dark so it stands out from the digits.
     */
    private void arrow(float a, float alpha) {
        for (int y = 0; y < N; y++)
            for (int x = 0; x < N; x++) {
                if (!on(x, y)) continue;
                float dx = x - C, dy = y - C;
                if (dx * dx + dy * dy < 5.9f * 5.9f) continue;        // outermost ring only
                double d = Math.abs(Math.IEEEremainder(Math.atan2(dx, -dy) - a, 2 * Math.PI));
                int i = y * N + x;
                if (d < 0.17) b[i] = HI * alpha;
                else if (d < 0.42) b[i] = MID * alpha;

            }
    }

    // ---------------------------------------------------------------- music

    /** Seven mirrored bars (bass in the middle) that open into a dark mouth. */
    private void drawMusic(long now, Scene.Input in) {
        beat.update(now, in.bands, musicOffSince < 0);
        // 6 bands -> 4 columns from the middle out
        float[] lv = {beat.bars[0], beat.bars[1], (beat.bars[2] + beat.bars[3]) / 2, (beat.bars[4] + beat.bars[5]) / 2};
        float[] cp = {beat.caps[0], beat.caps[1], (beat.caps[2] + beat.caps[3]) / 2, (beat.caps[4] + beat.caps[5]) / 2};
        for (int k = 0; k < 4; k++) {
            bar(6 + 2 * k, lv[k], cp[k]);
            if (k > 0) bar(6 - 2 * k, lv[k], cp[k]);
        }
    }

    private void bar(int col, float level, float cap) {
        int top = 0;
        while (top < N && !on(col, top)) top++;
        int room = (int) C - top;
        int half = Math.round(room * Px.clamp(level));
        int c = (int) C;
        int gap = half >= 3 ? Math.round(half * 0.4f) : 0;
        for (int d = gap; d <= half; d++) {
            float v = d == half && d > 0 ? HI : MID;
            set(col, c - d, v);
            set(col, c + d, v);
        }
        int capH = Math.round(room * Px.clamp(cap));
        if (cap > 0.08f && capH >= half + 2 && capH <= room) {
            set(col, c - capH, HI);
            set(col, c + capH, HI);
        }
    }

    private void set(int x, int y, float v) {
        if (x < 0 || y < 0 || x >= N || y >= N) return;
        int i = y * N + x;
        if (v > b[i]) b[i] = v;
    }
}
