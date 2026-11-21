package com.ramboai97.pixelpulse.core;

/**
 * The whole toy as a pure state machine: clock + compass, long-press box breathing,
 * and automatic music bars. No Android imports, so the same code drives the
 * real matrix, the in-app previews and the desktop preview renderer.
 *
 * Look: retro LED scoreboard, like Pocket Puzzle. Crisp pixels only (no
 * anti-aliasing), four shades (Px.snap), seven-segment digits with ghost 8s,
 * square shapes, stepped motion. A dim bezel frames every mode.
 */
public final class Scene {
    public static final int M_CLOCK = 0, M_COUNT = 1, M_BREATHE = 2, M_DONE = 3, M_CANCEL = 4, M_MUSIC = 5;
    public static final int H_START = 1, H_IN = 2, H_HOLD = 3, H_OUT = 4, H_DONE = 5, H_CANCEL = 6;

    public interface Listener {
        void haptic(int kind);
        void meditationUsed();
    }

    /** Everything the outside world tells the scene each frame. */
    public static final class Input {
        public int hour, minute, second;    // 0-23, 0-59, 0-59
        public float heading = Float.NaN;   // where north is on the matrix, radians clockwise from up
        public boolean headingOk;
        public boolean musicActive;
        public float[] bands;               // 6 levels 0..1 (bass first) or null if no audio data
    }

    // The palette: four shades, like an old handheld screen.
    public static final float HI = Px.S3, MID = Px.S2, DIM = Px.S1;

    private static final int FADE_MS = 400, COUNT_MS = 3000, DONE_MS = 2900, CANCEL_MS = 700;
    private static final int INTRO_MS = 1400, NOTE_MS = 900;

    // The breathing box: 13x13 outline, 11x11 inside, round pips underneath.
    private static final int BX0 = 6, BY0 = 4, BX1 = 18, BY1 = 16, PIP_Y = 19;
    private static final int[] PIP_X = {9, 11, 13, 15}; // max 4 pips

    private final Config cfg;
    private Listener listener;
    private final Px px = new Px();
    private final float[] last = new float[Px.N * Px.N];
    private float[] fadeFrom;
    private long fadeStart;

    private int mode = M_CLOCK;
    private long modeStart, openTime, lastFrame, medStart;
    private boolean introSweep, hint;
    private int lastPhaseKey = -1;
    private float cancelFrom, nAlpha;
    private boolean musicDismissed;
    private long musicOnSince = -1, musicOffSince = -1;
    private final Beat beat = new Beat();

    public Scene(Config cfg, Listener listener) {
        this.cfg = cfg;
        this.listener = listener;
    }

    public void setListener(Listener l) { listener = l; }
    public int mode() { return mode; }
    public boolean meditating() { return mode == M_COUNT || mode == M_BREATHE; }
    public boolean wantsAudio() { return mode == M_MUSIC; }
    /** True while something is moving and the matrix must not time out. */
    public boolean busy() { return mode != M_CLOCK; }

    /** Length of a full session in ms (rounds x one box). */
    public long sessionMs() {
        int[] p = cfg.breath();
        return (long) Math.max(1, cfg.rounds) * (p[0] + p[1] + p[2] + p[3]) * 1000L;
    }

    public void open(long now, boolean introSweep, boolean hint) {
        openTime = now;
        lastFrame = now;
        modeStart = now;
        this.introSweep = introSweep;
        this.hint = hint;
    }

    /** Glyph Button long press: start or stop a session. */
    public void longPress(long now) {
        if (mode == M_COUNT || mode == M_BREATHE) {
            cancel(now);
        } else if (mode != M_CANCEL) {
            switchTo(M_COUNT, now);
            hint = false;
            buzz(H_START);
            if (listener != null) listener.meditationUsed();
        }
    }

    /** Short press: always back to the clock. */
    public void tap(long now) {
        if (mode == M_COUNT || mode == M_BREATHE) {
            cancel(now);
        } else if (mode == M_MUSIC) {
            musicDismissed = true;
            switchTo(M_CLOCK, now);
        } else if (mode == M_DONE) {
            switchTo(M_CLOCK, now);
        }
    }

    private void cancel(long now) {
        cancelFrom = mode == M_BREATHE ? progress(now) : 0f;
        switchTo(M_CANCEL, now);
        buzz(H_CANCEL);
    }

