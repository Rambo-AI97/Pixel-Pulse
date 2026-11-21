import com.ramboai97.pixelpulse.core.*;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Play Store art rendered from the real Scene: 512 icon, 1024x500 feature graphic,
 * and 1080x1920 screenshots (matrix + one caption line).
 * java StoreArt <outDir>
 */
public class StoreArt {
    static final Color BG = new Color(10, 10, 10), RED = new Color(215, 25, 33);

    public static void main(String[] a) throws Exception {
        File out = new File(a[0]);
        out.mkdirs();
        int[] clock = clock(10, 9, 35);
        int[] north = clock(10, 9, 0);
        int[] breathe = breathe();
        int[] bars = music(new float[]{0.95f, 0.8f, 0.55f, 0.45f, 0.35f, 0.3f});
        int[] barsLow = music(new float[]{0.45f, 0.5f, 0.6f, 0.4f, 0.3f, 0.2f});

        // icon: clock face only, square, black
        BufferedImage icon = new BufferedImage(512, 512, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = gfx(icon);
        g.setColor(BG); g.fillRect(0, 0, 512, 512);
        matrix(g, clock, 16, 16, 480);
        ImageIO.write(icon, "png", new File(out, "icon-512.png"));

        // feature graphic
        BufferedImage fg = new BufferedImage(1024, 500, BufferedImage.TYPE_INT_RGB);
        g = gfx(fg);
        g.setColor(BG); g.fillRect(0, 0, 1024, 500);
        matrix(g, clock, 40, 50, 400);
        matrix(g, bars, 584, 50, 400);
        ImageIO.write(fg, "png", new File(out, "feature-1024x500.png"));

        shot(out, "shot-1-clock.png", north, "A retro LED clock", "for the back of your Phone (3)");
        shot(out, "shot-2-compass.png", clock, "The arrow points north", "turn the phone, it follows");
        shot(out, "shot-3-music.png", bars, "Music turns it into bars", "they jump with the beat");
        shot(out, "shot-4-voice.png", barsLow, "Voices too", "podcasts and calls open and close it");
        shot(out, "shot-5-breathe.png", breathe, "Hold to breathe", "4 in, 4 hold, 4 out, 4 hold");
        System.out.println("ok");
    }

    static Graphics2D gfx(BufferedImage i) {
        Graphics2D g = i.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        return g;
    }

    static void shot(File dir, String name, int[] f, String head, String sub) throws Exception {
        BufferedImage img = new BufferedImage(1080, 1920, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = gfx(img);
        g.setColor(BG); g.fillRect(0, 0, 1080, 1920);
        g.setColor(new Color(28, 28, 28));
        g.fillRoundRect(90, 420, 900, 900, 160, 160);       // the phone's back, simplified
        matrix(g, f, 140, 470, 800);
        g.setColor(Color.WHITE);
        g.setFont(new java.awt.Font("SansSerif", java.awt.Font.BOLD, 76));
        center(g, head, 1080, 230);
        g.setColor(new Color(170, 170, 170));
        g.setFont(new java.awt.Font("SansSerif", java.awt.Font.PLAIN, 46));
        center(g, sub, 1080, 315);
        g.setColor(RED);
        g.fillOval(530, 1500, 20, 20);
        g.setColor(new Color(120, 120, 120));
        g.setFont(new java.awt.Font("SansSerif", java.awt.Font.PLAIN, 38));
        center(g, "Pixel Pulse · Glyph Toy", 1080, 1640);
        ImageIO.write(img, "png", new File(dir, name));
    }

    static void center(Graphics2D g, String s, int w, int y) {
        int sw = g.getFontMetrics().stringWidth(s);
        g.drawString(s, (w - sw) / 2, y);
    }

    /** Round matrix of LEDs at x,y with diameter d. */
    static void matrix(Graphics2D g, int[] f, int x, int y, int d) {
        g.setColor(Color.BLACK);
        g.fillOval(x, y, d, d);
        float cell = d / 26f, off = cell / 2f;
        for (int r = 0; r < 25; r++)
            for (int c = 0; c < 25; c++) {
                if (!Px.on(c, r)) continue;
                int v = f[r * 25 + c];
                int k = v == 0 ? 22 : Math.min(255, 40 + v * 215 / 255);
                g.setColor(new Color(k, k, k));
                float s = cell * 0.78f;
                g.fillOval(Math.round(x + off + c * cell + (cell - s) / 2), Math.round(y + off + r * cell + (cell - s) / 2), Math.round(s), Math.round(s));
            }
    }

    static Scene scene() {
        Config c = new Config();
        c.h24 = false;
        return new Scene(c, null);
    }

    static int[] clock(int h, int m, int deg) {
        Scene s = scene();
        Scene.Input in = new Scene.Input();
        in.hour = h; in.minute = m; in.second = 0; in.headingOk = true; in.heading = (float) Math.toRadians(deg);
        s.open(0, false, false);
        int[] f = null;
        for (long t = 0; t < 2000; t += 33) f = s.frame(t, in);
        return f;
    }

    static int[] breathe() {
        Scene s = scene();
        Scene.Input in = new Scene.Input();
        in.hour = 10; in.minute = 9;
        s.open(0, false, false);
        s.longPress(0);
        int[] f = null;
        for (long t = 0; t < 3000 + 2600; t += 33) f = s.frame(t, in);
        return f;
    }

    static int[] music(float[] peak) {
        Scene s = scene();
        Scene.Input in = new Scene.Input();
        in.hour = 10; in.minute = 9; in.musicActive = true;
        s.open(0, false, false);
        int[] f = null;
        long t = 0;
        in.bands = new float[]{0.2f, 0.2f, 0.25f, 0.2f, 0.15f, 0.1f};
        for (; t < 2500; t += 33) f = s.frame(t, in);
        in.bands = peak;
        f = s.frame(t, in);
        f = s.frame(t + 33, in);
        return f;
    }
}
