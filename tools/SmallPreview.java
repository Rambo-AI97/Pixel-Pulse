import com.ramboai97.pixelpulse.core.*;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Contact sheet of the 13x13 (Phone (4a) Pro) toy. java SmallPreview out.png */
public class SmallPreview {
    public static void main(String[] a) throws Exception {
        Config cfg = new Config();
        cfg.h24 = false;
        List<int[]> fr = new ArrayList<>();
        List<String> lb = new ArrayList<>();
        int[] degs = {0, 45, 120, 250};
        for (int d : degs) {
            SmallScene s = new SmallScene(cfg);
            Scene.Input in = new Scene.Input();
            in.hour = 10; in.minute = 9; in.headingOk = true; in.heading = (float) Math.toRadians(d);
            int[] f = null;
            for (long t = 0; t < 2000; t += 33) f = s.frame(t, in);
            fr.add(f); lb.add("clock, N " + d);
        }
        SmallScene s = new SmallScene(cfg);
        Scene.Input in = new Scene.Input();
        in.hour = 4; in.minute = 37; in.musicActive = true; in.headingOk = false;
        long t = 0;
        in.bands = new float[]{0.2f, 0.2f, 0.25f, 0.2f, 0.15f, 0.1f};
        int[] f = null;
        for (; t < 2500; t += 33) f = s.frame(t, in);
        fr.add(f); lb.add("music quiet");
        in.bands = new float[]{0.95f, 0.8f, 0.5f, 0.4f, 0.3f, 0.3f};
        f = s.frame(t, in); t += 33; f = s.frame(t, in); t += 33;
        fr.add(f); lb.add("kick");
        in.bands = new float[]{0.3f, 0.25f, 0.25f, 0.2f, 0.15f, 0.1f};
        for (long e = t + 130; t < e; t += 33) f = s.frame(t, in);
        fr.add(f); lb.add("+130ms");

        int cell = 18, size = 13 * cell + 30, label = 22;
        BufferedImage img = new BufferedImage(size * fr.size(), size + label, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(18, 18, 18)); g.fillRect(0, 0, img.getWidth(), img.getHeight());
        for (int i = 0; i < fr.size(); i++) {
            int ox = i * size;
            g.setColor(Color.BLACK); g.fillOval(ox + 4, 4, size - 8, size - 8);
            for (int y = 0; y < 13; y++)
                for (int x = 0; x < 13; x++) {
                    if (!SmallScene.on(x, y)) continue;
                    int v = fr.get(i)[y * 13 + x];
                    int c = v == 0 ? 16 : Math.min(255, 40 + v * 215 / 255);
                    g.setColor(new Color(c, c, c));
                    g.fillOval(ox + 15 + x * cell + 2, 15 + y * cell + 2, cell - 4, cell - 4);
                }
            g.setColor(new Color(200, 200, 200));
            g.drawString(lb.get(i), ox + 8, size + 15);
        }
        ImageIO.write(img, "png", new File(a[0]));
    }
}