    public int[] frame(long now, Input in) {
        float dt = Math.max(0, Math.min(0.1f, (now - lastFrame) / 1000f));
        lastFrame = now;
        updateMusic(now, in);
        long t = now - modeStart;

        if (mode == M_COUNT && t >= COUNT_MS) { switchTo(M_BREATHE, now); medStart = now; lastPhaseKey = -1; t = 0; }
        if (mode == M_BREATHE && now - medStart >= sessionMs()) { switchTo(M_DONE, now); buzz(H_DONE); t = 0; }
        if (mode == M_DONE && t >= DONE_MS) { switchTo(M_CLOCK, now); t = 0; }
        if (mode == M_CANCEL && t >= CANCEL_MS) { switchTo(M_CLOCK, now); t = 0; }

        px.clear();
        switch (mode) {
            case M_CLOCK:   drawClock(now, in, dt); break;
            case M_COUNT:   drawCountdown(t); break;
            case M_BREATHE: drawBreathe(now); break;
            case M_DONE:    drawDone(t); break;
            case M_CANCEL:  drawCancel(t); break;
            case M_MUSIC:   drawMusic(now, t, in); break;
        }

        if (fadeFrom != null) {
            float a = Px.ease((now - fadeStart) / (float) FADE_MS);
            for (int i = 0; i < px.b.length; i++) px.b[i] = fadeFrom[i] * (1 - a) + px.b[i] * a;
            if (a >= 1) fadeFrom = null;
        }
        System.arraycopy(px.b, 0, last, 0, last.length);
        return px.toInts();
    }

    /** Clock with compass for the always-on display (no intro, no hint). */
    public int[] staticClock(Input in) {
        px.clear();
        px.rim(1f, DIM);
        drawTime(in, false);
        if (cfg.compass && in.headingOk && !Float.isNaN(in.heading)) needle(in.heading, 1f);
        return px.toInts();
    }

    // ---------------------------------------------------------------- clock

    /** HH:MM on one line, seven-segment style, ghost 8s behind like an LED scoreboard. */
    private void drawTime(Input in, boolean blink) {
        int h = in.hour;
        boolean pad = cfg.h24;
        if (!cfg.h24) { h = h % 12; if (h == 0) h = 12; }
        int y = 9;
        int[] xs = {2, 7, 14, 19};
        for (int x : xs) Font.seg(px, 8, x, y, DIM);
        if (h >= 10 || pad) Font.seg(px, h / 10, xs[0], y, HI);
        Font.seg(px, h % 10, xs[1], y, HI);
        Font.seg(px, in.minute / 10, xs[2], y, HI);
        Font.seg(px, in.minute % 10, xs[3], y, HI);
        float colon = blink && (in.second % 2 == 1) ? DIM : MID;
        px.set(12, y + 2, colon);
        px.set(12, y + 4, colon);
    }

    private void drawClock(long now, Input in, float dt) {
        px.rim(1f, DIM);
        drawTime(in, true);
        if (cfg.compass) {
            long since = now - openTime;
            boolean sweeping = introSweep && since < INTRO_MS;
            boolean ok = in.headingOk && !Float.isNaN(in.heading);
            float target = (ok || sweeping) ? 1f : 0f;
            nAlpha += (target - nAlpha) * Math.min(1f, dt * 4f);
            if (nAlpha >= 0.05f) {
                float a = Float.isNaN(in.heading) ? 0f : in.heading;
                if (sweeping) a += (float) (2 * Math.PI) * (1f - Px.ease(since / (float) INTRO_MS));
                needle(a, nAlpha);
            }
        }
        drawHint(now);
    }

    /**
     * Compass: a solid pixel arrowhead whose sharp tip sits on the bezel at
     * north and whose wide base faces the middle, so it reads as "that way".
     * (v1.2 had three rim LEDs with the tip inside, which read as pointing
     * inward / south.) A single dim rim LED marks south.
     */
    private void needle(float a, float alpha) {
        float sx = (float) Math.sin(a), cy = (float) Math.cos(a);
        for (int y = 0; y < Px.N; y++)
            for (int x = 0; x < Px.N; x++) {
                if (!Px.on(x, y)) continue;
                float dx = x - Px.C, dy = y - Px.C;
                float u = dx * sx - dy * cy;          // distance out towards north
                float v = dx * cy + dy * sx;          // sideways
                float tip = Math.min(12.4f, rimR(a));
                if (u < tip - 4.4f || u > tip + 0.5f) continue;
                float w = (tip + 0.5f - u) * 0.55f + 0.1f;
                int i = y * Px.N + x;
                if (u >= tip - 3.4f && Math.abs(v) <= w) px.b[i] = Math.max(px.b[i], HI * alpha);
                else if (Math.abs(v) <= w + 1f) px.b[i] *= (1 - alpha);   // dark gap so it never blends into the digits
            }
        px.setIdx(Px.RIM[Px.rimIndex(a + (float) Math.PI)], MID * alpha);
    }

