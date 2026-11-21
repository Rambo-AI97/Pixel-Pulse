import com.ramboai97.pixelpulse.core.*;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Renders labelled key frames of the real Scene code into a contact sheet PNG. */
public class Preview {
    static final int CELL = 10, PAD = 14, SIZE = 25 * CELL + PAD * 2, LABEL = 22;

    static List<int[]> frames = new ArrayList<>();
    static List<String> labels = new ArrayList<>();

    public static void main(String[] a) throws Exception {
        Config cfg = new Config();
        cfg.h24 = false;
        List<String> buzz = new ArrayList<>();
        Scene.Listener l = new Scene.Listener() {
            public void haptic(int k) { buzz.add("H" + k); }
            public void meditationUsed() {}
        };
        Scene s = new Scene(cfg, l);
        Scene.Input in = new Scene.Input();
        in.hour = 10; in.minute = 9; in.second = 0; in.headingOk = true; in.heading = 0;
        long t = 0;
        s.open(t, true, true);
        run(s, in, t, 300, "intro sweep 0.3s"); t += 300;
        run(s, in, t, 1700, "clock, needle N"); t += 1700;
        for (int deg : new int[]{30, 45, 120, 200, 300}) {
            in.heading = (float) Math.toRadians(deg);
            run(s, in, t, 2000, "needle " + deg); t += 2000;
        }
        in.heading = (float) Math.toRadians(20);
        run(s, in, t, 1500, "hint dot bright"); t += 1500;
        in.headingOk = false;
        run(s, in, t, 1500, "compass unreliable"); t += 1500;
        in.headingOk = true;
        s.longPress(t);
        long ms0 = t + 3000; // breathing starts after the 3 s countdown
        run(s, in, t, 200, "countdown 3"); t += 200;
        run(s, in, t, 1300, "countdown 2"); t += 1300;
        run(s, in, t, 1800 + 300, "IN 0.3s (4 left)"); t += 2100;
        run(s, in, t, 2000, "IN 2.3s"); t += 2000;
        run(s, in, t, 2000, "HOLD 0.3s"); t += 2000;
        run(s, in, t, 2000, "HOLD 2.3s"); t += 2000;
        run(s, in, t, 2000, "OUT 0.3s"); t += 2000;
        run(s, in, t, 2000, "OUT 2.3s"); t += 2000;
        run(s, in, t, 4000, "HOLD bottom"); t += 4000;
        run(s, in, t, ms0 + 16000 + 2000 - t, "box 2 (3 left)"); t = ms0 + 18000;
        run(s, in, t, ms0 + 48000 + 6000 - t, "box 4 (1 left)"); t = ms0 + 54000;
        run(s, in, t, ms0 + 64000 + 200 - t, "done flash"); t = ms0 + 64200;
        run(s, in, t, 400, "done sparks"); t += 400;
        run(s, in, t, 700, "done heart"); t += 700;
        run(s, in, t, 300, "heart beat"); t += 300;
        run(s, in, t, 3000, "back to clock"); t += 3000;
        s.longPress(t);
        run(s, in, t, 3000, "2nd session start"); t += 3000;
        run(s, in, t, 20000, "20 s in"); t += 20000;
        s.tap(t);
        run(s, in, t, 300, "tap cancel 0.3s"); t += 300;
        run(s, in, t, 1500, "after cancel"); t += 1500;
        in.musicActive = true;
        run(s, in, t, 1300, "music note"); t += 1300;
        float[] bands = {0.9f, 0.75f, 0.6f, 0.45f, 0.3f, 0.2f};
        in.bands = bands;
        run(s, in, t, 1500, "bars"); t += 1500;
        in.bands = new float[]{0.5f, 0.9f, 0.4f, 0.7f, 0.5f, 0.6f};
        run(s, in, t, 500, "bars 2"); t += 500;
        in.bands = new float[]{0.2f, 0.2f, 0.25f, 0.2f, 0.15f, 0.1f};
        run(s, in, t, 600, "between beats"); t += 600;
        in.bands = new float[]{0.95f, 0.8f, 0.5f, 0.4f, 0.3f, 0.3f};
        run(s, in, t, 33, "KICK +33ms"); t += 33;
        in.bands = new float[]{0.3f, 0.25f, 0.25f, 0.2f, 0.15f, 0.1f};
        run(s, in, t, 99, "+130ms"); t += 99;
        run(s, in, t, 165, "+300ms (shut)"); t += 165;
        in.bands = new float[]{0.2f, 0.3f, 0.8f, 0.7f, 0.4f, 0.2f};
        run(s, in, t, 33, "syllable"); t += 33;
        in.bands = new float[6];
        run(s, in, t, 600, "pause in speech"); t += 600;
        in.bands = null;
        run(s, in, t, 4000, "bars pulse (no data)"); t += 4000;
        s.tap(t);
        run(s, in, t, 1000, "tap -> clock (music on)"); t += 1000;
        in.musicActive = false;
        run(s, in, t, 3200, "music stops -> clock"); t += 3200;
        System.out.println("haptics: " + buzz.size() + " first: " + buzz.subList(0, Math.min(12, buzz.size())));
        System.out.println("rim leds: " + Px.RIM.length);
        sheet(a[0]);
        thumb(s, a[1]);
    }

