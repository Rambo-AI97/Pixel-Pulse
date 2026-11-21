package com.ramboai97.pixelpulse;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.net.Uri;


import com.ramboai97.pixelpulse.core.Config;

/** One screen: how it works, the two permissions, and settings. */
public class MainActivity extends Activity {
    private static final int FG = Color.WHITE, DIM = Color.rgb(150, 150, 150), CARD = Color.rgb(22, 22, 22),
            LINE = Color.rgb(48, 48, 48), ACCENT = Color.rgb(215, 25, 33);

    private LinearLayout root;
    private Config cfg;
    private TextView micButton;

    // ---- links (the tip page goes in res/values/strings.xml: tip_url; empty = row hidden)
    static final String REPO_URL = "https://github.com/Rambo-AI97/pixel-pulse";
    static final String POCKET_PUZZLE_URL = "https://play.google.com/store/apps/details?id=com.ramboai97.pocketpuzzle";
    static final String FEEDBACK_EMAIL = "pocketpuzzle.app@gmail.com";

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        cfg = Prefs.load(this);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.BLACK);
        scroll.setFitsSystemWindows(true);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(28), dp(20), dp(40));
        scroll.addView(root);
        setContentView(scroll);

        TextView title = text("Pixel Pulse", 30, FG);
        title.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        root.addView(title);
        TextView sub = text("A retro clock for the Glyph Matrix on the back of your Nothing Phone (3) or Phone (4a) Pro.", 15, DIM);
        sub.setPadding(0, dp(4), 0, dp(20));
        root.addView(sub);

        label("HOW IT WORKS");
        card(MatrixView.DEMO_CLOCK, "Clock & compass",
                "Time on one line, like an LED scoreboard. The bright arrow on the edge points north as you turn the phone. It hides if the compass isn't sure.");
        card(MatrixView.DEMO_BREATHE, "Hold to breathe",
                "Long-press the Glyph Button for box breathing: in 4 · hold 4 · out 4 · hold 4. A dot walks around the box and it fills as you breathe in. The dots underneath count the breaths left, 4 · 3 · 2 · 1, then the clock comes back. You feel a tap at each change, so eyes closed is fine. Press the button to stop early. (Phone (3) only: the (4a) Pro has no Glyph Button.)");
        card(MatrixView.DEMO_MUSIC, "Music → bars",
                "Play music and the clock turns into an equalizer by itself. A ♪ shows first so you know why. It goes back to the clock when the music stops.");

        label("SET UP");
        root.addView(button("Add to Glyph Toys", true, v -> openToysManager()));
        TextView tip = text("Then tap the Glyph Button on the back until Pixel Pulse appears.", 13, DIM);
        tip.setPadding(dp(4), dp(8), 0, dp(14));
        root.addView(tip);
        micButton = button("", false, v -> requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 1));
        root.addView(micButton);
        TextView why = text("Microphone permission is used only to see the music the phone itself is playing, so the bars can move with it. It never listens to the room and nothing is recorded or saved. Without it, the bars still show a gentle pulse.", 13, DIM);
        why.setPadding(dp(4), dp(8), 0, dp(8));
        root.addView(why);

        thanksSection();

        label("SETTINGS");
        toggle("24-hour clock", Prefs.H24, cfg.h24);
        toggle("Show compass (N)", Prefs.COMPASS, cfg.compass);
        toggle("Vibration cues while breathing", Prefs.HAPTICS, cfg.haptics);
        toggle("Switch to bars when music plays", Prefs.MUSIC, cfg.autoMusic);
        choice("Breaths per session", new String[]{"2", "3", "4"}, new int[]{2, 3, 4}, Prefs.ROUNDS, cfg.rounds);
        choice("Breathing", Config.PATTERN_NAMES, new int[]{0, 1, 2}, Prefs.PATTERN, cfg.pattern);

        TextView foot = text("Tap the Glyph Button to move between toys. Hold it to start or stop a session.", 13, DIM);
        foot.setPadding(dp(4), dp(24), 0, 0);
        root.addView(foot);
    }

    @Override protected void onResume() {
        super.onResume();
        boolean ok = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
        micButton.setText(ok ? "✓  Music bars allowed" : "Allow music bars");
        micButton.setEnabled(!ok);
        micButton.setAlpha(ok ? 0.6f : 1f);
    }

    /**
     * "Say thanks": the promise first, then one optional tip link (an outside page,
     * since toys from Playground can't use Google Play payments), then free ways to help.
     * Never a pop-up, never in the toy itself.
     */
    private void thanksSection() {
        label("SAY THANKS");
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(16), dp(16), dp(12));
        box.setBackground(round(CARD, 0));
        box.addView(text("Pixel Pulse is free, with no ads and no tracking, and it will stay that way. Made by a small family in Canada.", 13.5f, FG));
        String tipUrl = getString(R.string.tip_url).trim();
        if (!tipUrl.isEmpty()) {
            TextView how = text("If you enjoy it, you can leave a tip. It's a thank-you only: it unlocks nothing.", 13, DIM);
            how.setPadding(0, dp(10), 0, dp(4));
            box.addView(how);
            box.addView(row("♥", "Leave a tip", "Opens our tip page in your browser", null, v -> open(tipUrl)));
        }
        box.addView(row("★", "Star it on GitHub", "Updates and the code live here", null, v -> open(REPO_URL)));
        box.addView(row("✉", "Share an idea", "A toy you'd like, or something to fix", null,
                v -> open("mailto:" + FEEDBACK_EMAIL + "?subject=" + Uri.encode("Pixel Pulse idea"))));
        box.addView(row("▦", "Try Pocket Puzzle", "Our calm puzzle games, also free", null, v -> open(POCKET_PUZZLE_URL)));
        root.addView(box);
    }

    private void open(String url) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
        catch (Exception e) { Toast.makeText(this, url, Toast.LENGTH_LONG).show(); }
    }

    /** One tappable line: icon, label + hint, and an optional price on the right. */
    private LinearLayout row(String icon, String label, String hint, TextView right, View.OnClickListener l) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(4), dp(10), dp(4), dp(10));
        r.setOnClickListener(l);
        TextView ic = text(icon, 18, FG);
        ic.setGravity(Gravity.CENTER);
        r.addView(ic, new LinearLayout.LayoutParams(dp(32), -2));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(8), 0, dp(8), 0);
        col.addView(text(label, 15, FG));
        col.addView(text(hint, 12.5f, DIM));
        r.addView(col, new LinearLayout.LayoutParams(0, -2, 1));
        if (right != null) r.addView(right);
        return r;
    }

    @Override public void onRequestPermissionsResult(int code, String[] p, int[] r) { onResume(); }

    private void openToysManager() {
        try {
            Intent i = new Intent();
            i.setComponent(new ComponentName("com.nothing.thirdparty", "com.nothing.thirdparty.matrix.toys.manager.ToysManagerActivity"));
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "Open Settings › Glyph Interface › Glyph Toys", Toast.LENGTH_LONG).show();
        }
    }

    // ---------------------------------------------------------------- small UI kit

    private void card(int demo, String head, String body) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(14), dp(14), dp(14));
        row.setBackground(round(CARD, 0));
        MatrixView mv = new MatrixView(this, demo, cfg);
        row.addView(mv, new LinearLayout.LayoutParams(dp(104), dp(104)));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), 0, 0, 0);
        TextView h = text(head, 17, FG);
        col.addView(h);
        TextView t = text(body, 13.5f, DIM);
        t.setPadding(0, dp(4), 0, 0);
        col.addView(t);
        row.addView(col, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(10);
        root.addView(row, lp);
    }

    private void toggle(String name, String key, boolean value) {
        Switch s = new Switch(this);
        s.setText(name);
        s.setTextColor(FG);
        s.setTextSize(15);
        s.setChecked(value);
        s.setPadding(dp(4), dp(10), dp(4), dp(10));
        s.setOnCheckedChangeListener((v, on) -> { Prefs.sp(this).edit().putBoolean(key, on).apply(); Prefs.load(this, cfg); });
        root.addView(s);
    }

    private void choice(String name, String[] labels, int[] values, String key, int current) {
        TextView n = text(name, 15, FG);
        n.setPadding(dp(4), dp(14), 0, dp(8));
        root.addView(n);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        TextView[] chips = new TextView[labels.length];
        for (int i = 0; i < labels.length; i++) {
            TextView c = text(labels[i], 13, FG);
            c.setGravity(Gravity.CENTER);
            c.setPadding(dp(6), dp(10), dp(6), dp(10));
            final int idx = i;
            c.setOnClickListener(v -> {
                Prefs.sp(this).edit().putInt(key, values[idx]).apply();
                Prefs.load(this, cfg);
                for (int j = 0; j < chips.length; j++) styleChip(chips[j], j == idx);
            });
            chips[i] = c;
            styleChip(c, values[i] == current);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
            lp.rightMargin = i < labels.length - 1 ? dp(6) : 0;
            row.addView(c, lp);
        }
        root.addView(row);
    }

    private void styleChip(TextView c, boolean on) {
        c.setBackground(round(on ? FG : Color.BLACK, on ? 0 : LINE));
        c.setTextColor(on ? Color.BLACK : FG);
    }

    private TextView button(String label, boolean primary, View.OnClickListener l) {
        TextView b = text(label, 15, primary ? Color.WHITE : FG);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(16), dp(14), dp(16), dp(14));
        b.setBackground(round(primary ? ACCENT : Color.BLACK, primary ? 0 : LINE));
        b.setOnClickListener(l);
        return b;
    }

    private void label(String s) {
        TextView t = text(s, 12, DIM);
        t.setLetterSpacing(0.15f);
        t.setPadding(dp(4), dp(18), 0, dp(10));
        root.addView(t);
    }

    private TextView text(String s, float sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setLineSpacing(0, 1.15f);
        return t;
    }

    private GradientDrawable round(int fill, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(18));
        if (stroke != 0) d.setStroke(dp(1), stroke);
        return d;
    }

    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