    /** Distance from the centre to the rim LED nearest angle a. */
    private static float rimR(float a) {
        int i = Px.RIM[Px.rimIndex(a)];
        return (float) Math.hypot(i % Px.N - Px.C, i / Px.N - Px.C);
    }

    /** A blinking pixel under the time: "there is something to hold here". */
    private void drawHint(long now) {
        if (!hint) return;
        long t = now - openTime - 2500;
        if (t < 0) return;
        px.set(12, 19, (t / 700) % 2 == 0 ? MID : DIM);
    }

    // ---------------------------------------------------------------- breathing

    /** 3-2-1 while the box draws itself in, one third per second. */
    private void drawCountdown(long t) {
        px.rim(1f, DIM);
        int n = 3 - (int) (t / 1000);
        Font.seg(px, 8, 10, 7, DIM);
        Font.seg(px, Math.max(1, n), 10, 7, HI);
        float f = Px.clamp(t / (float) COUNT_MS);
        int[] path = boxPath();
        int lit = Math.round(f * path.length);
        for (int k = 0; k < path.length; k++) px.setIdx(path[k], k < lit ? MID : DIM * 0.6f);
        drawPips(cfg.rounds, -1, 0);
    }

    private float progress(long now) {
        return Px.clamp((now - medStart) / (float) sessionMs());
    }

    /**
     * Box breathing: a bright dot walks the box. Up the left side = breathe in,
     * across the top = hold, down the right = breathe out, along the bottom = hold.
     * The box fills like a tank as you breathe in and drains as you breathe out.
     * Pips underneath count the boxes left: 4, 3, 2, 1.
     */
    private void drawBreathe(long now) {
        int[] p = cfg.breath();
        int cycleMs = (p[0] + p[1] + p[2] + p[3]) * 1000;
        long e = now - medStart;
        int cycle = (int) (e / cycleMs);
        long tc = e % cycleMs;
        int phase = 0;
        long acc = 0;
        for (int i = 0; i < 4; i++) {
            long d = p[i] * 1000L;
            if (d > 0 && tc < acc + d) { phase = i; break; }
            acc += d;
        }
        float pp = (tc - acc) / (float) (p[phase] * 1000L);

        int key = cycle * 4 + phase;
        if (key != lastPhaseKey) {
            lastPhaseKey = key;
            buzz(phase == 0 ? H_IN : phase == 2 ? H_OUT : H_HOLD);
        }

        px.rim(1f, DIM);

        // water level 0..11 rows
        float level;
        switch (phase) {
            case 0:  level = pp; break;
            case 1:  level = 1; break;
            case 2:  level = 1 - pp; break;
            default: level = 0; break;
        }
        int rows = Math.round(level * (BY1 - BY0 - 1));
        for (int r = 0; r < rows; r++) {
            int y = BY1 - 1 - r;
            for (int x = BX0 + 1; x < BX1; x++) px.set(x, y, r == rows - 1 ? MID : DIM);
        }

        // outline, then the side being walked lit up to the dot
        for (int x = BX0; x <= BX1; x++) { px.set(x, BY0, DIM); px.set(x, BY1, DIM); }
        for (int y = BY0; y <= BY1; y++) { px.set(BX0, y, DIM); px.set(BX1, y, DIM); }
        int[] side = side(phase);
        int len = side.length / 2 - 1;
        int pos = Math.min(len, (int) Math.floor(pp * (len + 1)));
        for (int k = 0; k <= pos; k++) px.set(side[2 * k], side[2 * k + 1], k == pos ? HI : MID);

        int left = Math.max(1, cfg.rounds - cycle);
        drawPips(left, left - 1, now - medStart);
    }

    /** Points of one side of the box, in walking order, as x,y pairs. */
    private static int[] side(int phase) {
        int n = BX1 - BX0 + 1;
        int[] s = new int[2 * n];
        for (int k = 0; k < n; k++) {
            int x, y;
            switch (phase) {
                case 0:  x = BX0;     y = BY1 - k; break; // up the left
                case 1:  x = BX0 + k; y = BY0;     break; // across the top
                case 2:  x = BX1;     y = BY0 + k; break; // down the right
                default: x = BX1 - k; y = BY1;     break; // back along the bottom
            }
            s[2 * k] = x; s[2 * k + 1] = y;
        }
        return s;
    }

    /** Whole outline as pixel indexes, clockwise from the bottom-left corner. */
    private static int[] boxPath() {
        int n = BX1 - BX0;
        int[] out = new int[4 * n];
        int i = 0;
        for (int ph = 0; ph < 4; ph++) {
            int[] s = side(ph);
            for (int k = 0; k < n; k++) out[i++] = s[2 * k + 1] * Px.N + s[2 * k];
        }
        return out;
    }