    /** Advances at 30 fps for ms, keeps the last frame. */
    static void run(Scene s, Scene.Input in, long t0, long ms, String label) {
        int[] f = null;
        for (long t = t0; t <= t0 + ms; t += 33) f = s.frame(t, in);
        frames.add(f);
        labels.add(label);
    }

    static void sheet(String path) throws Exception {
        int cols = 6, rows = (frames.size() + cols - 1) / cols;
        BufferedImage img = new BufferedImage(cols * SIZE, rows * (SIZE + LABEL), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(18, 18, 18));
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        for (int i = 0; i < frames.size(); i++) {
            int ox = (i % cols) * SIZE, oy = (i / cols) * (SIZE + LABEL);
            g.setColor(Color.BLACK);
            g.fillOval(ox + 4, oy + 4, SIZE - 8, SIZE - 8);
            int[] f = frames.get(i);
            for (int y = 0; y < 25; y++)
                for (int x = 0; x < 25; x++) {
                    if (!Px.on(x, y)) continue;
                    int v = f[y * 25 + x];
                    int c = v == 0 ? 16 : Math.min(255, 40 + v * 215 / 255);
                    g.setColor(new Color(c, c, c));
                    g.fillOval(ox + PAD + x * CELL + 1, oy + PAD + y * CELL + 1, CELL - 2, CELL - 2);
                }
            g.setColor(new Color(200, 200, 200));
            g.setFont(new java.awt.Font("SansSerif", java.awt.Font.PLAIN, 13));
            g.drawString(labels.get(i), ox + 8, oy + SIZE + 15);
        }
        ImageIO.write(img, "png", new File(path));
    }

    /** Vector drawable of the clock face for the Glyph Toys settings list. */
    static void thumb(Scene unused, String path) throws Exception {
        Config c = new Config();
        c.h24 = false;
        Scene s = new Scene(c, null);
        Scene.Input in = new Scene.Input();
        in.hour = 10; in.minute = 9; in.second = 0; in.headingOk = true; in.heading = (float) Math.toRadians(35);
        s.open(0, false, false);
        int[] f = null;
        for (long t = 0; t < 2000; t += 33) f = s.frame(t, in);
        double pitch = 17.820416666666667, x0 = 35.63, sq = 13.05;
        StringBuilder sb = new StringBuilder();
        sb.append("<vector xmlns:android=\"http://schemas.android.com/apk/res/android\"\n")
          .append("    android:width=\"512dp\" android:height=\"512dp\"\n")
          .append("    android:viewportWidth=\"512\" android:viewportHeight=\"512\">\n")
          .append("  <path android:pathData=\"M256,256m-256,0a256,256 0,1 1,512 0a256,256 0,1 1,-512 0\" android:fillColor=\"#000000\"/>\n");
        for (int y = 0; y < 25; y++)
            for (int x = 0; x < 25; x++) {
                if (!Px.on(x, y)) continue;
                int v = f[y * 25 + x];
                String col = v == 0 ? "#1C1C1C" : String.format("#%02X%02X%02X", Math.max(28, v), Math.max(28, v), Math.max(28, v));
                sb.append(String.format(java.util.Locale.US,
                        "  <path android:pathData=\"M%.2f,%.2fh%.2fv%.2fh-%.2fz\" android:fillColor=\"%s\"/>\n",
                        x0 + x * pitch, x0 + y * pitch, sq, sq, sq, col));
            }
        sb.append("</vector>\n");
        java.nio.file.Files.writeString(new File(path).toPath(), sb.toString());
    }
}
