package com.ramboai97.pixelpulse.core;

/** User settings. Mutable so a live settings change reaches a running toy. */
public final class Config {
    public static final int[][] PATTERNS = {{4, 4, 4, 0}, {4, 4, 4, 4}, {4, 7, 8, 0}};
    public static final String[] PATTERN_NAMES = {"4 · 4 · 4", "Box 4 · 4 · 4 · 4", "4 · 7 · 8"};

    public volatile boolean h24 = true;
    public volatile boolean compass = true;
    public volatile boolean haptics = true;
    public volatile boolean autoMusic = true;
    public volatile int rounds = 4;
    public volatile int pattern = 1;

    public int[] breath() { return PATTERNS[Math.max(0, Math.min(PATTERNS.length - 1, pattern))]; }
}
