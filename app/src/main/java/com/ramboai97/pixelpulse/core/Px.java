package com.ramboai97.pixelpulse.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** 25x25 brightness buffer shaped like the Phone (3) Glyph Matrix (round, 489 LEDs). */
public final class Px {
    public static final int N = 25;
    public static final float C = 12f; // centre pixel

    /** The four shades of the retro palette (design values; see toHardware). */
    public static final float S1 = 34f, S2 = 120f, S3 = 255f;

    /** First/last lit column of every row, taken from Nothing's LED allocation drawing. */
    private static final int[][] ROWS = {
        {9, 15}, {7, 17}, {5, 19}, {4, 20}, {3, 21}, {2, 22}, {2, 22}, {1, 23}, {1, 23},
        {0, 24}, {0, 24}, {0, 24}, {0, 24}, {0, 24}, {0, 24}, {0, 24},
        {1, 23}, {1, 23}, {2, 22}, {2, 22}, {3, 21}, {4, 20}, {5, 19}, {7, 17}, {9, 15}
    };

    /** Outermost ring of LEDs, ordered clockwise starting at 12 o'clock. */
    public static final int[] RIM = buildRim();

    public final float[] b = new float[N * N];

    public static boolean on(int x, int y) {
        return y >= 0 && y < N && x >= ROWS[y][0] && x <= ROWS[y][1];
    }

    public static int firstRow(int col) {
        for (int y = 0; y < N; y++) if (on(col, y)) return y;
        return N;
    }

    public void clear() { Arrays.fill(b, 0f); }

    /** Max-blend a pixel (0..255). */
    public void set(int x, int y, float v) {
        if (x < 0 || y < 0 || x >= N || y >= N) return;
        int i = y * N + x;
        if (v > b[i]) b[i] = v;
    }

    /** Max-blend by flat index. */
    public void setIdx(int i, float v) { if (v > b[i]) b[i] = v; }

    /** Index into RIM of the LED closest to angle a (radians clockwise from up). */
    public static int rimIndex(float a) {
        double best = 9;
        int bi = 0;
        for (int i = 0; i < RIM.length; i++) {
            int x = RIM[i] % N, y = RIM[i] / N;
            double d = Math.abs(Math.IEEEremainder(Math.atan2(x - C, -(y - C)) - a, 2 * Math.PI));
            if (d < best) { best = d; bi = i; }
        }
        return bi;
    }

    /** Snap to the nearest palette shade, so every fade steps like an old console. */
    static int snap(float v) {
        if (v < 14) return 0;
        if (v < 70) return (int) S1;
        if (v < 180) return (int) S2;
        return (int) S3;
    }

    /** Multiply an area towards black (used to keep text readable). */
    public void dim(int x0, int y0, int x1, int y1, float keep) {
        for (int y = Math.max(0, y0); y <= Math.min(N - 1, y1); y++)
            for (int x = Math.max(0, x0); x <= Math.min(N - 1, x1); x++)
                b[y * N + x] *= keep;
    }

    /** Anti-aliased ring of radius r. */
    public void ring(float cx, float cy, float r, float width, float v) {
        for (int y = 0; y < N; y++)
            for (int x = 0; x < N; x++) {
                float d = (float) Math.hypot(x - cx, y - cy);
                float a = 1f - Math.abs(d - r) / width;
                if (a > 0) set(x, y, v * a);
            }
    }

    /** Anti-aliased filled disc. */
    public void disc(float cx, float cy, float r, float v) {
        for (int y = 0; y < N; y++)
            for (int x = 0; x < N; x++) {
                float d = (float) Math.hypot(x - cx, y - cy);
                float a = clamp(r + 0.5f - d);
                if (a > 0) set(x, y, v * a);
            }
    }

    /** Anti-aliased line segment. */
    public void line(float x0, float y0, float x1, float y1, float v) {
        float dx = x1 - x0, dy = y1 - y0, len2 = dx * dx + dy * dy;
        for (int y = 0; y < N; y++)
            for (int x = 0; x < N; x++) {
                float t = len2 == 0 ? 0 : clamp(((x - x0) * dx + (y - y0) * dy) / len2);
                float px = x0 + t * dx - x, py = y0 + t * dy - y;
                float a = 1f - (float) Math.hypot(px, py) / 0.75f;
                if (a > 0) set(x, y, v * Math.min(1f, a * 1.4f));
            }
    }

    /** Light the rim clockwise from 12 o'clock up to fraction f (0..1). */
    public void rim(float f, float v) {
        float filled = f * RIM.length;
        for (int i = 0; i < RIM.length; i++) {
            float a = clamp(filled - i);
            if (a > 0) b[RIM[i]] = Math.max(b[RIM[i]], v * a);
        }
    }

    /** Final 625-value frame, clamped and masked. */
    public int[] toInts() {
        int[] out = new int[N * N];
        for (int y = 0; y < N; y++)
            for (int x = 0; x < N; x++) {
                int i = y * N + x;
                out[i] = on(x, y) ? snap(b[i]) : 0;
            }
        return out;
    }

    /**
     * The Glyph service takes raw 0..4095 drive levels (the SDK's own renderer
     * multiplies by 4095/255). Map our 0..255 design values through a 2.2 gamma
     * so the matrix looks like the preview, with full white at full drive.
     */
    public static int[] toHardware(int[] f) {
        int[] out = new int[f.length];
        for (int i = 0; i < f.length; i++) {
            if (f[i] <= 0) continue;
            int v = Math.round(4095f * (float) Math.pow(f[i] / 255f, 2.2));
            out[i] = Math.max(6, Math.min(4095, v));
        }
        return out;
    }

    public static float clamp(float v) { return v < 0 ? 0 : (v > 1 ? 1 : v); }

    public static float ease(float t) { // smooth in-out
        t = clamp(t);
        return t * t * (3 - 2 * t);
    }

    private static int[] buildRim() {
        List<int[]> pts = new ArrayList<>();
        for (int y = 0; y < N; y++)
            for (int x = 0; x < N; x++) {
                if (!on(x, y)) continue;
                if (!on(x - 1, y) || !on(x + 1, y) || !on(x, y - 1) || !on(x, y + 1)) pts.add(new int[]{x, y});
            }
        pts.sort((p, q) -> Double.compare(angle(p), angle(q)));
        int[] r = new int[pts.size()];
        for (int i = 0; i < r.length; i++) r[i] = pts.get(i)[1] * N + pts.get(i)[0];
        return r;
    }

    private static double angle(int[] p) {
        double a = Math.atan2(p[0] - C, -(p[1] - C));
        return a < -1e-9 ? a + 2 * Math.PI : a;
    }
}