    /** Pips for boxes left, centred (4, 3, 2, 1); the one in use blinks once per second. */
    private void drawPips(int left, int current, long t) {
        int n = Math.max(0, Math.min(PIP_X.length, left));
        int x0 = 12 - (n - 1);
        for (int k = 0; k < n; k++) {
            float v = HI;
            if (k == current && (t / 500) % 2 == 1) v = MID;
            px.set(x0 + 2 * k, PIP_Y, v);
        }
    }

    /**
     * Finish: the box flashes full, pops into sparks flying out in steps, and a
     * pixel heart beats twice before the clock comes back.
     */
    private void drawDone(long t) {
        px.rim(1f, DIM);
        if (t < 350) {
            float v = (t / 90) % 2 == 0 ? HI : MID;
            for (int y = BY0; y <= BY1; y++) for (int x = BX0; x <= BX1; x++) px.set(x, y, v);
            return;
        }
        if (t < 1000) {
            int step = (int) ((t - 350) / 110);          // 0..5
            float d = 7f + 2.2f * step;
            float v = step < 4 ? HI : MID;
            for (int k = 0; k < 8; k++) {
                double a = k * Math.PI / 4;
                px.set((int) Math.round(Px.C + d * Math.sin(a)), (int) Math.round(10 - d * Math.cos(a)), v);
            }
        }
        if (t >= 800) {
            long h = t - 800;
            boolean beat = (h > 250 && h < 450) || (h > 650 && h < 850);
            float v = h > 1700 ? MID : HI;
            Font.draw(px, beat ? Font.HEART_BIG : Font.HEART, beat ? 8 : 9, beat ? 6 : 7, v);
        }
    }

    private void drawCancel(long t) {
        px.rim(1f, DIM);
        float k = Px.clamp(t / (float) CANCEL_MS);
        int[] path = boxPath();
        int lit = Math.round((1 - k) * path.length);
        for (int i = 0; i < lit; i++) px.setIdx(path[i], MID);
    }

    // ---------------------------------------------------------------- music

    private void updateMusic(long now, Input in) {
        if (!in.musicActive) musicDismissed = false;
        boolean on = cfg.autoMusic && in.musicActive && !musicDismissed;
        if (on) {
            musicOffSince = -1;
            if (musicOnSince < 0) musicOnSince = now;
            if (mode == M_CLOCK && now - musicOnSince >= 800) {
                switchTo(M_MUSIC, now);
                beat.reset(now);
            }
        } else {
            musicOnSince = -1;
            if (musicOffSince < 0) musicOffSince = now;
            if (mode == M_MUSIC && now - musicOffSince >= 2500) switchTo(M_CLOCK, now);
        }
    }

    /** Retro hi-fi meter: mirrored bars with peak caps that hang, then fall slowly. */
    private void drawMusic(long now, long t, Input in) {
        px.rim(1f, DIM);
        float noteA = t < NOTE_MS ? 1f : 0f;
        if (noteA > 0) { Font.draw(px, Font.NOTE, 9, 8, HI); return; }

        beat.update(now, in.bands, musicOffSince < 0);
        float[] bars = beat.bars, caps = beat.caps;
        for (int k = 0; k < 6; k++) {
            drawBar(12 + 2 * k, bars[k], caps[k]);
            if (k > 0) drawBar(12 - 2 * k, bars[k], caps[k]);
        }
    }

    private void drawBar(int col, float level, float cap) {
        int room = (int) (Px.C - Px.firstRow(col) - 1);
        int half = Math.round(room * Px.clamp(level));
        int c = (int) Px.C;
        // Negative space: the middle opens up as the bar grows, like lips parting.
        int gap = half >= 3 ? Math.round(half * 0.4f) : 0;
        for (int d = gap; d <= half; d++) {
            float v = d == half && d > 0 ? HI : MID;
            px.set(col, c - d, v);
            px.set(col, c + d, v);
        }
        int capH = Math.round(room * Px.clamp(cap));
        if (cap > 0.08f && capH >= half + 2 && capH <= room) {   // peak cap floats above with a gap
            px.set(col, c - capH, HI);
            px.set(col, c + capH, HI);
        }
    }

    // ---------------------------------------------------------------- helpers

    private void switchTo(int m, long now) {
        fadeFrom = last.clone();
        fadeStart = now;
        mode = m;
        modeStart = now;
    }

    private void buzz(int kind) {
        if (listener != null && cfg.haptics) listener.haptic(kind);
    }
}
