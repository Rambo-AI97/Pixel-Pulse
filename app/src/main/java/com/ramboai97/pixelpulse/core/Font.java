package com.ramboai97.pixelpulse.core;

/** Tiny pixel fonts tuned for a 25x25 round matrix. */
public final class Font {
    /** Thin 3x6 digits. */
    static final String[][] DIGITS = {
        {".#.", "#.#", "#.#", "#.#", "#.#", ".#."},
        {".#.", "##.", ".#.", ".#.", ".#.", "###"},
        {"##.", "..#", "..#", ".#.", "#..", "###"},
        {"##.", "..#", ".#.", "..#", "..#", "##."},
        {"#.#", "#.#", "###", "..#", "..#", "..#"},
        {"###", "#..", "##.", "..#", "..#", "##."},
        {".##", "#..", "##.", "#.#", "#.#", ".#."},
        {"###", "..#", "..#", ".#.", ".#.", ".#."},
        {".#.", "#.#", ".#.", "#.#", "#.#", ".#."},
        {".#.", "#.#", "#.#", ".##", "..#", "##."},
    };

    /** Pixel hearts for the finish (7x6 and a beat-sized 9x8). */
    static final String[] HEART = {".##.##.", "#######", "#######", ".#####.", "..###..", "...#..."};
    static final String[] HEART_BIG = {
        ".###.###.", "#########", "#########", "#########", ".#######.", "..#####..", "...###...", "....#...."};

    // Seven-segment digits, 4 wide x 7 tall: a top, b upper right, c lower right,
    // d bottom, e lower left, f upper left, g middle.
    private static final String[] SEGS = {"abcdef", "bc", "abdeg", "abcdg", "bcfg", "acdfg", "acdefg", "abc", "abcdefg", "abcdfg"};

    /** One seven-segment digit with its top-left at x,y. */
    public static void seg(Px p, int digit, int x, int y, float v) {
        String s = SEGS[Math.max(0, Math.min(9, digit))];
        for (int i = 0; i < s.length(); i++) {
            switch (s.charAt(i)) {
                case 'a': for (int k = 0; k < 4; k++) p.set(x + k, y, v); break;
                case 'd': for (int k = 0; k < 4; k++) p.set(x + k, y + 6, v); break;
                case 'g': for (int k = 0; k < 4; k++) p.set(x + k, y + 3, v); break;
                case 'f': for (int k = 0; k < 4; k++) p.set(x, y + k, v); break;
                case 'b': for (int k = 0; k < 4; k++) p.set(x + 3, y + k, v); break;
                case 'e': for (int k = 3; k < 7; k++) p.set(x, y + k, v); break;
                case 'c': for (int k = 3; k < 7; k++) p.set(x + 3, y + k, v); break;
            }
        }
    }

    /** 5x5 compass letter. */
    static final String[] NORTH = {"#...#", "##..#", "#.#.#", "#..##", "#...#"};

    static final String[] NOTE = {
        "...#..", "...##.", "...#.#", "...#..", "...#..", ".###..", "####..", "####..", ".##..."
    };

    /** Variable-width 5-row capitals (only the letters we need). */
    static String[] letter(char c) {
        switch (c) {
            case 'I': return new String[]{"#", "#", "#", "#", "#"};
            case 'N': return new String[]{"#..#", "##.#", "#.##", "#..#", "#..#"};
            case 'H': return new String[]{"#.#", "#.#", "###", "#.#", "#.#"};
            case 'O': return new String[]{".#.", "#.#", "#.#", "#.#", ".#."};
            case 'L': return new String[]{"#..", "#..", "#..", "#..", "###"};
            case 'D': return new String[]{"##.", "#.#", "#.#", "#.#", "##."};
            case 'U': return new String[]{"#.#", "#.#", "#.#", "#.#", "###"};
            case 'T': return new String[]{"###", ".#.", ".#.", ".#.", ".#."};
            default:  return new String[]{"", "", "", "", ""};
        }
    }

    public static void draw(Px p, String[] rows, int x, int y, float v) {
        for (int r = 0; r < rows.length; r++)
            for (int c = 0; c < rows[r].length(); c++)
                if (rows[r].charAt(c) == '#') p.set(x + c, y + r, v);
    }

    public static int textWidth(String s) {
        int w = 0;
        for (int i = 0; i < s.length(); i++) w += letter(s.charAt(i))[0].length() + (i > 0 ? 1 : 0);
        return w;
    }

    /** Draws text centred on the matrix, top row y. Returns left x. */
    public static int textCentered(Px p, String s, int y, float v) {
        int x = Math.round(Px.C - (textWidth(s) - 1) / 2f);
        int cx = x;
        for (int i = 0; i < s.length(); i++) {
            String[] g = letter(s.charAt(i));
            draw(p, g, cx, y, v);
            cx += g[0].length() + 1;
        }
        return x;
    }

    /** Two digits (or one, centred) with 3x6 glyphs. */
    public static void number(Px p, int n, boolean pad, int y, float v) {
        if (n >= 10 || pad) {
            draw(p, DIGITS[(n / 10) % 10], 9, y, v);
            draw(p, DIGITS[n % 10], 13, y, v);
        } else {
            draw(p, DIGITS[n], 11, y, v);
        }
    }
}
