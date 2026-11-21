package com.ramboai97.pixelpulse;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.format.DateFormat;

import com.ramboai97.pixelpulse.core.Config;

/** SharedPreferences <-> Config, plus first-use counters for the hints. */
final class Prefs {
    static final String FILE = "calm";
    static final String H24 = "h24", COMPASS = "compass", HAPTICS = "haptics", MUSIC = "music",
            ROUNDS = "rounds", PATTERN = "pattern", OPENS = "opens", MED_USED = "med_used";

    static SharedPreferences sp(Context c) { return c.getSharedPreferences(FILE, Context.MODE_PRIVATE); }

    static void load(Context c, Config cfg) {
        SharedPreferences p = sp(c);
        cfg.h24 = p.getBoolean(H24, DateFormat.is24HourFormat(c));
        cfg.compass = p.getBoolean(COMPASS, true);
        cfg.haptics = p.getBoolean(HAPTICS, true);
        cfg.autoMusic = p.getBoolean(MUSIC, true);
        cfg.rounds = p.getInt(ROUNDS, 4);
        cfg.pattern = p.getInt(PATTERN, 1);
    }

    static Config load(Context c) {
        Config cfg = new Config();
        load(c, cfg);
        return cfg;
    }
}
